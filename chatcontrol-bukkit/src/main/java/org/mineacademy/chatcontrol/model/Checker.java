package org.mineacademy.chatcontrol.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.SenderCache.Output;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.operator.Rule;
import org.mineacademy.chatcontrol.operator.Rule.RuleCheck;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.AntiBot;
import org.mineacademy.chatcontrol.settings.Settings.AntiCaps;
import org.mineacademy.chatcontrol.settings.Settings.AntiSpam;
import org.mineacademy.chatcontrol.settings.Settings.Grammar;
import org.mineacademy.chatcontrol.settings.Settings.WarningPoints;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.CompChatColor;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.model.Whiteblacklist;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.Lang;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 * Represents a singular check for antispam
 */
public final class Checker {

	/**
	 * The type of the check
	 */
	private final LogType type;

	/**
	 * The sender
	 */
	private final WrappedSender wrapped;

	/**
	 * The checked message
	 */
	@Getter
	private String message;

	/**
	 * Channel associated with this check
	 */
	@Nullable
	private final Channel channel;

	/**
	 * Should we cancel the event silently and only send the message
	 * to the sender himself?
	 */
	@Getter
	@Setter
	private boolean cancelledSilently;

	/**
	 * Should we ignore logging of this message (must be handled upstream)
	 */
	@Getter
	private boolean loggingIgnored;

	/**
	 * Should we ignore spying of this message (must be handled upstream)
	 */
	@Getter
	private boolean spyingIgnored;

	/**
	 * Was the message changed during the check?
	 */
	@Getter
	private boolean messageChanged;

	/**
	 * Create a new checker
	 *
	 * @param wrapped
	 * @param message
	 * @param channel
	 */
	private Checker(final LogType type, @NonNull final WrappedSender wrapped, final String message, @Nullable final Channel channel) throws EventHandledException {
		this.type = type;
		this.wrapped = wrapped;
		this.message = message;
		this.channel = channel;

		this.filter();
	}

	/**
	 * Parse antispam
	 *
	 * @param wrapped
	 * @param message
	 * @param channel the write channel of the player
	 *
	 * @return
	 * @throws EventHandledException
	 */
	public static Checker filterCommand(final WrappedSender wrapped, final String message, @Nullable final Channel channel) throws EventHandledException {
		return new Checker(LogType.COMMAND, wrapped, message, channel);
	}

	/**
	 * Parse antispam
	 *
	 * @param wrapped
	 * @param message
	 * @param channel
	 * @return
	 * @throws EventHandledException
	 */
	public static Checker filterChannel(final WrappedSender wrapped, final String message, @Nullable final Channel channel) throws EventHandledException {
		return new Checker(LogType.CHAT, wrapped, message, channel);
	}

	/**
	 * Parse antispam
	 *
	 * @param wrapped
	 * @param message
	 * @return
	 * @throws EventHandledException
	 */
	public static Checker filterChat(final WrappedSender wrapped, final String message) throws EventHandledException {
		return new Checker(LogType.CHAT, wrapped, message, null);
	}

