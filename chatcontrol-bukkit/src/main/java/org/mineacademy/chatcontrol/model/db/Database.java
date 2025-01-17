package org.mineacademy.chatcontrol.model.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.model.LogType;
import org.mineacademy.chatcontrol.model.Migrator;
import org.mineacademy.chatcontrol.model.db.Mail.Recipient;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.MathUtil;
import org.mineacademy.fo.SerializeUtilCore;
import org.mineacademy.fo.SerializeUtilCore.Language;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.database.SimpleDatabase;
import org.mineacademy.fo.database.SimpleResultSet;
import org.mineacademy.fo.database.Table;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.CompChatColor;
import org.mineacademy.fo.model.SimpleBook;
import org.mineacademy.fo.model.Task;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.remain.Remain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The centralized database to download/upload entries
 */
@RequiredArgsConstructor
public final class Database extends SimpleDatabase {

	/**
	 * The database instance.
	 */
	@Getter
	private static final Database instance = new Database();

	/**
	 * The map of UUID to player name
	 */
	private final Map<UUID, String> uniqueIdToName = new HashMap<>();

	/**
	 * The map of player name to UUID
	 */
	private final Map<String, UUID> nameToUniqueId = new HashMap<>();

	/**
	 * Called manually after the plugin is started to prevent NPE because settings#log#clean_after is loaded after the database is connected
	 * so we cannot call this in onConnected()
	 */
	public void prepareTables() {
		if (!this.isConnected())
			return;

		// Upgrade table structure
		this.migrateMailTable(ChatControlTable.MAIL);

		// Load caches
		this.selectColumns(ChatControlTable.PLAYERS, Arrays.asList("UUID", "Name"), resultSet -> {
			final String uuid = resultSet.getString("UUID");
			final String name = resultSet.getString("Name");

			this.uniqueIdToName.put(UUID.fromString(uuid), name);
			this.nameToUniqueId.put(name, UUID.fromString(uuid));
		});

		// Load server settings
		try {
			final ServerSettings stored = this.getRowWhere(ChatControlTable.SETTINGS, Where.builder().equals("Server", Platform.getCustomServerName()));

			ServerSettings.setInstance(stored != null ? stored : new ServerSettings());

		} catch (final Throwable ex) {
			CommonCore.error(ex, "Error loading server settings from MySQL.");
		}

		// Purge old entries
		if (Settings.Log.CLEAN_AFTER.isEnabled())
			this.deleteOlderThan(ChatControlTable.LOGS, new Timestamp(System.currentTimeMillis() - Settings.Log.CLEAN_AFTER.getTimeSeconds() * 1000));

		if (Settings.Mail.CLEAN_AFTER.isEnabled())
			this.updateUnsafe("DELETE FROM " + ChatControlTable.MAIL.getName() + " WHERE Send_Date < " + (System.currentTimeMillis() - Settings.Mail.CLEAN_AFTER.getTimeMilliseconds()));

		if (Settings.CLEAR_DATA_IF_INACTIVE.isEnabled())
			this.updateUnsafe("DELETE FROM " + ChatControlTable.PLAYERS.getName() + " WHERE LastModified < " + (System.currentTimeMillis() - Settings.CLEAR_DATA_IF_INACTIVE.getTimeMilliseconds()));

		// Migrate from data.db
		Migrator.migrateDataDbToSQL();

		if (Settings.Proxy.ENABLED)
			try {
				ServerSettings.setProxy(getProxySettings());

			} catch (final Throwable ex) {
				CommonCore.error(ex, "Error loading server proxy settings from MySQL.");
			}
	}

	@Override
	public Table[] getTables() {
		return ChatControlTable.values();
	}

	/* -------------------------------------------------------------------------------*/
	/* Name to UUID cache */
	/* -------------------------------------------------------------------------------*/

	/**
	 * Converts the given name into unique id
	 *
	 * @param playerName
	 * @param callback
	 */
	public void getUniqueId(final String playerName, final Consumer<UUID> callback) {
		final UUID localUniqueId = this.nameToUniqueId.get(playerName);

		if (localUniqueId != null) {
			callback.accept(localUniqueId);

			return;
		}

		Platform.runTaskAsync(() -> {
			final OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
			final UUID uniqueId = offline.getUniqueId();

			this.uniqueIdToName.put(uniqueId, playerName);
			this.nameToUniqueId.put(playerName, uniqueId);

			this.upsert(new PlayerCache(playerName, uniqueId));

			Platform.runTask(() -> callback.accept(uniqueId));
		});
	}

