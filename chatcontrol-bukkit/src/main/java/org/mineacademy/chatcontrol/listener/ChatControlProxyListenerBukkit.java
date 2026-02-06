package org.mineacademy.chatcontrol.listener;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.Announce;
import org.mineacademy.chatcontrol.model.Announce.AnnounceType;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.ChatControlProxyMessage;
import org.mineacademy.chatcontrol.model.MuteType;
import org.mineacademy.chatcontrol.model.Packets;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.model.ProxyChat;
import org.mineacademy.chatcontrol.model.Spy;
import org.mineacademy.chatcontrol.model.SyncType;
import org.mineacademy.chatcontrol.model.ToggleType;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.model.db.ServerSettings;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.ReflectionUtil;
import org.mineacademy.fo.collection.ExpiringMap;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.CompToastStyle;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.SimpleSound;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.proxy.message.IncomingMessage;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.Remain;

import lombok.Getter;

/**
 * Represents the connection to proxy
 */
public final class ChatControlProxyListenerBukkit extends org.mineacademy.fo.proxy.ProxyListener {

	/**
	 * The singleton of this class
	 */
	@Getter
	private final static ChatControlProxyListenerBukkit instance = new ChatControlProxyListenerBukkit();

	/**
	 * Stores server name to alias map updated from upstream
	 */
	private final Map<String, String> serverNameAliases = new HashMap<>();

	/**
	 * Due to problematic RedisBungee implementation, some of our messages may
	 * get duplicated. We simply store them here and ignore same messages sent to players
	 * right after one another.
	 *
	 * Message : Timestamp
	 */
	private final Map<String, Long> redisDeduplicator = ExpiringMap.builder().expiration(30, TimeUnit.MINUTES).build();

	/*
	 * The presently read packet
	 */
	private ChatControlProxyMessage packet;

	/*
	 * The server the packet is coming from
	 */
	private String server;

	/*
	 * The sender of the packet
	 */
	private UUID senderUid;

	private ChatControlProxyListenerBukkit() {
		super(ProxyChat.CHANNEL_NAME, ChatControlProxyMessage.class);
	}

