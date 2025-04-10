package org.mineacademy.chatcontrol.settings;

import java.io.File;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.Plugin;
import org.mineacademy.chatcontrol.model.LogType;
import org.mineacademy.chatcontrol.model.PlayerGroup;
import org.mineacademy.chatcontrol.model.PlayerMessageType;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.model.RuleType;
import org.mineacademy.chatcontrol.model.Spy.DiscordSpy;
import org.mineacademy.chatcontrol.model.WarningPoints.WarnTrigger;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.ReflectionUtil;
import org.mineacademy.fo.SerializeUtil;
import org.mineacademy.fo.SerializeUtilCore;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.DatabaseType;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.IsInList;
import org.mineacademy.fo.model.SimpleSound;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.model.Whiteblacklist;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.SimpleSettings;
import org.mineacademy.fo.settings.YamlConfig;

/**
 * The main settings.yml configuration class.
 */
@SuppressWarnings("unused")
public final class Settings extends SimpleSettings {

	/**
	 * What sections can the user modify? Such as channels,
	 * so that those are not updated by comments
	 *
	 * @return the ignored sections
	 */
	@Override
	protected List<String> getUncommentedSections() {
		return Collections.unmodifiableList(Arrays.asList(
				"Channels.List",
				"Groups",
				"Warning_Points.Reset_Task.Remove",
				"Warning_Points.Sets",
				"Messages.Prefix",
				"Messages.Discord",
				"Spy.Discord"));
	}

	/**
	 * Proxy connector.
	 */
	public static class Proxy {

		public static Boolean ENABLED;
		public static String PREFIX;

		private static void init() {
			// Handled in the main init() to ensure first priority

			if (ENABLED && Platform.getCustomServerName().equals("server")) {
				CommonCore.logFramed(
						"&c&lERROR: Proxy requires the",
						"Server_Name key in proxy.yml to be set!");

				ENABLED = false;
			}
		}
	}

	/**
	 * Settings for MySQL
	 *
	 * For security reasons, no sensitive information is stored here.
	 */
	public static class Database {

		public static DatabaseType TYPE;

		private static void init() {
			final File databaseYml = FileUtil.extract("database.yml");
			final YamlConfig databaseConfig = YamlConfig.fromFile(databaseYml);

			databaseConfig.setDefaults(YamlConfig.fromInternalPath("database.yml"));

			setPathPrefix("Database");

			boolean save = false;

			if (isSet("Type")) {
				databaseConfig.set("Type", get("Type", DatabaseType.class));

				save = true;
			}

			if (isSet("Host"))
				databaseConfig.set("Host", getString("Host"));

			if (isSet("Database"))
				databaseConfig.set("Database", getString("Database"));

			if (isSet("User"))
				databaseConfig.set("User", getString("User"));

			if (isSet("Password"))
				databaseConfig.set("Password", getString("Password"));

			if (isSet("Line"))
				databaseConfig.set("Line", getString("Line"));

			if (save)
				Common.log("Migrated 'Database' section from settings.yml to database.yml. Please check.");

			TYPE = databaseConfig.get("Type", DatabaseType.class);
			final String HOST = databaseConfig.getString("Host");
			final String DATABASE = databaseConfig.getString("Database");
			final String USER = databaseConfig.getString("User");
			final String PASSWORD = databaseConfig.getString("Password");
			final String LINE = databaseConfig.getString("Line");

			databaseConfig.save();

			boolean remoteFailed = false;

			if (TYPE == DatabaseType.REMOTE) {
				if (Platform.getCustomServerName().equals("server"))
					CommonCore.logFramed(true,
							"&fERROR: &cRemote database requires the",
							"Server_Name key in proxy.yml to be set!");
				else {
					CommonCore.log("", "Connecting to remote " + TYPE.getDriver() + " database...");
					final String address = LINE.replace("{driver}", TYPE.getDriver()).replace("{host}", HOST).replace("{database}", DATABASE);

					try {
						org.mineacademy.chatcontrol.model.db.Database.getInstance().connect(address, USER, PASSWORD);

					} catch (final Throwable t) {
						if (t instanceof SQLException && t.getMessage() != null && t.getMessage().contains("invalid database address")) {
							Common.warning("Invalid database address: " + address + ", falling back to local.");

							t.printStackTrace();

						} else
							CommonCore.error(t, "Error connecting to remote database, falling back to local.");

						remoteFailed = true;
					}
				}
			}

			if (TYPE == DatabaseType.LOCAL || remoteFailed)
				org.mineacademy.chatcontrol.model.db.Database.getInstance().connect("jdbc:sqlite:" + FileUtil.getFile("sqlite.db").getPath());
		}

		public static boolean isRemote() {
			return TYPE.isRemote();
		}
	}

	/**
	 * Channels settings
	 */
	public static class Channels {

		public static Boolean ENABLED;
		public static List<String> COMMAND_ALIASES;
		public static PlayerGroup<Integer> MAX_READ_CHANNELS;
		public static Boolean JOIN_READ_OLD;
		public static Boolean IGNORE_AUTOJOIN_IF_LEFT;
		public static SimpleTime ANTISPAM_BROADCAST_THRESHOLD;
		public static SimpleTime RANGED_CHANNEL_NO_NEAR_PLAYER_DELAY;
		public static String FORMAT_CONSOLE;
		public static String FORMAT_DISCORD;
		public static IsInList<String> IGNORE_WORLDS;

		private static void init() {
			setPathPrefix("Channels");

			ENABLED = getBoolean("Enabled");
			COMMAND_ALIASES = getCommandList("Command_Aliases");
			MAX_READ_CHANNELS = new PlayerGroup<>(PlayerGroup.Type.MAX_READ_CHANNELS, getInteger("Max_Read_Channels"));
			JOIN_READ_OLD = getBoolean("Join_Read_Old");
			IGNORE_AUTOJOIN_IF_LEFT = getBoolean("Ignore_Autojoin_If_Left");
			ANTISPAM_BROADCAST_THRESHOLD = getTime("Antispam_Broadcast_Threshold");
			RANGED_CHANNEL_NO_NEAR_PLAYER_DELAY = getTime("Ranged_Channel_No_Near_Player_Delay");
			FORMAT_CONSOLE = getString("Format_Console");
			FORMAT_DISCORD = getString("Format_Discord");
			IGNORE_WORLDS = getIsInList("Ignore_Worlds", String.class);
		}
	}

