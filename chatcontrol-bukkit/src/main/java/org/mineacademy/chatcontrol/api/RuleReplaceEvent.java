package org.mineacademy.chatcontrol.api;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.mineacademy.chatcontrol.operator.RuleOperator;
import org.mineacademy.fo.event.SimpleEvent;

import lombok.Getter;
import lombok.Setter;

/**
 * An event that is executed when a rule replaces a message
 * either via "then replace" or "then rewrite (in)" operators
 */
@Getter
@Setter
public final class RuleReplaceEvent extends SimpleEvent implements Cancellable {

	private static final HandlerList handlers = new HandlerList();

	/**
	 * The sender checked
	 */
	private final CommandSender sender;

	/**
	 * The message being matched
	 */
	private final String message;

	/**
	 * The replaced match
	 */
	private String replacedMatch;

	/**
	 * The matching rule operator
	 */
	private final RuleOperator rule;

	/**
	 * Did the rule replace the entire message? True for "then rewrite",
	 * false for "then replace"
	 */
	private final boolean wholeMessage;

	/**
	 * Is the event cancelled?
	 */
	private boolean cancelled;

	public RuleReplaceEvent(final CommandSender sender, final String message, final String replacedMatch, final RuleOperator rule, final boolean wholeMessage) {
		super(!Bukkit.isPrimaryThread());

		this.sender = sender;
		this.message = message;
		this.replacedMatch = replacedMatch;
		this.rule = rule;
		this.wholeMessage = wholeMessage;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}