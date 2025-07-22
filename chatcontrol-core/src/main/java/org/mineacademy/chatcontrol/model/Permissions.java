package org.mineacademy.chatcontrol.model;

import org.mineacademy.fo.command.annotation.Permission;
import org.mineacademy.fo.command.annotation.PermissionGroup;

/**
 * Holds all permissions we can use in the plugin that
 * are also dynamically loaded in the /chc perms command.
 *
 * IF YOU ARE reading this class and have no coding knowledge, you need
 * to give permissions after the public static final String XX = ""
 * such as chatcontrol.command.book is the right permission etc.
 */
public final class Permissions {

	@PermissionGroup("Permissions for main commands.")
	public static final class Command {

		@Permission("Announce important messages to everyone. Append chat, title, actionbar, bossbar or toast at the end.")
		public static final String ANNOUNCE_TYPE = "chatcontrol.command.announce.";

		@Permission("Read or save books you can use in rules etc.")
		public static final String BOOK = "chatcontrol.command.book";

		@Permission("Clear the game chat.")
		public static final String CLEAR = "chatcontrol.command.clear";

		@Permission("Clear the console.")
		public static final String CLEAR_CONSOLE = "chatcontrol.command.clear.console";

		@Permission("Change your chat color/decoration.")
		public static final String COLOR = "chatcontrol.command.color";

		@Permission("Change another player's color/decoration.")
		public static final String COLOR_OTHERS = "chatcontrol.command.color.others";

		@Permission("Compress settings for GitHub issues.")
		public static final String DEBUG = "chatcontrol.command.debug";

		@Permission("Send commands to proxy or another server.")
		public static final String FORWARD = "chatcontrol.command.forward";

		@Permission("Toggle seeing messages/PMs from players.")
		public static final String IGNORE = "chatcontrol.command.ignore";

		@Permission("List who is ignoring messages/PMs.")
		public static final String IGNORE_LIST = "chatcontrol.command.ignore.list";

		@Permission("Toggle seeing messages/PMs for others.")
		public static final String IGNORE_OTHERS = "chatcontrol.command.ignore.others";

		@Permission("Print various debug information.")
		public static final String INFO = "chatcontrol.command.info";

		@Permission("Browse players on your server or proxy.")
		public static final String LIST = "chatcontrol.command.list";

		@Permission("View last player communication.")
		public static final String LOG = "chatcontrol.command.log";

		@Permission("Manage your game mail.")
		public static final String MAIL = "chatcontrol.command.mail";

		@Permission("Send mail to all players who ever joined the server.")
		public static final String MAIL_SEND_ALL = "chatcontrol.command.mail.send.all";

		@Permission("Send mail to all online players.")
		public static final String MAIL_SEND_ONLINE = "chatcontrol.command.mail.send.online";

		@Permission("Send a formatted message.")
		public static final String ME = "chatcontrol.command.me";

		@Permission("Send a formatted message, similar to me but no bungee support.")
		public static final String SAY = "chatcontrol.command.say";

		@Permission("Manage player messages.")
		public static final String MESSAGE = "chatcontrol.command.message";

		@Permission("Migrate data between MySQL and data.yml.")
		public static final String MIGRATE = "chatcontrol.command.migrate";

		@Permission("Read the message of the day.")
		public static final String MOTD = "chatcontrol.command.motd";

		@Permission("Send the message of the day to other players.")
		public static final String MOTD_OTHERS = "chatcontrol.command.motd.others";

		@Permission("Mute the game chat.")
		public static final String MUTE = "chatcontrol.command.mute";

		@Permission("List all plugin permissions.")
		public static final String PERMISSIONS = "chatcontrol.command.permissions";

		@Permission("Manage player warning points.")
		public static final String POINTS = "chatcontrol.command.points";

		@Permission("Remove past player's messages.")
		public static final String PURGE = "chatcontrol.command.purge";

		@Permission("Look up player's real name and nick.")
		public static final String REAL_NAME = "chatcontrol.command.realname";

		@Permission("Manage map regions used in rules.")
		public static final String REGION = "chatcontrol.command.region";

		@Permission("Reload plugin configuration.")
		public static final String RELOAD = "chatcontrol.command.reload";

		@Permission("Reply to last player who messaged you.")
		public static final String REPLY = "chatcontrol.command.reply";

		@Permission("Manage the rules system.")
		public static final String RULE = "chatcontrol.command.rule";

		@Permission("Execute JavaScript scripts.")
		public static final String SCRIPT = "chatcontrol.command.script";

		@Permission("Send the chat message through the given format.")
		public static final String SEND_FORMAT = "chatcontrol.command.sendformat";

		@Permission("Toggle spying player commands and messages.")
		public static final String SPY = "chatcontrol.command.spy";

