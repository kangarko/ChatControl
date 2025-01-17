package org.mineacademy.chatcontrol.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.annotation.AutoRegister;
import org.mineacademy.fo.collection.ExpiringMap;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.model.CompChatColor;
import org.mineacademy.fo.model.DiscordListener;
import org.mineacademy.fo.model.DiscordSender;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.Lang;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.events.DiscordGuildMessagePreProcessEvent;
import github.scarsz.discordsrv.api.events.GameChatMessagePostProcessEvent;
import github.scarsz.discordsrv.api.events.GameChatMessagePreProcessEvent;
import github.scarsz.discordsrv.dependencies.emoji.EmojiParser;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Member;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Message;
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Role;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import github.scarsz.discordsrv.dependencies.jda.api.exceptions.ErrorResponseException;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.LangUtil;
import github.scarsz.discordsrv.util.MessageUtil;
import github.scarsz.discordsrv.util.WebhookUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Represents our Discord connection
 */
@AutoRegister(hideIncompatibilityWarnings = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Discord extends DiscordListener {

	/**
	 * The instance for this class
	 */
	@Getter
	private static final Discord instance = new Discord();

	/**
	 * Discord channel ID, message ID - message body
	 */
	private final Map<Long, Map<Long, String>> messagesCache = ExpiringMap.builder().expiration(1, TimeUnit.HOURS).build();

	@Override
	protected void onMessageReceived(final DiscordGuildMessagePreProcessEvent event) {
		if (!Settings.Channels.ENABLED || !Settings.Discord.ENABLED)
			return;

		// Take over channel handling
		event.setCancelled(true);

		final TextChannel discordChannel = event.getChannel();
		final Channel chatControlChannel = Channel.fromDiscordId(discordChannel.getIdLong());
		final Message discordMessage = event.getMessage();

		String message = discordMessage.getContentDisplay();

		if (chatControlChannel == null) {
			Debugger.debug("discord", "Received Discord message from channel '" + discordChannel.getName() + "' which isn't linked in ChatControl. It can be linked by adding 'Discord_Channel_Id: " + discordChannel.getId() + "' to Channel.List in our settings.yml. Message: " + message);

			return;
		}

		if (Settings.Discord.REMOVE_EMOJIS_V2)
			message = EmojiParser.removeAllEmojis(message);
		else
			message = EmojiParser.parseToAliases(message);

		if (message.trim().isEmpty()) {
			Debugger.debug("discord", "Received Discord message from channel '" + discordChannel.getName() + "' which is empty, not sending to ChatControl's channel.");

			return;
		}

		UUID linkedId = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(event.getAuthor().getId());
		String minecraftName = event.getAuthor().getName();

		OfflinePlayer offlinePlayer = null;

		if (linkedId != null)
			offlinePlayer = Remain.getOfflinePlayerByUniqueId(linkedId);

		if (offlinePlayer == null)
			offlinePlayer = Bukkit.getOfflinePlayer(minecraftName);

		if (offlinePlayer != null)
			minecraftName = offlinePlayer.getName();

		// Fallback, if we cant find a matching player name
		if (minecraftName == null)
			minecraftName = event.getAuthor().getName();

		if (linkedId == null && offlinePlayer != null)
			linkedId = offlinePlayer.getUniqueId();

		ValidCore.checkBoolean(minecraftName != null && !minecraftName.isEmpty(), "Unable to resolve Discord username in channel " + chatControlChannel.getName()
				+ " by " + event.getAuthor().getName() + " / offline player: " + offlinePlayer);

		final OfflinePlayer finalOfflinePlayer = offlinePlayer;
		final String finalMessage = message;
		final String finalName = minecraftName;
		final UUID finalUUID = linkedId;

		// Poll the player cache to prevent Discord>MC when muted
		PlayerCache.poll(minecraftName, cache -> {
			try {
				final DiscordSender sender = new DiscordSender(finalName, finalUUID, cache, finalOfflinePlayer, event.getAuthor(), discordChannel, discordMessage);
				final Channel.State state = Channel.State.from(WrappedSender.fromSender(sender), finalMessage);

				state.setPlaceholders(this.compilePlaceholders(finalMessage, finalName, finalUUID, event.getMember(), discordChannel, discordMessage));
				chatControlChannel.sendMessage(state);

				// Rewrite the message - applying antispam, rules etc.
				if (Settings.Discord.SEND_MESSAGES_AS_BOT) {
					final String replacedMessage = chatControlChannel.prepareFromDiscordMessage(sender, state.getMessage());

					this.editMessageById(discordChannel, discordMessage.getIdLong(), replacedMessage);
				}

				Debugger.debug("discord", "[Discord > Minecraft] From Discord '" + discordChannel.getName() + "' to Minecraft '" + chatControlChannel.getName() + "' message: " + finalMessage);

			} catch (final EventHandledException ex) {
				for (final SimpleComponent component : ex.getComponents())
					this.flashMessage(discordChannel, component);

				if (ex.isCancelled())
					this.deleteMessageById(discordChannel, discordMessage.getIdLong());
			}
		});
	}

	/**
	 * Sends a channel message to Discord
	 *
	 * @param discordChannelId
	 * @param message
	 */
	public void sendChannelMessageNoPlayer(final long discordChannelId, final String message) {
		this.sendChannelMessage(null, null, discordChannelId, message, null, null);
	}

	/**
	 *
	 * @param delayTicks
	 * @param discordChannelId
	 * @param message
	 */
	public void sendChannelMessageNoPlayerDelayed(final int delayTicks, final long discordChannelId, final String message) {
		this.sendChannelMessageDelayed(delayTicks, null, null, discordChannelId, message, message, null);
	}

	/**
	 *
	 * @param sender
	 * @param offlineSender
	 * @param discordChannelId
	 * @param message
	 * @param json
	 * @param chatControlChannel
	 */
	public void sendChannelMessage(@Nullable final CommandSender sender, @Nullable final OfflinePlayer offlineSender, final long discordChannelId, final String message, @Nullable final String json, @Nullable final Channel chatControlChannel) {
		this.sendChannelMessageDelayed(0, sender, offlineSender, discordChannelId, message, json, chatControlChannel);
	}

	/**
	 * Sends a channel message to Discord
	 * with an optional JSON argument that is cached and used to delete messages
	 *
	 * @param delayTicks
	 * @param sender
	 * @param offlineSender
	 * @param discordChannelId
	 * @param message
	 * @param json
	 * @param chatControlChannel
	 */
	public void sendChannelMessageDelayed(final int delayTicks, @Nullable final CommandSender sender, @Nullable final OfflinePlayer offlineSender, final long discordChannelId, String message, @Nullable final String json, @Nullable final Channel chatControlChannel) {
		if (!Settings.Discord.ENABLED || message.isEmpty())
			return;

		message = CompChatColor.stripColorCodes(message);

		final TextChannel channel = this.findChannel(discordChannelId);

		if (channel == null) {
			Debugger.debug("discord", "Failed to find Discord channel from ID " + discordChannelId + ". Message was not sent: " + message);

			return;
		}

		if (sender instanceof Player) {
			final GameChatMessagePreProcessEvent preEvent = new GameChatMessagePreProcessEvent(channel.getName(), message, (Player) sender);

			DiscordSRV.api.callEvent(preEvent);
			DiscordSRV.api.callEvent(new GameChatMessagePostProcessEvent(channel.getName(), message, (Player) sender, preEvent.isCancelled()));

			if (preEvent.isCancelled()) {
				Debugger.debug("discord", "[Minecraft > Discord] Other plugin canceled GameChatMessagePreProcessEvent. "
						+ "Not sending from MC '" + channel.getName() + "' to Discord '" + channel.getName() + "' message: " + message);

				return;
			}

			message = preEvent.getMessage();
		}

		final String finalMessage = message;

		// Send the message
		Platform.runTaskAsync(delayTicks, () -> {
			String replacedMessage = finalMessage;

			try {
				boolean replacedVariables = false;

				final String playerName = sender instanceof Player ? sender.getName() : offlineSender != null ? offlineSender.getName() : "";

				@Nullable
				final UUID playerUniqueId = sender instanceof Player ? ((Player) sender).getUniqueId() : offlineSender != null ? offlineSender.getUniqueId() : null;

				@Nullable
				final String linkedId = playerUniqueId != null ? DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(playerUniqueId) : null;

				if (!playerName.isEmpty())
					for (final Member member : channel.getMembers())
						if (linkedId != null && linkedId.equals(member.getId()) || member.getEffectiveName().equals(playerName)) {
							final Map<String, Object> placeholders = this.compilePlaceholders(replacedMessage, playerName, playerUniqueId, member, channel);

							replacedMessage = Variables.builder().placeholders(placeholders).replaceLegacy(replacedMessage);
							replacedVariables = true;
							break;
						}

				final Map<String, Object> placeholders = this.compilePlaceholders(replacedMessage, sender != null ? playerName : "", playerUniqueId, null, channel);

				if (!replacedVariables)
					replacedMessage = Variables.builder().placeholders(placeholders).replaceLegacy(replacedMessage);

				// Webhooks do not support callback
				if (Settings.Discord.WEBHOOK) {
					String webhookNameFormat = chatControlChannel != null ? chatControlChannel.getDiscordWebhookNameFormat() : null;

					if (webhookNameFormat != null) {
						webhookNameFormat = Variables.builder().audience(sender).placeholders(placeholders).replaceComponent(SimpleComponent.fromPlain(webhookNameFormat)).toPlain();

					} else
						webhookNameFormat = playerName.isEmpty() ? Lang.legacy("part-console") : playerName;

					if (sender == null && offlineSender != null)
						WebhookUtil.deliverMessage(channel, offlineSender, webhookNameFormat, replacedMessage, (MessageEmbed) null);

					else if (sender instanceof OfflinePlayer)
						WebhookUtil.deliverMessage(channel, (OfflinePlayer) sender, webhookNameFormat, replacedMessage, (MessageEmbed) null);

					else {
						final Message discordMessage = channel.sendMessage(replacedMessage).complete();

						// Mark it
						if (json != null)
							this.markReceivedMessage(discordChannelId, discordMessage.getIdLong(), json);
					}

					Debugger.debug("discord", "[Minecraft > Discord Webhook] From MC '" + channel.getName() + "' to Discord '" + channel.getName() + "' message: " + replacedMessage);

				} else {
					final Message discordMessage = channel.sendMessage(replacedMessage).complete();
					Debugger.debug("discord", "[Minecraft > Discord] From MC '" + channel.getName() + "' to Discord '" + channel.getName() + "' message: " + replacedMessage);

					// Mark it
					if (json != null)
						this.markReceivedMessage(discordChannelId, discordMessage.getIdLong(), json);
				}

			} catch (final ErrorResponseException ex) {
				Debugger.debug("discord", "Unable to send message to Discord channel " + channel.getName() + ", message: " + replacedMessage);

			} catch (final Throwable t) {
				t.printStackTrace();
			}
		});
	}

	/**
	 * Marks a message as received with the JSON representation of interactive in Minecraft chat
	 *
	 * @param discordChannelId
	 * @param sender
	 * @param json
	 */
	public void markReceivedMessage(final long discordChannelId, final DiscordSender sender, final String json) {
		this.markReceivedMessage(discordChannelId, sender.getMessage().getIdLong(), json);
	}

	/*
	 * Mark the message as received
	 */
	private void markReceivedMessage(final long discordChannelId, final long messageId, final String json) {
		final Map<Long, String> pastChannelMessages = this.messagesCache.getOrDefault(discordChannelId, new LinkedHashMap<>());

		pastChannelMessages.put(messageId, json);
		this.messagesCache.put(discordChannelId, pastChannelMessages);
	}

	/**
	 * Remove a channel message by its given unique ID
	 *
	 * @param uniqueId
	 */
	public void removeChannelMessage(final UUID uniqueId) {
		if (Settings.Discord.ENABLED)
			for (final Entry<Long, Map<Long, String>> entry : this.messagesCache.entrySet()) {
				final TextChannel channel = this.findChannel(entry.getKey());

				if (channel == null)
					continue;

				for (final Map.Entry<Long, String> message : entry.getValue().entrySet())
					if (message.getValue().contains(uniqueId.toString())) {
						long id = message.getKey();

						// Try to get the latest ID if we edited the message
						id = this.getEditedMessages().getOrDefault(id, id);

						this.deleteMessageById(channel, id);
					}
			}
	}

	/*
	 * Compiles a list of valid discord-only placeholders for the given data
	 */
	private Map<String, Object> compilePlaceholders(final String message, final String playerName, final UUID uniqueId, final Member member, final TextChannel channel) {
		return this.compilePlaceholders(message, playerName, uniqueId, member, channel, null);
	}

	/*
	 * Compiles a list of valid discord-only placeholders for the given data
	 */
	private Map<String, Object> compilePlaceholders(final String message, final String playerName, final UUID uniqueId, final Member member, final TextChannel channel, final Message guildMessage) {
		final List<Role> selectedRoles = member != null ? DiscordSRV.getPlugin().getSelectedRoles(member) : new ArrayList<>();
		final Function<String, String> escape = MessageUtil.isLegacy(message) ? str -> str : str -> str.replaceAll("([<>])", "\\\\$1");
		final Map<String, Object> placeholders = SyncedCache.getPlaceholders(playerName, uniqueId, PlaceholderPrefix.PLAYER);

		placeholders.putAll(CommonCore.newHashMap(
				"discord_channel", channel.getName(),
				"discord_name", member != null ? escape.apply(MessageUtil.strip(member.getEffectiveName())) : playerName,
				"discord_username", member != null ? escape.apply(MessageUtil.strip(member.getUser().getName())) : playerName,
				"discord_top_role", member != null ? escape.apply(DiscordUtil.getRoleName(!selectedRoles.isEmpty() ? selectedRoles.get(0) : null)) : "",
				"discord_top_role_initial", !selectedRoles.isEmpty() ? escape.apply(DiscordUtil.getRoleName(selectedRoles.get(0)).substring(0, 1)) : "",
				"discord_top_role_alias", member != null ? escape.apply(this.getTopRoleAlias(!selectedRoles.isEmpty() ? selectedRoles.get(0) : null)) : "",
				"discord_all_roles", member != null ? escape.apply(DiscordUtil.getFormattedRoles(selectedRoles)) : "",
				"discord_reply", guildMessage != null && guildMessage.getReferencedMessage() != null ? this.replaceReplyPlaceholders(LangUtil.Message.CHAT_TO_MINECRAFT_REPLY.toString(), guildMessage.getReferencedMessage()) : ""));

		return placeholders;
	}

	/*
	 * Replaces reply variables
	 */
	private String replaceReplyPlaceholders(final String format, final Message repliedMessage) {
		final Function<String, String> escape = MessageUtil.isLegacy(format) ? str -> str : str -> str.replaceAll("([<>])", "\\\\$1");
		final String repliedUserName = repliedMessage.getMember() != null ? repliedMessage.getMember().getEffectiveName() : repliedMessage.getAuthor().getName();

		return format
				.replace("{name}", escape.apply(MessageUtil.strip(repliedUserName)))
				.replace("{username}", escape.apply(MessageUtil.strip(repliedMessage.getAuthor().getName())))
				.replace("{message}", escape.apply(MessageUtil.strip(repliedMessage.getContentDisplay())));
	}

	/*
	 * Return the top alias for role
	 */
	private String getTopRoleAlias(final Role role) {
		if (role == null)
			return "";

		final String name = role.getName();
		final Map<String, String> roleAliases = DiscordSRV.getPlugin().getRoleAliases();

		return roleAliases.getOrDefault(role.getId(), roleAliases.getOrDefault(name.toLowerCase(), name));
	}
}