	@Override
	public void onMessageReceived(final IncomingMessage input) {
		this.packet = (ChatControlProxyMessage) input.getMessage();
		this.server = input.getServerName();
		this.senderUid = input.getSenderUid();

		if (!Settings.Proxy.ENABLED)
			return;

		if (this.packet != ChatControlProxyMessage.SYNCED_CACHE_BY_UUID && this.packet != ChatControlProxyMessage.SYNCED_CACHE_HEADER)
			Debugger.debug("proxy", "Received proxy packet " + this.packet + " from server " + this.server);

		if (this.packet == ChatControlProxyMessage.CHANNEL) {

			// Avoids processing proxy messages in the same server it was sent from, to avoid duplicate messages and unnecessary proxy prefix
			if (input.getServerName().equalsIgnoreCase(Platform.getCustomServerName())) {
				Debugger.debug("proxy", "Ignoring CHANNEL packet from own server " + input.getServerName());

				return;
			}

			final String channelName = input.readString();
			final String senderName = input.readString();
			final UUID senderUUID = input.readUUID();
			final SimpleComponent formattedMessage = input.readSimpleComponent();
			final String consoleLog = input.readString();
			final boolean hasMuteBypass = input.readBoolean();
			final boolean hasIgnoreBypass = input.readBoolean();
			final boolean hasLogBypass = input.readBoolean();

			final Channel channel = Channel.findChannel(channelName);

			if (!Settings.Channels.ENABLED) {
				Debugger.debug("proxy", "Ignoring CHANNEL packet from " + senderName + " because channels are disabled");

			} else if (channel == null) {
				Debugger.debug("proxy", "Ignoring CHANNEL packet from " + senderName + " because channel '" + channelName + "' was not found on this server");

			} else if (!channel.isProxy()) {
				Debugger.debug("proxy", "Ignoring CHANNEL packet from " + senderName + " because channel '" + channelName + "' does not have Proxy enabled");

			} else if (!this.canSendMessage(channelName + senderName + formattedMessage.toPlain(null))) {
				Debugger.debug("proxy", "Ignoring CHANNEL packet from " + senderName + " in channel '" + channelName + "' because a duplicate message was received within 500ms (deduplication)");

			} else {
				Debugger.debug("proxy", "Processing CHANNEL packet from " + senderName + " in channel '" + channelName + "' from server " + this.server);

				channel.processProxyMessage(senderName, senderUUID, formattedMessage, consoleLog, hasMuteBypass, hasIgnoreBypass, hasLogBypass);
			}
		}

		else if (this.packet == ChatControlProxyMessage.REMOVE_MESSAGE) {
			final String removeTypeName = input.readString();
			final UUID messageId = input.readUUID();

			if (HookManager.isProtocolLibLoaded()) {
				final Packets.RemoveMode removeMode = ReflectionUtil.lookupEnum(Packets.RemoveMode.class, removeTypeName);

				Packets.getInstance().removeMessage(removeMode, messageId);

			} else
				CommonCore.logTimed(3600, "Received a packet from proxy to remove a chat message. ProtocolLib is missing, ignoring. This message will not show for the next hour.");
		}

		else if (this.packet == ChatControlProxyMessage.SPY_UUID) {
			final Spy.Type type = input.readEnum(Spy.Type.class);
			final UUID senderUid = input.readUUID();
			final String channelName = input.readString();
			final boolean proxyMode = input.readBoolean();
			final SimpleComponent message = input.readSimpleComponent();
			final SimpleComponent format = input.readSimpleComponent();
			final Collection<UUID> ignoredPlayers = CommonCore.convertList(CommonCore.GSON.fromJson(input.readString(), List.class), UUID::fromString);
			final boolean deniedSilently = input.readBoolean();

			if (this.canSendMessage(this.senderUid.toString() + type + channelName + message.toPlain(null) + ignoredPlayers))
				Spy.broadcastFromProxy(type, senderUid, channelName, proxyMode, message, format, ignoredPlayers, deniedSilently);
		}

		else if (this.packet == ChatControlProxyMessage.TOAST) {
			final UUID receiverUid = input.readUUID();
			final ToggleType toggleType = input.readEnum(ToggleType.class);
			final String message = input.readString();
			final CompMaterial material = input.readEnum(CompMaterial.class);
			final Player receiver = Remain.getPlayerByUUID(receiverUid);
			final CompToastStyle style = input.readEnum(CompToastStyle.class);

			if (receiver != null && receiver.isOnline() && this.canSendMessage(this.senderUid + receiverUid.toString() + toggleType.getKey() + material.toString() + style.toString() + message))
				this.sendToast(receiver, toggleType, material, style, message);
		}

		else if (this.packet == ChatControlProxyMessage.SYNCED_CACHE_HEADER) {
			final SerializedMap namesAndUniqueIds = input.readMap();
			final Set<UUID> uniqueIds = new HashSet<>();

			for (final Map.Entry<String, Object> entry : namesAndUniqueIds.entrySet()) {
				final String playerName = entry.getKey();
				final UUID uniqueId = entry.getValue() instanceof UUID ? (UUID) entry.getValue() : UUID.fromString(entry.getValue().toString());

				SyncedCache.getOrCreate(playerName, uniqueId);
				uniqueIds.add(uniqueId);
			}

			// Prevent a desync bug
			for (final Player online : Remain.getOnlinePlayers())
				uniqueIds.add(online.getUniqueId());

			SyncedCache.removeDisconnectedPlayers(uniqueIds);
		}

		else if (this.packet == ChatControlProxyMessage.SYNCED_CACHE_BY_UUID) {
			final SyncType syncType = SyncType.valueOf(input.readString());
			final SerializedMap mergedData = input.readMap();

			SyncedCache.uploadClusterFromUids(syncType, mergedData);
		}

		else if (this.packet == ChatControlProxyMessage.DATABASE_UPDATE) {
			final String originServerName = input.readString();
			final UUID uniqueId = input.readUUID();
			final SerializedMap data = input.readMap();
			final SimpleComponent message = input.readSimpleComponent();

			final Player online = Remain.getPlayerByUUID(uniqueId);

			if (online != null) {
				if (!Platform.getCustomServerName().equals(originServerName)) {
					if (!PlayerCache.isCached(uniqueId))
						new PlayerCache(online.getName(), uniqueId).putToCacheMap();

					PlayerCache.fromCached(online).loadDataFromDataSectionOfMap(data);
				}

				if (!message.isEmpty())
					Messenger.info(online, message);
			}
		}

		else if (this.packet == ChatControlProxyMessage.TAG_UPDATE) {
			final String originServerName = input.readString();
			final UUID uniqueId = input.readUUID();
			final SerializedMap data = input.readMap();
			final SimpleComponent message = input.readSimpleComponent();
			final Player online = Remain.getPlayerByUUID(uniqueId);

			if (online != null && !Platform.getCustomServerName().equals(originServerName)) {
				final PlayerCache playerCache = PlayerCache.fromCached(online);
				final SenderCache senderCache = SenderCache.from(online);

				playerCache.loadDataFromDataSectionOfMap(data);

				Players.setTablistName(WrappedSender.fromPlayerCaches(online, playerCache, senderCache));

				if (!message.isEmpty())
					Messenger.info(online, message);
			}
		}

		else if (this.packet == ChatControlProxyMessage.REPLY_UPDATE) {
			final UUID targetUid = input.readUUID();
			final String replyPlayer = input.readString();
			/*final UUID replyUUID = */input.readUUID();

			// Update the /reply recipient for the player if he is online
			final Player target = Remain.getPlayerByUUID(targetUid);

			if (target != null && Settings.PrivateMessages.PROXY && PlayerCache.isCached(target))
				PlayerCache.fromCached(target).setReplyPlayerName(replyPlayer);
		}

		else if (this.packet == ChatControlProxyMessage.ANNOUNCEMENT) {
			final AnnounceType type = input.readEnum(AnnounceType.class);
			final String message = input.readString();
			final SerializedMap params = input.readMap();

			if (params.containsKey("server") && !params.getString("server").equals(Platform.getCustomServerName()))
				return;

			if (this.canSendMessage(this.senderUid + type.toString() + message + message))
				Announce.sendFromProxy(type, message, params);
		}

		else if (this.packet == ChatControlProxyMessage.FORWARD_COMMAND) {
			final String server = input.readString();
			final String command = input.readString();

			if (server.equalsIgnoreCase(Platform.getCustomServerName()) && this.canSendMessage(this.senderUid + server + command))
				Platform.dispatchConsoleCommand(null, command);
		}

		else if (this.packet == ChatControlProxyMessage.CLEAR_CHAT) {
			final SimpleComponent announceMessage = input.readSimpleComponent();
			final boolean forced = input.readBoolean();
			Players.clearChatFromProxy(announceMessage.isEmpty(), forced);

			if (!announceMessage.isEmpty() && this.canSendMessage(this.senderUid + announceMessage.toPlain(null)))
				Messenger.broadcastAnnounce(announceMessage);
		}

		else if (this.packet == ChatControlProxyMessage.ME) {
			final UUID senderUniqueId = input.readUUID();
			final boolean reachBypass = input.readBoolean();
			final SimpleComponent component = input.readSimpleComponent();

			if (this.canSendMessage(senderUniqueId.toString() + reachBypass + component.toPlain(null)))
				Players.showMe(senderUniqueId, reachBypass, component);
		}

		else if (this.packet == ChatControlProxyMessage.MOTD) {
			final UUID receiverUniqueId = input.readUUID();
			final Player receiver = Remain.getPlayerByUUID(receiverUniqueId);

			if (receiver != null)
				Players.showMotd(WrappedSender.fromPlayer(receiver), false);
		}

		else if (this.packet == ChatControlProxyMessage.NOTIFY) {
			final String permission = input.readString();
			final SimpleComponent component = input.readSimpleComponent();

			if (this.canSendMessage(this.senderUid + permission + component.toPlain(null)))
				CommonCore.broadcastWithPerm(permission, component);
		}

		else if (this.packet == ChatControlProxyMessage.BROADCAST) {
			final SimpleComponent message = input.readSimpleComponent();

			if (this.canSendMessage(this.senderUid + message.toPlain(null)))
				for (final Player online : Remain.getOnlinePlayers())
					Common.tell(online, message);
		}

		else if (this.packet == ChatControlProxyMessage.MESSAGE) {
			final UUID receiverUniqueId = input.readUUID();
			final SimpleComponent message = input.readSimpleComponent();

			final Player online = Remain.getPlayerByUUID(receiverUniqueId);

			if (online == null) {
				Debugger.debug("proxy", "Ignoring MESSAGE packet for receiver " + receiverUniqueId + " from server " + this.server + " because receiver is not online on this server");

			} else if (!this.canSendMessage(this.senderUid + receiverUniqueId.toString() + message.toPlain(null))) {
				Debugger.debug("proxy", "Ignoring MESSAGE packet for receiver " + receiverUniqueId + " from sender " + this.senderUid + " because a duplicate message was received within 500ms (deduplication)");

			} else {
				Debugger.debug("proxy", "Delivering MESSAGE packet to " + online.getName() + " from sender " + this.senderUid + " via server " + this.server);

				Common.tell(online, message);
			}
		}

		else if (this.packet == ChatControlProxyMessage.MUTE) {
			final MuteType type = input.readEnum(MuteType.class);
			final String targetName = input.readString();
			final String durationRaw = input.readString();
			final SimpleComponent announceMessage = input.readSimpleComponent();

			final boolean isOff = "off".equals(durationRaw);
			final SimpleTime duration = isOff ? null : SimpleTime.fromString(durationRaw);

			boolean canAnnounce = false;

			if (!this.canSendMessage(this.senderUid + type.getKey() + targetName + durationRaw + announceMessage.toPlain(null)))
				return;

			if (type == MuteType.CHANNEL) {
				final Channel channel = Channel.findChannel(targetName);

				if (channel != null) {
					channel.setMuted(duration);

					canAnnounce = true;
				}
			}

			else if (type == MuteType.SERVER) {
				// Server is not proxy supported

			} else if (type == MuteType.PROXY) {
				if (Settings.Proxy.ENABLED) {
					ServerSettings.getProxyOrOverload().setMuted(duration);

					canAnnounce = true;
				}

			} else
				throw new FoException("Unhandled mute packet type " + type);

			if (canAnnounce && !announceMessage.isEmpty())
				for (final Player online : Remain.getOnlinePlayers())
					Messenger.announce(online, announceMessage);
		}

		else if (this.packet == ChatControlProxyMessage.SOUND) {
			final UUID receiverUUID = input.readUUID();
			final SimpleSound sound = SimpleSound.fromString(input.readString());

			final Player receiver = Remain.getPlayerByUUID(receiverUUID);

			if (receiver != null && receiver.isOnline())
				sound.play(receiver);
		}

		else if (this.packet == ChatControlProxyMessage.SERVER_ALIAS) {
			final String serverName = input.readString();
			final String serverAlias = input.readString();

			this.serverNameAliases.put(serverName, serverAlias);
		}

		else
			throw new FoException("Unhandled packet from proxy: " + this.packet);
	}

	@Override
	public void onInvalidMessageReceived(UUID senderUid, String serverName, String actionName) {

		// Hide error if proxy is off
		if (Settings.Proxy.ENABLED)
			super.onInvalidMessageReceived(senderUid, serverName, actionName);
	}

	/*
	 * Return true if message can be sent, same messages can only
	 * be sent each 500ms
	 */
	private boolean canSendMessage(final String message) {
		final long timestamp = this.redisDeduplicator.getOrDefault(message, -1L);

		if (timestamp == -1 || System.currentTimeMillis() - timestamp > 500) {
			this.redisDeduplicator.put(message, System.currentTimeMillis());

			return true;
		}

		return false;
	}

	/*
	 * Sends a toast message to the player given he is not ignoring it nor the sender
	 */
	private void sendToast(final Player player, final ToggleType toggleType, final CompMaterial material, final CompToastStyle style, final String message) {
		final PlayerCache cache = PlayerCache.fromCached(player);

		if (!cache.hasToggledPartOff(toggleType) && !cache.isIgnoringPlayer(this.senderUid))
			Remain.sendToast(player, message, material, style);
	}

}