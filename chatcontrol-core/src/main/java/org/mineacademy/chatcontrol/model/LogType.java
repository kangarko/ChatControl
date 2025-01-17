package org.mineacademy.chatcontrol.model;

import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.settings.Lang;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents a log type
 */
@RequiredArgsConstructor
public enum LogType {

	/**
	 * This log is a chat message
	 */
	CHAT("chat"),

	/**
	 * This log is a command
	 */
	COMMAND("command"),

	/**
	 * This log is a private message
	 */
	PRIVATE_MESSAGE("private_message"),

	/**
	 * This log is a mail
	 */
	MAIL("mail"),

	/**
	 * This log is a sign
	 */
	SIGN("sign"),

	/**
	 * This log is book
	 */
	BOOK("book"),

	/**
	 * This log holds an itemus
	 */
	ANVIL("anvil");

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
		return Lang.plain("command-log-type-" + this.key.replace("_", "-"));
	}

	/**
	 * Attempt to load a log type from the given config key
	 *
	 * @param key
	 * @return
	 */
	public static LogType fromKey(final String key) {
		for (final LogType mode : values())
			if (mode.key.equalsIgnoreCase(key))
				return mode;

		throw new IllegalArgumentException("No such log type: " + key + ". Available: " + CommonCore.join(values()));
	}

	/**
	 * Returns {@link #getKey()}
	 */
	@Override
	public String toString() {
		return this.key;
	}
}