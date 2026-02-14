package org.mineacademy.chatcontrol.operator;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.ChatControlProxyMessage;
import org.mineacademy.chatcontrol.model.Discord;
import org.mineacademy.chatcontrol.model.Format;
import org.mineacademy.chatcontrol.model.Mute;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.PlaceholderPrefix;
import org.mineacademy.chatcontrol.model.PlayerMessageType;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.model.ProxyChat;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.ProxyUtil;
import org.mineacademy.fo.RandomUtil;
import org.mineacademy.fo.ReflectionUtil;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.exception.FoScriptException;
import org.mineacademy.fo.model.CompChatColor;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.JavaScriptExecutor;
import org.mineacademy.fo.model.RandomNoRepeatPicker;
import org.mineacademy.fo.model.RequireVariable;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.region.DiskRegion;

import lombok.Getter;
import lombok.NonNull;

/**
 * Represents an operator that has require/ignore for both sender and receiver
 * Used for join/leave/kick/death messages yo
 */
@Getter
public abstract class PlayerMessage extends Operator {

	/**
	 * The type of this message
	 */
	@Getter
	private final PlayerMessageType type;

	/**
	 * The name of this message group
	 */
	private final String group;

	/**
	 * Permission required for the player that caused the rule to fire in
	 * order for the rule to apply
	 */

	private Tuple<String, SimpleComponent> requireSenderPermission;

	/**
	 * Permission required for receivers of the message of the rule
	 */

	private Tuple<String, SimpleComponent> requireReceiverPermission;

	/**
	 * JavaScript boolean output required to be true for the rule to apply
	 */
	private String requireSenderScript;

	/**
	 * JavaScript boolean output required to be true for the rule to apply
	 */
	private String requireReceiverScript;

	/**
	 * JavaScript boolean output required to be true for the rule to apply
	 */
	private RequireVariable requireSenderVariable;

	/**
	 * JavaScript boolean output required to be true for the rule to apply
	 */
	private RequireVariable requireReceiverVariable;

	/**
	 * Gamemodes to ignore
	 */
	private final Set<GameMode> requireSenderGamemodes = new HashSet<>();

	/**
	 * Gamemodes to ignore
	 */
	private final Set<GameMode> requireReceiverGamemodes = new HashSet<>();

	/**
	 * World names to require
	 */
	private final Set<String> requireSenderWorlds = new HashSet<>();

	/**
	 * World names to require
	 */
	private final Set<String> requireReceiverWorlds = new HashSet<>();

	/**
	 * Region names to require
	 */
	private final Set<String> requireSenderRegions = new HashSet<>();

	/**
	 * Region names to require
	 */
	private final Set<String> requireReceiverRegions = new HashSet<>();

	/**
	 * List of channels to require matching from
	 */
	private final Set<String> requireSenderChannels = new HashSet<>();

	/**
	 * List of channels to require matching from
	 */
	private final Set<String> requireReceiverChannels = new HashSet<>();

	/**
	 * Should the message only be sent to the sending player?
	 */
	private boolean requireSelf;

	/**
	 * Should the message not be sent to the sending player?
	 */
	private boolean ignoreSelf;

	/**
	 * Permission to bypass the rule
	 */

	private String ignoreSenderPermission;

	/**
	 * Permission to bypass the rule
	 */

	private String ignoreReceiverPermission;

	/**
	 * The match that, if matched against a given message, will make the rule be ignored
	 */

	private Pattern ignoreMatch;

	/**
	 * JavaScript boolean output when true for the rule to bypass
	 */

	private String ignoreSenderScript;

	/**
	 * JavaScript boolean output when true for the rule to bypass
	 */

	private String ignoreReceiverScript;

	/**
	 * Gamemodes to ignore
	 */
	private final Set<GameMode> ignoreSenderGamemodes = new HashSet<>();

	/**
	 * Gamemodes to ignore
	 */
	private final Set<GameMode> ignoreReceiverGamemodes = new HashSet<>();

	/**
	 * World names to ignore
	 */
	private final Set<String> ignoreSenderWorlds = new HashSet<>();

	/**
	 * World names to ignore
	 */
	private final Set<String> ignoreReceiverWorlds = new HashSet<>();

	/**
	 * Region names to ignore
	 */
	private final Set<String> ignoreSenderRegions = new HashSet<>();

	/**
	 * Region names to ignore
	 */
	private final Set<String> ignoreReceiverRegions = new HashSet<>();

	/**
	 * List of channels to ignore matching from
	 */
	private final Set<String> ignoreSenderChannels = new HashSet<>();

	/**
	 * List of channels to ignore matching from
	 */
	private final Set<String> ignoreReceiverChannels = new HashSet<>();

	/**
	 * The suffix for the {@link #messages}
	 */
	private String prefix;

	/**
	 * The suffix for the {@link #messages}
	 */
	private String suffix;

	/**
	 * Shall we also broadcast the message to the network?
	 */
	private boolean proxy;

	/**
	 * Should the message selection be random?
	 */
	private boolean randomMessage;

	/**
	 * List of commands to run as console for each receiver that receives this message
	 */
	private final List<String> consoleForEachCommands = new ArrayList<>();

	/**
	 * The list of messages whereof we use {@link RandomNoRepeatPicker} to pick one at the time
	 * until we run out of them to prevent random repeating
	 */
	@Getter
	private final List<String> messages = new ArrayList<>();

	/*
	 * A special flag to indicate we are about to load messages
	 */
	private boolean loadingMessages = false;

	/*
	 * Used to compute messages
	 */
	private int lastMessageIndex = 0;

	protected PlayerMessage(final PlayerMessageType type, final String group) {
		this.type = type;
		this.group = group;
	}

	/**
	 * Return the next message in a cyclic repetition
	 *
	 * @return
	 */
	public final String getNextMessage() {
		if (this.messages.isEmpty())
			return "";

		if (this.randomMessage)
			return RandomUtil.nextItem(this.messages);

		if (this.messages.size() == 1) {
			this.lastMessageIndex = 0;

			return this.messages.get(0);
		}

		if (this.lastMessageIndex >= this.messages.size())
			this.lastMessageIndex = 0;

		return this.messages.get(this.lastMessageIndex++);
	}

