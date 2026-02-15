package org.mineacademy.chatcontrol.proxy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.ChatControlProxyMessage;
import org.mineacademy.chatcontrol.model.SyncType;
import org.mineacademy.chatcontrol.proxy.settings.ProxySettings;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.ReflectionUtil;
import org.mineacademy.fo.annotation.AutoRegister;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.model.CompChatColor;
import org.mineacademy.fo.platform.FoundationPlayer;
import org.mineacademy.fo.platform.FoundationServer;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.proxy.ProxyListener;
import org.mineacademy.fo.proxy.message.IncomingMessage;
import org.mineacademy.fo.proxy.message.OutgoingMessage;

import com.imaginarycode.minecraft.redisbungee.AbstractRedisBungeeAPI;

import lombok.Getter;

/**
 * Represents our core packet handling that reads and forwards
 * packets from Spigot servers
 */
@AutoRegister(requirePlatform = { Platform.Type.VELOCITY, Platform.Type.BUNGEECORD })
public final class ChatControlProxyListenerProxy extends ProxyListener {

	/**
	 * The singleton of this class
	 */
	@Getter
	private final static ChatControlProxyListenerProxy instance = new ChatControlProxyListenerProxy();

	/**
	 * The pending mutes to be sent to servers once they are not empty
	 */
	private final Map<String, List<byte[]>> pendingMutes = new HashMap<>();

	/**
	 * The present senders UUID
	 */
	private UUID senderUid;

	/**
	 * The present server name
	 */
	private String serverNameRaw;
	private String serverAlias;

	/**
	 * The data that are being synced between servers
	 */
	private final Map<SyncType, SerializedMap> clusteredData = new HashMap<>();

	private ChatControlProxyListenerProxy() {
		super(ProxyConstants.CHATCONTROL_CHANNEL, ChatControlProxyMessage.class);
	}

	/**
	 * Reschedule data sync task
	 */
	public void scheduleSyncTask() {
		Platform.runTaskTimerAsync(20 * 2, () -> {
			synchronized (ProxyListener.DEFAULT_CHANNEL) {

				// Upload the always reliable player list from proxy (do not compile lists given by downstream
				final SerializedMap namesAndUniqueIds = new SerializedMap();
				final Set<UUID> onlineUniqueIds = new HashSet<>();

				if (Redis.isEnabled()) {
					final AbstractRedisBungeeAPI redis = AbstractRedisBungeeAPI.getAbstractRedisBungeeAPI();

					for (final UUID uniqueId : redis.getPlayersOnline()) {
						final String name = redis.getNameFromUuid(uniqueId);

						namesAndUniqueIds.put(name, uniqueId);
						onlineUniqueIds.add(uniqueId);

						SyncedCache.getOrCreate(name, uniqueId);
					}

				} else {
					for (final FoundationPlayer online : Platform.getOnlinePlayers()) {
						namesAndUniqueIds.put(online.getName(), online.getUniqueId());
						onlineUniqueIds.add(online.getUniqueId());

						SyncedCache.getOrCreate(online.getName(), online.getUniqueId());
					}
				}

				// Send header first
				final OutgoingMessage headerMessage = new OutgoingMessage(ChatControlProxyMessage.SYNCED_CACHE_HEADER);
				headerMessage.writeMap(namesAndUniqueIds);
				headerMessage.broadcast();

				// Cleanup last, otherwise clustered data might still include past players
				SyncedCache.removeDisconnectedPlayers(onlineUniqueIds);

				// Send specific data second (we cant send everyone under one go because on large servers this is above max packet size)
				for (final Map.Entry<SyncType, SerializedMap> entry : this.clusteredData.entrySet()) {

					final SyncType type = entry.getKey();
					final SerializedMap playerUniqueIdsAndValues = entry.getValue();

					// Clean up here too
					for (final Iterator<String> it = playerUniqueIdsAndValues.keySet().iterator(); it.hasNext();) {
						final UUID clusterPlayerUniqueId = UUID.fromString(it.next());

						if (!onlineUniqueIds.contains(clusterPlayerUniqueId))
							it.remove();
					}

					final OutgoingMessage message = new OutgoingMessage(ChatControlProxyMessage.SYNCED_CACHE_BY_UUID);

					message.writeString(type.toString());
					message.writeMap(playerUniqueIdsAndValues);
					message.broadcast();

					SyncedCache.uploadClusterFromUids(type, playerUniqueIdsAndValues);

					if (Redis.isEnabled())
						Redis.sendPlayerCacheData(type, playerUniqueIdsAndValues);
				}

				this.clusteredData.clear();
			}
		});
	}

