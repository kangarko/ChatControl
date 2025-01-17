package org.mineacademy.chatcontrol.api;

import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.fo.event.SimpleEvent;
import org.mineacademy.fo.model.SimpleComponent;

import lombok.Getter;
import lombok.Setter;

/**
 * Event for when players send a message to a chat channel from another server.
 */
@Getter
@Setter
public final class ChatChannelProxyEvent extends SimpleEvent implements Cancellable {

	private static final HandlerList handlers = new HandlerList();

	/**
	 * The chat channel
	 */
	private final Channel channel;

	/**
	 * The sender name of whom issued the message
	 */
	private final String senderName;

	/**
	 * The sender UUID of whom issued the message
	 */
	private final UUID senderUid;

	/**
	 * A list of players receiving this message
	 */
	private final Set<Player> recipients;

	/**
	 * The formatted message
	 */
	@Setter
	private SimpleComponent formattedMessage;

	/**
	 * Is the event cancelled?
	 */
	private boolean cancelled;

	public ChatChannelProxyEvent(final Channel channel, final String senderName, final UUID senderUid, final SimpleComponent formattedMessage, final Set<Player> recipients) {
		this.channel = channel;
		this.senderName = senderName;
		this.senderUid = senderUid;
		this.formattedMessage = formattedMessage;
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