package org.mineacademy.chatcontrol.operator;

import org.mineacademy.chatcontrol.model.PlayerMessageType;

/**
 * Represents join, leave, kick or timed message broadcast
 */
public final class JoinQuitKickMessage extends PlayerMessage {

	/**
	 * Create a new broadcast by name
	 *
	 * @param type
	 * @param group
	 */
	public JoinQuitKickMessage(final PlayerMessageType type, final String group) {
		super(type, group);
	}
}
