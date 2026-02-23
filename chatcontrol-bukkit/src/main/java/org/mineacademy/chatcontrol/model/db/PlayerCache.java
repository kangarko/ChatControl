package org.mineacademy.chatcontrol.model.db;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.api.MuteEvent;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.ChannelMode;
import org.mineacademy.chatcontrol.model.Colors;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.PlayerMessageType;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.model.ProxyChat;
import org.mineacademy.chatcontrol.model.Spy;
import org.mineacademy.chatcontrol.model.ToggleType;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.model.db.Mail.Recipient;
import org.mineacademy.chatcontrol.operator.PlayerMessage;
import org.mineacademy.chatcontrol.operator.Tag;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.util.LogUtil;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.SerializeUtilCore.Language;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.database.Row;
import org.mineacademy.fo.database.SimpleResultSet;
import org.mineacademy.fo.database.Table;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.CompChatColor;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleBook;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.Lang;

import lombok.Getter;
import lombok.NonNull;

/**
 * Represents a cache saved to data file for living players
 */
@Getter
public final class PlayerCache extends Row {

	/**
	 * Cached database data for all players for maximum performance.
	 */
	private static Map<UUID, PlayerCache> uniqueCacheMap = new HashMap<>();

	/**
	 * The players unique id
	 */
	private final UUID uniqueId;

	/**
	 * The player name
	 */
	private final String playerName;

	/**
	 * The chat color
	 */
	private CompChatColor chatColor;

	/**
	 * The chat decoration
	 */
	private CompChatColor chatDecoration;

	/**
	 * Check if the player has manually toggled PMs, so we don't disable
	 * them back next time they log-in, when `Disabled_By_Default` is enabled.
	 */
	private boolean manuallyToggledPM;

	/**
	 * Set of channels the player has left by command to prevent
	 * autojoining again
	 */
	private Set<String> leftChannels = new HashSet<>();

	/**
	 * Set of players this player is ignoring
	 */
	private Set<UUID> ignoredPlayers = new HashSet<>();

	/**
	 * What parts of the plugin has player opted out from receiving? Such as mails etc.
	 */
	private Set<ToggleType> toggledOffParts = new HashSet<>();

	/**
	 * Set of timed message broadcast groups this player is not receiving
	 */
	private Map<PlayerMessageType, Set<String>> ignoredMessages = new HashMap<>();

	/**
	 * The player's tags, with colorless cached for performance.
	 */
	private Map<Tag.Type, String> tags = new HashMap<>();
	private final Map<Tag.Type, String> tagsColorless = new HashMap<>();

	/**
	 * Represents list of set names with their warn points
	 */
	private Map<String, Integer> warnPoints = new HashMap<>();

	/**
	 * Player channel list with channel name-mode pairs
	 */
	private Map<String, ChannelMode> channels = new HashMap<>();

	/**
	 * Data stored from rules
	 */
	private Map<String, Object> ruleData = new HashMap<>();

	/**
	 * If the player is muted, this is the unmute time in the future
	 * where he will no longer be muted
	 */
	private Long unmuteTime;

	/**
	 * The parts of the plugin the player is spying
	 */
	private Set<Spy.Type> spyingSectors = new HashSet<>();

	/**
	 * The channels the player is spying
	 */
	private Set<String> spyingChannels = new HashSet<>();

	/**
	 * Represents the email that is being sent to receiver
	 * when the sender has autoresponder on
	 */
	private Tuple<SimpleBook, Long> autoResponder;

	/**
	 * Get last reply player
	 */
	private String replyPlayerName;

	/**
	 * If conversation mode is enabled this holds the player the
	 * sender is conversing with, otherwise null as bull
	 */
	private String conversingPlayerName;

	/**
	 * Indicates when the cache was last manipulated with
	 */
	private long lastActive;

	/*
	 * Internal setting used to only save file when the player is disconnecting
	 */
	private boolean disconnecting;

	/*
	 * Create a new player cache (see at the bottom)
	 *
	 * @deprecated
	 */
	public PlayerCache(final String name, final UUID uniqueId) {
		this.playerName = name;
		this.uniqueId = uniqueId;
	}

	PlayerCache(final SimpleResultSet resultSet) throws SQLException {
		this(null, null, resultSet);
	}