	/**
	 * Our core anti spam checker
	 */
	public static class AntiSpam {

		public static class Chat {

			public static PlayerGroup<SimpleTime> DELAY;
			public static PlayerGroup<Double> SIMILARITY;
			public static Integer SIMILARITY_PAST_MESSAGES;
			public static Integer SIMILARITY_START_AT;
			public static SimpleTime SIMILARITY_FORGIVE_TIME;
			public static Whiteblacklist WHITELIST_DELAY;
			public static Whiteblacklist WHITELIST_SIMILARITY;

			public static SimpleTime LIMIT_PERIOD;
			public static Integer LIMIT_MAX;

			public static Boolean PARROT;
			public static SimpleTime PARROT_DELAY;
			public static Double PARROT_SIMILARITY;
			public static Whiteblacklist PARROT_WHITELIST;

			private static void init() {
				setPathPrefix("Anti_Spam.Chat");

				DELAY = new PlayerGroup<>(PlayerGroup.Type.MESSAGE_DELAY, getTime("Delay"));
				SIMILARITY = new PlayerGroup<>(PlayerGroup.Type.MESSAGE_SIMILARITY, getPercentage("Similarity"));
				SIMILARITY_PAST_MESSAGES = getInteger("Similarity_Past_Messages");
				SIMILARITY_START_AT = getInteger("Block_After_Times");
				SIMILARITY_FORGIVE_TIME = getTime("Similarity_Time");

				WHITELIST_DELAY = new Whiteblacklist(getStringList("Whitelist_Delay"), true);
				WHITELIST_SIMILARITY = new Whiteblacklist(getStringList("Whitelist_Similarity"), true);

				setPathPrefix("Anti_Spam.Chat.Limit");

				LIMIT_PERIOD = getTime("Period");
				LIMIT_MAX = getInteger("Max_Messages");

				setPathPrefix("Anti_Spam.Chat.Parrot");

				PARROT = getBoolean("Enabled");
				PARROT_DELAY = getTime("Delay");
				PARROT_SIMILARITY = getPercentage("Similarity");
				PARROT_WHITELIST = new Whiteblacklist(getStringList("Whitelist"), true);

				ValidCore.checkBoolean(SIMILARITY_PAST_MESSAGES > 0, "To disable Anti_Spam.Chat.Similarity_Past_Messages, simply set Anti_Spam.Chat.Similarity to 0%% instead!");
			}
		}

		public static class Commands {

			public static PlayerGroup<SimpleTime> DELAY;
			public static PlayerGroup<Double> SIMILARITY;
			public static Integer SIMILARITY_PAST_COMMANDS;
			public static Integer SIMILARITY_MIN_ARGS;
			public static Integer SIMILARITY_START_AT;
			public static SimpleTime SIMILARITY_FORGIVE_TIME;
			public static Whiteblacklist WHITELIST_DELAY;
			public static Whiteblacklist WHITELIST_SIMILARITY;

			public static SimpleTime LIMIT_PERIOD;
			public static Integer LIMIT_MAX;

			private static void init() {
				setPathPrefix("Anti_Spam.Commands");

				DELAY = new PlayerGroup<>(PlayerGroup.Type.COMMAND_DELAY, getTime("Delay"));
				SIMILARITY = new PlayerGroup<>(PlayerGroup.Type.COMMAND_SIMILARITY, getPercentage("Similarity"));
				SIMILARITY_PAST_COMMANDS = getInteger("Similarity_Past_Commands");
				SIMILARITY_MIN_ARGS = getInteger("Similarity_Min_Args");
				SIMILARITY_START_AT = getInteger("Block_After_Times");
				SIMILARITY_FORGIVE_TIME = getTime("Similarity_Time");
				WHITELIST_DELAY = new Whiteblacklist(getStringList("Whitelist_Delay"), false);
				WHITELIST_SIMILARITY = new Whiteblacklist(getStringList("Whitelist_Similarity"), false);
				LIMIT_PERIOD = getTime("Limit.Period");
				LIMIT_MAX = getInteger("Limit.Max_Commands");

				ValidCore.checkBoolean(SIMILARITY_PAST_COMMANDS > 0, "To disable Anti_Spam.Commands.Similarity_Past_Messages, simply set Anti_Spam.Commands.Similarity to 0%% instead!");
			}
		}
	}

	/**
	 * Anti caps check
	 */
	public static class AntiCaps {

		public static Boolean ENABLED;
		public static Whiteblacklist ENABLED_IN_COMMANDS;
		public static Integer MIN_MESSAGE_LENGTH;
		public static Integer MIN_CAPS_PERCENTAGE;
		public static Integer MIN_CAPS_IN_A_ROW;
		public static Whiteblacklist WHITELIST;

		private static void init() {
			setPathPrefix("Anti_Caps");

			ENABLED = getBoolean("Enabled");
			ENABLED_IN_COMMANDS = new Whiteblacklist(getStringList("Enabled_In_Commands"), false);
			MIN_MESSAGE_LENGTH = getInteger("Min_Message_Length");
			MIN_CAPS_PERCENTAGE = getInteger("Min_Caps_Percentage");
			MIN_CAPS_IN_A_ROW = getInteger("Min_Caps_In_A_Row");
			WHITELIST = new Whiteblacklist(getStringList("Whitelist"), false);
		}
	}

	/**
	 * Anti-bot settings
	 */
	public static class AntiBot {

