
package org.mineacademy.chatcontrol.command.chatcontrol;

import java.util.ArrayList;
import java.util.List;

import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.MainSubCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.PlayerMessageType;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.operator.PlayerMessage;
import org.mineacademy.chatcontrol.operator.PlayerMessages;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.settings.Lang;

/**
 * Represents command related to rules
 */
public final class MessageSubCommand extends MainSubCommand {

	public MessageSubCommand() {
		super("message/m");

		this.setMinArguments(1);
		this.setPermission(Permissions.Command.MESSAGE);
		this.setUsage(Lang.component("command-message-usage"));
		this.setDescription(Lang.component("command-message-description"));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		return Lang.component("command-message-usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		this.checkUsage(this.args.length <= 3);

		final WrappedSender wrapped = WrappedSender.fromAudience(this.audience);
		final String param = this.args[0];

		if ("reload".equals(param)) {
			PlayerMessages.getInstance().load();

			this.tellSuccess(Lang.component("command-message-reloaded"));
			return;
		}

		this.checkArgs(2, Lang.component("command-message-no-type", "available", CommonCore.join(Settings.Messages.APPLY_ON)));

		final PlayerMessageType type = this.findMessageType(this.args[1]);

		if ("list".equals(param)) {
			this.checkArgs(2, Lang.component("command-message-no-type", "available", Settings.Messages.APPLY_ON));

			final List<PlayerMessage> messages = new ArrayList<>();

			// add a copy
			messages.addAll(PlayerMessages.getInstance().getMessages(type));
			this.checkBoolean(!messages.isEmpty(), Lang.component("command-message-no-messages", "type", type.getToggleLangKey()));

			if (this.args.length == 3) {
				final String groupName = this.args[2];
				boolean found = false;

				messages.removeIf(message -> message.getUniqueName() == null || message.getUniqueName().isEmpty());

				for (final PlayerMessage message : messages)
					if (groupName.equalsIgnoreCase(message.getUniqueName())) {

						for (final String line : message.getMessages())
							message.sendMessage(wrapped, line);

						found = true;
						break;
					}

				if (!found)
					this.tellError("No such " + type + " message group {2}. Available: " + CommonCore.join(messages, ", ", PlayerMessage::getGroup));

			} else {
				final List<SimpleComponent> lines = new ArrayList<>();

				for (final PlayerMessage message : messages)
					lines.add(Lang
							.component("command-message-group", "group", message.getGroup())
							.onHoverLegacy(message.toDisplayableString().split("\n")));

				new ChatPaginator(15)
						.setFoundationHeader(Lang.legacy("command-message-list-header", "amount", messages.size(), "type", type.getToggleLangKey()))
						.setPages(lines)
						.send(this.audience);
			}

			return;
		}

		this.checkArgs(3, Lang.component("command-message-no-group"));
		final PlayerMessage message = this.findMessage(type, this.args[2]);

		if ("info".equals(param)) {
			this.tellNoPrefix(Lang.component("command-message-info",
					"type", ChatUtil.capitalize(type.getToggleLangKey()),
					"group", message.getGroup()));

			this.getSender().sendMessage(message.toDisplayableString());

			this.tellNoPrefix(Lang.component("command-message-info-footer"));
		}

		else if ("toggle".equals(param)) {
			final boolean toggle = !message.isDisabled();

			PlayerMessages.getInstance().toggleMessage(message, toggle);
			this.tellSuccess(Lang.component("command-message-toggle-" + (toggle ? "off" : "on"),
					"type", ChatUtil.capitalize(type.getToggleLangKey()),
					"group", message.getGroup()));
		}

		else if ("run".equals(param)) {
			this.tellSuccess("Force broadcasting " + message.getType() + " message '" + message.getGroup() + "':");

			PlayerMessages.run(wrapped, message);
		}

		else
			this.returnInvalidArgs(param);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (this.args.length == 1)
			return this.completeLastWord("info", "run", "toggle", "list", "reload");

		if (this.args.length == 2 && !"reload".equals(this.args[0]))
			return this.completeLastWord(Settings.Messages.APPLY_ON);

		if (this.args.length == 3 && !"reload".equals(this.args[0]) && !"list".equals(this.args[1])) {

			PlayerMessageType type;

			try {
				type = PlayerMessageType.fromKey(this.args[1]);
			} catch (final IllegalArgumentException ex) {
				return NO_COMPLETE;
			}

			return this.completeLastWord(PlayerMessages.getInstance().getEnabledMessageNames(type));
		}

		return NO_COMPLETE;
	}
}
