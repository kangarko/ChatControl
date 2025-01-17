package org.mineacademy.chatcontrol.command.channel;

import java.util.List;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.command.channel.ChannelCommands.ChannelSubCommand;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.ChannelMode;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.Lang;

public final class SetChannelSubCommand extends ChannelSubCommand {

	public SetChannelSubCommand() {
		super("set");

		this.setPermission(Permissions.Channel.SET);
		this.setUsage(Lang.component("channel-set-usage"));
		this.setDescription(Lang.component("channel-set-description"));
		this.setMinArguments(3);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		return Lang.component("channel-set-usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onChannelCommand() {
		this.pollCache(this.args[0], cache -> {
			final Player playerMaybe = Remain.getPlayerByUUID(cache.getUniqueId());

			// Check if player connected
			if (Settings.Proxy.ENABLED) {
				this.checkBoolean(Settings.Database.isRemote(), Lang.component("command-remote-database-required"));
				this.checkBoolean(SyncedCache.isPlayerConnected(cache.getUniqueId()), Lang.component("player-not-connected-proxy", "player", cache.getPlayerName()));

			} else
				this.checkNotNull(playerMaybe, Lang.component("player-not-online", "player", cache.getPlayerName()));

			for (final String channelAndMode : this.joinArgs(1).split("\\|")) {
				final String[] split = channelAndMode.split(" ");
				this.checkBoolean(split.length == 2, "Invalid syntax! Usage: /{label} {sublabel} <player> <channel> <mode>|<anotherChannel> <anotherMode>");

				final Channel channel = this.findChannel(split[0]);
				final ChannelMode mode = "none".equals(split[1]) ? null : this.findMode(split[1]);

				// Cannot have more than 1 write channel
				if (mode == ChannelMode.WRITE && cache.getWriteChannel() != null)
					cache.updateChannelMode(cache.getWriteChannel(), null, false);

				// Remove old channel forcefully
				cache.updateChannelMode(channel, null, false);

				// Then rejoin
				if (mode != null)
					channel.joinPlayer(cache, mode, false);

				cache.upsert();

				final SimpleComponent message = mode != null
						? Lang.component("channel-set-success", "channel", channel.getName(), "player", cache.getPlayerName(), "mode", mode.getKey())
						: Lang.component("channel-set-success-remove", "channel", channel.getName(), "player", cache.getPlayerName());

				this.tellSuccess(message);
			}

			// Notify proxy so that players connected on another server get their channel updated
			this.updateProxyData(cache);
		});
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (this.args.length == 1)
			return this.completeLastWordPlayerNames();

		if (this.args.length == 2)
			return this.completeLastWord(Channel.getChannels(), Channel::getName);

		if (this.args.length == 3)
			return this.completeLastWord(ChannelMode.values(), "none");

		return NO_COMPLETE;
	}
}
