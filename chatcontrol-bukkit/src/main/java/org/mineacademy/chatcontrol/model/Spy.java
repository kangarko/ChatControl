package org.mineacademy.chatcontrol.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.api.SpyEvent;
import org.mineacademy.chatcontrol.model.db.Mail;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.util.LogUtil;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.ProxyUtil;
import org.mineacademy.fo.SerializeUtil;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.ConfigSerializable;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleBook;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.Lang;

import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Class holding methods for the /spy command
 */
public final class Spy {

	/**
	 * The type of spying what this is
	 */
	private final Type type;

	/**
	 * The initiator of the spy
	 */
	private final WrappedSender wrapped;

	/**
	 * The message associated with the spy
	 */
	private final SimpleComponent message;

	/**
	 * The variables to replace in {@link #message}
	 */
	private final Map<String, Object> placeholders = new HashMap<>();

	/**
	 * The players who are exempted from seeing {@link #message}
	 */
	private final Collection<UUID> ignoredPlayers = new HashSet<>();

	/**
	 * The channel associated with the spy
	 */
	@Nullable
	private Channel channel;

	/**
	 * If the checker or channel denied the message silently meaning only sender sees it - we still send it to spying players
	 */
	private boolean deniedSilently = false;

	/**
	 * Does the {@link #channelName} (if any) has proxy option explicitly enabled?
	 */
	private boolean proxy = true;
	private boolean channelHasProxyMode = false;

	/**
	 * The custom format associated with this spy
	 * If null, {@link Type#getFormat()} is used
	 */
	@Nullable
	private String format;

	/*
	 * Create a new spy instance that sends a message to spying players
	 */
	private Spy(@NonNull final Type type, @NonNull final WrappedSender wrapped, @NonNull final SimpleComponent message) {
		ValidCore.checkNotNull(type.getFormat(), "Type " + type + " has no format!");

		this.type = type;
		this.wrapped = wrapped;
		this.message = message;
	}

	/**
	 * Assign a channel with this spy
	 *
	 * @param channel
	 * @return
	 */
	public Spy channel(@NonNull final Channel channel) {
		this.format = channel.getSpyFormat();
		this.channel = channel;

		return this;
	}

	/**
	 * Set a custom format associated with this spy
	 *
	 * @param format
	 * @return
	 */
	public Spy format(final String format) {
		this.format = format;

		return this;
	}

	/**
	 * Add variables to the spy
	 *
	 * @param array
	 * @return
	 */
	public Spy placeholders(final Object... array) {
		this.placeholders.putAll(SerializedMap.fromArray(array).asMap());

		return this;
	}

	/**
	 * Add variables to the spy
	 *
	 * @param placeholders
	 * @return
	 */
	public Spy placeholders(final Map<String, Object> placeholders) {
		this.placeholders.putAll(placeholders);

		return this;
	}

	/**
	 * Add ignore player to the list
	 *
	 * @param uuid
	 * @return
	 */
	public Spy ignore(final UUID uuid) {
		this.ignoredPlayers.add(uuid);

		return this;
	}

	/**
	 * Add the set to ignored receivers list
	 *
	 * @param uuids
	 * @return
	 */
	public Spy ignore(final Collection<UUID> uuids) {
		this.ignoredPlayers.addAll(uuids);

		return this;
	}