	@Override
	public void onMessageReceived(final IncomingMessage message) {
		try {

			// Get the raw data
			final byte[] data = message.getData();

			// Read the first three values of the packet, these are always the same
			this.senderUid = message.getSenderUid();
			this.serverNameRaw = message.getServerName();
			this.serverAlias = ProxySettings.getServerNameAlias(message.getServerName());

			final ChatControlProxyMessage packet = (ChatControlProxyMessage) message.getMessage();

			if (packet != ChatControlProxyMessage.SYNCED_CACHE_BY_UUID && packet != ChatControlProxyMessage.SYNCED_CACHE_HEADER)
				Debugger.debug("proxy", "Incoming packet " + packet + " from " + this.serverAlias);

			if (packet == ChatControlProxyMessage.SYNCED_CACHE_BY_UUID) {
				final SyncType syncType = ReflectionUtil.lookupEnum(SyncType.class, message.readString());
				final SerializedMap dataMap = message.readMap();

				final SerializedMap oldData = this.clusteredData.getOrDefault(syncType, new SerializedMap());
				oldData.mergeFrom(dataMap);

				this.clusteredData.put(syncType, oldData);
			}

			else if (packet == ChatControlProxyMessage.FORWARD_COMMAND) {
				final String server = message.readString();
				final String command = CompChatColor.translateColorCodes(message.readString()
						.replace("{server}", this.serverAlias)
						.replace("{server_name}", this.serverAlias));

				if (ProxySettings.ENABLE_FORWARD_COMMAND) {
					if ("proxy".equals(server)) {
						Debugger.debug("proxy", "Executing forwarded command on proxy: " + command);

						if (Redis.isEnabled())
							Redis.dispatchCommand(command);

						else
							Platform.dispatchConsoleCommand(null, command);

					} else {
						Debugger.debug("proxy", "Forwarding command to server '" + server + "' from " + this.serverAlias + ": " + command);

						this.forwardData(packet, data, true);
					}
				} else
					Debugger.debug("proxy", "Ignoring forward command from " + this.serverAlias + " because Enable_Forward_Command is disabled in settings.yml");
			}

			else if (packet == ChatControlProxyMessage.DATABASE_READY) {
				final String playerName = message.readString();
				final UUID playerUniqueId = message.readUUID();
				final SerializedMap dataMap = message.readMap();

				final FoundationPlayer player = Platform.getPlayer(playerUniqueId);

				if (player != null) {
					final SyncedCache cache = SyncedCache.uploadAll(playerName, playerUniqueId, dataMap);

					// Database ready packet is only broadcasted when the player is loaded = logged in
					cache.setLoggedIn(true);

					ProxyEvents.broadcastPendingMessage(player);

				} else
					Debugger.debug("player-message", "Could not find player " + playerUniqueId + " for join message. No broadcast.");
			}

			else
				this.forwardData(packet, data, packet.includeSelfServer());

		} catch (final Throwable t) {
			CommonCore.error(t,
					"ERROR COMMUNICATING WITH PAPER SERVERS",
					"Ensure you are running latest version of",
					"VelocityControl and the supporting plugins!",
					"",
					"Server: " + this.serverAlias,
					"Error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
		}
	}

	/**
	 * Send the pending mutes to the given server
	 *
	 * @param serverName
	 */
	public void sendPendingMutes(String serverName) {
		synchronized (pendingMutes) {
			final List<byte[]> datas = pendingMutes.remove(serverName);

			if (datas != null)
				for (final byte[] data : datas)
					this.forwardData(ChatControlProxyMessage.MUTE, data, false);
		}
	}

	/*
	 * Forward the given data with optional sender unique ID to all other servers
	 * or Redis
	 */
	private void forwardData(final ChatControlProxyMessage message, final byte[] data, final boolean forceSelf) {
		if (data.length > 32_000) { // Safety margin
			CommonCore.log("[forwardData-listener] Outgoing proxy message was oversized, not sending. Max length: 32766 bytes, got " + data.length + " bytes.");

			return;
		}

		final String fromCluster = ProxySettings.Clusters.getFromServerName(this.serverNameRaw, this.serverAlias);

		if (Redis.isEnabled()) {
			Debugger.debug("proxy", "Forwarding " + message + " via Redis for sender " + this.senderUid + " from " + this.serverAlias + " (" + data.length + " bytes)");

			Redis.sendDataToOtherServers(this.senderUid, ProxyConstants.CHATCONTROL_CHANNEL, data);

		} else {
			Debugger.debug("proxy", "About to forward " + message + " to servers: " + CommonCore.simplify(Platform.getServers()));

			for (final FoundationServer iteratedServer : Platform.getServers()) {
				final String iteratedName = iteratedServer.getName();
				final String iteratedAlias = ProxySettings.getServerNameAlias(iteratedName);
				final String iteratedCluster = ProxySettings.Clusters.getFromServerName(iteratedName, iteratedAlias);

				if (iteratedServer.isEmpty()) {
					Debugger.debug("proxy", "\tDid not send to '" + iteratedName + "', the server is empty");

					if (message == ChatControlProxyMessage.MUTE)
						synchronized (pendingMutes) {
							pendingMutes.computeIfAbsent(iteratedName, key -> new ArrayList<>()).add(data);
						}

					continue;
				}

				if (!forceSelf && !iteratedCluster.equals(fromCluster)) {
					Debugger.debug("proxy", "\tDid not send to '" + iteratedName + "', the server has different cluster (" + iteratedCluster + " != " + fromCluster + ")");

					continue;
				}

				if (!forceSelf && iteratedServer.getName().equals(this.serverNameRaw)) {
					Debugger.debug("proxy", "\tDid not send to '" + iteratedName + "', the server equals sender");

					continue;
				}

				Debugger.debug("proxy", "\tForwarded to '" + iteratedName + "'");
				iteratedServer.sendData(ProxyConstants.BUNGEECORD_CHANNEL, data);
			}
		}
	}
}