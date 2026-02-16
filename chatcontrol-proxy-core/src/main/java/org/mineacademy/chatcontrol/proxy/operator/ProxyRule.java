package org.mineacademy.chatcontrol.proxy.operator;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.PlaceholderPrefix;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.model.JavaScriptExecutor;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.platform.FoundationPlayer;

import lombok.Getter;
import lombok.NonNull;

/**
 * Represents a single proxy command rule with a regex match pattern.
 */
@Getter
public final class ProxyRule extends ProxyOperator {

	/**
	 * The compiled regex pattern from the "match" keyword
	 */
	private final Pattern pattern;

	/**
	 * Optional name for verbose/identification
	 */
	private String name = "";

	/**
	 * Permission required for the sender for this rule to apply
	 */
	private Tuple<String, SimpleComponent> requireSenderPermission;

	/**
	 * JavaScript condition required for the sender
	 */
	private String requireSenderScript;

	/**
	 * Servers required for the sender
	 */
	private final Set<String> requireSenderServers = new HashSet<>();

	/**
	 * Permission that bypasses this rule for the sender
	 */
	private String ignoreSenderPermission;

	/**
	 * JavaScript condition that bypasses this rule
	 */
	private String ignoreSenderScript;

	/**
	 * Servers to ignore for the sender
	 */
	private final Set<String> ignoreSenderServers = new HashSet<>();

	/**
	 * Should colors be stripped before matching?
	 */
	private Boolean stripColors;

	/**
	 * Should accents be stripped before matching?
	 */
	private Boolean stripAccents;

	/**
	 * Create a new proxy rule from the given regex match
	 *
	 * @param match
	 */
	public ProxyRule(final String match) {
		this.pattern = CommonCore.compilePattern(match);
	}

	@Override
	public String getUniqueName() {
		return this.pattern.pattern();
	}

	@Override
	public File getFile() {
		return FileUtil.getFile("rules/command.rs");
	}

	@Override
	protected boolean onParse(final String param, final String theRest, final String[] args) {
		final String firstThreeParams = CommonCore.joinRange(0, 3, args, " ");
		final String theRestThree = CommonCore.joinRange(3, args);

		if ("name".equals(args[0])) {
			this.checkNotSet(this.name.isEmpty() ? null : this.name, "name");

			this.name = CommonCore.joinRange(1, args);
		}

		else if ("require sender perm".equals(firstThreeParams) || "require sender permission".equals(firstThreeParams)) {
			this.checkNotSet(this.requireSenderPermission, "require sender perm");
			final String[] split = theRestThree.split(" ");

			this.requireSenderPermission = new Tuple<>(split[0], split.length > 1 ? SimpleComponent.fromMiniAmpersand(CommonCore.joinRange(1, split)) : null);
		}

		else if ("require sender script".equals(firstThreeParams)) {
			this.checkNotSet(this.requireSenderScript, "require sender script");

			this.requireSenderScript = theRestThree;
		}

		else if ("require sender server".equals(firstThreeParams))
			this.requireSenderServers.add(theRestThree);

		else if ("ignore sender perm".equals(firstThreeParams) || "ignore sender permission".equals(firstThreeParams)) {
			this.checkNotSet(this.ignoreSenderPermission, "ignore sender perm");

			this.ignoreSenderPermission = theRestThree;
		}

		else if ("ignore sender script".equals(firstThreeParams)) {
			this.checkNotSet(this.ignoreSenderScript, "ignore sender script");

			this.ignoreSenderScript = theRestThree;
		}

		else if ("ignore sender server".equals(firstThreeParams))
			this.ignoreSenderServers.add(theRestThree);

		else if ("strip colors".equals(param)) {
			this.checkNotSet(this.stripColors, "strip colors");

			this.stripColors = Boolean.parseBoolean(CommonCore.joinRange(2, args));
		}

		else if ("strip accents".equals(param)) {
			this.checkNotSet(this.stripAccents, "strip accents");

			this.stripAccents = Boolean.parseBoolean(CommonCore.joinRange(2, args));
		}

		// Support "require perm" and "ignore perm" as shorthand (alias for require/ignore sender perm)
		else if ("require perm".equals(param) || "require permission".equals(param)) {
			this.checkNotSet(this.requireSenderPermission, "require perm");

			final String line = CommonCore.joinRange(2, args);
			final String[] split = line.split(" ");

			this.requireSenderPermission = new Tuple<>(split[0], split.length > 1 ? SimpleComponent.fromMiniAmpersand(CommonCore.joinRange(1, split)) : null);
		}

		else if ("ignore perm".equals(param) || "ignore permission".equals(param)) {
			this.checkNotSet(this.ignoreSenderPermission, "ignore perm");

			this.ignoreSenderPermission = CommonCore.joinRange(2, args);
		}

		else
			return false;

		return true;
	}

	@Override
	protected SerializedMap collectOptions() {
		return super.collectOptions().putArray(
				"Pattern", this.pattern.pattern(),
				"Name", this.name,
				"Require Sender Permission", this.requireSenderPermission,
				"Require Sender Script", this.requireSenderScript,
				"Require Sender Servers", this.requireSenderServers,
				"Ignore Sender Permission", this.ignoreSenderPermission,
				"Ignore Sender Script", this.ignoreSenderScript,
				"Ignore Sender Servers", this.ignoreSenderServers,
				"Strip Colors", this.stripColors,
				"Strip Accents", this.stripAccents);
	}

	@Override
	public String toString() {
		return "Proxy Rule " + this.collectOptions().toStringFormatted();
	}

	// ------------------------------------------------------------------------------------------------------------
	// Classes
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Represents a rule check against a proxy command
	 */
	public static final class RuleCheck extends OperatorCheck<ProxyRule> {

