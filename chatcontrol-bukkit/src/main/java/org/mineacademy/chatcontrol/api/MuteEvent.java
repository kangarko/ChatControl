package org.mineacademy.chatcontrol.api;

import java.util.UUID;

import javax.annotation.Nullable;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.fo.event.SimpleEvent;
import org.mineacademy.fo.model.SimpleTime;

import lombok.Getter;
import lombok.Setter;

/**
 * Event for when something gets muted, either a channel, a player or the server.
 *
 * Channel mute = targetChannel is not null
 * Player mute = targetPlayerName and UUID are not null
 * Server mute = everything is null
 */
@Getter
public final class MuteEvent extends SimpleEvent implements Cancellable {

	private static final HandlerList handlers = new HandlerList();

	/**
	 * The type of the mute.
	 */
	private final Type type;

	/**
	 * The duration of the mute, null if we are UNmuting
	 */
	@Nullable
	@Setter
	private SimpleTime duration;

	/**
	 * The channel that got muted, or null if the event did not fire for a channel
	 */
	@Nullable
	private Channel targetChannel;

	/**
	 * The player name who got muted, or null if the event did not fire for a player
	 */
	@Nullable
	private String targetPlayerName;

	/**
	 * The player UUID who got muted, or null if the event did not fire for a player
	 */
	@Nullable
	private UUID targetPlayerUniqueId;

	/**
	 * Is the event cancelled?
	 */
	@Setter
	private boolean cancelled;

	/*
	 * Server mute
	 */
	private MuteEvent(final Type type, final SimpleTime duration) {
		this.type = type;
		this.duration = duration;
	}

	/*
	 * Channel mute
	 */
	private MuteEvent(final Type type, final Channel targetChannel, final SimpleTime duration) {
		this.type = type;
		this.targetChannel = targetChannel;
		this.duration = duration;
	}

	/*
	 * Player mute
	 */
	private MuteEvent(final Type type, final String targetPlayerName, final UUID targetPlayerUniqueId, final SimpleTime duration) {
		this.type = type;
		this.targetPlayerName = targetPlayerName;
		this.targetPlayerUniqueId = targetPlayerUniqueId;
		this.duration = duration;
	}

	/**
	 * Return true if this is a mute
	 *
	 * @return
	 */
	public boolean isMute() {
		return this.duration != null;
	}

	/**
	 * Return true if this is an unmute
	 *
	 * @return
	 */
	public boolean isUnmute() {
		return this.duration == null;
	}

	/**
	 * Return true if this is a player mute
	 *
	 * @return
	 */
	public boolean isPlayerMute() {
		return this.type == Type.PLAYER;
	}

	/**
	 * Return true if this is a channel mute
	 *
	 * @return
	 */
	public boolean isChannelMute() {
		return this.type == Type.CHANNEL;
	}

	/**
	 * Return true if this is a server mute
	 *
	 * @return
	 */
	public boolean isServerMute() {
		return this.type == Type.SERVER;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}

	public static MuteEvent server(final SimpleTime duration) {
		return new MuteEvent(Type.SERVER, duration);
	}

	public static MuteEvent channel(final Channel targetChannel, final SimpleTime duration) {
		return new MuteEvent(Type.CHANNEL, targetChannel, duration);
	}

	public static MuteEvent player(final String targetPlayerName, final UUID targetPlayerUniqueId, final SimpleTime duration) {
		return new MuteEvent(Type.PLAYER, targetPlayerName, targetPlayerUniqueId, duration);
	}

	public enum Type {
		SERVER,
		CHANNEL,
		PLAYER;
	}
}