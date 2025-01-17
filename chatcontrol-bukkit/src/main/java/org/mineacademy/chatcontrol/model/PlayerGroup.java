package org.mineacademy.chatcontrol.model;

import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.remain.Remain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Some settings in settings.yml can be customized according to what permission
 * the player has.
 *
 * Permissions are named "groups" so that the group "vip" will apply if player
 * has chatcontrol.group.vip permission.
 *
 * Groups override default settings from settings.yml
 *
 * @param <T>
 */
@RequiredArgsConstructor
public final class PlayerGroup<T> {

	/**
	 * The setting key, used to prevent errors
	 */
	private final Type type;

	/**
	 * The defalt value from settings.yml
	 */
	private final T defaultValue;

	/**
	 * See {@link #getFor(Player)}, except that we return defaultValue
	 * when sender is not player
	 *
	 * @param sender
	 * @return
	 */
	public T getFor(final CommandSender sender) {
		return this.getFor(sender, null);
	}

	/**
	 * See {@link #getFor(Player)}, except that we return defaultValue
	 * when sender is not player
	 *
	 * @param sender
	 * @param defaultValue
	 *
	 * @return
	 */
	public T getFor(final CommandSender sender, final T defaultValue) {
		return sender instanceof Player ? this.getFor((Player) sender, defaultValue) : CommonCore.getOrDefault(defaultValue, this.defaultValue);
	}

	/**
	 * Return the group setting value for the given player, reverting to the default one
	 * if not set
	 *
	 * @param player
	 * @return
	 */
	public T getFor(final Player player) {
		return this.getFor(player, null);
	}

	/**
	 * Return the group setting value for the given player, reverting to the default one
	 * if not set
	 *
	 * @param player
	 * @param defaultValue
	 *
	 * @return
	 */
	public T getFor(final Player player, final T defaultValue) {
		return this.filter(groupName -> player.hasPermission(Permissions.GROUP_NAME + groupName), defaultValue);
	}

	/**
	 * Return the group setting value for the given player name, reverting to the default one
	 * if not set
	 *
	 * @param uuid
	 * @return
	 */
	public T getForUUID(final UUID uuid) {
		final OfflinePlayer offline = Remain.getOfflinePlayerByUniqueId(uuid);

		return this.filter(groupName -> HookManager.hasVaultPermission(offline, Permissions.GROUP_NAME + groupName), this.defaultValue);
	}

	/*
	 * Retrieve the first player group setting value that matches the given filter
	 */
	private T filter(final Predicate<String> filter, final T defaultValue) {
		for (final Entry<String, Map<PlayerGroup.Type, Object>> entry : Settings.Groups.LIST.entrySet()) {
			final String groupName = entry.getKey();
			final Object groupSetting = entry.getValue().get(this.type);

			// If the group contains this and player has permission, return it
			if (groupSetting != null && filter.test(groupName))
				return (T) groupSetting;
		}

		return CommonCore.getOrDefault(defaultValue, this.defaultValue);
	}

	/**
	 * Represent different group setting types that
	 * override defaults from settings.yml
	 */
	@Getter
	@RequiredArgsConstructor
	public enum Type {

		/**
		 * Limit for reading channels
		 */
		MAX_READ_CHANNELS(Integer.class, "Max_Read_Channels"),

		/**
		 * Chat message delay for antispam
		 */
		MESSAGE_DELAY(SimpleTime.class, "Message_Delay"),

		/**
		 * Chat message similarity for antispam
		 */
		MESSAGE_SIMILARITY(Double.class, "Message_Similarity"),

		/**
		 * Command message delay for antispam
		 */
		COMMAND_DELAY(SimpleTime.class, "Command_Delay"),

		/**
		 * Command similarity for antispam
		 */
		COMMAND_SIMILARITY(Double.class, "Command_Similarity"),

		/**
		 * Sound notify color
		 */
		SOUND_NOTIFY_FORMAT(String.class, "Sound_Notify_Format"),

		/**
		 * Message of the day
		 */
		MOTD(String.class, "Motd_Format"),

		;

		/**
		 * The value class that the settings must be parseable from
		 */
		private final Class<?> validClass;

		/**
		 * The config key, unobfuscateable
		 */
		private final String key;

		/**
		 * Return group setting from the config key
		 *
		 * @param key
		 * @return
		 */
		public static Type fromKey(final String key) {
			for (final Type setting : values())
				if (setting.getKey().equalsIgnoreCase(key))
					return setting;

			throw new IllegalArgumentException("No such group setting " + key + " Available: " + CommonCore.join(values()));
		}

		@Override
		public String toString() {
			return this.key;
		}
	}
}
