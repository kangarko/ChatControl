package org.mineacademy.chatcontrol.api;

import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.mineacademy.chatcontrol.operator.Rule;
import org.mineacademy.fo.event.SimpleEvent;

import lombok.Getter;
import lombok.Setter;

/**
 * An event that is executed when a rule or a handler matches a message.
 * <p>
 * The event is fired before the rule edits the message in any way.
 */
@Getter
@Setter
public final class PreRuleMatchEvent extends SimpleEvent implements Cancellable {

	private static final HandlerList handlers = new HandlerList();

	/**
	 * The sender checked
	 */
	private final CommandSender sender;

	/**
	 * The full message
	 */
	private final String message;

	/**
	 * The matching rule
	 */
	private final Rule rule;

	/**
	 * Is the event cancelled?
	 */
	private boolean cancelled;

	public PreRuleMatchEvent(final CommandSender sender, final String message, final Rule rule) {
		this.sender = sender;
		this.message = message;
		this.rule = rule;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}