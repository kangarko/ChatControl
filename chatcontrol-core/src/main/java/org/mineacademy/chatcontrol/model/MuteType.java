package org.mineacademy.chatcontrol.model;

import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.settings.Lang;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents a mute type
 */
@RequiredArgsConstructor
public enum MuteType {

	SERVER("server"),
	CHANNEL("channel"),
	PLAYER("player"),
	PROXY("proxy");

	/**
	 * The saveable non-obfuscated key
	 */
	@Getter
	private final String key;

	/**
	 * OMG this is localized again!
	 * @return
	 */
	public final String getLangKey() {
		return Lang.plain("command-mute-type-" + this.key.replace("_", "-"));
	}

	/**
	 * Attempt to load a log type from the given config key
	 *
	 * @param key
	 * @return
	 */
	public static MuteType fromKey(final String key) {
		for (final MuteType mode : values())
			if (mode.key.equalsIgnoreCase(key))
				return mode;

		throw new IllegalArgumentException("No such mute type: " + key + ". Available: " + CommonCore.join(values()));
	}

	/**
	 * Returns {@link #getKey()}
	 */
	@Override
	public String toString() {
		return this.key;
	}
}