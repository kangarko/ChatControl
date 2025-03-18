package org.mineacademy.chatcontrol;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.mineacademy.chatcontrol.model.ChannelMode;
import org.mineacademy.chatcontrol.model.PlaceholderPrefix;
import org.mineacademy.chatcontrol.model.PlayerMessageType;
import org.mineacademy.chatcontrol.model.SyncType;
import org.mineacademy.chatcontrol.model.ToggleType;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.SerializeUtilCore.Language;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.platform.FoundationPlayer;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.settings.Lang;

import lombok.Getter;

/**
 * Represents a cache with data from proxy
 */
public final class SyncedCache {

	/**
	 * The internal map
	 * Name : Data
	 */
	private static Map<UUID, SyncedCache> uniqueCacheMap = new HashMap<>();

	/**
	 * The player name
	 */
	@Getter
	private final String playerName;

	/**
	 * The unique ID
	 */
	@Getter
	private final UUID uniqueId;

	/**
	 * The server where this player is on
	 */
	@Getter
	private String serverName = "";

	/**
	 * His nick if any
	 */
	private String nickColoredPrefixed;

	/**
	 * His nick if any
	 */
	private String nickColorless;

	/**
	 * Is the player vanished?
	 */
	private boolean vanished;

	/**
	 * Is the player a fucking drunk?
	 */
	private boolean afk;

	/**
	 * The plugin parts the player has toggled off.
	 */
	private final Set<ToggleType> toggledOffParts = new HashSet<>();

	/**
	 * Set of timed message broadcast groups this player is not receiving
	 */
	private final Map<PlayerMessageType, Set<String>> ignoredMessages = new HashMap<>();

	/**
	 * List of ignored dudes
	 */
	private final Set<UUID> ignoredPlayers = new HashSet<>();

	/**
	 * Map of channel names and modes this synced man is in
	 */
	@Getter
	private final Map<String, ChannelMode> channels = new HashMap<>();

	/**
	 * The player prefix from Vault
	 */
	@Getter
	private String prefix;

	/**
	 * The player suffix from Vault
	 */
	@Getter
	private String suffix;

	/**
	 * The player group from Vault
	 */
	@Getter
	private String group;

	/**
	 * Bypasses mute?
	 */
	@Getter
	private boolean hasMuteBypass;

	/*
	 * Create a synced cache from the given data map
	 */
	private SyncedCache(final String playerName, final UUID uniqueId) {
		this.playerName = playerName;
		this.uniqueId = uniqueId;
	}

	/**
	 * Load the given data into this cache
	 *
	 * @param syncType
	 * @param value
	 */
	public void loadData(final SyncType syncType, final String value) {
		if (syncType == SyncType.SERVER)
			this.serverName = value;

		else if (syncType == SyncType.NICK_COLORED_PREFIXED)
			this.nickColoredPrefixed = value.isEmpty() ? null : value;

		else if (syncType == SyncType.NICK_COLORLESS)
			this.nickColorless = value.isEmpty() ? null : value;

		else if (syncType == SyncType.VANISH)
			this.vanished = value.equals("1");

		else if (syncType == SyncType.AFK)
			this.afk = value.equals("1");

		else if (syncType == SyncType.TOGGLED_OFF_PARTS) {
			this.toggledOffParts.clear();

			if (!value.isEmpty())
				for (final String name : value.split("\\|"))
					this.toggledOffParts.add(ToggleType.valueOf(name));

		} else if (syncType == SyncType.IGNORED_MESSAGES) {
			this.ignoredMessages.clear();

			if (!value.isEmpty()) {
				final SerializedMap map = SerializedMap.fromObject(Language.JSON, value.toString());

				for (final Map.Entry<String, Object> entry : map.entrySet()) {
					final PlayerMessageType type = PlayerMessageType.valueOf(entry.getKey());
					final Set<String> groups = CommonCore.newSet(entry.getValue().toString().split("\\|"));

					this.ignoredMessages.put(type, groups);
				}
			}

		} else if (syncType == SyncType.IGNORE) {
			this.ignoredPlayers.clear();

			if (!value.isEmpty())
				for (final String rawUUID : value.split("\\|"))
					try {
						this.ignoredPlayers.add(UUID.fromString(rawUUID));

					} catch (final Throwable t) {
						CommonCore.warning("Failed to load ignored player from UUID: " + rawUUID);
					}
		}

		else if (syncType == SyncType.CHANNELS) {
			this.channels.clear();

			if (!value.isEmpty())
				for (final String channelWithMode : value.split("\\|")) {
					final String[] parts = channelWithMode.split("\\:");

					try {
						final int modeOrdinal = Integer.parseInt(parts[1]);

						this.channels.put(parts[0], ChannelMode.values()[modeOrdinal]);

					} catch (final Throwable t) {
					}
				}
		}

		else if (syncType == SyncType.GROUP)
			this.group = value;

		else if (syncType == SyncType.PREFIX)
			this.prefix = value;

		else if (syncType == SyncType.SUFFIX)
			this.suffix = value;

		else if (syncType == SyncType.MUTE_BYPASS)
			this.hasMuteBypass = value.equals("1");
	}

