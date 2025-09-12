package org.mineacademy.chatcontrol.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.api.ChannelJoinEvent;
import org.mineacademy.chatcontrol.api.ChannelLeaveEvent;
import org.mineacademy.chatcontrol.api.ChannelPostChatEvent;
import org.mineacademy.chatcontrol.api.ChannelPreChatEvent;
import org.mineacademy.chatcontrol.api.ChatChannelProxyEvent;
import org.mineacademy.chatcontrol.api.MuteEvent;
import org.mineacademy.chatcontrol.api.Party;
import org.mineacademy.chatcontrol.model.db.Log;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.model.db.ServerSettings;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Proxy;
import org.mineacademy.chatcontrol.util.LogUtil;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.ProxyUtil;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.ConfigStringSerializable;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.SimpleSound;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.platform.FoundationPlayer;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.ConfigItems;
import org.mineacademy.fo.settings.Lang;
import org.mineacademy.fo.settings.YamlConfig;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Represents a chat channel
 */
@Getter
public final class Channel extends YamlConfig implements ConfigStringSerializable {

	/**
	 * Stores all loaded channels
	 */
	private static final ConfigItems<Channel> loadedChannels = ConfigItems.fromFile("Channels.List", "settings.yml", Channel.class);

	/**
	 * Get the channel name
	 */
	private final String name;

	/**
	 * How the message should look like when typed to the channel?
	 */
	private String format;

	/**
	 * How the message should look like when logged to console? Null to use default, empty to not send.
	 */
	private String consoleFormat;

	/**
	 * How the message should look like when sent to Discord? Null to use default, empty to not send.
	 */
	private String toDiscordFormat;

	/**
	 * The format used to format the Discord message in Minecraft.
	 */
	private String fromDiscordFormat;

	/**
	 * The format used to format the name shown on the webhook
	 */
	private String discordWebhookNameFormat;

	/**
	 * Overrides the spy format from settings.yml
	 */
	private String spyFormat;

	/**
	 * Overrides the discord spy format from settings.yml
	 */
	private String spyDiscordFormat;

	/**
	 * Distance players within the world will receive the message.
	 *
	 * null = no range feature, all worlds
	 * whole number = range, radius in senders world
	 * * = range, whole world
	 */
	private String range;

	/**
	 * The linked worlds if range is set to *
	 */
	private Set<String> rangeWorlds;

	/**
	 * The minimum online players to enable ranged chat.
	 * I.e. if the value is 20, then if there is 0-19 players, global chat will be used.
	 */
	private Integer minPlayersForRange;

	/**
	 * If the channel is muted, this is the unmute time in the future
	 * where it will no longer be muted
	 */
	private Long unmuteTime;

	/**
	 * Integration with other plugins supporting their "party" feature such as connecting this channel to Towny etc.
	 */
	private Party party;

	/**
	 * How long should players wait before typing their message into this channel?
	 */
	private SimpleTime messageDelay;

	/**
	 * Shall we send data over proxy? If not set, uses the global setting from settings.yml
	 */
	private boolean proxy;

	/**
	 * Shall we send channel messages to spying administrators over proxy network? This works even if {@link #proxy}
	 * option is disabled.
	 */
	private boolean proxySpy;

	/**
	 * A linked Discord channel ID
	 */
	private long discordChannelId;

	/**
	 * A linked Discord channel ID for spy messages
	 */
	private long discordSpyChannelId;

	/**
	 * Z sound that is played to dudes in the channel
	 */
	private SimpleSound sound;

	/**
	 * Shall we cancel chat events from this channel? Used to hide this on DynMap etc.
	 */
	private boolean cancelEvent;

	/**
	 * Create a new channel by name
	 */
	private Channel(final String name) {
		this.name = name;

		this.setPathPrefix("Channels.List." + name);
		this.loadAndExtract(NO_DEFAULT, "settings.yml");
	}