		public static Boolean BLOCK_CHAT_UNTIL_MOVED;
		public static Whiteblacklist BLOCK_CMDS_UNTIL_MOVED;
		public static Boolean BLOCK_SAME_TEXT_SIGNS;

		public static Whiteblacklist DISALLOWED_USERNAMES_LIST;
		public static List<String> DISALLOWED_USERNAMES_COMMANDS;

		public static SimpleTime COOLDOWN_CHAT_AFTER_JOIN;
		public static SimpleTime COOLDOWN_COMMAND_AFTER_JOIN;

		public static Boolean JOIN_FLOOD_ENABLED;
		public static SimpleTime JOIN_FLOOD_THRESHOLD;
		public static Integer JOIN_FLOOD_MIN_PLAYERS;
		public static List<String> JOIN_FLOOD_COMMANDS;

		private static void init() {
			setPathPrefix("Anti_Bot");

			BLOCK_CHAT_UNTIL_MOVED = getBoolean("Block_Chat_Until_Moved");
			BLOCK_CMDS_UNTIL_MOVED = new Whiteblacklist(getStringList("Block_Commands_Until_Moved"), false);
			BLOCK_SAME_TEXT_SIGNS = getBoolean("Block_Same_Text_Signs");

			setPathPrefix("Anti_Bot.Disallowed_Usernames");

			DISALLOWED_USERNAMES_LIST = new Whiteblacklist(getStringList("List"), true);
			DISALLOWED_USERNAMES_COMMANDS = getStringList("Commands");

			setPathPrefix("Anti_Bot.Cooldown");

			COOLDOWN_CHAT_AFTER_JOIN = getTime("Chat_After_Login");
			COOLDOWN_COMMAND_AFTER_JOIN = getTime("Command_After_Login");

			setPathPrefix("Anti_Bot.Join_Flood");

			JOIN_FLOOD_ENABLED = getBoolean("Enabled");
			JOIN_FLOOD_THRESHOLD = getTime("Join_Threshold");
			JOIN_FLOOD_MIN_PLAYERS = getInteger("Min_Players");
			JOIN_FLOOD_COMMANDS = getStringList("Commands");
		}
	}

	/**
	 * Settings for the tab-completion system
	 */
	public static class TabComplete {

		public static Boolean ENABLED;
		public static Boolean USE_NICKNAMES;
		public static Integer PREVENT_IF_BELOW_LENGTH;
		public static Whiteblacklist WHITELIST;

		private static void init() {
			setPathPrefix("Tab_Complete");

			ENABLED = getBoolean("Enabled");
			USE_NICKNAMES = getBoolean("Use_Nicknames");
			PREVENT_IF_BELOW_LENGTH = getInteger("Prevent_If_Below_Length");
			WHITELIST = new Whiteblacklist(getStringList("Whitelist"), true);
		}

		public static boolean canCheckTabComplete(final String word) {
			return PREVENT_IF_BELOW_LENGTH == 0 || word.length() < PREVENT_IF_BELOW_LENGTH;
		}
	}

	/**
	 * Settings for Englush spellug and gramer to avoid mistakes and typpos
	 */
	public static class Grammar {

		public static Integer INSERT_DOT_MSG_LENGTH;
		public static Integer CAPITALIZE_MSG_LENGTH;

		private static void init() {
			setPathPrefix("Grammar");

			INSERT_DOT_MSG_LENGTH = getInteger("Insert_Dot_Message_Length");
			CAPITALIZE_MSG_LENGTH = getInteger("Capitalize_Message_Length");
		}
	}

	/**
	 * Settings for the rules system
	 */
	public static class Rules {

		public static Set<RuleType> APPLY_ON;
		public static Boolean VERBOSE;
		public static Boolean STRIP_ACCENTS;
		public static Boolean STRIP_COLORS;
		public static Integer SIGNS_CHECK_MODE;

		private static void init() {
			setPathPrefix("Rules");

			final List<String> applyOn = getStringList("Apply_On");

			if (applyOn.contains("packet") || applyOn.contains("PACKET")) {
				CommonCore.warning("Packet rules are no longer supported in ChatControl because they lead to crashes and never worked properly with ViaVersion and many other packet plugins. We'll now remove them from your Rules.Apply_On section.");

				applyOn.remove("packet");
				applyOn.remove("PACKET");

				set("Apply_On", applyOn);
			}

			APPLY_ON = getSet("Apply_On", RuleType.class);
			ValidCore.checkBoolean(!APPLY_ON.contains(RuleType.GLOBAL), "To enable global rules, remove @import global from files in the rules/ folder.");

			VERBOSE = getBoolean("Verbose");

			STRIP_ACCENTS = getBoolean("Strip_Accents");
			STRIP_COLORS = getBoolean("Strip_Colors");

			final String checkMode = getString("Signs_Check_Mode");

			if ("joined".equals(checkMode))
				SIGNS_CHECK_MODE = 1;

			else if ("separate".equals(checkMode))
				SIGNS_CHECK_MODE = 2;

			else if ("both".equals(checkMode))
				SIGNS_CHECK_MODE = 3;

			else {
				SIGNS_CHECK_MODE = 0;
				APPLY_ON.remove(RuleType.SIGN);

				CommonCore.warning("Invalid Signs_Check_Mode in settings.yml. Please use 'joined', 'separate' or 'both'.");
			}
		}
	}

	/**
	 * Settings for private messages
	 */
	public static class PrivateMessages {

		public static Boolean ENABLED;
		public static Boolean PROXY;
		public static Boolean TOASTS;
		public static SimpleSound SOUND;
		public static Boolean AUTOMODE;
		public static Boolean DISABLED_BY_DEFAULT;
		public static SimpleTime AUTOMODE_LEAVE_THRESHOLD;
		public static List<String> TELL_ALIASES;
		public static List<String> REPLY_ALIASES;
		public static Boolean SENDER_OVERRIDES_RECEIVER_REPLY;
		public static String FORMAT_SENDER;
		public static String FORMAT_RECEIVER;
		public static String FORMAT_TOAST;
		public static String FORMAT_CONSOLE;

