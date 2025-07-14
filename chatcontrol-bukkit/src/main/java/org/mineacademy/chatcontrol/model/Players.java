package org.mineacademy.chatcontrol.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.operator.Tag;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Proxy;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.collection.ExpiringMap;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.Lang;

import lombok.NonNull;
import lombok.Setter;

/**
 * Show methods related to players
 */
public final class Players {

	/**
	 * Internal flag indicating nicks are on, for best performance
	 */
	@Setter
	private static boolean nicksEnabled = false;

	/**
	 * Cache online players and only update them in a timed manner for best performance
	 */
	private static Map<Boolean, Set<String>> playerNicknames = ExpiringMap.builder().expiration(3, TimeUnit.SECONDS).build();
	private static Map<Boolean, Set<String>> playerNames = ExpiringMap.builder().expiration(3, TimeUnit.SECONDS).build();

	/**
	 * Render the message of the day to the player
	 *
	 * @param wrapped
	 * @param delay
	 */
	public static void showMotd(final WrappedSender wrapped, final boolean delay) {
		final int delayTicks = delay ? Settings.Motd.DELAY.getTimeTicks() : 3;

		if (Settings.Performance.ASYNC_MOTD)
			Platform.runTaskAsync(delayTicks, () -> showMotd0(wrapped, delay));
		else
			Platform.runTask(delayTicks, () -> showMotd0(wrapped, delay));
	}

	/*
	 * Actually perform the message of the day display.
	 */
	private static void showMotd0(final WrappedSender wrapped, final boolean delay) {
		final Player player = wrapped.getPlayer();

		// If player joined less than 5 seconds ago count as newcomer
		final boolean firstTime = ((System.currentTimeMillis() - player.getFirstPlayed()) / 1000) < 5;

		// Do not show to players who already left
		if (player.isOnline()) {
			final String motd = firstTime ? Settings.Motd.FORMAT_MOTD_FIRST_TIME : Newcomer.isNewcomer(player) ? Settings.Motd.FORMAT_MOTD_NEWCOMER : Settings.Motd.FORMAT_MOTD.getFor(player);

			if (!motd.isEmpty())
				wrapped.getAudience().sendMessage(Format.parse(motd).build(wrapped));

			if (!motd.isEmpty() || Settings.Motd.PLAY_SOUND_IF_NO_MESSAGE)
				Settings.Motd.SOUND.play(player);
		}
	}

	/**
	 * Broadcast the /me command to players
	 *
	 * @param senderId
	 * @param bypassReach
	 * @param component
	 */
	public static void showMe(final UUID senderId, final boolean bypassReach, final SimpleComponent component) {
		for (final Player online : Players.getOnlinePlayersWithLoadedDb()) {
			final PlayerCache cache = PlayerCache.fromCached(online);

			if (Settings.Toggle.APPLY_ON.contains(ToggleType.BROADCAST) && cache.hasToggledPartOff(ToggleType.BROADCAST) && !senderId.equals(online.getUniqueId()))
				continue;

			if (!bypassReach && Settings.Ignore.HIDE_ME && cache.isIgnoringPlayer(senderId))
				continue;

			Common.tell(online, component);
		}
	}

	/**
	 * Update player tablist name with colors the initiator has permissions for
	 *
	 * @param wrappedSender
	 */
	public static void setTablistName(@NonNull final WrappedSender wrappedSender) {
		final Player player = wrappedSender.getPlayer();

		if (Settings.TabList.ENABLED) {
			final SimpleComponent header = compileFormat(wrappedSender, Settings.TabList.HEADER);
			final SimpleComponent footer = compileFormat(wrappedSender, Settings.TabList.FOOTER);
			final SimpleComponent format = compileFormat(wrappedSender, Settings.TabList.FORMAT);

			wrappedSender.getAudience().sendPlayerListHeaderAndFooter(header, footer);

			if (format != null)
				if (Remain.isCommandSenderAudience())
					player.playerListName(format.toAdventure(wrappedSender.getAudience()));
				else
					player.setPlayerListName(format.toLegacySection(wrappedSender.getAudience()));
		}

		if (Settings.Tag.APPLY_ON.contains(Tag.Type.NICK)) {
			final PlayerCache cache = PlayerCache.fromCached(player);
			final boolean hasNick = cache.hasTag(Tag.Type.NICK);
			final String nick = cache.getTag(Tag.Type.NICK);

			player.setDisplayName(hasNick ? SimpleComponent.fromMiniAmpersand(nick).toLegacySection(null, false) : null);

			HookManager.setNick(player.getUniqueId(), nick);
		}
	}

	/*
	 * Compile the given format
	 */
	private static SimpleComponent compileFormat(final WrappedSender wrapped, final String formatName) {
		if (!formatName.equals("none") && !formatName.isEmpty()) {
			final Format format = Format.parse(formatName);

			return format.build(wrapped);
		}

		return SimpleComponent.empty();
	}

	/**
	 * @see #clearChat(CommandSender, boolean, boolean) method for dudes
	 *
	 * @param broadcastStaffMessage
	 * @param forced
	 */
	public static void clearChatFromProxy(final boolean broadcastStaffMessage, final boolean forced) {
		clearChat(null, broadcastStaffMessage, forced);
	}

