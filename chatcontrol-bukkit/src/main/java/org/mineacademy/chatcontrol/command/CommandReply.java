package org.mineacademy.chatcontrol.command;

import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.Format;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.PrivateMessage;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.settings.Lang;

public final class CommandReply extends ChatControlCommand {

	public CommandReply() {
		super(Settings.PrivateMessages.REPLY_ALIASES);

		this.setMinArguments(1);
		this.setPermission(Permissions.Command.REPLY);
		this.setUsage(Lang.component("command-reply-dosage"));
		this.setDescription(Lang.component("command-reply-prescription"));
		this.setAutoHandleHelp(false);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		this.checkConsole();

		final String message = this.joinArgs(0);

		final WrappedSender wrapped = WrappedSender.fromAudience(this.audience);
		final String replyPlayer = wrapped.getSenderCache().getReplyPlayerName();

		this.checkNotNull(replyPlayer, Lang.component("command-reply-alone"));

		// Handle replying to console
		if (replyPlayer.equalsIgnoreCase("CONSOLE")) {
			final Map<String, Object> placeholders = CommonCore.newHashMap(
					"message", message,
					"receiver", Lang.legacy("part-console"),
					"player", Lang.legacy("part-console"),
					"sender", this.audience.getName());

			this.tell(Format.parse(Settings.PrivateMessages.FORMAT_SENDER).build(wrapped, placeholders));
			Common.tell(Bukkit.getConsoleSender(), Format.parse(Settings.PrivateMessages.FORMAT_RECEIVER).build(wrapped, placeholders));

			return;
		}

		final SyncedCache syncedCache = SyncedCache.fromPlayerName(replyPlayer);
		this.checkNotNull(syncedCache, Lang.component("player-not-online", "player", replyPlayer));

		PrivateMessage.send(wrapped, syncedCache, message);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		return this.completeLastWordPlayerNames();
	}
}