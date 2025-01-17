package org.mineacademy.chatcontrol.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.PlayerMessageType;
import org.mineacademy.chatcontrol.model.ToggleType;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.operator.PlayerMessage;
import org.mineacademy.chatcontrol.operator.PlayerMessages;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.settings.Lang;

public final class CommandToggle extends ChatControlCommand {

	public CommandToggle() {
		super(Settings.Toggle.COMMAND_ALIASES);

		this.setValidArguments(1, 2);
		this.setPermission(Permissions.Command.TOGGLE_TYPE.substring(0, Permissions.Command.TOGGLE_TYPE.length() - 1));
		this.setUsage(Lang.component("command-toggle-usage"));
		this.setDescription(Lang.component("command-toggle-description"));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		final List<SimpleComponent> usages = new ArrayList<>();

		Collections.addAll(usages, Lang.component("command-toggle-usages-1",
				"label", this.getLabel(),
				"toggle_available", Settings.Toggle.APPLY_ON,
				"messages_available", Settings.Messages.APPLY_ON));

		if (this.canToggle(ToggleType.CHAT))
			usages.add(Lang.component("command-toggle-usages-chat"));

		if (this.canToggle(PlayerMessageType.JOIN))
			usages.add(Lang.component("command-toggle-usages-join"));

		if (this.canToggle(PlayerMessageType.TIMED))
			usages.add(Lang.component("command-toggle-usages-timed"));

		if (this.canToggle(ToggleType.PRIVATE_MESSAGE))
			usages.add(Lang.component("command-toggle-usages-private-message"));

		if (this.canToggle(ToggleType.SOUND_NOTIFY))
			usages.add(Lang.component("command-toggle-usages-sound-notify"));

		usages.add(Lang.component("command-toggle-usages-2"));

		return SimpleComponent.join(usages);

	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		this.checkConsole();

		final String typeRaw = this.args[0];

		final PlayerCache cache = PlayerCache.fromCached(this.getPlayer());
		ToggleType toggleType = null;
		PlayerMessageType messageType = null;

		try {
			toggleType = ToggleType.fromKey(typeRaw);

			if (toggleType != null)
				if (!Settings.Toggle.APPLY_ON.contains(toggleType))
					toggleType = null;
				else
					this.checkPerm(Permissions.Command.TOGGLE_TYPE + toggleType.getKey());

		} catch (final IllegalArgumentException ex) {
		}

		try {
			messageType = PlayerMessageType.fromKey(typeRaw);

			if (!this.canToggle(messageType))
				messageType = null;

		} catch (final IllegalArgumentException ex) {
		}

		if ("list".equals(typeRaw)) {
			final List<SimpleComponent> pages = new ArrayList<>();

			if (!Settings.Messages.APPLY_ON.isEmpty()) {
				for (final Entry<PlayerMessageType, Set<String>> entry : cache.getIgnoredMessages().entrySet())
					if (this.canToggle(entry.getKey())) {
						pages.add(Lang.component("command-toggle-list-line", "type", ChatUtil.capitalize(entry.getKey().getToggleLangKey())));

						for (final String groupName : entry.getValue())
							pages.add(SimpleComponent
									.fromMiniNative(" <gray>-<white> ")
									.appendPlain(groupName)
									.onHover(Lang.component("command-toggle-list-tooltip"))
									.onClickRunCmd("/" + this.getLabel() + " " + entry.getKey() + " " + groupName));
					}

				if (!pages.isEmpty())
					pages.add(SimpleComponent.empty());
			}

			if (!Settings.Toggle.APPLY_ON.isEmpty()) {
				pages.add(Lang.component("command-toggle-list-plugin-part-title"));

				for (final ToggleType playerToggle : ToggleType.values())
					if (this.canToggle(playerToggle))
						pages.add(SimpleComponent
								.fromMiniAmpersand(" <gray>-<white> " + ChatUtil.capitalize(playerToggle.getDescription()) + "<gray>: ")
								.append(Lang.component("command-toggle-list-plugin-part-" + (cache.hasToggledPartOff(playerToggle) ? "ignoring" : "receiving")))
								.onHover(Lang.component("command-toggle-list-plugin-part-toggle"))
								.onClickRunCmd("/" + this.getLabel() + " " + playerToggle.getKey() + " " + cache.getPlayerName())

						);
			}

			new ChatPaginator()
					.setPages(pages)
					.setFoundationHeader(Lang.legacy("command-toggle-list-plugin-part-header", "player", cache.getPlayerName()))
					.send(this.audience);

			return;
		}

		this.checkBoolean(toggleType != null || messageType != null, Lang.component("command-toggle-invalid-type",
				"available_toggles", Common.join(Settings.Toggle.APPLY_ON),
				"available_messages", Common.join(Settings.Messages.APPLY_ON)));

		if (messageType != null) {
			if (this.args.length == 2) {
				final PlayerMessage message = this.findMessage(messageType, this.args[1]);
				final boolean ignoring = cache.isIgnoringMessage(message);
				this.checkToggle(ignoring);

				cache.setIgnoringMessage(message, !ignoring);
				this.tellSuccess(Lang.component("command-toggle-ignore-group-" + (ignoring ? "off" : "on"), "type", messageType.getToggleLangKey(), "group", message.getGroup()));
			}

			else {
				final boolean ignoring = cache.isIgnoringMessages(messageType);
				this.checkToggle(ignoring);

				cache.setIgnoringMessages(messageType, !ignoring);
				this.tellSuccess(Lang.component("command-toggle-ignore-all-" + (ignoring ? "off" : "on"), "type", messageType.getToggleLangKey()));
			}

		} else {
			final boolean ignoring = cache.hasToggledPartOff(toggleType);
			this.checkToggle(ignoring);

			if (toggleType != null && toggleType.equals(ToggleType.PRIVATE_MESSAGE) && !cache.hasManuallyToggledPMs())
				cache.setManuallyToggledPMs(true);

			cache.setToggledPart(toggleType, !ignoring);
			this.tellSuccess(Lang.component("command-ignore-part-" + (ignoring ? "off" : "on"), "type", toggleType.getDescription()));
		}
	}

