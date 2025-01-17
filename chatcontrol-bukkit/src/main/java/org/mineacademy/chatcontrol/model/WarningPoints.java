package org.mineacademy.chatcontrol.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.util.LogUtil;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.MathUtil;
import org.mineacademy.fo.ProxyUtil;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.exception.CalculatorException;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.platform.FoundationPlayer;
import org.mineacademy.fo.platform.Platform;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of the warning points system.
 *
 * @since 29.09.2020
 */
public final class WarningPoints {

	/**
	 * The instance of this class for public use.
	 */
	@Getter
	private static final WarningPoints instance = new WarningPoints();

	/**
	 * Warning sets.
	 */
	private final Set<WarnSet> sets = new HashSet<>();

	// ---------------------------------------------------------------------------------------------------------------------------
	// Sub-classes
	// ---------------------------------------------------------------------------------------------------------------------------

	/**
	 * Represents a warn trigger
	 */
	@RequiredArgsConstructor
	public static final class WarnTrigger {

		/**
		 * The set name
		 */
		private final String set;

		/**
		 * The formula with variables
		 */
		private final String formula;

		/**
		 * Execute this for player, if false or no warning message show we
		 * show him the fallback one
		 *
		 * @param sender
		 * @param fallbackWarningMessage
		 * @param formulaVariables
		 */
		public void execute(final CommandSender sender, final SimpleComponent fallbackWarningMessage, final SerializedMap formulaVariables) {

			if (Settings.WarningPoints.ENABLED && sender instanceof Player) {
				String expression = this.formula;

				for (final Map.Entry<String, Object> entry : formulaVariables.entrySet()) {
					String key = entry.getKey();
					final String value = CommonCore.simplify(entry.getValue());

					final char startChar = key.charAt(0);
					final char endChar = key.charAt(key.length() - 1);

					if (startChar != '{' && endChar != '}')
						key = "{" + key + "}";

					expression = expression.replace(key, value);
				}

				if (expression.contains("{") || expression.contains("}"))
					throw new FoException("Unparsed variables found when evaluating warning points for " + sender.getName() + ". Supported variables: " + formulaVariables.keySet() + " Warning message: " + fallbackWarningMessage.toLegacySection(null) + ". Please correct your expression: " + expression, false);

				double calculatedPoints;

				try {
					calculatedPoints = MathUtil.calculate(expression);

				} catch (final CalculatorException ex) {
					CommonCore.error(ex,
							"Failed to calculate warning points for " + sender.getName(),
							"Expression: '" + expression + "'",
							"Variables supported: " + formulaVariables,
							"Error: {error}");

					return;
				}

				final boolean warned = WarningPoints.getInstance().givePoints((Player) sender, this.set, calculatedPoints);

				if (warned)
					throw new EventHandledException(true);
			}

			throw new EventHandledException(true, fallbackWarningMessage);
		}
	}

	/**
	 * Represents a warning set with actions.
	 */
	@Getter(AccessLevel.PRIVATE)
	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	public static final class WarnSet {

		private final String name;
		private final List<WarnAction> actions;

		@Override
		public boolean equals(final Object obj) {
			return obj instanceof WarnSet ? ((WarnSet) obj).name.equals(this.name) : false;
		}
	}

	/**
	 * Represents an action in a warning set.
	 */
	@Getter(AccessLevel.PRIVATE)
	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	public final static class WarnAction {

		private final int trigger;
		private final List<String> commands;

		@Override
		public boolean equals(final Object obj) {
			return obj instanceof WarnAction ? ((WarnAction) obj).trigger == this.trigger : false;
		}

		/**
		 * Run this action and return whether or not the player was warned.
		 *
		 * @param audience
		 * @return
		 */
		public boolean execute(final FoundationPlayer audience) {
			boolean warned = true;

			for (String commandLine : this.commands) {
				commandLine = Variables.builder(audience).replaceLegacy(commandLine);

				if (commandLine.startsWith("warn ")) {
					CommonCore.tellLater(2, audience, commandLine.replaceFirst("warn ", ""));
					warned = true;

				} else if (commandLine.startsWith("proxyconsole ") || commandLine.startsWith("bungeeconsole ")) {

					if (commandLine.startsWith("bungeeconsole "))
						CommonCore.warning("The 'bungeeconsole' command is deprecated, use 'proxyconsole' instead in warning point action: " + this);

					if (Settings.Proxy.ENABLED) {
						final String[] split = commandLine.replace("proxyconsole ", "").replace("bungeeconsole ", "").split(" ");
						final String server = split.length > 1 ? split[0] : "proxy";
						final String command = split.length > 1 ? CommonCore.joinRange(1, split) : split[0];

						ProxyUtil.sendPluginMessageAs(audience, ChatControlProxyMessage.FORWARD_COMMAND, server, command);
					}

				} else
					Platform.dispatchConsoleCommand(audience, commandLine);
			}

			return warned;
		}

		@Override
		public String toString() {
			return "Action[" + this.trigger + "]";
		}
	}

	// ---------------------------------------------------------------------------------------------------------------------------
	// Sets manipulation
	// ---------------------------------------------------------------------------------------------------------------------------

