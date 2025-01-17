package org.mineacademy.chatcontrol.command.chatcontrol;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.MainSubCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.TimeUtil;
import org.mineacademy.fo.menu.model.ItemCreator;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.SimpleBook;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.settings.Lang;

public final class BookSubCommand extends MainSubCommand {

	public BookSubCommand() {
		super("book");

		this.setUsage(Lang.component("command-book-usage"));
		this.setDescription(Lang.component("command-book-description"));
		this.setMinArguments(1);
		this.setPermission(Permissions.Command.BOOK);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		return Lang.component("command-book-usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {

		final String param = this.args[0];
		final SenderCache senderCache = SenderCache.from(this.getSender());

		if ("new".equals(param)) {
			this.checkConsole();
			this.checkBoolean(Settings.Mail.ENABLED, Lang.component("command-book-mail-disabled"));

			this.audience.dispatchCommand(Settings.Mail.COMMAND_ALIASES.get(0) + " new");
			this.tellSuccess(Lang.component("command-book-new"));
		}

		else if ("save".equals(param)) {
			final SimpleBook pendingBook = senderCache.getPendingMail();

			this.checkNotNull(pendingBook, Lang.component("command-book-save-no-book"));
			this.checkBoolean(pendingBook.isSigned(), Lang.component("command-book-save-not-signed"));
			this.checkArgs(2, Lang.component("command-book-save-no-name"));
			this.checkUsage(this.args.length == 2);

			try {
				pendingBook.save(this.args[1]);
				senderCache.setPendingMail(null);

				this.tellSuccess(Lang.component("command-book-save", "name", this.args[1]));

			} catch (final IOException ex) {
				ex.printStackTrace();

				this.tellError(Lang.component("command-book-save-error", "name", this.args[1], "error", ex.toString()));
			}

			return;
		}

		else if ("delete".equals(param)) {
			this.checkArgs(2, Lang.component("command-book-no-book", "available", SimpleBook.getBookNames()));

			final String bookName = this.args[1];
			final File bookFile = FileUtil.getFile("books/" + bookName + (bookName.endsWith(".yml") ? "" : ".yml"));
			this.checkBoolean(bookFile.exists(), Lang.component("command-book-invalid", "name", bookName, "available", SimpleBook.getBookNames()));

			final boolean success = bookFile.delete();

			if (success)
				this.tellSuccess(Lang.component("command-book-delete", "name", bookName));
			else
				this.tellError(Lang.component("command-book-delete-fail", "name", bookName));
		}

		else if ("open".equals(param) || "give".equals(param) || "load".equals(param)) {
			this.checkArgs(2, Lang.component("command-book-no-book", "available", SimpleBook.getBookNames()));

			final SimpleBook book;

			try {
				book = SimpleBook.fromFile(this.args[1]);

			} catch (final IllegalArgumentException ex) {
				this.returnTell(ex.getMessage());

				return;
			}

			final Player player = this.findPlayerOrSelf(this.args.length == 3 ? this.args[2] : null);
			final boolean self = player.getName().equals(this.audience.getName());

			if ("open".equals(param)) {
				book.openColorized(Platform.toPlayer(player));

				if (!self)
					this.tell(Lang.component("command-book-open", "player", player.getName()));
			}

			else if ("give".equals(param)) {
				ItemCreator.fromBookColorized(book, false).give(player);

				this.tell(Lang.component("command-book-give", "player", player.getName()));
			}

			else if ("load".equals(param)) {
				senderCache.setPendingMail(book);

				this.tell(Lang.component("command-book-loaded",
						"name", book.getFileName().replace(".yml", ""),
						"label", Settings.Mail.COMMAND_ALIASES.get(0)));
			}
		}

		else if ("list".equals(param)) {
			final List<SimpleComponent> pages = new ArrayList<>();

			for (final SimpleBook book : SimpleBook.getBooks())
				pages.add(SimpleComponent.empty()

						.append(Lang.component("command-book-list-open"))
						.onHover(Lang.component("command-book-list-open-tooltip"))
						.onClickRunCmd("/" + this.getLabel() + " " + this.getSublabel() + " open " + book.getFileName() + " " + this.audience.getName())

						.appendPlain(" ")

						.append(Lang.component("command-book-list-delete"))
						.onHover(Lang.component("command-book-list-delete-tooltip"))
						.onClickRunCmd("/" + this.getLabel() + " " + this.getSublabel() + " delete " + book.getFileName())

						.append(Lang.component("command-book-list",
								"title", book.getTitle(),
								"author", book.getAuthor(),
								"date", TimeUtil.getFormattedDateMonth(book.getLastModified())))
						.onHover(Lang.component("command-book-list-tooltip", "name", book.getFileName()))

				);

			this.checkBoolean(!pages.isEmpty(), Lang.component("command-book-list-none"));

			new ChatPaginator()
					.setFoundationHeader(Lang.legacy("command-book-list-header"))
					.setPages(pages)
					.send(this.audience);

		} else
			this.returnInvalidArgs(param);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (this.args.length == 1)
			return this.completeLastWord("new", "save", "delete", "open", "give", "list", "load");

		if (this.args.length >= 2 && ("save".equals(this.args[0]) || "delete".equals(this.args[0]) || "open".equals(this.args[0]) || "give".equals(this.args[0]) || "load".equals(this.args[0])))
			if (this.args.length == 2)
				return this.completeLastWord(SimpleBook.getBookNames());

			else if (this.args.length == 3 && ("open".equals(this.args[0]) || "give".equals(this.args[0])))
				return this.completeLastWordPlayerNames();

		return NO_COMPLETE;
	}
}
