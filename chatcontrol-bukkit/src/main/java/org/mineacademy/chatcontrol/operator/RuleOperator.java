package org.mineacademy.chatcontrol.operator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.bukkit.GameMode;
import org.mineacademy.chatcontrol.api.RuleReplaceEvent;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.ChannelMode;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.RandomUtil;
import org.mineacademy.fo.ReflectionUtil;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.exception.FoScriptException;
import org.mineacademy.fo.model.JavaScriptExecutor;
import org.mineacademy.fo.model.RequireVariable;
import org.mineacademy.fo.model.RuleTextReplacer;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.region.DiskRegion;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class RuleOperator extends Operator {

	/**
	 * A fix for for example the domain filter catching the "/chatcontrol:me" as URL in "/chatcontrol:me hello"
	 */
	private boolean ignoreCommandPrefix = false;

	/**
	 * Replace the matching expression in the message with the optional replacement
	 */
	private final Map<Pattern, String> beforeReplace = new HashMap<>();

	/**
	 * Permission required for the rule to apply,
	 * message sent to player if he lacks it.
	 */
	@Nullable
	private Tuple<String, String> requirePermission;

	/**
	 * Parse the given boolean and only continue with the rule if it's outcome is equaling the operator value.
	 */
	@Nullable
	private final List<RequireVariable> requireVariables = new ArrayList<>();

	/**
	 * JavaScript boolean output required to be true for the rule to apply
	 */
	@Nullable
	private String requireScript;

	/**
	 * A list of command labels including / that are required for this rule.
	 */
	private final Set<String> requireCommands = new HashSet<>();

	/**
	 * Gamemodes to require
	 */
	private final Set<GameMode> requireGamemodes = new HashSet<>();

	/**
	 * World names to require
	 */
	private final Set<String> requireWorlds = new HashSet<>();

	/**
	 * Region names to require
	 */
	private final Set<String> requireRegions = new HashSet<>();

	/**
	 * Channels and their modes to require
	 */
	private final Map<String, String> requireChannels = new HashMap<>();

	/**
	 * Permission to bypass the rule
	 */
	private String ignorePermission;

	/**
	 * The matches that, if one matched, will make the rule be ignored
	 */
	private final Set<Pattern> ignoreMatches = new HashSet<>();

	/**
	 * JavaScript boolean output when true for the rule to bypass
	 */
	private String ignoreScript;

	/**
	 * A list of command labels including / that will be ignored.
	 */
	private final Set<String> ignoreCommands = new HashSet<>();

	/**
	 * Gamemodes to ignore
	 */
	private final Set<GameMode> ignoreGamemodes = new HashSet<>();

	/**
	 * World names to ignore
	 */
	private final Set<String> ignoreWorlds = new HashSet<>();

	/**
	 * Region names to ignore
	 */
	private final Set<String> ignoreRegions = new HashSet<>();

	/**
	 * List of channels and their modes to ignore matching from
	 */
	private final Map<String, String> ignoreChannels = new HashMap<>();

	/**
	 * List of strings to randomly select to replace the matching part of the message to
	 */
	private final Set<String> replacements = new HashSet<>();

	/**
	 * List of strings to randomly select to completely rewrite the whole message to
	 */
	private final Set<String> rewrites = new HashSet<>();

	/**
	 * List of strings blahblahblah but for each world differently
	 */
	private final Map<String, Set<String>> worldRewrites = new HashMap<>();

	/**
	 * @see org.mineacademy.chatcontrol.operator.Operator#onParse(java.lang.String, java.lang.String, java.lang.String[])
	 */
	@Override
	protected boolean onParse(final String param, final String theRest, final String[] args) {
		final List<String> theRestSplit = splitVertically(theRest);

		if ("ignore commandprefix".equals(param)) {
			final String rest = param.replace("ignore commandprefix", "");

			this.ignoreCommandPrefix = rest.isEmpty() ? true : Boolean.parseBoolean(rest.trim());

		} else if ("before replace".equals(param)) {
			final String[] split = theRest.split(" with ");
			final Pattern regex = CommonCore.compilePattern(split[0]);
			final String replacement = split.length > 1 ? split[1] : "";

			this.beforeReplace.put(regex, replacement);
		}

		else if ("require perm".equals(param) || "require permission".equals(param)) {
			this.checkNotSet(this.requirePermission, "require perm");
			final String[] split = theRest.split(" ");

			this.requirePermission = new Tuple<>(split[0], split.length > 1 ? CommonCore.joinRange(1, split) : null);
		}

		else if ("require variable".equals(param)) {
			final String[] split = theRest.split(" ");

			if (split.length != 1 && split.length != 2)
				CommonCore.warning("Invalid 'require variable' syntax - it must be in the form 'require variable <variable> <true/false>' or 'require variable <variable>' (to match if it is true), got: '" + theRest + "' for rule: " + this);
			else
				this.requireVariables.add(RequireVariable.fromLine(theRest));
		}

		else if ("require script".equals(param)) {
			this.checkNotSet(this.requireScript, "require script");

			this.requireScript = theRest;
		}

		else if ("require command".equals(param) || "require commands".equals(param))
			this.requireCommands.addAll(theRestSplit);

		else if ("require gamemode".equals(param) || "require gamemodes".equals(param))
			for (final String modeName : theRestSplit) {
				final GameMode gameMode = ReflectionUtil.lookupEnum(GameMode.class, modeName);

				this.requireGamemodes.add(gameMode);
			}
		else if ("require world".equals(param) || "require worlds".equals(param))
			this.requireWorlds.addAll(theRestSplit);

		else if ("require region".equals(param) || "require regions".equals(param))
			this.requireRegions.addAll(theRestSplit);

		else if ("require channel".equals(param) || "require channels".equals(param))
			for (final String channelAndMode : theRestSplit) {
				final String[] split = channelAndMode.split(" ");

				final String channelName = split[0];
				final String mode = split.length == 2 ? split[1] : "";

				this.requireChannels.put(channelName, mode);
			}

		else if ("ignore perm".equals(param) || "ignore permission".equals(param)) {
			this.checkNotSet(this.ignorePermission, "ignore perm");

			this.ignorePermission = theRest;
		}

		else if ("ignore string".equals(param)) {
			final Pattern pattern = CommonCore.compilePattern(theRest);

			checkBoolean(!this.ignoreMatches.contains(pattern), "'ignore string' already contains: " + theRest + " for: " + this);
			this.ignoreMatches.add(pattern);
		}

		else if ("ignore script".equals(param)) {
			this.checkNotSet(this.ignoreScript, "ignore script");

			this.ignoreScript = theRest;
		}

		else if ("ignore command".equals(param) || "ignore commands".equals(param))
			this.ignoreCommands.addAll(theRestSplit);

		else if ("ignore gamemode".equals(param) || "ignore gamemodes".equals(param))
			for (final String modeName : theRestSplit) {
				final GameMode gameMode = ReflectionUtil.lookupEnum(GameMode.class, modeName);

				this.ignoreGamemodes.add(gameMode);
			}

		else if ("ignore world".equals(param) || "ignore worlds".equals(param))
			this.ignoreWorlds.addAll(theRestSplit);

		else if ("ignore region".equals(param) || "ignore regions".equals(param))
			this.ignoreRegions.addAll(theRestSplit);

		else if ("ignore channel".equals(param) || "ignore channels".equals(param))
			for (final String channelAndMode : theRestSplit) {
				final String[] split = channelAndMode.split(" ");

				final String channelName = split[0];
				final String mode = split.length == 2 ? split[1] : "";

				this.ignoreChannels.put(channelName, mode);
			}

		else if ("then replace".equals(param))
			this.replacements.addAll(theRestSplit);

		else if ("then rewrite".equals(param))
			this.rewrites.addAll(theRestSplit);

		else if ("then rewritein".equals(param)) {
			final String[] split = theRest.split(" ");
			checkBoolean(split.length > 1, "wrong then rewritein syntax! Usage: <world> <message>");

			final String world = split[0];
			final List<String> messages = splitVertically(CommonCore.joinRange(1, split));

			this.worldRewrites.put(world, new HashSet<>(messages));

		} else
			return false;

		return true;
	}

	/**
	 * Collect all options we have to debug
	 *
	 * @return
	 */
	@Override
	protected Map<String, Object> collectOptions() {
		final Map<String, Object> map = super.collectOptions();

		map.putAll(CommonCore.newHashMap(
				"Ignore Command Prefix", this.ignoreCommandPrefix,
				"Before Replace", this.beforeReplace,
				"Require Permission", this.requirePermission,
				"Require Variable", this.requireVariables,
				"Require Script", this.requireScript,
				"Require Commands", this.requireCommands,
				"Require Gamemodes", this.requireGamemodes,
				"Require Worlds", this.requireWorlds,
				"Require Regions", this.requireRegions,
				"Require Channels", this.requireChannels,
				"Ignore Permission", this.ignorePermission,
				"Ignore Matches", this.ignoreMatches,
				"Ignore Script", this.ignoreScript,
				"Ignore Commands", this.ignoreCommands,
				"Ignore Gamemodes", this.ignoreGamemodes,
				"Ignore Worlds", this.ignoreWorlds,
				"Ignore Regions", this.ignoreRegions,
				"Ignore Channels", this.ignoreChannels,
				"Replacements", this.replacements,
				"Rewrites", this.rewrites,
				"World Rewrites", this.worldRewrites));

		return map;
	}

	/* ------------------------------------------------------------------------------- */
	/* Classes */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Represents a check that is implemented by this class
	 * @param <T>
	 */
	public abstract static class RuleOperatorCheck<T extends RuleOperator> extends OperatorCheck<RuleOperator> {

		/**
		 * Channel wherefrom the checked message is sent, null if N/A
		 */
		@Nullable
		protected final Channel channel;

		/**
		 * Has the message been changed by the rule?
		 */
		@Getter
		private boolean messageChanged;

		/**
		 * @param wrapped
		 * @param message
		 */
		protected RuleOperatorCheck(final WrappedSender wrapped, final String message, @Nullable final Channel channel) {
			super(wrapped, message);

			this.channel = channel;
		}

		/**
		 * @see org.mineacademy.chatcontrol.model.Checkable#canFilter(org.bukkit.command.CommandSender, java.lang.String, org.mineacademy.chatcontrol.operator.Operator)
		 */
		@Override
		protected boolean canFilter(final RuleOperator operator) {

			// ----------------------------------------------------------------
			// Require
			// ----------------------------------------------------------------

			if (operator.getRequirePermission() != null) {
				final String permission = operator.getRequirePermission().getKey();
				final String noPermissionMessage = operator.getRequirePermission().getValue();

				if (!this.wrappedSender.hasPermission(permission)) {
					if (noPermissionMessage != null) {
						CommonCore.tell(this.wrappedSender.getAudience(), this.replaceSenderVariablesLegacy(noPermissionMessage, operator).replace("{permission}", permission));

						throw new EventHandledException(true);
					}

					return false;
				}
			}

			for (final RequireVariable requiredVariable : operator.getRequireVariables())
				if (!requiredVariable.matches(variable -> this.replaceSenderVariablesLegacy(variable, operator)))
					return false;

			if (operator.getRequireScript() != null) {
				Object result;

				try {
					result = JavaScriptExecutor.run(this.replaceSenderVariablesLegacy(operator.getRequireScript(), operator), this.wrappedSender.getAudience());

				} catch (final FoScriptException ex) {
					CommonCore.logFramed(
							"Error parsing 'require script' in rule!",
							"Rule " + operator.getUniqueName() + " in " + operator.getFile(),
							"",
							"Raw script: " + operator.getRequireScript(),
							"Evaluated script with variables replaced: '" + this.replaceSenderVariablesLegacy(operator.getRequireScript(), operator) + "'",
							"Sender: " + this.wrappedSender,
							"Error: " + ex.getMessage(),
							"",
							"Check that the evaluated script",
							"above is a valid JavaScript!");

					throw ex;
				}

				if (result != null) {
					checkBoolean(result instanceof Boolean, "require script condition must return boolean not " + (result == null ? "null" : result.getClass()) + " for rule " + operator);

					if (!((boolean) result))
						return false;
				}
			}

			if (!operator.getRequireGamemodes().isEmpty())
				if (!this.wrappedSender.isPlayer() || !operator.getRequireGamemodes().contains(this.wrappedSender.getPlayer().getGameMode()))
					return false;

			if (!operator.getRequireWorlds().isEmpty())
				if (!this.wrappedSender.isPlayer() || !ValidCore.isInList(this.wrappedSender.getPlayer().getWorld().getName(), operator.getRequireWorlds()))
					return false;

			if (!operator.getRequireRegions().isEmpty()) {
				if (!this.wrappedSender.isPlayer())
					return false;

				final List<String> regions = DiskRegion.findRegionNames(this.wrappedSender.getPlayer().getLocation());
				boolean found = false;

				for (final String requireRegionName : operator.getRequireRegions())
					if (regions.contains(requireRegionName)) {
						found = true;

						break;
					}

				if (!found)
					return false;
			}

			if (this.channel != null && !operator.getRequireChannels().isEmpty()) {
				boolean foundChannel = false;

				if (!this.wrappedSender.isPlayer())
					return false;

				for (final Entry<String, String> entry : operator.getRequireChannels().entrySet()) {
					final String channelName = entry.getKey();
					final String channelMode = entry.getValue();

					// Logical fallacy, can never send a message from reading channel
					if ("read".equals(channelMode) && this.wrappedSender.getPlayerCache().getChannelMode(channelName) == ChannelMode.READ) {
						foundChannel = true;

						continue;
					}

					if (this.channel.getName().equalsIgnoreCase(channelName) && this.wrappedSender.getPlayerCache().isInChannel(channelName)
							&& (channelMode.isEmpty() || channelMode.equalsIgnoreCase(this.wrappedSender.getPlayerCache().getChannelMode(channelName).getKey()))) {
						foundChannel = true;

						break;
					}
				}

				if (!foundChannel)
					return false;
			}

			// ----------------------------------------------------------------
			// Ignore
			// ----------------------------------------------------------------

			if (operator.getIgnorePermission() != null && this.wrappedSender.hasPermission(operator.getIgnorePermission()))
				return false;

			for (final Pattern ignoreMatch : operator.getIgnoreMatches())
				if (ignoreMatch.matcher(this.message).find())
					return false;

			if (operator.getIgnoreScript() != null) {
				final Object result;

				try {
					result = JavaScriptExecutor.run(this.replaceSenderVariablesLegacy(operator.getIgnoreScript(), operator), this.wrappedSender.getAudience());

				} catch (final FoScriptException ex) {
					CommonCore.logFramed(
							"Error parsing 'ignore script' in rule!",
							"Rule " + operator.getUniqueName() + " in " + operator.getFile(),
							"",
							"Raw script: " + operator.getIgnoreScript(),
							"Evaluated script with variables replaced: '" + this.replaceSenderVariablesLegacy(operator.getIgnoreScript(), operator) + "'",
							"Sender: " + this.wrappedSender,
							"Error: " + ex.getMessage(),
							"",
							"Check that the evaluated script",
							"above is a valid JavaScript!");

					throw ex;
				}

				if (result != null) {
					checkBoolean(result instanceof Boolean, "ignore script condition must return boolean not " + (result == null ? "null" : result.getClass()) + " for rule " + operator);

					if (((boolean) result))
						return false;
				}
			}

			if (this.wrappedSender.isPlayer()) {
				if (operator.getIgnoreGamemodes().contains(this.wrappedSender.getPlayer().getGameMode()))
					return false;

				if (operator.getIgnoreWorlds().contains(this.wrappedSender.getPlayer().getWorld().getName()))
					return false;

				for (final String playersRegion : DiskRegion.findRegionNames(this.wrappedSender.getPlayer().getLocation()))
					if (operator.getIgnoreRegions().contains(playersRegion))
						return false;

				if (this.channel != null) {
					Debugger.debug("operator", "Ignored channels: " + operator.getIgnoreChannels());

					for (final Entry<String, String> entry : operator.getIgnoreChannels().entrySet()) {
						final String channelName = entry.getKey();
						final String channelMode = entry.getValue();

						// Logical fallacy, can never send a message from reading channel
						if ("read".equals(channelMode) && this.wrappedSender.getPlayerCache().getChannelMode(channelName) == ChannelMode.READ)
							return false;

						if (this.channel.getName().equalsIgnoreCase(channelName) && this.wrappedSender.getPlayerCache().isInChannel(channelName)
								&& (channelMode.isEmpty() || channelMode.equalsIgnoreCase(this.wrappedSender.getPlayerCache().getChannelMode(channelName).getKey()))) {
							Debugger.debug("operator", "Not executing due to " + channelName + " in mode " + channelMode + " being ignored");

							return false;
						}
					}
				}
			}

			return super.canFilter(operator);
		}

		/**
		 * Run given operators for the given message and return the updated message
		 */
		protected void executeOperators(final RuleOperator operator, @Nullable final Matcher matcher) throws EventHandledException {

			// Delay
			if (operator.getDelay() != null) {
				final SimpleTime time = operator.getDelay().getKey();
				final String message = operator.getDelay().getValue();

				final long now = System.currentTimeMillis();

				// Round the number due to Bukkit scheduler lags
				final long delay = Math.round((now - operator.getLastExecuted()) / 1000D);

				if (delay < time.getTimeSeconds()) {
					Debugger.debug("operator", "\tbefore delay: " + delay + " threshold: " + time.getTimeSeconds());

					this.cancel(message == null ? null
							: this.replaceSenderVariablesLegacy(message
									.replace("{delay}", time.getTimeSeconds() - delay + ""), operator),
							message != null);
				}

				operator.setLastExecuted(now);
			}

			// Player Delay
			if (operator.getPlayerDelay() != null) {
				final SimpleTime time = operator.getPlayerDelay().getKey();
				final String message = operator.getPlayerDelay().getValue();

				final long now = System.currentTimeMillis();

				// Round the number due to Bukkit scheduler lags
				final long delay = Math.round((now - operator.getLastExecuted(this.wrappedSender.getSender())) / 1000D);

				if (delay < time.getTimeSeconds()) {
					Debugger.debug("operator", "\tbefore player delay: " + delay + " threshold: " + time.getTimeSeconds());

					this.cancel(message == null ? null
							: this.replaceSenderVariablesLegacy(message
									.replace("{delay}", time.getTimeSeconds() - delay + "")
									.replace("{player_delay}", time.getTimeSeconds() - delay + ""), operator),
							message != null);
				}

				operator.setLastExecuted(this.wrappedSender.getSender());
			}

			// Save performance
			boolean rewritten = false;

			if (this.wrappedSender.isPlayer())
				if (operator.getWorldRewrites().containsKey(this.wrappedSender.getPlayer().getWorld().getName())) {
					final String replacedMatch = RandomUtil.nextItem(operator.getWorldRewrites().get(this.wrappedSender.getPlayer().getWorld().getName()));

					// Call API
					final RuleReplaceEvent event = new RuleReplaceEvent(this.wrappedSender.getSender(), this.message, replacedMatch, operator, false);

					if (Platform.callEvent(event)) {
						this.message = event.getReplacedMatch();

						this.messageChanged = true;
						rewritten = true;
					}
				}

			if (!rewritten && !operator.getRewrites().isEmpty()) {
				final String replacedMatch = this.replaceSenderVariablesLegacy(RandomUtil.nextItem(operator.getRewrites()), operator);

				// Call API
				final RuleReplaceEvent event = new RuleReplaceEvent(this.wrappedSender.getSender(), this.message, replacedMatch, operator, false);

				if (Platform.callEvent(event)) {
					this.message = event.getReplacedMatch();

					this.messageChanged = true;
					rewritten = true;
				}
			}

			if (!rewritten && matcher != null && !operator.getReplacements().isEmpty()) {
				final boolean isRuleClass = operator instanceof Rule;
				Map<Pattern, String> beforeReplace = operator.getBeforeReplace();

				// Update the message with the matcher's content, this fixes inconsistencies if matcher removes colors
				if (isRuleClass) {
					if (((Rule) operator).isStripColors()) {
						this.message = SimpleComponent.fromMiniSection(this.message).toPlain();

						beforeReplace = operator.getBeforeReplace();
					}

				} else if (Settings.Rules.STRIP_COLORS)
					this.message = SimpleComponent.fromMiniSection(this.message).toPlain();

				if (isRuleClass) {
					if (((Rule) operator).isStripAccents())
						this.message = ChatUtil.replaceDiacritic(this.message);

				} else if (Settings.Rules.STRIP_ACCENTS)
					this.message = ChatUtil.replaceDiacritic(this.message);

				for (final Map.Entry<Pattern, String> entry : beforeReplace.entrySet())
					this.message = entry.getKey().matcher(this.message).replaceAll(entry.getValue());

				final RuleTextReplacer replacer = new RuleTextReplacer();
				final String replacement = this.replaceSenderVariablesLegacy(RandomUtil.nextItem(operator.getReplacements()), operator);

				final Component replacedAdventureComponent = replacer.replaceWithProlong(this.message, matcher.pattern(), replacement);

				if (replacer.isChanged()) {

					// Call API
					final RuleReplaceEvent event = new RuleReplaceEvent(this.wrappedSender.getSender(), this.message, PlainTextComponentSerializer.plainText().serialize(replacedAdventureComponent), operator, false);

					if (Platform.callEvent(event)) {
						this.message = event.getReplacedMatch();

						this.messageChanged = true;
					}
				}
			}

			super.executeOperators(operator);
		}

		@Override
		protected Map<String, Object> prepareVariables(final WrappedSender wrapped, final RuleOperator operator) {
			final Map<String, Object> map = super.prepareVariables(wrapped, operator);

			final long now = System.currentTimeMillis();

			if (operator.getDelay() != null) {
				final SimpleTime time = operator.getDelay().getKey();
				final long delay = Math.round((now - operator.getLastExecuted()) / 1000D);

				map.put("delay", delay < time.getTimeSeconds() ? time.getTimeSeconds() - delay + "" : "");
			}

			if (operator.getPlayerDelay() != null) {
				final SimpleTime time = operator.getPlayerDelay().getKey();
				final long delay = Math.round((now - operator.getLastExecuted(this.wrappedSender.getSender())) / 1000D);

				map.put("player_delay", delay < time.getTimeSeconds() ? time.getTimeSeconds() - delay + "" : "");
			}

			return map;
		}
	}
}