	/**
	 * Return the prefix or the default one if not set
	 *
	 * @return the prefix
	 */
	public String getPrefix() {
		final String raw = CommonCore.getOrDefaultStrict(this.prefix, Settings.Messages.PREFIX.get(this.type));

		return CommonCore.getOrEmpty(raw);
	}

	/**
	 * Return the name of this group
	 */
	@Override
	public final String getUniqueName() {
		return this.group;
	}

	/**
	 * @see org.mineacademy.fo.model.Rule#getFile()
	 */
	@Override
	public final File getFile() {
		return FileUtil.getFile("messages/" + this.type.getKey() + ".rs");
	}

	/**
	 * @see org.mineacademy.chatcontrol.operator.Operator#onParse(java.lang.String, java.lang.String, java.lang.String[])
	 */
	@Override
	protected boolean onParse(String firstThreeParams, String theRestThree, final String[] args) {

		final String firstTwoParams = CommonCore.joinRange(0, 2, args, " ");
		firstThreeParams = CommonCore.joinRange(0, 3, args, " ");
		theRestThree = CommonCore.joinRange(3, args);

		final List<String> theRestThreeSplit = splitVertically(theRestThree);

		if (this.loadingMessages) {
			final String everything = String.join(" ", args).trim();

			if (everything.startsWith("- ")) {
				String line = everything.substring(1).trim();

				if (line.startsWith("\"") || line.startsWith("'"))
					line = line.substring(1);

				if (line.endsWith("\"") || line.endsWith("'"))
					line = line.substring(0, line.length() - 1);

				this.messages.add(line);

			} else {
				if (this.messages.isEmpty()) {
					Common.warning("In " + this.getFile() + " -> " + this.getUniqueName() + ", enter messages with '-' on each separate line. "
							+ "For multiline messages, use this format: https://i.imgur.com/BW88git.png. Got: " + everything);

					return true;
				}

				// Merge the line that does not start with "-", assume it is used
				// A multiline message:
				// - first line
				//   second line
				//   third line etc.
				final int index = this.messages.size() - 1;
				final String lastMessage = this.messages.get(index) + "\n" + everything;

				this.messages.set(index, lastMessage);
			}

			return true;
		}

		final String line = CommonCore.joinRange(1, args);

		if ("prefix".equals(args[0])) {
			if (this.prefix != null)
				this.prefix += "\n" + line;

			else
				this.prefix = line;
		}

		else if ("suffix".equals(args[0])) {
			if (this.suffix != null)
				this.suffix += "\n" + line;

			else
				this.suffix = line;
		}

		else if ("proxy".equals(args[0]) || "bungee".equals(args[0])) {
			checkBoolean(!this.proxy, "Operator 'bungee' can only be used once in " + this);

			this.proxy = true;
		}

		else if ("random".equals(args[0])) {
			checkBoolean(!this.randomMessage, "Operator 'random' can only be used once in " + this);

			this.randomMessage = true;
		}

		else if ("then foreach console".equals(firstThreeParams))
			this.consoleForEachCommands.addAll(theRestThreeSplit);

		else if ("message:".equals(args[0]) || "messages:".equals(args[0])) {
			checkBoolean(!this.loadingMessages, "Operator messages: can only be used once in " + this);

			this.loadingMessages = true;
		}

		else if ("require sender perm".equals(firstThreeParams) || "require sender permission".equals(firstThreeParams)) {
			this.checkNotSet(this.requireSenderPermission, "require sender perm");
			final String[] split = theRestThree.split(" ");

			this.requireSenderPermission = new Tuple<>(split[0], split.length > 1 ? SimpleComponent.fromMiniAmpersand(CommonCore.joinRange(1, split)) : null);
		}

		else if ("require receiver perm".equals(firstThreeParams) || "require receiver permission".equals(firstThreeParams)) {
			this.checkNotSet(this.requireReceiverPermission, "require receiver perm");
			final String[] split = theRestThree.split(" ");

			this.requireReceiverPermission = new Tuple<>(split[0], split.length > 1 ? SimpleComponent.fromMiniAmpersand(CommonCore.joinRange(1, split)) : null);
		}

		else if ("require sender script".equals(firstThreeParams)) {
			this.checkNotSet(this.requireSenderScript, "require sender script");

			this.requireSenderScript = theRestThree;
		}

		else if ("require receiver script".equals(firstThreeParams)) {
			this.checkNotSet(this.requireReceiverScript, "require receiver script");

			this.requireReceiverScript = theRestThree;
		}

		else if ("require sender variable".equals(firstThreeParams)) {
			this.checkNotSet(this.requireSenderVariable, "require sender variable");

			this.requireSenderVariable = RequireVariable.fromLine(theRestThree);
		}

		else if ("require receiver variable".equals(firstThreeParams)) {
			this.checkNotSet(this.requireReceiverVariable, "require receiver variable");

			this.requireReceiverVariable = RequireVariable.fromLine(theRestThree);
		}

		else if ("require sender gamemode".equals(firstThreeParams) || "require sender gamemodes".equals(firstThreeParams))
			for (final String modeName : theRestThreeSplit) {
				final GameMode gameMode = ReflectionUtil.lookupEnum(GameMode.class, modeName);

				this.requireSenderGamemodes.add(gameMode);
			}

		else if ("require receiver gamemode".equals(firstThreeParams) || "require receiver gamemodes".equals(firstThreeParams))
			for (final String modeName : theRestThreeSplit) {
				final GameMode gameMode = ReflectionUtil.lookupEnum(GameMode.class, modeName);

				this.requireReceiverGamemodes.add(gameMode);
			}

		else if ("require sender world".equals(firstThreeParams) || "require sender worlds".equals(firstThreeParams))
			this.requireSenderWorlds.addAll(theRestThreeSplit);

		else if ("require receiver world".equals(firstThreeParams) || "require receiver worlds".equals(firstThreeParams))
			this.requireReceiverWorlds.addAll(theRestThreeSplit);

		else if ("require sender region".equals(firstThreeParams) || "require sender regions".equals(firstThreeParams))
			this.requireSenderRegions.addAll(theRestThreeSplit);

		else if ("require receiver region".equals(firstThreeParams) || "require receiver regions".equals(firstThreeParams))
			this.requireReceiverRegions.addAll(theRestThreeSplit);

		else if ("require sender channel".equals(firstThreeParams) || "require sender channels".equals(firstThreeParams))
			this.requireSenderChannels.addAll(theRestThreeSplit);

		else if ("require receiver channel".equals(firstThreeParams) || "require receiver channels".equals(firstThreeParams))
			this.requireReceiverChannels.addAll(theRestThreeSplit);

		else if ("require self".equals(firstTwoParams)) {
			checkBoolean(!this.requireSelf, "'require self' option already set for " + this);

			this.requireSelf = true;
		}

		else if ("ignore self".equals(firstTwoParams)) {
			checkBoolean(!this.ignoreSelf, "'ignore self' option already set for " + this);

			this.ignoreSelf = true;
		}

		else if ("ignore string".equals(firstThreeParams)) {
			this.checkNotSet(this.ignoreMatch, "ignore receiver string");

			this.ignoreMatch = CommonCore.compilePattern(CommonCore.joinRange(2, args));
		}

		else if ("ignore sender perm".equals(firstThreeParams) || "ignore sender permission".equals(firstThreeParams)) {
			this.checkNotSet(this.ignoreSenderPermission, "ignore sender perm");

			this.ignoreSenderPermission = theRestThree;
		}

		else if ("ignore receiver perm".equals(firstThreeParams) || "ignore receiver permission".equals(firstThreeParams)) {
			this.checkNotSet(this.ignoreReceiverPermission, "ignore receiver perm");

			this.ignoreReceiverPermission = theRestThree;
		}

		else if ("ignore sender script".equals(firstThreeParams)) {
			this.checkNotSet(this.ignoreSenderScript, "ignore sender script");

			this.ignoreSenderScript = theRestThree;
		}

		else if ("ignore receiver script".equals(firstThreeParams)) {
			this.checkNotSet(this.ignoreReceiverScript, "ignore receiver script");

			this.ignoreReceiverScript = theRestThree;
		}

		else if ("ignore sender gamemode".equals(firstThreeParams) || "ignore sender gamemodes".equals(firstThreeParams))
			for (final String modeName : theRestThreeSplit) {
				final GameMode gameMode = ReflectionUtil.lookupEnum(GameMode.class, modeName);

				this.ignoreSenderGamemodes.add(gameMode);
			}

		else if ("ignore receiver gamemode".equals(firstThreeParams) || "ignore receiver gamemodes".equals(firstThreeParams))
			for (final String modeName : theRestThreeSplit) {
				final GameMode gameMode = ReflectionUtil.lookupEnum(GameMode.class, modeName);

				this.ignoreReceiverGamemodes.add(gameMode);
			}

		else if ("ignore sender world".equals(firstThreeParams) || "ignore sender worlds".equals(firstThreeParams))
			this.ignoreSenderWorlds.addAll(theRestThreeSplit);

		else if ("ignore receiver world".equals(firstThreeParams) || "ignore receiver worlds".equals(firstThreeParams))
			this.ignoreReceiverWorlds.addAll(theRestThreeSplit);

		else if ("ignore sender region".equals(firstThreeParams) || "ignore sender regions".equals(firstThreeParams))
			this.ignoreSenderRegions.addAll(theRestThreeSplit);

		else if ("ignore receiver region".equals(firstThreeParams) || "ignore receiver regions".equals(firstThreeParams))
			this.ignoreReceiverRegions.addAll(theRestThreeSplit);

		else if ("ignore sender channel".equals(firstThreeParams) || "ignore sender channels".equals(firstThreeParams))
			this.ignoreSenderChannels.addAll(theRestThreeSplit);

		else if ("ignore receiver channel".equals(firstThreeParams) || "ignore receiver channels".equals(firstThreeParams))
			this.ignoreReceiverChannels.addAll(theRestThreeSplit);

		else
			return false;

		return true;
	}