	/**
	 * Converts the given unique id into name
	 *
	 * @param uniqueId
	 * @param callback
	 */
	public void getPlayerName(final UUID uniqueId, final Consumer<String> callback) {
		final String localName = this.uniqueIdToName.get(uniqueId);

		if (localName != null) {
			callback.accept(localName);

			return;
		}

		Platform.runTaskAsync(() -> {
			final OfflinePlayer offline = Remain.getOfflinePlayerByUniqueId(uniqueId);
			final String name = offline.getName();

			this.uniqueIdToName.put(uniqueId, name);
			this.nameToUniqueId.put(name, uniqueId);

			this.upsert(new PlayerCache(name, uniqueId));

			Platform.runTask(() -> callback.accept(name));
		});
	}

	/**
	 * Converts the list of player names into unique ids.
	 *
	 * @param playerNames
	 * @param callback
	 */
	public void getUniqueIds(final Collection<String> playerNames, final Consumer<List<UUID>> callback) {
		final List<UUID> uniqueIds = new ArrayList<>();
		final List<String> notFoundNames = new ArrayList<>();

		for (final String playerName : playerNames) {
			final UUID cachedUuid = this.nameToUniqueId.get(playerName);

			if (cachedUuid != null)
				uniqueIds.add(cachedUuid);
			else
				notFoundNames.add(playerName);
		}

		if (notFoundNames.isEmpty()) {
			callback.accept(uniqueIds);

			return;
		}

		Platform.runTaskAsync(() -> {
			for (final String playerName : notFoundNames) {
				final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
				final UUID uniqueId = offlinePlayer.getUniqueId();

				this.uniqueIdToName.put(uniqueId, playerName);
				this.nameToUniqueId.put(playerName, uniqueId);

				uniqueIds.add(uniqueId);

				this.upsert(new PlayerCache(playerName, uniqueId));
			}

			Platform.runTask(() -> callback.accept(uniqueIds));
		});
	}

	/**
	 * Converts the list of unique ids into player names.
	 *
	 * @param uniqueIds
	 * @return
	 */
	public List<String> getPlayerNamesSync(final Collection<UUID> uniqueIds) {
		final List<String> names = new ArrayList<>();
		final List<UUID> notFoundUniqueIds = new ArrayList<>();

		for (final UUID uniqueId : uniqueIds) {
			final String cachedName = this.uniqueIdToName.get(uniqueId);

			if (cachedName != null)
				names.add(cachedName);
			else
				notFoundUniqueIds.add(uniqueId);
		}

		if (notFoundUniqueIds.isEmpty())
			return names;

		for (final UUID uniqueId : notFoundUniqueIds) {
			final OfflinePlayer offlinePlayer = Remain.getOfflinePlayerByUniqueId(uniqueId);
			final String playerName = offlinePlayer.getName();

			this.uniqueIdToName.put(uniqueId, playerName);
			this.nameToUniqueId.put(playerName, uniqueId);

			names.add(playerName);

			this.upsert(new PlayerCache(playerName, uniqueId));
		}

		return names;
	}

	/* ------------------------------------------------------------------------------- */
	/* Log */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Remove old log entries as per settings
	 * @return
	 */
	public List<Log> getLogs() {
		return this.getRows(ChatControlTable.LOGS);
	}

	/**
	 * Remove old log entries as per settings of the given type
	 *
	 * @param type
	 * @return
	 */
	public List<Log> getLogs(LogType type) {
		return this.getRowsWhere(ChatControlTable.LOGS, Where.builder().equals("Type", type.getKey()));
	}