	/**
	 * @see org.mineacademy.chatcontrol.model.Checkable#filter()
	 */
	protected void filter() throws EventHandledException {

		// Prevent filtering from Discord if disabled
		if (this.wrapped.isDiscord() && !Settings.Discord.FILTER_CHAT)
			return;

		// The time is always now -Jesus
		final long now = System.currentTimeMillis();

		final SenderCache senderCache = this.wrapped.getSenderCache();

		final List<Output> lastOutputs = senderCache.getLastOutputs(this.type, this.get(AntiSpam.Chat.SIMILARITY_PAST_MESSAGES, AntiSpam.Commands.SIMILARITY_PAST_COMMANDS), this.channel);
		final Output lastOutput = lastOutputs.isEmpty() ? Output.NO_OUTPUT : lastOutputs.get(lastOutputs.size() - 1);

		// If the check relates to a command, this is the command label such as /tell
		final String label = this.message.split(" ")[0];

		// Convenience
		SimpleComponent[] pendingAntiCapsWarning = null;

		// Antibot -- the checker has been called from join event
		if (this.wrapped.isPlayer()) {
			final Player player = this.wrapped.getPlayer();

			if (this.type == LogType.CHAT) {
				final Map<String, Set<Player>> playerMessages = new LinkedHashMap<>();

				final long joinFloodDelay = AntiBot.JOIN_FLOOD_THRESHOLD.getTimeSeconds() * 1000;
				final long parrotDelay = AntiSpam.Chat.PARROT_DELAY.getTimeSeconds() * 1000;
				final boolean parrotToggled = !player.hasPermission(Permissions.Bypass.PARROT) && AntiSpam.Chat.PARROT && !AntiSpam.Chat.PARROT_WHITELIST.isInListRegex(this.message);

				boolean parrotTriggered = false;
				int similarityRounded = 0;

				for (final Player online : Remain.getOnlinePlayers()) {
					final SenderCache onlineCache = SenderCache.from(online);

					final String lastMessage = online.equals(player) ? this.message : onlineCache.getLastChatMessage();

					if (lastMessage == null)
						continue;

					final Set<Player> playersWhoTypedTheSame = playerMessages.getOrDefault(lastMessage, new HashSet<>());

					if (!online.equals(player) && parrotToggled) {
						final Output otherLastOutput = onlineCache.getLastChat();
						final double similarity = ChatUtil.getSimilarityPercentage(lastMessage, this.message);

						if (similarity >= AntiSpam.Chat.PARROT_SIMILARITY && (System.currentTimeMillis() - otherLastOutput.getTime()) < parrotDelay) {
							parrotTriggered = true;
							similarityRounded = (int) Math.round(similarity * 100);

							break;
						}
					}

					if (AntiBot.JOIN_FLOOD_ENABLED) {
						if (onlineCache.isJoinFloodActivated())
							continue;

						if ((System.currentTimeMillis() - onlineCache.getLastLogin()) < joinFloodDelay) {
							playersWhoTypedTheSame.add(online);

							playerMessages.put(lastMessage, playersWhoTypedTheSame);
						}
					}
				}

				if (parrotTriggered)
					this.cancel(Lang.component("checker-parrot",
							"message", this.message,
							"similarity", similarityRounded,
							"delay", AntiSpam.Chat.PARROT_DELAY.getRaw()));

				final List<Map.Entry<String, Set<Player>>> sortedPlayerMessages = new ArrayList<>(playerMessages.entrySet());
				Collections.sort(sortedPlayerMessages, (first, second) -> Integer.compare(first.getValue().size(), second.getValue().size()));

				// A given amount of players typed the same
				if (AntiBot.JOIN_FLOOD_ENABLED)
					for (final Map.Entry<String, Set<Player>> entry : sortedPlayerMessages) {

						final Set<Player> triggerPlayers = entry.getValue();
						final String message = entry.getKey();
						final int playerCount = triggerPlayers.size();

						if (AntiBot.JOIN_FLOOD_ENABLED) {
							if (playerCount < AntiBot.JOIN_FLOOD_MIN_PLAYERS)
								continue;

							// Then execute some nice commands for the players
							for (final Player triggerPlayer : triggerPlayers) {
								final SenderCache triggerCache = SenderCache.from(triggerPlayer);

								if (!triggerPlayer.isOnline() || triggerCache.isJoinFloodActivated())
									continue;

								for (final String command : AntiBot.JOIN_FLOOD_COMMANDS)
									Platform.dispatchConsoleCommand(Platform.toPlayer(triggerPlayer), command
											.replace("{player_amount}", String.valueOf(playerCount))
											.replace("{threshold}", AntiBot.JOIN_FLOOD_THRESHOLD.getRaw())
											.replace("{message}", message));

								triggerCache.setJoinFloodActivated(true);
							}
						}
					}
			}

			if (senderCache.hasJoinLocation() && !senderCache.isMovedFromJoin()) {

				// Prevent antimove firing again when player returns to his join location
				if (!Valid.locationEquals(senderCache.getJoinLocation(), player.getLocation()))
					senderCache.setMovedFromJoin(true);

				else if (!this.hasPerm(Permissions.Bypass.MOVE))
					if (this.get(AntiBot.BLOCK_CHAT_UNTIL_MOVED, AntiBot.BLOCK_CMDS_UNTIL_MOVED.isInList(label)))
						this.cancel(Lang.component("checker-move-" + (this.type == LogType.CHAT ? "chat" : "command")));
			}

			if (senderCache.hasJoinLocation()) {
				final long inputDelay = this.get(AntiBot.COOLDOWN_CHAT_AFTER_JOIN, AntiBot.COOLDOWN_COMMAND_AFTER_JOIN).getTimeSeconds();
				final long lastLogin = senderCache.getLastLogin();
				final long playtime = (now - lastLogin) / 1000;

				if (playtime < inputDelay)
					this.cancel(Lang.component("checker-delay-after-join-" + (this.type == LogType.CHAT ? "chat" : "command"),
							"seconds", Lang.numberFormat("case-second", inputDelay - playtime)));
			}
		}

		// Delay
		if (!this.wrapped.isConsole() && !this.hasPerm(this.type == LogType.CHAT ? Permissions.Bypass.DELAY_CHAT : Permissions.Bypass.DELAY_COMMAND) && lastOutput.getTime() != -1) {
			final Whiteblacklist whitelist = (this.type == LogType.CHAT ? AntiSpam.Chat.WHITELIST_DELAY : AntiSpam.Commands.WHITELIST_DELAY);

			if (!whitelist.isInListRegex(this.message)) {

				// Pull the default one
				final SimpleTime channelSpecificDelay = this.channel != null && this.channel.getMessageDelay() != null ? this.channel.getMessageDelay() : null;

				final long delay = this.type == LogType.CHAT ? AntiSpam.Chat.DELAY.getFor(this.wrapped.getSender(), channelSpecificDelay).getTimeSeconds() : AntiSpam.Commands.DELAY.getFor(this.wrapped.getSender()).getTimeSeconds();
				final long lastMessageDelay = (now - lastOutput.getTime()) / 1000;

				if (delay > lastMessageDelay) {
					final long remainingTime = delay - lastMessageDelay;

					this.get(WarningPoints.TRIGGER_CHAT_DELAY, WarningPoints.TRIGGER_COMMAND_DELAY)
							.execute(this.wrapped.getSender(),
									Lang.component("checker-delay-" + (this.type == LogType.CHAT ? "chat" : "command"), "seconds", Lang.numberFormat("case-second", remainingTime)),
									SerializedMap.fromArray("remaining_time", delay));
				}
			}
		}

		// Period
		if (!this.hasPerm(Permissions.Bypass.PERIOD)) {
			final SimpleTime period = this.get(AntiSpam.Chat.LIMIT_PERIOD, AntiSpam.Commands.LIMIT_PERIOD);
			final long periodTime = now - (period.getTimeSeconds() * 1000);
			final int periodLimit = this.get(AntiSpam.Chat.LIMIT_MAX, AntiSpam.Commands.LIMIT_MAX);

			if (senderCache.getOutputsAfter(this.type, periodTime, this.channel).size() >= periodLimit)
				this.get(WarningPoints.TRIGGER_CHAT_LIMIT, WarningPoints.TRIGGER_COMMAND_LIMIT).execute(this.wrapped.getSender(),
						Lang.component("checker-period",
								"type_amount", String.valueOf(periodLimit),
								"type", Lang.numberFormatNoAmount(this.get("case-message", "case-command"), periodLimit),
								"period_amount", String.valueOf(period.getTimeSeconds()),
								"period", Lang.numberFormatNoAmount("case-second", period.getTimeSeconds())),
						SerializedMap.fromArray("messages_in_period", periodLimit));
		}

		// Anticaps
		if (this.get(AntiCaps.ENABLED, AntiCaps.ENABLED_IN_COMMANDS.isInList(label)) && !this.hasPerm(Permissions.Bypass.CAPS) && this.message.length() >= AntiCaps.MIN_MESSAGE_LENGTH) {
			final Set<String> onlineNames = SyncedCache.getNamesAndNicks();
			final String[] wordsCopy = CompChatColor.stripColorCodes(this.message).split(" ");

			for (int i = 0; i < wordsCopy.length; i++) {
				final String word = wordsCopy[i];

				for (final String onlineName : onlineNames)
					if (onlineName.equalsIgnoreCase(word))
						wordsCopy[i] = word.toLowerCase();

			}

			final String messageWithPlayerNamesIgnored = String.join(" ", wordsCopy);

			final int capsPercentage = (int) (ChatUtil.getCapsPercentage(messageWithPlayerNamesIgnored) * 100);

			if (capsPercentage >= AntiCaps.MIN_CAPS_PERCENTAGE || ChatUtil.getCapsInRow(messageWithPlayerNamesIgnored, AntiCaps.WHITELIST) >= AntiCaps.MIN_CAPS_IN_A_ROW) {
				final String[] words = this.message.split(" ");

				boolean capsAllowed = false;
				boolean whitelisted = false;

				for (int i = 0; i < words.length; i++) {
					final String word = words[i];

					if (word.isEmpty())
						continue;

					if (ChatUtil.isDomain(word)) {
						whitelisted = true;
						capsAllowed = true;
					}

					// Filter whitelist
					if (AntiCaps.WHITELIST.isInList(word)) {
						capsAllowed = true;

						continue;
					}

					// Exclude user names
					for (final String onlineName : onlineNames)
						if (onlineName.equalsIgnoreCase(word)) {
							whitelisted = true;
							capsAllowed = true;

							continue;
						}

					if (!whitelisted) {
						if (!capsAllowed) {
							final char firstChar = word.charAt(0);

							words[i] = firstChar + word.toLowerCase().substring(1);

						} else
							words[i] = word.toLowerCase();

						capsAllowed = !words[i].endsWith(".") && !words[i].endsWith("!") && !words[i].endsWith("?");
					}

					whitelisted = false;
				}

				final String newMessage = String.join(" ", words);

				if (!this.message.equals(newMessage)) {
					this.message = newMessage;
					this.messageChanged = true;

					try {
						WarningPoints.TRIGGER_CAPS.execute(this.wrapped.getSender(),
								Lang.component("checker-caps", "type", Lang.numberFormatNoAmount(this.get("case-message", "case-command"), 1)),
								SerializedMap.fromArray("caps_percentage_double", capsPercentage / 100D));

					} catch (final EventHandledException ex) {
						pendingAntiCapsWarning = ex.getComponents();
					}
				}
			}
		}

		// Filter rules
		final RuleCheck<Rule> rulesCheck = Rule.filter(this.get(RuleType.CHAT, RuleType.COMMAND), this.wrapped, this.message, this.channel);

		if (rulesCheck.isLoggingIgnored())
			this.loggingIgnored = true;

		if (rulesCheck.isSpyingIgnored())
			this.spyingIgnored = true;

		if (rulesCheck.isMessageChanged()) {
			this.message = rulesCheck.getMessage();

			this.messageChanged = true;
		}

		if (!this.isCancelledSilently() && rulesCheck.isCancelledSilently())
			this.cancelledSilently = true;

		// Similarity -- filter after rules since rules can change the message
		if (!this.hasPerm(this.type == LogType.CHAT ? Permissions.Bypass.SIMILARITY_CHAT : Permissions.Bypass.SIMILARITY_COMMAND)) {
			final double threshold = this.get(AntiSpam.Chat.SIMILARITY, AntiSpam.Commands.SIMILARITY).getFor(this.wrapped.getSender());

			if (threshold > 0) {
				int breaches = 0;

				for (final Output output : lastOutputs) {
					if ((now - output.getTime()) > this.get(AntiSpam.Chat.SIMILARITY_FORGIVE_TIME, AntiSpam.Commands.SIMILARITY_FORGIVE_TIME).getTimeSeconds() * 1000)
						continue;

					if (this.type == LogType.COMMAND && Settings.AntiSpam.Commands.SIMILARITY_MIN_ARGS > 0)
						if (output.getOutput().split(" ").length - 1 < Settings.AntiSpam.Commands.SIMILARITY_MIN_ARGS)
							continue;

					final boolean isWhitelisted = this.type == LogType.CHAT ? AntiSpam.Chat.WHITELIST_SIMILARITY.isInListRegex(lastOutput.getOutput())
							: AntiSpam.Commands.WHITELIST_SIMILARITY.isInList(output.getOutput().split(" ")[0]);

					if (isWhitelisted)
						continue;

					final double similarity = ChatUtil.getSimilarityPercentage(output.getOutput(), this.message);

					if (similarity >= threshold && ++breaches >= this.get(Settings.AntiSpam.Chat.SIMILARITY_START_AT, Settings.AntiSpam.Commands.SIMILARITY_START_AT)) {
						this.get(WarningPoints.TRIGGER_CHAT_SIMILARITY, WarningPoints.TRIGGER_COMMAND_SIMILARITY)
								.execute(this.wrapped.getSender(),
										Lang.component("checker-similarity-" + (this.type == LogType.CHAT ? "chat" : "command"), "similarity", String.valueOf(Math.round(similarity * 100))),
										SerializedMap.fromArray("similarity_percentage_double", threshold));
					}
				}
			}
		}

		// Cache last message before grammar
		if (this.type == LogType.CHAT)
			senderCache.cacheMessage(this.message, this.channel);

		else if (this.type == LogType.COMMAND)
			senderCache.cacheCommand(this.message);

		else
			throw new FoException("Caching of " + this.type + " not implemented yet");

		// Grammar
		if (this.type == LogType.CHAT && !this.hasPerm(Permissions.Bypass.GRAMMAR)) {
			if (Grammar.CAPITALIZE_MSG_LENGTH != 0 && this.message.length() >= Grammar.CAPITALIZE_MSG_LENGTH) {
				this.message = ChatUtil.capitalizeFirst(this.message);

				this.messageChanged = true;
			}

			if (Grammar.INSERT_DOT_MSG_LENGTH != 0 && this.message.length() >= Grammar.INSERT_DOT_MSG_LENGTH) {
				this.message = ChatUtil.insertDot(this.message);

				this.messageChanged = true;
			}
		}

		if (pendingAntiCapsWarning != null)
			for (final SimpleComponent component : pendingAntiCapsWarning)
				Messenger.warn(this.wrapped.getSender(), component);
	}

	/*
	 * Return if the sender has the given permission
	 */
	private boolean hasPerm(final String permission) {
		return this.wrapped.hasPermission(permission);
	}

	/*
	 * Cancels the pipeline by throwing a {@link EventHandledException}
	 * and send an error message to the player
	 */
	private void cancel(@NonNull final SimpleComponent errorMessage) {
		throw new EventHandledException(true, Variables.builder(this.wrapped.getAudience()).replaceComponent(errorMessage));
	}

	/*
	 * If the checker checks chat, return first argument, if checker checks commands
	 * return the second
	 */
	private <T> T get(final T returnThisIfChat, final T returnThisIfCommand) {
		return this.type == LogType.CHAT ? returnThisIfChat : returnThisIfCommand;
	}
}