	/**
	 * Sends the given message to the receiver as if it would be send from this player message
	 *
	 * @param receiver
	 * @param message
	 */
	public void sendMessage(final WrappedSender receiver, final String message) {

		if (message.isEmpty() || "none".equals(message))
			return;

		String prefix = this.getPrefix();

		// Send message as JSON
		if ("[JSON]".equals(prefix) || message.startsWith("[JSON]")) {
			final String toSend = this.replaceVariables0(receiver.getSender(), message.startsWith("[JSON]") ? message : prefix + message);

			// Send whatever part starts with JSON
			receiver.getAudience().sendJson(toSend);
		}

		// Send as interactive format otherwise
		else {
			final String colorlessMessage = CompChatColor.stripColorCodes(message);

			// Support interactive chat
			if (Format.isInteractiveChat(colorlessMessage))
				receiver.getAudience().sendMessage(SimpleComponent.fromMiniAmpersand(message));

			else {

				// Add the main part and add prefix for all lines
				final Format format = Format.isFormatLoaded(message) ? Format.findFormat(message) : Format.parse("{message}");

				if (prefix == null || prefix.isEmpty())
					prefix = "";
				else {
					final char startChar = prefix.charAt(0);
					final char endChar = prefix.charAt(prefix.length() - 1);

					prefix = prefix + (startChar == '{' && endChar == '}' || endChar == ' ' ? "" : " ");
				}

				// Construct
				String replaced = this.replaceVariables0(receiver.getSender(), prefix + message + CommonCore.getOrEmpty(this.getSuffix()));

				// Support centering
				final String[] replacedLines = replaced.split("\n");

				for (int i = 0; i < replacedLines.length; i++) {
					final String line = replacedLines[i];

					if (CompChatColor.stripColorCodes(line).startsWith("<center>"))
						replacedLines[i] = ChatUtil.center(line.replace("<center>", "").trim());
				}

				replaced = String.join("\n", replacedLines);

				// Build again
				final SimpleComponent component;

				try {
					component = format.build(receiver, CommonCore.newHashMap("message", message));

				} catch (final EventHandledException ex) {
					return; // canceled?
				}

				// Send
				receiver.getAudience().sendMessage(component);
			}
		}
	}

