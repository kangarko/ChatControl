package org.mineacademy.chatcontrol.operator;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.api.PlayerMessageEvent;
import org.mineacademy.chatcontrol.model.PlayerMessageType;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.operator.DeathMessage.DeathMessageCheck;
import org.mineacademy.chatcontrol.operator.Operator.OperatorCheck;
import org.mineacademy.chatcontrol.operator.PlayerMessage.PlayerMessageCheck;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.RuleSetReader;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.model.Task;
import org.mineacademy.fo.platform.Platform;

import lombok.Getter;

/**
 * Represents the core engine for player message broadcasting
 */
public final class PlayerMessages extends RuleSetReader<PlayerMessage> {

	@Getter
	private static final PlayerMessages instance = new PlayerMessages();

	/**
	 * The loaded items sorted by group
	 */
	private final Map<PlayerMessageType, List<PlayerMessage>> messages = new HashMap<>();

	/**
	 * The task responsible for sending timed message broadcasts
	 */
	private Task broadcastTask;

	/*
	 * Create this class
	 */
	private PlayerMessages() {
		super("group");
	}

	/**
	 * Reloads the content of this class.
	 */
	@Override
	public void load() {
		this.messages.clear();

		for (final PlayerMessageType type : PlayerMessageType.values())
			if (type.getPlatform().contains(Platform.getType()))
				this.messages.put(type, this.loadFromFile("messages/" + type.getKey() + ".rs"));

		this.setupTimedTask();
	}

	/*
	 * Reschedule the timed message broadcasting task
	 */
	private void setupTimedTask() {
		if (this.broadcastTask != null)
			this.broadcastTask.cancel();

		// Re/schedule
		final SimpleTime delay = Settings.Messages.TIMED_DELAY;

		if (Settings.Messages.APPLY_ON.contains(PlayerMessageType.TIMED))
			this.broadcastTask = Platform.runTaskTimer(delay.getTimeTicks(), PlayerMessages::broadcastTimed);
	}

	/**
	 * @see org.mineacademy.fo.model.RuleSetReader#createRule(java.io.File, java.lang.String)
	 */
	@Override
	protected PlayerMessage createRule(final File file, final String value) {

		final PlayerMessageType type = PlayerMessageType.fromKey(FileUtil.getFileName(file));

		if (type == PlayerMessageType.DEATH)
			return new DeathMessage(value);

		else if (type == PlayerMessageType.TIMED)
			return new TimedMessage(value);

		else if (type == PlayerMessageType.JOIN || type == PlayerMessageType.KICK || type == PlayerMessageType.QUIT)
			return new JoinQuitKickMessage(type, value);

		throw new FoException("Unrecognized message type " + type);
	}

	/**
	 * Attempt to find a rule by name
	 * @param type
	 *
	 * @param group
	 * @return
	 */
	public PlayerMessage findMessage(final PlayerMessageType type, final String group) {
		for (final PlayerMessage item : this.getMessages(type))
			if (item.getGroup().equalsIgnoreCase(group))
				return item;

		return null;
	}

	/**
	 * Return all player message names
	 * @param type
	 *
	 * @return
	 */
	public Set<String> getMessageNames(final PlayerMessageType type) {
		return CommonCore.convertSet(this.getMessages(type), PlayerMessage::getGroup);
	}

	/**
	 * Return all player message that are also enabled in Apply_On in settings
	 *
	 * @param type
	 * @return
	 */
	public Set<String> getEnabledMessageNames(final PlayerMessageType type) {
		return CommonCore.convertSet(this.getMessages(type).stream().filter(message -> Settings.Messages.APPLY_ON.contains(message.getType())).collect(Collectors.toList()), PlayerMessage::getGroup);
	}

	/**
	 * Return immutable collection of all loaded broadcasts
	 * @param type
	 * @param <T>
	 *
	 * @return
	 */
	public <T extends PlayerMessage> List<T> getMessages(final PlayerMessageType type) {
		return (List<T>) Collections.unmodifiableList(this.messages.getOrDefault(type, new ArrayList<>()));
	}

	/* ------------------------------------------------------------------------------- */
	/* Static */
	/* ------------------------------------------------------------------------------- */

	/*
	 * Broadcast timed message
	 */
	private static <T extends PlayerMessage> void broadcastTimed() {
		broadcast(PlayerMessageType.TIMED, null, "");
	}