	/**
	 * Broadcast spying message to spying players
	 */
	public void broadcast() {
		if (!this.canBroadcast())
			return;

		// Place default variables
		if (this.wrapped.isPlayer()) {
			final String location = SerializeUtil.serializeLocation(this.wrapped.getPlayer().getLocation());

			this.placeholders.put("location", location);
			this.placeholders.put("player_location", location);
		}

		if (this.channel != null)
			this.placeholders.put("channel", this.channel.getName());

		this.placeholders.put("message", this.message);

		// A hacky workaround to avoid double prefix since this key is only used in the format anyways
		this.placeholders.put("message_is_denied_silently", false);

		// Add ignore self
		if (this.wrapped.isPlayer())
			this.ignoredPlayers.add(this.wrapped.getUniqueId());

		final String spyFormat = this.format != null ? this.format : this.type.getFormat();

		if (!"none".equalsIgnoreCase(spyFormat) && !spyFormat.isEmpty()) {

			// Build component
			final boolean noPrefix = spyFormat.startsWith("@noprefix ");

			final SimpleComponent main = Format.parse(noPrefix ? spyFormat.substring(9).trim() : spyFormat).build(this.wrapped, this.placeholders);

			final SimpleComponent compounded = (this.deniedSilently ? Lang.component("command-spy-deny-silently") : SimpleComponent.empty())
					.append(noPrefix ? main : SimpleComponent.fromMiniAmpersand(Settings.Spy.PREFIX).append(main));

			// Send
			final List<Player> spyingPlayers = this.channel != null ? getOnlineSpyingChannelPlayers(this.channel) : getOnlineSpyingPlayers(this.type);

			// Remove ignored
			spyingPlayers.removeIf(spyingPlayer -> this.ignoredPlayers.contains(spyingPlayer.getUniqueId()));

			// API call
			final SpyEvent event = new SpyEvent(this.type, this.wrapped, this.message, new HashSet<>(spyingPlayers));

			if (Platform.callEvent(event)) {

				// Update data from event
				spyingPlayers.clear();
				spyingPlayers.addAll(event.getRecipients());

				for (final Player spyingPlayer : spyingPlayers) {
					final SyncedCache spyingReceiverCache = SyncedCache.fromUniqueId(spyingPlayer.getUniqueId());

					if (spyingReceiverCache == null)
						continue;

					if (!spyingPlayer.isOnline())
						continue;

					if (spyingReceiverCache.isIgnoringPlayer(this.wrapped.getUniqueId()))
						continue;

					if (this.wrapped.isPlayer() && spyingPlayer.equals(this.wrapped.getPlayer()))
						continue;

					Common.tell(spyingPlayer, compounded);
					this.ignoredPlayers.add(spyingPlayer.getUniqueId());
				}

				final DiscordSpy discord = Settings.Spy.DISCORD.get(this.type);

				if (discord != null && discord.isEnabled() && HookManager.isDiscordSRVLoaded()) {
					String discordMessage = this.channel != null ? CommonCore.getOrDefault(this.channel.getSpyDiscordFormat(), discord.getFormat()) : discord.getFormat();
					final long discordChannelId = this.channel != null && this.channel.getDiscordSpyChannelId() != -1 ? this.channel.getDiscordSpyChannelId() : discord.getChannelId();

					if (discordMessage == null)
						discordMessage = Settings.Spy.FORMAT_CHAT;

					if (!"none".equals(discordMessage) && !"".equals(discordMessage) && discordChannelId != -1)
						HookManager.sendDiscordMessage(discordChannelId, Variables.builder(this.wrapped.getAudience()).placeholders(this.placeholders).replaceLegacy(discordMessage));
				}

				if (Settings.Proxy.ENABLED && this.proxy)
					ProxyUtil.sendPluginMessage(
							ChatControlProxyMessage.SPY_UUID,
							this.type.getKey(),
							this.wrapped.getUniqueId(),
							this.channel != null ? this.channel.getName() : "",
							this.channelHasProxyMode,
							this.message,
							ProxyChat.getProxyPrefix().append(compounded),
							CommonCore.convertUniqueIdListToJson(this.ignoredPlayers),
							this.deniedSilently);
			}
		}
	}

	/*
	 * Return true/false whether or not to send this spy broadcast
	 */
	private boolean canBroadcast() {

		// Globally disabled
		if (!Settings.Spy.APPLY_ON.contains(this.type))
			return false;

		// Bypass permission
		final String permission = Permissions.Bypass.SPY_TYPE + this.type.getKey();

		if (this.wrapped.hasPermission(permission)) {
			LogUtil.logOnce("spy-bypass", "Note: Not sending " + this.wrapped.getName() + "'s " + this.type + " to spying players because he has '" + permission + "' permission." +
					" Player messages with such permission are not spied on. To disable that, negate this permission (a false value if using LuckPerms).");

			return false;
		}

		// Command exempted
		if (this.type == Type.COMMAND) {
			final String label = this.message.toLegacySection(null).split(" ")[0];

			if (!Settings.Spy.COMMANDS.isInList(label))
				return false;
		}

		return true;
	}