	private String replaceVariables0(final CommandSender receiver, final String message) {
		return Variables.builder()
				.audience(receiver)
				.placeholderArray(
						"broadcast_group", this.getGroup(),
						"original_message", message,
						"message", message)
				.replaceLegacy(message);
	}

	/**
	 * Collect all options we have to debug
	 *
	 * @return
	 */
	@Override
	protected Map<String, Object> collectOptions() {
		return CommonCore.newHashMap(
				"Type", this.type,
				"Group", this.group,
				"Prefix", this.prefix,
				"Suffix", this.suffix,
				"Proxy", this.proxy,
				"Random", this.randomMessage,
				"Console For Each Commands", this.consoleForEachCommands,
				"Messages", this.messages,

				"Require Sender Permission", this.requireSenderPermission,
				"Require Sender Script", this.requireSenderScript,
				"Require Sender Variable", this.requireSenderVariable,
				"Require Sender Gamemodes", this.requireSenderGamemodes,
				"Require Sender Worlds", this.requireSenderWorlds,
				"Require Sender Regions", this.requireSenderRegions,
				"Require Sender Channels", this.requireSenderChannels,

				"Require Receiver Permission", this.requireReceiverPermission,
				"Require Receiver Script", this.requireReceiverScript,
				"Require Receiver Variable", this.requireReceiverVariable,
				"Require Receiver Gamemodes", this.requireReceiverGamemodes,
				"Require Receiver Worlds", this.requireReceiverWorlds,
				"Require Receiver Regions", this.requireReceiverRegions,
				"Require Receiver Channels", this.requireReceiverChannels,

				"Require Self", this.requireSelf,
				"Ignore Self", this.ignoreSelf,
				"Ignore Match", this.ignoreMatch,

				"Ignore Sender Permission", this.ignoreSenderPermission,
				"Ignore Sender Script", this.ignoreSenderScript,
				"Ignore Sender Regions", this.ignoreSenderRegions,
				"Ignore Sender Gamemodes", this.ignoreSenderGamemodes,
				"Ignore Sender Worlds", this.ignoreSenderWorlds,
				"Ignore Sender Channels", this.ignoreSenderChannels,

				"Ignore Receiver Permission", this.ignoreReceiverPermission,
				"Ignore Receiver Regions", this.ignoreReceiverRegions,
				"Ignore Receiver Script", this.ignoreReceiverScript,
				"Ignore Receiver Gamemodes", this.ignoreReceiverGamemodes,
				"Ignore Receiver Worlds", this.ignoreReceiverWorlds,
				"Ignore Receiver Channels", this.ignoreReceiverChannels

		);
	}

	/* ------------------------------------------------------------------------------- */
	/* Classes */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Represents a check that is implemented by this class
	 * @param <T>
	 */
	public static abstract class PlayerMessageCheck<T extends PlayerMessage> extends OperatorCheck<T> {

		/**
		 * The message type
		 */
		protected final PlayerMessageType type;

		/**
		 * Players who have seen at least one message (we prevent players
		 * from seeing more than one message at a time)
		 */
		private final Set<UUID> messageReceivers = new HashSet<>();

		/**
		 * The current iterated receiver
		 */
		protected WrappedSender wrappedReceiver;

		/**
		 * Pick one message randomly from the list to show to all players equally
		 */
		protected String pickedMessage;

		/**
		 * @param wrapped
		 * @param message
		 */
		protected PlayerMessageCheck(final PlayerMessageType type, final WrappedSender wrapped, final String message) {
			super(wrapped, message);

			this.type = type;
		}

		/**
		 * Set variables for the receiver when he is iterated and shown messages to
		 *
		 * @param receiver
		 */
		protected void setVariablesFor(@NonNull final Player receiver) {
			this.wrappedReceiver = WrappedSender.fromPlayer(receiver);
		}

		/**
		 * @see org.mineacademy.chatcontrol.operator.Operator.OperatorCheck#filter(org.mineacademy.chatcontrol.operator.Operator)
		 */
		@Override
		protected void filter(final T message) throws EventHandledException {

			Debugger.debug("operator", "FILTERING " + message.getUniqueName());

			// Ignore
			if (message.getIgnoreMatch() != null && message.getIgnoreMatch().matcher(this.message).find()) {
				Debugger.debug("operator", "\tignore match found");

				return;
			}

			// Delay
			if (message.getDelay() != null && !this.isDebugRun()) {
				final SimpleTime time = message.getDelay().getKey();
				final long now = System.currentTimeMillis();

				// Round the number due to Bukkit scheduler lags
				final long delay = Math.round((now - message.getLastExecuted()) / 1000D);

				// Prevent reloading spamming all messages
				if (message.getLastExecuted() == -1 && this.type == PlayerMessageType.TIMED) {
					Debugger.debug("operator", "\tprevented reload spam and rescheduled");
					message.setLastExecuted(now);

					return;
				}

				if (delay < time.getTimeSeconds()) {
					Debugger.debug("operator", "\tbefore delay: " + delay + " threshold: " + time.getTimeSeconds());

					return;
				}

				message.setLastExecuted(now);
			}

			// Player Delay
			if (message.getPlayerDelay() != null && !this.isDebugRun() && this.wrappedSender != null && this.wrappedSender.isPlayer()) {
				final SimpleTime time = message.getPlayerDelay().getKey();
				final long now = System.currentTimeMillis();

				// Round the number due to Bukkit scheduler lags
				final long delay = Math.round((now - message.getLastExecuted(this.wrappedSender.getSender())) / 1000D);

				if (delay < time.getTimeSeconds()) {
					Debugger.debug("operator", "\tbefore player delay: " + delay + " threshold: " + time.getTimeSeconds());

					return;
				}

				message.setLastExecuted(this.wrappedSender.getSender());
			}

			boolean pickedMessage = false;

			for (final Player player : Players.getOnlinePlayersWithLoadedDb()) {
				if (this.wrappedSender != null) {
					if (message.isRequireSelf() && !this.wrappedSender.getSender().equals(player))
						continue;

					if (message.isIgnoreSelf() && this.wrappedSender.getSender().equals(player))
						continue;
				}

				if (this.messageReceivers.contains(player.getUniqueId()) && Settings.Messages.STOP_ON_FIRST_MATCH.contains(this.type)) {
					Debugger.debug("operator", "\t" + player.getName() + " already received a message");

					continue;
				}

				this.setVariablesFor(player);

				if (this.wrappedReceiver.getPlayerCache().isIgnoringMessage(message) || this.wrappedReceiver.getPlayerCache().isIgnoringMessages(message.getType())) {
					Debugger.debug("operator", "\t" + player.getName() + " s ignoring this");

					continue;
				}

				// Filter for each player
				if (!this.canFilter(message)) {
					Debugger.debug("operator", "\tcanFilter returned false for " + player.getName());

					continue;
				}

				// Pick the message ONLY if it can be shown to at least ONE player
				if (!pickedMessage) {
					this.pickedMessage = message.getNextMessage();

					pickedMessage = true;
				}

				// Execute main operators
				this.executeOperators(message);
			}

			// Execute sender-only operators (discord, console, log, etc.) even when no online receivers exist
			if (!pickedMessage && this.wrappedSender != null && this.canFilterSender(message)) {
				this.pickedMessage = message.getNextMessage();

				this.executeSenderOnlyOperators(message);
			}
		}

