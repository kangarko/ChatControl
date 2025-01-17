package org.mineacademy.chatcontrol.api;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Checker;
import org.mineacademy.chatcontrol.model.Newcomer;
import org.mineacademy.chatcontrol.model.RuleType;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.model.db.ServerSettings;
import org.mineacademy.chatcontrol.operator.Rule;
import org.mineacademy.chatcontrol.operator.Rule.RuleCheck;
import org.mineacademy.fo.ValidCore;

/**
 * The main class of the ChatControl's API.
 *
 * @author kangarko
 */
public final class ChatControlAPI {

	/**
	 * Get if the chat has globally been muted via /mute.
	 *
	 * @return if the chat has been globally muted
	 */
	public static boolean isChatMuted() {
		return ServerSettings.getInstance().isMuted();
	}

	/**
	 * Get the player cache or complains if the player is not online.
	 *
	 * @param player the player
	 * @return
	 */
	public static PlayerCache getCache(final Player player) {
		ValidCore.checkBoolean(player.isOnline(), "Player " + player.getName() + " must be online to get his cache!");

		return PlayerCache.fromCached(player);
	}

	/**
	 * Run antispam, anticaps, time and delay checks as well as rules for the given message
	 *
	 * @param sender
	 * @param message
	 * @return
	 */
	public static Checker checkMessage(final Player sender, final String message) {
		final Checker checker = Checker.filterChannel(WrappedSender.fromPlayer(sender), message, null);

		return checker;
	}

	/**
	 * Apply the given rules type for the message sent by the player
	 *
	 * @param type
	 * @param sender
	 * @param message
	 * @return
	 */
	public static RuleCheck<?> checkRules(final RuleType type, final Player sender, final String message) {
		return checkRules(type, sender, message, null);
	}

	/**
	 * Apply the given rules type for the message sent by the player
	 *
	 * @param type
	 * @param sender
	 * @param message
	 * @param channel
	 * @return
	 */
	public static RuleCheck<?> checkRules(final RuleType type, final CommandSender sender, final String message, final Channel channel) {
		return Rule.filter(type, WrappedSender.fromSender(sender), message, channel);
	}

	/**
	 * Return if the player is newcomer according to the Newcomer settings.
	 *
	 * @param player the player
	 * @return if ChatControl considers the player a newcomer
	 */
	public static boolean isNewcomer(final Player player) {
		return Newcomer.isNewcomer(player);
	}

	/**
	 * Returns true if the given channel is available and loaded.
	 *
	 * @param channelName
	 * @return
	 */
	public static boolean isChannelInstalled(final String channelName) {
		return Channel.isChannelLoaded(channelName);
	}

	/**
	 * Sends a message to the given channel through the given sender.
	 *
	 * @param sender act as if the given sender issued the chat message
	 * @param channelName
	 * @param message
	 */
	public static void sendMessage(final CommandSender sender, final String channelName, final String message) {
		final Channel channel = Channel.findChannel(channelName);
		ValidCore.checkNotNull(channel, "Channel '" + channelName + "' is not installed. Use ChatControlAPI#isChannelInstalled to check that first!");

		channel.sendMessage(sender, message);
	}
}
