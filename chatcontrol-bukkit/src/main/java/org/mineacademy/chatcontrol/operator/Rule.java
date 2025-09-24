package org.mineacademy.chatcontrol.operator;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.mineacademy.chatcontrol.api.PreRuleMatchEvent;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.RuleType;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.model.db.Log;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.model.CompChatColor;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.platform.Platform;

import lombok.Getter;
import lombok.NonNull;

/**
 * Represents a simple chat rule
 */
@Getter
public class Rule extends RuleOperator {

	/**
	 * How kind the rule is
	 *
	 * (Rules are typically very kind)
	 */
	private final RuleType type;

	/**
	 * The match of the rule
	 */
	private final Pattern pattern;

	/**
	 * List of events this rule does not apply to
	 */
	private final Set<RuleType> ignoreTypes = new HashSet<>();

	/**
	 * The name of the rule, empty if not set
	 */
	private String name = "";

	/**
	 * Apply rules from the given group name
	 */
	@Nullable
	private String group;

	/**
	 * Overrides Strip_Colors option from settings.
	 */
	private boolean stripColors;

	/**
	 * Overrides Strip_Accents option from settings.
	 */
	private boolean stripAccents;

	/**
	 * Create a new rule of the given type and match operator
	 *
	 * @param type
	 * @param match the regex after the match
	 */
	public Rule(final RuleType type, final String match) {
		this.type = type;
		this.pattern = CommonCore.compilePattern(match);
		this.stripColors = Settings.Rules.STRIP_COLORS;
		this.stripAccents = Settings.Rules.STRIP_ACCENTS;
	}

	/*
	 * Helper copied over from Common class in Foundation to compile a pattern for best performance
	 * supporting per-rule color/accent strip
	 */
	private Matcher compileMatcher(@NonNull final Pattern pattern, final String message) {
		String strippedMessage = this.stripColors ? SimpleComponent.fromMiniNative(CompChatColor.stripColorCodes(message, true)).toPlain() : message;
		strippedMessage = this.stripAccents ? ChatUtil.replaceDiacritic(strippedMessage) : strippedMessage;

		return pattern.matcher(strippedMessage);
	}

	/**
	 * @see org.mineacademy.fo.model.Rule#getFile()
	 */
	@Override
	public File getFile() {
		return FileUtil.getFile("rules/" + this.type.getKey() + ".rs");
	}

	@Override
	public String getUniqueName() {
		return this.pattern.pattern();
	}

	public String getMatch() {
		return this.pattern.pattern();
	}

	/**
	 * @see org.mineacademy.chatcontrol.operator.Operator#onParse(java.lang.String, java.lang.String, java.lang.String[])
	 */
	@Override
	protected boolean onParse(final String param, final String theRest, final String[] args) {
		if ("name".equals(args[0]))
			this.name = CommonCore.joinRange(1, args);

		else if ("group".equals(args[0])) {
			this.checkNotSet(this.group, "group");

			this.group = CommonCore.joinRange(1, args);
		}

		else if ("ignore event".equals(param) || "ignore events".equals(param) || "ignore type".equals(param) || "ignore types".equals(param)) {
			ValidCore.checkBoolean(this.type == RuleType.GLOBAL, "You can only use 'ignore type' for global rules not " + this);

			for (final String typeKey : theRest.split("\\|")) {
				final RuleType type = RuleType.fromKey(typeKey);

				this.ignoreTypes.add(type);
			}
		}

		else if ("strip colors".equals(param)) {
			if (theRest.isEmpty())
				this.stripColors = false;

			try {
				this.stripColors = Boolean.parseBoolean(theRest);
			} catch (final Throwable t) {
				CommonCore.error(t, "Malformed syntax for 'strip color'! Expected 'strip colors' or 'strip colors true/false', got " + param + " " + theRest + " for rule " + this);
			}

		} else if ("strip accents".equals(param)) {
			if (theRest.isEmpty())
				this.stripAccents = false;

			try {
				this.stripAccents = Boolean.parseBoolean(theRest);
			} catch (final Throwable t) {
				CommonCore.error(t, "Malformed syntax for 'strip accents'! Expected 'strip accents' or 'strip accents true/false', got " + param + " " + theRest + " for rule " + this);
			}

		} else
			return super.onParse(param, theRest, args);

		return true;
	}

