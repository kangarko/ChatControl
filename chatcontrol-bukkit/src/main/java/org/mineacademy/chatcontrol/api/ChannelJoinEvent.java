package org.mineacademy.chatcontrol.api;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.ChannelMode;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.fo.event.SimpleEvent;

import lombok.Getter;
import lombok.Setter;

/**
 * An event that is executed when a player joins a channel.
 */
@Getter
public final class ChannelJoinEvent extends SimpleEvent implements Cancellable {

	private static final HandlerList handlers = new HandlerList();

	/**
	 * The cache of the Dude himself
	 */
	private final PlayerCache cache;

	/**
	 * The channel player is joining into
	 */
	private final Channel channel;

	/**
	 * The mode player is joining with
	 */
	private final ChannelMode mode;

	/**
	 * Enable joining?
	 */
	@Setter
	private boolean cancelled;

	public ChannelJoinEvent(final PlayerCache cache, final Channel channel, final ChannelMode mode) {
		this.cache = cache;
		this.channel = channel;
		this.mode = mode;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}