	/* ------------------------------------------------------------------------------- */
	/* Players */
	/* ------------------------------------------------------------------------------- */

	/*
	 * Return list of online spying players in channel
	 */
	private static List<Player> getOnlineSpyingChannelPlayers(final Channel channel) {
		final List<Player> spying = new ArrayList<>();

		for (final Player online : Players.getOnlinePlayersWithLoadedDb())
			if (PlayerCache.fromCached(online).isSpyingChannel(channel) && online.hasPermission(Permissions.Command.SPY) && HookManager.isLogged(online))
				spying.add(online);

		return spying;
	}

	/*
	 * Return list of online spying players
	 */
	private static List<Player> getOnlineSpyingPlayers(final Spy.Type type) {
		final List<Player> spying = new ArrayList<>();

		for (final Player online : Players.getOnlinePlayersWithLoadedDb())
			if (PlayerCache.fromCached(online).getSpyingSectors().contains(type) && online.hasPermission(Permissions.Command.SPY) && HookManager.isLogged(online))
				spying.add(online);

		return spying;
	}

	/* ------------------------------------------------------------------------------- */
	/* Sending */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Broadcast a PM
	 *
	 * @param wrapped
	 * @param receiverCache
	 * @param placeholders
	 * @param message
	 */
	public static void broadcastPrivateMessage(final WrappedSender wrapped, final SyncedCache receiverCache, final Map<String, Object> placeholders, final SimpleComponent message) {
		final Spy spy = from(Type.PRIVATE_MESSAGE, wrapped, message);

		spy.placeholders(placeholders);
		spy.ignore(receiverCache.getUniqueId());
		spy.broadcast();
	}

	/**
	 * Broadcast a mail
	 *
	 * @param wrapped
	 * @param recipients
	 * @param mail
	 */
	public static void broadcastMail(final WrappedSender wrapped, final List<PlayerCache> recipients, final Mail mail) {
		final Spy spy = from(Type.MAIL, wrapped, SimpleComponent.empty());

		spy.placeholders(
				"mail_sender", wrapped.getName(),
				"mail_receivers", CommonCore.join(CommonCore.convertList(recipients, PlayerCache::getPlayerName)),
				"mail_title", mail.getSubject(),
				"mail_uuid", mail.getUniqueId());

		spy.ignore(CommonCore.convertList(recipients, PlayerCache::getUniqueId));

		spy.broadcast();
	}

	/**
	 * Broadcast a sign being edited to all spying players
	 *
	 * @param wrapped
	 * @param lines joined lines with \n character
	 */
	public static void broadcastSign(final WrappedSender wrapped, final String[] lines) {
		final Spy spy = from(Type.SIGN, wrapped, SimpleComponent.fromSection(String.join("\n", lines).replace("\n", " ")));

		spy.placeholders(
				"sign_lines", String.join("\n<gray>", lines),
				"line_1", lines[0],
				"line_2", lines.length > 1 ? lines[1] : "",
				"line_3", lines.length > 2 ? lines[2] : "",
				"line_4", lines.length > 3 ? lines[3] : "");

		spy.broadcast();
	}

	/**
	 * Broadcast a book being edited to all spying players
	 *
	 * @param wrapped
	 * @param book
	 * @param uuid the unique ID of the book used for display
	 */
	public static void broadcastBook(final WrappedSender wrapped, final SimpleBook book, final UUID uuid) {
		final Spy spy = from(Type.BOOK, wrapped, SimpleComponent.empty());

		spy.placeholders(
				"book_uuid", uuid.toString(),
				"author", book.getAuthor(),
				"content", String.join("\n", book.getPages()),
				"title", CommonCore.getOrDefaultStrict(book.getTitle(), Lang.component("command-spy-book-untitled")));

		spy.broadcast();
	}

