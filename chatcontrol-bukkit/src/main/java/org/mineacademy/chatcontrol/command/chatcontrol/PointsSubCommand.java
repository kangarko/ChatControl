package org.mineacademy.chatcontrol.command.chatcontrol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.MainSubCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.WarningPoints;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.settings.Lang;

public final class PointsSubCommand extends MainSubCommand {

	public PointsSubCommand() {
		super("points/p");

		this.setMinArguments(1);
		this.setPermission(Permissions.Command.POINTS);
		this.setUsage(Lang.component("command-points-usage"));
		this.setDescription(Lang.component("command-points-description"));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		return Lang.component("command-points-usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		this.checkBoolean(Settings.WarningPoints.ENABLED, Lang.component("command-points-disabled"));

		final WarningPoints points = WarningPoints.getInstance();
		final String param = this.args[0];

		if ("list".equals(param)) {
			this.checkUsage(this.args.length < 3);

			final String setName = this.args.length == 2 ? this.args[1] : null;
			this.checkBoolean(setName == null || points.isSetLoaded(setName), Lang.component("command-invalid-warning-set",
					"warning_set", setName,
					"available", points.getSetNames()));

			this.pollCaches(caches -> {

				// Fill in the map
				// <Set name:<Player:Points>>
				final Map<String, Map<String, Integer>> listed = new LinkedHashMap<>();

				for (final PlayerCache cache : caches)
					for (final Map.Entry<String, Integer> entry : cache.getWarnPoints().entrySet()) {
						final String diskSet = entry.getKey();
						final int diskPoints = entry.getValue();

						if (setName == null || setName.equalsIgnoreCase(diskSet)) {
							final Map<String, Integer> playerPoints = listed.getOrDefault(diskSet, new LinkedHashMap<>());
							playerPoints.put(cache.getPlayerName(), diskPoints);

							listed.put(diskSet, playerPoints);
						}
					}

				final List<SimpleComponent> messages = new ArrayList<>();

				for (final Map.Entry<String, Map<String, Integer>> entry : listed.entrySet()) {
					messages.add(SimpleComponent.empty());
					messages.add(Lang.component("command-points-list-item-1", "warning_set", entry.getKey()));

					for (final Map.Entry<String, Integer> setEntry : entry.getValue().entrySet())
						messages.add(SimpleComponent
								.fromMiniNative(" <gray>- ")
								.appendMiniNative("<dark_gray>[<dark_red>X<dark_gray>]")
								.onHover(Lang.component("command-points-remove-tooltip"))
								.onClickRunCmd("/" + this.getLabel() + " " + this.getSublabel() + " set " + setEntry.getKey() + " " + entry.getKey() + " 0")
								.append(Lang.component("command-points-list-item-2", "warning_set", setEntry.getKey(), "points", setEntry.getValue())));
				}

				this.checkBoolean(!messages.isEmpty(), Lang.component("command-points-no-points"));

				new ChatPaginator(12)
						.setFoundationHeader(Lang.legacy("command-points-list-header"))
						.setPages(messages)
						.send(this.audience);
			});

			return;
		}

		this.checkArgs(2, Lang.component("command-no-player-name-given"));
		this.checkUsage(this.args.length >= 2);

		final String setName = this.args.length == 2 ? null : this.args[2];

		this.pollCache(this.args[1], cache -> {

			if ("get".equals(param)) {
				this.checkUsage(this.args.length <= 3);

				// Fill in the map
				// <Set name:Points>
				final Map<String, Integer> listed = new LinkedHashMap<>();

				for (final Map.Entry<String, Integer> entry : cache.getWarnPoints().entrySet()) {
					final String diskSet = entry.getKey();
					final int diskPoints = entry.getValue();

					if (setName == null || setName.equalsIgnoreCase(diskSet))
						listed.put(diskSet, diskPoints);
				}

				final List<SimpleComponent> messages = new ArrayList<>();

				for (final Map.Entry<String, Integer> entry : listed.entrySet())
					messages.add(SimpleComponent
							.fromMiniNative(" <gray>- ")
							.appendMiniNative("<dark_gray>[<dark_red>X<dark_gray>]")
							.onHover(Lang.component("command-points-remove-tooltip"))
							.onClickRunCmd("/" + this.getLabel() + " " + this.getSublabel() + " set " + cache.getPlayerName() + " " + entry.getKey() + " 0")
							.append(Lang.component("command-points-player-list-item", "player", entry.getKey(), "points", entry.getValue())));

				this.checkBoolean(!messages.isEmpty(), Lang.component("command-points-no-player-points", "player", cache.getPlayerName()));

				new ChatPaginator(12)
						.setFoundationHeader(Lang.legacy("command-points-player-list-header", "player", cache.getPlayerName()))
						.setPages(messages)
						.send(this.audience);
			}

			else if ("set".equals(param)) {
				this.checkArgs(3, Lang.component("command-points-no-set", "available", points.getSetNames()));
				this.checkUsage(this.args.length <= 4);
				this.checkBoolean(points.isSetLoaded(setName), Lang.component("command-invalid-type", "type", "warning set", "value", setName, "available", points.getSetNames()));

				final int pointsToSet = this.findInt(3, Lang.component("command-points-no-amount"));

				if (pointsToSet == 0 && cache.getWarnPoints(setName) == 0)
					this.returnTell(Lang.component("command-points-no-stored", "player", cache.getPlayerName(), "warning_set", setName));

				cache.setWarnPointsNoSave(setName, pointsToSet);
				cache.upsert();

				this.tellSuccess(Lang.component("command-points-" + (pointsToSet == 0 ? "removed" : "given"),
						"points", pointsToSet,
						"player", cache.getPlayerName(),
						"warning_set", setName));

				// Notify proxy so that players connected on another server get their channel updated
				this.updateProxyData(cache);
			}

			else
				this.returnInvalidArgs(param);
		});
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (this.args.length == 1)
			return this.completeLastWord("get", "set", "list");

		if (this.args.length == 2) {
			final String param = this.args[0];

			if ("get".equals(param) || "set".equals(param))
				return this.completeLastWordPlayerNames();

			else if ("list".equals(param))
				return this.completeLastWord(WarningPoints.getInstance().getSetNames());
		}

		if (this.args.length == 3 && ("get".equals(this.args[0]) || "set".equals(this.args[0])))
			return this.completeLastWord(WarningPoints.getInstance().getSetNames());

		return NO_COMPLETE;
	}
}
