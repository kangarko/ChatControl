package org.mineacademy.chatcontrol.model.db;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.model.ChatControlProxyMessage;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Proxy;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.ProxyUtil;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.database.Row;
import org.mineacademy.fo.database.SimpleResultSet;
import org.mineacademy.fo.database.Table;
import org.mineacademy.fo.model.ConfigSerializable;
import org.mineacademy.fo.model.SimpleBook;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.platform.FoundationPlayer;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.Lang;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents an email that can be send to players
 */
public class Mail extends Row implements ConfigSerializable {

	/**
	 * The date when the email was sent
	 */
	@Getter
	private long date;

	/**
	 * The unique ID of this mail
	 */
	@Getter
	private UUID uniqueId;

	/**
	 * The sender name
	 */
	@Getter
	private UUID sender;

	/**
	 * Did the sender mark this email as deleted?
	 */
	@Getter
	private boolean senderDeleted;

	/**
	 * The recipients for the email then flag if they read it and the time when they did
	 */
	private List<Recipient> recipients;

	/**
	 * The book holding the mail copy
	 */
	@Getter
	private SimpleBook body;

	/**
	 * Is the email an autoreply?
	 */
	@Getter
	private boolean autoReply;

	private Mail() {
	}

	Mail(final SimpleResultSet resultSet) throws SQLException {
		//super(resultSet); // Does not store ID

		this.uniqueId = resultSet.getUniqueIdStrict("UUID");
		this.sender = resultSet.getUniqueIdStrict("Sender");
		this.senderDeleted = resultSet.getBoolean("Sender_Deleted");
		this.recipients = resultSet.getList("Recipients", Recipient.class);
		this.body = resultSet.get("Body", SimpleBook.class);
		this.date = resultSet.getLong("Send_Date");
		this.autoReply = resultSet.getBoolean("Auto_Reply");
	}

	@Override
	public Table getTable() {
		return ChatControlTable.MAIL;
	}

	/**
	 * @see org.mineacademy.fo.model.ConfigSerializable#serialize()
	 */
	@Override
	public SerializedMap toMap() {
		return SerializedMap.fromArray(
				"UUID", this.uniqueId,
				"Sender", this.sender,
				"Sender_Deleted", this.senderDeleted,
				"Recipients", this.recipients,
				"Body", this.body.serialize().toJson(),
				"Send_Date", this.date,
				"Auto_Reply", this.autoReply);
	}

	@Override
	public SerializedMap serialize() {
		return this.toMap();
	}

	@Override
	public Tuple<String, Object> getUniqueColumn() {
		return new Tuple<>("UUID", this.uniqueId);
	}

	/**
	 * Turn a map into an email
	 *
	 * @param map
	 * @return
	 */
	public static Mail deserialize(final SerializedMap map) {
		final Mail mail = new Mail();

		mail.uniqueId = map.get("UUID", UUID.class);
		mail.sender = map.get("Sender", UUID.class);
		mail.senderDeleted = map.getBoolean("Sender_Deleted", false);
		mail.recipients = map.getList("Recipients", Recipient.class);
		mail.body = map.get("Body", SimpleBook.class);
		mail.date = map.getLong("Send_Date", 0L);
		mail.autoReply = map.getBoolean("Auto_Reply", false);

		return mail;
	}

	/**
	 * Reads this email for player (open book)
	 *
	 * @param audience
	 */
	public void displayTo(final FoundationPlayer audience) {
		this.body.openPlain(audience);
	}

	/**
	 * Marks the given email as opened by the given recipient
	 *
	 * @param recipientPlayer
	 */
	public void markOpen(final Player recipientPlayer) {
		final Recipient recipient = this.findRecipient(recipientPlayer.getUniqueId());
		ValidCore.checkNotNull(recipient, recipientPlayer.getName() + " has never read mail: " + this.getSubject() + " from " + this.getSenderName());

		recipient.markOpened();
		this.upsert();
	}

	/**
	 * Set the email sender to be deleted
	 *
	 * @param senderDeleted
	 */
	public void setSenderDeleted(final boolean senderDeleted) {
		this.senderDeleted = senderDeleted;

		this.upsert();
	}

	/*
	 * Register this email as sent and notify online recipients
	 */
	private void sendAndNotify() {
		final SimpleComponent warnMessage = Lang.component("command-mail-new-notification", "sender", this.getSenderName());

		// Notify recipients
		for (final Recipient recipient : this.recipients) {
			final Player onlineRecipient = Remain.getPlayerByUUID(recipient.getUniqueId());

			// Only notify those that actually can read their email
			if (onlineRecipient != null && onlineRecipient.hasPermission(Permissions.Command.MAIL))
				Messenger.announce(onlineRecipient, warnMessage);

			else if (Proxy.ENABLED)
				ProxyUtil.sendPluginMessage(ChatControlProxyMessage.MESSAGE, recipient.getUniqueId(), Messenger.getAnnouncePrefix().appendPlain(" ").append(warnMessage));
		}

		this.upsert();
	}

	/**
	 * Return true if this mail can be deleted
	 *
	 * @return
	 */
	public boolean canDelete() {

		// Remove if sender and all recipients have removed it
		if (this.isSenderDeleted()) {
			boolean allRecipientsDeleted = true;

			for (final Recipient recipient : this.getRecipients())
				if (!recipient.isMarkedDeleted()) {
					allRecipientsDeleted = false;

					break;
				}

			if (allRecipientsDeleted)
				return true;
		}

		// Remove if too old
		if (this.getDate() < System.currentTimeMillis() - Settings.CLEAR_DATA_IF_INACTIVE.getTimeSeconds() * 1000)
			return true;

		return false;
	}

