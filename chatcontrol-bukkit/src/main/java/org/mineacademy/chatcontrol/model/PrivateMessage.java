package org.mineacademy.chatcontrol.model;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.api.PrePrivateMessageEvent;
import org.mineacademy.chatcontrol.model.db.Log;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Proxy;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.ProxyUtil;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.model.CompToastStyle;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.Lang;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;

/**
 * Class dealing with private messages
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class PrivateMessage {

	/**
	 * Fire a private message between two players
	 *
	 * @param sender
	 * @param receiverCache
	 * @param message
	 *
	 * @throws EventHandledException if message was not delivered
	 */
	public static void send(@NonNull final WrappedSender sender, @NonNull final SyncedCache receiverCache, @NonNull String message) throws EventHandledException {

		@Nullable
		final Player receiver = Bukkit.getPlayerExact(receiverCache.getPlayerName());
		boolean playSound = true;
		final PrePrivateMessageEvent event = new PrePrivateMessageEvent(sender, receiverCache, receiver, message, playSound);

		// API
		if (!Platform.callEvent(event))
			throw new EventHandledException(true);

		playSound = event.isSound();
		message = event.getMessage();

		final Map<String, Object> placeholders = new HashMap<>();

		message = Colors.removeColorsNoPermission(sender.getSender(), message, Colors.Type.PRIVATE_MESSAGE);

		if (sender.hasPermission(Permissions.Chat.LINKS))
			message = ChatUtil.addMiniMessageUrlTags(message);

		final Variables variables = Variables.builder(sender.getAudience());
		final SimpleComponent messageComponent = variables.replaceMessageVariables(SimpleComponent.fromMiniSection(message));

		// Resolve MESSAGE type placeholders such as "I hold an [item]"
		message = messageComponent.toLegacySection(null);

		placeholders.put("message", messageComponent);
		placeholders.putAll(receiverCache.getPlaceholders(PlaceholderPrefix.RECEIVER));
		placeholders.putAll(SyncedCache.getPlaceholders(sender.getName(), sender.getUniqueId(), PlaceholderPrefix.SENDER));

		if (!sender.hasPermission(Permissions.Bypass.VANISH) && receiverCache.isVanished())
			throw new EventHandledException(true, Lang.component("command-tell-receiver-offline", placeholders));

		if (!sender.hasPermission(Permissions.Bypass.REACH) && sender.isPlayer()) {
			if (Settings.Ignore.ENABLED && Settings.Ignore.STOP_PRIVATE_MESSAGES) {
				if (Settings.Ignore.BIDIRECTIONAL && sender.getPlayerCache().isIgnoringPlayer(receiverCache.getUniqueId()))
					throw new EventHandledException(true, Lang.component("command-ignore-cannot-pm-ignored", placeholders));

				if (receiverCache.isIgnoringPlayer(sender.getPlayerCache().getUniqueId()))
					throw new EventHandledException(true, Lang.component("command-ignore-cannot-pm", placeholders));
			}

			if (Settings.Toggle.APPLY_ON.contains(ToggleType.PRIVATE_MESSAGE) && !sender.getName().equals(receiverCache.getPlayerName()) && receiverCache.hasToggledPartOff(ToggleType.PRIVATE_MESSAGE))
				throw new EventHandledException(true, Lang.component("command-toggle-cannot-pm", placeholders));
		}

		if (SimpleComponent.fromMiniAmpersand(message).toPlain().isEmpty())
			throw new EventHandledException(true, Lang.component("command-tell-empty-message"));

		if (sender.isPlayer())
			sender.getSenderCache().setReplyPlayerName(receiverCache.getPlayerName());

		if (receiverCache.isAfk())
			Common.tellLater(1, sender.getSender(), variables.replaceComponent(Lang.component("command-tell-afk-warning", "player", receiverCache.getPlayerName())));

		final SimpleComponent receiverMessage = Format.parse(Settings.PrivateMessages.FORMAT_RECEIVER).build(sender, placeholders);
		final SimpleComponent senderMessage = Format.parse(Settings.PrivateMessages.FORMAT_SENDER).build(sender, placeholders);
		final SimpleComponent consoleMessage = Format.parse(Settings.PrivateMessages.FORMAT_CONSOLE).build(sender, placeholders);

		// Fire
		sender.sendMessage(senderMessage);

		if (receiver != null)
			Common.tell(receiver, receiverMessage);

		else if (Proxy.ENABLED && Settings.PrivateMessages.PROXY)
			ProxyUtil.sendPluginMessage(ChatControlProxyMessage.MESSAGE, receiverCache.getUniqueId(), receiverMessage);

		// Play sound, if null then send via proxy
		if (playSound && Settings.PrivateMessages.SOUND.isEnabled()) {
			if (receiver != null)
				Settings.PrivateMessages.SOUND.play(receiver);

			else if (Proxy.ENABLED && Settings.PrivateMessages.PROXY)
				ProxyUtil.sendPluginMessage(ChatControlProxyMessage.SOUND, receiverCache.getUniqueId(), Settings.PrivateMessages.SOUND.toString());
		}

		CommonCore.log(consoleMessage.toLegacySection(null));

		// Toasts
		if (Settings.PrivateMessages.TOASTS) {
			final String toastLine = Variables.builder()
					.placeholders(placeholders)
					.placeholderArray("sender_name", CommonCore.limit(sender.getName(), 21), "message", CommonCore.limit(message, 41))
					.replaceLegacy(Settings.PrivateMessages.FORMAT_TOAST);

			final String[] toast = toastLine.split("\\|");

			for (int i = 0; i < toast.length; i++)
				toast[i] = CommonCore.limit(toast[i], 41);

			if (receiver != null)
				Remain.sendToast(receiver, String.join("\n", toast), CompMaterial.WRITABLE_BOOK, CompToastStyle.GOAL);

			else if (Proxy.ENABLED && Settings.PrivateMessages.PROXY)
				ProxyUtil.sendPluginMessage(ChatControlProxyMessage.TOAST, receiverCache.getUniqueId(), ToggleType.PRIVATE_MESSAGE.getKey(), String.join("\n", toast), CompMaterial.WRITABLE_BOOK.name(), CompToastStyle.GOAL.name());
		}

		if (Settings.PrivateMessages.SENDER_OVERRIDES_RECEIVER_REPLY) {
			final Player receiverPlayer = Remain.getPlayerByUUID(receiverCache.getUniqueId());

			if (receiverPlayer == null) {

				// Do not throw error if no proxy and null.. player might have just disconnected for example
				if (Proxy.ENABLED && Settings.PrivateMessages.PROXY)
					ProxyUtil.sendPluginMessage(ChatControlProxyMessage.REPLY_UPDATE, receiverCache.getUniqueId(), sender.getName(), sender.getUniqueId());

			} else
				SenderCache.from(receiverPlayer).setReplyPlayerName(sender.getName());
		}

		// Update last conv time
		sender.getSenderCache().setLastAutoModeChat(System.currentTimeMillis());

		// Log and ... spy!
		Log.logPrivateMessage(sender.getSender(), receiverCache.getPlayerName(), message);

		if (sender.getSenderCache() != null)
			Spy.broadcastPrivateMessage(sender, receiverCache, placeholders, SimpleComponent.fromMiniSection(message));
	}
}