	/**
	 * Convert this cache to a map
	 *
	 * @return
	 */
	public SerializedMap toMap() {
		final SerializedMap map = new SerializedMap();

		for (final SyncType type : SyncType.values()) {
			Object value = null;

			if (type == SyncType.SERVER)
				value = this.serverName;

			else if (type == SyncType.NICK_COLORED_PREFIXED)
				value = this.nickColoredPrefixed;

			else if (type == SyncType.NICK_COLORLESS)
				value = this.nickColorless;

			else if (type == SyncType.VANISH)
				value = this.vanished ? "1" : "0";

			else if (type == SyncType.AFK)
				value = this.afk ? "1" : "0";

			else if (type == SyncType.TOGGLED_OFF_PARTS)
				value = CommonCore.join(this.toggledOffParts);

			else if (type == SyncType.IGNORED_MESSAGES) {
				final SerializedMap map2 = new SerializedMap();

				for (final Map.Entry<PlayerMessageType, Set<String>> entry : this.ignoredMessages.entrySet())
					map2.put(entry.getKey().name(), CommonCore.join(entry.getValue(), "|"));

				value = map2;

			} else if (type == SyncType.IGNORE)
				value = CommonCore.join(this.ignoredPlayers, "|");

			else if (type == SyncType.CHANNELS) {
				final StringBuilder builder = new StringBuilder();

				for (final Map.Entry<String, ChannelMode> entry : this.channels.entrySet())
					builder.append(entry.getKey()).append(":").append(entry.getValue().ordinal()).append("|");

				value = builder.toString();

			} else if (type == SyncType.GROUP)
				value = this.group;

			else if (type == SyncType.PREFIX)
				value = this.prefix;

			else if (type == SyncType.SUFFIX)
				value = this.suffix;

			else if (type == SyncType.MUTE_BYPASS)
				value = this.hasMuteBypass ? "1" : "0";

			else
				throw new FoException("Unknown SyncType: " + type);

			map.putIfExists(type.toString(), value);
		}

		return map;
	}

	/**
	 * Return a dude's name or nick if set
	 *
	 * @return
	 */
	public String getNameOrNickColorless() {
		return CommonCore.getOrDefaultStrict(this.nickColorless, this.playerName);
	}

	/**
	 * Return a dude's name or nick if set
	 *
	 * @return
	 */
	public String getNameOrNickColoredPrefixed() {
		return CommonCore.getOrDefaultStrict(this.nickColoredPrefixed, this.playerName);
	}

	/**
	 * Return nick or null if not exists
	 *
	 * @return
	 */
	public String getNickColorlessOrNull() {
		return this.nickColorless;
	}

