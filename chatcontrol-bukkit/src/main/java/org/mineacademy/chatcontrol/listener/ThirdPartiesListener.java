package org.mineacademy.chatcontrol.listener;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.dynmap.DynmapWebChatEvent;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.model.ChatControlProxyMessage;
import org.mineacademy.chatcontrol.model.Checker;
import org.mineacademy.chatcontrol.model.Discord;
import org.mineacademy.chatcontrol.model.Format;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Spy;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.model.db.Database;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.operator.Tag.Type;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.ProxyUtil;
import org.mineacademy.fo.ReflectionUtil;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.model.DynmapSender;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.remain.Remain;

import com.gmail.nossr50.chat.author.Author;
import com.gmail.nossr50.datatypes.chat.ChatChannel;
import com.gmail.nossr50.datatypes.party.Party;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.events.chat.McMMOPartyChatEvent;
import com.gmail.nossr50.util.player.UserManager;
import com.palmergames.bukkit.TownyChat.channels.Channel;
import com.palmergames.bukkit.TownyChat.events.AsyncChatHookEvent;

import fr.xephi.authme.events.LoginEvent;
import net.ess3.api.IUser;
import net.ess3.api.events.NickChangeEvent;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.events.ChatEvent;

/**
 * A common listener for all third party plugin integration
 */
public final class ThirdPartiesListener {

	private static McMMOListener mcMMOHook;

	private static boolean bedWarsLoaded;

	private static boolean bedWarsErrorLogged;

	/**
	 * Register all compatible hooks
	 */
	public static void registerEvents() {

		if (Platform.isPluginInstalled("SimpleClans")) {
			CommonCore.log("Note: Hooked into SimpleClans to filter ignored players");

			Platform.registerEvents(new SimpleClansListener());
		}

		if (Platform.isPluginInstalled("TownyChat")) {
			CommonCore.log("Note: Hooked into TownyChat to spy channels");

			Platform.registerEvents(new TownyChatListener());
		}

		if (Platform.isPluginInstalled("mcMMO")) {
			final String version = Bukkit.getPluginManager().getPlugin("mcMMO").getDescription().getVersion();

			if (version.startsWith("2.")) {
				mcMMOHook = new McMMOListener();
				Platform.registerEvents(mcMMOHook);

				CommonCore.log("Note: Hooked into mcMMO to spy channels");
			} else
				CommonCore.warning("Could not hook into mcMMO. Version 2.x is required, you have " + version);
		}

		if (HookManager.isAuthMeLoaded()) {
			CommonCore.log("Note: Hooked into AuthMe to delay join message until login");

			Platform.registerEvents(new AuthMeListener());
		}

		if (HookManager.isEssentialsLoaded()) {
			CommonCore.log("Note: Hooked into Essentials (set Tag.Backward_Compatible to enable nick support)");

			Platform.registerEvents(new EssentialsListener());
		}

		if (Platform.isPluginInstalled("dynmap")) {
			CommonCore.log("Note: Hooked into dynmap");

			Platform.registerEvents(new DynmapListener());
		}

		if (Platform.isPluginInstalled("BedWars1058") || Platform.isPluginInstalled("BedWars2023")) {
			bedWarsLoaded = true;

			CommonCore.log("Note: Hooked into " + (Platform.isPluginInstalled("BedWars2023") ? "BedWars2023" : "BedWars1058") + " for arena party support");
		}
	}

	// ------------------------------------------------------------------------------------------------------------
	// mcMMO
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Return true if mcMMO is loaded
	 *
	 * @return
	 */
	public static boolean isMcMMOLoaded() {
		return mcMMOHook != null;
	}

	/**
	 * Return the active mcMMO party chat.
	 *
	 * @param player the player to check for.
	 * @return
	 */
	public static String getActivePartyChat(final Player player) {
		return isMcMMOLoaded() ? mcMMOHook.getActivePartyChat(player) : null;
	}

	/**
	 * Return the online residents in a player's party, or an empty list if
	 * there are none.
	 *
	 * @param player the player's party to check.
	 * @return
	 */
	public static List<Player> getMcMMOPartyRecipients(final Player player) {
		return isMcMMOLoaded() ? mcMMOHook.getPartyRecipients(player) : new ArrayList<>();
	}

