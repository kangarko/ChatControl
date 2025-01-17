package org.mineacademy.chatcontrol.model;

import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.model.db.ServerSettings;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.settings.Lang;

/**
 * Utilities related to muting players
 */
public final class Mute {

	/**
	 * Return true if the given setting is true, the mute is enabled in settings,
	 * player does not have bypass permission and its cache or the server is muted.
	 *
	 * @param setting
	 * @param wrapped
	 * @return
	 */
	public static boolean isSomethingMutedIf(final boolean setting, final WrappedSender wrapped) {
		if (setting)
			try {
				checkMute(wrapped, wrapped.isPlayer() ? wrapped.getPlayerCache().getWriteChannel() : null);

			} catch (final EventHandledException ex) {
				if (ex.isCancelled())
					return true;
			}

		return false;
	}

	/**
	 * Checks if the player, his channel or the server is muted and throws an exception
	 * with the appropriate error message if is.
	 *
	 * @param wrapped
	 * @param channel
	 * @throws EventHandledException
	 */
	public static void checkMute(final WrappedSender wrapped, final Channel channel) {
		if (Settings.Mute.ENABLED && !wrapped.hasPermission(Permissions.Bypass.MUTE)) {
			final Variables variables = Variables.builder(wrapped.getAudience());

			if (ServerSettings.getInstance().isMuted())
				throw new EventHandledException(true, Lang.component(variables, "command-mute-cannot-chat-server-muted"));

			if (Settings.Proxy.ENABLED && ServerSettings.isProxyLoaded())
				if (ServerSettings.getProxy().isMuted())
					throw new EventHandledException(true, Lang.component(variables, "command-mute-cannot-chat-proxy-muted"));

			if (channel != null && channel.isMuted())
				throw new EventHandledException(true, Lang.component(variables, "command-mute-cannot-chat-channel-muted", "channel", channel.getName()));

			final PlayerCache cache = wrapped.getPlayerCache();

			if (cache != null)
				if (cache.isMuted() || (wrapped.isPlayer() && HookManager.isMuted(wrapped.getPlayer().getUniqueId())))
					throw new EventHandledException(true, Lang.component(variables, "command-mute-cannot-chat-player-muted"));
		}
	}
}