		@Permission("Set yourself a tag such as prefix, suffix or nick. Append prefix, suffix or nick at the end.")
		public static final String TAG_TYPE = "chatcontrol.command.tag.";

		@Permission("Control tags for players.")
		public static final String TAG_ADMIN = "chatcontrol.command.tag.admin";

		@Permission("Send private messages to players.")
		public static final String TELL = "chatcontrol.command.tell";

		@Permission("Run a message through our rules.")
		public static final String TEST_RULES = "chatcontrol.command.testrules";

		@Permission("Forcefully save player data to the database.")
		public static final String TEST_SAVE = "chatcontrol.command.testsave";

		@Permission("Discover what ChatControl is and how it can help your server.")
		public static final String TOUR = "chatcontrol.command.tour";

		@Permission("Toggle seeing parts of the plugin. Append mail, announcement, me, pm, death, join, kick, quit, list or soundnotify at the end.")
		public static final String TOGGLE_TYPE = "chatcontrol.command.toggle.";

		@Permission(value = "Toggle a given plugin part on, see the main toggle permission.", def = true)
		public static final String TOGGLE_STATE_ON = "chatcontrol.command.toggle.on";

		@Permission(value = "Toggle a given plugin part off, see the main toggle permission.", def = true)
		public static final String TOGGLE_STATE_OFF = "chatcontrol.command.toggle.off";

		@Permission("Reload player's tab list name.")
		public static final String UPDATE = "chatcontrol.command.update";
	}

	@PermissionGroup("Use certain colors in chat via & or via command.")
	public static final class Color {

		@Permission("Allow players use & colors in chat. Append color name at the end such as red.")
		public static final String COLOR = "chatcontrol.color.";

		@Permission("Allow players use MiniMessage tags with actions in chat such as <click>, <hover> tags etc. Warning: Those tags can be exploited. Append the action at the end. Supported actions: hover, click, insertion, rainbow, and font")
		public static final String ACTION = "chatcontrol.action.";

		@Permission("Allow players use hex colors in chat. Append color at the end such as ccffdd without #.")
		public static final String HEXCOLOR = "chatcontrol.hexcolor.";

		@Permission("Allow players use color names in /{label_main} color command and menu. Append color at the end with name such as red.")
		public static final String GUICOLOR = "chatcontrol.guicolor.";

		@Permission("Allow players use hex colors in /{label_main} color command and menu. Append color at the end with code such as ccffdd without #.")
		public static final String HEXGUICOLOR = "chatcontrol.hexguicolor.";

		@Permission(value = "Allow players use & and hex colors. Append Colors.Apply_On sections from settings.yml at the end (by default players can use colors everywhere)", def = true)
		public static final String USE = "chatcontrol.use.color.";
	}

	@PermissionGroup("Prevent applying certain parts of the plugin.")
	public static final class Bypass {

		@Permission("Bypass the anticaps filter.")
		public static final String CAPS = "chatcontrol.bypass.caps";

		@Permission("Prevents your screen from getting wiped when chat is cleared.")
		public static final String CLEAR = "chatcontrol.bypass.clear";

		@Permission("Bypass time limit for messages.")
		public static final String DELAY_CHAT = "chatcontrol.bypass.delay.chat";

		@Permission("Bypass time limit for commands.")
		public static final String DELAY_COMMAND = "chatcontrol.bypass.delay.command";

		@Permission("Do not apply capitalize first/insert dot grammar adjustments.")
		public static final String GRAMMAR = "chatcontrol.bypass.grammar";

		@Permission("Prevent your messages and commands from being logged.")
		public static final String LOG = "chatcontrol.bypass.log";

		@Permission("Allow player joining if he has a disallowed nickname.")
		public static final String LOGIN_USERNAMES = "chatcontrol.bypass.login.usernames";

		@Permission("Prevent antibot chat/command until move check.")
		public static final String MOVE = "chatcontrol.bypass.move";

		@Permission("Except player from different rules if he is newcomer.")
		public static final String NEWCOMER = "chatcontrol.bypass.newcomer";

		@Permission("Bypass the vanilla antispam kick when typing rapidly.")
		public static final String SPAM_KICK = "chatcontrol.bypass.spamkick";

		@Permission("Prevent antibot sign duplication check.")
		public static final String SIGN_DUPLICATION = "chatcontrol.bypass.signduplication";

		@Permission("Bypass chat or channel mute.")
		public static final String MUTE = "chatcontrol.bypass.mute";

		@Permission("Bypass period antispam check.")
		public static final String PERIOD = "chatcontrol.bypass.period";

		@Permission("Bypass channel range and reach everyone on all worlds. False even to OPs by default.")
		public static final String RANGE = "chatcontrol.bypass.range";

