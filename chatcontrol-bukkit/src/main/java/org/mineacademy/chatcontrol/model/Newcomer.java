package org.mineacademy.chatcontrol.model;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.model.SimpleRunnable;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.platform.BukkitPlugin;
import org.mineacademy.fo.platform.Platform;

import lombok.NonNull;

/**
 * Represents method related to dudes who recently joined
 */
public final class Newcomer {

	/**
	 * Holds all temporary newcomer permissions
	 */
	private static final Map<UUID, Set<PermissionAttachment>> permissions = new LinkedHashMap<>();

	/**
	 * Reschedule the permissions task giving/taking newcomer permissions
	 */
	public static void scheduleTask() {
		Platform.runTaskTimer(5 * 20, new PermissionsTask());
	}

	/**
	 * Return true if player is newcomer
	 *
	 * @param player
	 * @return
	 */
	public static boolean isNewcomer(@NonNull final Player player) {
		if (isEnabled() && Settings.Newcomer.WORLDS.contains(player.getWorld().getName()) && !player.hasPermission(Permissions.Bypass.NEWCOMER)) {
			final long thresholdSeconds = Settings.Newcomer.THRESHOLD.getTimeSeconds();
			final long playedSeconds = (System.currentTimeMillis() - player.getFirstPlayed()) / 1000;

			return playedSeconds < thresholdSeconds;
		}

		return false;
	}

	/**
	 * Gives newcomer permissions to the player
	 *
	 * @param player
	 */
	public static void givePermissions(@NonNull final Player player) {
		for (final Tuple<String, Boolean> tuple : Settings.Newcomer.PERMISSIONS) {
			final String permission = tuple.getKey();
			final boolean value = tuple.getValue();

			if (!player.hasPermission(permission)) {
				final PermissionAttachment attachment = player.addAttachment(BukkitPlugin.getInstance(), permission, value);
				final Set<PermissionAttachment> attachments = permissions.getOrDefault(player.getUniqueId(), new HashSet<>());

				attachments.add(attachment);
				permissions.put(player.getUniqueId(), attachments);
			}
		}
	}

	/**
	 * Return true if newcomer option is enabled
	 *
	 * @return
	 */
	private static boolean isEnabled() {
		return Settings.Newcomer.THRESHOLD.isEnabled();
	}

	/**
	 * The task responsible for giving/taking special newcomer-only permissions
	 */
	private static class PermissionsTask extends SimpleRunnable {

		/**
		 * @see java.lang.Runnable#run()
		 */
		@Override
		public void run() {
			for (final Player player : Players.getOnlinePlayersWithLoadedDb())
				if (isNewcomer(player))
					givePermissions(player);

				else if (permissions.containsKey(player.getUniqueId()))

					// Remove all attachments
					for (final PermissionAttachment attachment : permissions.remove(player.getUniqueId()))

						// Verify if they truly belong to the player
						if (attachment.getPermissible() instanceof Player && ((Player) attachment.getPermissible()).getUniqueId().equals(player.getUniqueId()))
							try {
								player.removeAttachment(attachment);

							} catch (final IllegalArgumentException ex) {
								// Silence Spigot error
							}
		}
	}
}
