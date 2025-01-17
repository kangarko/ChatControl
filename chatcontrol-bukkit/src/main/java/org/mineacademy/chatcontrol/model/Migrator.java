package org.mineacademy.chatcontrol.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.mineacademy.chatcontrol.model.db.Database;
import org.mineacademy.chatcontrol.model.db.Log;
import org.mineacademy.chatcontrol.model.db.Mail;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.model.db.ServerSettings;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.DatabaseType;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.platform.FoundationPlugin;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.settings.YamlConfig;
import org.mineacademy.fo.visual.VisualizedRegion;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Migrates data from ChatControl 10 to 11.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Migrator {

	/**
	 * If the old folder was detected so we can proceed with the migration
	 */
	@Getter
	private static boolean oldFolderDetected = false;

	/**
	 * Stores v10 to v11 variable names
	 */
	private static final Map<String, String> REMAPPED_VARIABLES = Common.newHashMap(
			"label", "label_main",
			"sender", "sender_name",
			"receiver", "receiver_name",
			"warn_prefix", "prefix_warn",
			"announce_prefix", "prefix_announce",
			"info_prefix", "prefix_info",
			"error_prefix", "prefix_error",
			"success_prefix", "prefix_success",
			"question_prefix", "prefix_question",
			"pl_prefix", "player_prefix",
			"pl_suffix", "player_suffix",
			"pl_group", "player_group",
			"timestamp", "date",
			"nick", "player_nick",
			"chat_color", "player_chat_color",
			"chat_decoration", "player_chat_decoration",
			"top_role", "discord_top_role",
			"top_role_initial", "discord_top_role_initial",
			"top_role_alias", "discord_top_role_alias",
			"all_roles", "discord_all_roles");

	/**
	 * Migrate %syntax% placeholders to {syntax} placeholders.
	 */
	private static final Pattern PERCENT_VARIABLE_PATTERN = Pattern.compile("[%]([^%]+)[%]");

	/**
	 * Migrate settings from v10
	 *
	 * @param plugin
	 */
	public static void migrateV10Settings(final FoundationPlugin plugin) {
		final Path oldFolder = plugin.getDataFolder().getParentFile().toPath().resolve("ChatControlRed");

		if (Files.exists(oldFolder)) {
			final Path newFolder = plugin.getDataFolder().getParentFile().toPath().resolve("ChatControl");

			if (Files.exists(newFolder)) {
				CommonCore.warning("Both the new and the old folder exist. Skipping migration and using the new ChatControl/ folder. The old ChatControlRed/ folder can be removed.");

				return;
			}

			oldFolderDetected = true;

			CommonCore.log(" ",
					CommonCore.configLine(),
					"Migrating ChatControl 10 settings to ChatControl 11...",
					CommonCore.configLine());

			CommonCore.log("", "Migrating folder...");

			final Path backupFolder = plugin.getDataFolder().getParentFile().toPath().resolve("ChatControlRed_Backup_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()));

			try {
				// Copy to backup folder
				Files.walkFileTree(oldFolder, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attributes) throws IOException {
						final Path targetPath = backupFolder.resolve(oldFolder.relativize(dir));

						if (!Files.exists(targetPath))
							Files.createDirectory(targetPath);

						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFile(final Path file, final BasicFileAttributes attributes) throws IOException {
						Files.copy(file, backupFolder.resolve(oldFolder.relativize(file)), StandardCopyOption.REPLACE_EXISTING);

						return FileVisitResult.CONTINUE;
					}
				});

				// Rename old folder to new
				Files.move(oldFolder, newFolder, StandardCopyOption.REPLACE_EXISTING);

				CommonCore.log("Renamed ChatControlRed/ to ChatControl/ folder. Backup copy created in " + backupFolder);

			} catch (final IOException ex) {
				CommonCore.error(ex, "Failed to migrate ChatControlRed to ChatControl. Please rename the folder manually and keep a backup copy.");
			}

			CommonCore.log("", "Remapping variables...");
			remapVariables();

			final File settingsFile = FileUtil.getFile("settings.yml");

			if (settingsFile.exists()) {
				CommonCore.log("", "Migrating files...");

				final YamlConfig settings = YamlConfig.fromFile(settingsFile);
				boolean save = false;

				final File mysqlFile = FileUtil.getFile("mysql.yml");

				if (mysqlFile.exists()) {
					final YamlConfig mysqlConfig = YamlConfig.fromFile(mysqlFile);

					mysqlConfig.set("Type", mysqlConfig.getBoolean("Enabled") ? DatabaseType.REMOTE : DatabaseType.LOCAL);
					mysqlConfig.set("Enabled", null);
					mysqlConfig.set("Hikari", null);

					mysqlConfig.save();

					mysqlFile.renameTo(FileUtil.getFile("database.yml"));

					CommonCore.log("Migrated mysql.yml to database.yml.");
				}

				final File bungeeFile = FileUtil.getFile("bungee.yml");

				if (bungeeFile.exists()) {
					bungeeFile.renameTo(FileUtil.getFile("proxy.yml"));

					CommonCore.log("Migrated bungee.yml to proxy.yml.");
				}

				// Migrate Integration section from v10
				if (settings.isSet("Integration")) {
					for (final Map.Entry<String, Object> entry : settings.getMap("Integration").entrySet())
						settings.set(entry.getKey(), entry.getValue());

					settings.set("Integration", null);
					save = true;
				}

				if (settings.isSet("Channels.List")) {
					for (final Map.Entry<String, Object> entry : settings.getMap("Channels.List")) {
						final String name = entry.getKey();
						final SerializedMap channelData = SerializedMap.fromObject(entry.getValue());

						if (channelData.containsKey("Bungee")) {
							final boolean bungee = (boolean) channelData.remove("Bungee");

							channelData.put("Proxy", bungee);
						}

						// same for Bungee_Spy
						if (channelData.containsKey("Bungee_Spy")) {
							final boolean bungee = (boolean) channelData.remove("Bungee_Spy");

							channelData.put("Proxy_Spy", bungee);
						}

						settings.set("Channels.List." + name, channelData);
					}
				}

				if (save)
					settings.save();
			}

			CommonCore.log(" ", "Updating variables/ files...");

			for (final File variableFile : FileUtil.getFiles("variables", "yml")) {
				final YamlConfig variableConfig = YamlConfig.fromFile(variableFile);
				final String receiverCondition = variableConfig.getString("Receiver_Condition");
				final String receiverPermission = variableConfig.getString("Receiver_Permission");

				if (receiverCondition != null && !receiverCondition.isEmpty())
					CommonCore.log("The 'Receiver_Condition' key in " + variableFile + " is no longer supported, please remove it manually: " + receiverCondition);

				if (receiverPermission != null && !receiverPermission.isEmpty())
					CommonCore.log("The 'Receiver_Permission' key in " + variableFile + " is no longer supported, please remove it manually: " + receiverPermission);
			}

			CommonCore.log(" ", "Updating settings.yml...");

			// Print later because Settings class will migrate after this code
			Platform.runTask(() -> {
				final boolean enabled = Platform.getPlugin().isPluginEnabled();

				CommonCore.log(
						"",
						"You have now migrated to ChatControl 11!",
						"Migration should be 99% automatic but there might be edge cases where you need to manually adjust.",
						"Please read the above log and adjust as needed.",
						(enabled ? null : ""),
						(enabled ? null : "WARNING: THE PLUGIN IS DISABLED. Review the logs above, finish updating manually and do a clean restart."));
			});
		}
	}

	/*
	 * Remap variables in all known folders
	 */
	public static void remapVariables() {
		for (final File file : FileUtil.getFiles("books", "yml"))
			remapVariablesIn(file);

		for (final File file : FileUtil.getFiles("formats", "yml"))
			remapVariablesIn(file);

		for (final File file : FileUtil.getFiles("messages", "rs"))
			remapVariablesIn(file);

		for (final File file : FileUtil.getFiles("rules", "rs"))
			remapVariablesIn(file);

		for (final File file : FileUtil.getFiles("variables", "yml"))
			remapVariablesIn(file);

		final File settingsYml = FileUtil.getFile("settings.yml");

		if (settingsYml.exists())
			remapVariablesIn(settingsYml);

		Common.log("Variable migration complete.");
	}

	/*
	 * Remap variables in a single file
	 */
	private static void remapVariablesIn(final File file) {
		final List<String> lines = FileUtil.readLinesFromFile(file);
		final List<String> newLines = new ArrayList<>();
		final String fileName = file.getName();

		int lineNumber = 1;

		int replacedXColorCodes = 0;
		int replacedPercentVariables = 0;
		int replacedAmpersandHexColorCodes = 0;
		int replacedHexColorCodes = 0;

		for (String line : lines) {
			if (line.trim().startsWith("#")) {
				lineNumber++;
				newLines.add(line);

				continue; // Ignore commented out lines
			}

			// Replace §x MD5 color codes
			try {
				final Matcher matcher = Variables.HEX_MD5_PATTERN.matcher(line);
				final StringBuffer buffer = new StringBuffer();

				while (matcher.find()) {
					final String legacyFormat = matcher.group();
					final String hexColor = legacyFormat.replaceAll("[&§]", "").substring(1);

					matcher.appendReplacement(buffer, "<#" + hexColor + ">");
					replacedXColorCodes++;
				}

				matcher.appendTail(buffer);
				line = buffer.toString();
			} catch (final Throwable t) {
				Common.log("Error remapping §x variable syntax to mini in file " + file.getName() + " line: " + line);
			}

			// Replace %variable% placeholders to {variable}
			try {
				final Matcher matcher = PERCENT_VARIABLE_PATTERN.matcher(line);
				final StringBuffer result = new StringBuffer();

				while (matcher.find()) {
					final String variable = matcher.group(1); // Extract content inside '% %'

					matcher.appendReplacement(result, "{" + variable + "}");
					replacedPercentVariables++;
				}

				matcher.appendTail(result);
				line = result.toString();
			} catch (final Throwable t) {
				Common.log("Error remapping %syntax% to {syntax} variables in file " + file.getName() + " line: " + line);
			}

			// Replace &hex color codes
			try {
				final Matcher matcher = Variables.HEX_AMPERSAND_PATTERN.matcher(line);
				final StringBuffer result = new StringBuffer();

				while (matcher.find()) {
					final String hexColor = matcher.group().substring(1); // Remove '&' prefix

					matcher.appendReplacement(result, "<" + hexColor + ">");
					replacedAmpersandHexColorCodes++;
				}

				matcher.appendTail(result);
				line = result.toString();

			} catch (final Throwable t) {
				Common.log("Error remapping &#123456 to <#123456> color in file " + file.getName() + " line: " + line);
			}

			// Replace hex color codes
			try {
				final Matcher matcher = Variables.HEX_LITERAL_PATTERN.matcher(line);
				final StringBuffer result = new StringBuffer();

				while (matcher.find()) {
					final String hexColor = matcher.group();

					matcher.appendReplacement(result, "<" + hexColor + ">");
					replacedHexColorCodes++;
				}

				matcher.appendTail(result);
				line = result.toString();
			} catch (final Throwable t) {
				Common.log("Error remapping #123456 to <#123456> color in file " + file.getName() + " line: " + line);
			}

			for (final Map.Entry<String, String> entry : REMAPPED_VARIABLES.entrySet()) {
				final String[] patterns = new String[] {
						"{" + entry.getKey() + "}",
						"{+" + entry.getKey() + "}",
						"{" + entry.getKey() + "+}",
						"{+" + entry.getKey() + "+}"
				};

				for (final String pattern : patterns) {
					if (line.contains(pattern)) {
						final String replacement = pattern.replace(entry.getKey(), entry.getValue());

						CommonCore.log("[" + fileName + "] Remapped variable " + pattern + " to " + replacement + " on line " + lineNumber);
						line = line.replace(pattern, replacement);
					}
				}
			}

			if (fileName.contains("spy-mail")) {
				if (line.contains("{receivers}")) {
					CommonCore.log("[" + fileName + "] Remapped variable {receivers} to {mail_receivers} on line " + lineNumber);

					line = line.replace("{receivers}", "{mail_receivers}");

				} else if (line.contains("{player}")) {
					CommonCore.log("[" + fileName + "] Remapped variable {player} to {mail_sender} on line " + lineNumber);

					line = line.replace("{player}", "{mail_sender}");
				}
			}

			lineNumber++;
			newLines.add(line);
		}

		if (replacedXColorCodes > 0)
			Bukkit.getLogger().info("[" + file.getName() + "] Replaced " + replacedXColorCodes + " &x color code to mini tag in file, please check. We recommend using the valid minimessage syntax now.");

		if (replacedPercentVariables > 0)
			Bukkit.getLogger().info("[" + file.getName() + "] Replaced " + replacedPercentVariables + " %variable% placeholders to {variable} syntax.");

		if (replacedAmpersandHexColorCodes > 0)
			Bukkit.getLogger().info("[" + file.getName() + "] Wrapped " + replacedAmpersandHexColorCodes + " &# hex color codes to mini tags with '<>'.");

		if (replacedHexColorCodes > 0)
			Bukkit.getLogger().info("[" + file.getName() + "] Wrapped " + replacedHexColorCodes + " hex color codes to mini tags with '<>'. This is only PARTIALLY supported. If you used &m or other decoration this will overflow"
					+ " and needs to be closed with a minitag such as <bold></bold> - due to complexity this was not auto fixed.");

		FileUtil.write(file, newLines, StandardOpenOption.TRUNCATE_EXISTING);
	}

	/**
	 * Convert &# hex color codes to mini.
	 *
	 *
	 * @param line
	 * @return
	 */
	public static String convertAmpersandToMiniHex(String line) {
		final Matcher matcher = Variables.HEX_AMPERSAND_PATTERN.matcher(line);
		final StringBuffer result = new StringBuffer();

		while (matcher.find()) {
			final String hexColor = matcher.group().substring(1); // Remove '&' prefix

			matcher.appendReplacement(result, "<" + hexColor + ">");
		}

		matcher.appendTail(result);
		return result.toString();
	}

	/**
	 * Converts # hex color codes to mini.
	 *
	 * @param line
	 * @return
	 */
	public static String convertHexToMiniHex(String line) {
		final Matcher matcher = Variables.HEX_LITERAL_PATTERN.matcher(line);
		final StringBuffer result = new StringBuffer();

		while (matcher.find()) {
			final String hexColor = matcher.group();

			matcher.appendReplacement(result, "<" + hexColor + ">");
		}

		matcher.appendTail(result);
		return result.toString();
	}

	/**
	 * Migrate data.db yml data to a database
	 */
	public static void migrateDataDbToSQL() {

		// Only migrate if ChatControlRed existed
		if (oldFolderDetected) {
			final Database database = Database.getInstance();
			ValidCore.checkBoolean(database.isConnected(), "Cannot migrate data if database is not connected!");

			final ServerSettings serverSettings = ServerSettings.getInstance();
			final File dataFile = FileUtil.getFile("data.db");

			// Do not migrate if MySQL was used since we already stored the data there
			if (dataFile.exists() && !Settings.Database.isRemote()) {
				final YamlConfig data = YamlConfig.fromFile(dataFile);

				CommonCore.log(" ", "Migrating data.db entries to SQLite...");

				// Migrate Players
				if (data.isSet("Players")) {
					final SerializedMap allPlayerData = data.getMap("Players");

					CommonCore.log("Migrating " + allPlayerData.size() + " player entries");

					for (final Map.Entry<String, Object> entry : allPlayerData.entrySet()) {
						final UUID uniqueId = UUID.fromString(entry.getKey());
						final SerializedMap dataMap = SerializedMap.fromObject(entry.getValue());
						final String name = dataMap.getString("Name");

						final PlayerCache cache = PlayerCache.fromDataMap(uniqueId, name, dataMap);

						database.insertToQueue(cache);
					}
				}

				boolean saveSettings = false;

				// Migrate Regions
				if (data.isSet("Regions")) {
					final List<VisualizedRegion> allRegionData = data.getList("Regions", VisualizedRegion.class);

					CommonCore.log("Migrating " + allRegionData.size() + " regions");

					for (final VisualizedRegion region : allRegionData) {
						Common.log("Migrating region: " + region.getName());

						final File regionFile = FileUtil.createIfNotExists("regions/" + region.getName() + ".yml");
						final YamlConfig regionConfig = YamlConfig.fromFile(regionFile);

						for (final Map.Entry<String, Object> entry : region.serialize().entrySet())
							regionConfig.set(entry.getKey(), entry.getValue());

						regionConfig.save();
					}

					saveSettings = true;
				}

				// Migrate server mute
				if (data.isSet("Unmute_Time")) {
					final long timestamp = data.getLong("Unmute_Time");
					CommonCore.log("Migrating server unmute time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp)));

					serverSettings.setUnmuteTimeNoSave(timestamp);
					saveSettings = true;
				}

				if (saveSettings)
					database.insertToQueue(serverSettings);

				// Migrate Mails
				if (data.isSet("Mails")) {
					final List<Mail> mails = data.getList("Mails", Mail.class);

					CommonCore.log("Migrating " + mails.size() + " mails");

					for (final Mail mail : mails)
						database.insertToQueue(mail);
				}
			}

			if (dataFile.exists()) {
				dataFile.delete();

				CommonCore.log("Deleted data.db file after migration since it is now unused.");
			}

			final File logCsv = FileUtil.getFile("log.csv");

			if (logCsv.exists() && !Settings.Database.isRemote()) {
				final DateFormat fileDateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
				final List<String> lines = FileUtil.readLinesFromFile(logCsv);

				CommonCore.log("", "Migrating " + (lines.size() - 1) + " log entries");

				// Remove header
				lines.remove(0);

				for (final String line : lines) {
					final String[] split = line.split(", ");

					// Malformed structure
					if (split.length != 8) {
						CommonCore.log("Ignoring malformed log entry: " + line);

						continue;
					}

					// Remove quotations
					for (int i = 0; i < split.length; i++) {
						String part = split[i].trim();

						part = part.charAt(0) == '\'' ? part.substring(1) : part;
						part = part.charAt(part.length() - 1) == '\'' ? part.substring(0, part.length() - 1) : part;

						split[i] = part;
					}

					try {
						final long date = fileDateFormat.parse(split[0]).getTime();
						final LogType type = LogType.fromKey(split[1]);
						final String sender = split[2];
						final List<String> receivers = CommonCore.getOrDefault(split[3].equals("NULL") ? null : CommonCore.convertJsonToList(split[3]), new ArrayList<>());
						final String content = split[4].replace(",/ ", ", ");
						final String channelName = split[5].equals("NULL") || "".equals(split[5].trim()) ? null : split[5];
						final String ruleName = split[6].equals("NULL") || "".equals(split[6].trim()) ? null : split[6];
						final String ruleGroupName = split[7].equals("NULL") || "".equals(split[7].trim()) ? null : split[7];

						final Log log = new Log(date, type, sender, content, receivers, channelName, ruleName, ruleGroupName);

						database.insertToQueue(log);

					} catch (final Throwable throwable) {
						CommonCore.log("Failed to migrate log entry: " + line + ", got error: " + throwable.getMessage());
					}
				}
			}

			if (logCsv.exists()) {
				logCsv.delete();

				CommonCore.log("Deleted log.csv file after migration since it is now unused.");
			}
		}
	}
}