	// ------------------------------------------------------------------------------------------------------------
	// BedWars1058 / BedWars2023
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Return true if both players are in the same BedWars1058/BedWars2023 arena.
	 * Uses reflection to avoid a compile-time dependency on BedWars1058.
	 *
	 * @param receiver
	 * @param sender
	 * @return
	 */
	public static boolean isInSameBedWarsArena(final Player receiver, final Player sender) {
		if (!bedWarsLoaded)
			return false;

		try {
			final Class<?> apiClass = Class.forName("com.andrei1058.bedwars.api.BedWars");
			final RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(apiClass);

			if (rsp == null)
				return false;

			final Object api = rsp.getProvider();
			final Object arenaUtil = api.getClass().getMethod("getArenaUtil").invoke(api);
			final Object senderArena = arenaUtil.getClass().getMethod("getArenaByPlayer", Player.class).invoke(arenaUtil, sender);

			if (senderArena == null)
				return false;

			final Object receiverArena = arenaUtil.getClass().getMethod("getArenaByPlayer", Player.class).invoke(arenaUtil, receiver);

			return senderArena.equals(receiverArena);

		} catch (final Throwable t) {
			if (!bedWarsErrorLogged) {
				CommonCore.warning("Failed checking BedWars arena for " + sender.getName() + " and " + receiver.getName()
						+ ". Ensure you have the latest BedWars version. Error: " + t);

				bedWarsErrorLogged = true;
			}

			return false;
		}
	}
}

/**
 * SimpleClans handle
 */
final class SimpleClansListener implements Listener {

	/**
	 * Listen to simple clans chat and remove receivers who ignore the sender
	 *
	 * @param event
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerClanChat(final ChatEvent event) {

		final String message = event.getMessage();
		final ClanPlayer sender = event.getSender();
		final Player senderPlayer = sender.toPlayer();

		if (senderPlayer == null)
			return;

		if (!SenderCache.from(senderPlayer).isDatabaseLoaded())
			return;

		final WrappedSender wrapped = WrappedSender.fromPlayer(senderPlayer);
		final List<ClanPlayer> recipients = event.getReceivers();

		try {
			final Checker checker = Checker.filterChannel(wrapped, message, null);

			if (checker.isMessageChanged())
				event.setMessage(checker.getMessage());

			// Remove recipients if silent cancel
			if (checker.isCancelledSilently())
				recipients.removeIf(recipient -> !recipient.getName().equals(senderPlayer.getName()));

			// Reach message to players who ignore the sender if sender has bypass reach permission
			if (!senderPlayer.hasPermission(Permissions.Bypass.REACH))
				for (final Iterator<ClanPlayer> it = event.getReceivers().iterator(); it.hasNext();) {
					final ClanPlayer receiver = it.next();
					final Player receiverPlayer = receiver.toPlayer();

					if (receiverPlayer == null)
						continue;

					final PlayerCache receiverCache = PlayerCache.fromCached(receiverPlayer);

					if (receiverCache.isIgnoringPlayer(sender.getUniqueId()))
						it.remove();
				}

		} catch (final EventHandledException ex) {
			event.setCancelled(ex.isCancelled());

			for (final SimpleComponent component : ex.getComponents())
				Common.tell(senderPlayer, component);

		} catch (final IncompatibleClassChangeError ex) {
			CommonCore.warning("Processing message from SimpleClans failed, if you have TownyChat latest version contact "
					+ Platform.getPlugin().getName() + " authors to update their hook. The error was: " + ex);
		}
	}
}

/**
 * TownyChat handle
 */
final class TownyChatListener implements Listener {