	/**
	 * Return true if this dude is ignoring the other dude's unique id
	 * Females not supported
	 *
	 * @param uniqueId
	 * @return
	 */
	public boolean isIgnoringPlayer(final UUID uniqueId) {
		return this.ignoredPlayers.contains(uniqueId);
	}

	/**
	 * Return true if this dude is ignoring the given fucking part.
	 *
	 * @param type
	 * @return
	 */
	public boolean hasToggledPartOff(final ToggleType type) {
		return this.toggledOffParts.contains(type);
	}

	/**
	 * Return if this player is ignoring the given broadcast from the given type
	 *
	 * @param type
	 * @param group
	 * @return
	 */
	public boolean isIgnoringMessage(final PlayerMessageType type, final String group) {
		final Set<String> messages = this.ignoredMessages.getOrDefault(type, new HashSet<>());

		return messages.contains("*") || messages.contains(group);
	}

	/**
	 * Return the channel mode if player is in the given channel else null
	 *
	 * @param channelName
	 * @return
	 */
	public ChannelMode getChannelMode(final String channelName) {
		return this.channels.get(channelName);
	}

	/**
	 * Is vanished?
	 *
	 * @return
	 */
	public boolean isVanished() {
		return this.vanished;
	}

	/**
	 * Is afk?
	 *
	 * @return
	 */
	public boolean isAfk() {
		return this.afk;
	}

	/**
	 * Return true if this player has a mute bypass
	 *
	 * @return
	 */
	public boolean hasMuteBypass() {
		return this.hasMuteBypass;
	}

	/**
	 * Return the local player if available.
	 *
	 * If you call this on Bukkit and the player is on a different server, this will return null.
	 *
	 * @return
	 */
	public FoundationPlayer toPlayer() {
		return Platform.getPlayer(this.uniqueId);
	}

	/**
	 * Get all cache variables
	 *
	 * @param prefix the prefix such as "player" for {player_name}
	 *
	 * @return
	 */
	public Map<String, Object> getPlaceholders(final PlaceholderPrefix prefix) {
		return getPlaceholders(this, prefix, this.playerName);
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "SyncedCache{" + this.playerName + ", data=" + this.toMap() + "}";
	}

	// ------------------------------------------------------------------------------------------------------------
	// Static
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Return true if the given player is connected and synced on proxy
	 *
	 * @param uniqueId
	 * @return
	 */
	public static boolean isPlayerConnected(final UUID uniqueId) {
		synchronized (uniqueCacheMap) {
			return uniqueCacheMap.containsKey(uniqueId);
		}
	}

	/**
	 * Return true if the given player name is connected and synced on proxy
	 *
	 * @param name
	 * @return
	 */
	public static boolean isPlayerNameConnected(final String name) {
		synchronized (uniqueCacheMap) {
			for (final SyncedCache cache : uniqueCacheMap.values())
				if (cache.getPlayerName().equalsIgnoreCase(name))
					return true;

			return false;
		}
	}

	/**
	 * Get or create a cache for the given player
	 *
	 * @param playerName
	 * @param uniqueId
	 * @return
	 */
	public static SyncedCache getOrCreate(final String playerName, final UUID uniqueId) {
		synchronized (uniqueCacheMap) {
			SyncedCache cache = uniqueCacheMap.get(uniqueId);

			if (cache == null) {
				cache = new SyncedCache(playerName, uniqueId);

				uniqueCacheMap.put(uniqueId, cache);
			}

			return cache;
		}
	}

	/**
	 * Get or create a cache for the given player
	 *
	 * @return
	 */
	public static SyncedCache fromConsole() {
		return new SyncedCache(Lang.plain("part-console"), CommonCore.ZERO_UUID);
	}

