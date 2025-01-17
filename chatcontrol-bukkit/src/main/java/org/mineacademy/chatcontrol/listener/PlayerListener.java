package org.mineacademy.chatcontrol.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Colors;
import org.mineacademy.chatcontrol.model.Mute;
import org.mineacademy.chatcontrol.model.Newcomer;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.PlayerMessageType;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.model.ProxyChat;
import org.mineacademy.chatcontrol.model.RuleType;
import org.mineacademy.chatcontrol.model.Spy;
import org.mineacademy.chatcontrol.model.ToggleType;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.model.db.Database;
import org.mineacademy.chatcontrol.model.db.Log;
import org.mineacademy.chatcontrol.model.db.Mail;
import org.mineacademy.chatcontrol.model.db.Mail.Recipient;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.operator.PlayerMessages;
import org.mineacademy.chatcontrol.operator.Rule;
import org.mineacademy.chatcontrol.operator.Rule.RuleCheck;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.AntiBot;
import org.mineacademy.chatcontrol.util.LogUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.model.CompChatColor;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleBook;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Task;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.CompMetadata;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.Lang;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The general listener for player events
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class PlayerListener implements Listener {

	/**
	 * The singleton instance
	 */
	@Getter
	private static final PlayerListener instance = new PlayerListener();

	/**
	 * Listen for pre-login and handle antibot logic
	 *
	 * @param event
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPreLogin(final AsyncPlayerPreLoginEvent event) {
		final String playerName = event.getName();
		final UUID uniqueId = event.getUniqueId();

		final OfflinePlayer offline = Remain.getOfflinePlayerByUniqueId(uniqueId);

		// Disallowed usernames
		if (AntiBot.DISALLOWED_USERNAMES_LIST.isInListRegex(playerName) && (offline == null || !HookManager.hasVaultPermission(offline, Permissions.Bypass.LOGIN_USERNAMES))) {
			for (final String command : AntiBot.DISALLOWED_USERNAMES_COMMANDS)
				Platform.dispatchConsoleCommand(null, command.replace("{uuid}", uniqueId.toString()).replace("{player}", playerName));

			event.setKickMessage(Lang.legacy("player-kick-disallowed-nickname"));
			event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
			return;
		}
	}

	/**
	 * Listen for join events and perform plugin logic
	 *
	 * @param event
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onJoin(final PlayerJoinEvent event) {
		final Player player = event.getPlayer();
		final UUID uniqueId = player.getUniqueId();

		final SenderCache senderCache = SenderCache.from(player);
		final Database database = Database.getInstance();

		// Reset flags
		senderCache.setDatabaseLoaded(false);
		senderCache.setMovedFromJoin(false);
		senderCache.setJoinLocation(player.getLocation());
		senderCache.setLastLogin(System.currentTimeMillis());

		// Give permissions early so we can use them already below
		if (Newcomer.isNewcomer(player))
			Newcomer.givePermissions(player);

		// Disable Bukkit message if we handle that
		if (Settings.Messages.APPLY_ON.contains(PlayerMessageType.JOIN))
			event.setJoinMessage(null);

		// Moves MySQL off of the main thread
		// Delays the execution so that, if player comes from another server,
		// his data is saved first in case database has slower connection than us
		database.loadAndStoreCache(player, senderCache, cache -> {
			final WrappedSender wrapped = WrappedSender.fromPlayerCaches(player, cache, senderCache);

			// Force load all synced data
			ProxyChat.loadAllSyncedData(wrapped);

			// Update tablist name from nick
			Players.setTablistName(wrapped);

			// Remove old channels over limit
			cache.checkLimits(player);

			// Auto join channels
			if (Settings.Channels.ENABLED)
				Channel.autoJoin(player, cache);

			// Motd
			if (Settings.Motd.ENABLED) {
				Players.showMotd(wrapped, true);

				Platform.runTask(Settings.Motd.DELAY.getTimeTicks(), () -> {
					for (final String consoleCmd : Settings.Motd.CONSOLE_COMMANDS)
						Platform.dispatchConsoleCommand(wrapped.getAudience(), consoleCmd);

					for (final String playerCmd : Settings.Motd.PLAYER_COMMANDS)
						wrapped.getAudience().dispatchCommand(playerCmd);
				});
			}

			if (Settings.PrivateMessages.DISABLED_BY_DEFAULT && !cache.hasManuallyToggledPMs()) {
				cache.setToggledPart(ToggleType.PRIVATE_MESSAGE, true);

				LogUtil.logTip("TIP: " + player.getName() + " did not manually toggle on private messages. He will not be able to send/receive them until he toggles them back on.");
			}

			// Spying
			if (player.hasPermission(Permissions.Spy.AUTO_ENABLE) && !Settings.Spy.APPLY_ON.isEmpty()) {
				cache.setSpyingOn(player);

				if (!Lang.plain("command-spy-auto-enable-1").equals("none"))
					wrapped.getAudience().sendMessage(Lang
							.component("command-spy-auto-enable-1")
							.append(Lang.component("command-spy-auto-enable-2"))
							.onHover(Lang.component("command-spy-auto-enable-tooltip", "permission", Permissions.Spy.AUTO_ENABLE)));

				LogUtil.logOnce("spy-autojoin", "TIP: Automatically enabling spy mode for " + player.getName() + " because he has '" + Permissions.Spy.AUTO_ENABLE + "'"
						+ " permission. To stop automatically enabling spy mode for players, give them negative '" + Permissions.Spy.AUTO_ENABLE + "' permission"
						+ " (a value of false when using LuckPerms).");
			}

			// Unread mail notification
			if (Settings.Mail.ENABLED && player.hasPermission(Permissions.Command.MAIL))
				Platform.runTaskAsync(() -> {
					int unreadCount = 0;

					for (final Mail mail : database.findMailsTo(uniqueId)) {
						final Recipient recipient = mail.findRecipient(uniqueId);

						if (!recipient.isMarkedDeleted() && !recipient.isOpened())
							unreadCount++;
					}

					if (unreadCount > 0) {
						final int finalUnreadCount = unreadCount;

						Platform.runTask(4, () -> Messenger.warn(player, Lang.component("command-mail-join-notification", "amount", finalUnreadCount)));
					}
				});

			senderCache.setJoinMessage(CommonCore.getOrEmpty(event.getJoinMessage()));

			if (HookManager.isAuthMeLoaded() && Settings.AuthMe.DELAY_JOIN_MESSAGE_UNTIL_LOGGED) {
				// Do nothing
			} else
				senderCache.sendJoinMessage(wrapped);
		});
	}

	/**
	 * Handle player being kicked
	 *
	 * @param event
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onKick(final PlayerKickEvent event) {
		final Player player = event.getPlayer();
		final SenderCache senderCache = SenderCache.from(player);

		// Prevent disconnect spam if having permission
		if ((event.getReason().equals("disconnect.spam") || event.getReason().equalsIgnoreCase("kicked for spamming")) && !player.hasPermission(Permissions.Bypass.SPAM_KICK)) {
			event.setCancelled(true);

			LogUtil.logOnce("spamkick", "TIP: " + player.getName() + " was kicked for chatting or running commands rapidly. " +
					" If you are getting kicked when removing messages with [X], give yourself " + Permissions.Bypass.SPAM_KICK + " permission.");
			return;
		}

		if (!senderCache.isDatabaseLoaded() || !PlayerCache.isCached(player)) {
			Common.warning("Silencing kick message for " + player.getName() + " as his database was not loaded yet.");

			try {
				event.setLeaveMessage(null);

			} catch (final NullPointerException ex) {

				// Solve an odd bug in Paper
				event.setLeaveMessage("");
			}

			return;
		}

		final WrappedSender wrapped = WrappedSender.fromPlayer(player);

		// Custom message
		if (Settings.Messages.APPLY_ON.contains(PlayerMessageType.KICK)) {
			if ((!Mute.isSomethingMutedIf(Settings.Mute.HIDE_QUITS, wrapped) || Settings.Mute.SOFT_HIDE) && !PlayerUtil.isVanished(player))
				PlayerMessages.broadcast(PlayerMessageType.KICK, wrapped, event.getLeaveMessage());

			try {
				event.setLeaveMessage(null);

			} catch (final NullPointerException ex) {

				// Solve an odd bug in Paper
				event.setLeaveMessage("");
			}
		}
	}

	/**
	 * Handle player leave
	 *
	 * @param event
	 */
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onQuit(final PlayerQuitEvent event) {
		final Player player = event.getPlayer();
		final SenderCache senderCache = SenderCache.from(player);

		if (senderCache.getCacheLoadingTask() != null) {
			final Task task = senderCache.getCacheLoadingTask();

			senderCache.setCacheLoadingTask(null);
			task.cancel();
		}

		if (!senderCache.isDatabaseLoaded()) {
			Common.warning("Silencing quit message for " + player.getName() + " as his database was not loaded yet.");

			try {
				event.setQuitMessage(null);

			} catch (final NullPointerException ex) {

				// Solve an odd bug in Paper
				event.setQuitMessage("");
			}

			return;
		}

		final WrappedSender wrapped = WrappedSender.fromPlayer(player);

		wrapped.getSenderCache().setDatabaseLoaded(false);
		wrapped.getSenderCache().setPendingProxyJoinMessage(false);

		boolean hasQuitMessage = true;

		// Disable flag for next time
		wrapped.getSenderCache().setJoinFloodActivated(false);

		// AuthMe
		if (Settings.AuthMe.HIDE_QUIT_MESSAGE_IF_NOT_LOGGED && !HookManager.isLogged(player))
			hasQuitMessage = false;

		// Custom message
		if (hasQuitMessage && Settings.Messages.APPLY_ON.contains(PlayerMessageType.QUIT)) {
			if ((!Mute.isSomethingMutedIf(Settings.Mute.HIDE_QUITS, wrapped) || Settings.Mute.SOFT_HIDE) && !PlayerUtil.isVanished(player))
				PlayerMessages.broadcast(PlayerMessageType.QUIT, wrapped, event.getQuitMessage());

			hasQuitMessage = false;
		}

		if (!hasQuitMessage)
			event.setQuitMessage(null);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onDeath(final PlayerDeathEvent event) {
		final Player player = event.getEntity();

		if (!SenderCache.from(player).isDatabaseLoaded())
			return;

		final WrappedSender wrapped = WrappedSender.fromPlayer(player);

		// Custom message
		if (Settings.Messages.APPLY_ON.contains(PlayerMessageType.DEATH)) {
			if (Settings.CoreArena.IGNORE_DEATH_MESSAGES && player.hasMetadata("CoreArena_Arena")) {
				event.setDeathMessage(null);

				return;
			}

			if (!Mute.isSomethingMutedIf(Settings.Mute.HIDE_DEATHS, wrapped) || Settings.Mute.SOFT_HIDE)
				try {
					PlayerMessages.broadcast(PlayerMessageType.DEATH, wrapped, event.getDeathMessage());

				} catch (final EventHandledException ex) {
					// Handled upstream
				}

			event.setDeathMessage(null);
		}
	}

	/**
	 * Handle editing signs
	 *
	 * @param event
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onSign(final SignChangeEvent event) {
		final Player player = event.getPlayer();

		if (!SenderCache.from(player).isDatabaseLoaded())
			return;

		final WrappedSender wrapped = WrappedSender.fromPlayer(player);

		final Block block = event.getBlock();
		final Material material = block.getType();

		final String[] lines = event.getLines().clone();
		final String[] lastLines = CommonCore.getOrDefault(wrapped.getSenderCache().getLastSignText(), new String[] { "" });

		if (ValidCore.isNullOrEmpty(lines))
			return;

		// Check mute
		if (Mute.isSomethingMutedIf(Settings.Mute.PREVENT_SIGNS, wrapped)) {
			Messenger.warn(player, Lang.component("command-mute-cannot-place-signs"));

			event.setCancelled(true);
			return;
		}

		// Prevent crashing the server with too long lines text
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];

			if (line.length() > 49) {
				line = line.substring(0, 49);

				lines[i] = line;
				event.setLine(i, line);
			}
		}

		if (Settings.AntiBot.BLOCK_SAME_TEXT_SIGNS && !player.hasPermission(Permissions.Bypass.SIGN_DUPLICATION))
			if (ValidCore.listEquals(lines, lastLines)) {
				Messenger.error(player, Lang.component("checker-sign-duplication"));

				event.setCancelled(true);
				return;
			} else
				wrapped.getSenderCache().setLastSignText(lines);

		boolean cancelSilently = false;
		boolean ignoreLogging = false;
		boolean ignoreSpying = false;
		final List<Integer> linesChanged = new ArrayList<>();

		try {

			// First try to join the lines without space to prevent player
			// bypassing rules by simply splitting the string over multiple lines
			if (Settings.Rules.SIGNS_CHECK_MODE == 1 || Settings.Rules.SIGNS_CHECK_MODE == 3) {
				final String originalMessage = Colors.removeColorsNoPermission(player, String.join(" ", lines), Colors.Type.SIGN);
				final RuleCheck<Rule> allLinesCheck = Rule.filter(RuleType.SIGN, wrapped, originalMessage, CommonCore.newHashMap("sign_lines", originalMessage));

				if (allLinesCheck.isCancelledSilently())
					cancelSilently = true;

				if (allLinesCheck.isLoggingIgnored())
					ignoreLogging = true;

				if (allLinesCheck.isSpyingIgnored())
					ignoreSpying = true;

				if (allLinesCheck.isMessageChanged()) {

					// In this case, we will have to rerender the line order
					// and simply merge everything together (spaces will be lost)
					final String[] split = CommonCore.split(SimpleComponent.fromMiniNative(allLinesCheck.getMessage()).toLegacySection(), 15);

					for (int i = 0; i < 4; i++) {
						final String replacement = i < split.length ? split[i] : "";

						event.setLine(i, replacement);
						linesChanged.add(i);
					}
				}
			}

			// Also evaluate rules on a per line basis
			if (Settings.Rules.SIGNS_CHECK_MODE == 2 || Settings.Rules.SIGNS_CHECK_MODE == 3) {
				for (int i = 0; i < event.getLines().length; i++) {
					final String line = Colors.removeColorsNoPermission(player, event.getLine(i), Colors.Type.SIGN);

					final RuleCheck<Rule> lineCheck = Rule.filter(RuleType.SIGN, wrapped, line);

					if (lineCheck.isCancelledSilently())
						cancelSilently = true;

					if (lineCheck.isLoggingIgnored())
						ignoreLogging = true;

					if (lineCheck.isSpyingIgnored())
						ignoreSpying = true;

					if (lineCheck.isMessageChanged()) {
						event.setLine(i, CommonCore.limit(SimpleComponent.fromMiniNative(lineCheck.getMessage()).toLegacySection(), 15));
						linesChanged.add(i);
					}
				}
			}

			// Update the rest manually with colors
			if (Settings.Colors.APPLY_ON.contains(Colors.Type.SIGN))
				for (int i = 0; i < 4; i++)
					if (!linesChanged.contains(i))
						event.setLine(i, SimpleComponent.fromMiniNative(Colors.removeColorsNoPermission(player, event.getLine(i), Colors.Type.SIGN)).toLegacySection());

			// If rule is silent, send packet back as if the sign remained unchanged
			if (cancelSilently)
				Platform.runTask(2, () -> {

					// Check for the rare chance that the block has been changed
					if (block.getLocation().getBlock().getType().equals(material))
						player.sendSignChange(block.getLocation(), lines);
				});

		} catch (final EventHandledException ex) {
			event.setCancelled(true);

			return;
		}

		// Send the final message to spying players and log if the block is still a valid sign
		if (block.getState() instanceof Sign) {

			if (!ignoreLogging)
				Log.logSign(player, event.getLines());

			if (!ignoreSpying)
				Spy.broadcastSign(wrapped, event.getLines());
		}
	}

	/**
	 * Handler for inventory clicking
	 *
	 * @param event
	 */
	@EventHandler(ignoreCancelled = true)
	public void onClick(final InventoryClickEvent event) {
		final Player player = (Player) event.getWhoClicked();

		if (!SenderCache.from(player).isDatabaseLoaded())
			return;

		final WrappedSender wrapped = WrappedSender.fromPlayer(player);
		final ItemStack currentItem = event.getCurrentItem();

		// Check anvil rules
		if (event.getInventory().getType() == InventoryType.ANVIL && event.getSlotType() == InventoryType.SlotType.RESULT && currentItem.hasItemMeta() && currentItem.getItemMeta().hasDisplayName()) {
			final ItemMeta meta = currentItem.getItemMeta();
			String itemName = Colors.removeColorsNoPermission(player, meta.getDisplayName(), Colors.Type.ANVIL);

			// Check mute
			if (Mute.isSomethingMutedIf(Settings.Mute.PREVENT_ANVIL, wrapped)) {
				Messenger.warn(player, Lang.component("command-mute-cannot-rename-items"));

				event.setCancelled(true);
				return;
			}

			try {
				final RuleCheck<Rule> check = Rule.filter(RuleType.ANVIL, wrapped, itemName);

				if (check.isMessageChanged())
					itemName = check.getMessage();

				itemName = itemName.trim();

				if (CompChatColor.stripColorCodes(itemName).isEmpty())
					throw new EventHandledException(true);

				meta.setDisplayName(SimpleComponent.fromMiniNative(itemName).toLegacySection());

				currentItem.setItemMeta(meta);
				event.setCurrentItem(currentItem);

				// Send to spying players
				if (!check.isSpyingIgnored())
					Spy.broadcastAnvil(wrapped, currentItem);

				// Log
				if (!check.isLoggingIgnored())
					Log.logAnvil(player, currentItem);

			} catch (final EventHandledException ex) {
				if (ex.isCancelled())
					event.setCancelled(true);
			}
		}
	}

	/* ------------------------------------------------------------------------------- */
	/* Mail */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Monitor player dropping the draft and remove it.
	 *
	 * @param event
	 */
	@EventHandler
	public void onItemDrop(final PlayerDropItemEvent event) {
		final ItemStack item = event.getItemDrop().getItemStack();
		final Player player = event.getPlayer();

		if (CompMetadata.hasMetadata(item, SimpleBook.TAG)) {
			this.discardBook(player, event);

			Platform.runTask(() -> player.setItemInHand(new ItemStack(CompMaterial.AIR.getMaterial())));
		}
	}

	/**
	 * Monitor player clicking anywhere holding the draft and remove it.
	 *
	 * @param event
	 */
	@EventHandler
	public void onInventoryClick(final InventoryClickEvent event) {
		final Player player = (Player) event.getWhoClicked();
		final ItemStack clicked = event.getCurrentItem();
		final ItemStack cursor = event.getCursor();

		if (cursor != null && CompMetadata.hasMetadata(player, SimpleBook.TAG) || clicked != null && CompMetadata.hasMetadata(clicked, SimpleBook.TAG)) {
			event.setCursor(new ItemStack(CompMaterial.AIR.getMaterial()));
			event.setCurrentItem(new ItemStack(CompMaterial.AIR.getMaterial()));

			this.discardBook(player, event);
		}
	}

	/*
	 * Discards the pending mail if any
	 */
	private void discardBook(final Player player, final Cancellable event) {
		event.setCancelled(true);

		SenderCache.from(player).setPendingMail(null);
		Messenger.info(player, Lang.component("command-mail-draft-discarded"));

		Platform.runTask(() -> player.updateInventory());
	}
}