	/**
	 * @see org.mineacademy.fo.settings.YamlConfig#onLoad()
	 */
	@Override
	protected void onLoad() {
		if (this.getObject("") instanceof String)
			throw new FoException("Wrong configuration for channel " + this.getName() + ". Expected a map of settings inside the channel, but got a string. Delete Channels.List section of settings.yml to regenerate.", false);

		final SerializedMap map = this.getMap("");

		// Prevent users from putting keys we cannot detect in their config
		map.setRemoveOnGet(true);

		if (map.containsKey("Format_Discord")) {
			final String legacyFormat = map.getString("Format_Discord");

			map.put("Format_To_Discord", legacyFormat);
			map.put("Format_From_Discord", legacyFormat);

			Common.log("Migrated " + this.name + " Format_Discord option. We now have two keys Format_To_Discord and Format_From_Discord for more customization options. "
					+ "Setting both new keys to old key value, please check it and adjust: " + legacyFormat);
		}

		this.format = map.getString("Format");
		this.consoleFormat = map.getString("Format_Console");
		this.toDiscordFormat = map.getString("Format_To_Discord");
		this.fromDiscordFormat = map.getString("Format_From_Discord");
		this.discordWebhookNameFormat = map.getString("Format_Discord_Webhook_Name");
		this.spyFormat = map.getString("Format_Spy");
		this.spyDiscordFormat = map.getString("Format_Spy_Discord");
		this.range = this.isSet("Range") ? map.getObject("Range").toString() : null;
		this.rangeWorlds = map.getSet("Range_Worlds", String.class);
		this.minPlayersForRange = map.getInteger("Min_Players_For_Range");
		this.unmuteTime = map.getLong("Unmute_Time");
		this.party = this.isSet("Party") ? Party.fromKey(map.getString("Party")) : null;
		this.messageDelay = this.isSet("Message_Delay") ? SimpleTime.fromString(map.getString("Message_Delay")) : null;
		this.proxy = this.isSet("Proxy") ? map.getBoolean("Proxy") : Proxy.ENABLED;
		this.proxySpy = this.isSet("Proxy_Spy") ? map.getBoolean("Proxy_Spy") : this.proxy;
		this.discordChannelId = this.isSet("Discord_Channel_Id") ? map.getLong("Discord_Channel_Id") : -1;
		this.discordSpyChannelId = this.isSet("Discord_Spy_Channel_Id") ? map.getLong("Discord_Spy_Channel_Id") : -1;
		this.sound = this.isSet("Sound") ? map.get("Sound", SimpleSound.class) : null;
		this.cancelEvent = this.isSet("Cancel_Event") ? map.getBoolean("Cancel_Event") : false;

		if (!map.isEmpty())
			Common.warning("Channel " + this.name + " found unrecognized keys '" + map.keySet() + "', see the # comments in settings.yml in Channels.List for what keys you can use.");

		ValidCore.checkNotEmpty(this.format, "Format for channel '" + this.getName() + "' must be set!");

		if (this.range != null && !"*".equals(this.range)) {
			ValidCore.checkInteger(this.range, "Your channel " + this.name + " has option Range which must either be * (for entire world) or a whole number!");
			ValidCore.checkBoolean(this.rangeWorlds.isEmpty(), "Can only use key Range_Worlds for channel " + this.name + " when Range is set to '*' not: " + this.range);
		}
	}

	@Override
	public void onSave() {
		this.set("Format", this.format);
		this.set("Format_Console", this.consoleFormat);
		this.set("Format_To_Discord", this.toDiscordFormat);
		this.set("Format_From_Discord", this.fromDiscordFormat);
		this.set("Format_Spy", this.spyFormat);
		this.set("Format_Spy_Discord", this.spyDiscordFormat);
		this.set("Range", this.range);
		this.set("Range_Worlds", this.rangeWorlds);
		this.set("Min_Players_For_Range", this.minPlayersForRange);
		this.set("Unmute_Time", this.unmuteTime);
		this.set("Party", this.party);
		this.set("Message_Delay", this.messageDelay);
		this.set("Proxy", this.proxy);
		this.set("Proxy_Spy", this.proxySpy);
		this.set("Discord_Channel_Id", this.discordChannelId);
		this.set("Discord_Spy_Channel_Id", this.discordSpyChannelId);
	}

	// ----------------------------------------------------------------------------------
	// Getters and setters
	// ----------------------------------------------------------------------------------

	/**
	 * Return true if the channel is muted
	 *
	 * @return
	 */
	public boolean isMuted() {
		return this.unmuteTime != null && this.unmuteTime > System.currentTimeMillis();
	}

	/**
	 * Return the time left until the server is unmuted
	 *
	 * @return
	 */
	public long getUnmuteTimeRemaining() {
		return this.unmuteTime != null && this.unmuteTime > System.currentTimeMillis() ? this.unmuteTime - System.currentTimeMillis() : 0;
	}

	/**
	 * Set the mute for this channel
	 *
	 * @param duration how long, null to unmute
	 */
	public void setMuted(@Nullable SimpleTime duration) {
		final MuteEvent event = MuteEvent.channel(this, duration);

		if (Platform.callEvent(event)) {
			duration = event.getDuration();

			this.unmuteTime = duration == null ? null : System.currentTimeMillis() + duration.getTimeSeconds() * 1000;
			this.save();
		}
	}

	// ----------------------------------------------------------------------------------
	// Methods
	// ----------------------------------------------------------------------------------

	/**
	 * Joins the player into this channel for the given mode
	 *
	 * @param player
	 * @param mode
	 * @param save
	 *
	 * @return false if API call canceled joining
	 */
	public boolean joinPlayer(@NonNull final Player player, @NonNull final ChannelMode mode, final boolean save) {
		final PlayerCache cache = PlayerCache.fromCached(player);

		return this.joinPlayer(cache, mode, save);
	}

	/**
	 * Joins the given player cache into this channel for the given mode
	 * This potentially enables joining of offline players to channels
	 *
	 * @param cache
	 * @param mode
	 * @param save
	 *
	 * @return false if API call canceled join
	 */
	public boolean joinPlayer(@NonNull final PlayerCache cache, @NonNull final ChannelMode mode, final boolean save) {
		final ChannelMode oldMode = cache.getChannelMode(this);
		ValidCore.checkBoolean(oldMode != mode, "Player " + cache.getPlayerName() + " is already in channel " + this.name + " as " + mode);

		if (mode == ChannelMode.WRITE)
			this.checkSingleWrite(cache);

		if (!Platform.callEvent(new ChannelJoinEvent(cache, this, mode)))
			return false;

		cache.updateChannelMode(this, mode, save);

		return true;
	}