	/**
	 * Return true should the given server name match a valid server we know of...
	 *
	 * @param serverName
	 * @return
	 */
	public static boolean doesServerExist(final String serverName) {
		synchronized (uniqueCacheMap) {
			for (final SyncedCache cache : uniqueCacheMap.values())
				if (cache.getServerName().equalsIgnoreCase(serverName))
					return true;

			return false;
		}
	}

	/**
	 * Resolve a synced cache from the given player
	 * Returns null if not found
	 *
	 * @param playerName
	 * @return
	 */
	public static SyncedCache fromPlayerName(final String playerName) {
		synchronized (uniqueCacheMap) {
			for (final SyncedCache cache : uniqueCacheMap.values())
				if (cache.getPlayerName().equalsIgnoreCase(playerName))
					return cache;

			return null;
		}
	}

	/**
	 * Resolve a synced cache from the given player
	 * Returns null if not found
	 *
	 * @param uniqueId
	 * @return
	 */
	public static SyncedCache fromUniqueId(final UUID uniqueId) {
		synchronized (uniqueCacheMap) {
			return uniqueCacheMap.get(uniqueId);
		}
	}

	/**
	 * Return the synced cache (or null) from the player name or nick
	 * Returns null if not found
	 *
	 * @param nick
	 *
	 * @return
	 */
	public static SyncedCache fromNickColorless(String nick) {
		synchronized (uniqueCacheMap) {
			nick = nick.toLowerCase();

			for (final SyncedCache cache : uniqueCacheMap.values()) {
				if (cache.getPlayerName().equalsIgnoreCase(nick) || cache.getNickColorlessOrNull() != null && cache.getNickColorlessOrNull().toLowerCase().equals(nick))
					return cache;
			}

			return null;
		}
	}

	/**
	 * Return a set of all known servers on proxy where players are on
	 *
	 * @return
	 */
	public static Set<String> getServers() {
		synchronized (uniqueCacheMap) {
			final Set<String> servers = new HashSet<>();

			for (final SyncedCache cache : uniqueCacheMap.values())
				servers.add(cache.getServerName());

			return servers;
		}
	}

	/**
	 * Return all caches stored in memory
	 *
	 * @return
	 */
	public static Collection<SyncedCache> getCaches() {
		synchronized (uniqueCacheMap) {
			return Collections.unmodifiableCollection(uniqueCacheMap.values());
		}
	}

	/**
	 * Return a list of all network player names and their nicks
	 *
	 * @return
	 */
	public static Set<String> getNamesAndNicks() {
		synchronized (uniqueCacheMap) {
			final Set<String> names = new HashSet<>();

			for (final SyncedCache cache : uniqueCacheMap.values()) {
				final String name = cache.getPlayerName();
				final String nick = cache.getNameOrNickColorless();

				names.add(name);

				if (!nick.equals(name))
					names.add(nick);
			}

			return names;
		}
	}

	/**
	 * Return a list of all network player names
	 *
	 * @return
	 */
	public static Set<String> getNames() {
		synchronized (uniqueCacheMap) {
			final Set<String> names = new HashSet<>();

			for (final SyncedCache cache : uniqueCacheMap.values())
				names.add(cache.getPlayerName());

			return names;
		}
	}

	/**
	 * Uploads the given type of data for the given sync type
	 *
	 * @param type
	 * @param playerNamesAndValues
	 */
	public static void uploadClusterFromUids(final SyncType type, final SerializedMap playerNamesAndValues) {
		synchronized (uniqueCacheMap) {
			for (final Map.Entry<String, Object> entry : playerNamesAndValues.entrySet()) {
				final UUID uniqueId = UUID.fromString(entry.getKey());
				final String value = entry.getValue().toString();

				final SyncedCache cache = uniqueCacheMap.get(uniqueId);

				if (cache != null)
					cache.loadData(type, value);
			}
		}
	}