	/**
	 * Listen to chat in towny channels and broadcast spying
	 *
	 * @param event
	 */
	@EventHandler
	public void onChat(final AsyncChatHookEvent event) {
		try {
			final Player player = event.getPlayer();

			if (!SenderCache.from(player).isDatabaseLoaded())
				return;

			final WrappedSender wrapped = WrappedSender.fromPlayer(player);
			final String message = event.getMessage();
			final Channel channel = event.getChannel();

			if (Settings.TownyChat.CHANNEL_WHITELIST.isInList(channel.getName()))
				Spy.broadcastCustomChat(wrapped, SimpleComponent.fromSection(message), Settings.Spy.FORMAT_PARTY_CHAT, SerializedMap.fromArray("channel", channel.getName()), false);

		} catch (final IncompatibleClassChangeError ex) {
			CommonCore.warning("Processing message from TownyChat channel failed, if you have TownyChat latest version contact "
					+ Platform.getPlugin().getName() + " authors to update their hook. The error was: " + ex);
		}
	}
}

/**
 * mcMMO handle
 */
final class McMMOListener implements Listener {

	/*
	 * Prevent showing error multiple times if integration fails.
	 */
	private boolean errorLogged = false;

	/**
	 * Listen to party chat message and forward them to spying players
	 *
	 * @param event
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPartyChat(final McMMOPartyChatEvent event) {
		try {
			final String message = event.getMessage();
			final String party = event.getParty();

			CommandSender sender;

			try {
				final Author author = event.getAuthor();

				sender = author.isConsole() ? Bukkit.getConsoleSender() : Remain.getPlayerByUUID(author.uuid());

			} catch (final LinkageError ex) {
				sender = Bukkit.getPlayerExact(ReflectionUtil.invoke("getSender", event));
			}

			if (sender != null) {
				final WrappedSender wrapped = WrappedSender.fromSender(sender);

				Spy.broadcastCustomChat(wrapped, SimpleComponent.fromSection(message), Settings.Spy.FORMAT_PARTY_CHAT, SerializedMap.fromArray("channel", party), false);
			}

		} catch (final IncompatibleClassChangeError ex) {
			CommonCore.warning("Processing party chat from mcMMO failed, if you have mcMMO latest version contact "
					+ Platform.getPlugin().getName() + " authors to update their hook. The error was: " + ex);
		}
	}

	String getActivePartyChat(final Player player) {
		try {
			final McMMOPlayer mcplayer = UserManager.getPlayer(player);

			if (mcplayer != null) {
				final Party party = mcplayer.getParty();
				final ChatChannel channelType = mcplayer.getChatChannel();

				return channelType == ChatChannel.PARTY || channelType == ChatChannel.PARTY_OFFICER && party != null ? party.getName() : null;
			}

		} catch (final Throwable throwable) {
			if (!this.errorLogged) {
				CommonCore.warning("Failed getting mcMMO party chat for " + player.getName() + " due to an error. Returning null."
						+ " Ensure you have the latest mcMMO version. If so, contact the plugin authors to update the integration. Error was: " + throwable);

				this.errorLogged = true;
			}
		}

		return null;
	}

	List<Player> getPartyRecipients(final Player bukkitPlayer) {
		try {
			final McMMOPlayer mcplayer = UserManager.getPlayer(bukkitPlayer);

			if (mcplayer != null) {
				final Party party = mcplayer.getParty();

				if (party != null)
					return party.getOnlineMembers();
			}

		} catch (final Throwable throwable) {
			if (!this.errorLogged) {
				CommonCore.warning("Failed getting mcMMO party recipients for " + bukkitPlayer.getName() + " due to an error. Returning null."
						+ " Ensure you have the latest mcMMO version. If so, contact the plugin authors to update the integration. Error was: " + throwable);

				this.errorLogged = true;
			}
		}

		return new ArrayList<>();
	}
}

/**
 * AuthMe Reloaded handle
 */
final class AuthMeListener implements Listener {

	/*
	 * Notify about wrong setting combination
	 */
	AuthMeListener() {
		final boolean nativeDelayOption = Bukkit.getPluginManager().getPlugin("AuthMe").getConfig().getBoolean("settings.delayJoinMessage", false);

		if (nativeDelayOption)
			CommonCore.warning("Your AuthMe has settings.delayJoinMessage on true, which conflicts with AuthMe.Delay_Join_Message_Until_Logged option in ChatControl's settings.yml. "
					+ "Disable that and only use our option.");

	}

