package org.mineacademy.chatcontrol.command.channel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.command.channel.ChannelCommands.ChannelSubCommand;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.ChannelMode;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.Lang;

public final class LeaveChannelSubCommand extends ChannelSubCommand {

	public LeaveChannelSubCommand() {
		super("leave/l");

		this.setPermission(Permissions.Channel.LEAVE.replace(".{channel}", ""));
		this.setUsage(Lang.component("channel-leave-usage"));
		this.setDescription(Lang.component("channel-leave-description"));
		this.setValidArguments(1, 2);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		final List<SimpleComponent> usages = new ArrayList<>();

		usages.add(Lang.component("channel-leave-usages-self"));

		if (this.hasPerm(Permissions.Channel.LEAVE_OTHERS))
			usages.add(Lang.component("channel-leave-usages-others"));

		return SimpleComponent.join(usages);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onChannelCommand() {
		final Channel channel = this.findChannel(this.args[0]);

		this.checkBoolean(this.isPlayer() || this.args.length == 2, Lang.component("command-console-missing-player-name"));

		this.pollCache(this.args.length == 2 ? this.args[1] : this.audience.getName(), cache -> {
			final boolean self = cache.getPlayerName().equals(this.audience.getName());
			final Set<Channel> oldChannels = cache.getChannels().keySet();
			final Player playerMaybe = Remain.getPlayerByUUID(cache.getUniqueId());

			this.checkPerm(Permissions.Channel.LEAVE.replace("{channel}", channel.getName()));

			// Check if joining oneself
			if (!self)
				this.checkPerm(Permissions.Channel.LEAVE_OTHERS);

			// Check if player connected
			if (Settings.Proxy.ENABLED) {
				this.checkBoolean(Settings.Database.isRemote(), Lang.component("command-remote-database-required"));
				this.checkBoolean(SyncedCache.isPlayerConnected(cache.getUniqueId()), Lang.component("player-not-online", "player", cache.getPlayerName()));

			} else
				this.checkNotNull(playerMaybe, Lang.component("player-not-online", "player", cache.getPlayerName()));

			if (oldChannels.isEmpty())
				this.returnTell(Lang.component("channel-leave-no-channel" + (self ? "" : "-other")));

			final List<Channel> channelsPlayerCanLeave = Channel.filterChannelsPlayerCanLeave(cache.getChannels().keySet(), playerMaybe);

			if (!cache.isInChannel(channel.getName())) {
				if (channelsPlayerCanLeave.isEmpty())
					this.returnTell(Lang.component("channel-leave-not-joined" + (self ? "" : "-other"), "name", cache.getPlayerName()));
				else
					this.returnTell(Lang.component("channel-leave-not-joined-suggest-alt" + (self ? "" : "-other"),
							"name", cache.getPlayerName(),
							"channels", CommonCore.join(channelsPlayerCanLeave, ", ", Channel::getName)));
			}

			final int readLimit = Settings.Channels.MAX_READ_CHANNELS.getForUUID(cache.getUniqueId());

			final boolean hasJoinReadPerm = playerMaybe == null || playerMaybe.hasPermission(Permissions.Channel.JOIN.replace("{channel}", channel.getName()).replace("{mode}", ChannelMode.READ.getKey()));

			// If leaving channel player is not reading and he can read,
			// turn into reading channel first before leaving completely
			if (Settings.Channels.JOIN_READ_OLD && hasJoinReadPerm && cache.getChannels(ChannelMode.READ).size() < readLimit && cache.getChannelMode(channel) != ChannelMode.READ) {
				cache.updateChannelMode(channel, ChannelMode.READ, true);

				this.tellSuccess(Lang.component("channel-leave-switch-to-reading" + (self ? "" : "-other"),
						"channel", channel.getName(),
						"target", cache.getPlayerName()));

				if (!self && playerMaybe != null)
					Messenger.success(playerMaybe, Lang.component("channel-leave-switch-to-reading", "channel", channel.getName()));

				return;

			} else {
				channel.leavePlayer(cache, true);

				// Mark as manually left
				cache.markLeftChannel(channel);
			}

			if (!Settings.Proxy.ENABLED || !self)
				this.tellSuccess(Lang.component("channel-leave-success" + (self ? "" : "-other"),
						"channel", channel.getName(), "target", cache.getPlayerName()));

			if (!self && playerMaybe != null && !Settings.Proxy.ENABLED)
				Messenger.success(playerMaybe, Lang.component("channel-leave-success", "channel", channel.getName()));

			// Notify proxy so that players connected on another server get their channel updated
			this.updateProxyData(cache, Lang.component("channel-leave-success", "channel", channel.getName()));
		});
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (this.args.length == 1)
			return this.completeLastWord((this.isPlayer() ? Channel.getChannelsWithLeavePermission(this.getPlayer()) : Channel.getChannels())
					.stream()
					.filter(channel -> !this.isPlayer() || channel.isInChannel(this.getPlayer()))
					.collect(Collectors.toList()), Channel::getName);

		if (this.args.length == 2 && this.hasPerm(Permissions.Channel.LEAVE_OTHERS))
			return this.completeLastWordPlayerNames();

		return NO_COMPLETE;
	}
}