	private void checkToggle(final boolean ignoring) {
		this.checkPerm(ignoring ? Permissions.Command.TOGGLE_STATE_OFF : Permissions.Command.TOGGLE_STATE_ON);
	}

	/*
	 * Return true if the toggle is enabled from settings
	 */
	private boolean canToggle(final ToggleType type) {
		return Settings.Toggle.APPLY_ON.contains(type) && this.hasPerm(Permissions.Command.TOGGLE_TYPE + type.getKey());
	}

	/*
	 * Return true if the player message is enabled from settings
	 */
	private boolean canToggle(final PlayerMessageType type) {
		return (Settings.Messages.APPLY_ON.contains(type) || (type == PlayerMessageType.SWITCH && Settings.Proxy.ENABLED)) && this.hasPerm(Permissions.Command.TOGGLE_TYPE + type.getKey());
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		if (this.args.length == 1) {
			final Set<String> completed = CommonCore.newSet("list");

			for (final PlayerMessageType type : Settings.Messages.APPLY_ON)
				if (this.canToggle(type))
					completed.add(type.getKey());

			for (final ToggleType type : Settings.Toggle.APPLY_ON)
				if (this.canToggle(type))
					completed.add(type.getKey());

			if (Settings.Proxy.ENABLED)
				if (this.canToggle(PlayerMessageType.SWITCH))
					completed.add("switch");

			return this.completeLastWord(completed);
		}

		if (this.args.length == 2) {
			PlayerMessageType type;

			try {
				type = PlayerMessageType.fromKey(this.args[0]);

				if (type != null && !this.canToggle(type))
					return NO_COMPLETE;

			} catch (final IllegalArgumentException ex) {
				return NO_COMPLETE;
			}

			return this.completeLastWord(PlayerMessages.getInstance().getMessageNames(type));
		}

		return NO_COMPLETE;
	}
}
