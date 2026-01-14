package org.mineacademy.chatcontrol.command.chatcontrol;

import java.util.List;

import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.MainSubCommand;
import org.mineacademy.chatcontrol.model.ChatControlProxyMessage;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.ProxyUtil;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.settings.Lang;

public final class ForwardSubCommand extends MainSubCommand {

	public ForwardSubCommand() {
		super("forward/f");

		this.setMinArguments(2);
		this.setPermission(Permissions.Command.FORWARD);
		this.setUsage(Lang.component("command-forward-usage"));
		this.setDescription(Lang.component("command-forward-description"));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		return Lang.component("command-forward-usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		this.checkBoolean(this.isPlayer() || Settings.Proxy.ALLOW_CONSOLE_FORWARD_COMMAND, "Due to security, you need to send this command as a player. Set 'Allow_Console_Forward_Command' to true in proxy.yml to allow console execution.");
		this.checkBoolean(Settings.Proxy.ENABLED, Lang.component("command-no-proxy"));

		final String server = this.args[0];
		final String command = this.joinArgs(1);

		this.checkBoolean("proxy".equals(server) || SyncedCache.getServers().contains(server),
				Lang.component("command-forward-unknown-server", "available", SyncedCache.getServers()));

		this.tellInfo(Lang.component("command-forward-success"));

		if (this.isPlayer())
			ProxyUtil.sendPluginMessageAs(this.getPlayer(), ChatControlProxyMessage.FORWARD_COMMAND, server, Variables.builder(this.audience).replaceLegacy(command));
		else
			ProxyUtil.sendPluginMessage(ChatControlProxyMessage.FORWARD_COMMAND, server, command);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (this.args.length == 1)
			return this.completeLastWord("proxy", SyncedCache.getServers());

		return NO_COMPLETE;
	}
}
