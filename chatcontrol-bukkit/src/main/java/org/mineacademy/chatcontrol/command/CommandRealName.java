package org.mineacademy.chatcontrol.command;

import java.util.List;

import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.operator.Tag.Type;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.settings.Lang;

public final class CommandRealName extends ChatControlCommand {

	public CommandRealName() {
		super(Settings.RealName.COMMAND_ALIASES);

		this.setValidArguments(0, 1);
		this.setUsage(Lang.component("command-real-name-usage"));
		this.setDescription(Lang.component("command-real-name-description"));
		this.setPermission(Permissions.Command.REAL_NAME);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		this.checkBoolean(this.args.length == 1 || this.isPlayer(), Lang.component("command-console-missing-player-name"));

		this.pollCache(this.args.length == 1 ? this.args[0] : this.audience.getName(), cache -> {
			final boolean hasNick = Settings.Tag.APPLY_ON.contains(Type.NICK) && cache.hasTag(Type.NICK);

			this.tellInfo(Lang.component("command-real-name-" + (hasNick ? "" : "no-") + "nick",
					"player", cache.getPlayerName(),
					"nick", CommonCore.getOrEmpty(cache.getTag(Type.NICK))));
		});
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (this.args.length == 1)
			return this.completeLastWordPlayerNames();

		return NO_COMPLETE;
	}
}