		/*
		 * Check only sender-side conditions, used when there are no online receivers
		 * to still execute sender-only operators like then discord, then console, etc.
		 */
		private boolean canFilterSender(final T operator) {

			// Base checks (disabled, begins/expires, discord, muted, data)
			if (!super.canFilter(operator))
				return false;

			if (operator.getRequireSenderPermission() != null && !this.wrappedSender.hasPermission(operator.getRequireSenderPermission().getKey()))
				return false;

			if (operator.getIgnoreSenderPermission() != null && this.wrappedSender.hasPermission(operator.getIgnoreSenderPermission()))
				return false;

			if (operator.getRequireSenderScript() != null) {
				try {
					final Object result = JavaScriptExecutor.run(this.replaceSenderVariablesLegacy(operator.getRequireSenderScript(), operator), this.wrappedSender.getAudience());

					if (result instanceof Boolean && !((boolean) result))
						return false;

				} catch (final FoScriptException ex) {
					CommonCore.logFramed(
							"Error parsing 'require sender script' in player message",
							"Message " + operator.getUniqueName() + " in " + operator.getFile(),
							"",
							"Raw script: " + operator.getRequireSenderScript(),
							"Sender: " + this.wrappedSender,
							"Error: " + ex.getMessage(),
							"",
							"Check that the evaluated script",
							"above is a valid JavaScript!");

					throw ex;
				}
			}

			if (operator.getIgnoreSenderScript() != null) {
				try {
					final Object result = JavaScriptExecutor.run(this.replaceSenderVariablesLegacy(operator.getIgnoreSenderScript(), operator), this.wrappedSender.getAudience());

					if (result instanceof Boolean && ((boolean) result))
						return false;

				} catch (final FoScriptException ex) {
					CommonCore.logFramed(
							"Error parsing 'ignore sender script' in player message",
							"Message " + operator.getUniqueName() + " in " + operator.getFile(),
							"",
							"Raw script: " + operator.getIgnoreSenderScript(),
							"Sender: " + this.wrappedSender,
							"Error: " + ex.getMessage(),
							"",
							"Check that the evaluated script",
							"above is a valid JavaScript!");

					throw ex;
				}
			}

			if (operator.getRequireSenderVariable() != null && !operator.getRequireSenderVariable().matches(variable -> this.replaceSenderVariablesLegacy(variable, operator)))
				return false;

			if (this.wrappedSender.isPlayer()) {
				if (!operator.getRequireSenderGamemodes().isEmpty() && !operator.getRequireSenderGamemodes().contains(this.wrappedSender.getPlayer().getGameMode()))
					return false;

				if (!operator.getRequireSenderWorlds().isEmpty() && !ValidCore.isInList(this.wrappedSender.getPlayer().getWorld().getName(), operator.getRequireSenderWorlds()))
					return false;

				if (!operator.getRequireSenderRegions().isEmpty()) {
					final List<String> regions = DiskRegion.findRegionNames(this.wrappedSender.getPlayer().getLocation());
					boolean found = false;

					for (final String requireRegionName : operator.getRequireSenderRegions())
						if (regions.contains(requireRegionName)) {
							found = true;

							break;
						}

					if (!found)
						return false;
				}

				if (!operator.getRequireSenderChannels().isEmpty()) {
					boolean atLeastInOne = false;

					for (final String channelName : operator.getRequireSenderChannels())
						if (this.wrappedSender.getPlayerCache().isInChannel(channelName)) {
							atLeastInOne = true;

							break;
						}

					if (!atLeastInOne)
						return false;
				}

				if (operator.getIgnoreSenderGamemodes().contains(this.wrappedSender.getPlayer().getGameMode()))
					return false;

				if (operator.getIgnoreSenderWorlds().contains(this.wrappedSender.getPlayer().getWorld().getName()))
					return false;

				for (final String playersRegion : DiskRegion.findRegionNames(this.wrappedSender.getPlayer().getLocation()))
					if (operator.getIgnoreSenderRegions().contains(playersRegion))
						return false;

				for (final String channelName : operator.getIgnoreSenderChannels())
					if (this.wrappedSender.getPlayerCache().isInChannel(channelName))
						return false;
			}

			return true;
		}