	/*
	 * Check if player only has one write channel
	 */
	private void checkSingleWrite(final PlayerCache cache) {
		final List<String> writeChannels = new ArrayList<>();

		for (final Entry<Channel, ChannelMode> entry : cache.getChannels().entrySet()) {
			final String otherChannel = entry.getKey().getName();

			if (entry.getValue() == ChannelMode.WRITE)
				writeChannels.add(otherChannel);
		}

		ValidCore.checkBoolean(writeChannels.size() < 2, "Found player " + cache + " in more than one write channel: " + writeChannels);
	}

	/**
	 * Kicks player from this channel
	 *
	 * @param player
	 * @param save
	 *
	 * @return false if API call prevented leaving
	 */
	public boolean leavePlayer(final Player player, final boolean save) {
		final PlayerCache cache = PlayerCache.fromCached(player);

		return this.leavePlayer(cache, save);
	}

	/**
	 * Kicks player cache from this channel
	 * This potentially enables kicking offline players from channels
	 *
	 * @param cache
	 * @param save
	 *
	 * @return false if API call prevented leaving
	 */
	public boolean leavePlayer(final PlayerCache cache, final boolean save) {
		ValidCore.checkBoolean(this.isInChannel(cache), "Player " + cache.getPlayerName() + " is not in channel: " + this.name);

		final ChannelMode mode = cache.getChannelMode(this);

		if (!Platform.callEvent(new ChannelLeaveEvent(cache, this, mode)))
			return false;

		cache.updateChannelMode(this, null, save);

		return true;
	}

	/**
	 * Return true if player is in channel in any mode
	 *
	 * @param player
	 * @return
	 */
	public boolean isInChannel(@NonNull final Player player) {
		return this.getChannelMode(player) != null;
	}

	/**
	 * Return true if player cache is in channel in any mode
	 *
	 * @param cache
	 * @return
	 */
	public boolean isInChannel(@NonNull final PlayerCache cache) {
		return cache.getChannelMode(this) != null;
	}

	/**
	 * Return the channel mode for player, null if not joined
	 *
	 * @param player
	 * @return
	 */
	public ChannelMode getChannelMode(@NonNull final Player player) {
		return PlayerCache.fromCached(player).getChannelMode(this);
	}

	/**
	 * Return a map of all players in channel for the given mode
	 *
	 * @param mode
	 * @return
	 */
	public List<Player> getOnlinePlayers(final ChannelMode mode) {
		final List<Player> players = new ArrayList<>();

		for (final Player online : Players.getOnlinePlayersWithLoadedDb()) {
			final PlayerCache cache = PlayerCache.fromCached(online);
			final ChannelMode otherMode = cache.getChannelMode(this);

			if (otherMode == mode)
				players.add(online);
		}

		return players;
	}

	/**
	 * Return a map of all players in channel
	 *
	 * @return
	 */
	public Map<Player, ChannelMode> getOnlinePlayers() {
		final Map<Player, ChannelMode> players = new HashMap<>();

		for (final Player online : Players.getOnlinePlayersWithLoadedDb()) {
			final PlayerCache cache = PlayerCache.fromCached(online);
			final ChannelMode mode = cache.getChannelMode(this);

			if (mode != null)
				players.put(online, mode);
		}

		return players;
	}

	/**
	 * Send a message to the channel
	 *
	 * @param sender
	 * @param message
	 * @return
	 * @throws EventHandledException
	 */
	public State sendMessage(final CommandSender sender, final String message) throws EventHandledException {
		return this.sendMessage(State.from(WrappedSender.fromSender(sender), message, true));
	}

