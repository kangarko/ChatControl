package org.mineacademy.chatcontrol.command;

import java.util.List;

import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.PrivateMessage;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.settings.Settings;
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

		final SyncedCache syncedSenderCache = SyncedCache.fromUniqueId(wrapped.getUniqueId());

		final String replyPlayer = syncedSenderCache.getReplyPlayerName();

		this.checkNotNull(replyPlayer, Lang.component("command-reply-alone"));

		final SyncedCache syncedCache = replyPlayer.equalsIgnoreCase("CONSOLE") ? SyncedCache.fromConsole() : SyncedCache.fromPlayerName(replyPlayer);
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