	PlayerCache(final String playerName, final UUID uniqueId, final SimpleResultSet resultSet) throws SQLException {
		//super(set); This column has no ID

		// Load from columns
		this.uniqueId = uniqueId != null ? uniqueId : resultSet.getUniqueIdStrict("UUID");
		this.playerName = playerName != null ? playerName : resultSet.getStringStrict("Name");
		//final String nick = resultSet.getString("Nick"); // Not used here, only used for index
		final SerializedMap map = SerializedMap.fromObject(Language.JSON, resultSet.getString("Data"));

		this.lastActive = resultSet.getLongStrict("LastModified");

		// Load from the JSON data map
		this.chatColor = map.get("Chat_Color", CompChatColor.class);
		this.chatDecoration = map.get("Chat_Decoration", CompChatColor.class);
		this.leftChannels = map.getSet("Left_Channels", String.class);
		this.ignoredPlayers = map.getSet("Ignored_Players", UUID.class);
		this.toggledOffParts = map.getSet("Ignored_Parts", ToggleType.class);
		this.ignoredMessages = map.getMapSet("Ignored_Messages", PlayerMessageType.class, String.class);
		this.tags = map.getMap("Tags", Tag.Type.class, String.class);
		this.warnPoints = map.getMap("Warn_Points", String.class, Integer.class);

		if (Settings.PrivateMessages.DISABLED_BY_DEFAULT)
			this.manuallyToggledPM = map.getBoolean("Manually_Toggled_PMs", false);

		this.channels = map.getMap("Channels", String.class, ChannelMode.class);
		this.ruleData = map.getMap("Rule_Data", String.class, Object.class);
		this.unmuteTime = map.getLong("Unmute_Time");
		this.spyingSectors = map.getSet("Spying_Sectors", Spy.Type.class);
		this.spyingChannels = map.getSet("Spying_Channels", String.class);
		this.autoResponder = map.containsKey("Auto_Responder") ? Tuple.deserialize(map.getMap("Auto_Responder"), SimpleBook.class, Long.class) : null;
		this.conversingPlayerName = map.getString("Conversing_Player_Name");
		this.replyPlayerName = map.getString("Reply_Player_Name");

		for (final Entry<Tag.Type, String> entry : this.tags.entrySet()) {
			final Tag.Type type = entry.getKey();
			final String tag = entry.getValue();

			this.tagsColorless.put(type, SimpleComponent.fromMiniAmpersand(tag).toPlain());
		}
	}

	@Override
	public Table getTable() {
		return ChatControlTable.PLAYERS;
	}

	/**
	 * Convert the player cache to a map we save to db
	 */
	@Override
	public SerializedMap toMap() {
		return SerializedMap.fromArray(
				"UUID", this.getUniqueId(),
				"Name", this.playerName,
				"Nick", this.tagsColorless.getOrDefault(Tag.Type.NICK, ""),
				"Data", this.toDataSectionOfMap().toJson(), // test with " and '
				"LastModified", System.currentTimeMillis());
	}

	@Override
	public Tuple<String, Object> getUniqueColumn() {
		return Settings.UUID_LOOKUP ? new Tuple<>("UUID", this.getUniqueId()) : new Tuple<>("Name", this.playerName);
	}

	/**
	 * Convert the data to a map, this is a part of {@link #toMap()}
	 *
	 * @return
	 */
	public SerializedMap toDataSectionOfMap() {
		final SerializedMap data = new SerializedMap();

		data.putIfExists("Chat_Color", this.chatColor);
		data.putIfExists("Chat_Decoration", this.chatDecoration);
		data.putIfNotEmpty("Left_Channels", this.leftChannels);
		data.putIfNotEmpty("Ignored_Players", this.ignoredPlayers);
		data.putIfNotEmpty("Ignored_Parts", this.toggledOffParts);
		data.putIfNotEmpty("Ignored_Messages", this.ignoredMessages);
		data.putIfNotEmpty("Tags", this.tags);
		data.putIfNotEmpty("Warn_Points", this.warnPoints);
		data.putIfTrue("Manually_Toggled_PMs", Settings.PrivateMessages.DISABLED_BY_DEFAULT && this.manuallyToggledPM);
		data.putIfNotEmpty("Channels", this.channels);
		data.putIfNotEmpty("Rule_Data", this.ruleData);
		data.putIfExists("Unmute_Time", this.unmuteTime);
		data.putIfNotEmpty("Spying_Sectors", this.spyingSectors);
		data.putIfNotEmpty("Spying_Channels", this.spyingChannels);
		data.putIfExists("Auto_Responder", this.autoResponder);
		data.putIfExists("Conversing_Player_Name", this.conversingPlayerName);
		data.putIfExists("Reply_Player_Name", this.replyPlayerName);

		return data;
	}

	/**
	 * Load the data from the given map
	 *
	 * @param map
	 */
	public void loadDataFromDataSectionOfMap(final SerializedMap map) {
		Valid.checkBoolean(!map.containsKey("UUID"), "Expected only the 'Data' portion of map, got: " + map);

		this.chatColor = map.get("Chat_Color", CompChatColor.class);
		this.chatDecoration = map.get("Chat_Decoration", CompChatColor.class);
		this.leftChannels = map.getSet("Left_Channels", String.class);
		this.ignoredPlayers = map.getSet("Ignored_Players", UUID.class);
		this.toggledOffParts = map.getSet("Ignored_Parts", ToggleType.class);
		this.ignoredMessages = map.getMapSet("Ignored_Messages", PlayerMessageType.class, String.class);
		this.tags = map.getMap("Tags", Tag.Type.class, String.class);
		this.warnPoints = map.getMap("Warn_Points", String.class, Integer.class);
		this.manuallyToggledPM = map.getBoolean("Manually_Toggled_PMs", false);
		this.channels = map.getMap("Channels", String.class, ChannelMode.class);
		this.ruleData = map.getMap("Rule_Data", String.class, Object.class);
		this.unmuteTime = map.getLong("Unmute_Time");
		this.spyingSectors = map.getSet("Spying_Sectors", Spy.Type.class);
		this.spyingChannels = map.getSet("Spying_Channels", String.class);
		this.autoResponder = map.containsKey("Auto_Responder") ? Tuple.deserialize(map.getMap("Auto_Responder"), SimpleBook.class, Long.class) : null;
		this.conversingPlayerName = map.getString("Conversing_Player_Name");
		this.replyPlayerName = map.getString("Reply_Player_Name");

		this.tagsColorless.clear();

		for (final Entry<Tag.Type, String> entry : this.tags.entrySet()) {
			final Tag.Type type = entry.getKey();
			final String tag = entry.getValue();

			this.tagsColorless.put(type, SimpleComponent.fromMiniAmpersand(tag).toPlain());
		}
	}