	/**
	 * Send a message to the channel
	 *
	 * @param state the send state
	 *
	 * @return
	 * @throws EventHandledException
	 */
	public State sendMessage(final State state) throws EventHandledException {
		final WrappedSender sender = state.getSender();

		// Compile receivers:  Ensure the sender receives the message even if not in channel
		final Tuple<Set<Player>, Set<Player>> tuple = this.compileReceivers(sender);

		final Set<Player> receivers = tuple.getKey();
		final Set<Player> hiddenReceivers = tuple.getValue();

		// API
		final ChannelPreChatEvent event = new ChannelPreChatEvent(this, sender.getSender(), state.getMessage(), receivers);

		if (!Platform.callEvent(event))
			throw new EventHandledException(true);

		state.setMessage(event.getMessage());

		// Remove those who ignore the sender
		if (Settings.Ignore.ENABLED && Settings.Ignore.HIDE_CHAT && !sender.hasPermission(Permissions.Bypass.REACH)) {
			final Predicate<Player> filter = recipient -> PlayerCache.fromCached(recipient).isIgnoringPlayer(sender.getUniqueId()) || Settings.Ignore.BIDIRECTIONAL && sender.isPlayer() && sender.getPlayerCache().isIgnoringPlayer(recipient.getUniqueId());

			receivers.removeIf(filter);
			hiddenReceivers.removeIf(filter);
		}

		// Throw exception is handled
		Mute.checkMute(sender, this);

		// Remove colors early so filters can force colors
		state.setMessage(Colors.removeColorsNoPermission(sender.getSender(), state.getMessage(), Colors.Type.CHAT));

		// Filters
		final Checker check = Checker.filterChannel(sender, state.getMessage(), this);

		if (check.isMessageChanged())
			state.setMessage(check.getMessage());

		state.setMessage(SoundNotify.addTagAndSound(sender, state.getMessage()));
		state.setPlaceholder("message_is_denied_silently", check.isCancelledSilently());

		if (sender.hasPermission(Permissions.Chat.LINKS))
			state.setMessage(ChatUtil.addMiniMessageUrlTags(state.getMessage()));

		// Resolve MESSAGE type placeholders such as "I hold an [item]"
		final SimpleComponent chatComponent = state.getVariables().replaceMessageVariables(SimpleComponent.fromMiniSection(state.getMessage()));

		if (chatComponent.toPlain().isEmpty())
			throw new EventHandledException(true);

		state.setMessage(chatComponent.toMini(null));
		state.setComponent(chatComponent);

		final String chatMessageAsLegacy = chatComponent.toLegacySection(null, false);

		// Inject variables
		state.setPlaceholder("channel", this.name);
		state.setPlaceholder("message", chatComponent);

		// Warn if no visible receivers
		if (receivers.isEmpty() && this.range != null)
			Common.tellTimed((int) Settings.Channels.RANGED_CHANNEL_NO_NEAR_PLAYER_DELAY.getTimeSeconds(), sender.getSender(),
					state.getVariables().replaceLegacy(Lang.legacy("player-nobody-in-" + (this.range != null ? "range" : "channel"))));

		// Add self
		if (sender.isPlayer())
			receivers.add(sender.getPlayer());

		// Compile format
		final Format format = Format.parse(sender.isDiscord() && this.fromDiscordFormat != null ? this.fromDiscordFormat : this.format);

		if (format == null)
			throw new EventHandledException(true, SimpleComponent.fromAmpersand("&cChannel " + this.name + " is using non-existing formatting '" + this.format + "&c'. Please contact administrator."));

		// Build the component we send -- send the changed message from sound notify
		SimpleComponent formattedComponent = format.build(sender, state.getVariables().placeholdersReadOnly());

		// Build log
		String consoleFormat = this.consoleFormat != null ? this.consoleFormat : Settings.Channels.FORMAT_CONSOLE;

		if ("none".equals(consoleFormat))
			LogUtil.logOnce("channel-log-none", "Warning: Channel " + this.name + " had Format_Console set to 'none'. The only way to hide console log is to cancel the event, which may conflict with DynMap or other plugins. Be careful!");

		else if ("default".equals(consoleFormat)) {
			consoleFormat = formattedComponent.toLegacySection(null);

			if (Settings.SHOW_TIPS)
				if (formattedComponent.toPlain(null).trim().isEmpty())
					LogUtil.logOnce("channel-log-none", "Warning: Channel " + this.name + " will not show for consoles because the output is empty. This might be perfectly fine if all of your format parts are conditional," +
							"but also may indicate a problem with your format part design. This message will only show once.");

		} else {
			// test mini tags
			state.setPlaceholder("message", chatMessageAsLegacy);

			consoleFormat = state.getVariables().replaceLegacy(consoleFormat);
		}

		final ChannelPostChatEvent postEvent = new ChannelPostChatEvent(this, sender.getSender(), receivers, state.getMessage(), formattedComponent, consoleFormat, check.isCancelledSilently(), false);

		if (!Platform.callEvent(postEvent))
			throw new EventHandledException(true);

		consoleFormat = postEvent.getConsoleFormat();
		formattedComponent = postEvent.getFormat();

		// Send to players or the sender himself only if silently canceled
		if (check.isCancelledSilently()) {
			if (!formattedComponent.toPlain(sender.getAudience()).trim().isEmpty()) {

				if (sender.isPlayer())
					sender.getAudience().sendMessage(
							SimpleComponent.fromMiniSection(
									HookManager.replaceRelationPlaceholders(sender.getPlayer(), sender.getPlayer(), formattedComponent.toMini())));
				else
					sender.getAudience().sendMessage(formattedComponent);

				Platform.runTaskAsync(() -> {
					if (!check.isSpyingIgnored())
						Spy.broadcastChannel(this, sender, chatComponent, Arrays.asList(sender.getUniqueId()), state.getVariables().placeholdersReadOnly(), check.isCancelledSilently());

					if (!check.isLoggingIgnored())
						Log.logChannel(sender.getSender(), this, Lang.plain("command-spy-deny-silently") + chatMessageAsLegacy);
				});
			}

		} else {

			// Include hidden receivers
			receivers.addAll(hiddenReceivers);

			// Sender/Receiver condition might prevent sending to anyone
			boolean atLeastOneSuccessfulSent = false;

			for (final Player receiver : receivers) {
				final FoundationPlayer receiverAudience = Platform.toPlayer(receiver);

				if (!sender.isPlayer()) {
					receiverAudience.sendMessage(formattedComponent);

					atLeastOneSuccessfulSent = true;
				} else if (!formattedComponent.isEmpty(receiverAudience)) {
					final String replacedMini = HookManager.replaceRelationPlaceholders(sender.getPlayer(), receiver, formattedComponent.toMini(receiverAudience));

					receiverAudience.sendMessage(SimpleComponent.fromMiniSection(replacedMini));

					atLeastOneSuccessfulSent = true;
				}
			}

			if (atLeastOneSuccessfulSent) {
				final String finalConsoleFormat = consoleFormat;
				final SimpleComponent finalFormattedComponent = formattedComponent;

				Platform.runTaskAsync(() -> {
					if (this.sound != null)
						for (final Player receiver : receivers)
							if (!receiver.getUniqueId().equals(sender.getUniqueId()))
								this.sound.play(receiver);

					if (!check.isSpyingIgnored())
						Spy.broadcastChannel(this, sender, chatComponent, CommonCore.convertList(receivers, Player::getUniqueId), state.getVariables().placeholdersReadOnly(), check.isCancelledSilently());

					if (!check.isLoggingIgnored())
						Log.logChannel(sender.getSender(), this, chatMessageAsLegacy);

					if (this.discordChannelId != -1 && HookManager.isDiscordSRVLoaded()) {
						final String formatedComponentAsJson = finalFormattedComponent.toAdventureJson(null, MinecraftVersion.olderThan(V.v1_16));

						if (!sender.isDiscord()) {
							String discordProxyMessage = this.prepareToDiscordMessage(sender, chatComponent);

							if (discordProxyMessage != null && HookManager.isDiscordSRVLoaded()) {
								Discord.getInstance().sendChannelMessage(sender.getSender(), null, this.discordChannelId, discordProxyMessage, formatedComponentAsJson, this);

								// If this server has channel message configured to go to Discord
								// prevent other server from broadcasting it.
								discordProxyMessage = null;
							}

						} else if (HookManager.isDiscordSRVLoaded())
							Discord.getInstance().markReceivedMessage(this.discordChannelId, sender.getDiscordSender(), formatedComponentAsJson);
					}

					if (!sender.isDiscord() && this.proxy && Settings.Proxy.ENABLED)
						ProxyUtil.sendPluginMessage(ChatControlProxyMessage.CHANNEL,
								this.name,
								sender.getName(),
								sender.getUniqueId(),
								ProxyChat.getProxyPrefix().append(finalFormattedComponent),
								CommonCore.getOrEmpty(finalConsoleFormat),
								sender.hasPermission(Permissions.Bypass.MUTE),
								sender.hasPermission(Permissions.Bypass.REACH),
								sender.hasPermission(Permissions.Bypass.LOG));
				});
			}
		}

		state.setEventCancelled(this.cancelEvent);
		state.setConsoleFormat(consoleFormat);

		// Log manually if not handled in player chat event
		if ((state.isLogToConsole() || !sender.isPlayer()) && !"none".equals(consoleFormat))
			Platform.log(SimpleComponent.fromMiniAmpersand(consoleFormat).toLegacySection());

		return state;
	}

