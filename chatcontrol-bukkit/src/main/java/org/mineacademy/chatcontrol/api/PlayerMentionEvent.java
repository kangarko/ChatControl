package org.mineacademy.chatcontrol.api;

import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.fo.event.SimpleEvent;

import lombok.Getter;
import lombok.Setter;

/**
 * An event that is when a player is mentioned in chat.
 */
@Getter
@Setter
public final class PlayerMentionEvent extends SimpleEvent implements Cancellable {

	private static final HandlerList handlers = new HandlerList();

	/**
	 * The player that mentioned the other player
	 */
	private final CommandSender mentioner;

	/**
	 * The player that was mentioned
	 */
	private final SyncedCache mentioned;

	/**
	 * Is the event cancelled?
	 */
	private boolean cancelled;

	public PlayerMentionEvent(final CommandSender mentioner, final SyncedCache mentioned) {
		this.mentioner = mentioner;
		this.mentioned = mentioned;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}