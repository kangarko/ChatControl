package org.mineacademy.chatcontrol.command.channel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.command.channel.ChannelCommands.ChannelSubCommand;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.ChannelMode;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.settings.Lang;

public final class ListChannelSubCommand extends ChannelSubCommand {

	public ListChannelSubCommand() {
		super("list/ls");

		this.setPermission(Permissions.Channel.LIST);
		this.setMaxArguments(1);
		this.setDescription(Lang.component("channel-list-description"));
		this.setUsage(Lang.component("channel-list-usage"));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		return Lang.component("channel-list-usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onChannelCommand() {
		final String selectedChannelName = this.args.length == 1 ? this.args[0] : null;
		final Channel selectedChannel = selectedChannelName != null ? Channel.findChannel(selectedChannelName) : null;
		final boolean canSeeOptions = this.hasPerm(Permissions.Channel.LIST_OPTIONS);

		if (selectedChannelName != null)
			this.checkNotNull(selectedChannel, Lang.component("channel-no-channel", "channel", selectedChannelName, "available", Channel.getChannelNames()));

		this.pollCaches(caches -> {
			final List<SimpleComponent> messages = new ArrayList<>();
			final Map<String, Map<String /*player*/, ChannelMode>> allChannelPlayers = new LinkedHashMap<>();

			for (final PlayerCache cache : caches) {
				final SyncedCache synced = SyncedCache.fromUniqueId(cache.getUniqueId());

				if (synced == null)
					continue;

				for (final Map.Entry<String, ChannelMode> entry : synced.getChannels().entrySet()) {
					final String channelName = entry.getKey();
					final ChannelMode mode = entry.getValue();

					final Map<String /*player*/, ChannelMode> playersInChannel = allChannelPlayers.getOrDefault(channelName, new LinkedHashMap<>());

					playersInChannel.put(cache.getPlayerName(), mode);
					allChannelPlayers.put(channelName, playersInChannel);
				}
			}

			for (final Channel channel : Channel.getChannels()) {

				// Filter channel if parameter is given
				if (selectedChannel != null && !selectedChannel.equals(channel))
					continue;

				// Allow one-click un/mute
				final boolean muted = channel.isMuted();
				final boolean joined = this.isPlayer() ? channel.isInChannel(this.getPlayer()) : false;

				SimpleComponent channelNameComponent = SimpleComponent.fromMiniNative(" <white>" + channel.getName());

				if (canSeeOptions && this.isPlayer()) {

					if (Settings.Mute.ENABLED)
						channelNameComponent = channelNameComponent
								.append(Lang.component("channel-list-" + (muted ? "unmute" : "mute")))
								.onHover(Lang.component("channel-list-" + (muted ? "unmute-tooltip" : "mute-tooltip")))
								.onClickRunCmd("/" + Settings.Mute.COMMAND_ALIASES.get(0) + " channel " + channel.getName() + " " + (muted ? "off" : "15m"));

					channelNameComponent = channelNameComponent
							.append(Lang.component("channel-list-" + (joined ? "leave" : "join")))
							.onHover(Lang.component("channel-list-" + (joined ? "leave-tooltip" : "join-tooltip")))
							.onClickRunCmd("/" + Settings.Channels.COMMAND_ALIASES.get(0) + " " + (joined ? "leave" : "join") + " " + channel.getName());
				}

				messages.add(channelNameComponent);

				final Map<String /*player*/, ChannelMode> channelPlayers = allChannelPlayers.getOrDefault(channel.getName(), new LinkedHashMap<>());

				if (channelPlayers.isEmpty())
					messages.add(Lang.component("channel-list-no-players"));

				else
					for (final Entry<String, ChannelMode> entry : channelPlayers.entrySet()) {
						final String playerName = entry.getKey();
						final ChannelMode mode = entry.getValue();

						SimpleComponent playerComponent = SimpleComponent.fromMiniNative(" <gray>- ");

						if (canSeeOptions && this.isPlayer())
							playerComponent = playerComponent
									.append(Lang.component("channel-list-remove"))
									.onHover(Lang.component("channel-list-remove-tooltip", "player", playerName))
									.onClickRunCmd("/" + Settings.Channels.COMMAND_ALIASES.get(0) + " leave " + channel.getName() + " " + playerName);

						playerComponent = playerComponent.append(Lang.component("channel-list-line",
								"mode_color", mode.getColor().toString(),
								"mode", mode.getKey(),
								"mode_colorized", mode.getColor().toString() + mode.getKey(),
								"player", playerName));

						messages.add(playerComponent);
					}

				messages.add(SimpleComponent.fromPlain(" "));
			}

			new ChatPaginator()
					.setFoundationHeader(Lang.legacy("channel-list-header" + (Settings.Proxy.ENABLED ? "-network" : "")))
					.setPages(messages)
					.send(this.audience);

		});
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (this.args.length == 1)
			return this.completeLastWord(Channel.getChannelNames());

		return NO_COMPLETE;
	}
}
