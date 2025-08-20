package org.mineacademy.chatcontrol.listener.chat;

import java.util.Set;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Checker;
import org.mineacademy.chatcontrol.model.Colors;
import org.mineacademy.chatcontrol.model.Mute;
import org.mineacademy.chatcontrol.model.Newcomer;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.SoundNotify;
import org.mineacademy.chatcontrol.model.ToggleType;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.model.db.Log;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.platform.FoundationPlayer;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.settings.Lang;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A consolidated processor for both legacy and modern chat events.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ChatHandler {

	/**
	 * Handle chat.
	 *
	 * @param player
	 * @param viewers
	 * @param message
	 * @param state
	 */
	static void handle(final State state) {
		try {
			handle0(state);

		} catch (final EventHandledException ex) {
			final FoundationPlayer audience = Platform.toPlayer(state.getPlayer());

			for (final SimpleComponent component : ex.getComponents())
				Messenger.warn(audience, component);

			if (ex.isCancelled())
				state.setCancelled(true);
		}
	}

	/**
	 * Handle chat.
	 *
	 * @param player
	 * @param viewers
	 * @param message
	 * @param state
	 */
	static void handle0(final State state) {
		final Player player = state.getPlayer();
		final SenderCache senderCache = SenderCache.from(player);

		// Check pending database load, this must come first
		if (!senderCache.isDatabaseLoaded() || senderCache.isQueryingDatabase()) {
			Messenger.error(player, Lang.component("data-loading"));
			state.setCancelled(true);

			return;
		}

		final WrappedSender wrapped = WrappedSender.fromPlayerCaches(player, PlayerCache.fromCached(player), SenderCache.from(player));
		final PlayerCache cache = wrapped.getPlayerCache();

		// Check basic write permission
		if (!player.hasPermission(Permissions.Chat.WRITE)) {
			Messenger.error(player, Lang.component("player-no-write-chat-permission", "permission", Permissions.Chat.WRITE));
			state.setCancelled(true);

			return;
		}

		// Check newcomer
		if (Settings.Newcomer.RESTRICT_CHAT && Newcomer.isNewcomer(player)) {
			Messenger.error(player, Lang.component("player-newcomer-cannot-write"));
			state.setCancelled(true);

			return;
		}

		if (Settings.PrivateMessages.AUTOMODE && senderCache.hasConversingPlayer()) {
			final long thresholdMs = Settings.PrivateMessages.AUTOMODE_LEAVE_THRESHOLD.getTimeSeconds() * 1000;

			// Disable if last message is too old
			if (thresholdMs != 0 && senderCache.getLastAutoModeChat() != 0 && System.currentTimeMillis() - senderCache.getLastAutoModeChat() > thresholdMs) {
				senderCache.setConversingPlayerName(null);

				senderCache.setLastAutoModeChat(0);

			} else {
				final String targetName = senderCache.getConversingPlayerName();

				if (SyncedCache.isPlayerNameConnected(targetName)) {
					senderCache.setLastAutoModeChat(System.currentTimeMillis());

					wrapped.getAudience().dispatchCommand("/" + Settings.PrivateMessages.TELL_ALIASES.get(0) + " " + targetName + " " + state.getChatMessage());

				} else {
					Messenger.warn(player, Lang.component("command-tell-conversation-offline", "receiver_name", targetName));

					senderCache.setConversingPlayerName(null);
					senderCache.setLastAutoModeChat(0);
				}

				state.setCancelled(true);
				return;
			}
		}

		// Do not use channels
		if (!Settings.Channels.ENABLED || Settings.Channels.IGNORE_WORLDS.contains(player.getWorld().getName())) {

			// Check if the player, his channel or the server are muted
			Mute.checkMute(wrapped, null);

			// Add colors
			state.setChatMessage(Colors.removeColorsNoPermission(player, state.getChatMessage(), Colors.Type.CHAT));

			// Check if message is not only colors
			if (state.getChatMessage().trim().isEmpty()) {
				Messenger.error(player, Lang.component("checker-no-text"));
				state.setCancelled(true);

				return;
			}

			// Run antispam, anticaps and rules
			final Checker checker = Checker.filterChat(wrapped, state.getChatMessage());

			if (checker.isMessageChanged()) {
				state.setChatMessage(checker.getMessage());

				if (state.getChatMessage().trim().isEmpty()) {
					state.setCancelled(true);

					return;
				}
			}

			// Remove ignored players
			state.getViewers().removeIf(viewer -> {
				if (viewer instanceof Player) {
					final Player viewerPlayer = viewer;

					// Remove invalid players
					if (!SenderCache.from(viewerPlayer).isDatabaseLoaded())
						return true;

					// Always send to sender
					if (viewerPlayer.equals(player))
						return false;

					final PlayerCache viewerCache = PlayerCache.fromCached(viewerPlayer);

					if (checker.isCancelledSilently() && !viewerPlayer.getName().equals(player.getName()))
						return true;

					// Prevent recipients on worlds where channels are enabled from seeing the message
					if (Settings.Channels.ENABLED && !Settings.Channels.IGNORE_WORLDS.contains(viewerPlayer.getWorld().getName()))
						return true;

					if (Settings.Ignore.ENABLED && Settings.Ignore.HIDE_CHAT && !player.hasPermission(Permissions.Bypass.REACH) && viewerCache.isIgnoringPlayer(player.getUniqueId()))
						return true;

					if (!viewerPlayer.hasPermission(Permissions.Chat.READ))
						return true;

					if (Settings.Newcomer.RESTRICT_SEEING_CHAT && Newcomer.isNewcomer(viewerPlayer))
						return true;

					if (viewerCache.hasToggledPartOff(ToggleType.CHAT))
						return true;
				}

				return false;
			});

			// Add tagging such as "Hey @kangarko"
			state.setChatMessage(SoundNotify.addTagAndSound(wrapped, state.getChatMessage()));

			// Add cached colors
			if (cache.hasChatDecoration())
				state.setChatMessage("<" + cache.getChatDecoration().getName() + ">" + state.getChatMessage());

			if (cache.hasChatColor())
				state.setChatMessage("<" + cache.getChatColor().getName() + ">" + state.getChatMessage());

			// Log to file and db
			Log.logChat(player, state.getChatMessage());

			if (wrapped.hasPermission(Permissions.Chat.LINKS))
				state.setChatMessage(ChatUtil.addMiniMessageUrlTags(state.getChatMessage()));

		} else {
			final Channel writeChannel = cache.getWriteChannel();

			// Check if the player has a channel in write mode
			if (writeChannel == null) {
				Messenger.error(player, Lang.component(Channel.canJoinAnyChannel(player) ? "player-no-channel" : "player-no-possible-channel"));

				state.setCancelled(true);
				return;
			}

			final Channel.State result = writeChannel.sendMessage(Channel.State.from(wrapped, state.getChatMessage(), false));

			state.setChatMessage(result.getMessage());
			state.setConsoleFormat(result.getConsoleFormat());

			// Act as cancel at the pipeline
			if (result.isEventCancelled()) {
				state.setCancelled(true);

				return;
			}

			state.getViewers().clear();
		}
	}

	/**
	 * The currently processed chat event.
	 */
	@Getter
	static final class State {

		private final Player player;
		private final Set<Player> viewers;
		private String chatMessage;
		private boolean messageChanged;

		@Setter
		private String consoleFormat;

		@Setter
		private boolean cancelled;

		public State(final Player player, final Set<Player> viewers, final String chatMessage, final boolean cancelled) {
			this.player = player;
			this.viewers = viewers;
			this.chatMessage = chatMessage;
			this.cancelled = cancelled;
		}

		public void setChatMessage(final String chatMessage) {
			this.chatMessage = chatMessage;
			this.messageChanged = true;
		}
	}
}