	@Override
	protected Map<String, Object> collectOptions() {
		final Map<String, Object> map = super.collectOptions();

		map.putAll(CommonCore.newHashMap(
				"Name", this.name,
				"Type", this.type,
				"Match", this.pattern.pattern(),
				"Ignore Types", this.ignoreTypes,
				"Group", this.group));

		return map;
	}

	/* ------------------------------------------------------------------------------- */
	/* Static */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Start the rule check for the given rule type, sender and his message
	 *
	 * @param type
	 * @param wrapped
	 * @param message
	 * @param channel can be null
	 *
	 * @return
	 */
	public static RuleCheck<Rule> filter(final RuleType type, final WrappedSender wrapped, final String message, @Nullable final Channel channel) {
		final RuleCheck<Rule> check = new RuleCheck<>(type, wrapped, message, channel, null);

		check.start();
		return check;
	}

	/**
	 * Start the rule check for the given rule type, sender and his message
	 *
	 * @param type
	 * @param wrapped
	 * @param message
	 * @param variables
	 * @return
	 */
	public static RuleCheck<Rule> filter(final RuleType type, final WrappedSender wrapped, final String message, @Nullable final Map<String, Object> variables) {
		final RuleCheck<Rule> check = new RuleCheck<>(type, wrapped, message, null, variables);

		check.start();
		return check;
	}

	/**
	 * Start the rule check for the given rule type, sender and his message
	 *
	 * @param type
	 * @param wrapped
	 * @param message
	 * @return
	 */
	public static RuleCheck<Rule> filter(final RuleType type, final WrappedSender wrapped, final String message) {
		final RuleCheck<Rule> check = new RuleCheck<>(type, wrapped, message, null, null);

		check.start();
		return check;
	}

	/* ------------------------------------------------------------------------------- */
	/* Classes */
	/* ------------------------------------------------------------------------------- */

	public static class RuleCheck<T extends Rule> extends RuleOperatorCheck<T> {

		/**
		 * The rule type that started the check
		 */
		private final RuleType type;

		/**
		 * The at-present evaluated rule
		 */
		private RuleOperator ruleOrGroupEvaluated;

		/**
		 * The at-present rule that the current group is calling
		 */
		private RuleOperator ruleForGroup;

		/**
		 * The at-present matcher
		 */
		private Matcher matcher;

		/**
		 * Did we have at least one match?
		 */
		@Getter
		private final boolean atLeastOneMatch = false;

		/**
		 * Custom variables
		 */
		private final Map<String, Object> variables;

		/**
		 * @param wrapped
		 * @param message
		 */
		protected RuleCheck(final RuleType type, @NonNull final WrappedSender wrapped, @NonNull final String message, @Nullable final Channel channel, @Nullable final Map<String, Object> variables) {
			super(wrapped, message, channel);

			this.type = type;
			this.variables = variables;
		}

		/**
		 * @see org.mineacademy.chatcontrol.operator.Operator.OperatorCheck#getOperators()
		 */
		@Override
		public List<RuleOperator> getOperators() {
			ValidCore.checkNotNull(this.type, "Type not set!");

			if (!Settings.Rules.APPLY_ON.contains(this.type))
				return new ArrayList<>();

			// Get all rules and make a copy
			final List<RuleOperator> rules = new ArrayList<>(Rules.getInstance().getRules(this.type));

			// Import other rules to check
			for (final RuleType toImport : Rules.getInstance().getImports().getOrDefault(this.type, new ArrayList<>()))
				rules.addAll(0, Rules.getInstance().getRules(toImport));

			return rules;
		}

