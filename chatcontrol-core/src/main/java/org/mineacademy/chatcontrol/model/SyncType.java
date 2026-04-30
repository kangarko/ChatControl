package org.mineacademy.chatcontrol.model;

/**
 * Represents the type of data that can be synchronized between servers.
 */
public enum SyncType {
	AFK,
	CHANNELS,
	GROUP,
	IGNORE,
	TOGGLED_OFF_PARTS,
	IGNORED_MESSAGES,
	NICK_COLORED_PREFIXED,
	NICK_COLORLESS,
	PREFIX,
	SERVER,
	SUFFIX,
	VANISH,
	MUTE_BYPASS;

	/**
	 * Cached enum values. {@link #values()} clones the backing array on every call,
	 * which shows up on timings in hot proxy-sync loops.
	 * 
	 * See https://dzone.com/articles/memory-hogging-enumvalues-method
	 */
	public static final SyncType[] VALUES = values();
}