	/**
	 * Listen to logging in and show join message then instead
	 *
	 * @param event
	 */
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
	public void onLogin(final LoginEvent event) {
		final Player player = event.getPlayer();
		final SenderCache senderCache = SenderCache.from(player);

		if (Settings.AuthMe.DELAY_JOIN_MESSAGE_UNTIL_LOGGED)
			Database.getInstance().loadAndStoreCache(player, senderCache, cache -> cache.onJoin(player, senderCache));
	}
}

/**
 * EssentialsX handle
 */
final class EssentialsListener implements Listener {

	EssentialsListener() {
	}

	/**
	 * Listen to nicks changed
	 *
	 * @param event
	 */
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
	public void onNickChange(final NickChangeEvent event) {
		final IUser player = event.getController();
		final String newNick = event.getValue();

		if (Settings.Tag.BACKWARD_COMPATIBLE) {
			final UUID uniqueId = player.getUUID();
			final PlayerCache cache = PlayerCache.fromCached(Bukkit.getPlayer(uniqueId));

			cache.setTag(Type.NICK, newNick, false /* prevent a race condition */);
		}
	}
}

/**
 * Dynmap handle
 */
final class DynmapListener implements Listener {

	DynmapListener() {
	}

	/**
	 * Listen to dynmap chat
	 *
	 * @param event
	 */
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
	public void onDynmapWebChatEvent(final DynmapWebChatEvent event) {
		String message = event.getMessage();
		final String name = event.getName();
		final String source = event.getSource();

		if (Settings.Dynmap.ENABLED) {
			event.setProcessed();

			final Format format = Format.parse(Settings.Dynmap.FORMAT);
			DynmapSender sender = null;

			if (!"".equals(name)) {
				final Player online = Bukkit.getPlayerExact(name);

				if (online != null)
					sender = new DynmapSender(name, online.getUniqueId(), online);
			}

			if (sender == null)
				sender = new DynmapSender(Settings.Dynmap.FALLBACK_NAME, CommonCore.ZERO_UUID, null);

			final WrappedSender wrapped = WrappedSender.fromDynmap(sender);

			try {
				final Checker checker = Checker.filterChat(wrapped, message);

				if (checker.isMessageChanged())
					message = checker.getMessage();

				// Do not broadcast anywhere
				if (checker.isCancelledSilently())
					return;

			} catch (final EventHandledException ex) {
				if (ex.isCancelled()) {

					event.setCancelled(true);
					ex.sendErrorMessage(Platform.toPlayer(sender));

					return;
				}
			}

			final Map<String, Object> placeholders = Common.newHashMap(
					"message", message,
					"name", name.isEmpty() ? "web" : name,
					"player_name", name.isEmpty() ? "web" : name,
					"source", source);

			final SimpleComponent component = format.build(wrapped, placeholders);

			for (final Player online : Remain.getOnlinePlayers())
				Platform.toPlayer(online).sendMessage(component);

			if (!Settings.Dynmap.FORMAT_CONSOLE.equals("")) {
				final String consoleMessage = Settings.Dynmap.FORMAT_CONSOLE.equals("@inherit") ? component.toPlain()
						: Variables.builder(Platform.toPlayer(sender)).placeholders(placeholders).replaceComponent(SimpleComponent.fromMiniAmpersand(Settings.Dynmap.FORMAT_CONSOLE)).toLegacySection();

				Platform.log(consoleMessage);
			}

			if (Settings.Dynmap.PROXY && Settings.Proxy.ENABLED)
				ProxyUtil.sendPluginMessage(ChatControlProxyMessage.BROADCAST, component);

			if (Settings.Dynmap.DISCORD_CHANNEL_ID != 0 && Settings.Discord.ENABLED && HookManager.isDiscordSRVLoaded()) {
				final String discordMessage = Settings.Dynmap.FORMAT_DISCORD.equals("@inherit") ? component.toPlain()
						: Variables.builder(Platform.toPlayer(sender)).placeholders(placeholders).replaceComponent(SimpleComponent.fromMiniAmpersand(Settings.Dynmap.FORMAT_DISCORD)).toPlain();

				Discord.getInstance().sendMessage(Settings.Dynmap.DISCORD_CHANNEL_ID, discordMessage);
			}

			// Send back since it's handled and dynmap wont
			sender.sendPlainMessage(component.toPlain());
		}
	}
}