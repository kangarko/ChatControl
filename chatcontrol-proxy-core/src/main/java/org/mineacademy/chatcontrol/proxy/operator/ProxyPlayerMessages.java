package org.mineacademy.chatcontrol.proxy.operator;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.mineacademy.chatcontrol.model.PlayerMessageType;
import org.mineacademy.chatcontrol.proxy.operator.ProxyOperator.OperatorCheck;
import org.mineacademy.chatcontrol.proxy.operator.ProxyPlayerMessage.PlayerMessageCheck;
import org.mineacademy.chatcontrol.proxy.settings.ProxySettings;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.model.RuleSetReader;
import org.mineacademy.fo.platform.FoundationPlayer;
import org.mineacademy.fo.platform.Platform;

import lombok.Getter;
import lombok.NonNull;

/**
 * Represents the core engine for player message broadcasting
 */
public final class ProxyPlayerMessages extends RuleSetReader<ProxyPlayerMessage> {

	@Getter
	private static final ProxyPlayerMessages instance = new ProxyPlayerMessages();

	/**
	 * The loaded items sorted by group
	 */
	private final Map<PlayerMessageType, List<ProxyPlayerMessage>> messages = new HashMap<>();

	/*
	 * Create this class
	 */
	private ProxyPlayerMessages() {
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
	}

	/**
	 * @see org.mineacademy.vfo.model.RuleSetReader#createRule(java.io.File, java.lang.String)
	 */
	@Override
	protected ProxyPlayerMessage createRule(final File file, final String value) {
		final PlayerMessageType type = PlayerMessageType.fromKey(FileUtil.getFileName(file));

		return new ProxyJoinQuitKickMessage(type, value);

	}

	/**
	 * Attempt to find a rule by name
	 *
	 * @param type
	 * @param group
	 *
	 * @return
	 */
	public ProxyPlayerMessage findMessage(final PlayerMessageType type, final String group) {
		for (final ProxyPlayerMessage item : this.getMessages(type))
			if (item.getUniqueName().equalsIgnoreCase(group))
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
		return CommonCore.convertSet(this.getMessages(type), ProxyPlayerMessage::getUniqueName);
	}

	/**
	 * Return all player message that are also enabled in Apply_On in settings
	 *
	 * @param type
	 * @return
	 */
	public Set<String> getEnabledMessageNames(final PlayerMessageType type) {
		return CommonCore.convertSet(this.getMessages(type).stream().filter(message -> ProxySettings.Messages.APPLY_ON.contains(message.getType())).collect(Collectors.toList()), ProxyPlayerMessage::getUniqueName);
	}

	/**
	 * Return immutable collection of all loaded broadcasts
	 *
	 * @param type
	 * @param <T>
	 *
	 * @return
	 */
	public <T extends ProxyPlayerMessage> List<T> getMessages(final PlayerMessageType type) {
		return (List<T>) Collections.unmodifiableList(this.messages.get(type));
	}

	// ------------------------------------------------------------------------------------------------------------
	// Static
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Broadcast the given message type from the given sender and the original message
	 * ONLY if it's enabled through settings.
	 *
	 * @param type
	 * @param audience
	 * @param placeholders
	 */
	public static void broadcast(final PlayerMessageType type, @NonNull final FoundationPlayer audience, final Map<String, Object> placeholders) {
		synchronized (instance) {
			if (ProxySettings.Messages.APPLY_ON.contains(type)) {
				final OperatorCheck<?> check = new JoinQuitKickCheck(type, audience, placeholders);

				if (type == PlayerMessageType.JOIN)
					Platform.runTask(ProxySettings.Messages.DEFER_JOIN_MESSAGE_BY.getTimeTicks(), () -> check.start());

				else
					check.start();
			}
		}
	}

	// ------------------------------------------------------------------------------------------------------------
	// Classes
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Represents a singular broadcast
	 */
	public static final class JoinQuitKickCheck extends PlayerMessageCheck<ProxyPlayerMessage> {

		private final List<ProxyPlayerMessage> messages;

		/*
		 * Create new constructor with handy objects
		 */
		private JoinQuitKickCheck(final PlayerMessageType type, final FoundationPlayer audience, final Map<String, Object> placeholders) {
			super(type, audience, placeholders);

			this.messages = ProxyPlayerMessages.getInstance().getMessages(type);
		}

		/**
		 * @see ProxyOperator.OperatorCheck#getOperators()
		 */
		@Override
		public List<ProxyPlayerMessage> getOperators() {
			return this.messages;
		}
	}
}
