package org.mineacademy.chatcontrol.proxy.operator;

import org.mineacademy.chatcontrol.model.PlayerMessageType;

/**
 * Represents join, leave, kick or timed message broadcast
 */
public final class ProxyJoinQuitKickMessage extends ProxyPlayerMessage {

	/**
	 * Create a new broadcast by name
	 *
	 * @param type
	 * @param group
	 */
	public ProxyJoinQuitKickMessage(final PlayerMessageType type, final String group) {
		super(type, group);
	}
}
