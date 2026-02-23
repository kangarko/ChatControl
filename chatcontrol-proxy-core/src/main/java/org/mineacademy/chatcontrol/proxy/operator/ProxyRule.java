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
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.model.CompChatColor;
import org.mineacademy.fo.model.JavaScriptExecutor;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.platform.FoundationPlayer;

import lombok.Getter;
import lombok.NonNull;

@Getter
public final class ProxyRule extends ProxyOperator {

	private final Pattern pattern;

	private String name = "";

	private Tuple<String, SimpleComponent> requireSenderPermission;

	private String requireSenderScript;

	private final Set<String> requireSenderServers = new HashSet<>();

	private String ignoreSenderPermission;

	private String ignoreSenderScript;

	private final Set<String> ignoreSenderServers = new HashSet<>();

	private Boolean stripColors;

	private Boolean stripAccents;

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

		else if ("require sender perm".equals(firstThreeParams) || "require sender permission".equals(firstThreeParams)
				|| "require perm".equals(param) || "require permission".equals(param)) {
			this.checkNotSet(this.requireSenderPermission, "require sender perm");

			final boolean isSenderForm = firstThreeParams.startsWith("require sender ");
			final String rest = isSenderForm ? theRestThree : CommonCore.joinRange(2, args);
			final String[] split = rest.split(" ");

			this.requireSenderPermission = new Tuple<>(split[0], split.length > 1 ? SimpleComponent.fromMiniAmpersand(CommonCore.joinRange(1, split)) : null);
		}

		else if ("require sender script".equals(firstThreeParams)) {
			this.checkNotSet(this.requireSenderScript, "require sender script");

			this.requireSenderScript = theRestThree;
		}

		else if ("require sender server".equals(firstThreeParams))
			this.requireSenderServers.add(theRestThree);

		else if ("ignore sender perm".equals(firstThreeParams) || "ignore sender permission".equals(firstThreeParams)
				|| "ignore perm".equals(param) || "ignore permission".equals(param)) {
			this.checkNotSet(this.ignoreSenderPermission, "ignore sender perm");

			final boolean isSenderForm = firstThreeParams.startsWith("ignore sender ");
			this.ignoreSenderPermission = isSenderForm ? theRestThree : CommonCore.joinRange(2, args);
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

		else
			return false;

		return true;
	}

	@Override
	protected SerializedMap collectOptions() {
		return super.collectOptions().putArray(
				"Pattern", this.pattern.pattern(),
				"Name", this.name,
				"Require Sender Permission", this.requireSenderPermission != null ? this.requireSenderPermission.getKey() : null,
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

	public static final class RuleCheck extends OperatorCheck<ProxyRule> {

		private final String originalMessage;

		private String message;

		private Matcher matcher;

		private String[] matchedGroups;

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

			String messageToMatch = this.message;

			if (rule.getStripColors() != null && rule.getStripColors())
				messageToMatch = CompChatColor.stripColorCodes(messageToMatch);

			if (rule.getStripAccents() != null && rule.getStripAccents())
				messageToMatch = ChatUtil.replaceDiacritic(messageToMatch);

			this.matcher = rule.getPattern().matcher(messageToMatch);

			if (!this.matcher.find())
				return;

			this.matchedGroups = new String[this.matcher.groupCount() + 1];

			for (int i = 0; i <= this.matcher.groupCount(); i++)
				this.matchedGroups[i] = this.matcher.group(i) != null ? this.matcher.group(i) : "";

			if (!rule.isIgnoreVerbose())
				this.verbose("&f*-----possibly-filtered-proxy-command-----*",
						"&fCommand: &b" + this.originalMessage,
						"&fMatch &b" + rule.getUniqueName() + (rule.getName().isEmpty() ? "" : " &f(name: &b" + rule.getName() + "&f)"));

			if (!this.canFilterRule(rule))
				return;

			this.executeOperators(rule);

			if (rule.isAbort())
				throw new OperatorAbortException();
		}

		private boolean canFilterRule(final ProxyRule rule) {
			final String senderServerName = this.audience.isPlayer() && this.audience.getServer() != null ? this.audience.getServer().getName() : "";

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

			if (rule.getIgnoreSenderPermission() != null && this.audience.hasPermission(rule.getIgnoreSenderPermission())) {
				Debugger.debug("operator", "\tignore sender permission found");

				return false;
			}

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

			if (this.matchedGroups != null)
				for (int i = 0; i < this.matchedGroups.length; i++)
					map.put(String.valueOf(i), this.matchedGroups[i]);

			return map;
		}

		@Override
		protected String replaceVariablesLegacy(@NonNull final String message, final ProxyRule operator) {
			String result = super.replaceVariablesLegacy(message, operator);

			if (this.matchedGroups != null)
				for (int i = this.matchedGroups.length - 1; i >= 0; i--)
					result = result.replace("$" + i, this.matchedGroups[i]);

			return result;
		}
	}
}
