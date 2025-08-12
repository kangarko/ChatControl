package org.mineacademy.chatcontrol.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.model.Checker;
import org.mineacademy.chatcontrol.model.Mute;
import org.mineacademy.chatcontrol.model.Newcomer;
import org.mineacademy.chatcontrol.model.Spy;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.model.db.Log;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.event.SimpleListener;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.settings.Lang;
import org.mineacademy.fo.settings.SimpleSettings;

import lombok.Getter;

/**
 * Represents a simple command listener for rules
 */
public final class CommandListener extends SimpleListener<PlayerCommandPreprocessEvent> {

	/**
	 * The instance of this class
	 */
	@Getter
	private static final CommandListener instance = new CommandListener();

	/*
	 * Create a new listener
	 */
	private CommandListener() {
		super(PlayerCommandPreprocessEvent.class, EventPriority.HIGHEST, true);
	}

	/**
	 * @see org.mineacademy.fo.event.SimpleListener#execute(org.bukkit.event.Event)
	 */
	@Override
	protected void execute(final PlayerCommandPreprocessEvent event) {
		final Player player = event.getPlayer();
		final SenderCache senderCache = SenderCache.from(player);

		if (!senderCache.isDatabaseLoaded() || senderCache.isQueryingDatabase()) {
			event.setCancelled(true);

			this.returnTell(Lang.component("data-loading"));
		}

		String message = event.getMessage();

		final String[] args = event.getMessage().split(" ");
		final String label = args[0];

		for (String allowed : Settings.AuthMe.ALLOW_COMMANDS) {
			if (!allowed.startsWith("/"))
				allowed = "/" + allowed;

			if (message.startsWith(allowed)) {
				Debugger.debug("player-message", "[Authy/Allow Commands] Bypassing checks in " + message + " for " + player.getName());

				return;
			}
		}

		final WrappedSender wrapped = WrappedSender.fromPlayer(player);

		// Prevent internal command alias from being filtered
		for (final String alias : SimpleSettings.MAIN_COMMAND_ALIASES)
			if (message.startsWith("/" + alias + " internal"))
				return;

		// /channel send is handled as regular channel send
		for (final String alias : Settings.Channels.COMMAND_ALIASES)
			if (message.startsWith("/" + alias + " send ") || message.startsWith("/" + alias + " s "))
				return;

		// /tag command uses tag.rs rules only
		for (final String alias : Settings.Tag.COMMAND_ALIASES)
			if (message.startsWith("/" + alias + " "))
				return;

		// Check mute
		if (Settings.Mute.PREVENT_COMMANDS.isInList(label))
			Mute.checkMute(wrapped, null);

		// Newcomers
		if (Settings.Newcomer.RESTRICT_COMMANDS && Newcomer.isNewcomer(player) && !Settings.Newcomer.RESTRICT_COMMANDS_WHITELIST.isInList(label))
			this.cancel(Lang.component("player-newcomer-cannot-command"));

		// Filters
		final Checker check = Checker.filterCommand(wrapped, message, wrapped.getPlayerCache().getWriteChannel());

		if (check.isMessageChanged())
			message = check.getMessage();

		// Send to spying players and log but prevent duplicates
		if ((!ValidCore.isInList(label, Settings.Mail.COMMAND_ALIASES) || !Settings.Mail.ENABLED)
				&& ((!ValidCore.isInList(label, Settings.PrivateMessages.TELL_ALIASES) && !ValidCore.isInList(label, Settings.PrivateMessages.REPLY_ALIASES)) ||
						!Settings.PrivateMessages.ENABLED)
				&& !(ValidCore.isInList(label, SimpleSettings.MAIN_COMMAND_ALIASES) && args.length > 1 && "internal".equals(args[1]))) {

			if (!ValidCore.isInList(label, Settings.Me.COMMAND_ALIASES) && !check.isSpyingIgnored())
				Spy.broadcastCommand(wrapped, SimpleComponent.fromMiniSection(message));

			if (!check.isLoggingIgnored())
				Log.logCommand(player, message);
		}

		// Set the command back
		event.setMessage(message);
	}
}
