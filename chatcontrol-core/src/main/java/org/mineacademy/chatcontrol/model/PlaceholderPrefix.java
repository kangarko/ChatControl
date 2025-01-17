package org.mineacademy.chatcontrol.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Used to differentiate placeholders such as in private messages where
 * {player_name} is confusing, and we might want to use both {receiver_name} and {sender_name}
 */
@RequiredArgsConstructor
public enum PlaceholderPrefix {

	KILLER("killer"),
	PLAYER("player"),
	RECEIVER("receiver"),
	SENDER("sender"),
	TAGGED("tagged");

	@Getter
	private final String key;

	@Override
	public String toString() {
		return this.key;
	}
}