	/**
	 * Processes the joining of this player
	 *
	 * @param player
	 * @param senderCache
	 */
	public void onJoin(Player player, SenderCache senderCache) {
		this.onJoin(player, senderCache, null);
	}

	/**
	 * Processes the joining of this player
	 *
	 * @param player
	 * @param senderCache
	 * @param fallbackJoinMessage
	 */
	public void onJoin(Player player, SenderCache senderCache, String fallbackJoinMessage) {
		final UUID uniqueId = player.getUniqueId();
		final Database database = Database.getInstance();

		final WrappedSender wrapped = WrappedSender.fromPlayerCaches(player, this, senderCache);

		// Force load all synced data
		ProxyChat.loadAllSyncedData(wrapped);

		// Update tablist name from nick
		Players.setTablistName(wrapped);

		// Remove old channels over limit
		this.checkLimits(player);

		// Auto join channels
		if (Settings.Channels.ENABLED)
			Channel.autoJoin(player, this);

		// Motd
		if (Settings.Motd.ENABLED) {
			Players.showMotd(wrapped, true);

			Platform.runTask(Settings.Motd.DELAY.getTimeTicks(), () -> {
				for (final String consoleCmd : Settings.Motd.CONSOLE_COMMANDS)
					Platform.dispatchConsoleCommand(wrapped.getAudience(), consoleCmd);

				for (final String playerCmd : Settings.Motd.PLAYER_COMMANDS)
					wrapped.getAudience().dispatchCommand(playerCmd);
			});
		}

		if (Settings.PrivateMessages.DISABLED_BY_DEFAULT && !this.hasManuallyToggledPMs()) {
			this.setToggledPart(ToggleType.PRIVATE_MESSAGE, true);

			LogUtil.logTip("TIP: " + player.getName() + " did not manually toggle on private messages. He will not be able to send/receive them until he toggles them back on.");
		}

		// Spying
		if (player.hasPermission(Permissions.Spy.AUTO_ENABLE) && !Settings.Spy.APPLY_ON.isEmpty()) {
			this.setSpyingOn(player);

			if (!Lang.plain("command-spy-auto-enable-1").equals("none"))
				wrapped.getAudience().sendMessage(Lang
						.component("command-spy-auto-enable-1")
						.append(Lang.component("command-spy-auto-enable-2"))
						.onHover(Lang.component("command-spy-auto-enable-tooltip", "permission", Permissions.Spy.AUTO_ENABLE)));

			LogUtil.logOnce("spy-autojoin", "TIP: Automatically enabling spy mode for " + player.getName() + " because he has '" + Permissions.Spy.AUTO_ENABLE + "'"
					+ " permission. To stop automatically enabling spy mode for players, give them negative '" + Permissions.Spy.AUTO_ENABLE + "' permission"
					+ " (a value of false when using LuckPerms).");
		}

		// Unread mail notification
		if (Settings.Mail.ENABLED && player.hasPermission(Permissions.Command.MAIL))
			Platform.runTaskAsync(() -> {
				int unreadCount = 0;

				for (final Mail mail : database.findMailsTo(uniqueId)) {
					final Recipient recipient = mail.findRecipient(uniqueId);

					if (!recipient.isMarkedDeleted() && !recipient.isOpened())
						unreadCount++;
				}

				if (unreadCount > 0) {
					final int finalUnreadCount = unreadCount;

					Platform.runTask(4, () -> Messenger.warn(player, Lang.component("command-mail-join-notification", "amount", finalUnreadCount)));
				}
			});

		senderCache.setJoinMessage(CommonCore.getOrEmpty(fallbackJoinMessage));
		senderCache.sendJoinMessage(wrapped);
	}