		private static void init() {
			setPathPrefix("Private_Messages");

			if (isSet("Bungee"))
				move("Bungee", "Private_Messages.Proxy");

			ENABLED = getBoolean("Enabled");
			PROXY = getBoolean("Proxy");
			TOASTS = getBoolean("Toasts");
			SOUND = get("Sound", SimpleSound.class);
			AUTOMODE = getBoolean("Auto_Mode");
			DISABLED_BY_DEFAULT = getBoolean("Disabled_By_Default");
			AUTOMODE_LEAVE_THRESHOLD = getTime("Auto_Mode_Leave_Threshold");
			TELL_ALIASES = getCommandList("Tell_Aliases");
			REPLY_ALIASES = getCommandList("Reply_Aliases");
			SENDER_OVERRIDES_RECEIVER_REPLY = getBoolean("Sender_Overrides_Receivers_Reply");
			FORMAT_SENDER = getString("Format_Sender");
			FORMAT_RECEIVER = getString("Format_Receiver");
			FORMAT_TOAST = getString("Format_Toast");
			FORMAT_CONSOLE = getString("Format_Console");

			if (TOASTS)
				if (MinecraftVersion.olderThan(V.v1_12))
					TOASTS = false;

				else
					CommonCore.warning("Toast notifications write to disk on the main thread which poses a performance penaulty.");

			if (AUTOMODE && Remain.isFolia()) {
				CommonCore.warning("Auto mode is not supported on Folia.");

				AUTOMODE = false;
			}
		}
	}

	/**
	 * Settings for timed message broadcaster
	 */
	public static class Messages {

		public static Set<PlayerMessageType> APPLY_ON;
		public static Set<PlayerMessageType> STOP_ON_FIRST_MATCH;
		public static Map<PlayerMessageType, Long> DISCORD;
		public static Map<PlayerMessageType, String> PREFIX;
		public static SimpleTime DEFER_JOIN_MESSAGE_BY;
		public static SimpleTime TIMED_DELAY;

		private static void init() {
			setPathPrefix("Messages");

			APPLY_ON = new HashSet<>(getSet("Apply_On", PlayerMessageType.class));
			STOP_ON_FIRST_MATCH = new HashSet<>(getSet("Stop_On_First_Match", PlayerMessageType.class));
			DISCORD = getMap("Discord", PlayerMessageType.class, Long.class);
			PREFIX = getMap("Prefix", PlayerMessageType.class, String.class);
			DEFER_JOIN_MESSAGE_BY = getTime("Defer_Join_Message_By");
			TIMED_DELAY = getTime("Timed_Delay");

			if (TIMED_DELAY.getTimeSeconds() > 3)
				ValidCore.checkBoolean(TIMED_DELAY.getTimeSeconds() >= 3, "Timed_Messages.Delay must be equal or greater than 3 seconds!"
						+ " (If you want to disable timed messages, remove them from Apply_On)");

			if ((APPLY_ON.contains(PlayerMessageType.JOIN) || APPLY_ON.contains(PlayerMessageType.QUIT)) && HookManager.isEssentialsLoaded()) {
				final Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");

				if (essentials.getConfig().getBoolean("allow-silent-join-quit", false))
					CommonCore.warning("Your Essentials key 'allow-silent-join-quit' may cause duplicated join/quit messages, we recommend you set that key to false in config.yml of Essentials.");
			}
		}
	}

	/**
	 * Settings for colors
	 */
	public static class Colors {

		public static Set<org.mineacademy.chatcontrol.model.Colors.Type> APPLY_ON;

		private static void init() {
			setPathPrefix("Colors");

			APPLY_ON = new HashSet<>(getSet("Apply_On", org.mineacademy.chatcontrol.model.Colors.Type.class));
		}
	}

	/**
	 * Settings for the sound notify feature where we tag players in the chat and they receive a "pop"
	 */
	public static class SoundNotify {

		public static Boolean ENABLED;
		public static SimpleTime COOLDOWN;
		public static Boolean REQUIRE_AFK;
		public static String REQUIRE_PREFIX;
		public static SimpleSound SOUND;
		public static PlayerGroup<String> FORMAT;

		private static void init() {
			setPathPrefix("Sound_Notify");

			ENABLED = getBoolean("Enabled");
			COOLDOWN = getTime("Cooldown");
			REQUIRE_AFK = getBoolean("Require_Afk");
			REQUIRE_PREFIX = getString("Require_Prefix");
			SOUND = get("Sound", SimpleSound.class);
			FORMAT = new PlayerGroup<>(PlayerGroup.Type.SOUND_NOTIFY_FORMAT, getString("Format"));
		}
	}

	/**
	 * Settings for the me command
	 */
	public static class Me {

		public static Boolean ENABLED;
		public static List<String> COMMAND_ALIASES;
		public static String FORMAT;

		private static void init() {
			setPathPrefix("Me");

			ENABLED = getBoolean("Enabled");
			COMMAND_ALIASES = getCommandList("Command_Aliases");
			FORMAT = getString("Format");
		}
	}

	/**
	 * Settings for the say command
	 */
	public static class Say {

		public static Boolean ENABLED;
		public static List<String> COMMAND_ALIASES;
		public static String FORMAT;

		private static void init() {
			setPathPrefix("Say");

			ENABLED = getBoolean("Enabled");
			COMMAND_ALIASES = getCommandList("Command_Aliases");
			FORMAT = getString("Format");
		}
	}

	/**
	 * Settings for the list command
	 */
	public static class ListPlayers {

		public static Boolean ENABLED;
		public static List<String> COMMAND_ALIASES;
		public static String FORMAT;
		public static List<String> FORMAT_HOVER;
		public static SortBy SORT_BY;