		/**
		 * The original command
		 */
		private final String originalMessage;

		/**
		 * The command being matched (may have colors/accents stripped)
		 */
		private String message;

		/**
		 * The current regex matcher
		 */
		private Matcher matcher;

		/**
		 * @param audience
		 * @param command
		 */
		public RuleCheck(final FoundationPlayer audience, final String command) {
			super(audience, CommonCore.newHashMap());

			this.originalMessage = command;
			this.message = command;
		}

		@Override
		public List<ProxyRule> getOperators() {
			return ProxyRules.getInstance().getRules();
		}

		@Override
		protected void filter(final ProxyRule rule) throws EventHandledException {

			// Prepare the message for matching
			String messageToMatch = this.message;

			if (rule.getStripColors() != null && rule.getStripColors())
				messageToMatch = org.mineacademy.fo.model.CompChatColor.stripColorCodes(messageToMatch);

			if (rule.getStripAccents() != null && rule.getStripAccents())
				messageToMatch = org.mineacademy.fo.ChatUtil.replaceDiacritic(messageToMatch);

			// Try to match
			this.matcher = rule.getPattern().matcher(messageToMatch);

			if (!this.matcher.find())
				return;

			// Verbose
			if (!rule.isIgnoreVerbose())
				this.verbose("&f*-----possibly-filtered-proxy-command-----*",
						"&fCommand: &b" + this.originalMessage,
						"&fMatch &b" + rule.getUniqueName() + (rule.getName().isEmpty() ? "" : " &f(name: &b" + rule.getName() + "&f)"));

			// Check sender conditions
			if (!this.canFilterRule(rule))
				return;

			// Execute operators
			this.executeOperators(rule);

			// Check for abort
			if (rule.isAbort())
				throw new OperatorAbortException();
		}

		/**
		 * Check sender-specific conditions
		 */
		private boolean canFilterRule(final ProxyRule rule) {
			final String senderServerName = this.audience.isPlayer() && this.audience.getServer() != null ? this.audience.getServer().getName() : "";

			// Require sender permission
			if (rule.getRequireSenderPermission() != null) {
				final String permission = rule.getRequireSenderPermission().getKey();
				final SimpleComponent noPermissionMessage = rule.getRequireSenderPermission().getValue();

				if (!this.audience.hasPermission(permission)) {
					if (noPermissionMessage != null) {
						this.audience.sendMessage(this.replaceVariables(noPermissionMessage, rule));

						throw new EventHandledException(true);
					}

					Debugger.debug("operator", "\tno required sender permission");
					return false;
				}
			}

			// Require sender script
			if (rule.getRequireSenderScript() != null) {
				final Object result = JavaScriptExecutor.run(this.replaceVariablesLegacy(rule.getRequireSenderScript(), rule), this.audience);

				if (result != null) {
					ValidCore.checkBoolean(result instanceof Boolean, "require sender script condition must return boolean not " + (result == null ? "null" : result.getClass()) + " for rule " + rule);

					if (!((boolean) result)) {
						Debugger.debug("operator", "\tno required sender script");

						return false;
					}
				}
			}

			// Require sender server
			if (!rule.getRequireSenderServers().isEmpty()) {
				boolean found = false;

				for (final String server : rule.getRequireSenderServers())
					if (senderServerName.equalsIgnoreCase(server))
						found = true;

				if (!found) {
					Debugger.debug("operator", "\tno require sender server");

					return false;
				}
			}

			// Ignore sender permission
			if (rule.getIgnoreSenderPermission() != null && this.audience.hasPermission(rule.getIgnoreSenderPermission())) {
				Debugger.debug("operator", "\tignore sender permission found");

				return false;
			}

			// Ignore sender script
			if (rule.getIgnoreSenderScript() != null) {
				final Object result = JavaScriptExecutor.run(this.replaceVariablesLegacy(rule.getIgnoreSenderScript(), rule), this.audience);

				if (result != null) {
					ValidCore.checkBoolean(result instanceof Boolean, "ignore sender script condition must return boolean not " + (result == null ? "null" : result.getClass()) + " for rule " + rule);

					if (((boolean) result)) {
						Debugger.debug("operator", "\tignore sender script found");

						return false;
					}
				}
			}

			// Ignore sender server
			if (!rule.getIgnoreSenderServers().isEmpty()) {
				boolean found = false;

				for (final String server : rule.getIgnoreSenderServers())
					if (senderServerName.equalsIgnoreCase(server))
						found = true;

				if (found) {
					Debugger.debug("operator", "\tignore sender server found");

					return false;
				}
			}

			return true;
		}

		@Override
		protected Map<String, Object> prepareVariables(final ProxyRule operator) {
			final Map<String, Object> map = SyncedCache.getPlaceholders(this.audience, PlaceholderPrefix.PLAYER);

			map.put("original_message", this.originalMessage);
			map.put("command", this.originalMessage);

			if (!operator.getName().isEmpty()) {
				map.put("rule_name", operator.getName());
				map.put("ruleID", operator.getName());
			}

			// Add regex group matches for {0}, {1}, etc.
			if (this.matcher != null)
				for (int i = 0; i <= this.matcher.groupCount(); i++)
					map.put(String.valueOf(i), this.matcher.group(i) != null ? this.matcher.group(i) : "");

			return map;
		}

		/**
		 * Also replace $0, $1, $2 etc. with regex group matches
		 */
		@Override
		protected String replaceVariablesLegacy(@NonNull final String message, final ProxyRule operator) {
			String result = super.replaceVariablesLegacy(message, operator);

			if (this.matcher != null)
				for (int i = this.matcher.groupCount(); i >= 0; i--) {
					final String group = this.matcher.group(i);

					result = result.replace("$" + i, group != null ? group : "");
				}

			return result;
		}
	}
}