	/*
	 * Check if player has not exceeded any cache limits and disable appropriately
	 */
	private void checkLimits(final Player player) {
		boolean save = false;

		// Check if player still has permissions for their custom color/decoration
		// removing them if not
		if (this.hasChatColor() && !player.hasPermission(Colors.getReadableGuiColorPermission(player, this.chatColor))) {
			LogUtil.logTip("TIP Alert: Removing chat color due to lost permission");

			this.setChatColorNoSave(null);
			save = true;
		}

		if (this.hasChatDecoration() && !player.hasPermission(Colors.getReadableGuiColorPermission(player, this.chatDecoration))) {
			LogUtil.logTip("TIP Alert: Removing chat decoration due to lost permission");

			this.setChatDecorationNoSave(null);
			save = true;
		}

		if (!this.ignoredPlayers.isEmpty() && !player.hasPermission(Permissions.Command.IGNORE)) {
			LogUtil.logTip("TIP Alert: Removing ignored players due to lost permission");

			this.ignoredPlayers.clear();
			save = true;
		}

		for (final Iterator<Spy.Type> it = this.spyingSectors.iterator(); it.hasNext();) {
			final Spy.Type spyType = it.next();

			if (!player.hasPermission(spyType.getPermission())) {
				LogUtil.logTip("TIP Alert: Stopping spying " + spyType + " due to lost permission");

				if (spyType == Spy.Type.CHAT)
					this.spyingChannels.clear();

				it.remove();
				save = true;
			}
		}

		// Check max write/read channel limits and remove if over them
		int readChannels = 0;
		int writeChannels = 0;
		final int maxReadChannels = Settings.Channels.MAX_READ_CHANNELS.getFor(player);

		for (final Iterator<Entry<String, ChannelMode>> it = this.channels.entrySet().iterator(); it.hasNext();) {
			final Entry<String, ChannelMode> entry = it.next();
			final String otherChannel = entry.getKey();
			final ChannelMode otherMode = entry.getValue();

			final Channel channelInstance = Channel.findChannel(otherChannel);

			// Skip channels not installed
			if (channelInstance == null) {
				LogUtil.logTip("TIP Alert: Supposed to add " + player.getName() + " to channel '" + otherChannel + "' which is not installed on this server, skipping");

				continue;
			}

			final String joinPermission = Permissions.Channel.JOIN.replace("{channel}", otherChannel).replace("{mode}", otherMode.getKey());

			if (!player.hasPermission(joinPermission)) {
				LogUtil.logTip("TIP Alert: Removing channel " + otherChannel + " in mode " + otherMode + " due to not having " + joinPermission + " permission");

				it.remove();
				save = true;

				continue;
			}

			if (otherMode == ChannelMode.WRITE && ++writeChannels > 1) {
				LogUtil.logTip("TIP Alert: Removing channel " + otherChannel + " in mode " + otherMode + " due to having another channel on write already (hard limit is 1)");

				it.remove();
				save = true;

				continue;
			}

			if (otherMode == ChannelMode.READ && ++readChannels > maxReadChannels) {
				LogUtil.logTip("TIP Alert: Removing channel " + otherChannel + " in mode " + otherMode + " due to having another channels on read already (player-specific limit is " + maxReadChannels + ")");

				it.remove();
				save = true;
			}

			LogUtil.logTip("TIP: Joining player to his saved channel " + otherChannel + " in mode " + otherMode);
		}

		if (save)
			this.upsert();
	}

	/* ------------------------------------------------------------------------------- */
	/* Methods */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Set the value of the toggle chat
	 *
	 * @param manuallyToggledPM
	 */
	public void setManuallyToggledPMs(final boolean manuallyToggledPM) {
		this.manuallyToggledPM = manuallyToggledPM;
	}

	/**
	 * Return if the chat has been manually toggled for the first time
	 *
	 * @return
	 */
	public boolean hasManuallyToggledPMs() {
		return this.manuallyToggledPM;
	}

	/**
	 * Return if the chat color has been set
	 *
	 * @return
	 */
	public boolean hasChatColor() {
		return this.chatColor != null;
	}

	/**
	 * Return if the chat decoration has been set
	 *
	 * @return
	 */
	public boolean hasChatDecoration() {
		return this.chatDecoration != null;
	}

	/**
	 * Set a new chat color
	 *
	 * @param chatColor
	 */
	public void setChatColorNoSave(@Nullable final CompChatColor chatColor) {
		this.chatColor = chatColor;
	}

	/**
	 * Set a new chat decoration
	 *
	 * @param chatDecoration
	 */
	public void setChatDecorationNoSave(@Nullable final CompChatColor chatDecoration) {
		this.chatDecoration = chatDecoration;
	}

	/**
	 * Return true if the given unique ID is being ignored
	 *
	 * @param uniqueId
	 * @return
	 */
	public boolean isIgnoringPlayer(final UUID uniqueId) {
		return Settings.Ignore.ENABLED && this.ignoredPlayers.contains(uniqueId);
	}

	/**
	 * Set the given player to be ignored or not
	 *
	 * @param uniqueId
	 * @param ignored
	 */
	public void setIgnoredPlayer(final UUID uniqueId, final boolean ignored) {
		if (ignored)
			this.ignoredPlayers.add(uniqueId);
		else
			this.ignoredPlayers.remove(uniqueId);

		this.upsert();
	}

	/**
	 * Return true if the given part is being ignored
	 *
	 * @param type
	 * @return
	 */
	public boolean hasToggledPartOff(final ToggleType type) {
		return this.toggledOffParts.contains(type);
	}

	/**
	 * Set the given toggle to be ignored or not
	 *
	 * @param type
	 * @param state
	 */
	public void setToggledPart(final ToggleType type, final boolean state) {
		if (state)
			this.toggledOffParts.add(type);
		else
			this.toggledOffParts.remove(type);

		this.upsert();
	}

	/**
	 * Did the player use /ch leave command to leave the channel?
	 *
	 * @param channel
	 * @return
	 */
	public boolean hasLeftChannel(final Channel channel) {
		return this.leftChannels.contains(channel.getName());
	}

	/**
	 * Mark the given channel as left
	 *
	 * @param channel
	 */
	public void markLeftChannel(final Channel channel) {
		this.leftChannels.add(channel.getName());

		this.upsert();
	}