		/**
		 * @see org.mineacademy.chatcontrol.operator.Operator.OperatorCheck#filter(org.mineacademy.chatcontrol.operator.Operator)
		 */
		@Override
		protected void filter(final RuleOperator rule) throws EventHandledException {

			// Ignore if the message matches any player's online name and the rule is ignoring them
			if (rule.isIgnorePlayers() && Platform.getOnlinePlayers().stream().anyMatch(player -> player.getName().equalsIgnoreCase(this.message)))
				return;

			// Set this to use later in variables
			this.ruleOrGroupEvaluated = rule;

			// Reset for new rule
			this.ruleForGroup = null;

			final Rule ruleEvaluated = (Rule) rule;

			if (ruleEvaluated.getIgnoreTypes().contains(this.type))
				return;

			final String originalMessage = this.message;
			String messageMatched = this.message;

			// Prepare the message before checking, avoid calling compileMatcher in loop to save performance
			{
				for (final Entry<Pattern, String> entry : rule.getBeforeReplace().entrySet()) {
					String strippedMessage = ruleEvaluated.isStripColors() ? SimpleComponent.fromMiniNative(CompChatColor.stripColorCodes(messageMatched, true)).toPlain() : messageMatched;
					strippedMessage = ruleEvaluated.isStripAccents() ? ChatUtil.replaceDiacritic(strippedMessage) : strippedMessage;
					messageMatched = entry.getKey().matcher(strippedMessage).replaceAll(entry.getValue());
				}
			}

			// Find group early
			final Group group = ruleEvaluated.getGroup() != null ? Groups.getInstance().findGroup(ruleEvaluated.getGroup()) : null;

			// Ignore command prefix
			if (this.type == RuleType.COMMAND) {
				final String[] split = messageMatched.split(" ");
				final String label = split[0];

				// Fix some /chc subcommands being filtered
				final boolean isOurs = Settings.MAIN_COMMAND_ALIASES.contains(label.substring(1));

				if (isOurs && split.length > 1) {
					final String sublabel = split[1];

					if ("rule".equals(sublabel) || "r".equals(sublabel) || "message".equals(sublabel) || "m".equals(sublabel) || "sendformat".equals(sublabel) || "sf".equals(sublabel))
						return;
				}

				if ((ruleEvaluated.isIgnoreCommandPrefix() || (group != null && group.isIgnoreCommandPrefix())) && split.length > 1)
					messageMatched = String.join(" ", Arrays.copyOfRange(split, 1, split.length));
			}

			final Matcher matcher = ruleEvaluated.compileMatcher(ruleEvaluated.getPattern(), messageMatched);

			if (matcher.find()) {

				this.matcher = matcher;

				// Only continue if we match a rule that we can filter.
				if (!this.canFilter(rule))
					return;

				// API
				final PreRuleMatchEvent event = new PreRuleMatchEvent(this.wrappedSender.getSender(), this.message, ruleEvaluated);

				if (!Platform.callEvent(event))
					return;

				// Update message from the API
				this.message = event.getMessage();

				final String identifier = CommonCore.getOrDefault(ruleEvaluated.getName(), group != null ? group.getGroup() : "");

				// Verbose
				if (!rule.isIgnoreVerbose())
					this.verbose("&f*--------- Rule match (" + ruleEvaluated.getType().getKey() + (identifier.isEmpty() ? "" : "/" + identifier) + ") for " + this.wrappedSender.getName() + " --------- ",
							"&fMATCH&b: &r" + ruleEvaluated.getMatch(),
							"&fCATCH&b: &r" + this.message);

				// Execute main operators
				this.executeOperators(rule, matcher);

				// Execute group operators
				if (group != null) {
					ValidCore.checkNotNull(group, "Rule referenced to non-existing group '" + group.getGroup() + "'! Rule: " + rule);

					this.ruleForGroup = rule;

					if (this.canFilter(group))
						this.executeOperators(group, matcher);
				}

				if (!rule.isIgnoreVerbose() && !originalMessage.equals(this.message))
					this.verbose("&fUPDATE&b: &r" + this.message);

				if (!rule.isIgnoreVerbose() && this.cancelledSilently)
					this.verbose("&cOriginal message cancelled silently.");

				if (rule.isAbort())
					throw new OperatorAbortException();

				// Move abort to the bottom to let full rule and handler execute
				if (group != null && group.isAbort())
					throw new OperatorAbortException();
			}
		}

