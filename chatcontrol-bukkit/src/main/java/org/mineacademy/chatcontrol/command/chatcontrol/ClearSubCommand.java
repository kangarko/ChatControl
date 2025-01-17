package org.mineacademy.chatcontrol.command.chatcontrol;

import java.util.List;

import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.CommandFlagged;
import org.mineacademy.chatcontrol.model.ChatControlProxyMessage;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.ProxyUtil;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.settings.Lang;

public final class ClearSubCommand extends CommandFlagged {

	public ClearSubCommand() {
		super("clear/cl", Lang.component("command-clear-usage"), Lang.component("command-clear-description"));

		this.setPermission(Permissions.Command.CLEAR);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		return Lang.component("command-clear-usages");
	}

	@Override
	protected void execute(final boolean console, final boolean anonymous, final boolean silent, final boolean force, final String reason) {

		// Compile message
		final SimpleComponent announceMessage;

		if (silent)
			announceMessage = SimpleComponent.empty();
		else {
			announceMessage = Lang.component(
					"command-clear-success" + (Settings.Proxy.ENABLED ? "-network" : "") + (anonymous ? "-anonymous" : "") + (reason.isEmpty() ? "-no-reason" : ""),
					"player", this.audience.getName(),
					"reason", reason);
		}

		// Do the actual clear
		if (console)
			for (int i = 0; i < 5000; i++)
				System.out.println("             ");
		else
			Players.clearChat(this.getSender(), announceMessage.isEmpty(), force);

		if (console) {
			this.checkPerm(Permissions.Command.CLEAR_CONSOLE);
			final SimpleComponent message = Lang.component("command-clear-success-console" + (reason.isEmpty() ? "-no-reason" : ""),
					"reason", reason,
					"player", this.audience.getName());

			this.tellSuccess(message);
			CommonCore.log(message.toLegacySection(null));

			return;
		}

		if (!announceMessage.isEmpty())
			Messenger.broadcastAnnounce(announceMessage);

		if (!this.isPlayer())
			this.tellNoPrefix(Variables.builder(this.audience).replaceComponent(announceMessage.isEmpty() ? Lang.component("command-clear-success-staff", "player", this.audience.getName()) : announceMessage));

		if (Settings.Proxy.ENABLED)
			ProxyUtil.sendPluginMessage(ChatControlProxyMessage.CLEAR_CHAT, announceMessage, force);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		if (this.args.length < 3)
			return this.completeLastWord("-anonymous", "-a", "-console", "-c", "-silent", "-s", "-force", "-f");

		return super.tabComplete();
	}
}