	/**
	 * Return if this player is ignoring the given broadcast all messages
	 *
	 * @param type
	 * @return
	 */
	public boolean isIgnoringMessages(final PlayerMessageType type) {
		final Set<String> messages = this.ignoredMessages.getOrDefault(type, new HashSet<>());

		return messages.contains("*");
	}

	/**
	 * Return if this player is ignoring the given broadcast from the given type
	 *
	 * @param message
	 * @return
	 */
	public boolean isIgnoringMessage(final PlayerMessage message) {
		final Set<String> messages = this.ignoredMessages.getOrDefault(message.getType(), new HashSet<>());

		return messages.contains(message.getGroup());
	}

	/**
	 * Sets the given broadcast as ignored or not
	 *
	 * @param message
	 * @param ignoring
	 */
	public void setIgnoringMessage(final PlayerMessage message, final boolean ignoring) {
		final PlayerMessageType type = message.getType();
		final Set<String> messages = this.ignoredMessages.getOrDefault(type, new HashSet<>());

		if (ignoring)
			messages.add(message.getGroup());
		else
			messages.remove(message.getGroup());

		if (messages.isEmpty())
			this.ignoredMessages.remove(type);
		else
			this.ignoredMessages.put(type, messages);

		this.upsert();
	}

	/**
	 * Sets the all broadcasts of this type are ignored or not
	 *
	 * @param type
	 * @param ignoring
	 */
	public void setIgnoringMessages(final PlayerMessageType type, final boolean ignoring) {
		this.ignoredMessages.remove(type);

		if (ignoring)
			this.ignoredMessages.put(type, CommonCore.newSet("*"));

		this.upsert();
	}

	/**
	 * Return the nick without colors, or null if not set
	 * @param type
	 * @return
	 */
	public String getTagColorless(final Tag.Type type) {
		String tag = this.tagsColorless.get(type);

		if (tag == null) {
			tag = SimpleComponent.fromMiniAmpersand(this.tags.get(type)).toPlain();

			if (tag != null)
				this.tagsColorless.put(type, tag);
		}

		return tag != null && !tag.isEmpty() ? tag : null;
	}

	/**
	 * Return true if the given player tag is set
	 *
	 * @param type
	 * @return
	 */
	public boolean hasTag(final Tag.Type type) {
		final String tag = this.getTag(type);

		return tag != null && !tag.isEmpty();
	}

	/**
	 * Return a tag or null if not set
	 *
	 * @param type
	 * @return
	 */
	public String getTag(final Tag.Type type) {
		return this.tags.get(type);
	}

	/**
	 * Return a read-only map of tags
	 *
	 * @return
	 */
	public Map<Tag.Type, String> getTags() {
		final Map<Tag.Type, String> copy = new HashMap<>();

		// We have to pull in nick from other source
		for (final Tag.Type type : Tag.Type.values()) {
			final String tag = this.getTag(type);

			if (tag != null && !tag.isEmpty())
				copy.put(type, tag);
		}

		return Collections.unmodifiableMap(copy);
	}

	/**
	 * Set a tag for a player or remove it if tag is null
	 *
	 * @param type
	 * @param tag
	 */
	public void setTag(final Tag.Type type, @Nullable final String tag) {
		this.setTag(type, tag, true);
	}

	/**
	 * Set a tag for a player or remove it if tag is null
	 *
	 * @param type
	 * @param tag
	 * @param reportToOtherPlugins
	 */
	public void setTag(final Tag.Type type, @Nullable final String tag, final boolean reportToOtherPlugins) {
		ValidCore.checkBoolean(tag == null || !tag.trim().isEmpty(), "Cannot save an empty tag, to remove it, set it to null");

		if (tag != null) {
			this.tags.put(type, tag);
			this.tagsColorless.put(type, SimpleComponent.fromMiniAmpersand(tag).toPlain());

		} else {
			this.tags.remove(type);
			this.tagsColorless.remove(type);
		}

		if (type == Tag.Type.NICK && Settings.Tag.APPLY_ON.contains(Tag.Type.NICK) && reportToOtherPlugins)
			// Hook into other plugins
			HookManager.setNick(this.getUniqueId(), tag);

		this.upsert();
	}

	/**
	 * Return a set's warning points or 0
	 *
	 * @param warnSet
	 * @return
	 */
	public int getWarnPoints(final String warnSet) {
		return this.warnPoints.getOrDefault(warnSet, 0);
	}

	/**
	 * Set warning points of a set to the given amount
	 *
	 * @param warnSet
	 * @param points
	 */
	public void setWarnPointsNoSave(final String warnSet, final int points) {
		if (points == 0)
			this.warnPoints.remove(warnSet);

		else
			this.warnPoints.put(warnSet, points);
	}

	/**
	 * Return immutable map of all stored warning sets with their points
	 *
	 * @return
	 */
	public Map<String, Integer> getWarnPoints() {
		return Collections.unmodifiableMap(new HashMap<>(this.warnPoints));
	}

	/**
	 * Return true if the player is in a channel
	 *
	 * @param channelName
	 * @return
	 */
	public boolean isInChannel(final String channelName) {
		return this.channels.containsKey(channelName.toLowerCase());
	}