	/*
	 * Prepares the message to be sent to Discord
	 */
	private String prepareToDiscordMessage(final WrappedSender wrapped, SimpleComponent chatMessage) {
		final String formatName = CommonCore.getOrDefault(this.toDiscordFormat, Settings.Channels.FORMAT_DISCORD);

		if (formatName == null || "".equals(formatName))
			return null;

		if (!wrapped.hasPermission(Permissions.Discord.TAG))
			chatMessage = chatMessage.replaceLiteral("@", "\\@");

		if (!wrapped.hasPermission(Permissions.Color.COLOR + "." + TextDecoration.BOLD.name().toLowerCase()))
			chatMessage = chatMessage.replaceLiteral("*", "\\*");

		if (!wrapped.hasPermission(Permissions.Color.COLOR + "." + TextDecoration.ITALIC.name().toLowerCase()))
			chatMessage = chatMessage.replaceLiteral("_", "\\_");

		if (Settings.Discord.REMOVE_EMOJIS_V2)
			chatMessage = chatMessage.replaceLiteral(":", "\\:");

		final Format format = Format.parse(formatName);

		chatMessage = Variables.builder(wrapped.getAudience()).placeholder("channel", this.name).replaceMessageVariables(chatMessage);

		final SimpleComponent formattedComponent = format.build(wrapped, CommonCore.newHashMap("channel", this.name, "message", chatMessage));

		return formattedComponent.toPlain(wrapped.getAudience());
	}

	/**
	 * Prepare the message to be sent from Discord
	 *
	 * @param sender
	 * @param discordMessage
	 * @return
	 */
	String prepareFromDiscordMessage(final CommandSender sender, final String discordMessage) {
		final String formatName = CommonCore.getOrDefault(this.fromDiscordFormat, Settings.Channels.FORMAT_DISCORD);

		if (formatName == null)
			return null;

		final Format format = Format.parse(formatName);
		final WrappedSender wrapped = WrappedSender.fromSender(sender);

		return format.build(wrapped, CommonCore.newHashMap("channel", this.name, "message", discordMessage)).toPlain(null);
	}

