package org.mineacademy.chatcontrol.model;

import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.settings.Lang;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ToggleType {

	/**
	 * Player has disabled seeing "/chc announce" announcements.
	 */
	ANNOUNCEMENT("announcement") {

		@Override
		public String getDescription() {
			return Lang.plain("command-spy-type-announcement");
		}
	},

	/**
	 * Player has disabled receiving /me sent by others.
	 */
	BROADCAST("broadcast") {

		@Override
		public String getDescription() {
			return Lang.plain("command-spy-type-broadcast");
		}
	},

	/**
	 * Player has disabled seeing all chat messages
	 */
	CHAT("chat") {

		@Override
		public String getDescription() {
			return Lang.plain("command-spy-type-chat");
		}
	},

	/**
	 * Player has disabled receiving all mail.
	 */
	MAIL("mail") {

		@Override
		public String getDescription() {
			return Lang.plain("command-spy-type-mail");
		}
	},

	/**
	 * Player has disabled receiving private messages from any player.
	 */
	PRIVATE_MESSAGE("private_message") {

		@Override
		public String getDescription() {
			return Lang.plain("command-spy-type-private-message");
		}
	},

	/**
	 * Player has disabled sound notifications
	 */
	SOUND_NOTIFY("sound_notify") {

		@Override
		public String getDescription() {
			return Lang.plain("command-spy-type-sound-notify");
		}
	};

	/**
	 * The localized key
	 */
	@Getter
	private final String key;

	/**
	 * Get the localized description
	 *
	 * @return
	 */
	public abstract String getDescription();

	/**
	 * Returns {@link #getKey()}
	 *
	 * @return
	 */
	@Override
	public String toString() {
		return this.key;
	}

	/**
	 * Return this enum key from the given config key
	 *
	 * @param key
	 * @return
	 */
	public static ToggleType fromKey(final String key) {

		// Backward compatible
		if (key.equalsIgnoreCase("me"))
			return BROADCAST;

		if (key.equalsIgnoreCase("pm"))
			return PRIVATE_MESSAGE;

		if (key.equalsIgnoreCase("soundnotify"))
			return SOUND_NOTIFY;

		for (final ToggleType party : values())
			if (party.key.equalsIgnoreCase(key))
				return party;

		throw new IllegalArgumentException("No such channel party: " + key + " Available: " + CommonCore.join(values()));
	}
}