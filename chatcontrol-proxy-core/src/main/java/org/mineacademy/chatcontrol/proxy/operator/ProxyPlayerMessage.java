package org.mineacademy.chatcontrol.proxy.operator;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.PlaceholderPrefix;
import org.mineacademy.chatcontrol.model.PlayerMessageType;
import org.mineacademy.chatcontrol.proxy.settings.ProxySettings;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.model.JavaScriptExecutor;
import org.mineacademy.fo.model.RandomNoRepeatPicker;
import org.mineacademy.fo.model.Rule;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.platform.FoundationPlayer;
import org.mineacademy.fo.platform.Platform;

import lombok.Getter;

/**
 * Represents an operator that has require/ignore for both sender and receiver
 * Used for join/leave/kick/death messages yo
 */
public abstract class ProxyPlayerMessage extends ProxyOperator {

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
	@Getter
	private Tuple<String, SimpleComponent> requireSenderPermission;

	/**
	 * Permission required for receivers of the message of the rule
	 */
	@Getter
	private Tuple<String, SimpleComponent> requireReceiverPermission;

	/**
	 * JavaScript boolean output required to be true for the rule to apply
	 */
	@Getter
	private String requireSenderScript;

	/**
	 * JavaScript boolean output required to be true for the rule to apply
	 */
	@Getter
	private String requireReceiverScript;

	/**
	 * The server to require for sender
	 */
	@Getter
	private final Set<String> requireSenderServers = new HashSet<>();

	/**
	 * The server to require for receiver
	 */
	@Getter
	private final Set<String> requireReceiverServers = new HashSet<>();

	/**
	 * Should the message only be sent to the sending player?
	 */
	@Getter
	private boolean requireSelf;

	/**
	 * Should the message not be sent to the sending player?
	 */
	@Getter
	private boolean ignoreSelf;

	/**
	 * Permission to bypass the rule
	 */
	@Getter
	private String ignoreSenderPermission;

	/**
	 * Permission to bypass the rule
	 */
	@Getter
	private String ignoreReceiverPermission;

	/**
	 * JavaScript boolean output when true for the rule to bypass
	 */
	@Getter
	private String ignoreSenderScript;

	/**
	 * JavaScript boolean output when true for the rule to bypass
	 */
	@Getter
	private String ignoreReceiverScript;

	/**
	 * The server to ignore for sender
	 */
	@Getter
	private final Set<String> ignoreSenderServers = new HashSet<>();

	/**
	 * The server to ignore for receiver
	 */
	@Getter
	private final Set<String> ignoreReceiverServers = new HashSet<>();

	/**
	 * The suffix for the {@link #messages}
	 */
	private String prefix;

	/**
	 * The suffix for the {@link #messages}
	 */
	private String suffix;

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

	protected ProxyPlayerMessage(final PlayerMessageType type, final String group) {
		this.type = type;
		this.group = group;
	}

	/**
	 * Return the next message in a cyclic repetition
	 *
	 * @return
	 */
	public final String getNextMessage() {
		ValidCore.checkBoolean(!this.messages.isEmpty(), "Messages must be set on " + this);

		if (this.messages.size() == 1) {
			this.lastMessageIndex = 0;

			return this.messages.get(0);
		}

		if (this.lastMessageIndex >= this.messages.size())
			this.lastMessageIndex = 0;

		return this.messages.get(this.lastMessageIndex++);
	}

	/**
	 * Return the prefix or the default one if not set. Empty if no default.
	 *
	 * @return the prefix
	 */
	public final String getPrefix() {
		return this.prefix != null ? this.prefix : ProxySettings.Messages.PREFIX.getOrDefault(this.type, "");
	}

	/**
	 * Return the suffix or empty if not set.
	 *
	 * @return the suffix
	 */
	public final String getSuffix() {
		return CommonCore.getOrEmpty(this.suffix);
	}

	@Override
	public final String getUniqueName() {
		return this.group;
	}

	/**
	 * @see Rule#getFile()
	 */
	@Override
	public final File getFile() {
		return FileUtil.getFile("messages/" + this.type.getKey() + ".rs");
	}