	/* ------------------------------------------------------------------------------- */
	/* Mail */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Migrate the mail table to the new format
	 */
	private void migrateMailTable(final Table table) {
		try {
			final boolean columnExists = this.doesColumnExist(table, "Sender");

			if (!columnExists) {
				CommonCore.log("", "Migrating remote mail table to the new format...");

				if (this.isSQLite()) {
					this.batchUpdateUnsafe(Arrays.asList(
							"ALTER TABLE " + table.getName() + " ADD COLUMN `Sender` varchar(64);",
							"ALTER TABLE " + table.getName() + " ADD COLUMN `Sender_Deleted` tinyint(1) DEFAULT 0;",
							"ALTER TABLE " + table.getName() + " ADD COLUMN `Recipients` longtext;",
							"ALTER TABLE " + table.getName() + " ADD COLUMN `Body` longtext;",
							"ALTER TABLE " + table.getName() + " ADD COLUMN `Send_Date` bigint(20) DEFAULT 0;",
							"ALTER TABLE " + table.getName() + " ADD COLUMN `Auto_Reply` tinyint(1) DEFAULT 0;"));

				} else {
					final String alterSQL = "ALTER TABLE " + table.getName() + " "
							+ "ADD COLUMN `Sender` varchar(64), "
							+ "ADD COLUMN `Sender_Deleted` tinyint(1) DEFAULT 0, "
							+ "ADD COLUMN `Recipients` longtext, "
							+ "ADD COLUMN `Body` longtext, "
							+ "ADD COLUMN `Send_Date` bigint(20) DEFAULT 0, "
							+ "ADD COLUMN `Auto_Reply` tinyint(1) DEFAULT 0;";

					try (PreparedStatement statement = this.prepareStatement(alterSQL)) {
						statement.executeUpdate();
					}
				}

				final String selectSQL = "SELECT UUID, Data FROM " + table.getName() + " WHERE Sender IS NULL"; // Only process old rows
				final String updateSQL = "UPDATE " + table.getName() + " SET Sender = ?, Sender_Deleted = ?, Recipients = ?, Body = ?, Send_Date = ?, Auto_Reply = ? WHERE UUID = ?";

				try (PreparedStatement selectStatement = this.prepareStatement(selectSQL);
						PreparedStatement updateStatement = this.prepareStatement(updateSQL);
						ResultSet resultSet = selectStatement.executeQuery()) {

					while (resultSet.next()) {
						final String uuid = resultSet.getString("UUID");
						final String rawData = resultSet.getString("Data");

						final SerializedMap map = SerializedMap.fromObject(Language.JSON, rawData);
						final UUID sender = map.getUniqueId("Sender");
						final boolean senderDeleted = map.getBoolean("Sender_Deleted", false);
						final List<Recipient> recipients = map.getList("Recipients", Recipient.class);
						final SimpleBook body = map.get("Body", SimpleBook.class);
						final long sendDate = map.getLong("Send_Date", 0L);
						final boolean autoReply = map.getBoolean("Auto_Reply", false);

						updateStatement.setString(1, sender != null ? sender.toString() : null);
						updateStatement.setBoolean(2, senderDeleted);
						updateStatement.setString(3, SerializeUtilCore.serialize(Language.JSON, recipients).toString());
						updateStatement.setString(4, body.serialize().toJson());
						updateStatement.setLong(5, sendDate);
						updateStatement.setBoolean(6, autoReply);
						updateStatement.setString(7, uuid);

						updateStatement.executeUpdate();
					}
				}

				try (PreparedStatement dropStatement = this.prepareStatement("ALTER TABLE " + table.getName() + " DROP COLUMN Data")) {
					dropStatement.executeUpdate();
				}

				CommonCore.log("Migrated mail table to the new format.");
			}

		} catch (final SQLException ex) {
			CommonCore.error(ex, "Error updating " + table.getName() + " table.");
		}
	}

	/**
	 * Return all mails that the recipient got
	 *
	 * @param recipient
	 * @return
	 */
	public List<Mail> findMailsTo(final UUID recipient) {
		final List<Mail> found = this.getRowsWhere(ChatControlTable.MAIL, Where.builder().like("Recipients", recipient.toString()));
		Collections.sort(found, Comparator.comparing(Mail::getDate).reversed());

		return found;
	}

	/**
	 * Return all mails the sender has sent
	 *
	 * @param sender
	 * @return
	 */
	public List<Mail> findMailsFrom(final UUID sender) {
		final List<Mail> filtered = this.getRowsWhere(ChatControlTable.MAIL, Where.builder().equals("Sender", sender.toString()));
		Collections.sort(filtered, Comparator.comparing(Mail::getDate).reversed());

		return filtered;
	}

	/**
	 * Look up mail by its unique ID or return null if not found
	 *
	 * @param uniqueId
	 *
	 * @return
	 */
	@Nullable
	public Mail findMail(final UUID uniqueId) {
		return this.getRowWhere(ChatControlTable.MAIL, Where.builder().equals("UUID", uniqueId.toString()));
	}