	/**
	 * Return write channel for player or null
	 * Players may only write to one channel at a time
	 *
	 * @return
	 */
	public Channel getWriteChannel() {
		final List<Channel> list = this.getChannels(ChannelMode.WRITE);

		if (!list.isEmpty()) {

			// Force kick from multiple write channels
			if (list.size() > 1) {
				final Player player = this.toPlayer();

				if (player != null)
					this.checkLimits(player);
			}

			return list.get(0);
		}

		return null;
	}

	/**
	 * Return all channels for the given mode
	 *
	 * @param mode
	 * @return
	 */
	public List<Channel> getChannels(final ChannelMode mode) {
		final List<Channel> channels = new ArrayList<>();

		for (final Entry<Channel, ChannelMode> entry : this.getChannels().entrySet())
			if (entry.getValue() == mode)
				channels.add(entry.getKey());

		return channels;
	}

	/**
	 * Return all channels with their modes in
	 *
	 * @return
	 */
	public Map<Channel, ChannelMode> getChannels() {
		final Map<Channel, ChannelMode> map = new HashMap<>();

		for (final Entry<String, ChannelMode> entry : this.channels.entrySet()) {
			final Channel channel = Channel.findChannel(entry.getKey());

			if (channel != null)
				map.put(channel, entry.getValue());
		}

		return Collections.unmodifiableMap(map);
	}

	/**
	 * Return the mode for a channel or null if not joined
	 *
	 * @param channel
	 * @return
	 */
	public ChannelMode getChannelMode(final Channel channel) {
		return this.channels.get(channel.getName().toLowerCase());
	}

	/**
	 * Return the mode for a channel or null if not joined
	 *
	 * @param channelName
	 * @return
	 */
	public ChannelMode getChannelMode(final String channelName) {
		return this.channels.get(channelName.toLowerCase());
	}

	/**
	 * Update data file with new information about channel and its mode
	 *
	 * Internal use only! Use {@link Channel} methods as API means
	 *
	 * @param channel
	 * @param mode
	 * @param save
	 */
	public void updateChannelMode(final Channel channel, @Nullable final ChannelMode mode, final boolean save) {
		final String channelName = channel.getName().toLowerCase();

		if (mode == null)
			this.channels.remove(channelName);

		else
			this.channels.put(channelName, mode);

		if (save)
			this.upsert();
	}

	/**
	 * Return true if player has rule data
	 *
	 * @param key
	 * @return
	 */
	public boolean hasRuleData(final String key) {
		return this.ruleData.containsKey(key);
	}

	/**
	 * Get rule data
	 *
	 * @param key
	 * @return
	 */
	public Object getRuleData(final String key) {
		return this.ruleData.get(key);
	}

	/**
	 * Save the given rule data pair
	 *
	 * @param key
	 * @param object
	 */
	public void setRuleData(final String key, @Nullable final Object object) {
		if (object == null || object.toString().trim().equals("") || object.toString().equalsIgnoreCase("null"))
			this.ruleData.remove(key);

		else
			this.ruleData.put(key, object);

		this.upsert();
	}

	/**
	 * Return true if the player is muted
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
	 * Set the mute for this player
	 *
	 * @param duration how long, null to unmute
	 */
	public void setMuted(@Nullable SimpleTime duration) {
		final MuteEvent event = MuteEvent.player(this.getPlayerName(), this.getUniqueId(), duration);

		if (Platform.callEvent(event)) {
			duration = event.getDuration();
			this.unmuteTime = duration == null ? null : System.currentTimeMillis() + duration.getTimeSeconds() * 1000;

			this.upsert();
		}
	}

	/**
	 * Return if the player is spying something
	 *
	 * @return
	 */
	public boolean isSpyingSomething() {
		return !this.spyingChannels.isEmpty() || !this.spyingSectors.isEmpty();
	}

	/**
	 * Return if the player is spying something that is in the Apply_On
	 * list in settings.yml
	 *
	 * @return
	 */
	public boolean isSpyingSomethingEnabled() {
		return Settings.Spy.APPLY_ON.contains(Spy.Type.CHAT) && !this.spyingChannels.isEmpty()
				|| !this.spyingSectors.stream().filter(sector -> Settings.Spy.APPLY_ON.contains(sector)).collect(Collectors.toList()).isEmpty();
	}

	/**
	 * Disable all spying
	 */
	public void setSpyingOff() {
		this.spyingChannels.clear();
		this.spyingSectors.clear();

		this.upsert();
	}

	/**
	 * Enable all spying
	 *
	 * @param permissible
	 * @return true if at least one spying type was enabled
	 */
	public boolean setSpyingOn(@Nullable final Permissible permissible) {
		boolean atLeastOne = false;

		for (final Spy.Type type : Spy.Type.values())
			if ((permissible == null || permissible.hasPermission(Permissions.Spy.TYPE + type.getKey())) && Settings.Spy.APPLY_ON.contains(type)) {
				this.spyingSectors.add(type);

				atLeastOne = true;
			} else
				this.spyingSectors.remove(type);

		if (permissible == null || permissible.hasPermission(Permissions.Spy.TYPE + Spy.Type.CHAT.getKey())) {
			for (final Channel channel : Channel.getChannels())
				this.spyingChannels.add(channel.getName());

			atLeastOne = true;
		} else
			this.spyingChannels.clear();

		this.upsert();
		return atLeastOne;
	}

