package org.mineacademy.chatcontrol.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.operator.Tag;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.ProxyUtil;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.SimpleRunnable;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.remain.Remain;

/**
 * Responsible for proxy synchronization.
 */
public final class ProxyChat {

	/**
	 * The channel name
	 */
	public static final String CHANNEL_NAME = "plugin:chcred";

	/**
	 * Return the prefix for proxy.
	 *
	 * @return
	 */
	public static SimpleComponent getProxyPrefix() {
		return Prefix.getPrefix();
	}

	/**
	 * Reschedule the permissions task giving/taking newcomer permissions
	 */
	public static void scheduleTask() {
		Platform.runTaskTimerAsync(20 * 2, new SyncTask());
		Platform.runTaskTimerAsync(10, new PendingProxyJoinTask());

	}

	private static final class PendingProxyJoinTask extends SimpleRunnable {

		/**
		 * @see java.lang.Runnable#run()
		 */
		@Override
		public void run() {
			final Collection<? extends Player> players = Remain.getOnlinePlayers();

			if (Settings.Proxy.ENABLED) {
				final List<WrappedSender> pendingProxyJoins = new ArrayList<>();

				for (final Player online : players) {
					final SenderCache senderCache = SenderCache.from(online);

					if (senderCache.isDatabaseLoaded()) {
						final WrappedSender wrapped = WrappedSender.fromPlayerCaches(online, PlayerCache.fromCached(online), senderCache);

						if (senderCache.isPendingProxyJoinMessage()) {
							senderCache.setPendingProxyJoinMessage(false);

							pendingProxyJoins.add(wrapped);
						}
					}
				}

				for (final WrappedSender wrapped : pendingProxyJoins)
					ProxyUtil.sendPluginMessage(ChatControlProxyMessage.DATABASE_READY, wrapped.getName(), wrapped.getUniqueId(), ProxyChat.collect(wrapped));

			}
		}
	}

	/**
	 * Represents uploading data to proxy
	 */
	private static final class SyncTask extends SimpleRunnable {

		/**
		 * @see java.lang.Runnable#run()
		 */
		@Override
		public void run() {
			final Collection<? extends Player> players = Remain.getOnlinePlayers();

			if (Settings.Proxy.ENABLED) {
				final Map<SyncType, SerializedMap> syncTypeDataMap = new HashMap<>();

				for (final Player online : players) {
					final SenderCache senderCache = SenderCache.from(online);

					if (senderCache.isDatabaseLoaded()) {
						final WrappedSender wrapped = WrappedSender.fromPlayerCaches(online, PlayerCache.fromCached(online), senderCache);

						for (final SyncType syncType : SyncType.values())
							syncTypeDataMap.computeIfAbsent(syncType, key -> new SerializedMap()).put(online.getUniqueId().toString(), compileValue(syncType, wrapped));
					}
				}

				for (final Map.Entry<SyncType, SerializedMap> entry : syncTypeDataMap.entrySet()) {
					final SyncType syncType = entry.getKey();
					final SerializedMap uniqueIdsWithData = entry.getValue();

					ProxyUtil.sendPluginMessage(ChatControlProxyMessage.SYNCED_CACHE_BY_UUID, syncType.toString(), uniqueIdsWithData);
				}

			} else {
				final Set<UUID> onlinePlayerUniqueIds = new HashSet<>();

				for (final Player online : players) {
					final SenderCache senderCache = SenderCache.from(online);

					if (senderCache.isDatabaseLoaded()) {
						final WrappedSender wrapped = WrappedSender.fromPlayerCaches(online, PlayerCache.fromCached(online), senderCache);

						loadAllSyncedData(wrapped);
						onlinePlayerUniqueIds.add(wrapped.getUniqueId());
					}
				}

				SyncedCache.removeDisconnectedPlayers(onlinePlayerUniqueIds);
			}
		}
	}

	/**
	 * Load all synced data for the given player
	 * @param wrapped
	 */
	public static void loadAllSyncedData(WrappedSender wrapped) {
		final SyncedCache syncedCache = SyncedCache.getOrCreate(wrapped.getName(), wrapped.getUniqueId());

		for (final SyncType syncType : SyncType.values())
			syncedCache.loadData(syncType, compileValue(syncType, wrapped));
	}