	/*
	 * Return receiver list for player, first set is visible receivers, the other set is hidden receivers
	 */
	private Tuple<Set<Player>, Set<Player>> compileReceivers(@Nullable final WrappedSender sender) {
		final Player playerSender = sender != null ? sender.getPlayer() : null;
		final boolean senderIsInArena = sender != null && sender.isPlayer() && playerSender.hasMetadata("CoreArena_Arena");

		final Set<Player> receivers = new HashSet<>();
		final Set<Player> hiddenReceivers = new HashSet<>();

		boolean rangeBypass = false;

		// Global chat if below limit
		if (this.minPlayersForRange != null && Remain.getOnlinePlayers().size() < this.minPlayersForRange)
			rangeBypass = true;

		for (final Player receiver : this.getOnlinePlayers().keySet()) {
			final PlayerCache receiverCache = PlayerCache.fromCached(receiver);

			if (!senderIsInArena && receiver.hasMetadata("CoreArena_Arena") && !Settings.CoreArena.SEND_CHANNEL_MESSAGES)
				continue;

			if (playerSender != null) {

				// We'll add the player later
				if (receiver.getName().equals(playerSender.getName()))
					continue;

				if (Settings.Channels.ENABLED && Settings.Channels.IGNORE_WORLDS.contains(receiver.getWorld().getName()))
					continue;

				if (this.range != null && !rangeBypass && !this.isInRange(receiver, playerSender))
					continue;

				if (this.party != null && !this.party.isInParty(receiver, playerSender))
					continue;
			}

			if (Settings.Spy.APPLY_ON.contains(Spy.Type.CHAT) && receiverCache.isSpyingChannel(this) && !receiverCache.isInChannel(this.getName()))
				continue;

			if (receiverCache.hasToggledPartOff(ToggleType.CHAT))
				continue;

			// Prevent seeing vanished players
			if (PlayerUtil.isVanished(receiver, playerSender) || receiver.getGameMode().toString().equals("SPECTATOR")) {
				hiddenReceivers.add(receiver);

				continue;
			}

			receivers.add(receiver);

		}

		final Set<Player> visibleReceivers = new HashSet<>(receivers);

		// Remove vanished or spying players
		visibleReceivers.removeAll(hiddenReceivers);

		return new Tuple<>(visibleReceivers, hiddenReceivers);
	}

	/**
	 * Process and broadcast incoming proxy message
	 *
	 * @param senderName
	 * @param senderUid
	 * @param formattedMessage
	 * @param consoleLog
	 * @param muteBypass
	 * @param ignoreBypass
	 * @param logBypass
	 */
	public void processProxyMessage(final String senderName, final UUID senderUid, SimpleComponent formattedMessage, final String consoleLog, final boolean muteBypass, final boolean ignoreBypass, final boolean logBypass) {
		if (formattedMessage.toPlain(null).trim().isEmpty())
			return;

		final Tuple<Set<Player>, Set<Player>> tuple = this.compileReceivers(null);

		final Set<Player> receivers = tuple.getKey();
		final Set<Player> hiddenReceivers = tuple.getValue();

		// Remove those who ignore the sender
		if (Settings.Ignore.ENABLED && Settings.Ignore.HIDE_CHAT && !ignoreBypass && !senderUid.equals(CommonCore.ZERO_UUID)) {
			final SyncedCache syncedCache = SyncedCache.fromUniqueId(senderUid);

			final Predicate<Player> filter = receiver -> {
				final PlayerCache receiverCache = PlayerCache.fromCached(receiver);

				return receiverCache.isIgnoringPlayer(senderUid) || receiverCache.hasToggledPartOff(ToggleType.CHAT)
						|| Settings.Ignore.BIDIRECTIONAL && syncedCache != null && syncedCache.isIgnoringPlayer(receiver.getUniqueId());
			};

			receivers.removeIf(filter);
			hiddenReceivers.removeIf(filter);
		}

		// Avoid sending doubled message to sender himself
		final Predicate<Player> filter = recipient -> recipient.getUniqueId().equals(senderUid);
		receivers.removeIf(filter);
		hiddenReceivers.removeIf(filter);

		if (Settings.Mute.ENABLED && (ServerSettings.getInstance().isMuted() || (ServerSettings.isProxyLoaded() && ServerSettings.getProxy().isMuted()) || this.isMuted()) && !muteBypass)
			return;

		final ChatChannelProxyEvent event = new ChatChannelProxyEvent(this, senderName, senderUid, formattedMessage, receivers);

		if (!Platform.callEvent(event))
			return;

		if (receivers.isEmpty() && this.range != null)
			return;

		formattedMessage = event.getFormattedMessage();

		receivers.addAll(hiddenReceivers);

		for (final Player receiver : receivers)
			Common.tell(receiver, formattedMessage);

		if (this.sound != null)
			for (final Player receiver : receivers)
				if (!receiver.getUniqueId().equals(senderUid))
					this.sound.play(receiver);

		if (!consoleLog.isEmpty())
			CommonCore.log(consoleLog);
	}