	/**
	 * Return if the player is spying the given sector
	 *
	 * @param type
	 * @return if is spying or not
	 */
	public boolean isSpying(final Spy.Type type) {
		ValidCore.checkBoolean(type != Spy.Type.CHAT, "When checking for spying channels use isSpyingChannel instead!");

		return this.spyingSectors.contains(type);
	}

	/**
	 * Update the player's spying mode
	 *
	 * @param type what game sector to spy
	 * @param spying true or false
	 */
	public void setSpying(final Spy.Type type, final boolean spying) {
		ValidCore.checkBoolean(type != Spy.Type.CHAT, "When setting spying channels use setSpyingChannel instead!");

		if (spying)
			this.spyingSectors.add(type);
		else
			this.spyingSectors.remove(type);

		this.upsert();
	}

	/**
	 * Return if the player is spying the given channel
	 *
	 * @param channel
	 * @return
	 */
	public boolean isSpyingChannel(final Channel channel) {
		return this.spyingChannels.contains(channel.getName());
	}

	/**
	 * Return if the player is spying the given channel
	 *
	 * @param channelName
	 * @return
	 */
	public boolean isSpyingChannel(final String channelName) {
		return this.spyingChannels.contains(channelName);
	}

	/**
	 * Update the player's spying mode
	 *
	 * @param channel what channel to spy
	 * @param spying true or false
	 */
	public void setSpyingChannel(final Channel channel, final boolean spying) {
		if (spying) {
			this.spyingSectors.add(Spy.Type.CHAT);
			this.spyingChannels.add(channel.getName());

		} else {
			this.spyingSectors.remove(Spy.Type.CHAT);
			this.spyingChannels.remove(channel.getName());
		}

		this.upsert();
	}

	/**
	 * Return if player has autoresponder and its expiration date is valid
	 *
	 * @return
	 */
	public boolean isAutoResponderValid() {
		return this.hasAutoResponder() && System.currentTimeMillis() < this.autoResponder.getValue();
	}

	/**
	 * Return if any (even expired) auto responder is set
	 *
	 * @return
	 */
	public boolean hasAutoResponder() {
		return this.autoResponder != null;
	}

	/**
	 * Return if the sender is conversing with another player
	 *
	 * @return
	 */
	public boolean hasConversingPlayer() {
		return this.conversingPlayerName != null;
	}

	/**
	 * Updates an autoresponder's date if {@link #hasAutoResponder()} is true
	 *
	 * @param futureExpirationDate
	 */
	public void setAutoResponderDate(final long futureExpirationDate) {
		ValidCore.checkBoolean(this.hasAutoResponder(), "Cannot update auto responder date if none is set");

		this.setAutoResponder(this.autoResponder.getKey(), futureExpirationDate);
	}

	/**
	 * Set the book autoresponder
	 *
	 * @param book
	 * @param futureExpirationDate
	 */
	public void setAutoResponder(final SimpleBook book, final long futureExpirationDate) {
		this.autoResponder = new Tuple<>(book, futureExpirationDate);

		this.upsert();
	}

	/**
	 * Set reply player name
	 * 
	 * @param replyPlayerName
	 */
	public void setReplyPlayerName(String replyPlayerName) {
		this.replyPlayerName = replyPlayerName;

		this.upsert();
	}

	/**
	 * Set conversing player name
	 * 
	 * @param conversingPlayerName
	 */
	public void setConversingPlayerName(String conversingPlayerName) {
		this.conversingPlayerName = conversingPlayerName;

		this.upsert();
	}

	/**
	 * Remove autoresponder or throw error if does not exist
	 */
	public void removeAutoResponder() {
		ValidCore.checkBoolean(this.hasAutoResponder(), "Cannot remove an auto responder player does not have");

		this.autoResponder = null;
		this.upsert();
	}

	/**
	 * Return player from cache if online or null otherwise
	 *
	 * @return
	 */
	@Nullable
	public Player toPlayer() {
		final Player player = Remain.getPlayerByUUID(this.getUniqueId());

		return player != null && player.isOnline() ? player : null;
	}

	/**
	 * Puts the loaded player cache into the player map.
	 *
	 * @deprecated internal use only
	 */
	@Deprecated
	public void putToCacheMap() {
		synchronized (uniqueCacheMap) {
			uniqueCacheMap.put(this.getUniqueId(), this);
		}
	}

	/**
	 * Return the player name
	 *
	 * @return
	 */
	public String getPlayerName() {
		ValidCore.checkNotNull(this.playerName, "Player name is null");

		return this.playerName;
	}

	/**
	 * Return the player unique id
	 *
	 * @return
	 */
	public UUID getUniqueId() {
		ValidCore.checkNotNull(this.uniqueId, "Unique id is null");

		return this.uniqueId;
	}

	@Override
	public String toString() {
		return "PlayerCache{" + this.getPlayerName() + ", " + this.getUniqueId() + "}";
	}

