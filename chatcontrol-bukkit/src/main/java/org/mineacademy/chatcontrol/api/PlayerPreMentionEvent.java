package org.mineacademy.chatcontrol.api;

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
public final class PlayerPreMentionEvent extends SimpleEvent implements Cancellable {

	private static final HandlerList handlers = new HandlerList();

	/**
	 * The matched player
	 */
	private final SyncedCache player;

	/**
	 * The prefix look for when matching
	 */
	private String prefix;

	/**
	 * Is the event cancelled?
	 */
	private boolean cancelled;

	public PlayerPreMentionEvent(final SyncedCache player, final String prefix) {
		this.player = player;
		this.prefix = prefix;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}