	/* ------------------------------------------------------------------------------- */
	/* Player cache */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Loads the given player cache with data from MySQL
	 *
	 * @param player
	 * @param senderCache
	 * @param syncCallback
	 */
	public void loadAndStoreCache(final Player player, final SenderCache senderCache, final Consumer<PlayerCache> syncCallback) {
		final String playerName = player.getName();
		final UUID uniqueId = player.getUniqueId();

		ValidCore.checkBoolean(senderCache.getCacheLoadingTask() == null, "Cache loading task already scheduled! Only call loadAndStoreCache() once when player joins!");

		this.nameToUniqueId.put(playerName, uniqueId);
		this.uniqueIdToName.put(uniqueId, playerName);

		final Task task = Platform.runTaskAsync(() -> {
			try {
				final long now = System.currentTimeMillis();
				final Where where = Settings.UUID_LOOKUP ? Where.builder().equals("UUID", uniqueId.toString()) : Where.builder().equals("Name", playerName);

				PlayerCache cache = this.getPlayerCacheWhereStrict(player, where);

				// Not stored previously
				if (cache == null)
					cache = new PlayerCache(playerName, uniqueId);

				if (System.currentTimeMillis() - now > 100 && Settings.Proxy.ENABLED)
					CommonCore.warning("Your database connection is too slow (" + MathUtil.formatTwoDigits(((System.currentTimeMillis() - now) / 1000.0)) + " seconds). "
							+ "This will cause issues on your proxy on player server switch. We recommend having the database server on the same machine as the proxy.");

				final PlayerCache finalCache = cache;

				Platform.runTask(() -> {
					if (player.isOnline()) {
						finalCache.putToCacheMap();
						senderCache.setDatabaseLoaded(true);
						syncCallback.accept(finalCache);
					}
				});

			} catch (final Throwable t) {
				CommonCore.error(t, "Unable to load database data for player " + playerName);
			}
		});

		senderCache.setCacheLoadingTask(task);
	}

	/*
	 * Get the player cache where the given player matches the given where clause
	 */
	private PlayerCache getPlayerCacheWhereStrict(final Player player, final Where where) {
		final List<PlayerCache> entries = new ArrayList<>();

		this.select(ChatControlTable.PLAYERS, where, resultSet -> {
			final PlayerCache row = new PlayerCache(player.getName(), player.getUniqueId(), resultSet);

			if (row != null)
				entries.add(row);
		});

		if (entries.size() > 1)
			new FoException("Found multiple entries for player " + player.getName() + " in the database! Please clean your table or report this if it's a bug: " + entries, false).printStackTrace();

		return entries.isEmpty() ? null : entries.get(0);
	}

	@Nullable
	public PlayerCache getCache(final String nameOrNick) {
		final Table table = ChatControlTable.PLAYERS;

		try (PreparedStatement statement = this.prepareStatement("SELECT * FROM " + table.getName() + " WHERE Name = ? OR Nick LIKE ?")) {
			statement.setString(1, nameOrNick);
			statement.setString(2, CompChatColor.stripColorCodes(nameOrNick));

			try (ResultSet resultSet = statement.executeQuery()) {
				if (resultSet.next())
					return new PlayerCache(null, null, SimpleResultSet.wrap(table, resultSet));
			}

		} catch (final Throwable t) {
			CommonCore.error(t, "Error getting user record from MySQL with name or nickname '" + nameOrNick + "'.");
		}

		return null;
	}

	/**
	 * Return true if the given nick is used by someone else already in the database (case insensitive search).
	 *
	 * @param nick
	 * @return
	 */
	public boolean isNickUsed(final String nick) {
		try (PreparedStatement statement = this.prepareStatement("SELECT * FROM " + ChatControlTable.PLAYERS.getName() + " WHERE Nick = ?")) {
			statement.setString(1, nick);

			try (ResultSet resultSet = statement.executeQuery()) {
				if (resultSet.next())
					return true;
			}

		} catch (final Throwable t) {
			CommonCore.error(t, "Error getting user record from MySQL with nickname '" + nick + "'.");
		}

		return false;
	}

	/* ------------------------------------------------------------------------------- */
	/* Server settings */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Get the server settings for the entire proxy
	 *
	 * @return
	 */
	public ServerSettings getProxySettings() {
		final ServerSettings settings = this.getServerSettings("proxy");

		return settings != null ? settings : new ServerSettings("proxy");
	}

	/**
	 * Get the server settings for the given server name
	 *
	 * @param serverName
	 * @return
	 */
	public ServerSettings getServerSettings(String serverName) {
		return this.getRowWhere(ChatControlTable.SETTINGS, Where.builder().equals("Server", "proxy"));
	}

}
