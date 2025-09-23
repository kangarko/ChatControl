package org.mineacademy.chatcontrol.command;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.PlaceholderPrefix;
import org.mineacademy.chatcontrol.model.PrivateMessage;
import org.mineacademy.chatcontrol.model.ToggleType;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.settings.Lang;

public final class CommandTell extends ChatControlCommand {

	public CommandTell() {
		super(Settings.PrivateMessages.TELL_ALIASES);

		this.setMinArguments(1);
		this.setPermission(Permissions.Command.TELL);
		this.setUsage(Lang.component("command-tell-usage"));
		this.setDescription(Lang.component("command-tell-description"));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		return Lang.component("command-tell-usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		final boolean isOff = "off".equalsIgnoreCase(this.args[0]);
		final String message = this.joinArgs(1);

		final WrappedSender wrapped = WrappedSender.fromAudience(this.audience);

		final SyncedCache syncedSenderCache = SyncedCache.fromUniqueId(wrapped.getUniqueId());
		final SyncedCache syncedReceiverCache = SyncedCache.fromNickColorless(this.args[0]);

		final SenderCache senderCache = wrapped.getSenderCache();

		if (isOff || message.isEmpty()) {
			this.checkBoolean(Settings.PrivateMessages.AUTOMODE, Lang.component("command-rule-conversation-disabled"));
			this.checkConsole();

			final String conversingPlayer = syncedSenderCache.getConversingPlayerName();

			if (isOff) {
				this.checkNotNull(conversingPlayer, Lang.component("command-tell-conversation-mode-not-conversing"));
				final SyncedCache conversingCache = SyncedCache.fromPlayerName(conversingPlayer);

				this.tellSuccess(Lang.component("command-tell-conversation-mode-off", conversingCache == null ? new HashMap<>() : conversingCache.getPlaceholders(PlaceholderPrefix.RECEIVER)));
				syncedSenderCache.setConversingPlayerName(null);
				senderCache.setLastAutoModeChat(0);

			} else {
				this.checkNotNull(syncedReceiverCache, Lang.component("command-tell-receiver-offline", "receiver_name", this.args[0]));

				final String receiverName = syncedReceiverCache.getPlayerName();
				final boolean isConversing = receiverName.equals(conversingPlayer);

				if (syncedReceiverCache.isVanished() && !this.hasPerm(Permissions.Bypass.VANISH))
					this.returnTell(Lang.component("command-tell-receiver-offline", "receiver_name", receiverName));

				final Map<String, Object> placeholders = new HashMap<>();

				placeholders.put("message", message);
				placeholders.putAll(syncedReceiverCache.getPlaceholders(PlaceholderPrefix.RECEIVER));
				placeholders.putAll(syncedSenderCache.getPlaceholders(PlaceholderPrefix.SENDER));

				if (this.isPlayer() && !this.hasPerm(Permissions.Bypass.REACH)) {
					if (Settings.Ignore.ENABLED && Settings.Ignore.STOP_PRIVATE_MESSAGES && syncedReceiverCache.isIgnoringPlayer(this.getPlayer().getUniqueId()))
						this.returnTell(Lang.component("command-ignore-cannot-pm", placeholders));

					if (Settings.Toggle.APPLY_ON.contains(ToggleType.PRIVATE_MESSAGE) && !this.audience.getName().equals(receiverName) && syncedReceiverCache.hasToggledPartOff(ToggleType.PRIVATE_MESSAGE))
						this.returnTell(Lang.component("command-toggle-cannot-pm", placeholders));
				}

				syncedSenderCache.setConversingPlayerName(isConversing ? null : receiverName);
				senderCache.setLastAutoModeChat(0);
				this.tellSuccess(Lang.component("command-tell-conversation-mode-" + (isConversing ? "off" : "on"), placeholders));
			}

			return;
		}

		this.checkBoolean(
				syncedReceiverCache != null // Original offline player check, reversed to match new verification method
				// Prevents cross-server PMs when disabled. Previously, the message wasn't delivered,
				// but the sender still saw it as if it was — this fixes that inconsistency.
				&& (Settings.PrivateMessages.PROXY || syncedSenderCache == null || syncedReceiverCache.getServerName().equals(syncedSenderCache.getServerName())),
				Lang.component("command-tell-receiver-offline", "receiver_name", this.args[0])
		);

		PrivateMessage.send(wrapped, syncedReceiverCache, message);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		return this.args.length == 1 ? this.completeLastWordPlayerNames(Settings.PrivateMessages.ALLOW_CROSS_SERVER_TAB_COMPLETE) : NO_COMPLETE;
	}
}