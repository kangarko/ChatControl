package org.mineacademy.chatcontrol.command.chatcontrol;

import java.util.List;

import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.MainSubCommand;
import org.mineacademy.chatcontrol.model.Migrator;

public final class ConvertSubCommand extends MainSubCommand {

	public ConvertSubCommand() {
		super("convert");

		this.setMinArguments(1);
		this.setUsage("<start>");
		this.setDescription("Converts ChatControl 10 variables syntax.");
	}

	@Override
	protected String[] getMultilineUsageMessage() {
		return new String[] {
				"&cThis command scans files in your plugin folder, and replaces:",
				"",
				"&71. &cThe &#ccffdd hex variables to <#ccffdd> mini syntax.",
				"&72. &cThe #ccffdd hex variables to <#ccffdd> mini syntax.",
				"&73. &cThe &x 14-digit hex format to mini syntax.",
				"&74. &cThe percentage %player% variables to {player} syntax.",
				"&75. &cSome legacy variables like {chat_color} to {player_chat_color} etc.",
				"",
				"&c&lWARNING: BACKUP YOUR CHATCONTROL/ FOLDER",
				"",
				"&6NOTICE: &cYou do not need to runthis command if you've",
				"&cmigrated from ChatControl 10 because the conversion has",
				"&calready taken place automatically during migration."
		};
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		final String param = args[0];

		if ("start".equals(param)) {
			Migrator.remapVariables();

			this.tellSuccess("Complete. See console for logs.");

		} else
			this.returnInvalidArgs(param);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		switch (this.args.length) {
			case 1:
				return this.completeLastWord("start");
		}

		return NO_COMPLETE;
	}
}