	/**
	 * Clear all dudes' windows.
	 *
	 * @param sender
	 * @param broadcastStaffMessage
	 * @param forced
	 */
	public static void clearChat(@Nullable final CommandSender sender, final boolean broadcastStaffMessage, final boolean forced) {
		for (final Player online : Players.getOnlinePlayersWithLoadedDb())
			if (online.hasPermission(Permissions.Bypass.CLEAR) && !forced) {
				if (broadcastStaffMessage && sender != null)
					Messenger.announce(online, Lang.component("command-clear-success-staff", "player", Platform.toPlayer(sender).getName()));
			}

			else
				for (int line = 0; line < 100; line++)
					Common.tell(online, "&r");
	}

	/**
	 * Retrieve a player by his name or nickname if set
	 *
	 * @param nameOrNick
	 * @return
	 */
	public static Player findPlayer(@NonNull final String nameOrNick) {
		if (nicksEnabled) {
			for (final Player online : Players.getOnlinePlayersWithLoadedDb()) {
				final String nick = getNickOrNullColorless(online);

				if ((nick != null && nick.equalsIgnoreCase(nameOrNick)) || online.getName().equalsIgnoreCase(nameOrNick))
					return online;
			}

			return null;
		}

		return Bukkit.getPlayer(nameOrNick);
	}

	/**
	 * Return the nick or the name of the player
	 *
	 * @param player
	 * @return
	 */
	public static String getNickOrNullColorless(final Player player) {
		if (nicksEnabled) {
			final PlayerCache cache = PlayerCache.fromCached(player);

			if (cache.hasTag(Tag.Type.NICK))
				return cache.getTagColorless(Tag.Type.NICK);
		}

		return Settings.Tag.BACKWARD_COMPATIBLE ? HookManager.getNickOrNullColorless(player) : null;
	}

	/**
	 * Return the nick or the name of the player
	 *
	 * @param player
	 * @return
	 */
	public static String getNickOrNullColored(final Player player) {
		if (nicksEnabled) {
			final PlayerCache cache = PlayerCache.fromCached(player);

			if (cache.hasTag(Tag.Type.NICK))
				return cache.getTag(Tag.Type.NICK);
		}

		return Settings.Tag.BACKWARD_COMPATIBLE ? HookManager.getNickOrNullColored(player) : null;
	}

	/**
	 * Compile a list of all online players for the given receiver, returning a list
	 * of their names or nicknames according to Tab_Complete.Use_Nicknames setting.
	 * Vanished players are included only if receiver has bypass reach permission.
	 *
	 * @param includeVanished
	 * @return
	 */
	public static Set<String> getPlayerNamesForTabComplete(final boolean includeVanished) {
		return compilesPlayers(includeVanished, Proxy.ENABLE_NETWORK_TAB_COMPLETING, Settings.TabComplete.USE_NICKNAMES);
	}

	/**
	 * Compile a list of all online players for the given receiver, returning a list
	 * of their names or nicknames according to Tab_Complete.Use_Nicknames setting.
	 * Vanished players are included only if receiver has bypass reach permission.
	 *
	 * @param requester
	 * @return
	 */
	public static Set<String> getPlayerNamesForTabComplete(@NonNull final CommandSender requester) {
		final boolean includeVanished = requester.hasPermission(Permissions.Bypass.VANISH),
				includeNetwork = Proxy.ENABLE_NETWORK_TAB_COMPLETING || requester.hasPermission(Permissions.Bypass.NETWORK_TAB_COMPLETING);

		return compilesPlayers(includeVanished, includeNetwork, Settings.TabComplete.USE_NICKNAMES);
	}

	/*
	 * Compile a list of players
	 */
	private static Set<String> compilesPlayers(final boolean includeVanished, final boolean includeNetwork, final boolean preferNicknames) {
		final Set<String> players = (preferNicknames ? playerNicknames : playerNames).getOrDefault(includeVanished, new TreeSet<>());

		if (!players.isEmpty())
			return players;

		// Add players from the network
		if (Proxy.ENABLED && includeNetwork)
			for (final SyncedCache cache : SyncedCache.getCaches())
				if (includeVanished || !cache.isVanished())
					players.add(preferNicknames ? cache.getNameOrNickColorless() : cache.getPlayerName());

		for (final Player player : Players.getOnlinePlayersWithLoadedDb())
			if (includeVanished || !PlayerUtil.isVanished(player)) {
				final String nick = preferNicknames ? getNickOrNullColorless(player) : null;

				players.add(nick != null ? nick : player.getName());
			}

		(preferNicknames ? playerNicknames : playerNames).put(includeVanished, players);

		return players;
	}

	/**
	 * Get online players with loaded database
	 *
	 * @return
	 */
	public static List<Player> getOnlinePlayersWithLoadedDb() {
		final List<Player> players = new ArrayList<>();

		for (final Player player : Remain.getOnlinePlayers())
			if (SenderCache.from(player).isDatabaseLoaded())
				players.add(player);

		return players;
	}
}
