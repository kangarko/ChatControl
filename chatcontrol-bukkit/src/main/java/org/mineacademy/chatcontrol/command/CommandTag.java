package org.mineacademy.chatcontrol.command;

import java.util.ArrayList;
import java.util.List;

import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.ChatControlProxyMessage;
import org.mineacademy.chatcontrol.model.Colors;
import org.mineacademy.chatcontrol.model.Migrator;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.operator.Tag;
import org.mineacademy.chatcontrol.operator.Tag.TagCheck;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.ProxyUtil;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.settings.Lang;

public final class CommandTag extends ChatControlCommand {

	public CommandTag() {
		super(Settings.Tag.COMMAND_ALIASES);

		this.setMinArguments(1);
		this.setPermission(Permissions.Command.TAG_TYPE.substring(0, Permissions.Command.TAG_TYPE.length() - 1));
		this.setUsage(Lang.component("command-tag-usage"));
		this.setDescription(Lang.component("command-tag-description"));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		return Lang.component("command-tag-usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		this.checkConsole();

		final WrappedSender wrapped = WrappedSender.fromPlayer(this.getPlayer());
		final PlayerCache cache = wrapped.getPlayerCache();

		if ("list".equals(this.args[0])) {
			if (this.args.length > 1)
				this.returnInvalidArgs(this.joinArgs(2));

			this.tellInfo(Lang.component("command-tag-your-tags"));

			boolean shownAtLeastOne = false;

			for (final Tag.Type tag : Settings.Tag.APPLY_ON)
				if (this.hasPerm(Permissions.Command.TAG_TYPE + tag.getKey())) {
					this.tellNoPrefix(" &7- " + ChatUtil.capitalize(tag.getKey()) + ": &f" + CommonCore.getOrDefault(cache.getTag(tag), Lang.plain("part-none") + "."));

					shownAtLeastOne = true;
				}

			if (!shownAtLeastOne)
				this.tellNoPrefix("&7 - " + Lang.plain("part-none"));

			return;
		}

		else if ("off".equals(this.args[0])) {
			this.checkBoolean(!cache.getTags().isEmpty(), Lang.component("command-tag-off-no-tags"));

			for (final Tag.Type tag : Settings.Tag.APPLY_ON)
				cache.setTag(tag, null);

			Players.setTablistName(wrapped);

			final SimpleComponent message = Lang.component("command-tag-off-all");

			if (Settings.Proxy.ENABLED)
				ProxyUtil.sendPluginMessage(ChatControlProxyMessage.TAG_UPDATE, Platform.getCustomServerName(), cache.getUniqueId(), cache.toMap(), message);

			this.tellSuccess(message);
			return;
		}

		final Tag.Type type = this.findTag(this.args[0]);
		this.checkPerm(Permissions.Command.TAG_TYPE + type.getKey());

		if (this.args.length == 1) {
			this.tellInfo(Lang.component("command-tag-status-self",
					"type", type.getKey(),
					"tag", CommonCore.getOrDefault(cache.getTag(type), Lang.plain("part-none"))));

			return;
		}

		String tag = this.joinArgs(1);

		if (tag.contains("&#"))
			tag = Migrator.convertAmpersandToMiniHex(tag);

		if (tag.contains("#"))
			tag = Migrator.convertHexToMiniHex(tag);

		// Colorize according to senders permissions
		tag = Colors.removeColorsNoPermission(this.getSender(), tag, type.getColorType());

		// Apply rules!
		final TagCheck check = Tag.filter(type, wrapped, tag);
		tag = check.getMessage();

		this.checkBoolean(!check.isCancelledSilently(), Lang.component("command-tag-not-allowed"));
		this.setTag(type, cache, tag);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (this.args.length == 1) {
			final List<String> tags = new ArrayList<>();

			for (final Tag.Type type : Settings.Tag.APPLY_ON)
				if (this.hasPerm(Permissions.Command.TAG_TYPE + type.getKey()))
					tags.add(type.getKey());

			return this.completeLastWord(tags, "list");
		}

		if (this.args.length == 2)
			return this.completeLastWord("off");

		return NO_COMPLETE;
	}
}