package org.mineacademy.chatcontrol.proxy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.ChatControlProxyMessage;
import org.mineacademy.chatcontrol.model.SyncType;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.SerializeUtilCore.Language;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.platform.FoundationPlayer;
import org.mineacademy.fo.platform.FoundationServer;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.proxy.message.OutgoingMessage;

import com.imaginarycode.minecraft.redisbungee.AbstractRedisBungeeAPI;
import com.imaginarycode.minecraft.redisbungee.api.events.IPubSubMessageEvent;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The main class providing a partial Redis integration
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Redis {

	/**
	 * Whether the Redis integration is enabled
	 */
	@Getter
	@Setter
	private static boolean enabled = false;

	/**
	 * Listen to plugin messages across network
	 *
	 * @param event
	 */
	public static void handlePubSubMessage(final IPubSubMessageEvent event) {
		if (enabled)
			Hook.handlePubSubMessage(event);
	}

	/**
	 * Returns all servers found from all players connected on the Redis network
	 *
	 * @return
	 */
	public static Collection<String> getServers() {
		return enabled ? Hook.getServers() : new ArrayList<>();
	}

	/**
	 * Sends a raw plugin message data to the whole Redis network
	 *
	 * @param uuid
	 * @param channel
	 * @param data
	 */
	public static void sendDataToOtherServers(final UUID uuid, final String channel, final byte[] data) {
		if (enabled)
			Hook.sendDataToOtherServers(uuid, channel, data);
	}

	/**
	 * Sends a SyncedCache data for the given synced type to the Redis network
	 *
	 * @param type
	 * @param data
	 */
	public static void sendPlayerCacheData(final SyncType type, SerializedMap data) {
		if (enabled)
			Hook.sendPlayerCacheData(type, data);
	}

	/**
	 * Executes the given command across Redis network
	 *
	 * @param command
	 */
	public static void dispatchCommand(final String command) {
		if (enabled)
			Hook.dispatchCommand(command);
	}
}

final class Hook {

	/*
	 * Always fetch the current RedisBungee API instance to avoid stale references.
	 * Previously this was a static final field that could go stale if RedisBungee
	 * reconnected internally, causing all Redis operations to silently fail.
	 */
	private static AbstractRedisBungeeAPI getRedisAPI() {
		return AbstractRedisBungeeAPI.getAbstractRedisBungeeAPI();
	}

	public static void handlePubSubMessage(final IPubSubMessageEvent event) {
		if (event.getChannel().equals(ProxyConstants.REDIS_CHANNEL)) {
			Debugger.debug("redis", "Received redis message: " + event.getMessage());

			try {
				final String[] data = event.getMessage().split(":", 4);

				if (data.length == 4 && data[0].equals("SEND_SB")) {
					final UUID playerId = UUID.fromString(data[1]);
					final FoundationPlayer player = Platform.getPlayer(playerId);

					if (player != null && player.getServer() != null) {
						Debugger.debug("redis", "SEND_SB: forwarding to server " + player.getServer().getName() + " for player " + playerId);

						final byte[] byteOutput = decapsulate(data[3]);
						player.getServer().sendData(ProxyConstants.REDIS_CHANNEL, byteOutput);
					} else
						Debugger.debug("redis", "SEND_SB: player " + playerId + " not found or has no server, dropping message");

				} else if (data.length == 4 && data[0].equals("SEND_OB")) {
					// send to servers that player is not on
					final UUID playerId = UUID.fromString(data[1]);
					final Collection<FoundationServer> servers = Platform.getServers();

					Debugger.debug("redis", "SEND_OB: forwarding for player " + playerId + ", found " + servers.size() + " servers: " + CommonCore.simplify(servers));

					if (servers.isEmpty())
						Debugger.debug("redis", "SEND_OB: WARNING server list is empty! Messages will not be forwarded to any server.");

					for (final FoundationServer otherServer : servers) {
						// Check if the player is on this server
						boolean playerOnServer = false;

						for (final UUID otherPlayerUid : otherServer.getPlayerUniqueIds()) {
							if (otherPlayerUid.equals(playerId)) {
								playerOnServer = true;

								break;
							}
						}

						// Only send to servers where the player is NOT present
						if (!playerOnServer) {
							Debugger.debug("redis", "\tSending data to " + otherServer.getName() + " (player not present)");
							final byte[] byteOutput = decapsulate(data[3]);
							otherServer.sendData(ProxyConstants.BUNGEECORD_CHANNEL, byteOutput);
						} else {
							Debugger.debug("redis", "\tNot sending to " + otherServer.getName() + " as player is present");
						}
					}

				} else if (data.length == 4 && data[0].equals("SEND_CACHE")) {
					final SyncType type = SyncType.valueOf(data[1]);
					final String redisProxyId = data[2];
					final byte[] byteOutput = decapsulate(data[3]);
					final String json = new String(byteOutput, StandardCharsets.UTF_8);
					final SerializedMap playerUniqueIdsAndValues = SerializedMap.fromObject(Language.JSON, json);

					final AbstractRedisBungeeAPI redisAPI = getRedisAPI();

					if (!redisProxyId.equals(redisAPI.getProxyId())) {
						Debugger.debug("redis", "SEND_CACHE: syncing " + type + " from proxy " + redisProxyId + " (our proxy: " + redisAPI.getProxyId() + ")");

						final OutgoingMessage message = new OutgoingMessage(ChatControlProxyMessage.SYNCED_CACHE_BY_UUID);

						message.writeString(type.toString());
						message.writeMap(playerUniqueIdsAndValues);
						message.broadcast();

						SyncedCache.uploadClusterFromUids(type, playerUniqueIdsAndValues);
					} else
						Debugger.debug("redis", "SEND_CACHE: ignoring own cache sync for " + type + " from proxy " + redisProxyId);

				} else if (data.length == 4 && data[0].equals("SEND_M")) {
					final UUID playerId = UUID.fromString(data[1]);
					final FoundationPlayer player = Platform.getPlayer(playerId);

					if (player != null) {
						Debugger.debug("redis", "SEND_M: delivering message to player " + playerId);
						player.sendMessage(SimpleComponent.fromSection(data[3]));
					} else
						Debugger.debug("redis", "SEND_M: player " + playerId + " not found on this proxy, dropping message");

				} else
					CommonCore.log("Received invalid Redis message: " + event.getMessage());

			} catch (final Throwable throwable) {
				CommonCore.error(throwable, "Error processing Redis message");
			}
		}
	}