		/**
		 * @see org.mineacademy.chatcontrol.model.Checkable#canFilter(org.bukkit.command.CommandSender, java.lang.String, org.mineacademy.chatcontrol.operator.Operator)
		 */
		@Override
		protected boolean canFilter(final RuleOperator operator) {

			// Set this to use later in variables
			this.ruleOrGroupEvaluated = operator;

			if (this.type == RuleType.COMMAND) {
				final String label = this.message.split(" ")[0];

				for (final String command : operator.getIgnoreCommands())
					if (command.endsWith("*")) {
						if (label.startsWith(command.substring(0, command.length() - 1).trim()))
							return false;

					} else if (label.equalsIgnoreCase(command))
						return false;

				boolean found = false;

				if (operator.getRequireCommands().isEmpty())
					found = true;
				else {
					for (final String command : operator.getRequireCommands()) {
						if (command.endsWith("*")) {
							if (label.startsWith(command.substring(0, command.length() - 1).trim()))
								found = true;

						} else if (label.equalsIgnoreCase(command))
							found = true;
					}
				}

				if (!found)
					return false;
			}

			if (this.type == RuleType.CHAT && this.wrappedSender.isPlayer() && this.channel != null) {
				if (operator.getIgnoreChannels().containsKey(this.channel.getName()))
					return false;

				if (!operator.getRequireChannels().isEmpty() && !operator.getRequireChannels().containsKey(this.channel.getName()))
					return false;
			}

			return super.canFilter(operator);
		}

		/**
		 * @see org.mineacademy.chatcontrol.model.Checkable#executeOperators(org.mineacademy.chatcontrol.operator.Operator, java.util.regex.Matcher)
		 */
		@Override
		protected void executeOperators(final RuleOperator operator, final Matcher matcher) throws EventHandledException {
			if (!operator.isIgnoreLogging())
				Log.logRule(this.type, this.wrappedSender, operator, this.message);

			super.executeOperators(operator, matcher);
		}

		/**
		 * @see org.mineacademy.chatcontrol.model.Checkable#prepareVariables(org.mineacademy.chatcontrol.operator.Operator)
		 */
		@Override
		protected Map<String, Object> prepareVariables(final WrappedSender wrapped, final RuleOperator operator) {
			final Map<String, Object> map = super.prepareVariables(wrapped, operator);

			if (this.ruleOrGroupEvaluated instanceof Rule) {
				final Rule rule = (Rule) this.ruleOrGroupEvaluated;

				map.put("ruleID", CommonCore.getOrDefaultStrict(rule.getName(), "")); // backward compatibile
				map.put("rule_name", CommonCore.getOrDefaultStrict(rule.getName(), ""));
				map.put("rule_group", CommonCore.getOrDefault(rule.getGroup(), ""));
				map.put("rule_match", rule.getMatch());
			}

			if (this.ruleOrGroupEvaluated instanceof Group) {
				final Group group = (Group) this.ruleOrGroupEvaluated;

				map.put("group_name", CommonCore.getOrDefault(group.getGroup(), ""));
				map.put("rule_name", CommonCore.getOrDefault(this.ruleForGroup != null ? ((Rule) this.ruleForGroup).getName() : null, ""));
			}

			if (this.variables != null)
				map.putAll(this.variables);

			map.put("rule_fine", this.ruleOrGroupEvaluated.getFine());
			map.put("rule_type", this.type.getKey());

			return map;
		}

		@Override
		protected SimpleComponent replaceVariablesFor(@NonNull SimpleComponent component, final RuleOperator operator, final WrappedSender wrapped) {
			component = super.replaceVariablesFor(component, operator, wrapped);

			if (this.matcher != null) {
				for (int i = 0; i <= this.matcher.groupCount(); i++)
					try {
						final String result = this.matcher.group(i);

						// Can be null for regexes using optional groups
						component = component.replaceLiteral("$" + i, result == null ? "" : result.trim());

					} catch (final IllegalStateException ex) {
						// silently ignore
					}

				component = component.replaceBracket("matched_message", this.matcher.group().trim());
			}

			return component;
		}

		@Override
		protected String replaceVariablesForLegacy(@NonNull String message, final RuleOperator operator, final WrappedSender wrapped) {
			message = super.replaceVariablesForLegacy(message, operator, wrapped);

			if (this.matcher != null) {
				for (int i = 0; i <= this.matcher.groupCount(); i++)
					try {
						final String result = this.matcher.group(i);

						// Can be null for regexes using optional groups
						message = message.replace("$" + i, result == null ? "" : result.trim());

					} catch (final IllegalStateException ex) {
						// silently ignore
					}

				message = message.replace("{matched_message}", this.matcher.group().trim());
			}

			return message;
		}
	}
}