	/**
	 * Add a new warning set.
	 *
	 * @param name
	 * @param actions
	 */
	public void addSet(final String name, final SerializedMap actions) {
		ValidCore.checkBoolean(!this.isSetLoaded(name), "Warning set '" + name + "' already exists! (Use /chc reload, not plugin managers!)");

		// Sort actions by points
		final TreeSet<WarnAction> loadedActions = new TreeSet<>((first, second) -> {
			final int x = second.getTrigger();
			final int y = first.getTrigger();

			return x > y ? -1 : x == y ? 0 : 1;
		});

		// Load them
		actions.forEach((triggerRaw, actionsList) -> {
			final int trigger = Integer.parseInt(triggerRaw);

			loadedActions.add(new WarnAction(trigger, (List<String>) actionsList));
		});

		this.sets.add(new WarnSet(name, new ArrayList<>(loadedActions)));
	}

	/**
	 * Returns true if a set by the given name exists.
	 *
	 * @param name
	 * @return
	 */
	public boolean isSetLoaded(final String name) {
		return this.getSet(name) != null;
	}

	/**
	 * Get a set by its name
	 *
	 * @param name
	 * @return
	 */
	public WarnSet getSet(final String name) {
		for (final WarnSet set : this.sets)
			if (set.getName().equals(name))
				return set;

		return null;
	}

	/**
	 * Clears sets, called on reload.
	 */
	public void clearSets() {
		this.sets.clear();
	}

	/**
	 * Return all set names
	 * @return
	 */
	public List<String> getSetNames() {
		return CommonCore.convertList(this.sets, WarnSet::getName);
	}

	// ---------------------------------------------------------------------------------------------------------------------------
	// Giving points
	// ---------------------------------------------------------------------------------------------------------------------------

	/**
	 * Gives points to player and runs proper actions. Returns if the player was warned so you can
	 * prevent sending duplicate messages.
	 * <p>
	 * Please note that we round the amount to a whole number.
	 *
	 * @param player
	 * @param setName
	 * @param exactAmount the warn points amount. If the player is a newcomer it will be multiplied
	 *                     by the setting option this number is later rounded to an integer
	 * @return
	 */
	public boolean givePoints(final Player player, final String setName, final double exactAmount) {
		if (!Settings.WarningPoints.ENABLED || player.hasPermission(Permissions.Bypass.WARNING_POINTS) || exactAmount < 1)
			return false;

		final WarnSet set = this.getSet(setName);
		ValidCore.checkNotNull(set, "Cannot give warn points to a non-existing warn set '" + setName + "'. Available: " + CommonCore.join(this.sets, ", ", WarnSet::getName));

		// Multiply if the player is a newcomer
		final int amount = (int) Math.round(exactAmount);

		final int points = this.assignPoints(player, setName, amount);
		final WarnAction action = this.findHighestAction(set, points, player);

		return action != null && action.execute(Platform.toPlayer(player));
	}

	//
	// Give the warning points to the player's data.yml section.
	// Returns new points
	//
	private int assignPoints(final Player player, final String set, final int amount) {
		final PlayerCache data = PlayerCache.fromCached(player);

		final int oldPoints = data.getWarnPoints(set);
		final int newPoints = oldPoints + amount;

		CommonCore.log("Set " + player.getName() + "'s warning set '" + set + "' points from " + oldPoints + " -> " + newPoints);

		data.setWarnPointsNoSave(set, newPoints);

		Platform.runTask(() -> data.upsert());

		return newPoints;
	}

	//
	// Find the highest action we can run for a player.
	//
	private WarnAction findHighestAction(final WarnSet set, final int points, final Player player) {
		WarnAction highestAction = null;

		for (final WarnAction action : set.getActions()) {
			// If player has more or equals points for this action, assign it.
			if (points >= action.getTrigger()) {

				if (highestAction != null && highestAction.getTrigger() > action.getTrigger())
					continue;

				highestAction = action;
			}
		}

		return highestAction;
	}

	// ---------------------------------------------------------------------------------------------------------------------------
	// Static
	// ---------------------------------------------------------------------------------------------------------------------------

	/**
	 * Start the task removing old warning points if the warning points are enabled and the tasks
	 * repeat period is over 0
	 */
	public static void scheduleTask() {
		if (!Settings.WarningPoints.ENABLED || Settings.WarningPoints.RESET_TASK_PERIOD.getRaw().equals("0"))
			return;

		Platform.runTaskTimer(Settings.WarningPoints.RESET_TASK_PERIOD.getTimeTicks(), () -> {
			for (final Player player : Players.getOnlinePlayersWithLoadedDb()) {
				final PlayerCache data = PlayerCache.fromCached(player);

				for (final Map.Entry<String, Integer> entry : new LinkedHashMap<>(data.getWarnPoints()).entrySet()) {
					final String set = entry.getKey();
					final int amount = entry.getValue();

					final Integer setRemoveThreshold = Settings.WarningPoints.RESET_MAP.get(set);

					// if the remove task has this set
					if (setRemoveThreshold != null) {
						final int remaining = Math.max(0, amount - setRemoveThreshold);

						LogUtil.logTip("TIP Note: Reset " + player.getName() + "'s warning points in " + set + " from " + amount + " to " + remaining);
						data.setWarnPointsNoSave(set, remaining);
						data.upsert();
					}
				}
			}
		});
	}
}