	public static Collection<String> getServers() {
		final Set<String> servers = new HashSet<>();

		try {
			final AbstractRedisBungeeAPI redisAPI = getRedisAPI();
			final Set<UUID> playersOnline = redisAPI.getPlayersOnline();

			Debugger.debug("redis", "getServers: Redis reports " + playersOnline.size() + " players online");

			for (final UUID playerId : playersOnline) {
				final String serverName = redisAPI.getServerNameFor(playerId);

				if (serverName != null)
					servers.add(serverName);
			}

			Debugger.debug("redis", "getServers: found " + servers.size() + " servers from online players: " + servers);

		} catch (final Throwable throwable) {
			CommonCore.error(throwable,
					"Failed to get server list from RedisBungee",
					"Returning empty server list, forwarding may be affected.");
		}

		return servers;
	}

	public static void sendDataToOtherServers(final UUID uuid, final String channel, final byte[] data) {
		try {
			Debugger.debug("redis", "sendDataToOtherServers: publishing SEND_OB for player " + uuid + " on channel " + channel + " (" + data.length + " bytes)");

			getRedisAPI().sendChannelMessage(ProxyConstants.REDIS_CHANNEL, "SEND_OB:" + uuid.toString() + ":" + channel.replace("\\", "\\\\").replace(":", " \\;") + ":" + encapsulate(data));
		} catch (final Throwable throwable) {
			CommonCore.error(throwable,
					"Failed to send plugin data via Redis",
					"Player UUID: " + uuid,
					"Channel: " + channel,
					"Data length: " + data.length + " bytes");
		}
	}

	public static void sendPlayerCacheData(final SyncType type, SerializedMap data) {
		try {
			final AbstractRedisBungeeAPI redisAPI = getRedisAPI();

			redisAPI.sendChannelMessage(ProxyConstants.REDIS_CHANNEL, "SEND_CACHE:" + type.toString() + ":" + redisAPI.getProxyId() + ":" + encapsulate(data.toJson().getBytes(StandardCharsets.UTF_8)));
		} catch (final Throwable throwable) {
			CommonCore.error(throwable,
					"Failed to send player cache data via Redis",
					"Sync type: " + type);
		}
	}

	static void dispatchCommand(final String command) {
		try {
			getRedisAPI().sendProxyCommand(command);
		} catch (final Throwable throwable) {
			CommonCore.error(throwable,
					"Failed to dispatch command via Redis",
					"Command: " + command);
		}
	}

	/*
	 * Convert the data array to Base64
	 */
	private static String encapsulate(final byte[] data) {
		return Base64.getEncoder().encodeToString(data);
	}

	/*
	 * Convers the Base64 string to data array
	 */
	private static byte[] decapsulate(final String data) {
		return Base64.getDecoder().decode(data);
	}
}