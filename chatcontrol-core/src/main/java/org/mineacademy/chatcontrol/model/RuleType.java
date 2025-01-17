package org.mineacademy.chatcontrol.model;

import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.settings.Lang;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
	 * Represents a rule type
	 */
@RequiredArgsConstructor
public enum RuleType {

	/**
	 * Rules applied over everything
	 */
	GLOBAL("global", LogType.CHAT),

	/**
	 * Rule matching chat messages
	 */
	CHAT("chat", LogType.CHAT),

	/**
	 * Rule matching player commands
	 */
	COMMAND("command", LogType.COMMAND),

	/**
	 * Rule matching text on signs
	 */
	SIGN("sign", LogType.SIGN),

	/**
	 * Rule matching text in books and their titles
	 */
	BOOK("book", LogType.BOOK),

	/**
	 * Rule matching item names when renamed
	 */
	ANVIL("anvil", LogType.ANVIL),

	/**
	 * Rule matching player tags: nicks/prefix/suffix
	 */
	TAG("tag", LogType.COMMAND);

	/**
	 * The saveable non-obfuscated key
	 */
	@Getter
	private final String key;

	/**
	 * Get the log type or null if logging not supported
	 */
	@Getter
	private final LogType logType;

	/**
	 * Holy cow, we're international!
	 * Just kidding, Slovakia FTW.
	 *
	 * @return
	 */
	public final String getLangKey() {
		return Lang.plain("command-rule-type-" + this.key.replace("_", "-"));
	}

	/**
	 * Attempt to load a log type from the given config key
	 *
	 * @param key
	 * @return
	 */
	public static RuleType fromKey(final String key) {
		for (final RuleType mode : values())
			if (mode.key.equalsIgnoreCase(key) || mode.key.equalsIgnoreCase(key.substring(0, key.length() - 1)))
				return mode;

		throw new IllegalArgumentException("No such rule type: " + key + ". Available: " + CommonCore.join(values()));
	}

	/**
	 * Returns {@link #getKey()}
	 */
	@Override
	public String toString() {
		return this.key;
	}
}