		private static void init() {
			setPathPrefix("List");

			ENABLED = getBoolean("Enabled");
			COMMAND_ALIASES = getCommandList("Command_Aliases");
			FORMAT = getString("Format");
			FORMAT_HOVER = getStringList("Format_Hover");
			SORT_BY = get("Sort_By", SortBy.class);
		}

		public enum SortBy {
			NAME,
			NICK,
			PREFIX,
			GROUP;
		}
	}

	/**
	 * Settings for the ignore command
	 */
	public static class Ignore {

		public static Boolean ENABLED;
		public static Boolean BIDIRECTIONAL;
		public static List<String> COMMAND_ALIASES;
		public static Boolean HIDE_CHAT;
		public static Boolean HIDE_ME;
		public static Boolean HIDE_SAY;
		public static Boolean STOP_PRIVATE_MESSAGES;

		private static void init() {
			setPathPrefix("Ignore");

			ENABLED = getBoolean("Enabled");
			BIDIRECTIONAL = getBoolean("Bidirectional");
			COMMAND_ALIASES = getCommandList("Command_Aliases");
			HIDE_CHAT = getBoolean("Hide_Chat");
			HIDE_ME = getBoolean("Hide_Me");
			HIDE_SAY = getBoolean("Hide_Say");
			STOP_PRIVATE_MESSAGES = getBoolean("Stop_Private_Messages");
		}
	}

	/**
	 * Settings for the mute command
	 */
	public static class Mute {

		public static Boolean ENABLED;
		public static Boolean BROADCAST;
		public static List<String> COMMAND_ALIASES;
		public static Whiteblacklist PREVENT_COMMANDS;
		public static Boolean PREVENT_BOOKS;
		public static Boolean PREVENT_SIGNS;
		public static Boolean PREVENT_ANVIL;
		public static Boolean HIDE_JOINS;
		public static Boolean HIDE_QUITS;
		public static Boolean HIDE_DEATHS;
		public static Boolean SOFT_HIDE;

		private static void init() {
			setPathPrefix("Mute");

			ENABLED = getBoolean("Enabled");
			BROADCAST = getBoolean("Broadcast");
			COMMAND_ALIASES = getCommandList("Command_Aliases");
			PREVENT_COMMANDS = new Whiteblacklist(getStringList("Prevent_Commands"), false);
			PREVENT_BOOKS = getBoolean("Prevent_Writing_Books");
			PREVENT_SIGNS = getBoolean("Prevent_Placing_Signs");
			PREVENT_ANVIL = getBoolean("Prevent_Anvil_Renaming");
			HIDE_JOINS = getBoolean("Hide_Join_Messages");
			HIDE_QUITS = getBoolean("Hide_Quit_Messages");
			HIDE_DEATHS = getBoolean("Hide_Death_Messages");
			SOFT_HIDE = getBoolean("Soft_Hide");
		}
	}

	/**
	 * Settings for the /motd command
	 */
	public static class Motd {

		public static Boolean ENABLED;
		public static SimpleTime DELAY;
		public static List<String> COMMAND_ALIASES;
		public static PlayerGroup<String> FORMAT_MOTD;
		public static String FORMAT_MOTD_FIRST_TIME;
		public static String FORMAT_MOTD_NEWCOMER;
		public static SimpleSound SOUND;
		public static Boolean PLAY_SOUND_IF_NO_MESSAGE;
		public static List<String> CONSOLE_COMMANDS;
		public static List<String> PLAYER_COMMANDS;

		private static void init() {
			setPathPrefix("Motd");

			ENABLED = getBoolean("Enabled");
			DELAY = getTime("Delay");
			COMMAND_ALIASES = getCommandList("Command_Aliases");
			FORMAT_MOTD = new PlayerGroup<>(PlayerGroup.Type.MOTD, getString("Format_Motd"));
			FORMAT_MOTD_FIRST_TIME = getString("Format_Motd_First_Time");
			FORMAT_MOTD_NEWCOMER = getString("Format_Motd_Newcomer");
			SOUND = get("Sound", SimpleSound.class);
			PLAY_SOUND_IF_NO_MESSAGE = getBoolean("Play_Sound_If_No_Message");
			CONSOLE_COMMANDS = getStringList("Commands.Console");
			PLAYER_COMMANDS = getStringList("Commands.Player");
		}
	}

	/**
	 * Settings for the /chc announce command
	 */
	public static class Announcer {

		public static SimpleSound CHAT_SOUND;

		private static void init() {
			setPathPrefix("Announcer");

			CHAT_SOUND = get("Chat_Sound", SimpleSound.class);
		}
	}

	/**
	 * Settings for the /mail command
	 */
	public static class Mail {

		public static Boolean ENABLED;
		public static SimpleTime CLEAN_AFTER;
		public static List<String> COMMAND_ALIASES;

		private static void init() {
			setPathPrefix("Mail");

			ENABLED = getBoolean("Enabled");
			CLEAN_AFTER = getTime("Clean_After");
			COMMAND_ALIASES = getCommandList("Command_Aliases");
		}
	}

	/**
	 * Settings for the /tag command
	 */
	public static class Tag {

		public static Set<org.mineacademy.chatcontrol.operator.Tag.Type> APPLY_ON;
		public static List<String> COMMAND_ALIASES;
		public static Boolean BACKWARD_COMPATIBLE;
		public static Integer MAX_NICK_LENGTH;
		public static String NICK_PREFIX;
		public static Boolean NICK_DISABLE_IMPERSONATION;

		private static void init() {
			setPathPrefix("Tag");

			APPLY_ON = new HashSet<>(getSet("Apply_On", org.mineacademy.chatcontrol.operator.Tag.Type.class));
			COMMAND_ALIASES = getCommandList("Command_Aliases");
			BACKWARD_COMPATIBLE = getBoolean("Backward_Compatible");
			MAX_NICK_LENGTH = getInteger("Max_Nick_Length");
			NICK_PREFIX = getString("Nick_Prefix");
			NICK_DISABLE_IMPERSONATION = getBoolean("Nick_Disable_Impersonation");

			Players.setNicksEnabled(APPLY_ON.contains(org.mineacademy.chatcontrol.operator.Tag.Type.NICK));
		}
	}

