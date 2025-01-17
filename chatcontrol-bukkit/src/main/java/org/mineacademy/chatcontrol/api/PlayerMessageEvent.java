package org.mineacademy.chatcontrol.api;

import javax.annotation.Nullable;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.mineacademy.chatcontrol.model.PlayerMessageType;
import org.mineacademy.chatcontrol.operator.Operator.OperatorCheck;
import org.mineacademy.fo.event.SimpleEvent;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * An event that is executed when a player joins a channel.
 */
@Getter
@RequiredArgsConstructor
public final class PlayerMessageEvent extends SimpleEvent implements Cancellable {

	private static final HandlerList handlers = new HandlerList();

	/**
	 * The Dude himself, null if timed or demo run
	 */
	@Nullable
	private final Player player;

	/**
	 * The channel player is joining into
	 */
	private final PlayerMessageType type;

	/**
	 * The associated check with this
	 */
	private final OperatorCheck<?> check;

	/**
	 * The original message if any, can be null
	 * For example the death event has its own native MC messages returned here
	 */
	@Nullable
	private final String originalMessage;

	/**
	 * Enable joining?
	 */
	@Setter
	private boolean cancelled;

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}