	/*
	 * Return true if the given receiver is within the range of the player
	 */
	private boolean isInRange(final Player receiver, final Player sender) {
		ValidCore.checkNotNull(this.range);

		// Include all players when range is off or has perm
		if (sender.hasPermission(Permissions.Bypass.RANGE)) {
			LogUtil.logOnce("channel-reach", "Note: Player " + sender.getName() + " write to channel '" + this.name
					+ "' that has range, but because he has '" + Permissions.Bypass.RANGE + "' permission everyone will see his message.");

			return true;
		}

		final World senderWorld = sender.getWorld();
		final World receiverWorld = receiver.getWorld();

		final boolean hasBypassRangeWorld = sender.hasPermission(Permissions.Bypass.RANGE_WORLD);
		final boolean sameWorlds = senderWorld.equals(receiverWorld);

		if (sameWorlds) {
			if ("*".equals(this.range) || hasBypassRangeWorld) {

				if (hasBypassRangeWorld)
					LogUtil.logOnce("channel-reach", "Note: Player " + sender.getName() + " wrote to channel '" + this.name
							+ "' that has range, but because he has '" + Permissions.Bypass.RANGE_WORLD + "' permission "
							+ "everyone on his world will see his message.");

				return true;
			}

		} else // Linked worlds
		if (this.rangeWorlds.contains(senderWorld.getName()) && this.rangeWorlds.contains(receiverWorld.getName()))
			return true;

		return sameWorlds ? sender.getLocation().distance(receiver.getLocation()) <= Integer.parseInt(this.range) : false;
	}

