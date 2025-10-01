package org.mineacademy.chatcontrol.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.api.PlayerMentionEvent;
import org.mineacademy.chatcontrol.api.PlayerPreMentionEvent;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.ProxyUtil;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.platform.FoundationPlayer;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.settings.Lang;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Handles @ mentioning and sound notify.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SoundNotify {

	/**
	 * Add colorized tag and sound notify to the message if enabled
	 *
	 * @param wrapped
	 * @param message
	 * @return
	 */
	public static String addTagAndSound(final WrappedSender wrapped, String message) {
		if (Settings.SoundNotify.ENABLED && wrapped.hasPermission(Permissions.SOUND_NOTIFY)) {
			final Set<UUID> playersWhoHeardSound = new HashSet<>();

			final Channel senderChannel = Settings.Channels.ENABLED && wrapped.isPlayer() ? wrapped.getPlayerCache().getWriteChannel() : null;
			final long cooldown = Settings.SoundNotify.COOLDOWN.getTimeSeconds();
			final long delaySinceLast = (System.currentTimeMillis() - wrapped.getSenderCache().getLastSoundNotify()) / 1000;
			final boolean canUse = wrapped.hasPermission(Permissions.Bypass.REACH) || cooldown == 0 || wrapped.getSenderCache().getLastSoundNotify() == -1 || delaySinceLast >= cooldown;
			final String format = Settings.SoundNotify.FORMAT.getFor(wrapped.getSender());

			for (final SyncedCache networkPlayer : SyncedCache.getCaches()) {
				if (Settings.Channels.ENABLED) {
					final ChannelMode receiverChannel = senderChannel != null ? networkPlayer.getChannelMode(senderChannel.getName()) : null;

					// Ignore if the other player is spying or not in channel
					if (senderChannel != null && receiverChannel == null)
						continue;
				}

				// Ignore if receiver has disabled sound notifications
				if (Settings.Toggle.APPLY_ON.contains(ToggleType.SOUND_NOTIFY) && networkPlayer.hasToggledPartOff(ToggleType.SOUND_NOTIFY))
					continue;

				// Ignore if self, afk or vanished
				if (networkPlayer.getPlayerName().equals(wrapped.getName()) || wrapped.isPlayer() && networkPlayer.isVanished() || Settings.SoundNotify.REQUIRE_AFK && !networkPlayer.isAfk())
					continue;

				final PlayerPreMentionEvent event = new PlayerPreMentionEvent(networkPlayer, Settings.SoundNotify.REQUIRE_PREFIX);

				if (Platform.callEvent(event)) {
					final Pattern pattern = Pattern.compile("(?i)(^|\\s)(" + Pattern.quote(event.getPrefix() + networkPlayer.getNameOrNickColorless()) + "|" + Pattern.quote(event.getPrefix() + networkPlayer.getPlayerName()) + ")(?=\\s|$)");
					final Component replacedMessage = LegacyComponentSerializer.legacySection().deserialize(message).replaceText(b -> b.match(pattern).replacement((matchResult, builder) -> {

						// Ignore if ignoring
						if (wrapped.getPlayerCache() != null && (networkPlayer.isIgnoringPlayer(wrapped.getPlayerCache().getUniqueId()) || wrapped.getPlayerCache().isIgnoringPlayer(networkPlayer.getUniqueId()))) {
							if (networkPlayer.isIgnoringPlayer(wrapped.getPlayerCache().getUniqueId()))
								Platform.runTask(() -> Messenger.warn(wrapped.getAudience(), Lang.component("command-ignore-cannot-sound-notify-receiver", "player", networkPlayer.getPlayerName())));

							if (wrapped.getPlayerCache().isIgnoringPlayer(networkPlayer.getUniqueId()))
								Platform.runTask(() -> Messenger.warn(wrapped.getAudience(), Lang.component("command-ignore-cannot-sound-notify-sender", "player", networkPlayer.getPlayerName())));

							return builder;
						}

						if (Settings.Toggle.APPLY_ON.contains(ToggleType.SOUND_NOTIFY) && wrapped.getPlayerCache() != null && wrapped.getPlayerCache().hasToggledPartOff(ToggleType.SOUND_NOTIFY))
							return builder;

						if (!canUse) {
							CommonCore.tellLater(0, wrapped.getAudience(), Lang.component("checker-sound-notify", "seconds", Lang.numberFormat("case-second", cooldown - delaySinceLast)));

							return builder;
						}

						// Call API and finish up
						if (Platform.callEvent(new PlayerMentionEvent(wrapped.getSender(), networkPlayer))) {

							// Send the sound over network if possible
							final FoundationPlayer onlineNetworkPlayer = networkPlayer.toPlayer();

							if (onlineNetworkPlayer != null) {
								Settings.SoundNotify.SOUND.play(onlineNetworkPlayer);

								playersWhoHeardSound.add(onlineNetworkPlayer.getUniqueId());
							} else
								ProxyUtil.sendPluginMessage(ChatControlProxyMessage.SOUND, networkPlayer.getUniqueId(), Settings.SoundNotify.SOUND.toString());

							wrapped.getSenderCache().setLastSoundNotify(System.currentTimeMillis());
						}

						final Variables variables = Variables.builder().placeholder("match", matchResult.group()).placeholders(networkPlayer.getPlaceholders(PlaceholderPrefix.TAGGED));

						return PlainTextComponentSerializer.plainText().deserialize(variables.replaceLegacy(format));
					}));

					message = PlainTextComponentSerializer.plainText().serialize(replacedMessage);
				}
			}
		}

		return message;
	}
}