	/**
	 * @see org.mineacademy.chatcontrol.ProxyOperator.operator.Operator#onParse(java.lang.String, java.lang.String, java.lang.String[])
	 */
	@Override
	protected boolean onParse(String firstThreeParams, String theRestThree, final String[] args) {

		firstThreeParams = CommonCore.joinRange(0, 3, args, " ");
		theRestThree = CommonCore.joinRange(3, args);

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
				ValidCore.checkBoolean(!this.messages.isEmpty(), "Enter messages with '-' on each line. Got: " + everything);

				// Merge the line that does not start with "-", assume it is used
				// for multiline messages:
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

		else if ("message:".equals(args[0]) || "messages:".equals(args[0])) {
			ValidCore.checkBoolean(!this.loadingMessages, "Operator messages: can only be used once in " + this);

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

		else if ("require sender server".equals(firstThreeParams))
			this.requireSenderServers.add(theRestThree);

		else if ("require receiver server".equals(firstThreeParams))
			this.requireReceiverServers.add(theRestThree);

		else if ("require self".equals(CommonCore.joinRange(0, 2, args, " "))) {
			ValidCore.checkBoolean(!this.requireSelf, "'require self' option already set for " + this);

			this.requireSelf = true;
		}

		else if ("ignore self".equals(CommonCore.joinRange(0, 2, args, " "))) {
			ValidCore.checkBoolean(!this.ignoreSelf, "'ignore self' option already set for " + this);

			this.ignoreSelf = true;
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

		else if ("ignore sender server".equals(firstThreeParams))
			this.ignoreSenderServers.add(theRestThree);
		else if ("ignore receiver server".equals(firstThreeParams))
			this.ignoreReceiverServers.add(theRestThree);
		else
			return false;

		return true;
	}

	/**
	 * Collect all options we have to debug
	 *
	 * @return
	 */
	@Override
	protected SerializedMap collectOptions() {
		return SerializedMap.fromArray(
				"Group", this.group,
				"Prefix", this.prefix,
				"Suffix", this.suffix,
				//"Proxy", this.proxy,
				"Messages", this.messages,

				"Require Sender Permission", this.requireSenderPermission,
				"Require Sender Script", this.requireSenderScript,
				/*"Require Sender Gamemodes", this.requireSenderGamemodes,
				"Require Sender Worlds", this.requireSenderWorlds,
				"Require Sender Regions", this.requireSenderRegions,
				"Require Sender Channels", this.requireSenderChannels,

				"Require Receiver Permission", this.requireReceiverPermission,
				"Require Receiver Script", this.requireReceiverScript,
				"Require Receiver Gamemodes", this.requireReceiverGamemodes,
				"Require Receiver Worlds", this.requireReceiverWorlds,
				"Require Receiver Regions", this.requireReceiverRegions,
				"Require Receiver Channels", this.requireReceiverChannels,*/

				"Require Self", this.requireSelf,
				"Ignore Self", this.ignoreSelf,
				//"Ignore Match", this.ignoreMatch,

				"Ignore Sender Permission", this.ignoreSenderPermission,
				"Ignore Sender Script", this.ignoreSenderScript//,
		/*"Ignore Sender Regions", this.ignoreSenderRegions,
		"Ignore Sender Gamemodes", this.ignoreSenderGamemodes,
		"Ignore Sender Worlds", this.ignoreSenderWorlds,
		"Ignore Sender Channels", this.ignoreSenderChannels,

		"Ignore Receiver Permission", this.ignoreReceiverPermission,
		"Ignore Receiver Regions", this.ignoreReceiverRegions,
		"Ignore Receiver Script", this.ignoreReceiverScript,
		"Ignore Receiver Gamemodes", this.ignoreReceiverGamemodes,
		"Ignore Receiver Worlds", this.ignoreReceiverWorlds,
		"Ignore Receiver Channels", this.ignoreReceiverChannels*/

		);
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Player Message " + super.collectOptions().put(SerializedMap.fromArray("Type", this.type)).toStringFormatted();
	}

	// ------------------------------------------------------------------------------------------------------------
	// Classes
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Represents a check that is implemented by this class
	 * @param <T>
	 */
	public static abstract class PlayerMessageCheck<T extends ProxyPlayerMessage> extends OperatorCheck<T> {

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
		 * The current iterated receiver audience
		 */
		protected FoundationPlayer receiverAudience;

		/**
		 * Pick one message randomly from the list to show to all players equally
		 */
		protected String pickedMessage;

		/**
		 * Has this rule been run at least once? Used to prevent firing operators
		 * for the receiver the amount of times as the online player count.
		 */
		private boolean executed;

		/**
		 * @param audience
		 * @param message
		 */
		protected PlayerMessageCheck(final PlayerMessageType type, final FoundationPlayer audience, final Map<String, Object> placeholders) {
			super(audience, placeholders);

			this.type = type;
		}

		/**
		 * @see org.mineacademy.chatcontrol.ProxyOperator.operator.Operator.OperatorCheck#filter(org.mineacademy.chatcontrol.ProxyOperator.operator.Operator)
		 */
		@Override
		protected void filter(final T message) throws EventHandledException {

			Debugger.debug("operator", "FILTERING " + message.getUniqueName());

			// Delay
			if (message.getDelay() != null) {
				final SimpleTime time = message.getDelay().getKey();
				final long now = System.currentTimeMillis();

				// Round the number due to Bukkit scheduler lags
				final long delay = Math.round((now - message.getLastExecuted()) / 1000D);

				if (delay < time.getTimeSeconds()) {
					Debugger.debug("operator", "\tbefore delay: " + delay + " threshold: " + time.getTimeSeconds());

					return;
				}

				message.setLastExecuted(now);
			}

			boolean pickedMessage = false;

			for (final FoundationPlayer online : Platform.getOnlinePlayers()) {
				final SyncedCache onlineCache = SyncedCache.fromUniqueId(online.getUniqueId());

				if (onlineCache != null && onlineCache.isIgnoringMessage(this.type, message.getUniqueName()))
					continue;

				if (message.isRequireSelf() && !this.audience.getName().equals(online.getName()))
					continue;

				if (message.isIgnoreSelf() && this.audience.getName().equals(online.getName()))
					continue;

				if (this.messageReceivers.contains(online.getUniqueId()) && ProxySettings.Messages.STOP_ON_FIRST_MATCH) {
					Debugger.debug("operator", "\t" + online.getName() + " already received a message");

					continue;
				}

				this.receiverAudience = online;

				// Filter for each player
				if (!this.canFilterMessage(message)) {
					Debugger.debug("operator", "\tcanFilterMessage returned false for " + online.getName());

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
		}

		/**
		 * @see org.mineacademy.chatcontrol.ProxyOperator.operator.Operator.OperatorCheck#canFilter(org.mineacademy.chatcontrol.ProxyOperator.operator.Operator)
		 */
		private boolean canFilterMessage(final T operator) {
			ValidCore.checkNotNull(this.receiverAudience, "receiver in canFilter == null");

			Debugger.debug("operator", "CAN FILTER message " + operator.getUniqueName());

			final String senderServerName = this.audience.isPlayer() ? this.audience.getServer().getName() : "";
			final String receiverServerName = this.receiverAudience.isPlayer() ? this.receiverAudience.getServer().getName() : "";

			// ----------------------------------------------------------------
			// Require
			// ----------------------------------------------------------------

			if (operator.getRequireSenderPermission() != null) {
				final String permission = operator.getRequireSenderPermission().getKey();
				final SimpleComponent noPermissionMessage = operator.getRequireSenderPermission().getValue();

				if (!this.audience.hasPermission(permission)) {
					if (noPermissionMessage != null) {
						this.audience.sendMessage(this.replaceVariables(noPermissionMessage, operator));

						throw new EventHandledException(true);
					}

					Debugger.debug("operator", "\tno required sender permission");
					return false;
				}
			}

			if (operator.getRequireReceiverPermission() != null) {
				final String permission = operator.getRequireReceiverPermission().getKey();
				final SimpleComponent noPermissionMessage = operator.getRequireReceiverPermission().getValue();

				if (!this.receiverAudience.hasPermission(permission)) {
					if (noPermissionMessage != null) {
						this.receiverAudience.sendMessage(this.replaceVariables(noPermissionMessage, operator));

						throw new EventHandledException(true);
					}

					Debugger.debug("operator", "\tno required receiver permission");
					return false;
				}
			}

			if (operator.getRequireSenderScript() != null) {
				final Object result = JavaScriptExecutor.run(this.replaceVariablesLegacy(operator.getRequireSenderScript(), operator), this.audience);

				if (result != null) {
					ValidCore.checkBoolean(result instanceof Boolean, "require sender script condition must return boolean not " + (result == null ? "null" : result.getClass()) + " for rule " + operator);

					if (!((boolean) result)) {
						Debugger.debug("operator", "\tno required sender script");

						return false;
					}
				}
			}

			if (operator.getRequireReceiverScript() != null) {
				final Object result = JavaScriptExecutor.run(this.replaceVariablesLegacy(operator.getRequireReceiverScript(), operator), this.receiverAudience);

				if (result != null) {
					ValidCore.checkBoolean(result instanceof Boolean, "require receiver script condition must return boolean not " + (result == null ? "null" : result.getClass()) + " for rule " + operator);

					if (!((boolean) result)) {
						Debugger.debug("operator", "\tno required receiver script");

						return false;
					}
				}
			}

			boolean foundRequiredSenderServer = false;

			for (final String server : operator.getRequireSenderServers())
				if (senderServerName.equalsIgnoreCase(server))
					foundRequiredSenderServer = true;

			if (!operator.getRequireSenderServers().isEmpty() && !foundRequiredSenderServer) {
				Debugger.debug("operator", "\tno require sender server");

				return false;
			}

			boolean foundRequiredReceiverServer = false;

			for (final String server : operator.getRequireReceiverServers())
				if (receiverServerName.equalsIgnoreCase(server))
					foundRequiredReceiverServer = true;

			if (!operator.getRequireReceiverServers().isEmpty() && !foundRequiredReceiverServer) {
				Debugger.debug("operator", "\tno require receiver server");

				return false;
			}

			// ----------------------------------------------------------------
			// Ignore
			// ----------------------------------------------------------------

			if (operator.getIgnoreSenderPermission() != null && this.audience.hasPermission(operator.getIgnoreSenderPermission())) {
				Debugger.debug("operator", "\tignore sender permission found");

				return false;
			}

			if (operator.getIgnoreReceiverPermission() != null && this.receiverAudience.hasPermission(operator.getIgnoreReceiverPermission())) {
				Debugger.debug("operator", "\tignore receiver permission found");

				return false;
			}

			if (operator.getIgnoreSenderScript() != null) {
				final Object result = JavaScriptExecutor.run(this.replaceVariablesLegacy(operator.getIgnoreSenderScript(), operator), this.audience);

				if (result != null) {
					ValidCore.checkBoolean(result instanceof Boolean, "ignore sendre script condition must return boolean not " + (result == null ? "null" : result.getClass()) + " for rule " + operator);

					if (((boolean) result)) {
						Debugger.debug("operator", "\tignore sender script found");

						return false;
					}
				}
			}

			if (operator.getIgnoreReceiverScript() != null) {
				final Object result = JavaScriptExecutor.run(this.replaceVariablesLegacy(operator.getIgnoreReceiverScript(), operator), this.receiverAudience);

				if (result != null) {
					ValidCore.checkBoolean(result instanceof Boolean, "ignore receiver script condition must return boolean not " + (result == null ? "null" : result.getClass()) + " for rule " + operator);

					if (((boolean) result)) {
						Debugger.debug("operator", "\tignore receiver script found");

						return false;
					}
				}
			}

			boolean foundIgnoredSenderServer = false;

			for (final String server : operator.getIgnoreSenderServers())
				if (senderServerName.equalsIgnoreCase(server))
					foundIgnoredSenderServer = true;

			if (!operator.getIgnoreSenderServers().isEmpty() && foundIgnoredSenderServer) {
				Debugger.debug("operator", "\tignore sender server found");

				return false;
			}

			boolean foundIgnoredReceiverServer = false;

			for (final String server : operator.getIgnoreReceiverServers())
				if (receiverServerName.equalsIgnoreCase(server))
					foundIgnoredReceiverServer = true;

			if (!operator.getIgnoreReceiverServers().isEmpty() && foundIgnoredReceiverServer) {
				Debugger.debug("operator", "\tignore receiver server found");

				return false;
			}

			return true;
		}

		/**
		 * @see org.mineacademy.chatcontrol.ProxyOperator.operator.Operator.OperatorCheck#executeOperators(org.mineacademy.chatcontrol.ProxyOperator.operator.Operator)
		 */
		@Override
		protected void executeOperators(final T operator) throws EventHandledException {

			// Use the same message for all players
			String message = this.pickedMessage;

			if (!message.isEmpty() && !"none".equals(message)) {
				String prefix = operator.getPrefix();

				// Send message as JSON
				if ("[JSON]".equals(prefix) || message.startsWith("[JSON]")) {
					prefix = prefix.replace("[JSON]", "").trim();
					message = message.replace("[JSON]", "").trim();

					try {
						final SimpleComponent json = SimpleComponent.fromAdventureJson(this.replaceVariablesLegacy(prefix + message, operator), !this.receiverAudience.hasHexColorSupport());

						this.receiverAudience.sendMessage(json);

					} catch (final Throwable ex) {
						if (ex.getClass().getSimpleName().equals("JsonSyntaxException"))
							CommonCore.throwError(ex,
									"Malformed JSON message! Make sure that 'combined' gives a valid JSON syntax",
									"Prefix (after [JSON] tag removed): '" + prefix + "'",
									"Message (after [JSON] tag removed): '" + message + "'",
									"Combined: '" + prefix + message + "'",
									"Rule: " + operator);
						else
							throw ex;
					}
				}

				// Send as interactive format otherwise
				else {
					if (!prefix.isEmpty() && !prefix.endsWith(" "))
						prefix = prefix + " ";

					// Support centering, add prefix that can be centered too
					{
						final String[] lines = message.split("\n");

						for (int i = 0; i < lines.length; i++) {
							final String line = lines[i];

							if (line.startsWith("<center>"))
								lines[i] = ChatUtil.center(prefix + line.replace("<center>", "").trim());
							else
								lines[i] = prefix + line;
						}

						message = String.join("\n", lines);
					}

					// Add suffix
					if (!operator.getSuffix().isEmpty())
						message = message + operator.getSuffix();

					// Add the main message, centered
					this.receiverAudience.sendMessage(SimpleComponent.fromMiniAmpersand(this.replaceVariablesLegacy(message, operator)));
				}
			}

			// Register as received message
			if (this.receiverAudience.isPlayer())
				this.messageReceivers.add(this.receiverAudience.getUniqueId());

			if (!this.executed)
				super.executeOperators(operator);

			// Mark as executed, starting the first receiver
			this.executed = true;
		}

		/**
		 * @see org.mineacademy.chatcontrol.model.Checkable#prepareVariables(org.mineacademy.chatcontrol.ProxyOperator.operator.Operator)
		 */
		@Override
		protected Map<String, Object> prepareVariables(final T operator) {
			final Map<String, Object> map = super.prepareVariables(operator);

			map.put("broadcast_group", operator.getUniqueName());

			final SyncedCache receiverCache = SyncedCache.fromUniqueId(this.receiverAudience.getUniqueId());

			if (receiverCache != null)
				map.putAll(receiverCache.getPlaceholders(PlaceholderPrefix.RECEIVER));
			else
				map.put("receiver_server", this.receiverAudience.isPlayer() ? this.receiverAudience.getServer().getName() : "");

			return map;
		}
	}
}
