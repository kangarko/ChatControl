package org.mineacademy.chatcontrol.model.db;

import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.LogType;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.RuleType;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.operator.Group;
import org.mineacademy.chatcontrol.operator.Rule;
import org.mineacademy.chatcontrol.operator.RuleOperator;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.util.LogUtil;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.database.RowDate;
import org.mineacademy.fo.database.SimpleResultSet;
import org.mineacademy.fo.database.Table;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.CompChatColor;
import org.mineacademy.fo.model.SimpleBook;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.platform.Platform;

import lombok.Getter;
import lombok.NonNull;

/**
 * Represents an elegant way of storing server communication
 */
@Getter
public final class Log extends RowDate {

	/**
	 * The server where this log was created
	 */
	private final String server;

	/**
	 * The type of this log
	 */
	private final LogType type;

	/**
	 * The issuer of this log
	 */
	private final String sender;

	/**
	 * The issuer's input
	 */
	private final String content;

	/**
	 * Optional: The receivers of this message
	 */
	private List<String> receivers = new ArrayList<>();

	/**
	 * Optional, channel name, if associated with
	 */
	@Nullable
	private String channelName;

	/**
	 * Optional, rule name, if associated with
	 */
	@Nullable
	private String ruleName;

	/**
	 * Optional, rule group name, if associated with
	 */
	@Nullable
	private String ruleGroupName;

	/**
	 * Create a new log
	 *
	 * @deprecated only used for migrating from v10
	 * @param date
	 * @param type
	 * @param sender
	 * @param content
	 * @param receivers
	 * @param channelName
	 * @param ruleName
	 * @param ruleGroupName
	 */
	@Deprecated
	public Log(final long date, final LogType type, final String sender, final String content, final List<String> receivers, final String channelName, final String ruleName, final String ruleGroupName) {
		super(date);

		this.server = Platform.getCustomServerName();
		this.type = type;
		this.sender = sender;
		this.content = content;
		this.receivers = receivers;
		this.channelName = channelName;
		this.ruleName = ruleName;
		this.ruleGroupName = ruleGroupName;
	}

	private Log(final LogType type, final String sender, final String content) {
		this.server = Platform.getCustomServerName();
		this.type = type;
		this.sender = sender;
		this.content = content;
	}

	Log(final SimpleResultSet resultSet) throws SQLException {
		super(resultSet);

		try {
			this.server = resultSet.getStringStrict("Server");
			this.type = resultSet.getEnumStrict("Type", LogType.class);
			this.sender = resultSet.getString("Sender");
			this.content = resultSet.getString("Content");
			this.receivers = CommonCore.convertJsonToList(CommonCore.getOrEmpty(resultSet.getString("Receiver")).replace("\\\"", "\""));
			this.channelName = resultSet.getString("ChannelName");
			this.ruleName = resultSet.getString("RuleName");
			this.ruleGroupName = resultSet.getString("RuleGroupName");

		} catch (final SQLSyntaxErrorException ex) {
			throw new FoException(ex, "Log database has incorrect schema or values.", false);
		}
	}

	@Override
	public Table getTable() {
		return ChatControlTable.LOGS;
	}

	/**
	 * Set the receiver
	 *
	 * @param receiver the receiver to set
	 * @return
	 */
	public Log receiver(final String receiver) {
		this.receivers.add(receiver);

		return this;
	}

	/**
	 * Set the receivers
	 *
	 * @param receivers the receivers to set
	 * @return
	 */
	public Log receivers(final Set<String> receivers) {
		this.receivers.addAll(receivers);

		return this;
	}

	/**
	 * Attach a channel to this log
	 *
	 * @param channel
	 * @return
	 */
	public Log channel(final Channel channel) {
		this.channelName = channel.getName();

		return this;
	}

	/**
	 * Attach a rule to this log
	 *
	 * @param rule
	 * @return
	 */
	public Log rule(final Rule rule) {
		this.ruleName = rule.getName();

		return this;
	}

	/**
	 * Attach a group to this log
	 *
	 * @param group
	 * @return
	 */
	public Log ruleGroup(final Group group) {
		this.ruleGroupName = group.getGroup();

		return this;
	}

	@Nullable
	public String getChannelName() {
		return this.channelName == null || this.channelName.isEmpty() ? null : this.channelName;
	}

	@Nullable
	public String getRuleName() {
		return this.ruleName == null || this.ruleName.isEmpty() ? null : this.ruleName;
	}

	@Nullable
	public String getRuleGroupName() {
		return this.ruleGroupName == null || this.ruleGroupName.isEmpty() ? null : this.ruleGroupName;
	}

