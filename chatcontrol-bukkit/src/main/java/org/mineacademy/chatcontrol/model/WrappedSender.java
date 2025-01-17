package org.mineacademy.chatcontrol.model;

import java.util.UUID;

import javax.annotation.Nullable;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.model.DiscordSender;
import org.mineacademy.fo.model.DynmapSender;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.platform.FoundationPlayer;
import org.mineacademy.fo.platform.Platform;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class WrappedSender {

	/**
	 * The sender
	 */
	private final CommandSender sender;

	/**
	 * The sender player, null if not applicable
	 */
	@Nullable
	private final Player player;

	/**
	 * The sender player cache, null if not applicable
	 */
	@Nullable
	private final PlayerCache playerCache;

	/**
	 * The sender cache
	 */
	private final SenderCache senderCache;

	/**
	 * The audience
	 */
	private final FoundationPlayer audience;

	/**
	 * Return the name of the sender
	 *
	 * @return
	 */
	public String getName() {
		return this.audience.getName();
	}

	/**
	 * Return true if we got a player
	 *
	 * @return
	 */
	public boolean isPlayer() {
		return this.player != null;
	}

	/**
	 * Return true if we got a console
	 *
	 * @return
	 */
	public boolean isConsole() {
		return this.sender instanceof ConsoleCommandSender;
	}

	/**
	 * Return true if we got a discord sender
	 *
	 * @return
	 */
	public boolean isDiscord() {
		return this.sender instanceof DiscordSender;
	}

	/**
	 * Return the discord sender or null if not applicable
	 *
	 * @return
	 */
	@Nullable
	public DiscordSender getDiscordSender() {
		return this.isDiscord() ? (DiscordSender) this.sender : null;
	}

	/**
	 * Return the player cache or null if not applicable
	 * Also works for Discord sender
	 *
	 * @return
	 */
	public PlayerCache getPlayerCache() {
		return this.isDiscord() ? (PlayerCache) this.getDiscordSender().getCache() : this.isPlayer() ? this.playerCache : null;
	}

	/**
	 * Return the unique id, or FoConstants.NULL_UUID if not applicable
	 *
	 * @return
	 */
	public UUID getUniqueId() {
		return this.audience.getUniqueId();
	}

	/**
	 * Return true if the sender has the given permission
	 *
	 * @param permission
	 * @return
	 */
	public boolean hasPermission(final String permission) {
		return this.sender.hasPermission(permission);
	}

	/**
	 * Sends the sender a message
	 *
	 * @param message
	 */
	public void sendMessage(final String message) {
		Common.tell(this.sender, message);
	}

	/**
	 * Sends the sender a message
	 *
	 * @param component
	 */
	public void sendMessage(final SimpleComponent component) {
		this.audience.sendMessage(component);
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj instanceof WrappedSender)
			return ((WrappedSender) obj).getUniqueId().equals(this.getUniqueId());

		else if (obj instanceof CommandSender)
			return ((CommandSender) obj).getName().equals(this.getName());

		else if (obj instanceof FoundationPlayer)
			return ((FoundationPlayer) obj).getName().equals(this.getName());

		return false;
	}

	@Override
	public String toString() {
		return "WrappedSender{name=" + this.sender.getName() + ", class=" + this.sender.getClass().getSimpleName() + "}";
	}

	/**
	 * Wrap a command sender
	 *
	 * @param audience
	 * @return
	 */
	public static WrappedSender fromAudience(final FoundationPlayer audience) {
		final CommandSender sender = audience.getSender();
		final Player player = audience.isPlayer() ? audience.getPlayer() : null;

		return new WrappedSender(sender, player, player != null ? PlayerCache.fromCached(player) : null, SenderCache.from(audience.getUniqueId(), audience.getName()), audience);
	}

	/**
	 * Wrap a command sender
	 *
	 * @param sender
	 * @return
	 */
	public static WrappedSender fromSender(final CommandSender sender) {
		return sender instanceof Player ? fromPlayer((Player) sender) : new WrappedSender(sender, null, null, SenderCache.from(sender), Platform.toPlayer(sender));
	}

	/**
	 * Wrap a discord sender
	 *
	 * @param sender
	 * @return
	 */
	public static WrappedSender fromDiscord(final DiscordSender sender) {
		return new WrappedSender(sender, null, (PlayerCache) sender.getCache(), SenderCache.from(sender), Platform.toPlayer(sender));
	}

	/**
	 * Wrap a discord sender
	 *
	 * @param sender
	 * @return
	 */
	public static WrappedSender fromDynmap(final DynmapSender sender) {
		final Player onlinePlayer = sender.getOnlinePlayer();

		return new WrappedSender(sender, onlinePlayer,
				onlinePlayer != null ? PlayerCache.fromCached(onlinePlayer) : null,
				SenderCache.from(onlinePlayer != null ? onlinePlayer : sender),
				Platform.toPlayer(onlinePlayer != null ? onlinePlayer : sender));
	}

	/**
	 * Wrap a command sender and his cache
	 *
	 * @param sender
	 * @param senderCache
	 * @return
	 */
	public static WrappedSender fromSenderCache(final CommandSender sender, final SenderCache senderCache) {
		return sender instanceof Player ? fromPlayer((Player) sender) : new WrappedSender(sender, null, null, senderCache, Platform.toPlayer(sender));
	}

	/**
	 * Wrap a player
	 *
	 * @param player
	 * @return
	 */
	public static WrappedSender fromPlayer(final Player player) {
		return new WrappedSender(player, player, PlayerCache.fromCached(player), SenderCache.from(player), Platform.toPlayer(player));
	}

	/**
	 * Wrap a player and his cache
	 *
	 * @param player
	 * @param playerCache
	 * @param senderCache
	 * @return
	 */
	public static WrappedSender fromPlayerCaches(final Player player, final PlayerCache playerCache, final SenderCache senderCache) {
		return new WrappedSender(player, player, playerCache, senderCache, Platform.toPlayer(player));
	}
}
