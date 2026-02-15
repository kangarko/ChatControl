package org.mineacademy.chatcontrol.operator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.ChatControlProxyMessage;
import org.mineacademy.chatcontrol.model.Discord;
import org.mineacademy.chatcontrol.model.Format;
import org.mineacademy.chatcontrol.model.Mute;
import org.mineacademy.chatcontrol.model.PlaceholderPrefix;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.model.WarningPoints;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.model.db.Log;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.ProxyUtil;
import org.mineacademy.fo.RandomUtil;
import org.mineacademy.fo.ReflectionUtil;
import org.mineacademy.fo.SerializeUtilCore;
import org.mineacademy.fo.SerializeUtilCore.Language;
import org.mineacademy.fo.TimeUtil;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.exception.FoScriptException;
import org.mineacademy.fo.model.BossBarMessage;
import org.mineacademy.fo.model.CompChatColor;
import org.mineacademy.fo.model.CompToastStyle;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.JavaScriptExecutor;
import org.mineacademy.fo.model.SimpleBook;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.SimpleSound;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.model.TitleMessage;
import org.mineacademy.fo.model.ToastMessage;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.remain.CompMaterial;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.bossbar.BossBar;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Operator implements org.mineacademy.fo.model.Rule {

	/**
	 * Map of keys and JS expressions to match certain player data to require
	 */
	private final Map<String, String> requireData = new HashMap<>();

	/**
	 * Map of keys and JS expressions to match certain player data to ignore
	 */
	private final Map<String, String> ignoreData = new HashMap<>();

	/**
	 * Map of keys and JS expressions to match certain player data to add
	 */
	private final Map<String, String> saveData = new HashMap<>();

	/**
	 * The time since when this broadcast should run
	 */
	@Getter
	private String begins;

	/**
	 * The time in the future when this broadcast no longer runs
	 */
	@Getter
	private String expires;

	/**
	 * The delay between the next time this rule can be fired up, with optional warning message
	 */
	private Tuple<SimpleTime, String> delay;

	/**
	 * The delay between the next time this rule can be fired up for the given sender, with optional warning message
	 */
	private Tuple<SimpleTime, String> playerDelay;

	/**
	 * List of commands to run as player when rule matches
	 */
	private final List<String> playerCommands = new ArrayList<>();

	/**
	 * List of commands to run as console when rule matches
	 */
	private final List<String> consoleCommands = new ArrayList<>();

	/**
	 * List of commands to send to proxy to run when rule matches
	 */
	private final List<String> proxyCommands = new ArrayList<>();

	/**
	 * List of messages to log
	 */
	private final List<String> consoleMessages = new ArrayList<>();

	/**
	 * Kick message that when set, rule will kick player
	 */
	@Nullable
	private String kickMessage;

	/**
	 * The message that, if set, will show as a toast notification
	 */
	@Nullable
	private ToastMessage toast;

	/**
	 * Permission:Message map to send to other players having such permission
	 */
	private final Map<String, String> notifyMessages = new HashMap<>();

	/**
	 * Channel:Message map to send to Discord
	 */
	private final Map<Long, List<String>> discordMessages = new HashMap<>();

	/**
	 * File:Message messages to log
	 */
	private final Map<String, String> writeMessages = new HashMap<>();

	/**
	 * Map of messages to send back to player when rule matches
	 * They have unique ID assigned to prevent duplication
	 */
	private final Map<UUID, String> warnMessages = new LinkedHashMap<>();

	/**
	 * How much money to take from player? Uses Vault.
	 */
	private double fine = 0D;

	/**
	 * Warning set:Points map to give warning points for these sets
	 */
	private final Map<String, Double> warningPoints = new HashMap<>();

	/**
	 * Lists of sounds to send to player
	 */
	private final List<SimpleSound> sounds = new ArrayList<>();

	/**
	 * The book to open for player
	 */
	@Nullable
	private SimpleBook book;

	/**
	 * Title and subtitle to send
	 */
	@Nullable
	private TitleMessage title;

	/**
	 * The message on the action bar
	 */
	@Nullable
	private String actionBar;

	/**
	 * The Boss bar message
	 */
	@Nullable
	private BossBarMessage bossBar;

	/**
	 * Should we abort checking more rules below this one?
	 */
	private boolean abort = false;

	/**
	 * Shall we cancel the event and not send the message at all?
	 */
	private boolean cancelMessage = false;

	/**
	 * Should we send the message only to the sender making him think it went through
	 * while hiding it from everyone else?
	 */
	private boolean cancelMessageSilently = false;

	/**
	 * Should we exempt the rule from being logged?
	 */
	private boolean ignoreLogging = false;

	/**
	 * Should we exempt the rule from being spied? (I.e. chat/signs spy wont get broadcasted)
	 */
	private boolean ignoreSpying = false;

	/**
	 * Prevent console catch information coming up?
	 */
	private boolean ignoreVerbose = false;

	/**
	 * Prevent this rule from working on Discord.
	 */
	private boolean ignoreDiscord = false;

	/**
	 * Prevent running the rule if the sending player is muted.
	 */
	private boolean ignoreMuted = false;

	/**
	 * Limit this rule to only work on Discord messages
	 */
	private boolean requireDiscord = false;

	/**
	 * Spy commands when used
	 */
	private boolean spyingManually = false;

	/**
	 * Is this class (all operators here) temporary disabled?
	 */
	private boolean disabled;

	/**
	 * The time the operator was last executed
	 */
	private long lastExecuted = -1;

	/**
	 * The time the operator was last executed for the given player name(s)
	 */
	private final Map<String, Long> lastExecutedForPlayers = new HashMap<>();

	/**
	 * @see org.mineacademy.fo.model.Rule#onOperatorParse(java.lang.String[])
	 */
	@Override
	public final boolean onOperatorParse(final String[] args) {
		final String firstTwo = CommonCore.joinRange(0, 2, args);
		final String theRest = CommonCore.joinRange(args.length >= 2 ? 2 : 1, args);

		if ("require key".equals(firstTwo) || "ignore key".equals(firstTwo) || "save key".equals(firstTwo)) {
			final String[] split = theRest.split(" ");

			if (split.length == 0)
				CommonCore.warning("Wrong '" + firstTwo + "' operator syntax! Usage: <keyName> <JavaScript condition with 'value' as the value object> in: " + this);
			else {
				final String key = split[0];
				final String script = split.length > 1 ? CommonCore.joinRange(1, split) : "";

				if ("require key".equals(firstTwo)) {
					checkBoolean(!this.requireData.containsKey(key), "The 'require key' operator already contains key: " + key);

					this.requireData.put(key, script);

				} else if ("ignore key".equals(firstTwo)) {
					checkBoolean(!this.ignoreData.containsKey(key), "The 'ignore key' operator already contains key: " + key);

					this.ignoreData.put(key, script);

				} else if ("save key".equals(firstTwo)) {
					checkBoolean(!this.saveData.containsKey(key), "The 'save key' operator already contains key: " + key);

					this.saveData.put(key, script);
				}
			}
		}

		else if ("expires".equals(args[0])) {
			checkBoolean(this.expires == null, "Operator 'expires' already defined on " + this);
			final String date = CommonCore.joinRange(1, args);

			this.expires = date;
		}

		else if ("begins".equals(args[0])) {
			checkBoolean(this.begins == null, "Operator 'begins' already defined on " + this);
			final String date = CommonCore.joinRange(1, args);

			this.begins = date;
		}

		else if ("delay".equals(args[0]) || "player delay".equals(firstTwo)) {
			final int offset = "player delay".equals(firstTwo) ? 1 : 0;

			try {
				final SimpleTime time = SimpleTime.fromString(CommonCore.joinRange(1 + offset, 3 + offset, args));
				final String message = args.length > 2 ? CommonCore.joinRange(3 + offset, args) : null;

				final Tuple<SimpleTime, String> tuple = new Tuple<>(time, message);

				if ("delay".equals(args[0])) {
					this.checkNotSet(this.delay, args[0]);

					this.delay = tuple;

				} else {
					this.checkNotSet(this.playerDelay, firstTwo);

					this.playerDelay = tuple;
				}

			} catch (final Throwable ex) {
				throw new FoException("Syntax error in 'delay' operator. Valid: <amount> <unit> (1 second, 2 minutes). Got: " + String.join(" ", args), false);
			}
		}

		else if ("then command".equals(firstTwo) || "then commands".equals(firstTwo) || "then playercommand".equals(firstTwo) || "then playercommands".equals(firstTwo))
			this.playerCommands.add(theRest);

		else if ("then console".equals(firstTwo) || "then consolecommand".equals(firstTwo) || "then consolecommands".equals(firstTwo)) {
			this.consoleCommands.add(theRest);

		} else if ("then bungeeconsole".equals(firstTwo) || "then bungee".equals(firstTwo) || "then proxyconsole".equals(firstTwo) || "then proxy".equals(firstTwo) || "then proxycommand".equals(firstTwo) || "then proxycommands".equals(firstTwo))
			this.proxyCommands.add(theRest);

		else if ("then log".equals(firstTwo))
			this.consoleMessages.add(theRest);

		else if ("then kick".equals(firstTwo)) {
			this.checkNotSet(this.kickMessage, "then kick");

			this.kickMessage = theRest;
		}

		else if ("then toast".equals(firstTwo)) {
			this.checkNotSet(this.toast, "then toast");

			final String[] split = theRest.split(" ");
			checkBoolean(split.length >= 3, "Invalid 'then toast' syntax. Usage: <material> <task/goal/challenge> <message>");

			final CompMaterial icon = ReflectionUtil.lookupEnumSilent(CompMaterial.class, split[0].toUpperCase());

			if (icon == null)
				throw new FoException("Invalid material '" + split[0] + "' in 'then toast' for rule: " + this, false);

			final CompToastStyle style = ReflectionUtil.lookupEnumSilent(CompToastStyle.class, split[1].toUpperCase());

			if (style == null)
				throw new FoException("Invalid style '" + split[1] + "' in 'then toast' for rule: " + this, false);

			final String message = CommonCore.joinRange(2, split);

			this.toast = new ToastMessage(icon, style, message);
		}

		else if ("then notify".equals(firstTwo)) {
			final String[] split = theRest.split(" ");
			checkBoolean(split.length > 1, "wrong then notify syntax! Usage: <permission> <message>");

			final String permission = split[0];
			final String message = CommonCore.joinRange(1, split);

			this.notifyMessages.put(permission, message);
		}

		else if ("then discord".equals(firstTwo)) {
			final String[] split = theRest.split(" ");
			checkBoolean(split.length > 1, "wrong 'then discord' syntax! Use: 'then discord <yourDiscordChannelId> <message>' (must have two words at least, found: " + split.length + " words: " + theRest);

			long channelId;

			try {
				channelId = Long.parseLong(split[0]);

			} catch (final Throwable t) {
				CommonCore.warning("Your 'then discord' operator now requires Discord channel ID instead of name, please update it in " + this);

				channelId = -1;
			}

			final String message = CommonCore.joinRange(1, split);

			if (channelId != -1) {
				final List<String> previousMessages = this.discordMessages.getOrDefault(channelId, new ArrayList<>());
				previousMessages.add(message);

				this.discordMessages.put(channelId, previousMessages);
			}
		}

		else if ("then write".equals(firstTwo)) {
			final String[] split = theRest.split(" ");
			checkBoolean(split.length > 1, "wrong 'then log' syntax! Usage: <file (without spaces)> <message>");

			final String file = split[0];
			final String message = CommonCore.joinRange(1, split);

			this.writeMessages.put(file, message);
		}

		else if ("then fine".equals(firstTwo)) {
			checkBoolean(this.fine == 0D, "everything is fine except you specifying 'then fine' twice (dont do that) for rule: " + this);

			double fine;

			try {
				fine = Double.parseDouble(theRest);

			} catch (final NumberFormatException ex) {
				throw new FoException("Invalid whole number in 'then fine': " + theRest, false);
			}

			this.fine = fine;
		}

		else if ("then points".equals(firstTwo)) {
			final String[] split = theRest.split(" ");
			checkBoolean(split.length == 2, "wrong then points syntax! Usage: <warning set> <points>");

			final String warningSet = split[0];

			double points;

			try {
				points = Double.parseDouble(split[1]);

			} catch (final NumberFormatException ex) {
				throw new FoException("Invalid whole number in 'then points': " + split[1], false);
			}

			this.warningPoints.put(warningSet, points);
		}

		else if ("then sound".equals(firstTwo)) {
			final SimpleSound sound = SimpleSound.fromString(theRest);

			this.sounds.add(sound);
		}

		else if ("then book".equals(firstTwo)) {
			this.checkNotSet(this.book, "then book");

			this.book = SimpleBook.fromFile(theRest);
		}

		else if ("then title".equals(firstTwo)) {
			this.checkNotSet(this.title, "then title");

			final List<String> split = splitVertically(theRest);
			final String title = split.get(0);
			final String subtitle = split.size() > 1 ? split.get(1) : "";
			final int fadeIn = Integer.parseInt(split.size() > 2 ? split.get(2) : "10");
			final int stay = Integer.parseInt(split.size() > 3 ? split.get(3) : "30");
			final int fadeOut = Integer.parseInt(split.size() > 4 ? split.get(4) : "10");

			this.title = new TitleMessage(title, subtitle, fadeIn, stay, fadeOut);
		}

		else if ("then actionbar".equals(firstTwo)) {
			this.checkNotSet(this.actionBar, "then actionbar");

			this.actionBar = theRest;
		}

		else if ("then bossbar".equals(firstTwo)) {
			this.checkNotSet(this.bossBar, "then bossbar");

			final String[] split = theRest.split(" ");
			checkBoolean(split.length >= 4, "Invalid 'then bossbar' syntax. Usage: <color> <style> <secondsToShow> <message>");

			final BossBar.Color color = SerializeUtilCore.deserialize(Language.YAML, BossBar.Color.class, split[0]);
			final BossBar.Overlay style = SerializeUtilCore.deserialize(Language.YAML, BossBar.Overlay.class, split[1]);

			int secondsToShow;

			try {
				secondsToShow = Integer.parseInt(split[2]);

			} catch (final NumberFormatException ex) {
				throw new FoException("Invalid seconds to show in 'then bossbar': " + split[2], false);
			}

			final String message = CommonCore.joinRange(3, split);

			this.bossBar = new BossBarMessage(color, style, secondsToShow, 1F, message);
		}

		else if ("then warn".equals(firstTwo) || "then alert".equals(firstTwo) || "then message".equals(firstTwo)) {
			this.warnMessages.put(UUID.randomUUID(), theRest);

		} else if ("then abort".equals(firstTwo)) {
			checkBoolean(!this.abort, "then abort already used on " + this);

			this.abort = true;
		}

		else if ("then deny".equals(firstTwo)) {
			if ("silently".equals(theRest)) {
				checkBoolean(!this.cancelMessageSilently, "then deny silently already used on " + this);

				this.cancelMessageSilently = true;

			} else {
				checkBoolean(!this.cancelMessage, "then deny already used on " + this);

				this.cancelMessage = true;
			}
		}

		else if ("dont log".equals(firstTwo)) {
			checkBoolean(!this.ignoreLogging, "dont log already used on " + this);

			this.ignoreLogging = true;
		}

		else if ("dont spy".equals(firstTwo)) {
			checkBoolean(!this.ignoreSpying, "dont spy already used on " + this);

			this.ignoreSpying = true;
		}

		else if ("dont verbose".equals(firstTwo)) {
			checkBoolean(!this.ignoreVerbose, "dont verbose already used on " + this);

			this.ignoreVerbose = true;
		}

		else if ("require discord".equals(firstTwo)) {
			checkBoolean(!this.requireDiscord, "require discord already used on " + this);

			this.requireDiscord = true;
		}

		else if ("spy command".equals(firstTwo) || "then spy".equals(firstTwo)) {
			checkBoolean(!this.spyingManually, "then spy already used on " + this);

			this.spyingManually = true;
		}

		else if ("ignore discord".equals(firstTwo)) {
			checkBoolean(!this.ignoreDiscord, "ignore discord already used on " + this);

			this.ignoreDiscord = true;
		}

		else if ("ignore muted".equals(firstTwo)) {
			checkBoolean(!this.ignoreMuted, "ignore muted already used on " + this);

			this.ignoreMuted = true;
		}

		else if ("disabled".equals(args[0])) {
			checkBoolean(!this.disabled, "'disabled' already used on " + this);

			this.disabled = true;
		}

		else {
			final boolean success = this.onParse(firstTwo, theRest, args);

			if (!success)
				throw new FoException("Unrecognized operator '" + String.join(" ", args) + "' found in " + this, false);
		}

		return true;
	}

	/**
	 * Parses additional operators
	 *
	 * @param param
	 * @param theRest
	 * @param args
	 * @return
	 */
	protected abstract boolean onParse(String param, String theRest, String[] args);

	/**
	 * Check if the value is null or complains that the operator of the given type is already defined
	 *
	 * @param value
	 * @param type
	 */
	protected final void checkNotSet(final Object value, final String type) {
		if (value != null)
			throw new FoException("Operator '" + type + "' already defined on " + this, false);
	}

	/**
	 * Throws an unreported exception if the given value is false.
	 *
	 * @param value
	 * @param falseMessage
	 */
	protected final static void checkBoolean(final boolean value, final String falseMessage) {
		if (!value)
			throw new FoException(falseMessage, false);
	}

	/**
	 * A helper method to split a message by |
	 * but ignore \| and replace it with | only.
	 *
	 * @param message
	 * @return
	 */
	protected static final List<String> splitVertically(final String message) {
		final List<String> split = Arrays.asList(message.split("(?<!\\\\)\\|"));

		for (int i = 0; i < split.size(); i++)
			split.set(i, split.get(i).replace("\\|", "|"));

		return split;
	}

	/**
	 * Collect all options we have to debug
	 *
	 * @return
	 */
	protected Map<String, Object> collectOptions() {
		return CommonCore.newHashMap(
				"Require Keys", this.requireData,
				"Ignore Keys", this.ignoreData,
				"Save Keys", this.saveData,
				"Expires", this.expires,
				"Begins", this.begins,
				"Delay", this.delay != null ? this.delay.getKey() + (this.delay.getValue() != null && !this.delay.getValue().isEmpty() ? ", warnmessage=" + this.delay.getValue() : "") : "",
				"Player Delay", this.playerDelay != null ? this.playerDelay.getKey() + (this.playerDelay.getValue() != null && !this.playerDelay.getValue().isEmpty() ? ", warnmessage=" + this.playerDelay.getValue() : "") : "",
				"Player Commands", this.playerCommands,
				"Console Commands", this.consoleCommands,
				"Proxy Commands", this.proxyCommands,
				"Console Messages", this.consoleMessages,
				"Kick Message", this.kickMessage,
				"Toast Message", this.toast == null ? null : this.toast.toString(),
				"Notify Messages", this.notifyMessages,
				"Spy Manually", this.spyingManually,
				"Discord Message", this.discordMessages,
				"Log To File", this.writeMessages,
				"Fine", this.fine,
				"Warning Points", this.warningPoints,
				"Sounds", this.sounds,
				"Book", this.book,
				"Title", this.title == null ? null : this.title.toString(),
				"Action Bar", this.actionBar,
				"Boss Bar", this.bossBar == null ? null : this.bossBar.toString(),
				"Warn Messages", this.warnMessages,
				"Abort", this.abort,
				"Cancel Message", this.cancelMessage,
				"Cancel Message Silently", this.cancelMessageSilently,
				"Require Discord", this.requireDiscord,
				"Ignore Logging", this.ignoreLogging,
				"Ignore Spying", this.ignoreSpying,
				"Ignore Verbose", this.ignoreVerbose,
				"Ignore Discord", this.ignoreDiscord,
				"Ignore Muted", this.ignoreMuted,
				"Disabled", this.disabled);
	}

	/**
	 *
	 * @return
	 */
	protected final long getLastExecuted() {
		return this.lastExecuted;
	}

	/**
	 *
	 * @param time
	 */
	protected final void setLastExecuted(final long time) {
		this.lastExecuted = time;
	}

	/**
	 *
	 * @param sender
	 * @return
	 */
	protected final long getLastExecuted(final CommandSender sender) {
		return this.lastExecutedForPlayers.getOrDefault(sender.getName(), -1L);
	}

	/**
	 *
	 * @param sender
	 */
	protected final void setLastExecuted(final CommandSender sender) {
		this.lastExecutedForPlayers.put(sender.getName(), System.currentTimeMillis());
	}

	/**
	 * Return a tostring representation suitable to show in game
	 *
	 * @return
	 */
	public final String toDisplayableString() {
		final String json = SerializedMap.fromObject(this.collectOptions()).toStringFormatted();

		if (json.length() > 2) {
			final StringBuilder builder = new StringBuilder();

			for (final String line : json.substring(2, json.length() - 1).split("\n"))
				builder.append(line.substring(2)).append("\n");

			return builder.toString().substring(0, builder.length() - 1);
		}

		return "Empty";
	}

	@Override
	public final String toString() {
		return this.getClass().getSimpleName() + " " + CompChatColor.stripColorCodes(SerializedMap.fromObject(this.collectOptions()).toStringFormatted());
	}

	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public final boolean equals(final Object obj) {
		return obj instanceof Operator && ((Operator) obj).getUniqueName().equals(this.getUniqueName());
	}

	/* ------------------------------------------------------------------------------- */
	/* Classes */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Represents a check that is implemented by this class
	 * @param <T>
	 */
	public abstract static class OperatorCheck<T extends Operator> {

		private static final Object LOCK = new Object();

		/**
		 * The message that is being altered
		 */
		@Getter
		@Nullable
		protected String message;

		/**
		 * The original message that was matched
		 */
		@Nullable
		protected String originalMessage;

		/**
		 * The player
		 */
		protected WrappedSender wrappedSender;

		/**
		 * Should we cancel the event silently and only send the message
		 * to the sender himself?
		 */
		@Getter
		protected boolean cancelledSilently;

		/**
		 * Shall we not broadcast spying on the message from this rule?
		 */
		@Getter
		protected boolean spyingIgnored;

		/**
		 * Shall we not broadcast {@link Log} feature on this message?
		 */
		@Getter
		protected boolean loggingIgnored;

		/**
		 * Used for operators that can run for multiple receivers, returns false on the second receiver and so forth.
		 */
		protected boolean firstTimeRun = true;

		/**
		 * Was the sender already warned? Used to prevent multiple warnings.
		 */
		private boolean receivedAnyWarningMessage;

		/**
		 * Stores sent notify messages to prevent duplication, such as when multiple curse words are matched
		 */
		private final Set<String> notifyMessages = new HashSet<>();

		/**
		 * Construct check and useful parameters
		 *
		 * @param wrappedPlayer
		 * @param message
		 */
		protected OperatorCheck(@Nullable final WrappedSender wrappedPlayer, final String message) {
			this.wrappedSender = wrappedPlayer;
			this.message = message;
			this.originalMessage = message;
		}

		public final void start() {

			// Collect all to filter
			final List<T> operators = this.getOperators();

			// Iterate through all rules and parse
			for (final T operator : operators)
				try {
					this.filter(operator);

				} catch (final OperatorAbortException ex) {
					if (!operator.isIgnoreVerbose())
						this.verbose("&cStopping further operator check.");

					break;

				} catch (final EventHandledException ex) {
					if (ex.isCancelled())
						throw ex; // send upstream if canceled

				} catch (final Throwable throwable) {
					CommonCore.throwError(throwable, "Error parsing rule: " + operator, "Error: %error");
				}
		}

		/**
		 * Returns the list of effective operators this check will evaluate against the message
		 *
		 * @return
		 */
		public abstract List<T> getOperators();

		/**
		 * Starts the filtering
		 */
		protected abstract void filter(T operator) throws EventHandledException;

		/**
		 * Return true if the given operator can be applied for the given message
		 */
		protected boolean canFilter(final T operator) {

			// Ignore disabled rules
			if (operator.isDisabled())
				return false;

			// Did not start
			if (operator.getBegins() != null)
				if (!TimeUtil.isInTimeframe(operator.getBegins(), true))
					return false;

			// Expired
			if (operator.getExpires() != null)
				if (!TimeUtil.isInTimeframe(operator.getExpires(), false))
					return false;

			final boolean isDiscord = this.wrappedSender.isDiscord();

			if (isDiscord && operator.isIgnoreDiscord())
				return false;

			if (!this.wrappedSender.isPlayer() && operator.isSpyingManually())
				return false;

			if (!isDiscord && operator.isRequireDiscord())
				return false;

			if (operator.isIgnoreMuted() && Mute.isSomethingMutedIf(true, wrappedSender))
				return false;

			if (this.wrappedSender.isPlayer()) {
				for (final Map.Entry<String, String> entry : operator.getRequireData().entrySet()) {
					final String key = entry.getKey();

					if (!this.wrappedSender.getPlayerCache().hasRuleData(key))
						return false;

					if (entry.getValue() != null && !"".equals(entry.getValue())) {
						final String script = this.replaceSenderVariablesLegacy(entry.getValue(), operator);

						final Object value = this.wrappedSender.getPlayerCache().getRuleData(key);
						final Object result;

						try {
							result = JavaScriptExecutor.run(script, CommonCore.newHashMap("player", this.wrappedSender.getPlayer(), "value", value));

						} catch (final FoScriptException ex) {
							CommonCore.logFramed(
									"Error parsing 'require key'!",
									"",
									"Operator: " + operator,
									"",
									"Evaluated script with variables replaced: '" + script + "'",
									"Sender: " + this.wrappedSender,
									"Error: " + ex.getMessage(),
									"",
									"Check that the evaluated script",
									"above is a valid JavaScript!");

							throw ex;
						}

						if (!(result instanceof Boolean))
							throw new FoException("'require key' expected boolean, got " + result.getClass() + ": " + result + " for rule: " + this, false);

						if (!((boolean) result))
							return false;
					}
				}

				for (final Map.Entry<String, String> entry : operator.getIgnoreData().entrySet()) {
					final String key = entry.getKey();
					final Object value = this.wrappedSender.getPlayerCache().getRuleData(key);

					if ((entry.getValue() == null || "".equals(entry.getValue())) && value != null)
						return false;

					final String script = this.replaceSenderVariablesLegacy(entry.getValue(), operator);

					if (value != null) {
						final Object result;

						try {
							result = JavaScriptExecutor.run(script, CommonCore.newHashMap("player", this.wrappedSender.getPlayer(), "value", value));

						} catch (final FoScriptException ex) {
							CommonCore.logFramed(
									"Error parsing 'ignore key'!",
									"",
									"Operator: " + operator,
									"",
									"Evaluated script with variables replaced: '" + script + "'",
									"Sender: " + this.wrappedSender,
									"Error: " + ex.getMessage(),
									"",
									"Check that the evaluated script",
									"above is a valid JavaScript!");

							throw ex;
						}

						if (!(result instanceof Boolean))
							throw new FoException("'ignore key' expected boolean, got " + result.getClass() + ": " + result + " for rule: " + this, false);

						if (((boolean) result))
							return false;
					}
				}
			}

			return true;
		}

		/**
		 * Run given operators for the given message and return the updated message
		 */
		protected void executeOperators(final T operator) throws EventHandledException {
			if (operator.isIgnoreSpying())
				this.spyingIgnored = true;

			if (operator.isIgnoreLogging())
				this.loggingIgnored = true;

			if (this.wrappedSender.isPlayer())
				for (final String command : operator.getPlayerCommands()) {
					final String picked = RandomUtil.nextItem(splitVertically(command));

					this.wrappedSender.getAudience().dispatchCommand(this.replaceSenderVariablesLegacy(picked, operator));
				}

			if (this.firstTimeRun) {
				if (!this.wrappedSender.isConsole())
					for (final String command : operator.getConsoleCommands()) {
						final String picked = RandomUtil.nextItem(splitVertically(command));

						Platform.dispatchConsoleCommand(this.wrappedSender.getAudience(), this.replaceSenderVariablesLegacy(picked, operator));
					}

				if (this.wrappedSender.isPlayer())
					for (final String commandLine : operator.getProxyCommands()) {
						final String picked = this.replaceSenderVariablesLegacy(RandomUtil.nextItem(splitVertically(commandLine)), operator);

						final String[] split = picked.split(" ");
						final String server = split.length > 1 ? split[0] : "proxy";
						final String command = (split.length > 1 ? CommonCore.joinRange(1, split) : split[0]);

						ProxyUtil.sendPluginMessageAs(this.wrappedSender.getAudience(), ChatControlProxyMessage.FORWARD_COMMAND, server, command);
					}

				for (final String message : operator.getConsoleMessages())
					CommonCore.log(SimpleComponent.fromMiniAmpersand(this.replaceSenderVariablesLegacy(RandomUtil.nextItem(splitVertically(message)), operator)).toLegacySection());

				for (final Map.Entry<String, String> entry : operator.getNotifyMessages().entrySet()) {
					final String permission = entry.getKey();
					final String formatOrMessage = this.replaceSenderVariablesLegacy(entry.getValue(), operator);

					final Format format = Format.parse(formatOrMessage);

					final SimpleComponent component = format.build(this.wrappedSender, this.prepareVariables(this.wrappedSender, operator));
					final String plain = component.toPlain(null);

					if (!this.notifyMessages.contains(plain)) {
						this.notifyMessages.add(plain);

						// Delay so it's shown after the message
						Platform.runTask(2, () -> {
							for (final Player online : Players.getOnlinePlayersWithLoadedDb())
								if (online.hasPermission(permission) && !online.getName().equals(this.wrappedSender.getName()))
									Platform.toPlayer(online).sendMessage(component);
						});

						if (Settings.Proxy.ENABLED)
							ProxyUtil.sendPluginMessage(ChatControlProxyMessage.NOTIFY, permission, component);
					}
				}

				if (HookManager.isDiscordSRVLoaded())
					for (final Entry<Long, List<String>> entry : operator.getDiscordMessages().entrySet()) {
						final Long discordChannelId = entry.getKey();
						final List<String> discordMessages = entry.getValue();
						int delay = 1;

						for (final String discordMessage : discordMessages) {
							Discord.getInstance().sendChannelMessageNoPlayerDelayed(delay, discordChannelId, SimpleComponent.fromMiniAmpersand(this.replaceSenderVariablesLegacy(discordMessage, operator)).toPlain());

							delay += 2;
						}

					}

				for (final Map.Entry<String, String> entry : operator.getWriteMessages().entrySet()) {
					final String file = this.replaceSenderVariablesLegacy(entry.getKey(), operator);
					final String message = SimpleComponent.fromMiniAmpersand(this.replaceSenderVariablesLegacy(entry.getValue(), operator)).toPlain();

					Platform.runTaskAsync(() -> FileUtil.writeFormatted(file, message));
				}
			}

			if (this.wrappedSender.isPlayer()) {
				if (operator.getFine() > 0)
					HookManager.withdraw(this.wrappedSender.getPlayer(), operator.getFine());

				for (final Entry<String, Double> entry : operator.getWarningPoints().entrySet()) {
					final boolean warned = WarningPoints.getInstance().givePoints(this.wrappedSender.getPlayer(), entry.getKey(), entry.getValue());

					if (!this.receivedAnyWarningMessage && warned)
						this.receivedAnyWarningMessage = true;
				}

				for (final SimpleSound sound : operator.getSounds())
					sound.play(this.wrappedSender.getPlayer());

				if (operator.getBook() != null)
					operator.getBook().openColorized(this.wrappedSender.getAudience());

				if (operator.getToast() != null)
					operator.getToast().displayTo(this.wrappedSender.getPlayer(), message -> this.replaceSenderVariablesLegacy(message, operator).replace("\\n", "\n"));

				if (operator.getTitle() != null)
					operator.getTitle().displayLegacyTo(this.wrappedSender.getAudience(), titleOrSubtitle -> this.replaceSenderVariablesLegacy(titleOrSubtitle, operator));

				if (operator.getActionBar() != null)
					this.wrappedSender.getAudience().sendActionBar(this.replaceSenderVariablesLegacy(operator.getActionBar(), operator));

				if (operator.getBossBar() != null)
					operator.getBossBar().displayLegacyTo(this.wrappedSender.getAudience(), message -> this.replaceSenderVariablesLegacy(message, operator));

				for (final Map.Entry<String, String> entry : operator.getSaveData().entrySet()) {
					final String key = entry.getKey();
					final String script = this.replaceSenderVariablesLegacy(entry.getValue(), operator);
					final Object result;

					try {
						result = script.trim().isEmpty() ? null : JavaScriptExecutor.run(script, CommonCore.newHashMap("player", this.wrappedSender.getPlayer()));

						Platform.runTask(() -> this.wrappedSender.getPlayerCache().setRuleData(key, result));

					} catch (final FoScriptException ex) {
						CommonCore.logFramed(
								"Error saving data in operator!",
								"",
								"Operator: " + operator,
								"",
								"Evaluated script with variables replaced: '" + script + "'",
								"Sender: " + this.wrappedSender,
								"Error: " + ex.getMessage(),
								"",
								"Check that the evaluated script",
								"above is a valid JavaScript!");

						throw ex;
					}
				}
			}

			if (operator.getKickMessage() != null) {
				final String kickReason = this.replaceSenderVariablesLegacy(operator.getKickMessage(), operator);

				if (!this.wrappedSender.isConsole())
					this.wrappedSender.getAudience().kick(SimpleComponent.fromMiniAmpersand(kickReason));

				else if (this.wrappedSender.isDiscord() && Settings.Discord.ENABLED)
					Discord.getInstance().kickMember(this.wrappedSender.getDiscordSender(), kickReason);
			}

			// Dirty: Run later including when EventHandledException is thrown
			if (this.firstTimeRun)
				Platform.runTask(1, () -> {
					if (!operator.getWarnMessages().isEmpty() && !this.receivedAnyWarningMessage)
						for (final Entry<UUID, String> entry : operator.getWarnMessages().entrySet()) {
							final UUID uniqueId = entry.getKey();
							final String warnMessage = RandomUtil.nextItem(splitVertically(entry.getValue())); // pick one in a list of |

							final long now = System.currentTimeMillis();
							final long lastTimeShown = this.wrappedSender.getSenderCache().getRecentWarningMessages().getOrDefault(uniqueId, -1L);

							// Prevent duplicate messages in the last 0.5 seconds
							if (lastTimeShown == -1L || now - lastTimeShown > 500) {
								final Format format = Format.parse(this.replaceSenderVariablesLegacy(warnMessage, operator));
								final SimpleComponent component = format.build(this.wrappedSender, this.prepareVariables(this.wrappedSender, operator));

								this.wrappedSender.getAudience().sendMessage(component);
								this.wrappedSender.getSenderCache().getRecentWarningMessages().put(uniqueId, now);
							}
						}
				});

			if (operator.isCancelMessage()) {
				if (!operator.isIgnoreVerbose())
					this.verbose("&cOriginal message cancelled.");

				throw new EventHandledException(true);
			}

			if (operator.isCancelMessageSilently())
				this.cancelledSilently = true;

			if (operator.isAbort())
				throw new OperatorAbortException();
		}

		/**
		 * Replace all kinds of check variables
		 *
		 * @param message
		 * @param operator
		 * @return
		 */
		protected final String replaceSenderVariablesLegacy(@Nullable final String message, final T operator) {
			return this.replaceVariablesForLegacy(message, operator, this.wrappedSender);
		}

		/**
		 * Replace all kinds of check variables
		 *
		 * @param message
		 * @param operator
		 * @param wrapped
		 * @return
		 */
		protected String replaceVariablesForLegacy(@NonNull final String message, final T operator, final WrappedSender wrapped) {
			return Variables.builder(wrapped.getAudience()).placeholders(this.prepareVariables(wrapped, operator)).replaceLegacy(message);
		}

		/**
		 * Replace all kinds of check variables
		 *
		 * @param component
		 * @param operator
		 * @return
		 */
		protected final SimpleComponent replaceSenderVariables(@Nullable final SimpleComponent component, final T operator) {
			return this.replaceVariablesFor(component, operator, this.wrappedSender);
		}

		/**
		 * Replace all kinds of check variables
		 *
		 * @param component
		 * @param operator
		 * @param wrapped
		 * @return
		 */
		protected SimpleComponent replaceVariablesFor(@NonNull final SimpleComponent component, final T operator, final WrappedSender wrapped) {
			return Variables.builder(wrapped.getAudience()).placeholders(this.prepareVariables(wrapped, operator)).replaceComponent(component);
		}

		/**
		 * Prepare variables available in this check
		 *
		 * @param operator
		 * @return
		 */
		protected Map<String, Object> prepareVariables(final WrappedSender sender, final T operator) {
			synchronized (LOCK) {
				final Map<String, Object> map = new HashMap<>();

				if (this.wrappedSender.isPlayer())
					for (final Map.Entry<String, Object> data : this.wrappedSender.getPlayerCache().getRuleData().entrySet())
						map.put("data_" + data.getKey(), SerializeUtilCore.serialize(Language.YAML, data.getValue()).toString());

				final String message = CommonCore.getOrDefaultStrict(this.message, "");

				try {
					map.put("message", SimpleComponent.stripMiniMessageTags(message));
					map.put("original_message", this.originalMessage == null ? "" : SimpleComponent.stripMiniMessageTags(this.originalMessage));
				} catch (final NoSuchMethodError err) {

					// Really old, and using CraftBukkit
					map.put("message", message);
					map.put("original_message", this.originalMessage == null ? "" : this.originalMessage);
				}

				map.putAll(SyncedCache.getPlaceholders(this.wrappedSender.getName(), this.wrappedSender.getUniqueId(), PlaceholderPrefix.SENDER));

				return map;
			}
		}

		/**
		 * Cancels the pipeline by throwing a {@link EventHandledException}
		 */
		protected final void cancel(final boolean cancelEvent) {
			this.cancel(cancelEvent);
		}

		/**
		 * Cancels the pipeline by throwing a {@link EventHandledException}
		 * and send an error message to the player
		 *
		 * @param errorMessage
		 */
		protected final void cancel(@Nullable final String errorMessage, final boolean cancelEvent) {
			if (errorMessage != null && !errorMessage.isEmpty())
				Messenger.error(this.wrappedSender.getAudience(), Variables.builder().audience(this.wrappedSender.getAudience()).replaceLegacy(errorMessage));

			throw new EventHandledException(cancelEvent);
		}

		/**
		 * Show the message if rules are set to verbose
		 *
		 * @param message
		 */
		protected final void verbose(final String... messages) {
			if (Settings.Rules.VERBOSE)
				CommonCore.log(messages);
		}
	}

	/**
	 * Represents an indication that further rule processing should be aborted
	 */
	@Getter
	@RequiredArgsConstructor
	public final static class OperatorAbortException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
}