		@Permission("Bypass channel range and reach everyone on the same world only. False even to OPs by default.")
		public static final String RANGE_WORLD = "chatcontrol.bypass.range.world";

		@Permission("Send messages and private messages to players who ignore you, or have PMs disabled.")
		public static final String REACH = "chatcontrol.bypass.reach";

		@Permission("See and message vanished players.")
		public static final String VANISH = "chatcontrol.bypass.vanish";

		@Permission("Bypass similarity antispam check for messages.")
		public static final String SIMILARITY_CHAT = "chatcontrol.bypass.similarity.chat";

		@Permission("Bypass similarity antispam check for commands.")
		public static final String SIMILARITY_COMMAND = "chatcontrol.bypass.similarity.command";

		@Permission("Bypass tab-complete filtering.")
		public static final String TAB_COMPLETE = "chatcontrol.bypass.tabcomplete";

		@Permission("Do not receive warning points and bypass their actions.")
		public static final String WARNING_POINTS = "chatcontrol.bypass.warnpoints";

		@Permission("Bypass the antispam parrot check.")
		public static final String PARROT = "chatcontrol.bypass.parrot";

		@Permission("Prevent player actions from being spied upon. Append chat, command, private_message, mail, sígn, book or anvil at the end.")
		public static final String SPY_TYPE = "chatcontrol.bypass.spy.";

		@Permission("Allows the user to see all players across the network when tab-completing private messages, even if cross-server tab completion is disabled in the config.")
		public static final String NETWORK_TAB_COMPLETING = "chatcontrol.bypass.network.tabcomplete";
	}

	@PermissionGroup("Permissions for chat channels.")
	public static final class Channel {

		@Permission("Automatically join the given channel to the given mode on join")
		public static final String AUTO_JOIN = "chatcontrol.channel.autojoin.{channel}.{mode}";

		@Permission("Join channel in mode with '/{label_channel} join'")
		public static final String JOIN = "chatcontrol.channel.join.{channel}.{mode}";

		@Permission("Join others to channels with '/{label_channel} join'")
		public static final String JOIN_OTHERS = "chatcontrol.channel.join.others";

		@Permission("Leave channel with '/{label_channel} leave'")
		public static final String LEAVE = "chatcontrol.channel.leave.{channel}";

		@Permission("Leave others from channels with '/{label_channel} leave'")
		public static final String LEAVE_OTHERS = "chatcontrol.channel.leave.others";

		@Permission("List players in channels with '/{label_channel} list'")
		public static final String LIST = "chatcontrol.channel.list";

		@Permission("Mute or kick players from channels in '/{label_channel} list'")
		public static final String LIST_OPTIONS = "chatcontrol.channel.list.options";

		@Permission("Send messages to channel as a player with '/{label_channel} sendas'")
		public static final String SEND_AS = "chatcontrol.channel.sendas.{channel}";

		@Permission("Send messages to channel with '/{label_channel} send'")
		public static final String SEND = "chatcontrol.channel.send.{channel}";

		@Permission("Manually set a player's channel with '/{label_channel} set'")
		public static final String SET = "chatcontrol.channel.set";
	}

	@PermissionGroup("Control messages the player can receive.")
	public static final class Receive {

		@Permission(value = "See messages from /{label_main} announce.", def = true)
		public static final String ANNOUNCER = "chatcontrol.receive.announcer";
	}

	@PermissionGroup("Permissions for Discord related features.")
	public static final class Discord {

		@Permission(value = "Able to use @ in chat messages and tag roles on Discord.")
		public static final String TAG = "chatcontrol.discord.tag";
	}

	@PermissionGroup("Spying related permissions.")
	public static final class Spy {

		@Permission("Prevent player actions from being spied upon. Append chat, command, private_message, mail, sígn, book or anvil at the end.")
		public static final String TYPE = "chatcontrol.spy.";

		@Permission("Automatically start spying everything on join.")
		public static final String AUTO_ENABLE = "chatcontrol.spy.autoenable";
	}

	@PermissionGroup("Permissions related to game chat.")
	public static final class Chat {

		@Permission(value = "See game chat messages.", def = true)
		public static final String READ = "chatcontrol.chat.read";

		@Permission(value = "Write messages to game chat.", def = true)
		public static final String WRITE = "chatcontrol.chat.write";

		@Permission(value = "Convert URLs to clickable.", def = true)
		public static final String LINKS = "chatcontrol.chat.links";
	}

	@Permission("Automatically assign a certain group to player. Append the group name at the end.")
	public static final String GROUP_NAME = "chatcontrol.group.";

	@Permission(value = "Use the sound notify feature. True by default.", def = true)
	public static final String SOUND_NOTIFY = "chatcontrol.soundnotify";

}