	/**
	 * Settings for the realname command
	 */
	public static class RealName {

		public static List<String> COMMAND_ALIASES;

		private static void init() {
			setPathPrefix("Real_Name");

			COMMAND_ALIASES = getCommandList("Command_Aliases");
		}
	}

	/**
	 * Settings for the tab list feature
	 */
	public static class TabList {

		public static Boolean ENABLED;
		public static String HEADER;
		public static String FOOTER;
		public static String FORMAT;

		private static void init() {
			setPathPrefix("Tab_List");

			ENABLED = getBoolean("Enabled");
			HEADER = getString("Header");
			FOOTER = getString("Footer");
			FORMAT = getString("Format");
		}
	}

	/**
	 * Settings for the /toggle command
	 */
	public static class Toggle {

		public static Set<org.mineacademy.chatcontrol.model.ToggleType> APPLY_ON;
		public static List<String> COMMAND_ALIASES;

		private static void init() {
			setPathPrefix("Toggle");

			APPLY_ON = new HashSet<>(getSet("Apply_On", org.mineacademy.chatcontrol.model.ToggleType.class));
			COMMAND_ALIASES = getCommandList("Command_Aliases");
		}
	}

	/**
	 * Settings for the spy command
	 */
	public static class Spy {

		public static String PREFIX;
		public static List<String> COMMAND_ALIASES;
		public static Set<org.mineacademy.chatcontrol.model.Spy.Type> APPLY_ON;
		public static String FORMAT_CHAT;
		public static String FORMAT_PARTY_CHAT;
		public static String FORMAT_COMMAND;
		public static String FORMAT_PRIVATE_MESSAGE;
		public static String FORMAT_MAIL;
		public static String FORMAT_SIGN;
		public static String FORMAT_BOOK;
		public static String FORMAT_ANVIL;
		public static Map<org.mineacademy.chatcontrol.model.Spy.Type, DiscordSpy> DISCORD;
		public static Whiteblacklist COMMANDS;

		private static void init() {
			setPathPrefix("Spy");

			PREFIX = getString("Prefix");
			COMMAND_ALIASES = getCommandList("Command_Aliases");
			APPLY_ON = new HashSet<>(getList("Apply_On", org.mineacademy.chatcontrol.model.Spy.Type.class));
			FORMAT_CHAT = getString("Format_Chat");
			FORMAT_PARTY_CHAT = getString("Format_Party_Chat");
			FORMAT_COMMAND = getString("Format_Command");
			FORMAT_PRIVATE_MESSAGE = getString("Format_Private_Message");
			FORMAT_MAIL = getString("Format_Mail");
			FORMAT_SIGN = getString("Format_Sign");
			FORMAT_BOOK = getString("Format_Book");
			FORMAT_ANVIL = getString("Format_Anvil");
			DISCORD = getMap("Discord", org.mineacademy.chatcontrol.model.Spy.Type.class, DiscordSpy.class);
			COMMANDS = new Whiteblacklist(getStringList("Commands"), false);
		}
	}

	/**
	 * Settings for settings groups
	 */
	public static class Groups {

		public static Map<String, Map<PlayerGroup.Type, Object>> LIST;

		private static void init() {
			setPathPrefix("Groups");

			LIST = new LinkedHashMap<>();

			for (final Entry<String, Object> entry : getMap("").entrySet()) {
				final String groupName = entry.getKey();
				final Map<PlayerGroup.Type, Object> settings = new LinkedHashMap<>();

				for (final Map.Entry<String, Object> groupSetting : SerializedMap.fromObject(entry.getValue()).entrySet()) {
					final PlayerGroup.Type settingKey;

					try {
						settingKey = PlayerGroup.Type.fromKey(groupSetting.getKey());

					} catch (final IllegalArgumentException ex) {
						Common.warning("Skipping invalid group setting key: " + groupSetting.getKey() + " in group " + groupName + ". Available values: " + Common.join(PlayerGroup.Type.values()));

						continue;
					}

					final Object settingValue = SerializeUtilCore.deserialize(SerializeUtil.Language.YAML, settingKey.getValidClass(), groupSetting.getValue());

					settings.put(settingKey, settingValue);
				}

				LIST.put(groupName, settings);
			}
		}
	}

	/**
	 * Settings for the warning points
	 */
	public static class WarningPoints {

		public static Boolean ENABLED;

		public static SimpleTime RESET_TASK_PERIOD;
		public static Map<String, Integer> RESET_MAP;

		public static WarnTrigger TRIGGER_CHAT_DELAY;
		public static WarnTrigger TRIGGER_CHAT_SIMILARITY;
		public static WarnTrigger TRIGGER_CHAT_LIMIT;
		public static WarnTrigger TRIGGER_COMMAND_DELAY;
		public static WarnTrigger TRIGGER_COMMAND_SIMILARITY;
		public static WarnTrigger TRIGGER_COMMAND_LIMIT;
		public static WarnTrigger TRIGGER_CAPS;

		private static void init() {
			setPathPrefix("Warning_Points");

			ENABLED = getBoolean("Enabled");

			// Load warning points
			org.mineacademy.chatcontrol.model.WarningPoints.getInstance().clearSets();

			for (final Map.Entry<String, Object> entry : getMap("Sets").entrySet()) {
				final String setName = entry.getKey();
				final SerializedMap actions = SerializedMap.fromObject(entry.getValue());

				org.mineacademy.chatcontrol.model.WarningPoints.getInstance().addSet(setName, actions);
			}

			setPathPrefix("Warning_Points.Reset_Task");

			RESET_TASK_PERIOD = getTime("Period");
			RESET_MAP = getMap("Remove", String.class, Integer.class);

			setPathPrefix("Warning_Points.Triggers");

			TRIGGER_CHAT_DELAY = getTrigger("Chat_Delay");
			TRIGGER_CHAT_SIMILARITY = getTrigger("Chat_Similarity");
			TRIGGER_CHAT_LIMIT = getTrigger("Chat_Limit");
			TRIGGER_COMMAND_DELAY = getTrigger("Command_Delay");
			TRIGGER_COMMAND_SIMILARITY = getTrigger("Command_Similarity");
			TRIGGER_COMMAND_LIMIT = getTrigger("Command_Limit");
			TRIGGER_CAPS = getTrigger("Caps");
		}

