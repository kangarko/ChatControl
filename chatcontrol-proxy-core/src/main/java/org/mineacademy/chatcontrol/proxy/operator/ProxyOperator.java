package org.mineacademy.chatcontrol.proxy.operator;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.PlaceholderPrefix;
import org.mineacademy.chatcontrol.proxy.ProxyServerCache;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.RandomUtil;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.model.CompChatColor;
import org.mineacademy.fo.model.Rule;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.platform.FoundationPlayer;
import org.mineacademy.fo.platform.Platform;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ProxyOperator implements Rule {

	/**
	 * Represents the date formatting using to evaluate "expires" operator
	 *
	 * d MMM yyyy, HH:mm
	 */
	private final static DateFormat DATE_FORMATTING = new SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.ENGLISH);

	/**
	 * The time in the future when this broadcast no longer runs
	 */
	@Getter
	private long expires = -1;

	/**
	 * The delay between the next time this rule can be fired up, with optional warning message
	 */
	private Tuple<SimpleTime, String> delay;

	/**
	 * List of commands to run as player when rule matches
	 */
	private final List<String> playerCommands = new ArrayList<>();

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
	private SimpleComponent kickMessage;

	/**
	 * Channel:Message map to send to Discord
	 */
	private final Map<String, String> discordMessages = new HashMap<>();

	/**
	 * File:Message messages to log
	 */
	private final Map<String, String> writeMessages = new HashMap<>();

	/**
	 * Map of messages to send back to player when rule matches
	 * They have unique ID assigned to prevent duplication
	 */
	private final Map<UUID, SimpleComponent> warnMessages = new LinkedHashMap<>();

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
	 * Only fire this operator for the sender if he played before.
	 */
	private boolean requirePlayedBefore = false;

	/**
	 * Ignore this operator for the sender if he played before.
	 */
	private boolean ignorePlayedBefore = false;

	/**
	 * Should we exempt the rule from being logged?
	 */
	private boolean ignoreLogging = false;

	/**
	 * Prevent console catch information coming up?
	 */
	private boolean ignoreVerbose = false;

	/**
	 * Is this class (all operators here) temporary disabled?
	 */
	private boolean disabled;

	/**
	 * The time the operator was last executed
	 */
	@Setter(value = AccessLevel.PROTECTED)
	@Getter
	private long lastExecuted = -1;

	/**
	 * @see Rule#onOperatorParse(java.lang.String[])
	 */
	@Override
	public final boolean onOperatorParse(final String[] args) {
		final String param = CommonCore.joinRange(0, 2, args);
		final String theRest = CommonCore.joinRange(args.length >= 2 ? 2 : 1, args);

		final List<String> theRestSplit = splitVertically(theRest);

		if ("expires".equals(args[0])) {
			ValidCore.checkBoolean(this.expires == -1, "Operator 'expires' already defined on " + this);

			String date = CommonCore.joinRange(1, args);

			try {
				// Workaround to enable users put in both short and fully abbreviated month names
				final String[] months = { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };
				final String[] fullNameMonths = { "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December" };

				for (int i = 0; i < months.length; i++)
					date = date.replaceAll(months[i] + "\\b", fullNameMonths[i]);

				this.expires = DATE_FORMATTING.parse(date).getTime();

			} catch (final ParseException ex) {
				CommonCore.throwError(ex, "Syntax error in 'expires' operator. Valid: dd MMM yyyy, HH:mm Got: " + date);
			}
		}

		else if ("delay".equals(args[0])) {
			this.checkNotSet(this.delay, "delay");

			try {
				final SimpleTime time = SimpleTime.fromString(CommonCore.joinRange(1, 3, args));
				final String message = args.length > 2 ? CommonCore.joinRange(3, args) : null;

				this.delay = new Tuple<>(time, message);

			} catch (final Throwable ex) {
				CommonCore.throwError(ex, "Syntax error in 'delay' operator. Valid: <amount> <unit> (1 second, 2 minutes). Got: " + String.join(" ", args));
			}
		}

		else if ("then command".equals(param) || "then commands".equals(param))
			this.playerCommands.addAll(theRestSplit);

		else if ("then proxy".equals(param) || "then proxyconsole".equals(param) || "then bungeeconsole".equals(param) || "then bungee".equals(param)) {
			if ("then bungeeconsole".equals(param) || "then bungee".equals(param))
				CommonCore.warning("The 'then bungeeconsole' and 'then bungee' operators are deprecated and will be removed in the future. Use 'then proxy' instead in " + this);

			this.proxyCommands.addAll(theRestSplit);

		} else if ("then log".equals(param))
			this.consoleMessages.addAll(theRestSplit);

		else if ("then kick".equals(param)) {
			this.checkNotSet(this.kickMessage, "then kick");

			this.kickMessage = SimpleComponent.fromMiniAmpersand(theRest);
		}

		else if ("then discord".equals(param)) {
			final String[] split = theRest.split(" ");
			ValidCore.checkBoolean(split.length > 1, "wrong then discord syntax! Usage: <channel> <message>");

			final String channel = split[0];
			final String message = CommonCore.joinRange(1, split);

			this.discordMessages.put(channel, message);
		}

		else if ("then write".equals(param)) {
			final String[] split = theRest.split(" ");
			ValidCore.checkBoolean(split.length > 1, "wrong 'then log' syntax! Usage: <file (without spaces)> <message>");

			final String file = split[0];
			final String message = CommonCore.joinRange(1, split);

			this.writeMessages.put(file, message);
		}

		else if ("then warn".equals(param))
			this.warnMessages.put(UUID.randomUUID(), SimpleComponent.fromMiniAmpersand(theRest));

		else if ("then abort".equals(param)) {
			ValidCore.checkBoolean(!this.abort, "then abort already used on " + this);

			this.abort = true;
		}

		else if ("then deny".equals(param)) {
			if ("silently".equals(theRest)) {
				ValidCore.checkBoolean(!this.cancelMessageSilently, "then deny silently already used on " + this);

				this.cancelMessageSilently = true;

			} else {
				ValidCore.checkBoolean(!this.cancelMessage, "then deny already used on " + this);

				this.cancelMessage = true;
			}
		}

		else if ("require playedbefore".equals(param)) {
			ValidCore.checkBoolean(!this.requirePlayedBefore, "require playedbefore already used on " + this);

			this.requirePlayedBefore = true;
		}

		else if ("ignore playedbefore".equals(param)) {
			ValidCore.checkBoolean(!this.ignorePlayedBefore, "ignore playedbefore already used on " + this);

			this.ignorePlayedBefore = true;
		}

		else if ("dont log".equals(param)) {
			ValidCore.checkBoolean(!this.ignoreLogging, "dont log already used on " + this);

			this.ignoreLogging = true;
		}

		else if ("dont verbose".equals(param)) {
			ValidCore.checkBoolean(!this.ignoreVerbose, "dont verbose already used on " + this);

			this.ignoreVerbose = true;
		}

		else if ("disabled".equals(args[0])) {
			ValidCore.checkBoolean(!this.disabled, "'disabled' already used on " + this);

			this.disabled = true;
		}

		else {
			final boolean success = this.onParse(param, theRest, args);

			ValidCore.checkBoolean(success, "Unrecognized operator '" + String.join(" ", args) + "' found in " + this);
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
		ValidCore.checkBoolean(value == null, "Operator '" + type + "' already defined on " + this);
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
	protected SerializedMap collectOptions() {
		return SerializedMap.fromArray(
				//"Require Keys", this.requireData,
				//"Ignore Keys", this.ignoreData,
				//"Save Keys", this.saveData,
				"Expires", this.expires != -1 ? this.expires : null,
				"Delay", this.delay,
				"Player Commands", this.playerCommands,
				//"Console Commands", this.consoleCommands,
				"Proxy Commands", this.proxyCommands,
				"Console Messages", this.consoleMessages,
				"Kick Message", this.kickMessage != null ? this.kickMessage.toPlain() : null,
				//"Toast Message", this.toast,
				//"Notify Messages", this.notifyMessages,
				"Discord Message", this.discordMessages,
				"Log To File", this.writeMessages,
				//"Fine", this.fine,
				//"Warning Points", this.warningPoints,
				//"Sounds", this.sounds,
				//"Book", this.book,
				//"Title", this.title,
				//"Action Bar", this.actionBar,
				//"Boss Bar", this.bossBar == null ? null : this.bossBar.toString(),
				"Warn Messages", this.warnMessages.size(),
				"Abort", this.abort,
				"Cancel Message", this.cancelMessage,
				"Cancel Message Silently", this.cancelMessageSilently,
				"Require Played Before", this.requirePlayedBefore,
				"Ignore Played Before", this.ignorePlayedBefore,
				//"Require Discord", this.requireDiscord,
				"Ignore Logging", this.ignoreLogging,
				"Ignore Verbose", this.ignoreVerbose,
				//"Ignore Discord", this.ignoreDiscord,
				"Disabled", this.disabled

		);
	}

	/**
	 * Return a tostring representation suitable to show in game
	 *
	 * @return
	 */
	public final String toDisplayableString() {
		return CompChatColor.stripColorCodes(this.toString().replace("\t", "    "));
	}

	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public final boolean equals(final Object obj) {
		return obj instanceof ProxyOperator && ((ProxyOperator) obj).getUniqueName().equals(this.getUniqueName());
	}

	// ------------------------------------------------------------------------------------------------------------
	// Classes
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Represents a check that is implemented by this class
	 * @param <T>
	 */
	public abstract static class OperatorCheck<T extends ProxyOperator> {

		/**
		 * Variables available at all times
		 */
		private final Map<String, Object> placeholders;

		/**
		 * The sender Audience
		 */
		protected FoundationPlayer audience;

		/**
		 * Should we cancel the event silently and only send the message
		 * to the sender himself?
		 */
		@Getter
		protected boolean cancelledSilently;

		/**
		 * Construct check and useful parameters
		 *
		 * @param sender
		 * @param message
		 */
		protected OperatorCheck(@NonNull final FoundationPlayer audience, final Map<String, Object> placeholders) {
			this.placeholders = placeholders;

			this.audience = audience;
		}

		public final void start() {

			// Collect all to filter
			final List<T> operators = this.getOperators();

			// Iterate through all rules and parse
			for (final T operator : operators)
				try {
					if (!this.canFilter(operator))
						continue;

					this.filter(operator);

				} catch (final OperatorAbortException ex) {
					// Verbose
					if (!operator.isIgnoreVerbose())
						this.verbose("&cStopping further operator check.");

					break;

				} catch (final EventHandledException ex) {
					throw ex; // send upstream

				} catch (final Throwable t) {
					CommonCore.throwError(t, "Error parsing rule: " + operator);
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
		private final boolean canFilter(final T operator) {

			// Ignore disabled rules
			if (operator.isDisabled())
				return false;

			// Expired
			if (operator.getExpires() != -1 && System.currentTimeMillis() > operator.getExpires())
				return false;

			final ProxyServerCache cache = ProxyServerCache.getInstance();

			if (operator.isRequirePlayedBefore() && this.audience.isPlayer() && !cache.isPlayerRegistered(this.audience.getUniqueId()))
				return false;

			if (operator.isIgnorePlayedBefore() && this.audience.isPlayer() && cache.isPlayerRegistered(this.audience.getUniqueId()))
				return false;

			return true;
		}

		/**
		 * Run given operators for the given message and return the updated message
		 */
		protected void executeOperators(final T operator) throws EventHandledException {

			if (this.audience.isPlayer())
				for (final String command : operator.getPlayerCommands())
					this.audience.dispatchCommand(this.replaceVariablesLegacy(command, operator));

			for (final String commandLine : operator.getProxyCommands()) {
				final String command = CommonCore.joinRange(0, commandLine.split(" "));

				Platform.dispatchConsoleCommand(this.audience, this.replaceVariablesLegacy(command, operator));
			}

			for (final String message : operator.getConsoleMessages())
				CommonCore.log(this.replaceVariablesLegacy(CompChatColor.translateColorCodes(message), operator));

			for (final Map.Entry<String, String> entry : operator.getWriteMessages().entrySet()) {
				final String file = entry.getKey();
				final String message = this.replaceVariablesLegacy(entry.getValue(), operator);

				FileUtil.writeFormatted(file, "", message);
			}

			if (this.audience.isPlayer() && operator.getKickMessage() != null) {
				final SimpleComponent kickReason = this.replaceVariables(operator.getKickMessage(), operator);

				this.audience.kick(kickReason);
			}

			// Dirty: Run later including when EventHandledException is thrown
			if (!operator.getWarnMessages().isEmpty())
				Platform.runTask(1, () -> {
					for (final Entry<UUID, SimpleComponent> entry : operator.getWarnMessages().entrySet()) {
						final SimpleComponent warnMessage = RandomUtil.nextItem(entry.getValue());

						this.audience.sendMessage(this.replaceVariables(warnMessage, operator));
					}
				});

			if (operator.isCancelMessage()) {
				if (!operator.isIgnoreVerbose())
					this.verbose("&cOriginal message cancelled.");

				throw new EventHandledException(true);
			}

			if (operator.isCancelMessageSilently())
				this.cancelledSilently = true;
		}

		/**
		 * Replace all kinds of check variables
		 */
		protected SimpleComponent replaceVariables(@NonNull final SimpleComponent component, final T operator) {
			return Variables.builder(this.audience).placeholders(this.prepareVariables(operator)).replaceComponent(component);
		}

		/**
		 * Replace all kinds of check variables
		 */
		protected String replaceVariablesLegacy(@NonNull final String message, final T operator) {
			return Variables.builder(this.audience).placeholders(this.prepareVariables(operator)).replaceLegacy(message);
		}

		/**
		 * Prepare variables available in this check
		 *
		 * @param operator
		 * @return
		 */
		protected Map<String, Object> prepareVariables(final T operator) {
			final Map<String, Object> map = SyncedCache.getPlaceholders(this.audience, PlaceholderPrefix.PLAYER);

			map.putAll(this.placeholders);

			return map;
		}

		/**
		 * Return if the sender has the given permission
		 *
		 * @param permission
		 * @return
		 */
		protected final boolean hasSenderPerm(final String permission) {
			return this.audience.hasPermission(permission);
		}

		/**
		 * Cancels the pipeline by throwing a {@link EventHandledException}
		 */
		protected final void cancel() {
			this.cancel(null, null);
		}

		/**
		 * Cancels the pipeline by throwing a {@link EventHandledException}
		 * and send an error message to the player
		 *
		 * @param errorMessage
		 * @param operator
		 */
		protected final void cancel(final SimpleComponent errorMessage, final T operator) {
			if (errorMessage != null && !errorMessage.toPlain().isEmpty())
				Messenger.error(this.audience, Variables.builder(this.audience).placeholders(this.prepareVariables(operator)).replaceComponent(errorMessage));

			throw new EventHandledException(true);
		}

		/**
		 * Show the message if rules are set to verbose
		 *
		 * @param message
		 */
		protected final void verbose(final String... messages) {
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
