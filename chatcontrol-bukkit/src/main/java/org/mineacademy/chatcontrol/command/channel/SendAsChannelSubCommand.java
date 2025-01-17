package org.mineacademy.chatcontrol.command.channel;

import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.command.channel.ChannelCommands.ChannelSubCommand;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.settings.Lang;

public final class SendAsChannelSubCommand extends ChannelSubCommand {

	public SendAsChannelSubCommand() {
		super("sendas/sa");

		this.setPermission(Permissions.Channel.SEND_AS.replace(".{channel}", ""));
		this.setUsage(Lang.component("channel-send-as-usage"));
		this.setDescription(Lang.component("channel-send-as-description"));
		this.setMinArguments(3);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onChannelCommand() {
		final Player player = this.findPlayer(this.args[0]);
		final Channel channel = this.findChannel(this.args[1]);
		final String message = this.joinArgs(2);

		this.checkPerm(Permissions.Channel.SEND_AS.replace("{channel}", channel.getName()));

		try {
			channel.sendMessage(player, message);

		} catch (final EventHandledException ex) {
			final StringBuilder builder = new StringBuilder();

			for (final SimpleComponent component : ex.getComponents())
				builder.append(component.toLegacySection(null));

			this.tellError(Lang.component("channel-send-as-error", "player", player.getName(), "error", builder.toString()));
		}
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (this.args.length == 1)

			// Remove proxy players, no support yet
			return this.completeLastWordPlayerNames()
					.stream()
					.filter(name -> Bukkit.getPlayerExact(name) != null)
					.collect(Collectors.toList());

		if (this.args.length == 2)
			return this.completeLastWord(Channel.getChannelNames());

		return NO_COMPLETE;
	}
}