		/*
		 * Load a warning trigger from config path, that is a math formula
		 */
		private static WarnTrigger getTrigger(final String path) {
			final String[] split = getString(path).split(" ");
			ValidCore.checkBoolean(split.length > 1, "Invalid warning point trigger syntax, use <warn set> <formula> in Warning_Points.Triggers." + path);

			final org.mineacademy.chatcontrol.model.WarningPoints instance = org.mineacademy.chatcontrol.model.WarningPoints.getInstance();

			final String set = split[0];
			ValidCore.checkBoolean(instance.isSetLoaded(set), "Warn set '" + set + "' specified in Warning_Points.Triggers." + path + " does not exist! Available: " + instance.getSetNames());

			final String formula = CommonCore.joinRange(1, split);

			return new WarnTrigger(set, formula);
		}
	}

	/**
	 * Settings for new players
	 */
	public static class Newcomer {

		public static SimpleTime THRESHOLD;
		public static IsInList<String> WORLDS;
		public static Set<Tuple<String, Boolean>> PERMISSIONS;

		public static Boolean RESTRICT_SEEING_CHAT;
		public static Boolean RESTRICT_CHAT;

		public static Boolean RESTRICT_COMMANDS;
		public static Whiteblacklist RESTRICT_COMMANDS_WHITELIST;

		private static void init() {
			setPathPrefix("Newcomer");

			if (isSet("Restrict_Chat.Enabled")) {
				final boolean enabled = getBoolean("Restrict_Chat.Enabled");

				set("Restrict_Chat", enabled);
			}

			THRESHOLD = getTime("Threshold");
			WORLDS = getIsInList("Worlds", String.class);
			PERMISSIONS = loadPermissions();
			RESTRICT_SEEING_CHAT = getBoolean("Restrict_Seeing_Chat");
			RESTRICT_CHAT = getBoolean("Restrict_Chat");

			setPathPrefix("Newcomer.Restrict_Commands");

			RESTRICT_COMMANDS = getBoolean("Enabled");
			RESTRICT_COMMANDS_WHITELIST = new Whiteblacklist(getStringList("Whitelist"), false);
		}

		private static Set<Tuple<String, Boolean>> loadPermissions() {
			final Set<Tuple<String, Boolean>> loaded = new HashSet<>();

			for (final String raw : getSet("Permissions", String.class)) {
				final String split[] = raw.split(" \\- ");
				final String permission = split[0];
				final boolean value = split.length > 1 ? Boolean.parseBoolean(split[1]) : true;

				loaded.add(new Tuple<>(permission, value));
			}

			return loaded;
		}
	}

	/**
	 * Settings for the chat saving and logging feature
	 */
	public static class Log {

		public static Set<LogType> APPLY_ON;
		public static SimpleTime CLEAN_AFTER;
		public static Whiteblacklist COMMAND_LIST;

		private static void init() {
			setPathPrefix("Log");

			APPLY_ON = getSet("Apply_On", LogType.class);
			CLEAN_AFTER = getTime("Clean_After");
			COMMAND_LIST = new Whiteblacklist(getStringList("Command_List"), false);
		}
	}

	/**
	 * AuthMe support.
	 */
	public static class AuthMe {

		public static List<String> ALLOW_COMMANDS;
		public static Boolean DELAY_JOIN_MESSAGE_UNTIL_LOGGED;
		public static Boolean HIDE_QUIT_MESSAGE_IF_NOT_LOGGED;

		private static void init() {
			setPathPrefix("AuthMe");

			ALLOW_COMMANDS = getStringList("Allow_Commands");
			DELAY_JOIN_MESSAGE_UNTIL_LOGGED = getBoolean("Delay_Join_Message_Until_Logged");
			HIDE_QUIT_MESSAGE_IF_NOT_LOGGED = getBoolean("Hide_Quit_Message_If_Not_Logged");
		}
	}

	/**
	 * CoreArena support.
	 */
	public static class CoreArena {

		public static Boolean IGNORE_DEATH_MESSAGES;
		public static Boolean SEND_CHANNEL_MESSAGES;

		private static void init() {
			setPathPrefix("CoreArena");

			IGNORE_DEATH_MESSAGES = getBoolean("Ignore_Death_Messages");
			SEND_CHANNEL_MESSAGES = getBoolean("Send_Channel_Messages");
		}
	}

	/**
	 * Discord support.
	 */
	public static class Discord {

		public static Boolean ENABLED;
		public static Boolean FILTER_CHAT;
		public static Boolean REMOVE_EMOJIS_V2;
		public static Boolean WEBHOOK;
		public static Boolean SEND_MESSAGES_AS_BOT;

		private static void init() {
			setPathPrefix("Discord");

			ENABLED = getBoolean("Enabled");
			FILTER_CHAT = getBoolean("Filter_Chat");
			REMOVE_EMOJIS_V2 = getBoolean("Remove_Emojis");
			WEBHOOK = getBoolean("Webhook");
			SEND_MESSAGES_AS_BOT = getBoolean("Send_Messages_As_Bot");
		}
	}

	/**
	 * ProtocolLib support.
	 */
	public static class ProtocolLib {

		public static Boolean ENABLED;

		private static void init() {
			setPathPrefix("ProtocolLib");

			if (isSet("Listen_For_Packets"))
				move("Listen_For_Packets", "ProtocolLib.Enabled");

			ENABLED = getBoolean("Enabled");
		}
	}

