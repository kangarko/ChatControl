package org.mineacademy.chatcontrol.api;

import java.util.Set;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.fo.event.SimpleEvent;
import org.mineacademy.fo.model.SimpleComponent;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Event for when players send a message to a chat channel.
 *
 * This is fired after checker, rules, colors, sound notify etc. has fired.
 * Cancelling has thus only partial effect and is not recommended.
 */
@Getter
@Setter
@AllArgsConstructor
public final class ChannelPostChatEvent extends SimpleEvent implements Cancellable {

	private static final HandlerList handlers = new HandlerList();

	/**
	 * The chat channel
	 */
	private final Channel channel;

	/**
	 * The player who issued the message
	 */
	private final CommandSender sender;

	/**
	 * A list of players receiving this message
	 */
	private final Set<Player> recipients;

	/**
	 * The message
	 */
	private final String message;

	/**
	 * The formatted message
	 */
	private SimpleComponent format;

	/**
	 * The message
	 */
	private String consoleFormat;

	/**
	 * Is the event cancelled?
	 */
	private final boolean cancelledSilently;

	/**
	 * Is the event cancelled?
	 */
	private boolean cancelled;

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}