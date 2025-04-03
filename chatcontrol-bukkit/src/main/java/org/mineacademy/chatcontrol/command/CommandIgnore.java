package org.mineacademy.chatcontrol.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.model.db.Database;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.Lang;

public final class CommandIgnore extends ChatControlCommand {

	public CommandIgnore() {
		super(Settings.Ignore.COMMAND_ALIASES);

		this.setUsage(Lang.component("command-ignore-usage"));
		this.setDescription(Lang.component("command-ignore-description"));
		this.setMinArguments(1);
		this.setPermission(Permissions.Command.IGNORE);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		final List<SimpleComponent> usages = new ArrayList<>();

		usages.add(Lang.component("command-ignore-usages"));

		if (this.hasPerm(Permissions.Command.IGNORE_OTHERS))
			usages.add(Lang.component("command-ignore-usages-others"));

		if (this.hasPerm(Permissions.Command.IGNORE_LIST))
			usages.add(Lang.component("command-ignore-usages-list"));

		if (this.hasPerm(Permissions.Command.IGNORE_LIST) && this.hasPerm(Permissions.Command.IGNORE_OTHERS))
			usages.add(Lang.component("command-ignore-usages-list-others"));

		return SimpleComponent.join(usages);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		this.checkUsage(this.args.length <= 2);

		final String param = this.args[0];
		final boolean otherPlayer = this.args.length == 2;

		this.checkBoolean(this.isPlayer() || otherPlayer, Lang.component("command-console-missing-player-name"));

		if (otherPlayer)
			this.checkPerm(Permissions.Command.IGNORE_OTHERS);

		if ("list".equals(param)) {
			this.checkPerm(Permissions.Command.IGNORE_LIST);

			this.pollCache(otherPlayer ? this.args[1] : this.audience.getName(), listPlayer -> {
				this.checkBoolean(!listPlayer.getIgnoredPlayers().isEmpty(), Lang.component("command-ignore-not-ignoring" + (otherPlayer ? "-other" : ""), "player", listPlayer.getPlayerName()));

				final List<String> ignoredPlayerNames = Database.getInstance().getPlayerNamesSync(listPlayer.getIgnoredPlayers());

				new ChatPaginator()
						.setFoundationHeader(Lang.legacy("command-ignore-list-header", "player", listPlayer.getPlayerName()))
						.setPages(CommonCore.convertList(ignoredPlayerNames, name -> SimpleComponent
								.fromMiniNative(" <gray>- " + name)
								.onHover(Lang.component("command-ignore-list-tooltip-stop", "player", name))
								.onClickRunCmd("/" + this.getLabel() + " " + (otherPlayer ? listPlayer.getPlayerName() + " " : "") + name)))
						.send(this.audience);
			});

			return;
		}

		this.pollCache(otherPlayer ? this.args[0] : this.audience.getName(), forCache -> {
			this.pollCache(this.args[otherPlayer ? 1 : 0], targetCache -> {
				this.checkBoolean(!forCache.getPlayerName().equals(targetCache.getPlayerName()), Lang.component("command-ignore-cannot-ignore-self"));

				final UUID targetId = targetCache.getUniqueId();
				final boolean ignored = forCache.isIgnoringPlayer(targetId);

				final Player otherOnline = Remain.getPlayerByUUID(targetId);

				if (!ignored && otherOnline != null && otherOnline.isOnline() && otherOnline.hasPermission(Permissions.Bypass.REACH))
					this.returnTell(Lang.component("command-ignore-cannot-ignore-admin"));

				if (!ignored && forCache.getIgnoredPlayers().size() > 200)
					this.returnTell(Lang.component("command-ignore-limit-reached", "limit", 200));

				forCache.setIgnoredPlayer(targetId, !ignored);

				// Hook into CMI/Essentials async to prevent server freeze
				Platform.runTask(() -> HookManager.setIgnore(forCache.getUniqueId(), targetId, !ignored));

				this.tellSuccess(Lang.component(
						ignored ? "command-ignore-disable" : "command-ignore-enable" + (otherPlayer ? "-other" : ""),
						"player", forCache.getPlayerName(),
						"target", targetCache.getPlayerName()));

				this.updateProxyData(forCache);
			});
		});
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (this.args.length == 1)
			return this.completeLastWord(
					Players.getPlayerNamesForTabComplete(this.getSender()).stream().filter(name -> !name.equals(this.audience.getName())).collect(Collectors.toList()),
					Arrays.asList(this.hasPerm(Permissions.Command.IGNORE_LIST) ? "list" : ""));

		if (this.args.length == 2 && this.hasPerm(Permissions.Command.IGNORE_OTHERS))
			return this.completeLastWordPlayerNames();

		return NO_COMPLETE;
	}
}