	/**
	 * Return the subject for this mail or null if not yet set
	 *
	 * @return
	 */
	public String getSubject() {
		return this.body.getTitle();
	}

	/**
	 * Get sender name
	 *
	 * @return
	 */
	public String getSenderName() {
		return this.body.getAuthor();
	}

	/**
	 * Return the senders this email is send to
	 *
	 * @return the recipients
	 */
	public List<Recipient> getRecipients() {
		return Collections.unmodifiableList(this.recipients);
	}

	/**
	 * Return if the given recipient read the mail
	 *
	 * @param recipientId
	 * @return
	 */
	public boolean isReadBy(final UUID recipientId) {
		final Recipient recipient = this.findRecipient(recipientId);

		return recipient != null && recipient.isOpened();
	}

	/**
	 * Find mails recipient by uid
	 *
	 * @param recipientId
	 * @return
	 */
	public Recipient findRecipient(final UUID recipientId) {
		for (final Recipient recipient : this.recipients)
			if (recipient.getUniqueId().equals(recipientId))
				return recipient;

		return null;
	}

	/**
	 * Set that the given recipient of this email has deleted it.
	 *
	 * @param recipient
	 */
	public void setDeletedBy(final Recipient recipient) {
		recipient.setMarkedDeleted(true);

		this.upsert();
	}

	/**
	 * Set that the given recipient of this email has opened it.
	 *
	 * @param recipient
	 */
	public void setOpenedBy(final Recipient recipient) {
		recipient.markOpened();

		this.upsert();
	}

	/**
	 * Return this mail as a JSON string
	 *
	 * @return
	 */
	public String toJson() {
		return this.toMap().toJson();
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Mail " + this.toMap().toStringFormatted();
	}

	// ------------------------------------------------------------------------------------------------------------
	// Static
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Create, store mail in caches, send it n notify recipient if ze is online
	 *
	 * @param sender
	 * @param recipient
	 * @param body
	 * @return
	 */
	public static Mail sendAutoReply(final UUID sender, final UUID recipient, final SimpleBook body) {
		return send(sender, CommonCore.newList(Recipient.create(recipient)), body, true);
	}

	/**
	 * Create, store mail in caches, send it n notify online recipients
	 *
	 * @param sender
	 * @param recipients
	 * @param body
	 *
	 * @return
	 */
	public static Mail send(final UUID sender, final Set<UUID> recipients, final SimpleBook body) {
		return send(sender, CommonCore.convertList(recipients, Recipient::create), body, false);
	}

	public static Mail send(final Player from, final List<PlayerCache> recipients, final SimpleBook body) {
		return send(from.getUniqueId(), CommonCore.convertList(recipients, Recipient::create), body, false);
	}

	/*
	 * Create, store mail in caches, send it n notify online recipients
	 */
	private static Mail send(final UUID sender, final List<Recipient> recipients, final SimpleBook body, final boolean autoReply) {
		final Mail mail = new Mail();

		mail.uniqueId = UUID.randomUUID();
		mail.sender = sender;
		mail.recipients = recipients;
		mail.body = body;
		mail.date = System.currentTimeMillis();
		mail.autoReply = autoReply;

		mail.sendAndNotify();

		return mail;
	}

	// ------------------------------------------------------------------------------------------------------------
	// Classes
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * A simple email recipient
	 */
	@Getter
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	public static class Recipient implements ConfigSerializable {

		/**
		 * The recipient unique ID
		 */
		private final UUID uniqueId;

		/**
		 * Did the recipient open this email?
		 */
		private boolean opened;

		/**
		 * When the recipient opened the email
		 */
		private long openTime;

		/**
		 * Did the recipient mark this email as deleted?
		 */
		@Setter(value = AccessLevel.PRIVATE)
		private boolean markedDeleted;

		/**
		 * Marks the recipient as having read this email
		 */
		private void markOpened() {
			this.opened = true;
			this.openTime = System.currentTimeMillis();
		}

		/**
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(final Object obj) {
			return obj instanceof Recipient && ((Recipient) obj).getUniqueId().equals(this.uniqueId);
		}

		@Override
		public String toString() {
			return this.serialize().toJson();
		}

		/**
		 * @see org.mineacademy.fo.model.ConfigSerializable#serialize()
		 */
		@Override
		public SerializedMap serialize() {
			final SerializedMap map = new SerializedMap();

			map.put("UUID", this.uniqueId);
			map.putIfTrue("Opened", this.opened);
			map.put("Open_Time", this.openTime);
			map.putIfTrue("Deleted", this.markedDeleted);

			return map;
		}

		/**
		 * Turn a config section into a recipient
		 *
		 * @param map
		 * @return
		 */
		public static Recipient deserialize(final SerializedMap map) {
			final UUID uniqueId = map.get("UUID", UUID.class);
			final boolean opened = map.getBoolean("Opened", false);
			final long openTime = map.getLong("Open_Time");
			final boolean deleted = map.getBoolean("Deleted", false);

			return new Recipient(uniqueId, opened, openTime, deleted);
		}

		/*
		 * Create a new recipient that has not yet opened the mail
		 */
		private static Recipient create(final UUID uniqueId) {
			return new Recipient(uniqueId, false, -1, false);
		}

		/*
		 * Create a new recipient that has not yet opened the mail
		 */
		private static Recipient create(final PlayerCache cache) {
			return new Recipient(cache.getUniqueId(), false, -1, false);
		}
	}
}