	/* ------------------------------------------------------------------------------- */
	/* Writing */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Write this log to database if enabled, if not then to file.
	 * @param sender
	 *
	 * @return true if log was saved successfully
	 */
	public boolean write(@Nullable final CommandSender sender) {
		if (!Settings.Log.APPLY_ON.contains(this.type))
			return false;

		if (this.type == LogType.COMMAND && !Settings.Log.COMMAND_LIST.isInList(this.content.split(" ")[0]))
			return false;

		if (sender != null && sender.hasPermission(Permissions.Bypass.LOG)) {
			LogUtil.logOnce("log-bypass", "Note: Not logging " + sender.getName() + "'s " + this.type + " because he has '" + Permissions.Bypass.LOG + "' permission." +
					" Players with these permission do not get their content logged. To disable that, negate this permission (a false value if using LuckPerms).");

			return false;
		}

		this.insertToQueue();

		return true;
	}

	@Override
	public SerializedMap toMap() {
		return super.toMap().putArray(
				"Server", this.server,
				"Type", this.type.toString(),
				"Sender", this.sender,
				"Receiver", this.receivers.isEmpty() ? null : CommonCore.convertListToJson(this.receivers),
				"Content", this.content,
				"ChannelName", this.channelName,
				"RuleName", this.ruleName,
				"RuleGroupName", this.ruleGroupName);
	}

	// ------------------------------------------------------------------------------------------------------------
	// Static
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Create a new log from a chat message
	 *
	 * @param sender
	 * @param message
	 */
	public static void logChat(@NonNull final CommandSender sender, @NonNull final String message) {
		final Log log = new Log(LogType.CHAT, sender.getName(), SimpleComponent.fromMiniSection(message).toPlain());

		log.write(sender);
	}

	/**
	 * Create a new log from a chat message
	 *
	 * @param sender
	 * @param channel
	 * @param chatMessage
	 */
	public static void logChannel(@NonNull final CommandSender sender, @NonNull final Channel channel, @NonNull final String chatMessage) {
		final Log log = new Log(LogType.CHAT, sender.getName(), chatMessage);

		log.channel(channel);
		log.write(sender);
	}

	/**
	 * Create a new log from a command
	 *
	 * @param sender
	 * @param command
	 */
	public static void logCommand(@NonNull final CommandSender sender, @NonNull final String command) {
		final Log log = new Log(LogType.COMMAND, sender.getName(), CompChatColor.stripColorCodes(command));

		log.write(sender);
	}

	/**
	 * Create a new log from a private message
	 *
	 * @param sender
	 * @param receiver
	 * @param message
	 */
	public static void logPrivateMessage(@NonNull final CommandSender sender, @NonNull final String receiver, @NonNull final String message) {
		final Log log = new Log(LogType.PRIVATE_MESSAGE, sender.getName(), CompChatColor.stripColorCodes(message));

		log.receiver(receiver);
		log.write(sender);
	}

	/**
	 * Create a new log from a sign
	 *
	 * @param player
	 * @param lines
	 */
	public static void logSign(@NonNull final Player player, @NonNull final String[] lines) {
		final Log log = new Log(LogType.SIGN, player.getName(), String.join("%FOLINES%", lines));

		log.write(player);
	}

	/**
	 * Create a new log from a book
	 *
	 * @param player
	 * @param book
	 */
	public static void logBook(@NonNull final Player player, @NonNull final SimpleBook book) {
		final Log log = new Log(LogType.BOOK, player.getName(), book.serialize().toJson());

		log.write(player);
	}

	/**
	 * Create a new log from a book
	 *
	 * @param player
	 * @param item
	 */
	public static void logAnvil(@NonNull final Player player, @NonNull final ItemStack item) {
		final ItemMeta meta = item.getItemMeta();

		if (meta != null) {
			// Saving the whole stack would be overwhelming, only save name/lore
			final String type = ChatUtil.capitalizeFully(item.getType());
			final String name = meta.hasDisplayName() ? meta.getDisplayName() : type;
			final List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();

			final Log log = new Log(LogType.ANVIL, player.getName(), SerializedMap.fromArray("type", type, "name", name, "lore", lore).toJson());

			log.write(player);
		}
	}

	/**
	 * Create a new log from a mail
	 *
	 * @param player
	 * @param mail
	 */
	public static void logMail(@NonNull final Player player, @NonNull final Mail mail) {
		final Log log = new Log(LogType.MAIL, player.getName(), mail.toJson());

		log.write(player);
	}

	/**
	 * Create a new log from a rule
	 *
	 * @param type
	 * @param wrapped
	 * @param operator
	 * @param message
	 */
	public static void logRule(@NonNull final RuleType type, @NonNull final WrappedSender wrapped, @NonNull final RuleOperator operator, @NonNull final String message) {

		// Logging not supported
		if (type.getLogType() == null)
			return;

		final Log log = new Log(type.getLogType(), wrapped.getName(), message);

		if (wrapped.isPlayer() && wrapped.getPlayerCache().getWriteChannel() != null)
			log.channel(wrapped.getPlayerCache().getWriteChannel());

		if (operator instanceof Rule)
			log.rule((Rule) operator);

		else if (operator instanceof Group)
			log.ruleGroup((Group) operator);

		else
			throw new FoException("Logging of operator class " + operator.getClass() + " not implemented!");

		log.write(wrapped.getSender());
	}
}
