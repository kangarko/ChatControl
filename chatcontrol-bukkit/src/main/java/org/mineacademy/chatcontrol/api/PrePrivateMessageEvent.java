package org.mineacademy.chatcontrol.api;

import javax.annotation.Nullable;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.fo.event.SimpleEvent;

import lombok.Getter;
import lombok.Setter;

/**
 * An event that is executed when players send a message via /reply or /tell.
 * <p>
 * This is fired before all checks are done so you can just cancel the event and manually force the
 * message to be sent.
 */
@Getter
@Setter
public final class PrePrivateMessageEvent extends SimpleEvent implements Cancellable {

	private static final HandlerList handlers = new HandlerList();

	/**
	 * The command sender
	 */
	private final WrappedSender sender;

	/**
	 * The recipient... may be on this server or on proxy
	 */
	private final SyncedCache receiverCache;

	/**
	 * The recipient, null if he's on proxy
	 */
	@Nullable
	private final Player receiver;

	/**
	 * The message being sent
	 */
	private String message;

	/**
	 * Should we play the sound?
	 */
	private boolean sound;

	/**
	 * Should we allow this event to occur?
	 */
	private boolean cancelled;

	public PrePrivateMessageEvent(final WrappedSender sender, final SyncedCache receiverCache, final Player receiver, final String message, final boolean sound) {
		this.sender = sender;
		this.receiverCache = receiverCache;
		this.receiver = receiver;
		this.message = message;
		this.sound = sound;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}