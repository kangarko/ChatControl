package org.mineacademy.chatcontrol.command.chatcontrol;

import java.util.List;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.MainSubCommand;
import org.mineacademy.chatcontrol.model.Newcomer;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.TimeUtil;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.settings.Lang;

public final class InfoSubCommand extends MainSubCommand {

	public InfoSubCommand() {
		super("info");

		this.setMinArguments(2);
		this.setPermission(Permissions.Command.INFO);
		this.setUsage(Lang.component("command-info-usage"));
		this.setDescription(Lang.component("command-info-description"));
	}

	@Override
	protected SimpleComponent getMultilineUsage() {
		return Lang.component("command-info-usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		this.checkUsage(this.args.length >= 2);

		final String param = this.args[0];

		if ("cache".equals(param)) {
			this.checkUsage(this.args.length > 1);

			this.pollCache(this.args[1], cache -> {
				this.tellNoPrefix("&8" + CommonCore.chatLineSmooth());
				this.tellNoPrefix(Lang.component("command-info-cache-player", "player", cache.getPlayerName()));
				this.tellNoPrefix(cache.toDataSectionOfMap().toStringFormatted()
						.replace("\t", "    ")
						.replace("'", "")
						.replace("=", "&7=&f")
						.replace("[", "&7[&f")
						.replace("]", "&7]&f")
						.replace("{", "&7{&f")
						.replace("}", "&7}&f"));
			});

			return;
		}

		final Player player = this.findPlayer(this.args[1]);

		if ("newcomer".equals(param)) {
			if (player.hasPermission(Permissions.Bypass.NEWCOMER))
				this.tellInfo("Player has permission '" + Permissions.Bypass.NEWCOMER + "', newcomer check skipped.");
			else
				this.tellInfo(Lang.component("command-info-is" + (Newcomer.isNewcomer(player) ? "" : "-not") + "-newcomer",
						"player", player.getName(),
						"date_joined", TimeUtil.formatTimeShort((System.currentTimeMillis() - player.getFirstPlayed()) / 1000)));

		} else if ("variables".equals(param) || "variable".equals(param)) {
			this.checkArgs(3, Lang.component("command-info-variables-no-message"));

			final String message = this.joinArgs(2);
			final long now = System.currentTimeMillis();
			final SimpleComponent replaced = Variables.builder().audience(player).replaceComponent(SimpleComponent.fromMiniAmpersand(message));

			this.tellNoPrefix(Lang.component("command-info-variables" + (replaced.isEmpty() ? "-empty" : ""),
					"time", (System.currentTimeMillis() - now),
					"result", replaced));

		} else
			this.returnInvalidArgs(param);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		if (this.args.length == 1)
			return this.completeLastWord("cache", "newcomer", "variables");

		if (this.args.length == 2)
			return this.completeLastWordPlayerNames();

		return NO_COMPLETE;
	}
}