	/**
	 * Uploads the given type of data to the given player
	 *
	 * @param playerName
	 * @param uniqueId
	 * @param cachesByType
	 */
	public static void uploadAll(final String playerName, final UUID uniqueId, final SerializedMap cachesByType) {
		synchronized (uniqueCacheMap) {
			SyncedCache cache = uniqueCacheMap.get(uniqueId);

			if (cache == null) {
				cache = new SyncedCache(playerName, uniqueId);

				uniqueCacheMap.put(uniqueId, cache);
			}

			for (final Map.Entry<String, Object> entry : cachesByType.entrySet()) {
				final SyncType type = SyncType.valueOf(entry.getKey());
				final String value = entry.getValue().toString();

				cache.loadData(type, value);
			}
		}
	}

	/**
	 * Remove disconnected players from the cache
	 *
	 * @param newUniqueIds
	 */
	public static void removeDisconnectedPlayers(final Collection<UUID> newUniqueIds) {
		synchronized (uniqueCacheMap) {
			for (final Iterator<Entry<UUID, SyncedCache>> it = uniqueCacheMap.entrySet().iterator(); it.hasNext();) {
				final Entry<UUID, SyncedCache> entry = it.next();
				final UUID uniqueId = entry.getKey();

				if (!newUniqueIds.contains(uniqueId))
					it.remove();
			}
		}
	}

	/**
	 * Get all cache variables for the given player name
	 *
	 * @param audience
	 * @param prefix the prefix such as "player" for {player_name}
	 *
	 * @return
	 */
	public static Map<String, Object> getPlaceholders(final FoundationPlayer audience, final PlaceholderPrefix prefix) {
		synchronized (uniqueCacheMap) {
			final SyncedCache cache = uniqueCacheMap.get(audience.getUniqueId());

			return getPlaceholders(cache, prefix, audience.getName());
		}
	}

	/**
	 * Get all cache variables for the given player name
	 *
	 * @param fallbackName
	 * @param uniqueId can be null
	 * @param prefix the prefix such as "player" for {player_name}
	 * @return
	 */
	public static Map<String, Object> getPlaceholders(final String fallbackName, final UUID uniqueId, final PlaceholderPrefix prefix) {
		synchronized (uniqueCacheMap) {
			final SyncedCache cache = uniqueId != null ? uniqueCacheMap.get(uniqueId) : null;

			return getPlaceholders(cache, prefix, fallbackName);
		}
	}

	/*
	 * Get all cache variables
	 */
	private static Map<String, Object> getPlaceholders(final SyncedCache cache, final PlaceholderPrefix prefix, final String fallbackName) {
		final String name = cache != null ? cache.getPlayerName() : fallbackName;
		final String nick = cache != null ? cache.getNameOrNickColoredPrefixed() : fallbackName;

		final Map<String, Object> variables = CommonCore.newHashMap(
				prefix + "_name", name,
				prefix + "_nick", nick,
				prefix + "_group", cache != null ? cache.getGroup() : "",
				prefix + "_prefix", cache != null ? cache.getPrefix() : "",
				prefix + "_suffix", cache != null ? cache.getSuffix() : "",
				prefix + "_server", cache != null ? cache.getServerName() : "",
				prefix + "_channels", cache == null || cache.getChannels().isEmpty() ? Lang.plain("part-none") : CommonCore.join(CommonCore.newSet(cache.getChannels().keySet())), // Clone to prevent CME
				prefix + "_is_afk", cache != null && cache.isAfk() ? "true" : "false",
				prefix + "_is_vanished", cache != null && cache.isVanished() ? "true" : "false");

		for (final ToggleType part : ToggleType.values()) {
			final String value = cache != null && cache.hasToggledPartOff(part) ? "true" : "false";

			variables.put(prefix + "_is_ignoring_" + part.name().toLowerCase(), value);
			variables.put(prefix + "_is_ignoring_" + part.name().toLowerCase() + "s", value);
		}

		return variables;
	}
}
