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
}