		/*
		 * Execute only the sender-side one-time operators that do not require an
		 * online receiver, such as then discord, then console, then log, then write.
		 */
		private void executeSenderOnlyOperators(final T operator) {
			Debugger.debug("operator", "EXECUTING sender-only operators for " + operator.getUniqueName() + " (no online receivers)");

			if (!this.wrappedSender.isConsole())
				for (final String command : operator.getConsoleCommands()) {
					final String picked = RandomUtil.nextItem(splitVertically(command));

					Platform.dispatchConsoleCommand(this.wrappedSender.getAudience(), this.replaceSenderVariablesLegacy(picked, operator));
				}

			for (final String message : operator.getConsoleMessages())
				CommonCore.log(SimpleComponent.fromMiniAmpersand(this.replaceSenderVariablesLegacy(RandomUtil.nextItem(splitVertically(message)), operator)).toLegacySection());

			for (final Map.Entry<String, String> entry : operator.getNotifyMessages().entrySet()) {
				final String permission = entry.getKey();
				final String formatOrMessage = this.replaceSenderVariablesLegacy(entry.getValue(), operator);

				final Format format = Format.parse(formatOrMessage);
				final SimpleComponent component = format.build(this.wrappedSender, this.prepareVariables(this.wrappedSender, operator));

				if (Settings.Proxy.ENABLED)
					ProxyUtil.sendPluginMessage(ChatControlProxyMessage.NOTIFY, permission, component);
			}

			if (HookManager.isDiscordSRVLoaded())
				for (final Map.Entry<Long, List<String>> entry : operator.getDiscordMessages().entrySet()) {
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

		/**
		 * @see org.mineacademy.chatcontrol.operator.Operator.OperatorCheck#canFilter(org.mineacademy.chatcontrol.operator.Operator)
		 */
		@Override
		protected boolean canFilter(final T operator) {
			Debugger.debug("operator", "CAN FILTER message " + operator.getUniqueName());

			// ----------------------------------------------------------------
			// Require
			// ----------------------------------------------------------------

			if (operator.getRequireSenderPermission() != null) {
				final String permission = operator.getRequireSenderPermission().getKey();
				final SimpleComponent noPermissionMessage = operator.getRequireSenderPermission().getValue();

				if (!this.wrappedSender.hasPermission(permission)) {
					if (noPermissionMessage != null) {
						this.wrappedSender.getAudience().sendMessage(this.replaceSenderVariables(noPermissionMessage, operator));

						throw new EventHandledException(true);
					}

					Debugger.debug("operator", "\tno required sender permission");
					return false;
				}
			}

			if (operator.getRequireReceiverPermission() != null) {
				final String permission = operator.getRequireReceiverPermission().getKey();
				final SimpleComponent noPermissionMessage = operator.getRequireReceiverPermission().getValue();

				if (!this.wrappedReceiver.hasPermission(permission)) {
					if (noPermissionMessage != null) {
						this.wrappedReceiver.getAudience().sendMessage(this.replaceReceiverVariables(noPermissionMessage, operator));

						throw new EventHandledException(true);
					}

					Debugger.debug("operator", "\tno required receiver permission");
					return false;
				}
			}

			if (operator.getRequireSenderScript() != null) {
				final Object result;

				try {
					result = JavaScriptExecutor.run(this.replaceSenderVariablesLegacy(operator.getRequireSenderScript(), operator), this.wrappedSender.getAudience());

				} catch (final FoScriptException ex) {
					CommonCore.logFramed(
							"Error parsing 'require sender script' in player message",
							"Message " + operator.getUniqueName() + " in " + operator.getFile(),
							"",
							"Raw script: " + operator.getRequireSenderScript(),
							"Evaluated script with variables replaced: '" + this.replaceSenderVariablesLegacy(operator.getRequireSenderScript(), operator) + "'",
							"Sender: " + this.wrappedSender,
							"Error: " + ex.getMessage(),
							"",
							"Check that the evaluated script",
							"above is a valid JavaScript!");

					throw ex;
				}

				if (result != null) {
					checkBoolean(result instanceof Boolean, "require sender script condition must return boolean not " + (result == null ? "null" : result.getClass()) + " for rule " + operator);

					if (!((boolean) result)) {
						Debugger.debug("operator", "\tno required sender script");

						return false;
					}
				}
			}

			if (operator.getRequireSenderVariable() != null)
				if (!operator.getRequireSenderVariable().matches(variable -> this.replaceSenderVariablesLegacy(variable, operator)))
					return false;

			if (operator.getRequireReceiverScript() != null) {
				final Object result;

				try {
					result = JavaScriptExecutor.run(this.replaceReceiverVariablesLegacy(operator.getRequireReceiverScript(), operator), this.wrappedReceiver.getAudience());

				} catch (final FoScriptException ex) {
					CommonCore.logFramed(
							"Error parsing 'require receiver script' in player message",
							"Message " + operator.getUniqueName() + " in " + operator.getFile(),
							"",
							"Raw script: " + operator.getRequireReceiverScript(),
							"Evaluated script with variables replaced: '" + this.replaceSenderVariablesLegacy(operator.getRequireReceiverScript(), operator) + "'",
							"Sender: " + this.wrappedSender,
							"Error: " + ex.getMessage(),
							"",
							"Check that the evaluated script",
							"above is a valid JavaScript!");

					throw ex;
				}

				if (result != null) {
					checkBoolean(result instanceof Boolean, "require receiver script condition must return boolean not " + (result == null ? "null" : result.getClass()) + " for rule " + operator);

					if (!((boolean) result)) {
						Debugger.debug("operator", "\tno required receiver script");

						return false;
					}
				}
			}

			if (operator.getRequireReceiverVariable() != null)
				if (!operator.getRequireReceiverVariable().matches(variable -> this.replaceReceiverVariablesLegacy(variable, operator)))
					return false;

			if (this.wrappedSender.isPlayer()) {
				if (Mute.isSomethingMutedIf(true, this.wrappedSender) && Settings.Mute.SOFT_HIDE && this.type != PlayerMessageType.TIMED && !this.wrappedReceiver.getName().equals(this.wrappedSender.getName()) && !this.wrappedReceiver.hasPermission(Permissions.Bypass.MUTE))
					return false;

				if (!operator.getRequireSenderGamemodes().isEmpty() && !operator.getRequireSenderGamemodes().contains(this.wrappedSender.getPlayer().getGameMode())) {
					Debugger.debug("operator", "\trequire sender gamemodes found");

					return false;
				}

				if (!operator.getRequireSenderWorlds().isEmpty() && !ValidCore.isInList(this.wrappedSender.getPlayer().getWorld().getName(), operator.getRequireSenderWorlds())) {
					Debugger.debug("operator", "\tno required sender worlds");

					return false;
				}

				if (!operator.getRequireSenderRegions().isEmpty()) {
					final List<String> regions = DiskRegion.findRegionNames(this.wrappedSender.getPlayer().getLocation());
					boolean found = false;

					for (final String requireRegionName : operator.getRequireSenderRegions())
						if (regions.contains(requireRegionName)) {
							found = true;

							break;
						}

					if (!found) {
						Debugger.debug("operator", "\tno required sender regions");

						return false;
					}
				}

				if (!operator.getRequireSenderChannels().isEmpty()) {
					boolean atLeastInOne = false;

					for (final String channelName : operator.getRequireSenderChannels())
						if (this.wrappedSender.getPlayerCache().isInChannel(channelName)) {
							atLeastInOne = true;

							break;
						}

					if (!atLeastInOne)
						return false;
				}
			}

			if (!operator.getRequireReceiverGamemodes().isEmpty() && !operator.getRequireReceiverGamemodes().contains(this.wrappedReceiver.getPlayer().getGameMode())) {
				Debugger.debug("operator", "\trequire receiver gamemodes found");

				return false;
			}

			if (!operator.getRequireReceiverWorlds().isEmpty() && !ValidCore.isInList(this.wrappedReceiver.getPlayer().getWorld().getName(), operator.getRequireReceiverWorlds())) {
				Debugger.debug("operator", "\tno required receiver worlds");

				return false;
			}

			if (!operator.getRequireReceiverRegions().isEmpty()) {
				final List<String> regions = DiskRegion.findRegionNames(this.wrappedReceiver.getPlayer().getLocation());
				boolean found = false;

				for (final String requireRegionName : operator.getRequireReceiverRegions())
					if (regions.contains(requireRegionName)) {
						found = true;

						break;
					}

				if (!found) {
					Debugger.debug("operator", "\tno required receiver regions");

					return false;
				}
			}

			if (!operator.getRequireReceiverChannels().isEmpty()) {
				boolean atLeastInOne = false;

				for (final String channelName : operator.getRequireReceiverChannels())
					if (this.wrappedReceiver.getPlayerCache().isInChannel(channelName)) {
						atLeastInOne = true;

						break;
					}

				if (!atLeastInOne)
					return false;
			}

			// ----------------------------------------------------------------
			// Ignore
			// ----------------------------------------------------------------

			if (operator.getIgnoreSenderPermission() != null && this.wrappedSender.hasPermission(operator.getIgnoreSenderPermission())) {
				Debugger.debug("operator", "\tignore sender permission found");

				return false;
			}

			if (operator.getIgnoreReceiverPermission() != null && this.wrappedReceiver.hasPermission(operator.getIgnoreReceiverPermission())) {
				Debugger.debug("operator", "\tignore receiver permission found");

				return false;
			}

			if (operator.getIgnoreSenderScript() != null) {
				final Object result;

				try {
					result = JavaScriptExecutor.run(this.replaceSenderVariablesLegacy(operator.getIgnoreSenderScript(), operator), this.wrappedSender.getAudience());

				} catch (final FoScriptException ex) {
					CommonCore.logFramed(
							"Error parsing 'ignore sender script' in player message",
							"Message " + operator.getUniqueName() + " in " + operator.getFile(),
							"",
							"Raw script: " + operator.getIgnoreSenderScript(),
							"Evaluated script with variables replaced: '" + this.replaceSenderVariablesLegacy(operator.getIgnoreSenderScript(), operator) + "'",
							"Sender: " + this.wrappedSender,
							"Error: " + ex.getMessage(),
							"",
							"Check that the evaluated script",
							"above is a valid JavaScript!");

					throw ex;
				}

				if (result != null) {
					checkBoolean(result instanceof Boolean, "ignore sendre script condition must return boolean not " + (result == null ? "null" : result.getClass()) + " for rule " + operator);

					if (((boolean) result)) {
						Debugger.debug("operator", "\tignore sender script found");

						return false;
					}
				}
			}

			if (operator.getIgnoreReceiverScript() != null) {
				final Object result;

				try {
					result = JavaScriptExecutor.run(this.replaceReceiverVariablesLegacy(operator.getIgnoreReceiverScript(), operator), this.wrappedReceiver.getAudience());

				} catch (final FoScriptException ex) {
					CommonCore.logFramed(
							"Error parsing 'ignore receiver script' in player message",
							"Message " + operator.getUniqueName() + " in " + operator.getFile(),
							"",
							"Raw script: " + operator.getIgnoreReceiverScript(),
							"Evaluated script with variables replaced: '" + this.replaceSenderVariablesLegacy(operator.getIgnoreReceiverScript(), operator) + "'",
							"Sender: " + this.wrappedSender,
							"Error: " + ex.getMessage(),
							"",
							"Check that the evaluated script",
							"above is a valid JavaScript!");

					throw ex;
				}

				if (result != null) {
					checkBoolean(result instanceof Boolean, "ignore receiver script condition must return boolean not " + (result == null ? "null" : result.getClass()) + " for rule " + operator);

					if (((boolean) result)) {
						Debugger.debug("operator", "\tignore receiver script found");

						return false;
					}
				}
			}

			if (this.wrappedSender.isPlayer()) {
				if (operator.getIgnoreSenderGamemodes().contains(this.wrappedSender.getPlayer().getGameMode())) {
					Debugger.debug("operator", "\tignore sender gamemodes found");

					return false;
				}

				if (operator.getIgnoreSenderWorlds().contains(this.wrappedSender.getPlayer().getWorld().getName())) {
					Debugger.debug("operator", "\tignore sender worlds found");

					return false;
				}

				for (final String playersRegion : DiskRegion.findRegionNames(this.wrappedSender.getPlayer().getLocation()))
					if (operator.getIgnoreSenderRegions().contains(playersRegion)) {
						Debugger.debug("operator", "\tignore sender regions found");

						return false;
					}

				for (final String channelName : operator.getIgnoreSenderChannels())
					if (this.wrappedSender.getPlayerCache().isInChannel(channelName))
						return false;
			}

			if (operator.getIgnoreReceiverGamemodes().contains(this.wrappedReceiver.getPlayer().getGameMode())) {
				Debugger.debug("operator", "\tignore receiver gamemodes found");

				return false;
			}

			if (operator.getIgnoreReceiverWorlds().contains(this.wrappedReceiver.getPlayer().getWorld().getName())) {
				Debugger.debug("operator", "\tignore receiver worlds found");

				return false;
			}

			for (final String playersRegion : DiskRegion.findRegionNames(this.wrappedReceiver.getPlayer().getLocation()))
				if (operator.getIgnoreReceiverRegions().contains(playersRegion)) {
					Debugger.debug("operator", "\tignore receiver regions found");

					return false;
				}

			for (final String channelName : operator.getIgnoreReceiverChannels())
				if (this.wrappedReceiver.getPlayerCache().isInChannel(channelName))
					return false;

			return super.canFilter(operator);
		}

		/**
		 * @see org.mineacademy.chatcontrol.operator.Operator.OperatorCheck#executeOperators(org.mineacademy.chatcontrol.operator.Operator)
		 */
		@Override
		protected void executeOperators(final T operator) throws EventHandledException {

			// Use the same message for all players
			final String message = this.pickedMessage;

			if (!message.isEmpty() && !"none".equals(message)) {
				String prefix = operator.getPrefix();
				SimpleComponent proxyComponent;
				String plainMessage;

				// Send message as JSON
				if ("[JSON]".equals(prefix) || message.startsWith("[JSON]")) {
					final String toSend = this.replaceSenderVariablesLegacy(message.startsWith("[JSON]") ? message.replace("[JSON]", "").trim() : prefix + message, operator).replace("[JSON]", "").trim();

					// Prepare message we send to proxy
					final SimpleComponent component = SimpleComponent.fromAdventureJson(toSend, MinecraftVersion.olderThan(V.v1_16));

					proxyComponent = component;
					plainMessage = component.toPlain(null);

					// Send whatever part starts with JSON
					this.wrappedReceiver.getAudience().sendJson(toSend);
				}

				// Send as interactive format otherwise
				else {
					final String colorlessMessage = CompChatColor.stripColorCodes(message);

					// Support interactive chat
					if (Format.isInteractiveChat(colorlessMessage)) {
						this.wrappedReceiver.getAudience().sendMessage(SimpleComponent.fromMiniAmpersand(message));

						// Remove the first <> prefix
						plainMessage = message.replaceFirst("<[a-zA-Z]+>", "");
						proxyComponent = null;

					} else {

						// Add the main part and add prefix for all lines
						final Format format = Format.isFormatLoaded(message) ? Format.findFormat(message) : Format.parse("{message}");

						// Construct
						prefix = prefix != null ? prefix : "";

						String replaced = this.replaceSenderVariablesLegacy(prefix + message + CommonCore.getOrEmpty(operator.getSuffix()), operator);

						// Support centering
						final String[] replacedLines = replaced.split("\n");

						for (int i = 0; i < replacedLines.length; i++) {
							final String line = replacedLines[i];

							if (CompChatColor.stripColorCodes(line).startsWith("<center>"))
								replacedLines[i] = ChatUtil.center(line.replace("<center>", "").trim());
						}

						replaced = String.join("\n", replacedLines);

						// Build again
						final SimpleComponent component;

						try {
							component = format.build(this.getMessagePlayerForVariables(), CommonCore.newHashMap("message", replaced));

						} catch (final EventHandledException ex) {
							return; // canceled?
						}

						// Send
						this.wrappedReceiver.getAudience().sendMessage(component);

						// Prepare message we send to proxy
						proxyComponent = component;
						plainMessage = component.toPlain(null);
					}
				}

				// Send to proxy and Discord
				if (this.firstTimeRun && !plainMessage.isEmpty()) {
					if (Settings.Proxy.ENABLED && operator.isProxy())
						ProxyUtil.sendPluginMessage(ChatControlProxyMessage.BROADCAST,
								ProxyChat.getProxyPrefix().append(proxyComponent != null ? proxyComponent : SimpleComponent.fromMiniAmpersand(message)));

					if (HookManager.isDiscordSRVLoaded()) {
						final Long discordChannelId = Settings.Messages.DISCORD.get(this.type);

						if (discordChannelId != null)
							try {
								Discord.getInstance().sendChannelMessageNoPlayer(discordChannelId, CompChatColor.stripColorCodes(plainMessage));

							} catch (final NoClassDefFoundError ex) {
								// Ignore, prob plugman removed discord
							}
					}
				}
			}

			// Register as received message
			this.messageReceivers.add(this.wrappedReceiver.getUniqueId());

			// Execute console commands for each receiver
			if (!this.wrappedSender.isConsole())
				for (final String command : operator.getConsoleForEachCommands())
					Platform.dispatchConsoleCommand(this.wrappedSender.getAudience(), this.replaceSenderVariablesLegacy(command, operator));

			super.executeOperators(operator);

			// Mark as executed, starting the first receiver
			this.firstTimeRun = false;
		}

		protected WrappedSender getMessagePlayerForVariables() {
			return this.wrappedReceiver;
		}

		protected boolean isDebugRun() {
			return false;
		}

		/*
		 * Replace all kinds of check variables
		 */
		private final SimpleComponent replaceReceiverVariables(final SimpleComponent component, final T operator) {
			if (component == null)
				return null;

			return Variables.builder(this.wrappedReceiver.getAudience()).placeholders(this.prepareVariables(this.wrappedReceiver, operator)).replaceComponent(component);
		}

		/*
		 * Replace all kinds of check variables
		 */
		private final String replaceReceiverVariablesLegacy(final String message, final T operator) {
			if (message == null)
				return null;

			return Variables.builder(this.wrappedReceiver.getAudience()).placeholders(this.prepareVariables(this.wrappedReceiver, operator)).replaceLegacy(message);
		}

		/**
		 * @see org.mineacademy.chatcontrol.model.Checkable#prepareVariables(org.mineacademy.chatcontrol.operator.Operator)
		 */
		@Override
		protected Map<String, Object> prepareVariables(final WrappedSender wrapped, final T operator) {
			final Map<String, Object> map = super.prepareVariables(wrapped, operator);

			if (this.wrappedReceiver != null)
				map.putAll(SyncedCache.getPlaceholders(this.wrappedSender.getName(), this.wrappedReceiver.getUniqueId(), PlaceholderPrefix.RECEIVER));

			map.put("broadcast_group", operator.getGroup());

			return map;
		}
	}
}