	/**
	 * Collect all data for the given player
	 *
	 * @param wrapped
	 * @return
	 */
	private static SerializedMap collect(final WrappedSender wrapped) {
		final SerializedMap map = new SerializedMap();

		for (final SyncType syncType : SyncType.values())
			map.put(syncType.name(), compileValue(syncType, wrapped));

		return map;
	}

	/*
	 * Returns the value of this sync type for the given player
	 */
	private static String compileValue(final SyncType syncType, final WrappedSender wrapped) {
		final Player player = wrapped.getPlayer();
		final PlayerCache cache = wrapped.getPlayerCache();

		if (syncType == SyncType.SERVER)
			return Platform.getCustomServerName();

		else if (syncType == SyncType.AFK)
			return HookManager.isAfk(player) ? "1" : "0";

		else if (syncType == SyncType.CHANNELS)
			return cache.getChannels().entrySet()
					.stream()
					.map(entry -> entry.getKey().getName() + ":" + entry.getValue().ordinal())
					.collect(Collectors.joining("|"));

		else if (syncType == SyncType.IGNORE)
			return cache.getIgnoredPlayers().stream()
					.map(UUID::toString)
					.collect(Collectors.joining("|"));

		else if (syncType == SyncType.TOGGLED_OFF_PARTS)
			return cache.getToggledOffParts().stream().map(ToggleType::name).collect(Collectors.joining("|"));

		else if (syncType == SyncType.IGNORED_MESSAGES) {
			final SerializedMap map = new SerializedMap();

			for (final Map.Entry<PlayerMessageType, Set<String>> entry : cache.getIgnoredMessages().entrySet())
				map.put(entry.getKey().name(), Common.join(entry.getValue(), "|"));

			return map.toJson();
		}

		else if (syncType == SyncType.NICK_COLORED_PREFIXED) {
			if (Settings.Tag.APPLY_ON.contains(Tag.Type.NICK)) {
				final String nick = cache.getTag(Tag.Type.NICK);

				return nick == null ? "" : Settings.Tag.NICK_PREFIX + nick;
			}

			return Settings.Tag.BACKWARD_COMPATIBLE ? Common.getOrEmpty(HookManager.getNickOrNullColored(player)) : "";
		}

		else if (syncType == SyncType.NICK_COLORLESS) {
			if (Settings.Tag.APPLY_ON.contains(Tag.Type.NICK))
				return Common.getOrEmpty(cache.getTagColorless(Tag.Type.NICK));

			return Settings.Tag.BACKWARD_COMPATIBLE ? Common.getOrEmpty(HookManager.getNickOrNullColorless(player)) : "";
		}

		else if (syncType == SyncType.VANISH)
			return PlayerUtil.isVanished(player) ? "1" : "0";

		else if (syncType == SyncType.GROUP)
			return HookManager.getPlayerPrimaryGroup(player);

		else if (syncType == SyncType.PREFIX) {
			if (Settings.Tag.APPLY_ON.contains(Tag.Type.PREFIX)) {
				final String prefix = cache.getTag(Tag.Type.PREFIX);

				return prefix == null ? "" : prefix;
			}

			return HookManager.getPlayerPrefix(player);
		}

		else if (syncType == SyncType.SUFFIX) {
			if (Settings.Tag.APPLY_ON.contains(Tag.Type.SUFFIX)) {
				final String suffix = cache.getTag(Tag.Type.SUFFIX);

				return suffix == null ? "" : suffix;
			}

			return HookManager.getPlayerSuffix(player);
		}

		else if (syncType == SyncType.MUTE_BYPASS)
			try {
				return player.hasPermission(Permissions.Bypass.MUTE) ? "1" : "0";

			} catch (final Throwable t) {
				Common.logFramed(
						"Failed to ask if " + player.getName() + " has " + Permissions.Bypass.MUTE + " permission",
						"Read the stack trace below and report this issue",
						"to your permission plugin's developer.",
						"This is NOT a ChatControl bug.");

				t.printStackTrace();

				return "0";
			}
		else
			throw new FoException("Sync type: " + syncType + " not implemented!");
	}

	/**
	 * A helper class to prevent init errors
	 */
	private static class Prefix {
		private static SimpleComponent PREFIX;

		private static SimpleComponent getPrefix() {
			if (PREFIX == null)
				PREFIX = Settings.Proxy.PREFIX.isEmpty() ? SimpleComponent.empty() : SimpleComponent.fromMiniAmpersand(Settings.Proxy.PREFIX.replace("{server_name}", Platform.getCustomServerName()));

			return PREFIX;
		}
	}
}
