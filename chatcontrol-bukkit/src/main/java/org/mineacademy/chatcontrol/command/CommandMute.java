package org.mineacademy.chatcontrol.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.ChatControlProxyMessage;
import org.mineacademy.chatcontrol.model.MuteType;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.model.db.ServerSettings;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.ProxyUtil;
import org.mineacademy.fo.TimeUtil;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.Lang;

public final class CommandMute extends ChatControlCommand {

	public CommandMute() {
		super(Settings.Mute.COMMAND_ALIASES);

		this.setMinArguments(1);
		this.setPermission(Permissions.Command.MUTE);
		this.setUsage(Lang.component("command-mute-usage"));
		this.setDescription(Lang.component("command-mute-description"));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		return Lang.component("command-mute-usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		final MuteType type = this.findEnum(MuteType.class, this.args[0]);

		// Print status
		if (this.args.length == 1) {
			final List<SimpleComponent> messages = new ArrayList<>();

			if (type == MuteType.SERVER) {
				final boolean muted = ServerSettings.getInstance().isMuted();
				final String remainingTime = TimeUtil.formatTimeShort(ServerSettings.getInstance().getUnmuteTimeRemaining() / 1000);

				messages.add(Lang
						.component("command-mute-server-" + (muted ? "on" : "off"), "remaining_time", remainingTime)
						.onHover(Lang.component("command-mute-change-status-tooltip-" + (muted ? "unmute" : "mute"), "remaining_time", remainingTime))
						.onClickRunCmd("/" + this.getLabel() + " server " + (muted ? "off" : "3m")));

			} else if (type == MuteType.PROXY) {
				final boolean muted = ServerSettings.isProxyLoaded() ? ServerSettings.getProxy().isMuted() : false;
				final String remainingTime = ServerSettings.isProxyLoaded() ? TimeUtil.formatTimeShort(ServerSettings.getProxy().getUnmuteTimeRemaining() / 1000) : "0";

				messages.add(Lang
						.component("command-mute-proxy-" + (muted ? "on" : "off"), "remaining_time", remainingTime)
						.onHover(Lang.component("command-mute-change-status-tooltip-" + (muted ? "unmute" : "mute"), "remaining_time", remainingTime))
						.onClickRunCmd("/" + this.getLabel() + " proxy " + (muted ? "off" : "3m")));

			} else if (type == MuteType.CHANNEL)
				for (final Channel channel : Channel.getChannels()) {
					final boolean muted = channel.isMuted();
					final String remainingTime = TimeUtil.formatTimeShort(channel.getUnmuteTimeRemaining() / 1000);

					messages.add(Lang
							.component("command-mute-player-or-channel-" + (muted ? "on" : "off"), "player_or_channel", channel.getName(), "remaining_time", remainingTime)
							.onHover(Lang.component("command-mute-change-status-tooltip-" + (muted ? "unmute" : "mute"), "remaining_time", remainingTime))
							.onClickRunCmd("/" + this.getLabel() + " channel " + channel.getName() + " " + (muted ? "off" : "3m")));
				}
			else {
				this.pollCaches(caches -> {
					for (final PlayerCache otherCache : caches) {
						boolean muted = otherCache.isMuted();
						long remainingTimeLong = Common.getOrDefault(otherCache.getUnmuteTimeRemaining(), 0L);
						boolean externalMute = false;

						if (!muted) {
							final Tuple<Boolean, Long> muteTuple = HookManager.getUnmuteTime(otherCache.getUniqueId());

							if (muteTuple.getKey()) {
								muted = true;
								remainingTimeLong = muteTuple.getValue() == 0 ? 0 : (muteTuple.getValue() - System.currentTimeMillis());

								externalMute = true;
							}
						}

						final String remainingTime;

						if (muted) {
							if (remainingTimeLong != 0)
								remainingTime = TimeUtil.formatTimeShort((remainingTimeLong / 1000) + 1);
							else
								remainingTime = externalMute ? Lang.legacy("command-mute-external") : Lang.plain("part-unknown");
						} else
							remainingTime = Lang.plain("part-none");

						String command;

						if (externalMute) {
							if (HookManager.isEssentialsLoaded())
								command = "/essentials:mute " + otherCache.getPlayerName();
							else
								command = muted ? "/unmute " + otherCache.getPlayerName() : "/tempmute " + otherCache.getPlayerName() + " 3m";
						} else
							command = "/" + this.getLabel() + " player " + otherCache.getPlayerName() + " " + (muted ? "off" : "3m");

						messages.add(Lang
								.component("command-mute-player-or-channel-" + (muted ? "on" : "off"), "player_or_channel", otherCache.getPlayerName(), "remaining_time", remainingTime)
								.onHover(Lang.component("command-mute-change-status-tooltip-" + (muted ? "unmute" : "mute"), "remaining_time", remainingTime))
								.onClickRunCmd(command));
					}

					new ChatPaginator()
							.setFoundationHeader(Lang.legacy("command-mute-player-status"))
							.setPages(messages)
							.send(this.audience);
				});

				return;
			}

			new ChatPaginator()
					.setFoundationHeader(Lang.legacy("command-mute-status").replace("{type}", type.getLangKey()))
					.setPages(messages)
					.send(this.audience);
		}

		else if (this.args.length >= 2) {
			final boolean requiresNoExtraArgument = type == MuteType.PROXY || type == MuteType.SERVER;

			this.checkBoolean(this.args.length >= (requiresNoExtraArgument ? 2 : 3), Lang.component("command-mute-no-duration"));

			final String name = requiresNoExtraArgument ? "" : this.args[1];
			final String reason = CommonCore.joinRange(requiresNoExtraArgument ? 2 : 3, this.args);
			final String rawDuration = this.args[requiresNoExtraArgument ? 1 : 2];
			final boolean isOff = "off".equals(rawDuration);

			SimpleTime duration = null;

			if (!isOff)
				try {
					final long seconds = TimeUtil.toMilliseconds(rawDuration) / 1000L;

					duration = SimpleTime.fromSeconds((int) seconds);

				} catch (final NumberFormatException ex) {
					this.returnTell(Lang.component("command-mute-invalid-duration", "input", rawDuration));

				} catch (final IllegalArgumentException ex) {
					this.returnTell(ex.getMessage());
				}

			final SimpleComponent muteMessage = Lang.component("command-mute-mute-success-" + (reason.isEmpty() ? "plain" : "reason"),
					"type", type.getLangKey() + (requiresNoExtraArgument ? "" : " " + name),
					"duration", rawDuration,
					"reason", reason,
					"player", this.audience.getName());

			final SimpleComponent unmuteMessage = Lang.component("command-mute-unmute-success",
					"type", type.getLangKey() + (requiresNoExtraArgument ? "" : " " + name),
					"player", this.audience.getName());

			final Set<CommandSender> recipients = new HashSet<>();

			if (type == MuteType.CHANNEL) {
				final Channel channel = this.findChannel(name);

				this.checkBoolean(isOff || !channel.isMuted(), Lang.component("command-mute-already-muted", "type", ChatUtil.capitalize(type.getKey()), "name", name));
				this.checkBoolean(!isOff || channel.isMuted(), Lang.component("command-mute-not-muted", "type", ChatUtil.capitalize(type.getKey()), "name", name));

				channel.setMuted(duration);

				recipients.addAll(channel.getOnlinePlayers().keySet());

				if (Settings.Proxy.ENABLED)
					ProxyUtil.sendPluginMessage(ChatControlProxyMessage.MUTE, type.getKey(), channel.getName(), isOff ? "off" : duration.toString(), (isOff ? unmuteMessage : muteMessage));

			} else if (type == MuteType.PLAYER) {
				final SimpleTime finalDuration = duration;

				this.pollCache(name, playerCache -> {
					final SyncedCache syncedCache = SyncedCache.fromUniqueId(playerCache.getUniqueId());

					this.checkBoolean(isOff || !playerCache.isMuted(), Lang.component("command-mute-already-muted", "type", ChatUtil.capitalize(type.getKey()), "name", name));
					this.checkBoolean(!isOff || playerCache.isMuted(), Lang.component("command-mute-not-muted", "type", ChatUtil.capitalize(type.getKey()), "name", name));

					final Player playerInstance = playerCache.toPlayer();
					final String targetPlayerName = playerCache.getPlayerName();

					// Check not self
					if (playerInstance != null) {
						this.checkBoolean(isOff || !this.isPlayer() || !playerInstance.equals(this.getPlayer()), Lang.component("command-mute-cannot-mute-yourself"));

						recipients.add(playerInstance);
					}

					if (syncedCache != null)
						this.checkBoolean(!syncedCache.hasMuteBypass(), Lang.component("command-mute-cannot-mute-admin"));

					// Send to LiteBans
					if (isOff)
						HookManager.setLiteBansUnmute(targetPlayerName);
					else
						HookManager.setLiteBansMute(targetPlayerName, rawDuration, reason);

					playerCache.setMuted(finalDuration);

					if (Settings.Mute.BROADCAST)
						recipients.addAll(Remain.getOnlinePlayers());

					final SimpleComponent broadcastMessage = (isOff ? unmuteMessage : muteMessage);

					for (final CommandSender recipient : recipients)
						Messenger.announce(recipient, broadcastMessage);

					if (Settings.Mute.BROADCAST)
						ProxyUtil.sendPluginMessage(ChatControlProxyMessage.BROADCAST, Messenger.getAnnouncePrefix().appendPlain(" ").append(broadcastMessage));
					else
						ProxyUtil.sendPluginMessage(ChatControlProxyMessage.MESSAGE, playerCache.getUniqueId(), Messenger.getAnnouncePrefix().appendPlain(" ").append(broadcastMessage));

					if (!this.isPlayer())
						this.tellInfo(broadcastMessage);

					this.updateProxyData(playerCache);
				});

				return;

			} else if (type == MuteType.SERVER || type == MuteType.PROXY) {

				if (type == MuteType.PROXY && !Settings.Proxy.ENABLED)
					this.returnTell(Lang.component("command-mute-cannot-mute-proxy-not-enabled"));

				if (type == MuteType.PROXY && !ServerSettings.isProxyLoaded())
					this.returnTell("Proxy settings not loaded yet.");

				final ServerSettings serverCache = type == MuteType.PROXY ? ServerSettings.getProxy() : ServerSettings.getInstance();

				this.checkBoolean(isOff || !serverCache.isMuted(), Lang.component("command-mute-already-muted-server", "type", ChatUtil.capitalize(type.getKey())));
				this.checkBoolean(!isOff || serverCache.isMuted(), Lang.component("command-mute-not-muted-server", "type", ChatUtil.capitalize(type.getKey())));

				serverCache.setMuted(duration);
				recipients.addAll(Remain.getOnlinePlayers());

				if (Settings.Proxy.ENABLED)
					ProxyUtil.sendPluginMessage(ChatControlProxyMessage.MUTE, type.getKey(), "", isOff ? "off" : duration.toString(), (isOff ? unmuteMessage : muteMessage));

			} else
				throw new FoException("Unknown mute type: " + type);

			recipients.add(this.getSender());

			for (final CommandSender recipient : recipients)
				Messenger.announce(recipient, isOff ? unmuteMessage : muteMessage);
		}
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		final List<String> timeSuggestions = Arrays.asList("off", "1m", "15m");

		if (this.args.length == 1) {
			final List<String> keys = new ArrayList<>();

			for (final MuteType type : MuteType.values()) {
				if (type == MuteType.PROXY && !Settings.Proxy.ENABLED)
					continue;

				keys.add(type.getKey());
			}

			return this.completeLastWord(keys);

		}
		if (this.args.length == 2)
			if ("server".equals(this.args[0]) || "proxy".equals(this.args[0]))
				return this.completeLastWord(timeSuggestions);

			else if ("channel".equals(this.args[0]))
				return this.completeLastWord(Channel.getChannelNames());

			else
				return this.completeLastWordPlayerNames();

		if (this.args.length == 3 && !"server".equals(this.args[0]))
			return this.completeLastWord(timeSuggestions);

		return NO_COMPLETE;
	}
}
