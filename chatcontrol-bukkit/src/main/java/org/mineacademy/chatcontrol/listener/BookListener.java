package org.mineacademy.chatcontrol.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.model.Colors;
import org.mineacademy.chatcontrol.model.Mute;
import org.mineacademy.chatcontrol.model.RuleType;
import org.mineacademy.chatcontrol.model.Spy;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.model.db.Log;
import org.mineacademy.chatcontrol.model.db.Mail;
import org.mineacademy.chatcontrol.operator.Rule;
import org.mineacademy.chatcontrol.operator.Rule.RuleCheck;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.event.SimpleListener;
import org.mineacademy.fo.model.SimpleBook;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.CompMetadata;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.Lang;

import lombok.Getter;

/**
 * Represents a simple listener for editing books
 */
public final class BookListener extends SimpleListener<PlayerEditBookEvent> {

	/**
	 * The instance of this class
	 */
	@Getter
	private static final BookListener instance = new BookListener();

	/*
	 * Create a new listener
	 */
	private BookListener() {
		super(PlayerEditBookEvent.class, EventPriority.HIGHEST, true);
	}

	/**
	 * @see org.mineacademy.fo.event.SimpleListener#execute(org.bukkit.event.Event)
	 */
	@Override
	protected void execute(final PlayerEditBookEvent event) {
		final Player player = event.getPlayer();
		final ItemStack hand = player.getItemInHand().clone();

		if (!SenderCache.from(player).isDatabaseLoaded())
			return;

		final WrappedSender wrapped = WrappedSender.fromPlayer(player);
		final BookMeta bookMeta = event.getNewBookMeta();
		final boolean signing = event.isSigning();

		// Check chat mute
		if (Mute.isSomethingMutedIf(Settings.Mute.PREVENT_BOOKS, wrapped)) {
			Messenger.warn(player, Lang.component("command-mute-cannot-create-books"));

			event.setCancelled(true);
			return;
		}

		boolean ignoreLogging = false;
		boolean ignoreSpying = false;

		// Check title
		if (bookMeta.hasTitle()) {
			String title = Colors.removeColorsNoPermission(player, bookMeta.getTitle(), Colors.Type.BOOK);
			final RuleCheck<Rule> check = Rule.filter(RuleType.BOOK, wrapped, title);

			if (check.isLoggingIgnored())
				ignoreLogging = true;

			if (check.isSpyingIgnored())
				ignoreSpying = true;

			boolean changed = false;

			if (check.isMessageChanged()) {
				title = check.getMessage();

				changed = true;
			}

			if (check.getMessage().isEmpty())
				this.cancel();

			if (changed || Settings.Colors.APPLY_ON.contains(Colors.Type.BOOK))
				bookMeta.setTitle(SimpleComponent.fromMiniSection(title).toLegacySection());
		}

		// Check pages
		if (bookMeta.hasPages()) {
			final List<String> oldPages = bookMeta.getPages();
			final List<String> newPages = new ArrayList<>();
			boolean changed = false;

			for (String page : oldPages) {
				if (signing)
					page = Colors.removeColorsNoPermission(player, page, Colors.Type.BOOK);

				final RuleCheck<Rule> pageCheck = Rule.filter(RuleType.BOOK, wrapped, page);

				if (pageCheck.isLoggingIgnored())
					ignoreLogging = true;

				if (pageCheck.isSpyingIgnored())
					ignoreSpying = true;

				if (pageCheck.isMessageChanged()) {
					changed = true;

					page = pageCheck.getMessage();

					if (!signing)
						page = SimpleComponent.fromMiniSection(page).toPlain();
				}

				newPages.add(page);
			}

			if (changed || Settings.Colors.APPLY_ON.contains(Colors.Type.BOOK)) {
				if (signing)
					Remain.setPages(bookMeta, CommonCore.convertList(newPages, page -> SimpleComponent.fromMiniSection(ChatColor.stripColor(page))));
				else
					bookMeta.setPages(newPages);
			}
		}

		// Modify and inject our mail title incase we are replying to someone
		if (wrapped.getSenderCache().getPendingMailReply() != null) {
			final String mailTitle = wrapped.getSenderCache().getPendingMailReply().getSubject();

			bookMeta.setTitle((mailTitle.startsWith("Re: ") ? "" : "Re: ") + mailTitle);
		}

		// Update the book
		event.setNewBookMeta(bookMeta);

		// Register in cache
		final UUID uniqueId = UUID.randomUUID();
		final SimpleBook book = convertEventToBook(event, uniqueId);

		// Handle mails
		if (CompMetadata.hasMetadata(hand, SimpleBook.TAG)) {

			// Save it
			wrapped.getSenderCache().setPendingMail(book);

			if (wrapped.getSenderCache().getPendingMailReply() != null) {
				final Mail replyMail = wrapped.getSenderCache().getPendingMailReply();

				book.setAuthor(player.getName());
				book.setSigned(true); // Ignore signing since we reuse old title

				player.chat("/" + Settings.Mail.COMMAND_ALIASES.get(0) + " send " + replyMail.getSenderName());

				// Disable the reply
				wrapped.getSenderCache().setPendingMailReply(null);

			} else
				Messenger.info(player, Lang.component(event.isSigning() ? "command-mail-ready" : "command-mail-draft-saved"));

			// Remove hand item
			Remain.runEntityTask(player, 0, () -> {
				player.setItemInHand(new ItemStack(CompMaterial.AIR.getMaterial()));
				player.updateInventory();
			});

			return;
		}

		// Log
		if (!ignoreLogging)
			Log.logBook(player, book);

		// Broadcast to spying players
		if (!ignoreSpying)
			Spy.broadcastBook(wrapped, book, uniqueId);
	}

	/**
	 * Make a {@link SimpleBook} from the given book edit event
	 *
	 * @param event
	 * @param uniqueId
	 *
	 * @return
	 */
	private static SimpleBook convertEventToBook(final PlayerEditBookEvent event, final UUID uniqueId) {
		final BookMeta meta = event.getNewBookMeta();

		final String title = meta.getTitle();
		final String author = meta.getAuthor();
		final List<String> pages = meta.getPages();
		final boolean signed = event.isSigning();
		final long lastModified = System.currentTimeMillis();

		return new SimpleBook(title, author, pages, signed, lastModified, null, uniqueId);
	}
}
