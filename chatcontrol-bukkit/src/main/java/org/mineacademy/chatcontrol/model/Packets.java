package org.mineacademy.chatcontrol.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.operator.Rule;
import org.mineacademy.chatcontrol.operator.Rule.RuleCheck;
import org.mineacademy.chatcontrol.operator.Rules;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.annotation.AutoRegister;
import org.mineacademy.fo.collection.ExpiringMap;
import org.mineacademy.fo.model.PacketListener;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.platform.FoundationPlayer;
import org.mineacademy.fo.platform.Platform;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketContainer;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

/**
 * Represents packet handling using ProtocolLib
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AutoRegister(hideIncompatibilityWarnings = true)
public final class Packets extends PacketListener {

	/**
	* The singleton for this class
	*/
	@Getter
	private static final Packets instance = new Packets();

	/**
	 * Connects tab-complete sending and receiving packet
	 */
	private final Map<String, String> buffers = ExpiringMap.builder().expiration(10, TimeUnit.MINUTES).build();

	/**
	 * Players being processed RIGHT NOW inside the method. Prevents dead loop.
	 */
	private final Set<String> playersPendingMessageRemoval = new HashSet<>();

	/**
	 * Register and initiate packet listening
	 */
	@Override
	public void onRegister() {

		if (!Settings.ProtocolLib.ENABLED)
			return;

		//
		// Process tab-completions for legacy Minecraft versions
		//
		if (MinecraftVersion.olderThan(V.v1_13)) {

			// Receiving tab complete request
			this.addReceivingListener(ListenerPriority.HIGHEST, PacketType.Play.Client.TAB_COMPLETE, event -> {

				final String buffer = event.getPacket().getStrings().read(0);

				// Save for sending later, see below
				this.buffers.put(event.getPlayer().getName(), buffer);
			});

			// Filter players from tab complete
			this.addSendingListener(ListenerPriority.HIGHEST, PacketType.Play.Server.TAB_COMPLETE, event -> {

				final String buffer = this.buffers.remove(event.getPlayer().getName());

				if (buffer == null || event.getPlayer().hasPermission(Permissions.Bypass.TAB_COMPLETE))
					return;

				final boolean hasVanishBypass = event.getPlayer().hasPermission(Permissions.Bypass.VANISH);
				final PacketContainer packet = event.getPacket();
				final List<String> suggestions = CommonCore.toList(packet.getStringArrays().read(0));
				final Set<String> nicks = new HashSet<>();
				final boolean isCommand = buffer.charAt(0) == '/';

				// Prevent tab completing completely if the command is too short
				if (isCommand && Settings.TabComplete.PREVENT_IF_BELOW_LENGTH != 0 && (buffer.length() - 1) < Settings.TabComplete.PREVENT_IF_BELOW_LENGTH) {
					event.setCancelled(true);

					return;
				}

				// Remove vanished players
				for (final Iterator<String> it = suggestions.iterator(); it.hasNext();) {
					final String suggestion = it.next();
					final Player player = Players.findPlayer(suggestion);

					if (player != null) {
						if (hasVanishBypass || !PlayerUtil.isVanished(player)) {
							final String nick = Settings.TabComplete.USE_NICKNAMES ? Players.getNickOrNullColorless(player) : null;

							nicks.add(nick != null ? nick : player.getName());
						}

						it.remove();
					}

					else if (isCommand && !Settings.TabComplete.WHITELIST.isInListRegex(suggestion))
						it.remove();
				}

				// Add all nicknames matching the word, ignoring commands
				if (!isCommand) {
					final String word = buffer.endsWith(" ") ? "" : CommonCore.last(buffer.split(" "));

					nicks.addAll(CommonCore.tabComplete(word, Players.getPlayerNamesForTabComplete(hasVanishBypass)));
				}

				// Merge together and sort
				final List<String> allTogether = CommonCore.joinLists(suggestions, nicks);
				Collections.sort(allTogether);

				packet.getStringArrays().write(0, CommonCore.toArray(allTogether));
			});
		}

		//
		// Process chat messages
		//
		this.addPacketListener(new SimpleChatAdapter() {

			@Override
			protected String onJsonMessage(final Player player, final String jsonMessage) {
				String result = jsonMessage;

				// Apply packet rules if enabled
				if (Settings.Rules.APPLY_ON.contains(RuleType.PACKET)) {
					final boolean legacy = MinecraftVersion.olderThan(V.v1_16);
					final String plainText = SimpleComponent.fromAdventureJson(jsonMessage, legacy).toPlain();

					if (!plainText.isEmpty()) {
						final WrappedSender wrapped = WrappedSender.fromPlayer(player);

						// Rule.filter throws EventHandledException on "then deny", which
						// SimpleChatAdapter catches and uses to cancel the packet
						final RuleCheck<Rule> check = Rule.filter(RuleType.PACKET, wrapped, plainText);

						if (check.isMessageChanged())
							result = SimpleComponent.fromMiniAmpersand(check.getMessage()).toAdventureJson(null, legacy);
					}
				}

				// Store for purge feature (only reached if not denied)
				synchronized (Packets.this.playersPendingMessageRemoval) {
					if (!Packets.this.playersPendingMessageRemoval.contains(player.getName()))
						SenderCache.from(player).getLastChatPackets().add(result);
				}

				return result;
			}

			@Override
			protected boolean editJson() {
				return Settings.Rules.APPLY_ON.contains(RuleType.PACKET)
						&& !Rules.getInstance().getRules(RuleType.PACKET).isEmpty();
			}
		});
	}

	/**
	 * Remove the given message containing the given unique ID for all players,
	 * sending them their last 100 messages without it, or blank if not enough data
	 *
	 * Removal is depending on the given remove mode
	 *
	 * @param mode
	 * @param uniqueId
	 */
	public void removeMessage(final RemoveMode mode, final UUID uniqueId) {
		Platform.runTask(() -> {
			synchronized (this.playersPendingMessageRemoval) {
				final String stringId = uniqueId.toString();

				for (final Iterator<SenderCache> cacheIt = SenderCache.getCaches(); cacheIt.hasNext();) {
					final SenderCache cache = cacheIt.next();
					final List<String> last100Packets = new ArrayList<>(100);

					last100Packets.addAll(cache.getLastChatPackets());

					boolean found = false;

					for (final Iterator<String> it = last100Packets.iterator(); it.hasNext();) {
						final String jsonMessage = it.next();

						if (jsonMessage.contains(mode.getPrefix() + "_" + stringId)) {
							it.remove();

							found = true;
						}
					}

					if (found && !this.playersPendingMessageRemoval.contains(cache.getSenderName()))
						try {
							this.playersPendingMessageRemoval.add(cache.getSenderName());
							final List<String> new100packets = new ArrayList<>();

							// Fill in the blank if no data
							for (int i = 0; i < 100 - last100Packets.size(); i++)
								new100packets.add(0, "{\"text\": \" \"}");

							for (final String json : last100Packets)
								new100packets.add(json);

							final Player player = cache.toPlayer();

							if (player != null) {
								final FoundationPlayer audience = Platform.toPlayer(player);

								for (final String jsonPacket : new100packets)
									audience.sendJson(jsonPacket);
							}

							cache.getLastChatPackets().clear();
							cache.getLastChatPackets().addAll(new100packets);

						} finally {
							this.playersPendingMessageRemoval.remove(cache.getSenderName());
						}
				}
			}
		});
	}

	/**
	 * How we should remove sent messages?
	 */
	@RequiredArgsConstructor
	public enum RemoveMode {

		/**
		 * Only remove the message matching the UUID
		 */
		SPECIFIC_MESSAGE("SPECIFIC_MESSAGE", "flpm"),

		/**
		 * Remove all messages from the UUID of the sender
		 */
		ALL_MESSAGES_FROM_SENDER("ALL_MESSAGES_FROM_SENDER", "flps");

		/**
		 * The unobfuscatable key
		 */
		@Getter
		private final String key;

		/**
		 * The prefix used for matching in the method
		 */
		@Getter
		private final String prefix;

		/**
		 * @see java.lang.Enum#toString()
		 */
		@Override
		public String toString() {
			return this.key;
		}

		/**
		 * Parse from {@link #getKey()}
		 *
		 * @param key
		 * @return
		 */
		public static RemoveMode fromKey(final String key) {
			for (final RemoveMode mode : values())
				if (mode.getKey().equalsIgnoreCase(key))
					return mode;

			throw new IllegalArgumentException("No such packet remove mode " + key + " Available: " + values());
		}
	}
}
