package org.mineacademy.chatcontrol.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.ChatControlProxyMessage;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.ProxyUtil;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.settings.Lang;

public final class CommandMotd extends ChatControlCommand {

	public CommandMotd() {
		super(Settings.Motd.COMMAND_ALIASES);

		this.setMaxArguments(1);
		this.setUsage(Lang.component("command-motd-usage"));
		this.setDescription(Lang.component("command-motd-description"));
		this.setPermission(Permissions.Command.MOTD);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		final List<SimpleComponent> usages = new ArrayList<>();

		usages.add(Lang.component("command-motd-usages-self"));

		if (this.hasPerm(Permissions.Command.MOTD_OTHERS))
			usages.add(Lang.component("command-motd-usages-others"));

		return SimpleComponent.join(usages);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		this.checkBoolean(this.isPlayer() || this.args.length == 1, Lang.component("command-console-missing-player-name"));

		this.pollCache(this.args.length == 1 ? this.args[0] : this.audience.getName(), cache -> {
			this.checkBoolean(SyncedCache.isPlayerConnected(cache.getUniqueId()), Lang.component("player-not-online", "player", cache.getPlayerName()));

			final boolean self = cache.getPlayerName().equals(this.audience.getName());

			if (!self)
				this.checkPerm(Permissions.Command.MOTD_OTHERS);

			final Player player = Bukkit.getPlayerExact(cache.getPlayerName());

			if (player != null)
				Players.showMotd(WrappedSender.fromPlayerCaches(player, cache, SenderCache.from(player)), false);

			else if (Settings.Proxy.ENABLED)
				ProxyUtil.sendPluginMessage(ChatControlProxyMessage.MOTD, cache.getUniqueId());

			if (!self)
				this.tellSuccess(Lang.component("command-motd-success"));
		});
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		return this.completeLastWordPlayerNames();
	}
}