	/**
	 * Broadcast an item just renamed (not too long ago) on anvil to all spying players
	 *
	 * @param wrapped
	 * @param item
	 */
	public static void broadcastAnvil(final WrappedSender wrapped, final ItemStack item) {
		final Player player = wrapped.getPlayer();

		// Copy and use the hand item in Hover_Item script to show the item actually not in hands,
		// but on anvil
		final ItemStack handClone = player.getItemInHand();
		player.setItemInHand(item);

		try {
			final Spy spy = from(Type.ANVIL, wrapped, SimpleComponent.empty());

			spy.placeholders(
					"item_name", item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : Remain.getI18NDisplayName(item),
					"item_type", ChatUtil.capitalizeFully(item.getType()),
					"item_stack", item);

			spy.broadcast();

		} finally {
			player.setItemInHand(handClone);
		}
	}

	/**
	 * Broadcast a message from a chat channel to all recipients
	 *
	 * @param channel
	 * @param wrapped
	 * @param message
	 * @param ignoredPlayers
	 * @param placeholders
	 * @param deniedSilently
	 */
	public static void broadcastChannel(final Channel channel, final WrappedSender wrapped, final SimpleComponent message, final Collection<UUID> ignoredPlayers, final Map<String, Object> placeholders, final boolean deniedSilently) {
		final Spy spy = from(Type.CHAT, wrapped, message);

		spy.channel(channel);
		spy.ignore(ignoredPlayers);
		spy.placeholders(placeholders);

		spy.deniedSilently = deniedSilently;
		spy.proxy = channel.isProxySpy();
		spy.channelHasProxyMode = channel.isProxy();

		spy.broadcast();
	}

	/**
	 * Broadcast a command to spying players
	 *
	 * @param wrapped
	 * @param command
	 */
	public static void broadcastCommand(final WrappedSender wrapped, final SimpleComponent command) {
		final Spy spy = from(Type.COMMAND, wrapped, command);

		spy.broadcast();
	}

	/**
	 * Broadcasts a chat message with a custom format to spying players
	 *
	 * @param wrapped
	 * @param message
	 * @param format
	 * @param variables
	 * @param deniedSilently
	 */
	public static void broadcastCustomChat(final WrappedSender wrapped, final SimpleComponent message, final String format, final SerializedMap variables, final boolean deniedSilently) {
		final Spy spy = from(Type.CHAT, wrapped, message);

		spy.deniedSilently = deniedSilently;
		spy.format(format);
		spy.placeholders(variables);

		spy.broadcast();
	}

	/**
	 * Broadcast a type message to all spying players
	 *
	 * @param type
	 * @param wrapped
	 * @param message
	 */
	private static Spy from(final Type type, final WrappedSender wrapped, final SimpleComponent message) {
		return new Spy(type, wrapped, message);
	}

	/**
	 * Processes a spy message from proxy
	 *
	 * @param type
	 * @param senderUid
	 * @param channelName
	 * @param hasProxyMode
	 * @param message
	 * @param component
	 * @param ignoredPlayers
	 * @param deniedSilently
	 */
	public static void broadcastFromProxy(final Type type, final UUID senderUid, final String channelName, final boolean hasProxyMode, SimpleComponent message, final SimpleComponent component, final Collection<UUID> ignoredPlayers, final boolean deniedSilently) {
		if (!Settings.Spy.APPLY_ON.contains(type))
			return;

		if (type == Type.COMMAND) {
			final String label = message.toPlain(null).split(" ")[0];

			if (!Settings.Spy.COMMANDS.isInList(label))
				return;
		}

		// Send
		final Channel channel = !channelName.isEmpty() ? Channel.findChannel(channelName) : null;
		final List<Player> spyingPlayers = channel != null ? getOnlineSpyingChannelPlayers(channel) : getOnlineSpyingPlayers(type);

		// Remove ignored
		spyingPlayers.removeIf(spyingPlayer -> {
			final SyncedCache spyingReceiverCache = SyncedCache.fromUniqueId(spyingPlayer.getUniqueId());

			if (ignoredPlayers != null && ignoredPlayers.contains(spyingPlayer.getUniqueId()))
				return true;

			if (channel != null && channel.isInChannel(spyingPlayer) && hasProxyMode && !deniedSilently)
				return true;

			if (spyingReceiverCache.isIgnoringPlayer(senderUid))
				return true;

			return false;
		});

		final SpyEvent event = new SpyEvent(type, null, message, new HashSet<>(spyingPlayers));

		// API call
		if (Platform.callEvent(event)) {

			// Update data from event
			message = event.getMessage();
			spyingPlayers.clear();
			spyingPlayers.addAll(event.getRecipients());

			// Broadcast
			for (final Player spyingPlayer : spyingPlayers)
				Common.tell(spyingPlayer, component);
		}
	}

