package org.mineacademy.chatcontrol.command;

import java.util.List;
import java.util.UUID;

import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.ChatControlProxyMessage;
import org.mineacademy.chatcontrol.model.Colors;
import org.mineacademy.chatcontrol.model.Format;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.ProxyUtil;
import org.mineacademy.fo.RandomUtil;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.settings.Lang;

public final class CommandMe extends ChatControlCommand {

	public CommandMe() {
		super(Settings.Me.COMMAND_ALIASES);

		this.setUsage(Lang.component("command-me-usage"));
		this.setDescription(Lang.component("command-me-description"));
		this.setMinArguments(1);
		this.setPermission(Permissions.Command.ME);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		return Lang.component("command-me-usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		String message = this.joinArgs(0);

		if (message.equals("is the best")) {
			this.tell("&k | &r &6You are not the best. Kangarko with his MineAcademy.org thing is the best. Our trained personal has been sent to help you. Expected arrival: " + (5 + RandomUtil.nextInt(10)) + " minutes.");

			return;
		}

		message = Colors.removeColorsNoPermission(this.getSender(), message, Colors.Type.ME);

		if (Settings.MAKE_CHAT_LINKS_CLICKABLE && this.getSender().hasPermission(Permissions.Chat.LINKS))
			message = ChatUtil.addMiniMessageUrlTags(message);

		final WrappedSender wrappedSender = WrappedSender.fromAudience(this.audience);
		final SimpleComponent messageComponent = Variables.builder(this.audience).replaceMessageVariables(SimpleComponent.fromMiniSection(message));

		final Format format = Format.parse(Settings.Me.FORMAT);

		final SimpleComponent formattedMessage = format.build(wrappedSender, CommonCore.newHashMap("message", messageComponent));
		final UUID senderId = this.isPlayer() ? this.getPlayer().getUniqueId() : CommonCore.ZERO_UUID;
		final boolean bypassReach = this.hasPerm(Permissions.Bypass.REACH);

		Players.showMe(senderId, bypassReach, formattedMessage);

		if (!this.isPlayer())
			this.tell(formattedMessage);

		if (Settings.Proxy.ENABLED)
			ProxyUtil.sendPluginMessage(ChatControlProxyMessage.ME, senderId, bypassReach, formattedMessage);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		return this.completeLastWordPlayerNames();
	}
}
