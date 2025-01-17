package org.mineacademy.chatcontrol.command;

import java.util.List;

import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.ChatControlCommand;

public final class CommandDummy extends ChatControlCommand {

	public CommandDummy() {
		super("dummy");

		this.setMinArguments(0);
		this.setPermission(null);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		// This command does nothing, we let people route to it in commands.yml to register it in tab-complete
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		return NO_COMPLETE;
	}
}