	@Override
	public String serialize() {
		return this.getName();
	}

	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj) {
		return obj instanceof Channel && ((Channel) obj).getName().equals(this.getName());
	}

	@Override
	public String toString() {
		return "Channel{" + this.name + "}";
	}

	// ------–------–------–------–------–------–------–------–------–------–------–------–
	// Classes
	// ------–------–------–------–------–------–------–------–------–------–------–------–

	/**
	 * Represents a sent result from the message
	 */
	@Getter
	@Setter
	public static class State {

		/**
		 * The sender of this message
		 */
		private final WrappedSender sender;

		/**
		 * The message to send
		 */
		private String message;

		/**
		 * The component
		 */
		private SimpleComponent component;

		/**
		 * The variables to replace
		 */
		private Variables variables;

		/**
		 * Is any rule indicating that we should cancel the event, if any?
		 */
		private boolean eventCancelled;

		/**
		 * The console format such as "[Admin] kangakro: Hello!"
		 */
		private String consoleFormat;

		/**
		 * Should we log to console manually?
		 */
		@Setter
		private boolean logToConsole;

		public void setPlaceholders(final Map<String, Object> placeholders) {
			this.variables.placeholders(placeholders);
		}

		public void setPlaceholder(final String key, final Object value) {
			this.variables.placeholder(key, value);
		}

		private State(final WrappedSender sender, final String message, final boolean logToConsole) {
			this.sender = sender;
			this.message = message;
			this.variables = Variables.builder(sender.getAudience());
			this.logToConsole = logToConsole;
		}

		public static final State from(final WrappedSender sender, final String message, final boolean logToConsole) {
			return new State(sender, message, logToConsole);
		}
	}

	// ------–------–------–------–------–------–------–------–------–------–------–------–
	// Static
	// ------–------–------–------–------–------–------–------–------–------–------–------–

	/**
	 * Run autojoin channel logic for the player
	 *
	 * @param player
	 * @param cache
	 */
	public static void autoJoin(final Player player, final PlayerCache cache) {
		final int limitRead = Settings.Channels.MAX_READ_CHANNELS.getFor(player);
		boolean save = false;

		for (final Channel channel : Channel.getChannels()) {
			final ChannelMode oldMode = channel.getChannelMode(player);

			if (cache.hasLeftChannel(channel)) {
				LogUtil.logTip("TIP: Not joining " + player.getName() + " to channel " + channel.getName() + " because he left it manually");

				continue;
			}

			for (final ChannelMode mode : ChannelMode.values()) {

				// Channel mode over limit
				if (mode == ChannelMode.WRITE && cache.getWriteChannel() != null || mode == ChannelMode.READ && cache.getChannels(ChannelMode.READ).size() >= limitRead)
					continue;

				// Permission to autojoin channels
				final String autoJoinPermission = Permissions.Channel.AUTO_JOIN.replace("{channel}", channel.getName()).replace("{mode}", mode.getKey());
				final String joinPermission = Permissions.Channel.JOIN.replace("{channel}", channel.getName()).replace("{mode}", mode.getKey());

				if (player.hasPermission(autoJoinPermission)) {

					if (mode == ChannelMode.WRITE && oldMode == ChannelMode.READ) {
						// Allow autojoin override from read to write
						cache.updateChannelMode(channel, null, false);
						save = true;

					} else if (oldMode != null) {

						LogUtil.logTip("TIP: Player " + player.getName() + " is already in channel '" + channel.getName() + "' in mode " + mode + ", not adding.");

						// Else disallow autojoin if player is already in channel
						continue;
					}

					if (!player.hasPermission(joinPermission)) {
						LogUtil.logTip("TIP Warning: Player " + player.getName() + " had " + autoJoinPermission + " but lacked " + joinPermission
								+ " so he won't be added to the '" + channel.getName() + "' channel!");

						continue;
					}

					LogUtil.logTip("TIP: Joining " + player.getName() + " to channel " + channel.getName() + " in mode " + mode + " due to '"
							+ autoJoinPermission + "' permission.  We won't join him again when he leaves channel manually.");

					channel.joinPlayer(player, mode, false);
					save = true;

					break;
				}
			}
		}

		if (save)
			cache.upsert();
	}

	/**
	 * A shortcut method to quickly check if the given sender can read the given channel.
	 *
	 * @param sender
	 * @param channel
	 * @return
	 */
	public static boolean canRead(final CommandSender sender, final Channel channel) {
		return hasPermission(sender, channel, ChannelMode.READ);
	}

	/**
	 * A shortcut method to quickly check if the given sender can read the given channel.
	 *
	 * @param sender
	 * @param channelName
	 * @return
	 */
	public static boolean canRead(final CommandSender sender, final String channelName) {
		return hasPermission(sender, channelName, ChannelMode.READ);
	}

	/**
	 * A shortcut method to quickly check if the given sender can write into the given channel.
	 *
	 * @param sender
	 * @param channel
	 * @return
	 */
	public static boolean canWriteInto(final CommandSender sender, final Channel channel) {
		return hasPermission(sender, channel, ChannelMode.WRITE);
	}

	/**
	 * A shortcut method to quickly check if the given sender can write into the given channel.
	 *
	 * @param sender
	 * @param channelName
	 * @return
	 */
	public static boolean canWriteInto(final CommandSender sender, final String channelName) {
		return hasPermission(sender, channelName, ChannelMode.WRITE);
	}

	/**
	 * A shortcut method to quickly check if the given sender has a permission to interact with the channel in said mode
	 *
	 * @param sender
	 * @param channel
	 * @param mode
	 * @return
	 */
	public static boolean hasPermission(final CommandSender sender, final Channel channel, final ChannelMode mode) {
		return hasPermission(sender, channel.getName(), mode);
	}

	/**
	 * A shortcut method to quickly check if the given sender has a permission to interact with the channel in said mode
	 *
	 * @param sender
	 * @param channelName
	 * @param mode
	 * @return
	 */
	public static boolean hasPermission(final CommandSender sender, final String channelName, final ChannelMode mode) {
		return sender.hasPermission(Permissions.Channel.JOIN.replace("{channel}", channelName).replace("{mode}", mode.getKey()));
	}

	/**
	 * Return if the given player can join at least one channel by permission
	 *
	 * @param player
	 * @return
	 */
	public static boolean canJoinAnyChannel(final Player player) {
		return !getChannelsWithJoinPermission(player).isEmpty();
	}

	/**
	 * Return list of channels the player has permission to join into
	 *
	 * @param player
	 * @return
	 */
	public static List<Channel> getChannelsWithJoinPermission(final Player player) {
		return collectChannels(Channel.getChannels(), channel -> {
			final String permission = Permissions.Channel.JOIN.replace("{channel}", channel.getName());

			for (final ChannelMode mode : ChannelMode.values())
				if (player.hasPermission(permission.replace("{mode}", mode.getKey())))
					return true;

			return false;
		});
	}

	/**
	 * Return list of channels the player has permission to leave
	 *
	 * @param player
	 * @return
	 */
	public static List<Channel> getChannelsWithLeavePermission(final Player player) {
		return collectChannels(Channel.getChannels(), channel -> player.hasPermission(Permissions.Channel.LEAVE.replace("{channel}", channel.getName())));
	}

	/**
	 * Return only those channels from the given list the player can leave
	 *
	 * @param channels
	 * @param player
	 * @return
	 */
	public static List<Channel> filterChannelsPlayerCanLeave(final Collection<Channel> channels, @Nullable final Player player) {
		return collectChannels(channels, channel -> player == null || player.hasPermission(Permissions.Channel.LEAVE.replace("{channel}", channel.getName())));
	}

	/*
	 * Return list of all channels matching the given filter
	 */
	private static List<Channel> collectChannels(final Collection<Channel> channels, final Predicate<Channel> filter) {
		return channels.stream().filter(filter).collect(Collectors.toList());
	}

	/**
	 * Load all channels, typically only called when the plugin is enabled
	 */
	public static void loadChannels() {
		loadedChannels.loadItems();
	}

	/**
	 * Return true if the channel by the given name exists
	 *
	 * @param name
	 * @return
	 */
	public static boolean isChannelLoaded(final String name) {
		return loadedChannels.isItemLoaded(name);
	}

	/**
	 * Return a channel from name or null if does not exist
	 *
	 * @param name
	 * @return
	 */
	public static Channel findChannel(@NonNull final String name) {
		return loadedChannels.findItem(name);
	}

	/**
	 * Return a channel from name or null if does not exist
	 *
	 * @param name
	 * @return
	 */
	public static Channel fromString(final String name) {
		return findChannel(name);
	}

	/**
	 * Find a channel by its discord id or null if not found
	 *
	 * @param discordId
	 * @return
	 */
	public static Channel fromDiscordId(final long discordId) {
		for (final Channel channel : getChannels())
			if (channel.getDiscordChannelId() == discordId)
				return channel;

		return null;
	}

	/**
	 * Return a list of all channels
	 *
	 * @return
	 */
	public static Collection<Channel> getChannels() {
		return loadedChannels.getItems();
	}

	/**
	 * Return a list of all channel names
	 *
	 * @return
	 */
	public static List<String> getChannelNames() {
		return loadedChannels.getItemNames();
	}
}