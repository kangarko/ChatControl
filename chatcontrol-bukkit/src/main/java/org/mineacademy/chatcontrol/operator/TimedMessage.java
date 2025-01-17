package org.mineacademy.chatcontrol.operator;

import org.mineacademy.chatcontrol.model.PlayerMessageType;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.model.Tuple;

/**
 * Represents join, leave, kick or timed message broadcast
 */
public final class TimedMessage extends PlayerMessage {

	/**
	 * @param group
	 */
	public TimedMessage(final String group) {
		super(PlayerMessageType.TIMED, group);
	}

	/**
	 * Return the broadcast specific delay or the default one
	 *
	 * @return
	 */
	@Override
	public Tuple<SimpleTime, String> getDelay() {
		return CommonCore.getOrDefault(super.getDelay(), new Tuple<>(Settings.Messages.TIMED_DELAY, null));
	}
}