	// ------------------------------------------------------------------------------------------------------------
	// Classes
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Represents a rule type
	 */
	@RequiredArgsConstructor
	public enum Type {

		/**
		 * Spying channel messages
		 */
		CHAT("chat") {
			@Override
			public String getFormat() {
				return Settings.Spy.FORMAT_CHAT;
			}
		},

		/**
		 * Spying player commands
		 */
		COMMAND("command") {
			@Override
			public String getFormat() {
				return Settings.Spy.FORMAT_COMMAND;
			}
		},

		/**
		 * Your mom n NSA spying private conversations yay!
		 */
		PRIVATE_MESSAGE("private_message") {
			@Override
			public String getFormat() {
				return Settings.Spy.FORMAT_PRIVATE_MESSAGE;
			}
		},

		/**
		 * Spying mails when sent
		 */
		MAIL("mail") {
			@Override
			public String getFormat() {
				return Settings.Spy.FORMAT_MAIL;
			}
		},

		/**
		 * Spying signs
		 */
		SIGN("sign") {
			@Override
			public String getFormat() {
				return Settings.Spy.FORMAT_SIGN;
			}
		},

		/**
		 * Spying writing to books
		 */
		BOOK("book") {
			@Override
			public String getFormat() {
				return Settings.Spy.FORMAT_BOOK;
			}
		},

		/**
		 * Spying items when renamed
		 */
		ANVIL("anvil") {
			@Override
			public String getFormat() {
				return Settings.Spy.FORMAT_ANVIL;
			}
		};

		/**
		 * The saveable non-obfuscated key
		 */
		@Getter
		private final String key;

		/**
		 * Return the format used for the given spy type
		 *
		 * @return
		 */
		public abstract String getFormat();

		/**
		 * The the lang yummy dummy key
		 *
		 * @return
		 */
		public final String getLangKey() {
			return Lang.plain("command-spy-type-" + this.key.replace("_", "-"));
		}

		/**
		 * Get the permission node for this type
		 *
		 * @return
		 */
		public final String getPermission() {
			return Permissions.Spy.TYPE + this.key;
		}

		/**
		 * Attempt to load this class from the given config key
		 *
		 * @param key
		 * @return
		 */
		public static Type fromKey(final String key) {
			for (final Type mode : values())
				if (mode.key.equalsIgnoreCase(key))
					return mode;

			throw new IllegalArgumentException("No such spying type: " + key + " Available: " + CommonCore.join(values()));
		}

		/**
		 * Returns {@link #getKey()}
		 */
		@Override
		public String toString() {
			return this.key;
		}
	}

	/**
	 * Represents a Discord sending of spy.
	 */
	@Data
	public static class DiscordSpy implements ConfigSerializable {
		private final boolean enabled;
		private final long channelId;
		private final String format;

		@Override
		public SerializedMap serialize() {
			return SerializedMap.fromArray(
					"Enabled", this.enabled,
					"Channel_Id", this.channelId,
					"Format", this.format);
		}

		public static DiscordSpy deserialize(final SerializedMap map) {
			final boolean enabled = map.getBoolean("Enabled");
			final Long channelId = map.getLong("Channel_Id");
			final String format = map.getString("Format");

			if (enabled && channelId == null)
				throw new FoException("If spy to Discord is enabled, Channel_Id must be set", false);

			return new DiscordSpy(enabled, channelId, format);
		}
	}
}
