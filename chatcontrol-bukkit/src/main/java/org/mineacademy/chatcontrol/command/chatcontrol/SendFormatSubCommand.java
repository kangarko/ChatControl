package org.mineacademy.chatcontrol.command.chatcontrol;

import java.util.List;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.MainSubCommand;
import org.mineacademy.chatcontrol.model.ChatControlProxyMessage;
import org.mineacademy.chatcontrol.model.Colors;
import org.mineacademy.chatcontrol.model.Format;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.ProxyUtil;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.settings.Lang;

public final class SendFormatSubCommand extends MainSubCommand {

	public SendFormatSubCommand() {
		super("sendformat/sf");

		this.setUsage("[-reload] <format> <player> [message]");
		this.setDescription("Send the given format as a chat message, optionally reloading formats from disk.");
		this.setMinArguments(2);
		this.setPermission(Permissions.Command.SEND_FORMAT);
	}

	@Override
	protected String[] getMultilineUsageMessage() {
		return new String[] {
				"/{label} {sublabel} <format> <player> [message] - Send the given format to the player as a chat message.",
				"/{label} {sublabel} me-format kangarko Hello world! - Sents kangarko 'Hello world!' message using formats/me-format.yml to format it.",
				"/{label} {sublabel} -reload motd kangarko - Reloads formats and sents kangarko the formats/motd.yml format.",
		};
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		final boolean reload = this.args[0].equalsIgnoreCase("-reload");

		if (reload && this.args.length < 3)
			this.returnTell("You must specify the format and player.");

		final String formatName = this.args[reload ? 1 : 0];
		final String playerName = this.args[reload ? 2 : 1];
		String message = this.joinArgs(reload ? 3 : 2);

		final Player target = this.findPlayer(playerName);

		if (playerName == null) {
			this.checkBoolean(Settings.Database.isRemote(), Lang.component("command-remote-database-required"));
			this.checkBoolean(SyncedCache.isPlayerNameConnected(playerName), Lang.component("player-not-connected-proxy", "player", playerName));

			ProxyUtil.sendPluginMessage(ChatControlProxyMessage.FORWARD_FORMAT, playerName, reload, formatName, message);

			this.tellSuccess("Sending formatted message to proxy player " + playerName + " on server " + SyncedCache.fromPlayerName(playerName).getServerName() + ".");
			return;
		}

		if (reload)
			Format.loadFormats();

		final Format format = Format.findFormat(formatName);
		this.checkNotNull(format, "No such format: '" + formatName + "'. Available: " + Format.getFormatNames());

		final WrappedSender wrappedTarget = WrappedSender.fromSender(target);

		message = Colors.removeColorsNoPermission(target, message, Colors.Type.CHAT);

		final SimpleComponent component = format.build(wrappedTarget, CommonCore.newHashMap("message", message));

		wrappedTarget.getAudience().sendMessage(component);

		if (!target.getName().equals(this.getSender().getName()))
			this.tellSuccess(SimpleComponent.fromPlain(target.getName() + " received message: ").append(component));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		final int argsLength = this.args.length;

		if (argsLength == 1) {
			return this.completeLastWord(Format.getFormatNames(), "-reload");

		} else if (argsLength == 2 || argsLength == 3) {
			final boolean reload = this.args[0].equalsIgnoreCase("-reload");

			if (reload) {
				if (argsLength == 2)
					return this.completeLastWord(Format.getFormatNames());
				else
					return this.completeLastWordPlayerNames();

			} else if (argsLength == 2)
				return this.completeLastWordPlayerNames();
		}

		return NO_COMPLETE;
	}
}
