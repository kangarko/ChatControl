package org.mineacademy.chatcontrol.command;

import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.Colors;
import org.mineacademy.chatcontrol.model.Format;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.model.ToggleType;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.settings.Lang;

public final class CommandSay extends ChatControlCommand {

	public CommandSay() {
		super(Settings.Say.COMMAND_ALIASES);

		this.setUsage(Lang.component("command-say-usage"));
		this.setDescription(Lang.component("command-say-description"));
		this.setMinArguments(1);
		this.setPermission(Permissions.Command.SAY);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		return Lang.component("command-say-usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		String message = Colors.removeColorsNoPermission(this.getSender(), this.joinArgs(0), Colors.Type.SAY);

		if (Settings.MAKE_CHAT_LINKS_CLICKABLE && this.getSender().hasPermission(Permissions.Chat.LINKS))
			message = ChatUtil.addMiniMessageUrlTags(message);

		final WrappedSender wrappedSender = WrappedSender.fromAudience(this.audience);
		final SimpleComponent messageComponent = Variables.builder(this.audience).replaceMessageVariables(SimpleComponent.fromMiniSection(message));

		final SimpleComponent formattedMessage = Format.parse(Settings.Say.FORMAT).build(wrappedSender, CommonCore.newHashMap("message", messageComponent));

		final UUID senderId = this.isPlayer() ? this.getPlayer().getUniqueId() : CommonCore.ZERO_UUID;
		final boolean bypassReach = this.hasPerm(Permissions.Bypass.REACH);

		for (final Player online : Players.getOnlinePlayersWithLoadedDb()) {
			final PlayerCache cache = PlayerCache.fromCached(online);

			if (Settings.Toggle.APPLY_ON.contains(ToggleType.BROADCAST) && cache.hasToggledPartOff(ToggleType.BROADCAST) && !senderId.equals(online.getUniqueId()))
				continue;

			if (!bypassReach && Settings.Ignore.HIDE_SAY && cache.isIgnoringPlayer(senderId))
				continue;

			Common.tell(online, formattedMessage);
		}
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		return this.completeLastWordPlayerNames();
	}
}