	/**
	 * Broadcast the given message type from the given sender and the original message
	 *
	 * @param type
	 * @param wrappedSender
	 * @param originalMessage
	 */
	public static void broadcast(final PlayerMessageType type, @Nullable final WrappedSender wrappedSender, final String originalMessage) {
		synchronized (instance) {
			final OperatorCheck<?> check;

			if (type == PlayerMessageType.DEATH) {
				ValidCore.checkNotNull(wrappedSender, "Wrapped sender cannot be null for death messages");

				check = new DeathMessageCheck(wrappedSender, originalMessage);
			}

			else if (type == PlayerMessageType.TIMED)
				check = new TimedMessagesCheck();

			else {
				ValidCore.checkNotNull(wrappedSender, "Wrapped sender cannot be null for join/kick/quit messages");

				check = new JoinQuitKickCheck(type, wrappedSender, originalMessage);
			}

			if (Platform.callEvent(new PlayerMessageEvent(wrappedSender != null ? wrappedSender.getPlayer() : null, type, check, originalMessage)))
				try {
					check.start();
				} catch (final Throwable t) {
					CommonCore.error(t, "Failed to broadcast " + type + " message for sender " + wrappedSender + " with original message: " + originalMessage);
				}
		}
	}

	/**
	 * Force run the message as broadcast, without calling API event.
	 *
	 * @param wrapped
	 * @param message
	 */
	public static void run(final WrappedSender wrapped, final PlayerMessage message) {
		final FauxMessageCheck check = new FauxMessageCheck(wrapped.isPlayer() ? wrapped : null, message);

		check.start();
	}

	/* ------------------------------------------------------------------------------- */
	/* Classes */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Represents a singular broadcast
	 */
	public static final class JoinQuitKickCheck extends PlayerMessageCheck<PlayerMessage> {

		private final List<PlayerMessage> messages;

		/*
		 * Create new constructor with handy objects
		 */
		private JoinQuitKickCheck(final PlayerMessageType type, final WrappedSender wrapped, final String originalMessage) {
			super(type, wrapped, originalMessage);

			ValidCore.checkBoolean(type != PlayerMessageType.DEATH, "For death messages use separate class");
			this.messages = PlayerMessages.getInstance().getMessages(type);
		}

		/**
		 * @see org.mineacademy.chatcontrol.operator.Operator.OperatorCheck#getOperators()
		 */
		@Override
		public List<PlayerMessage> getOperators() {
			return this.messages;
		}

		@Override
		protected WrappedSender getMessagePlayerForVariables() {
			return this.wrappedSender;
		}
	}

	/**
	 * Represents timed broadcaster check
	 */
	public static final class TimedMessagesCheck extends PlayerMessageCheck<PlayerMessage> {

		private final List<PlayerMessage> messages;

		/*
		 * Create new constructor with handy objects
		 */
		private TimedMessagesCheck() {
			super(PlayerMessageType.TIMED, null, "");

			this.messages = PlayerMessages.getInstance().getMessages(PlayerMessageType.TIMED);
		}

		/**
		 * @see org.mineacademy.chatcontrol.operator.Operator.OperatorCheck#getOperators()
		 */
		@Override
		public List<PlayerMessage> getOperators() {
			return this.messages;
		}

		/**
		 * We need to set variables for each player separately.
		 */
		@Override
		protected void setVariablesFor(final Player receiver) {
			this.wrappedReceiver = WrappedSender.fromPlayer(receiver);
			this.wrappedSender = this.wrappedReceiver;
		}
	}

	/**
	 * A class that will force to broadcast the given message, used for testing
	 */
	public static final class FauxMessageCheck extends PlayerMessageCheck<PlayerMessage> {

		private final PlayerMessage message;

		private FauxMessageCheck(final WrappedSender wrappedReceiver, final PlayerMessage message) {
			super(message.getType(), wrappedReceiver, "");

			this.message = message;
		}

		/**
		 * @see org.mineacademy.chatcontrol.operator.Operator.OperatorCheck#getOperators()
		 */
		@Override
		public List<PlayerMessage> getOperators() {
			return Arrays.asList(this.message);
		}

		@Override
		protected boolean isDebugRun() {
			return true;
		}

		/**
		 * We need to set variables for each player separately.
		 */
		@Override
		protected void setVariablesFor(final Player receiver) {
			this.wrappedReceiver = WrappedSender.fromPlayer(receiver);
			this.wrappedSender = this.wrappedReceiver;
		}
	}
}
