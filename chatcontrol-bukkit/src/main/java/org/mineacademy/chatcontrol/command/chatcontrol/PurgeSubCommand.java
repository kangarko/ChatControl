package org.mineacademy.chatcontrol.command.chatcontrol;

import java.util.List;
import java.util.UUID;

import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.MainSubCommand;
import org.mineacademy.chatcontrol.model.ChatControlProxyMessage;
import org.mineacademy.chatcontrol.model.Packets;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.ProxyUtil;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.settings.Lang;

public final class PurgeSubCommand extends MainSubCommand {

	public PurgeSubCommand() {
		super("purge");

		this.setMinArguments(1);
		this.setPermission(Permissions.Command.PURGE);
		this.setUsage(Lang.component("command-purge-usage"));
		this.setDescription(Lang.component("command-purge-description"));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		this.checkBoolean(HookManager.isProtocolLibLoaded(), "This feature requires ProtocolLib.");

		for (final String playerName : this.args)
			this.pollCache(playerName, cache -> {
				final UUID uniqueId = cache.getUniqueId();

				Packets.getInstance().removeMessage(Packets.RemoveMode.ALL_MESSAGES_FROM_SENDER, uniqueId);

				if (Settings.Proxy.ENABLED)
					ProxyUtil.sendPluginMessage(ChatControlProxyMessage.REMOVE_MESSAGE, Packets.RemoveMode.ALL_MESSAGES_FROM_SENDER.getKey(), uniqueId);

				this.tellSuccess(Lang.component("command-purge-success", "player", cache.getPlayerName()));
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