	/**
	 * TownyChat support.
	 */
	public static class TownyChat {

		public static Whiteblacklist CHANNEL_WHITELIST;

		private static void init() {
			setPathPrefix("TownyChat");

			CHANNEL_WHITELIST = new Whiteblacklist(getStringList("Spy_Channels_Whitelist"), false);
		}
	}

	/**
	 * Dymap support.
	 */
	public static class Dynmap {

		public static Boolean ENABLED;
		public static Boolean PROXY;
		public static String FALLBACK_NAME;
		public static String FORMAT;
		public static String FORMAT_CONSOLE;
		public static String FORMAT_DISCORD;
		public static Long DISCORD_CHANNEL_ID;

		private static void init() {
			setPathPrefix("Dynmap");

			ENABLED = getBoolean("Enabled");
			PROXY = getBoolean("Proxy");
			FALLBACK_NAME = getString("Fallback_Name");
			FORMAT = getString("Format");
			FORMAT_DISCORD = getString("Format_Discord");
			FORMAT_CONSOLE = getString("Format_Console");
			DISCORD_CHANNEL_ID = getLong("Discord_Channel_Id");
		}
	}

	/**
	 * Performance options.
	 */
	public static class Performance {

		public static Boolean SUPPORT_GRADIENTS_IN_HOVER;
		public static Boolean SUPPORT_VARIABLES_IN_VARIABLES;
		public static Boolean SUPPORT_FULL_PLACEHOLDERAPI_SYNTAX;
		public static Boolean UPGRADE_HEX_TO_MINI_IN_VARIABLES;

		private static void init() {
			setPathPrefix("Performance");

			SUPPORT_GRADIENTS_IN_HOVER = getBoolean("Support_Gradients_In_Hover");
			SUPPORT_VARIABLES_IN_VARIABLES = getBoolean("Support_Variables_In_Variables");
			SUPPORT_FULL_PLACEHOLDERAPI_SYNTAX = getBoolean("Support_Full_PlaceholderAPI_Syntax");
			UPGRADE_HEX_TO_MINI_IN_VARIABLES = getBoolean("Upgrade_Hex_To_Mini_In_Variables");

			Variables.setDoubleParse(SUPPORT_VARIABLES_IN_VARIABLES);
			Variables.setConvertHexToMini(UPGRADE_HEX_TO_MINI_IN_VARIABLES);
		}
	}

	public static Boolean RULES_CASE_INSENSITIVE;
	public static Boolean RULES_UNICODE;

	public static Boolean MAKE_CHAT_LINKS_CLICKABLE;
	public static Boolean FILTER_UNKNOWN_MINI_TAGS;
	public static Boolean UUID_LOOKUP;
	public static Tuple<EventPriority, Boolean> CHAT_LISTENER_PRIORITY;
	public static Boolean SHOW_TIPS;
	public static SimpleTime CLEAR_DATA_IF_INACTIVE;

	private static void init() {
		// Need to be init first since server name stored there is required by remote db
		loadProxySettings();

		setPathPrefix(null);

		// Need to be here because this is loaded first and is used in the above subclasses such as in whiteblacklist
		RULES_CASE_INSENSITIVE = getBoolean("Rules.Case_Insensitive");
		RULES_UNICODE = getBoolean("Rules.Unicode");

		MAKE_CHAT_LINKS_CLICKABLE = getBoolean("Make_Chat_Links_Clickable");
		FILTER_UNKNOWN_MINI_TAGS = getBoolean("Filter_Unknown_Mini_Tags");
		UUID_LOOKUP = getBoolean("UUID_Lookup");

		String priority = getString("Chat_Listener_Priority").toUpperCase();
		boolean modern = priority.endsWith("-MODERN");

		if (modern)
			priority = priority.replace("-MODERN", "");

		if (modern && !Remain.hasAdventureChatEvent()) {
			Debugger.debug("chat", "Modern chat listener is not available on your server version, switching to legacy...");

			modern = false;
		}

		CHAT_LISTENER_PRIORITY = new Tuple<>(ReflectionUtil.lookupEnum(EventPriority.class, priority), modern);
		SHOW_TIPS = getBoolean("Show_Tips");
		CLEAR_DATA_IF_INACTIVE = getTime("Clear_Data_If_Inactive");

		// Force register tools if regions are enabled
		REGISTER_TOOLS = REGISTER_REGIONS;
	}

	private static void loadProxySettings() {
		final File proxyYml = FileUtil.extract("proxy.yml");
		final YamlConfig proxyConfig = YamlConfig.fromFile(proxyYml);
		boolean save = false;

		setPathPrefix(null);

		if (isSet("Server_Name")) {
			proxyConfig.set("Server_Name", getString("Server_Name"));

			save = true;
		}

		setPathPrefix("Proxy");

		if (isSet("Enabled")) {
			proxyConfig.set("Enabled", getBoolean("Enabled"));

			save = true;
		}

		if (isSet("Prefix")) {
			proxyConfig.set("Prefix", getString("Prefix"));

			save = true;
		}

		Proxy.ENABLED = proxyConfig.getBoolean("Enabled");
		Proxy.PREFIX = proxyConfig.getString("Prefix");

		try {
			Platform.setCustomServerName(proxyConfig.getString("Server_Name"));

		} catch (final IllegalArgumentException ex) {
			CommonCore.logFramed(true,
					"(You will find the Server_Name key in proxy.yml)",
					ex.getMessage());

			ex.printStackTrace();
		}

		if (Proxy.ENABLED && Platform.getCustomServerName().isEmpty())
			throw new FoException("Please set a unique server name in proxy.yml before loading the plugin when using proxy.", false);

		if (save) {
			Common.log("Migrated 'Proxy' section from settings.yml to proxy.yml. Please check.");

			proxyConfig.save();
		}
	}
}