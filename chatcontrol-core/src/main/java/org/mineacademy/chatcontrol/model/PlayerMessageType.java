package org.mineacademy.chatcontrol.model;

import java.util.Set;

import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.settings.Lang;

import lombok.Getter;
import lombok.NonNull;

/**
 * Represents a player message type.
 */
@Getter
public enum PlayerMessageType {

	JOIN("join", Platform.Type.values()),
	QUIT("quit", Platform.Type.values()),
	KICK("kick", Platform.Type.values()),
	DEATH("death", Platform.Type.BUKKIT),
	TIMED("timed", Platform.Type.BUKKIT),
	SWITCH("switch", Platform.Type.proxies());

	/**
	 * The saveable non-obfuscated key
	 */
	private final String key;

	/**
	 * The supported platform
	 */
	private final Set<Platform.Type> platform;

	/**
	 * Create a new player message type
	 *
	 * @param key
	 */
	PlayerMessageType(final String key, @NonNull final Platform.Type... platform) {
		this(key, CommonCore.newSet(platform));
	}

	/**
	 * Create a new player message type
	 *
	 * @param key
	 */
	PlayerMessageType(final String key, @NonNull final Set<Platform.Type> platform) {
		this.key = key;
		this.platform = platform;
	}

	/**
	 * Yummy dummy localized key from lang
	 *
	 * @return
	 */
	public final String getToggleLangKey() {
		return Lang.plain("command-toggle-type-" + this.key);
	}

	/**
	 * Attempt to load a log type from the given config key
	 *
	 * @param key
	 * @return
	 */
	public static PlayerMessageType fromKey(final String key) {
		for (final PlayerMessageType mode : values())
			if (mode.key.equalsIgnoreCase(key))
				return mode;

		throw new IllegalArgumentException("No such message type: " + key + ". Available: " + CommonCore.join(values()));
	}

	/**
	 * Returns {@link #getKey()}
	 */
	@Override
	public String toString() {
		return this.key;
	}
}