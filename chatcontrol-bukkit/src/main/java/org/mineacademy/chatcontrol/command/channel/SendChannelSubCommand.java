package org.mineacademy.chatcontrol.command.channel;

import java.util.List;

import org.mineacademy.chatcontrol.command.channel.ChannelCommands.ChannelSubCommand;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.fo.settings.Lang;

public final class SendChannelSubCommand extends ChannelSubCommand {

	public SendChannelSubCommand() {
		super("send/s");

		this.setPermission(Permissions.Channel.SEND.replace(".{channel}", ""));
		this.setUsage(Lang.component("channel-send-usage"));
		this.setDescription(Lang.component("channel-send-description"));
		this.setMinArguments(2);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onChannelCommand() {
		final Channel channel = this.findChannel(this.args[0]);
		final String message = this.joinArgs(1);

		this.checkPerm(Permissions.Channel.SEND.replace("{channel}", channel.getName()));

		channel.sendMessage(this.getSender(), message);

	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (this.args.length == 1)
			return this.completeLastWord(Channel.getChannelNames());

		return NO_COMPLETE;
	}
}
