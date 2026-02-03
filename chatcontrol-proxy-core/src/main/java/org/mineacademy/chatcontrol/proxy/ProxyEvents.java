package org.mineacademy.chatcontrol.proxy;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.ChatControlProxyMessage;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.PlaceholderPrefix;
import org.mineacademy.chatcontrol.model.PlayerMessageType;
import org.mineacademy.chatcontrol.proxy.command.MeCommand;
import org.mineacademy.chatcontrol.proxy.command.SayCommand;
import org.mineacademy.chatcontrol.proxy.operator.ProxyPlayerMessages;
import org.mineacademy.chatcontrol.proxy.settings.ProxySettings;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.collection.ExpiringMap;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.IsInList;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.platform.FoundationPlayer;
import org.mineacademy.fo.platform.FoundationServer;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.proxy.message.OutgoingMessage;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Shared proxy code for event handling logic.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProxyEvents {

	/**
	 * We cannot show join/switch messages right now because we need to wait for Spigot to tell
	 * us if player is vanished, afk etc etc for rules conditions. This map is used to store players
	 * waiting for their messages to show.
	 *
	 * It overrides previous values, only last message will get shown
	 */
	private static final Map<UUID, Tuple<PlayerMessageType, Map<String, Object>>> pendingMessages = ExpiringMap.builder().expiration(10, TimeUnit.SECONDS).build();

	/**
	 * Message de-duplicator
	 */
	private static final ExpiringMap<UUID, PlayerMessageType> lastMessages = ExpiringMap.builder().expiration(50, TimeUnit.MILLISECONDS).build();

	/**
	 * Because join and switch events are called in the same event (ServerSwitchEvent), we
	 * need to store joining players here to handle network switch.
	 */
	private static final Map<String, String> playerServerNamesByUniqueId = new HashMap<>();

	public static void registerCommands() {
		if (ProxySettings.Say.ENABLED)
			new SayCommand().register();

		if (ProxySettings.Me.ENABLED)
			new MeCommand().register();
	}

	/**
	 * Fires after the player has connected.
	 *
	 * @param player
	 * @param server
	 */
	public static void handlePostConnect(final FoundationPlayer player, final FoundationServer server) {
		if (server.getPlayerCount() <= 1)
			ChatControlProxyListenerProxy.getInstance().sendPendingMutes(server.getName());
	}

	/**
	 * Handle join messages.
	 *
	 * @param player
	 * @param server
	 */
	public static void handleConnect(final FoundationPlayer player, final FoundationServer server) {
		synchronized (pendingMessages) {

			// Force-create if not exist
			SyncedCache.getOrCreate(player.getName(), player.getUniqueId());

			if (!playerServerNamesByUniqueId.containsKey(player.getUniqueId().toString()) && !isSilent(server)) {
				final String toServer = ProxySettings.getServerNameAlias(server.getName());

				if (!isSilent(toServer)) {
					Debugger.debug("player-message", "Detected " + player.getName() + " join to " + toServer + ", waiting for server data..");

					pendingMessages.put(player.getUniqueId(), new Tuple<>(PlayerMessageType.JOIN, CommonCore.newHashMap(
							"server", toServer,
							"server_name", toServer,
							"player_server_name", toServer)));
				} else
					Debugger.debug("player-message", "Skipping join message for " + player.getName() + " because the server is ignored.");
			} else
				Debugger.debug("player-message", "Skipping join message for " + player.getName() + " because the last server name was already set.");

			final OutgoingMessage message = new OutgoingMessage(ChatControlProxyMessage.SERVER_ALIAS);

			message.writeString(server.getName());
			message.writeString(ProxySettings.getServerNameAlias(server.getName()));

			final byte[] data = message.toByteArray(CommonCore.ZERO_UUID, server.getName());

			if (data.length > Short.MAX_VALUE)
				CommonCore.log("[forwardData-main] Outgoing proxy message was oversized, not sending. Max length: 32766 bytes, got " + data.length + " bytes.");

			else {
				if (Redis.isEnabled())
					Redis.sendDataToOtherServers(player.getUniqueId(), ProxyConstants.CHATCONTROL_CHANNEL, data);
				else
					for (final FoundationServer iteratedServer : Platform.getServers())
						if (!iteratedServer.isEmpty())
							iteratedServer.sendData(ProxyConstants.BUNGEECORD_CHANNEL, data);
			}
		}
	}

	/**
	 * Handle network switch messages.
	 *
	 * @param player
	 * @param currentServer
	 */
	public static void handleSwitch(final FoundationPlayer player, final FoundationServer currentServer) {
		synchronized (pendingMessages) {
			final String lastServerName = playerServerNamesByUniqueId.put(player.getUniqueId().toString(), currentServer.getName());

			// Announce switches to/from silent servers on servers not silenced
			if (lastServerName != null) {
				final String fromServer = ProxySettings.getServerNameAlias(lastServerName);
				final String toServer = ProxySettings.getServerNameAlias(currentServer.getName());

				if (!isSilent(fromServer)) {
					Debugger.debug("player-message", "Detected " + player.getName() + " switch from " + fromServer + " to " + toServer + ", waiting for server data...");

					pendingMessages.put(player.getUniqueId(), new Tuple<>(PlayerMessageType.SWITCH, CommonCore.newHashMap(
							"from_server", fromServer,
							"from_server_name", fromServer,
							"player_from_server_name", fromServer,
							"to_server", toServer,
							"to_server_name", toServer,
							"player_to_server_name", toServer)));
				} else
					Debugger.debug("player-message", "Skipping switch message for " + player.getName() + " because the server is ignored.");
			} else
				Debugger.debug("player-message", "Skipping switch message for " + player.getName() + " because the last server name was null.");
		}
	}

	/**
	 * Handle disconnect messages.
	 *
	 * @param type
	 * @param player
	 */
	public static void handleDisconnect(final PlayerMessageType type, final FoundationPlayer player) {
		final String playerName = player.getName();
		final UUID playerUniqueId = player.getUniqueId();

		final ProxyServerCache cache = ProxyServerCache.getInstance();
		final String serverName = playerServerNamesByUniqueId.remove(playerUniqueId.toString());

		if (serverName != null && !isSilent(serverName)) {
			final String fromServer = ProxySettings.getServerNameAlias(serverName);
			final SyncedCache synced = SyncedCache.fromUniqueId(playerUniqueId);

			if (synced == null)
				Debugger.debug("player-message", "Skipping " + type.getKey() + " message for " + playerName + " on server " + fromServer + ", cache is null");

			else if (!synced.isLoggedIn())
				Debugger.debug("player-message", "Skipping " + type.getKey() + " message for " + playerName + " on server " + fromServer + ", player is not logged in");

			else if (isSilent(fromServer))
				Debugger.debug("player-message", "Skipping " + type.getKey() + " message for " + playerName + " on server " + fromServer + ", server is silent");

			else if (synced.isVanished() && !player.hasPermission(Permissions.Bypass.VANISH))
				Debugger.debug("player-message", "Skipping " + type.getKey() + " message for " + playerName + " on server " + fromServer + ", he is vanished and doesnt have " + Permissions.Bypass.VANISH + " perm");

			else {
				Debugger.debug("player-message", "Broadcasting " + type.getKey() + " message for " + playerName + " from server " + fromServer);

				final Map<String, Object> placeholders = synced.getPlaceholders(PlaceholderPrefix.PLAYER);

				placeholders.put("player_server_name", fromServer);
				placeholders.put("server_name", fromServer);
				placeholders.put("server", fromServer);

				ProxyPlayerMessages.broadcast(type, player, placeholders);
			}
		}

		// Register player for rules operator "has played before"
		if (!cache.isPlayerRegistered(player))
			cache.registerPlayer(player);
	}

	/**
	 * Handle tab complete for the given cursor and arguments.
	 *
	 * @param cursor
	 * @param args
	 */
	public static void handleTabComplete(final String cursor, final List<String> args) {
		final String label = !cursor.trim().isEmpty() && cursor.charAt(0) == '/' ? cursor.substring(1) : cursor;
		final IsInList<String> filterArgs = ProxySettings.TabComplete.FILTER_ARGUMENTS.get(label);

		if (filterArgs != null)
			for (final Iterator<String> it = args.iterator(); it.hasNext();) {
				final String arg = it.next();

				if (filterArgs.contains(arg))
					it.remove();
			}
	}

	/**
	 * Handle chat forwarding.
	 *
	 * @param player
	 * @param message
	 */
	public static void handleChatForwarding(final FoundationPlayer player, String message) {
		if (message.length() == 0 || message.charAt(0) == '/') {
			Debugger.debug("chat-forwarding", "Ignoring chat forwarding because message is empty or command: " + message);

			return;
		}

		if (player.getServer() == null) {
			CommonCore.log("Unexpected chat forwarding error: unknown server for " + player.getName());

			return;
		}

		final FoundationServer server = player.getServer();

		if (!ProxySettings.ChatForwarding.FROM_SERVERS.contains(server.getName())) {
			Debugger.debug("chat-forwarding", "Ignoring chat forwarding because from servers option does not contain '" + server.getName() + "'. Message: " + message);

			return;
		}

		message = String.format("<%s> %s", player.getName(), message);

		final Collection<FoundationPlayer> players = Platform.getOnlinePlayers();

		Debugger.debug("chat-forwarding", "Forwarding chat to " + players.size() + " players. Message: " + message);

		for (final FoundationPlayer online : players) {
			final FoundationServer onlineServer = online.getServer();

			if (onlineServer == null || onlineServer.equals(server)) {
				Debugger.debug("chat-forwarding", "Not forwarding to " + online.getName() + " because his server is empty or same as sending server");

				continue;
			}

			if (!ProxySettings.ChatForwarding.TO_SERVERS.contains(onlineServer.getName())) {
				Debugger.debug("chat-forwarding", "Not forwarding to " + online.getName() + " because to servers settings option does not contain: "
						+ onlineServer.getName() + " + but only: " + ProxySettings.ChatForwarding.TO_SERVERS);

				continue;
			}

			Debugger.debug("chat-forwarding", "Forwarded to " + online.getName() + " on server " + onlineServer.getName());
			online.sendMessage(SimpleComponent.fromAmpersand(message));
		}
	}

	/**
	 * Return true if the server alias is ignored
	 *
	 * @param serverAlias
	 * @return
	 */
	private static boolean isSilent(@NonNull final String serverAlias) {
		return ProxySettings.Messages.IGNORED_SERVERS.contains(serverAlias);
	}

	/**
	 * Return true if the server is ignored
	 *
	 * @param server
	 * @return
	 */
	private static boolean isSilent(@NonNull final FoundationServer server) {
		return ProxySettings.Messages.IGNORED_SERVERS.contains(server.getName());
	}

	/**
	 * Broadcast pending join or switch message for the given player.
	 *
	 * @param player
	 */
	public static void broadcastPendingMessage(@NonNull final FoundationPlayer player) {
		synchronized (pendingMessages) {
			final FoundationPlayer audience = Platform.toPlayer(player);
			final UUID playerUniqueId = player.getUniqueId();
			final String playerName = player.getName();

			final Tuple<PlayerMessageType, Map<String, Object>> data = pendingMessages.remove(playerUniqueId);

			if (data != null) {
				final PlayerMessageType type = data.getKey();
				final Map<String, Object> placeholders = data.getValue();
				final SyncedCache cache = SyncedCache.fromUniqueId(playerUniqueId);

				if (cache == null)
					throw new FoException("Unable to find synced data for " + playerName);

				if (!cache.isVanished() || player.hasPermission(Permissions.Bypass.VANISH)) {
					if (lastMessages.containsKey(playerUniqueId)) {
						Debugger.debug("player-message", "Not broadcasting " + type + " message for " + playerName + ", broadcasted recently, preventing duplicate.");

						return;
					}

					Debugger.debug("player-message", "Broadcast " + type + " message for " + playerName + " with variables " + placeholders);

					ProxyPlayerMessages.broadcast(type, audience, placeholders);
					lastMessages.put(playerUniqueId, type);

				} else
					Debugger.debug("player-message", "Failed sending " + type + " message for " + playerName + ", vanished ? " + cache.isVanished() + ", has bypass reach perm ? " + player.hasPermission(Permissions.Bypass.VANISH));
			}

			else
				Debugger.debug("player-message", "Failed finding pending join/switch message for " + playerName + ", pending message was null. All data: " + pendingMessages);
		}
	}
}
