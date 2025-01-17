package org.mineacademy.chatcontrol.command;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Spy;
import org.mineacademy.chatcontrol.model.ToggleType;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.model.db.Database;
import org.mineacademy.chatcontrol.model.db.Log;
import org.mineacademy.chatcontrol.model.db.Mail;
import org.mineacademy.chatcontrol.model.db.Mail.Recipient;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.TimeUtil;
import org.mineacademy.fo.conversation.SimplePrompt;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.menu.model.ItemCreator;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.SimpleBook;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.platform.FoundationPlayer;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.CompMetadata;
import org.mineacademy.fo.settings.Lang;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public final class CommandMail extends ChatControlCommand {

	public CommandMail() {
		super(Settings.Mail.COMMAND_ALIASES);

		this.setUsage(Lang.component("command-mail-usage"));
		this.setDescription(Lang.component("command-mail-description"));
		this.setMinArguments(1);
		this.setPermission(Permissions.Command.MAIL);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		return Lang.component("command-mail-usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		this.checkConsole();

		final String param = this.args[0];
		final Player player = this.getPlayer();

		final Database database = Database.getInstance();
		final WrappedSender wrapped = WrappedSender.fromPlayer(player);

		final UUID uuid = wrapped.getUniqueId();
		final SenderCache senderCache = wrapped.getSenderCache();
		final PlayerCache playerCache = wrapped.getPlayerCache();

		final SimpleBook pendingBody = CommonCore.getOrDefault(senderCache.getPendingMail(), SimpleBook.newEmptyBook());

		if ("send".equals(param) || "s".equals(param)) {
			this.checkNotNull(senderCache.getPendingMail(), Lang.component("command-mail-no-pending"));
			this.checkBoolean(pendingBody.isSigned(), Lang.component("command-mail-no-subject"));
			this.checkArgs(2, Lang.component("command-mail-no-recipients"));
			this.checkUsage(this.args.length == 2);

			this.sendMail(wrapped, this.args[1], pendingBody);

			return;
		}

		else if ("autoreply".equals(param) || "autor".equals(param) || "ar".equals(param)) {
			this.checkArgs(2, Lang.component("command-mail-autoresponder-usage"));

			final String timeRaw = this.joinArgs(1);

			if ("off".equals(timeRaw) || "view".equals(timeRaw)) {
				this.checkBoolean(playerCache.isAutoResponderValid(), Lang.component("command-mail-autoresponder-disabled"));

				final Tuple<SimpleBook, Long> autoResponder = playerCache.getAutoResponder();

				if ("off".equals(timeRaw)) {
					playerCache.removeAutoResponder();

					this.tellSuccess(Lang.component("command-mail-autoresponder-removed"));
				} else {
					autoResponder.getKey().openPlain(wrapped.getAudience());

					this.tellSuccess(Lang.component("command-mail-autoresponder",
							"title", autoResponder.getKey().getTitle(),
							"date", TimeUtil.getFormattedDateShort(autoResponder.getValue())));
				}

				return;
			}

			long futureTime;

			try {
				futureTime = System.currentTimeMillis() + this.findTime(timeRaw).getTimeSeconds() * 1000;

			} catch (final FoException ex) {
				this.returnTell(Lang.component("command-mail-autoresponder-invalid-time", "time", timeRaw));

				return;
			}

			// If has no email, try updating current auto-responder's date
			if (senderCache.getPendingMail() == null) {
				this.checkBoolean(playerCache.hasAutoResponder(), Lang.component("command-mail-autoresponder-missing"));

				playerCache.setAutoResponderDate(futureTime);

				this.tellSuccess(Lang.component("command-mail-autoresponder-updated", "date", TimeUtil.getFormattedDateShort(futureTime)));
				return;
			}

			this.checkBoolean(pendingBody.isSigned(), Lang.component("command-mail-no-subject"));

			// Save
			playerCache.setAutoResponder(senderCache.getPendingMail(), futureTime);

			// Remove draft from cache because it was finished
			senderCache.setPendingMail(null);

			this.tellSuccess(Lang.component("command-mail-autoresponder-set", "date", TimeUtil.getFormattedDateShort(futureTime)));
			return;
		}

		else if ("open".equals(param) || "forward".equals(param) || "reply".equals(param) || "delete-sender".equals(param) || "delete-recipient".equals(param)) {
			this.checkArgs(2, "Unique mail ID has not been provided. If this is a bug, please report it. If you are playing hard, play harder!");

			final UUID mailId = UUID.fromString(this.args[1]);

			this.syncCallback(() -> database.findMail(mailId), mail -> {
				this.checkNotNull(mail, Lang.component("command-mail-delete-invalid"));

				if ("open".equals(param)) {
					mail.displayTo(this.audience);

					if (!String.join(" ", this.args).contains("-donotmarkasread"))
						mail.markOpen(player);

				} else if ("forward".equals(param)) {

					// No recipients
					if (this.args.length == 2)
						new PromptForwardRecipients(mailId).show(player);

					else if (this.args.length == 3)
						this.sendMail(wrapped, this.args[2], SimpleBook.clone(mail.getBody(), player.getName()));

					else
						this.returnInvalidArgs(this.joinArgs(1));
				}

				else if ("reply".equals(param)) {
					this.checkConsole();

					senderCache.setPendingMailReply(mail);

					this.getPlayer().chat("/" + this.getLabel() + " new");
				}

				else if ("delete-sender".equals(param)) {
					this.checkBoolean(!mail.isSenderDeleted(), Lang.component("command-mail-delete-invalid"));

					mail.setSenderDeleted(true);

					this.tellSuccess(Lang.component("command-mail-delete-sender"));
				}

				else if ("delete-recipient".equals(param)) {
					this.checkArgs(3, Lang.component("command-mail-delete-no-recipient"));

					final UUID recipientId = UUID.fromString(this.args[2]);
					final Recipient recipient = mail.findRecipient(recipientId);
					this.checkNotNull(recipient, Lang.component("command-mail-delete-invalid-recipient", "uuid", recipientId));

					this.checkBoolean(!recipient.isMarkedDeleted(), Lang.component("command-mail-delete-invalid"));

					mail.setDeletedBy(recipient);

					this.tellSuccess(Lang.component("command-mail-delete-recipient"));
				}
			});

			return;
		}

		this.checkUsage(this.args.length == 1);

		if ("new".equals(param) || "n".equals(param)) {
			this.checkUsage(this.args.length == 1);
			this.checkBoolean(CompMaterial.isAir(player.getItemInHand().getType()), Lang.component("command-mail-hand-full"));

			for (final ItemStack stack : player.getInventory().getContents())
				if (stack != null && CompMetadata.hasMetadata(stack, SimpleBook.TAG))
					this.returnTell(Lang.component("command-mail-already-drafting"));

			final ItemStack bookItem = ItemCreator
					.fromBookPlain(pendingBody, true)
					.name(Lang.legacy("command-mail-item-title"))
					.lore(Lang.legacy("command-mail-item-tooltip"))
					.make();

			player.setItemInHand(bookItem);

			if (senderCache.getPendingMailReply() != null)
				this.tellInfo(Lang.component("command-mail-reply-usage", "player", senderCache.getPendingMailReply().getSenderName()));
			else
				this.tellInfo(Lang.component("command-mail-new-usage-" + (senderCache.getPendingMail() == null ? "new" : "pending")));

		} else if ("inbox".equals(param) || "i".equals(param) || "read".equals(param)) {
			this.syncCallback(() -> database.findMailsTo(uuid), mails -> {
				final List<SimpleComponent> pages = new ArrayList<>();

				for (final Mail incoming : mails) {
					final Recipient recipient = incoming.findRecipient(uuid);
					final boolean read = recipient.isOpened();

					// Hide deleted emails
					if (recipient.isMarkedDeleted())
						continue;

					final List<SimpleComponent> openHover = new ArrayList<>();

					openHover.add(Lang.component("command-mail-open-tooltip"));
					openHover.add(Lang.component("command-mail-open-tooltip-script-" + (read ? "read" : "unread"),
							"date", TimeUtil.getFormattedDateShort(recipient.getOpenTime())));

					pages.add(SimpleComponent
							.fromMiniNative("<dark_gray>[" + (read ? "<gray>" : "<dark_green>") + "O<dark_gray>]")
							.onHover(openHover)
							.onClickRunCmd("/" + this.getLabel() + " open " + incoming.getUniqueId())

							.appendPlain(" ")

							.appendMiniNative("<dark_gray>[<gold>R<dark_gray>]")
							.onHover(Lang.component("command-mail-reply-tooltip"))
							.onClickRunCmd("/" + this.getLabel() + " reply " + incoming.getUniqueId())

							.appendPlain(" ")

							.appendMiniNative("<dark_gray>[<dark_aqua>F<dark_gray>]")
							.onHover(Lang.component("command-mail-forward-tooltip"))
							.onClickRunCmd("/" + this.getLabel() + " forward " + incoming.getUniqueId())

							.appendPlain(" ")

							.appendMiniNative("<dark_gray>[<red>X<dark_gray>]<white>")
							.onHover(Lang.component("command-mail-delete-tooltip"))
							.onClickRunCmd("/" + this.getLabel() + " delete-recipient " + incoming.getUniqueId() + " " + player.getUniqueId())

							.append(Lang.component("command-mail-inbox-line",
									"subject", CommonCore.getOrDefault(incoming.getSubject(), ChatUtil.capitalize(Lang.plain("part-blank"))),
									"sender", CommonCore.getOrDefault(incoming.getSenderName(), ChatUtil.capitalize(Lang.plain("part-unknown"))),
									"date", TimeUtil.getFormattedDateMonth(incoming.getDate()))));
				}

				this.checkBoolean(!pages.isEmpty(), Lang.component("command-mail-no-incoming-mail"));

				new ChatPaginator()
						.setFoundationHeader(Lang.legacy("command-mail-inbox-header"))
						.setPages(pages)
						.send(this.audience);
			});

		} else if ("archive".equals(param) || "a".equals(param) || "sent".equals(param)) {
			this.syncCallback(() -> database.findMailsFrom(uuid), mails -> {
				final List<SimpleComponent> pages = new ArrayList<>();

				for (final Mail outgoing : mails) {

					// Hide deleted emails
					if (outgoing.isSenderDeleted() || outgoing.isAutoReply())
						continue;

					final List<SimpleComponent> statusHover = new ArrayList<>();
					statusHover.add(Lang.component("command-mail-archive-recipients-tooltip"));

					final List<Recipient> recipients = outgoing.getRecipients();

					int index = 0;
					for (final String recipientName : database.getPlayerNamesSync(CommonCore.convertList(recipients, Recipient::getUniqueId))) {
						final Recipient recipient = recipients.get(index);

						statusHover.add(Lang.component("command-mail-archive-tooltip-" + (recipient.isOpened() ? "read" : "unread"), "recipient", recipientName));
						index++;
					}

					pages.add(SimpleComponent.empty()

							.appendMiniNative("<dark_gray>[<gold>O<dark_gray>]")
							.onHover(Lang.component("command-mail-open-tooltip"))
							.onClickRunCmd("/" + this.getLabel() + " open " + outgoing.getUniqueId() + " -donotmarkasread")

							.appendPlain(" ")

							.appendMiniNative("<dark_gray>[<dark_aqua>F<dark_gray>]")
							.onHover(Lang.component("command-mail-forward-tooltip"))
							.onClickRunCmd("/" + this.getLabel() + " forward " + outgoing.getUniqueId())

							.appendPlain(" ")

							.appendMiniNative("<dark_gray>[<red>X<dark_gray>]")
							.onHover(Lang.component("command-mail-delete-tooltip"))
							.onClickRunCmd("/" + this.getLabel() + " delete-sender " + outgoing.getUniqueId())

							.append(Lang.component("command-mail-archive-line",
									"subject", outgoing.getSubject(),
									"recipients", Lang.numberFormat("case-recipient", outgoing.getRecipients().size()),
									"date", TimeUtil.getFormattedDateMonth(outgoing.getDate())))

							.onHover(statusHover));
				}

				this.checkBoolean(!pages.isEmpty(), Lang.component("command-mail-archive-no-mail"));

				new ChatPaginator()
						.setFoundationHeader(Lang.legacy("command-mail-archive-header"))
						.setPages(pages)
						.send(this.audience);
			});

		} else
			this.returnInvalidArgs(param);
	}

	/*
	 * Parses the recipients and sends the book email
	 */
	private void sendMail(final WrappedSender wrapped, final String recipientsLine, final SimpleBook body) {

		String[] split = recipientsLine.split("\\|");

		// Check for special cases
		if (split.length == 1) {
			final String param = split[0].toLowerCase();

			// Send to offline receivers
			if ("all".equals(param)) {
				this.checkPerm(Permissions.Command.MAIL_SEND_ALL);

				this.pollCaches(caches -> {
					this.sendMail0(wrapped, body, caches);
				});
				return;
			}

			// Send to all online network players
			else if ("online".equals(param)) {
				this.checkPerm(Permissions.Command.MAIL_SEND_ONLINE);

				split = CommonCore.toArray(Common.getPlayerNames());
			}
		}

		final List<PlayerCache> tempRecipients = new ArrayList<>();

		for (int i = 0; i < split.length; i++) {
			final String recipientName = split[i];

			if (!recipientName.isEmpty()) {
				final boolean last = i + 1 == split.length;

				this.pollCache(recipientName, recipientCache -> {
					tempRecipients.add(recipientCache);

					if (last)
						this.sendMail0(wrapped, body, tempRecipients);
				});
			}
		}
	}

	private void sendMail0(final WrappedSender wrapped, final SimpleBook body, final List<PlayerCache> recipients) {
		final Set<String> uniqueNames = new HashSet<>();
		boolean warned = false;

		// Remove invalid recipients
		for (final Iterator<PlayerCache> it = recipients.iterator(); it.hasNext();) {
			final PlayerCache recipient = it.next();
			final String playerName = recipient.getPlayerName();
			final boolean isSelf = this.getPlayer().getUniqueId().equals(recipient.getUniqueId());

			// Strip duplicates
			if (uniqueNames.contains(playerName)) {
				it.remove();

				continue;
			}

			// Disallow when recipients ignores sender
			if (!this.hasPerm(Permissions.Bypass.REACH) && recipient.isIgnoringPlayer(this.getPlayer().getUniqueId())) {
				this.tellError(Lang.component("command-mail-send-fail-ignore", "player", playerName));
				warned = true;

				it.remove();
				continue;
			}

			// Disallow when recipient has turned mails off
			if (Settings.Toggle.APPLY_ON.contains(ToggleType.MAIL) && recipient.hasToggledPartOff(ToggleType.MAIL) && !isSelf) {
				this.tellError(Lang.component("command-mail-send-fail-toggle", "player", playerName));
				warned = true;

				it.remove();
				continue;
			}

			// Auto responder > send auto response back to sender
			if (recipient.isAutoResponderValid() && !isSelf)
				Platform.runTask(20, () -> Mail.sendAutoReply(recipient.getUniqueId(), this.getPlayer().getUniqueId(), recipient.getAutoResponder().getKey()));

			uniqueNames.add(playerName);
		}

		// Prepare mail
		final Mail mail = Mail.send(this.getPlayer(), recipients, body);

		// Broadcast to spying players
		Spy.broadcastMail(wrapped, recipients, mail);

		// Log
		Log.logMail(this.getPlayer(), mail);

		// Remove draft from sender because it was finished
		SenderCache.from(this.getSender()).setPendingMail(null);

		// Try removing the item if it still exists
		final Player player = this.getPlayer();
		final ItemStack[] content = player.getInventory().getContents();

		for (int itemIndex = 0; itemIndex < content.length; itemIndex++) {
			final ItemStack item = content[itemIndex];

			if (item != null && CompMetadata.hasMetadata(player, SimpleBook.TAG))
				content[itemIndex] = new ItemStack(CompMaterial.AIR.getMaterial());
		}

		player.getInventory().setContents(content);
		player.updateInventory();

		if (recipients.isEmpty()) {
			if (!warned)
				this.tellError(Lang.component("command-mail-send-fail"));

		} else
			this.tellSuccess(Lang.component("command-mail-send-success"));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (this.args.length == 1)
			return this.completeLastWord("new", "n", "send", "s", "autor", "inbox", "i", "read", "archive", "a", "sent");

		if (this.args.length == 2)
			if ("autor".equals(this.args[0]))
				return this.completeLastWord("view", "off", "3 hours", "7 days");

			else if ("send".equals(this.args[0])) {
				final List<String> tab = new ArrayList<>();

				if (this.hasPerm(Permissions.Command.MAIL_SEND_ONLINE))
					tab.add("online");

				if (this.hasPerm(Permissions.Command.MAIL_SEND_ALL))
					tab.add("all");

				tab.addAll(this.completeLastWordPlayerNames());

				return tab;
			}

		return NO_COMPLETE;
	}

	// ------------------------------------------------------------------------------------------------------------
	// Classes
	// ------------------------------------------------------------------------------------------------------------

	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	private static class PromptForwardRecipients extends SimplePrompt {

		/**
		 * The mail that is being forwarded
		 */
		private final UUID mailId;

		/**
		 * @see org.mineacademy.fo.conversation.SimplePrompt#getPrompt(org.bukkit.conversations.ConversationContext)
		 */
		@Override
		protected String getPrompt(final ConversationContext ctx) {
			return Lang.legacy("command-mail-forward-recipients");
		}

		/**
		 * @see org.mineacademy.fo.conversation.SimplePrompt#isInputValid(org.bukkit.conversations.ConversationContext, java.lang.String)
		 */
		@Override
		protected boolean isInputValid(final ConversationContext context, final String input) {
			if (input.isEmpty() || input.equals("|")) {
				this.tell(Lang.component("command-mail-forward-recipients-invalid", "input", input));

				return false;
			}

			return true;
		}

		/**
		 * @see org.bukkit.conversations.ValidatingPrompt#acceptValidatedInput(org.bukkit.conversations.ConversationContext, java.lang.String)
		 */
		@Override
		protected Prompt acceptValidatedInput(final ConversationContext context, final String input) {
			Platform.runTaskAsync(() -> {
				for (final String playerName : input.split("\\|"))
					if (!Bukkit.getOfflinePlayer(playerName).hasPlayedBefore()) {
						this.tell(Lang.component("player-not-stored", "player", playerName));

						return;
					}

				// Send later so that the player is not longer counted as "conversing"
				Platform.runTask(5, () -> {
					final FoundationPlayer audience = Platform.toPlayer(this.getPlayer(context));
					final Variables variables = Variables.builder(audience);

					audience.dispatchCommand(variables.replaceLegacy(Settings.Mail.COMMAND_ALIASES.get(0) + " forward " + this.mailId + " " + input));
					this.tell(context, variables.replaceLegacy(Lang.legacy("command-mail-forward-success")));
				});
			});

			return null;
		}
	}
}
