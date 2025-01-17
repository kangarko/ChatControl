package org.mineacademy.chatcontrol.command.channel;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.command.channel.ChannelCommands.ChannelSubCommand;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.ChannelMode;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Channels;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.Lang;

public final class JoinChannelSubCommand extends ChannelSubCommand {

	public JoinChannelSubCommand() {
		super("join/j");

		this.setPermission(Permissions.Channel.JOIN.replace(".{channel}.{mode}", ""));
		this.setUsage(Lang.component("channel-join-usage"));
		this.setDescription(Lang.component("channel-join-description"));
		this.setMinArguments(1);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		final List<SimpleComponent> usages = new ArrayList<>();

		usages.add(Lang.component("channel-join-usages-self"));

		if (this.hasPerm(Permissions.Channel.JOIN_OTHERS))
			usages.add(Lang.component("channel-join-usages-others"));

		usages.add(Lang.component("channel-join-usages-2"));

		return SimpleComponent.join(usages);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onChannelCommand() {
		this.checkBoolean(this.args.length < 4, this.getUsage());

		final Channel channel = this.findChannel(this.args[0]);
		final ChannelMode pickedMode = this.args.length >= 2 ? this.findMode(this.args[1]) : ChannelMode.WRITE;

		this.checkBoolean(this.isPlayer() || this.args.length == 3, Lang.component("command-console-missing-player-name"));

		this.pollCache(this.args.length == 3 ? this.args[2] : this.getSender().getName(), cache -> {
			final boolean self = cache.getPlayerName().equals(this.getSender().getName());
			final ChannelMode previousMode = cache.getChannelMode(channel);
			final Channel previousWriteChannel = cache.getWriteChannel();
			final Player targetPlayerOrNull = Remain.getPlayerByUUID(cache.getUniqueId());

			ChannelMode mode = pickedMode;

			// If no mode is specified and player can only read the channel, default to reading
			if (this.args.length < 2 && !Channel.canWriteInto(this.getSender(), channel) && Channel.canRead(this.getSender(), channel))
				mode = ChannelMode.READ;

			this.checkPerm(Permissions.Channel.JOIN.replace("{channel}", channel.getName()).replace("{mode}", mode.getKey()));

			// Check if joining oneself
			if (!self)
				this.checkPerm(Permissions.Channel.JOIN_OTHERS);

			// Check if player connected
			if (Settings.Proxy.ENABLED) {
				this.checkBoolean(Settings.Database.isRemote(), Lang.component("command-remote-database-required"));
				this.checkBoolean(SyncedCache.isPlayerConnected(cache.getUniqueId()), Lang.component("player-not-connected-proxy", "player", cache.getPlayerName()));

			} else
				this.checkNotNull(targetPlayerOrNull, Lang.component("player-not-online", "player", cache.getPlayerName()));

			this.checkBoolean(previousMode != mode, Lang.component("channel-join-already-joined" + (self ? "-self" : ""), "mode", previousMode == null ? "" : previousMode.getKey()));

			// Start compiling message for player
			SimpleComponent component = previousMode != null
					? Lang.component("channel-join-switch-success", "channel", channel.getName(), "old_mode", previousMode == null ? "" : previousMode.getKey(), "new_mode", mode.getKey())
					: Lang.component("channel-join-success", "channel", channel.getName(), "mode", mode.getKey());

			final int readLimit = Settings.Channels.MAX_READ_CHANNELS.getForUUID(cache.getUniqueId());
			this.checkBoolean(mode != ChannelMode.READ || readLimit > 0, Lang.component("channel-join-read-disabled"));

			boolean save = false;

			// Check limits
			if (mode == ChannelMode.READ) {
				int readingChannelsAmount = 0;

				final List<String> channelsPlayerLeft = new ArrayList<>();

				for (final Channel otherReadChannel : cache.getChannels(ChannelMode.READ))
					if (++readingChannelsAmount >= readLimit) {
						cache.updateChannelMode(otherReadChannel, null, false);

						channelsPlayerLeft.add(otherReadChannel.getName());
						save = true;
					}

				if (!channelsPlayerLeft.isEmpty())
					component = component.append(Lang.component("channel-join-leave-reading-" + (self ? "you" : "other"),
							"channels", CommonCore.joinAnd(channelsPlayerLeft),
							"target", cache.getPlayerName()));
			}

			// If player has another mode for that channel, remove it first
			if (previousMode != null) {
				cache.updateChannelMode(channel, null, false);

				save = true;
			}

			// Remove the player from old write channel early to avoid errors
			if (previousWriteChannel != null && mode == ChannelMode.WRITE) {
				cache.updateChannelMode(previousWriteChannel, null, false);

				save = true;
			}

			channel.joinPlayer(cache, mode, false);
			save = true;

			// If player was writing in another write channel, leave him or change mode if set
			if (previousWriteChannel != null && mode == ChannelMode.WRITE)
				if (Channels.JOIN_READ_OLD && cache.getChannels(ChannelMode.READ).size() + 1 <= readLimit) {
					cache.updateChannelMode(previousWriteChannel, ChannelMode.READ, false);
					save = true;

				} else if (previousMode == null)
					component = component.append(Lang.component("channel-join-leave-reading-" + (self ? "you" : "other"),
							"channels", previousWriteChannel.getName(),
							"target", cache.getPlayerName()));

			if (!Settings.Proxy.ENABLED || !self)
				this.tellSuccess(component);

			if (!self && targetPlayerOrNull != null && this.isPlayer())
				Messenger.success(targetPlayerOrNull, component);

			if (save)
				cache.upsert();

			// Notify proxy so that players connected on another server get their channel updated
			this.updateProxyData(cache, component);
		});
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (this.args.length == 1)
			return this.completeLastWord(this.isPlayer() ? Channel.getChannelsWithJoinPermission(this.getPlayer()) : Channel.getChannels(), Channel::getName);

		if (this.args.length == 2) {
			final List<String> modesPlayerHasPermissionFor = new ArrayList<>();

			for (final ChannelMode mode : ChannelMode.values())
				if (this.hasPerm(Permissions.Channel.JOIN.replace("{channel}", this.args[0]).replace("{mode}", mode.getKey())))
					modesPlayerHasPermissionFor.add(mode.getKey());

			return this.completeLastWord(modesPlayerHasPermissionFor);
		}

		if (this.args.length == 3 && this.hasPerm(Permissions.Channel.JOIN_OTHERS))
			return this.completeLastWordPlayerNames();

		return NO_COMPLETE;
	}
}
