package org.mineacademy.chatcontrol.api;

import java.util.Set;

import javax.annotation.Nullable;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.mineacademy.chatcontrol.model.Spy;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.fo.event.SimpleEvent;
import org.mineacademy.fo.model.SimpleComponent;

import lombok.Getter;
import lombok.Setter;

/**
 * Event for when spying players receive a /spy message.
 */
@Getter
@Setter
public final class SpyEvent extends SimpleEvent implements Cancellable {

	private static final HandlerList handlers = new HandlerList();

	/**
	 * The spy type
	 */
	private final Spy.Type type;

	/**
	 * The player who issued the message, may be null if coming from proxy for example
	 */
	@Nullable
	private final WrappedSender sender;

	/**
	 * A list of players receiving this message
	 */
	private final Set<Player> recipients;

	/**
	 * The message
	 */
	private final SimpleComponent message;

	/**
	 * Is the event cancelled?
	 */
	private boolean cancelled;

	public SpyEvent(final Spy.Type type, final WrappedSender sender, final SimpleComponent message, final Set<Player> recipients) {
		this.type = type;
		this.sender = sender;
		this.message = message;
		this.recipients = recipients;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}