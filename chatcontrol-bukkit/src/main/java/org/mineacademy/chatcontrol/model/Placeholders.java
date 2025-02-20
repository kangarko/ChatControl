package org.mineacademy.chatcontrol.model;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.model.db.ServerSettings;
import org.mineacademy.chatcontrol.operator.Tag;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.SerializeUtilCore;
import org.mineacademy.fo.SerializeUtilCore.Language;
import org.mineacademy.fo.TimeUtil;
import org.mineacademy.fo.annotation.AutoRegister;
import org.mineacademy.fo.model.DynmapSender;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.SimpleExpansion;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.platform.FoundationPlayer;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.settings.Lang;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Dynamically insert data variables for PlaceholderAPI
 */
@AutoRegister
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Placeholders extends SimpleExpansion {

	/**
	 * The singleton of this class
	 */
	@Getter
	private static final SimpleExpansion instance = new Placeholders();

	@Override
	protected String onReplace(final FoundationPlayer audience, final String identifier) {
		final CommandSender sender = audience != null ? audience.getSender() : null;

		//
		// Variables that do not require any sender
		//
		if ("label_channel".equals(identifier))
			return Settings.Channels.COMMAND_ALIASES.get(0);

		else if ("label_ignore".equals(identifier))
			return Settings.Ignore.COMMAND_ALIASES.get(0);

		else if ("label_mail".equals(identifier))
			return Settings.Mail.COMMAND_ALIASES.get(0);

		else if ("label_me".equals(identifier))
			return Settings.Me.COMMAND_ALIASES.get(0);

		else if ("label_mute".equals(identifier))
			return Settings.Mute.COMMAND_ALIASES.get(0);

		else if ("label_motd".equals(identifier))
			return Settings.Motd.COMMAND_ALIASES.get(0);

		else if ("label_tag".equals(identifier))
			return Settings.Tag.COMMAND_ALIASES.get(0);

		else if ("label_reply".equals(identifier))
			return Settings.PrivateMessages.REPLY_ALIASES.get(0);

		else if ("label_spy".equals(identifier))
			return Settings.Spy.COMMAND_ALIASES.get(0);

		else if ("label_tell".equals(identifier))
			return Settings.PrivateMessages.TELL_ALIASES.get(0);

		else if ("label_toggle".equals(identifier))
			return Settings.Toggle.COMMAND_ALIASES.get(0);

		else if ("sender_is_dynmap".equals(identifier))
			return sender instanceof DynmapSender ? "true" : "false";

		else if ("server_unmute_remaining_seconds".equals(identifier))
			return String.valueOf(ServerSettings.getInstance().getUnmuteTimeRemaining() / 1000);

		else if ("server_unmute_remaining".equals(identifier))
			return String.valueOf(TimeUtil.formatTimeDays(ServerSettings.getInstance().getUnmuteTimeRemaining() / 1000));

		else if ("server_unmute_remaining_short".equals(identifier))
			return String.valueOf(TimeUtil.formatTimeShort(ServerSettings.getInstance().getUnmuteTimeRemaining() / 1000));

		else if ("network_unmute_remaining_seconds".equals(identifier))
			return Settings.Proxy.ENABLED && ServerSettings.isProxyLoaded() ? String.valueOf(ServerSettings.getProxy().getUnmuteTimeRemaining() / 1000) : "";

		else if ("network_unmute_remaining".equals(identifier))
			return Settings.Proxy.ENABLED && ServerSettings.isProxyLoaded() ? String.valueOf(TimeUtil.formatTimeDays(ServerSettings.getProxy().getUnmuteTimeRemaining() / 1000)) : "";

		else if ("network_unmute_remaining_short".equals(identifier))
			return Settings.Proxy.ENABLED && ServerSettings.isProxyLoaded() ? String.valueOf(TimeUtil.formatTimeShort(ServerSettings.getProxy().getUnmuteTimeRemaining() / 1000)) : "";

		final Player player = sender instanceof Player ? (Player) sender : null;
		final SenderCache senderCache = player != null ? SenderCache.from(player) : null;
		final PlayerCache playerCache = senderCache != null && senderCache.isDatabaseLoaded() ? PlayerCache.fromCached(player) : null;
		final SyncedCache syncedCache = audience != null ? SyncedCache.fromUniqueId(audience.getUniqueId()) : null;

		if (player != null && !player.isOnline())
			return null;

		if ("player_server".equals(identifier)) {
			if (syncedCache == null)
				return Platform.getCustomServerName();

			return syncedCache.getServerName();
		}

		else if ("player_unmute_remaining_seconds".equals(identifier) || "player_unmute_remaining".equals(identifier) || "player_unmute_remaining_short".equals(identifier)) {
			if (playerCache == null)
				return "";

			long remaining = Common.getOrDefault(playerCache.getUnmuteTimeRemaining(), 0L);

			if (remaining == 0) {
				final Tuple<Boolean, Long> muteTuple = HookManager.getUnmuteTime(playerCache.getUniqueId());

				if (muteTuple.getKey()) {
					if (muteTuple.getValue() == 0)
						return Lang.plain("part-unknown");

					remaining = System.currentTimeMillis() - muteTuple.getValue();
				}
			}

			if (remaining > 0)
				remaining += 1000;

			if ("player_unmute_remaining_seconds".equals(identifier))
				return remaining == 0 ? "0" : String.valueOf(remaining / 1000);

			else if ("player_unmute_remaining".equals(identifier))
				return remaining == 0 ? Lang.plain("part-none") : TimeUtil.formatTimeDays(remaining / 1000);

			else
				return remaining == 0 ? Lang.plain("part-none") : TimeUtil.formatTimeShort(remaining / 1000);
		}

		else if ("player_channel_unmute_remaining_seconds".equals(identifier))
			return playerCache != null && playerCache.getWriteChannel() != null ? String.valueOf(playerCache.getWriteChannel().getUnmuteTimeRemaining() / 1000) : "";

		else if ("player_channel_unmute_remaining".equals(identifier))
			return playerCache != null && playerCache.getWriteChannel() != null ? String.valueOf(TimeUtil.formatTimeDays(playerCache.getWriteChannel().getUnmuteTimeRemaining() / 1000)) : "";

		else if ("player_channel_unmute_remaining_short".equals(identifier))
			return playerCache != null && playerCache.getWriteChannel() != null ? String.valueOf(TimeUtil.formatTimeShort(playerCache.getWriteChannel().getUnmuteTimeRemaining() / 1000)) : "";

		else if ("player_newcomer".equals(identifier))
			return player != null && Newcomer.isNewcomer(player) ? "true" : "false";

		else if ("player_reply_target".equals(identifier))
			return senderCache == null ? "" : CommonCore.getOrDefault(senderCache.getReplyPlayerName(), Lang.plain("part-none").toLowerCase());

		else if ("player_last_active".equals(identifier) || "player_last_active_elapsed".equals(identifier) || "player_last_active_elapsed_seconds".equals(identifier)) {
			final long lastActive = player == null ? 0 : player.getLastPlayed();

			if ("player_last_active".equals(identifier))
				return lastActive == 0 ? Lang.plain("part-none").toLowerCase() : TimeUtil.getFormattedDate(lastActive);

			else if ("player_last_active_elapsed".equals(identifier))
				return lastActive == 0 ? Lang.plain("part-none").toLowerCase() : TimeUtil.formatTimeShort((System.currentTimeMillis() - lastActive) / 1000);

			else if ("player_last_active_elapsed_seconds".equals(identifier))
				return lastActive == 0 ? Lang.plain("part-none").toLowerCase() : String.valueOf((System.currentTimeMillis() - lastActive) / 1000);
		}

		//
		// Variables that accept any command sender
		//
		if (playerCache != null) {
			if (identifier.startsWith("player_is_spying_") || identifier.startsWith("player_in_channel_")) {
				if (!Settings.Channels.ENABLED)
					return "false";

				final String channelName = this.join(3);

				if (identifier.startsWith("player_is_spying_")) {
					if ((identifier.endsWith("command") || identifier.endsWith("commands")) && Settings.Spy.APPLY_ON.contains(Spy.Type.COMMAND))
						return String.valueOf(playerCache.isSpying(Spy.Type.COMMAND) || playerCache.getSpyingSectors().contains(Spy.Type.COMMAND));

					else if ((identifier.endsWith("private_message") || identifier.endsWith("private_messages") || identifier.endsWith("pms")) && Settings.Spy.APPLY_ON.contains(Spy.Type.PRIVATE_MESSAGE))
						return String.valueOf(playerCache.isSpying(Spy.Type.PRIVATE_MESSAGE) || playerCache.getSpyingSectors().contains(Spy.Type.PRIVATE_MESSAGE));

					else if (identifier.endsWith("mail") && Settings.Spy.APPLY_ON.contains(Spy.Type.MAIL))
						return String.valueOf(playerCache.isSpying(Spy.Type.MAIL) || playerCache.getSpyingSectors().contains(Spy.Type.MAIL));

					else if ((identifier.endsWith("sign") || identifier.endsWith("signs")) && Settings.Spy.APPLY_ON.contains(Spy.Type.SIGN))
						return String.valueOf(playerCache.isSpying(Spy.Type.SIGN) || playerCache.getSpyingSectors().contains(Spy.Type.SIGN));

					else if ((identifier.endsWith("book") || identifier.endsWith("books")) && Settings.Spy.APPLY_ON.contains(Spy.Type.BOOK))
						return String.valueOf(playerCache.isSpying(Spy.Type.BOOK) || playerCache.getSpyingSectors().contains(Spy.Type.BOOK));

					else if (identifier.endsWith("anvil") && Settings.Spy.APPLY_ON.contains(Spy.Type.ANVIL))
						return String.valueOf(playerCache.isSpying(Spy.Type.ANVIL) || playerCache.getSpyingSectors().contains(Spy.Type.ANVIL));

					else if (channelName.equals("chat") && Settings.Spy.APPLY_ON.contains(Spy.Type.CHAT))
						return String.valueOf(playerCache.getSpyingSectors().contains(Spy.Type.CHAT));

					else if (Settings.Spy.APPLY_ON.contains(Spy.Type.CHAT) || !playerCache.isInChannel(channelName))
						return String.valueOf(playerCache.isSpyingChannel(channelName) && !playerCache.isInChannel(channelName));

					else
						return "false";

				} else
					return String.valueOf(playerCache.isInChannel(channelName));

			} else if (identifier.startsWith("player_channel_mode_")) {
				if (!Settings.Channels.ENABLED)
					return Lang.plain("part-none");

				final String channelName = this.join(3);
				final Channel channel = Channel.findChannel(channelName);

				if (playerCache != null && channel != null)
					return playerCache.isInChannel(channelName) ? playerCache.getChannelMode(channel).getKey() : Lang.plain("part-none");

				return Lang.plain("part-none");

			} else if (("channel".equals(identifier) || "player_channel".equals(identifier))) {
				if (!Settings.Channels.ENABLED)
					return Lang.plain("part-none");

				final Channel writeChannel = playerCache.getWriteChannel();

				return writeChannel != null && !Settings.Channels.IGNORE_WORLDS.contains(((Player) sender).getWorld().getName()) ? writeChannel.getName() : Lang.plain("part-none").toLowerCase();
			}

			else if ("player_nick".equals(identifier)) {
				final String nick = Players.getNickOrNullColored(player);

				return nick != null ? Settings.Tag.NICK_PREFIX + nick : player.getName();
			}

			else if ("player_nick_section".equals(identifier)) {
				final String nick = Players.getNickOrNullColored(player);

				if (nick != null)
					return SimpleComponent.fromMiniAmpersand(Settings.Tag.NICK_PREFIX + nick).toLegacySection();

				return player.getName();
			}

			else if ("player_nick_mini".equals(identifier)) {
				final String nick = Players.getNickOrNullColored(player);

				if (nick != null)
					return SimpleComponent.fromMiniAmpersand(Settings.Tag.NICK_PREFIX + nick).toMini();

				return player.getName();
			}

			else if ("player_prefix".equals(identifier) && Settings.Tag.APPLY_ON.contains(Tag.Type.PREFIX))
				return CommonCore.getOrEmpty(playerCache.getTag(Tag.Type.PREFIX));

			else if ("player_prefix_section".equals(identifier) && Settings.Tag.APPLY_ON.contains(Tag.Type.PREFIX)) {
				final String prefix = CommonCore.getOrEmpty(playerCache.getTag(Tag.Type.PREFIX));

				return SimpleComponent.fromMiniAmpersand(prefix).toLegacySection();

			} else if ("player_suffix".equals(identifier) && Settings.Tag.APPLY_ON.contains(Tag.Type.SUFFIX))
				return CommonCore.getOrEmpty(playerCache.getTag(Tag.Type.SUFFIX));

			else if ("player_suffix_section".equals(identifier) && Settings.Tag.APPLY_ON.contains(Tag.Type.PREFIX)) {
				final String suffix = CommonCore.getOrEmpty(playerCache.getTag(Tag.Type.SUFFIX));

				return SimpleComponent.fromMiniAmpersand(suffix).toLegacySection();
			}

			else if ("player_chat_color".equals(identifier) || "chat_color".equals(identifier))
				return playerCache.getChatColor() != null ? playerCache.getChatColor().toString() : "";

			else if ("player_chat_color_name".equals(identifier) || "chat_color_name".equals(identifier))
				return playerCache.getChatColor() != null ? playerCache.getChatColor().toChatString() : Lang.plain("part-none").toLowerCase();

			else if ("player_chat_color_letter".equals(identifier) || "chat_color_letter".equals(identifier))
				return playerCache.getChatColor() != null ? playerCache.getChatColor().isHex() ? playerCache.getChatColor().getName() : "&" + playerCache.getChatColor().getCode() : "";

			else if ("player_chat_decoration".equals(identifier) || "chat_decoration".equals(identifier))
				return playerCache.getChatDecoration() != null ? playerCache.getChatDecoration().toString() : "";

			else if ("player_chat_decoration_name".equals(identifier) || "chat_decoration_name".equals(identifier))
				return playerCache.getChatDecoration() != null ? playerCache.getChatDecoration().toChatString() : Lang.plain("part-none").toLowerCase();

			else if ("player_chat_decoration_letter".equals(identifier) || "chat_decoration_letter".equals(identifier))
				return playerCache.getChatDecoration() != null ? "&" + playerCache.getChatDecoration().getCode() : "";

			else if ("player_channel_range".equals(identifier))
				return playerCache.getWriteChannel() != null && playerCache.getWriteChannel().getRange() != null ? playerCache.getWriteChannel().getRange() : Lang.plain("part-none").toLowerCase();

			else if (this.args.length > 2 && "player_data".equalsIgnoreCase(this.args[0] + "_" + this.args[1])) {
				final String key = this.join(2);
				final Object value = playerCache.getRuleData(key);

				return value != null ? SerializeUtilCore.serialize(Language.YAML, value).toString() : "";
			}
		}

		// Replace player-only variables on discord/console with empty
		else if (identifier.startsWith("player_is_spying_") ||
				identifier.startsWith("player_in_channel_") ||
				identifier.startsWith("player_channel_mode_") ||
				"player_channel".equals(identifier) ||
				"player_reply_target".equals(identifier) ||
				"player_last_active".equals(identifier) ||
				"player_last_active_elapsed".equals(identifier) ||
				"player_last_active_elapsed_seconds".equals(identifier) ||
				"player_chat_color_name".equals(identifier) ||
				"player_chat_color".equals(identifier) ||
				"player_chat_decoration_name".equals(identifier) ||
				"player_chat_decoration".equals(identifier) ||
				identifier.startsWith("player_data_"))

			return "";

		for (final ToggleType toggleType : Settings.Toggle.APPLY_ON) {
			final String toggleName = toggleType.getKey();

			if (identifier.equals("is_ignoring_" + toggleName) || identifier.equals("player_is_ignoring_" + toggleName) || identifier.equals("player_is_ignoring_" + toggleName + "s"))
				return playerCache == null ? "false" : String.valueOf(playerCache.hasToggledPartOff(toggleType));
		}

		for (final PlayerMessageType messageType : Settings.Messages.APPLY_ON) {
			final String toggleName = messageType.getKey();

			if (identifier.equals("is_ignoring_" + toggleName) || identifier.equals("player_is_ignoring_" + toggleName) || identifier.equals("player_is_ignoring_" + toggleName + "s"))
				return playerCache == null ? "false" : String.valueOf(playerCache.isIgnoringMessages(messageType));
		}

		// TODO merge with SyncedCache variables and remove duplicated code

		if (identifier.startsWith("player_is_ignoring_"))
			return "Unknown '" + identifier + "' part. Available: " + Common.join(ToggleType.values());

		return null;
	}

	@Override
	public int getPriority() {
		return 10;
	}
}
