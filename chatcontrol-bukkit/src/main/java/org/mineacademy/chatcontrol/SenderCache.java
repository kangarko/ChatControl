package org.mineacademy.chatcontrol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.LogType;
import org.mineacademy.chatcontrol.model.Mute;
import org.mineacademy.chatcontrol.model.PlayerMessageType;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.model.db.Mail;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.operator.PlayerMessages;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.model.LimitedQueue;
import org.mineacademy.fo.model.SimpleBook;
import org.mineacademy.fo.model.Task;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.visual.VisualizedRegion;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Represents a cache that can work for any command sender,
 * such as those coming from Discord too.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class SenderCache {

	/**
	 * The internal sender map by unique ID.
	 */
	private static final Map<UUID, SenderCache> uniqueCacheMap = new HashMap<>();

	/**
	 * The sender name
	 */
	@Getter
	private final String senderName;

	/**
	 * Sender's last communication
	 */
	private final Map<LogType, Queue<Output>> lastCommunication = new HashMap<>();

	/**
	 * Stores last packets sent, caught by ProtocolLib
	 *
	 * 100 is the maximum chat line count you can view in history
	 * This is used to delete messages
	 */
	private final LimitedQueue<String> lastChatPackets = new LimitedQueue<>(100);

	/**
	 * The last time the sender has joined the server or -1 if not set
	 */
	@Getter
	@Setter
	private long lastLogin = -1;

	/**
	 * The last time the sender used sound notify successfuly
	 */
	@Getter
	@Setter
	private long lastSoundNotify = -1;

	/**
	 * If sender is player - his join location
	 */
	@Setter
	private Location joinLocation;

	/**
	 * Did the sender move from his {@link #joinLocation}
	 */
	@Getter
	@Setter
	private boolean movedFromJoin;

	/**
	 * The last sign test, null if not yet set
	 */
	@Getter
	@Setter
	@Nullable
	private String[] lastSignText;

	/**
	 * Represents a region the player is currently creating
	 */
	@Getter
	@Setter
	private VisualizedRegion createdRegion = new VisualizedRegion();

	/**
	 * Represents an unfinished mail the player writes
	 */
	@Getter
	@Setter
	private SimpleBook pendingMail;

	/**
	 * The mail this player is replying to
	 */
	@Getter
	@Setter
	private Mail pendingMailReply;

	/**
	 * Recent warning messages the sender has received
	 * Used to prevent duplicate warning messages
	 */
	@Getter
	private final Map<UUID, Long> recentWarningMessages = new HashMap<>();

	/**
	 * Was the related {@link PlayerCache} loaded from the database?
	 */
	@Getter
	@Setter
	private boolean databaseLoaded = false;

	/**
	 * Is the database currently being queried?
	 */
	@Getter
	@Setter
	private boolean queryingDatabase = false;

	/**
	 * Used for AuthMe to delay join message
	 */
	@Getter
	@Setter
	private String joinMessage;

	/**
	 * Get last reply player
	 */
	/*@Getter
	@Setter
	private String replyPlayerName;*/

	/**
	 * If conversation mode is enabled this holds the player the
	 * sender is conversing with, otherwise null as bull
	 */
	/*@Getter
	@Setter
	private String conversingPlayerName;*/

	/**
	 * When did the player chat in automode last time?
	 */
	@Getter
	@Setter
	private long lastAutoModeChat;

	/**
	 * Did we already triggered join flood feature for this player?
	 * Used to limit running commands only once.
	 */
	@Getter
	@Setter
	private boolean joinFloodActivated;

	/**
	 * The database loading task which might hang on slow db and we need to clean it manually
	 */
	@Getter
	@Setter
	private Task cacheLoadingTask;

	/**
	 * Ping proxy back that db has loaded and we can now send join message.
	 */
	@Getter
	@Setter
	private boolean pendingProxyJoinMessage;

	/**
	 * Proxy to show join message
	 *
	 * @param wrapped
	 */
	public void sendJoinMessage(final WrappedSender wrapped) {
		ValidCore.checkBoolean(this.databaseLoaded, "Cannot send join message for " + wrapped.getName() + " since db was not loaded yet!");
		ValidCore.checkNotNull(this.joinMessage, "Join message must be set!");

		if (Settings.Messages.APPLY_ON.contains(PlayerMessageType.JOIN) && (!Mute.isSomethingMutedIf(Settings.Mute.HIDE_JOINS, wrapped) || Settings.Mute.SOFT_HIDE) && !PlayerUtil.isVanished(wrapped.getPlayer()))
			Platform.runTask(Settings.Messages.DEFER_JOIN_MESSAGE_BY.getTimeTicks(), () -> PlayerMessages.broadcast(PlayerMessageType.JOIN, wrapped, this.joinMessage));

		if (Settings.Proxy.ENABLED)
			this.pendingProxyJoinMessage = true;
	}

	/**
	 * Return the last chat message, or null if not yet registered
	 *
	 * @return
	 */
	@Nullable
	public String getLastChatMessage() {
		final Output lastChatOutput = this.getLastChat();

		return lastChatOutput == null ? null : lastChatOutput.getOutput();
	}

	/**
	 * Return the last chat output
	 * @return
	 */
	@Nullable
	public Output getLastChat() {
		final List<Output> lastOutputs = this.getLastOutputs(LogType.CHAT, 1, null);

		return lastOutputs.isEmpty() ? null : lastOutputs.get(0);
	}

	/**
	 * Retrieve a list of the last X amount of outputs the sender has issued
	 *
	 * @param type
	 * @param amountInHistory
	 * @param channel
	 * @return
	 */
	public List<Output> getLastOutputs(final LogType type, final int amountInHistory, @Nullable final Channel channel) {
		return this.filterOutputs(type, channel, amountInHistory, null);
	}

	/**
	 * Retrieve a list of all outputs issued on or after the given date
	 *
	 * @param type
	 * @param timestamp
	 * @param channel
	 * @return
	 */
	public List<Output> getOutputsAfter(final LogType type, final long timestamp, @Nullable final Channel channel) {
		return this.filterOutputs(type, channel, -1, output -> output.getTime() >= timestamp);
	}

	/*
	 * Return a list of inputs by the given type, if the type is chat then also from the given channel,
	 * maximum of the given limit and matching the given filter
	 */
	private List<Output> filterOutputs(final LogType type, @Nullable final Channel channel, final int limit, @Nullable final Predicate<Output> filter) {
		final Queue<Output> allOutputs = this.lastCommunication.get(type);
		final List<Output> listedOutputs = new ArrayList<>();

		if (allOutputs != null) {
			final Output[] outputArray = allOutputs.toArray(new Output[allOutputs.size()]);

			// Start from the last output
			for (int i = allOutputs.size() - 1; i >= 0; i--) {
				final Output output = outputArray[i];

				// Return if channels set but not equal
				if (output == null)
					continue;

				if (output.getChannel() != null && channel != null && !output.getChannel().equals(channel.getName()))
					continue;

				if (limit != -1 && listedOutputs.size() >= limit)
					break;

				if (filter != null && !filter.test(output))
					break;

				listedOutputs.add(output);
			}
		}

		// Needed to reverse the entire list now
		Collections.reverse(listedOutputs);

		return listedOutputs;
	}

	/**
	 * Cache the given chat message from the given channel
	 *
	 * @param input
	 * @param channel
	 */
	public void cacheMessage(final String input, final Channel channel) {
		this.record(LogType.CHAT, input, channel);
	}

	/**
	 * Cache the given command
	 *
	 * @param input
	 */
	public void cacheCommand(final String input) {
		this.record(LogType.COMMAND, input, null);
	}

	/*
	 * Internal caching handler method
	 */
	private void record(final LogType type, final String input, @Nullable final Channel channel) {
		final Queue<Output> queue = this.lastCommunication.getOrDefault(type, new LimitedQueue<>(100));
		final Output record = new Output(System.currentTimeMillis(), input, channel == null ? null : channel.getName());

		queue.add(record);
		this.lastCommunication.put(type, queue);
	}

	/**
	 * Get the last chat packets
	 *
	 * @return
	 */
	public LimitedQueue<String> getLastChatPackets() {
		synchronized (this.lastChatPackets) {
			return this.lastChatPackets;
		}
	}

	/**
	 * Get the join location, throwing exception if not set
	 *
	 * @return the joinLocation
	 */
	public Location getJoinLocation() {
		ValidCore.checkBoolean(this.hasJoinLocation(), "Join location has not been set!");

		return this.joinLocation;
	}

	/**
	 * Return if the join location has been set
	 * @return
	 */
	public boolean hasJoinLocation() {
		return this.joinLocation != null;
	}

	/**
	 * Return if the sender is conversing with another player
	 *
	 * @return
	 */
	/*public boolean hasConversingPlayer() {
		return this.conversingPlayerName != null;
	}*/

	/**
	 * Convert the sender name to a player is online
	 *
	 * @return
	 */
	public Player toPlayer() {
		return Bukkit.getPlayerExact(this.senderName);
	}

	// ------------------------------------------------------------------------------------------------------------
	// Static
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Return all caches stored in memory
	 *
	 * @return
	 */
	public static Iterator<SenderCache> getCaches() {
		synchronized (uniqueCacheMap) {
			return uniqueCacheMap.values().iterator();
		}
	}

	/**
	 * Retrieve (or create) a sender cache
	 *
	 * @param sender
	 * @return
	 */
	public static SenderCache from(final CommandSender sender) {
		return from(Platform.toPlayer(sender).getUniqueId(), sender.getName());
	}

	/**
	 * Retrieve (or create) a sender cache
	 *
	 * @param wrapped
	 * @return
	 */
	public static SenderCache from(final WrappedSender wrapped) {
		return from(wrapped.getUniqueId(), wrapped.getName());
	}

	/**
	 * Retrieve (or create) a sender cache
	 *
	 * @param senderUid
	 * @param senderName
	 * @return
	 */
	public static SenderCache from(final UUID senderUid, final String senderName) {
		synchronized (uniqueCacheMap) {
			SenderCache cache = uniqueCacheMap.get(senderUid);

			if (cache == null) {
				cache = new SenderCache(senderName);

				uniqueCacheMap.put(senderUid, cache);
			}

			return cache;
		}
	}

	/**
	 * Remove the sender cache for the given player
	 *
	 * @param player
	 */
	public static void remove(Player player) {
		synchronized (uniqueCacheMap) {
			uniqueCacheMap.remove(player.getUniqueId());
		}
	}

	// ------------------------------------------------------------------------------------------------------------
	// Classes
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Represents a given senders output
	 */
	@Getter
	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	public static final class Output {

		/**
		 * The default output with -1 time and a blank message
		 */
		public static final Output NO_OUTPUT = new Output(-1, "", "");

		/**
		 * The time the message was sent
		 */
		private final long time;

		/**
		 * The message content
		 */
		private final String output;

		/**
		 * Message channel or null if not associated (such as for commands)
		 */
		@Nullable
		private final String channel;

		/**
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return this.time + " '" + this.output + "' " + this.channel;
		}
	}
}