	@Override
	public boolean equals(final Object obj) {
		return obj instanceof PlayerCache && ((PlayerCache) obj).getPlayerName().equals(this.getPlayerName());
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.getPlayerName());
	}

	/* ------------------------------------------------------------------------------- */
	/* Staticus Belavaros - Just fucking kidding, no idea what that this family        */
	/* friendly language means                                                         */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Return the player cache for the given player or error if not found
	 *
	 * @param player
	 * @return
	 */
	public static PlayerCache fromCached(@NonNull final Player player) {
		synchronized (uniqueCacheMap) {
			final PlayerCache cache = uniqueCacheMap.get(player.getUniqueId());

			if (cache == null)
				throw new FoException("Unable to find cached database player " + player.getName() + " (db loaded? " + SenderCache.from(player).isDatabaseLoaded() + "; map: " + uniqueCacheMap + ")");

			return cache;
		}
	}

	/**
	 * Return true if the player is cached
	 *
	 * @param player
	 * @return
	 */
	public static boolean isCached(final Player player) {
		return isCached(player.getUniqueId());
	}

	/**
	 * Return true if the player is cached
	 *
	 * @param uniqueId
	 * @return
	 */
	public static boolean isCached(final UUID uniqueId) {
		synchronized (uniqueCacheMap) {
			return uniqueCacheMap.containsKey(uniqueId);
		}
	}

	/**
	 * Load the player cache from the given data map without saving
	 *
	 * @deprecated only used to migrate from v10
	 * @param uuid
	 * @param name
	 * @param map
	 * @return
	 */
	@Deprecated
	public static PlayerCache fromDataMap(final UUID uuid, final String name, final SerializedMap map) {
		final PlayerCache cache = new PlayerCache(name, uuid);

		cache.loadDataFromDataSectionOfMap(map);
		return cache;
	}

	/**
	 * Attempts to get a player cache from name or nick, from data file or database
	 * Due to blocking call we handle stuff in a synced callback
	 *
	 * @param nameOrNick
	 * @param syncCallback
	 */
	public static void poll(String nameOrNick, final Consumer<PlayerCache> syncCallback) {
		poll(nameOrNick, syncCallback, null);
	}

	/**
	 * @see #poll(String, Consumer)
	 *
	 * @param nameOrNick
	 * @param syncCallback
	 * @param entity the entity for Folia entity-thread scheduling, or null
	 */
	public static void poll(String nameOrNick, final Consumer<PlayerCache> syncCallback, @Nullable final Entity entity) {
		synchronized (uniqueCacheMap) {
			Debugger.debug("cache", "Polling player cache from name or nick '" + nameOrNick + "'");

			nameOrNick = CompChatColor.stripColorCodes(nameOrNick);

			for (final PlayerCache loaded : uniqueCacheMap.values()) {
				final String nick = Settings.Tag.APPLY_ON.contains(Tag.Type.NICK) ? loaded.getTagColorless(Tag.Type.NICK) : null;

				if (loaded.getPlayerName().equalsIgnoreCase(nameOrNick) || (nick != null && nick.equalsIgnoreCase(nameOrNick))) {
					Debugger.debug("cache", "\tFound in memory: " + loaded.getPlayerName());
					syncCallback.accept(loaded);

					return;
				}
			}

			for (final Player online : Players.getOnlinePlayersWithLoadedDb()) {
				final String nick = Settings.Tag.BACKWARD_COMPATIBLE ? HookManager.getNickOrNullColorless(online) : null;

				if (nameOrNick.equalsIgnoreCase(online.getName()) || (nick != null && nameOrNick.equalsIgnoreCase(nick))) {
					Debugger.debug("cache", "\tFound online: " + online.getName());
					syncCallback.accept(PlayerCache.fromCached(online));

					return;
				}
			}

			final String finalNameOrNick = nameOrNick;

			Debugger.debug("cache", "\tNot found, querying database");

			Platform.runTaskAsync(() -> {
				final PlayerCache cache = Database.getInstance().getCache(finalNameOrNick);

				if (entity != null && Remain.isFolia())
					Remain.runEntityTask(entity, 0, () -> syncCallback.accept(cache));
				else
					Platform.runTask(() -> syncCallback.accept(cache));
			});
		}
	}

	/**
	 * Attempts to get a player cache from name or nick, from data file or database
	 * Due to blocking call we handle stuff in a synced callback
	 *
	 * @param syncCallback
	 */
	public static void pollAll(final Consumer<List<PlayerCache>> syncCallback) {
		pollAll(syncCallback, null);
	}

	/**
	 * @see #pollAll(Consumer)
	 *
	 * @param syncCallback
	 * @param entity the entity for Folia entity-thread scheduling, or null
	 */
	public static void pollAll(final Consumer<List<PlayerCache>> syncCallback, @Nullable final Entity entity) {
		Valid.checkSync("Polling cache must be called sync!");

		Platform.runTaskAsync(() -> {
			final List<PlayerCache> caches = Database.getInstance().getRows(ChatControlTable.PLAYERS);

			Collections.sort(caches, Comparator.comparing(PlayerCache::getPlayerName));

			if (entity != null && Remain.isFolia())
				Remain.runEntityTask(entity, 0, () -> syncCallback.accept(caches));
			else
				Platform.runTask(() -> syncCallback.accept(caches));
		});
	}

	/**
	 * Remove the player from the cache map
	 *
	 * @param player
	 */
	public static void remove(final Player player) {
		synchronized (uniqueCacheMap) {
			uniqueCacheMap.remove(player.getUniqueId());
		}
	}
}
