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
import org.mineacademy.chatcontrol.model.Colors;
import org.mineacademy.chatcontrol.model.Mute;
import org.mineacademy.chatcontrol.model.Newcomer;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.PlayerMessageType;
import org.mineacademy.chatcontrol.model.RuleType;
import org.mineacademy.chatcontrol.model.Spy;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.model.db.Database;
import org.mineacademy.chatcontrol.model.db.Log;
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
import org.mineacademy.fo.debug.Debugger;
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
		if (HookManager.isVaultLoaded())
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
		if (HookManager.isAuthMeLoaded() && Settings.AuthMe.DELAY_JOIN_MESSAGE_UNTIL_LOGGED) {
			Debugger.debug("player-message", "Waiting for " + player.getName() + " to log in AuthMe before loading his data and join message.");

		} else
			database.loadAndStoreCache(player, senderCache, cache -> cache.onJoin(player, senderCache, event.getJoinMessage()));
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

			PlayerCache.remove(player);
			return;
		}

		final WrappedSender wrapped = WrappedSender.fromPlayer(player);

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

		// Mark as unloaded after broadcasting so the quitting player is still
		// included in the receiver list during the broadcast, ensuring global
		// operators like 'then discord' execute even when no other players are online
		wrapped.getSenderCache().setDatabaseLoaded(false);

		// This data is stored in the database, so we can remove it from memory always
		PlayerCache.remove(player);

		if (Settings.CLEAR_CACHE_ON_EXIT)
			SenderCache.remove(player);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onDeath(final PlayerDeathEvent event) {
		final Player player = event.getEntity();

		if (!SenderCache.from(player).isDatabaseLoaded())
			return;

		final WrappedSender wrapped = WrappedSender.fromPlayer(player);

		// Custom message
		if (Settings.Messages.APPLY_ON.contains(PlayerMessageType.DEATH)) {
			if (player.hasMetadata("CoreArena_Arena")) {
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
					final String[] split = CommonCore.split(SimpleComponent.fromMiniSection(allLinesCheck.getMessage()).toLegacySection(), 15);

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
						event.setLine(i, CommonCore.limit(SimpleComponent.fromMiniSection(lineCheck.getMessage()).toLegacySection(), 15));
						linesChanged.add(i);
					}
				}
			}

			// Update the rest manually with colors
			if (Settings.Colors.APPLY_ON.contains(Colors.Type.SIGN))
				for (int i = 0; i < 4; i++)
					if (!linesChanged.contains(i))
						event.setLine(i, SimpleComponent.fromMiniSection(Colors.removeColorsNoPermission(player, event.getLine(i), Colors.Type.SIGN)).toLegacySection());

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
			final boolean enabledOnAnvil = Settings.Colors.APPLY_ON.contains(Colors.Type.ANVIL);

			LogUtil.logOnce("anvil-colors", "Applying rules to Anvil. If you wish players to use colors on items, give them 'chatcontrol.use.color.anvil' permission.");
			String itemName = enabledOnAnvil ? Colors.removeColorsNoPermission(player, meta.getDisplayName(), Colors.Type.ANVIL) : meta.getDisplayName();

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

				if (check.isMessageChanged() || enabledOnAnvil) {
					itemName = itemName.trim();

					if (CompChatColor.stripColorCodes(itemName).isEmpty())
						throw new EventHandledException(true);

					meta.setDisplayName(SimpleComponent.fromMiniSection(itemName).toLegacySection());

					currentItem.setItemMeta(meta);
					event.setCurrentItem(currentItem);
				}

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