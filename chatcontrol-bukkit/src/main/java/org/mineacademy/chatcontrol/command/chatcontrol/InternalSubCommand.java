package org.mineacademy.chatcontrol.command.chatcontrol;

import java.util.List;
import java.util.UUID;

import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.MainSubCommand;
import org.mineacademy.chatcontrol.model.ChatControlProxyMessage;
import org.mineacademy.chatcontrol.model.Discord;
import org.mineacademy.chatcontrol.model.LogType;
import org.mineacademy.chatcontrol.model.Packets;
import org.mineacademy.chatcontrol.model.db.Database;
import org.mineacademy.chatcontrol.model.db.Log;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.ProxyUtil;
import org.mineacademy.fo.SerializeUtilCore.Language;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleBook;
import org.mineacademy.fo.settings.Lang;

public final class InternalSubCommand extends MainSubCommand {

	public InternalSubCommand() {
		super("internal");

		this.setUsage("<type> <id>");
		this.setDescription("Internal command (keep calm).");
		this.setMinArguments(2);

		// No permission, since this command can't be executed without knowing
		// the unique key, we give access to everyone (it is conditioned that they
		// have access to a command giving them the key passthrough)
		this.setPermission(null);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleSubCommand#showInHelp()
	 */
	@Override
	protected boolean showInHelp() {
		return false;
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		final String param = this.args[0];
		final UUID uuid = UUID.fromString(this.args[1]);

		if ("log-book".equals(param)) {
			this.checkConsole();

			final String metadataName = "FoLogBook_" + uuid;
			this.checkBoolean(this.getPlayer().hasMetadata(metadataName), Lang.component("command-internal-invalid-meta"));

			final SimpleBook mail = (SimpleBook) this.getPlayer().getMetadata(metadataName).get(0).value();
			mail.openPlain(this.audience);
		}

		else if ("remove".equals(param)) {
			if (HookManager.isProtocolLibLoaded())
				Packets.getInstance().removeMessage(Packets.RemoveMode.SPECIFIC_MESSAGE, uuid);
			else
				// Every 3 hours broadcast this
				CommonCore.logTimed(3600 * 3, "Warning: Attempted to remove a chat message but ProtocolLib plugin is missing, please install it. Ignoring...");

			if (HookManager.isDiscordSRVLoaded() && Settings.Discord.ENABLED)
				Discord.getInstance().removeChannelMessage(uuid);

			if (Settings.Proxy.ENABLED)
				ProxyUtil.sendPluginMessage(ChatControlProxyMessage.REMOVE_MESSAGE, Packets.RemoveMode.SPECIFIC_MESSAGE.getKey(), uuid);
		}

		else if ("book".equals(param)) {
			this.checkConsole();

			this.syncCallback(() -> Database.getInstance().getLogs(LogType.BOOK), logs -> {
				boolean found = false;

				for (final Log log : logs) {
					final String copy = log.getContent();

					// Avoiding parsing malformed JSON
					if (copy.contains("{") && copy.contains("}")) {
						final SimpleBook book = SimpleBook.deserialize(SerializedMap.fromObject(Language.JSON, copy));

						if (book.getUniqueId().equals(uuid)) {
							book.openPlain(this.audience);

							found = true;
							break;
						}
					}
				}

				if (!found)
					this.tellError(Lang.component("command-internal-invalid-book", "uuid", uuid));
			});
		}

		else
			this.returnInvalidArgs(param);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		return NO_COMPLETE;
	}
}
