package org.mineacademy.chatcontrol.model;

import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.model.CompChatColor;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents what mode the player is in the channel
 */
@RequiredArgsConstructor
public enum ChannelMode {

	/**
	 * Receive and send messages
	 */
	WRITE("write", CompChatColor.GOLD),

	/**
	 * Receive messages but not write them
	 */
	READ("read", CompChatColor.GREEN);

	/**
	 * The unobfuscated config key
	 */
	@Getter
	private final String key;

	/**
	 * The color associated with this mode
	 * Used in command channel listing
	 */
	@Getter
	private final CompChatColor color;

	/**
	 * Load the mode from the config key
	 *
	 * @param key
	 * @return
	 */
	public static ChannelMode fromKey(final String key) {
		for (final ChannelMode mode : values())
			if (mode.key.equalsIgnoreCase(key))
				return mode;

		throw new IllegalArgumentException("No such channel mode: " + key + ". Available: " + CommonCore.join(values(), ChannelMode::getKey));
	}

	@Override
	public String toString() {
		return this.key;
	}
}