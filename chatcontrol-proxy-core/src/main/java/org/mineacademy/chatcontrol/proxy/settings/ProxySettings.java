package org.mineacademy.chatcontrol.proxy.settings;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mineacademy.chatcontrol.model.PlayerMessageType;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.model.IsInList;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.settings.SimpleSettings;

/**
 * Represents the main plugin configuration
 */
@SuppressWarnings("unused")
public final class ProxySettings extends SimpleSettings {

	/**
	 * @see org.mineacademy.vfo.settings.YamlStaticConfig#getUncommentedSections()
	 */
	@Override
	protected List<String> getUncommentedSections() {
		return Arrays.asList("Server_Aliases", "Clusters.List", "Tab_Complete.Filter_Arguments", "Messages.Prefix");
	}

	/**
	 * Settings for timed message broadcaster
	 */
	public static class Messages {

		public static Set<PlayerMessageType> APPLY_ON;
		public static List<String> IGNORED_SERVERS = new ArrayList<>();
		public static Boolean STOP_ON_FIRST_MATCH;
		public static Map<PlayerMessageType, String> PREFIX;
		public static SimpleTime DEFER_JOIN_MESSAGE_BY;

		private static void init() {
			setPathPrefix(null);

			if (isSet("Messages_New"))
				move("Messages_New", "Messages");

			setPathPrefix("Messages");

			APPLY_ON = new HashSet<>(getList("Apply_On", PlayerMessageType.class));
			IGNORED_SERVERS = new ArrayList<>(getStringList("Ignored_Servers"));
			STOP_ON_FIRST_MATCH = getBoolean("Stop_On_First_Match");
			PREFIX = getMap("Prefix", PlayerMessageType.class, String.class);
			DEFER_JOIN_MESSAGE_BY = getTime("Defer_Join_Message_By");
		}
	}

	/**
	 * Settings for tab filter
	 */
	public static class TabComplete {

		public static Map<String, IsInList<String>> FILTER_ARGUMENTS;

		private static void init() {
			setPathPrefix("Tab_Complete");

			final Map<String, IsInList<String>> filterArgs = new HashMap<>();

			for (final Map.Entry<String, Object> entry : getMap("Filter_Arguments").entrySet()) {
				final String label = entry.getKey();
				final IsInList<String> args = IsInList.fromList((List<String>) entry.getValue());

				filterArgs.put(label, args);
			}

			FILTER_ARGUMENTS = filterArgs;
		}
	}

	/**
	 * Clusters
	 */
	public static class Clusters {

		public static Boolean ENABLED;
		public static Map<String, Set<String>> LIST;

		private static void init() {
			setPathPrefix("Clusters");

			ENABLED = getBoolean("Enabled");
			LIST = new HashMap<>();

			for (final Map.Entry<String, Object> entry : getMap("List").entrySet()) {
				final String clusterName = entry.getKey();
				final List<String> servers = (List<String>) entry.getValue();

				LIST.put(clusterName, new HashSet<>(servers));
			}
		}

		public static String getFromServerName(final String serverName, final String serverAlias) {
			if (ENABLED)
				for (final Map.Entry<String, Set<String>> entry : LIST.entrySet()) {
					final String clusterName = entry.getKey();

					for (final String clusterServerName : entry.getValue())
						if (clusterServerName.equals(serverName) || clusterServerName.equals(serverAlias))
							return clusterName;
				}

			return "global";
		}
	}

	/**
	 * Relay chat
	 */
	public static class ChatForwarding {

		public static Boolean ENABLED;
		public static List<String> TO_SERVERS;
		public static List<String> FROM_SERVERS;

		private static void init() {
			setPathPrefix("Chat_Forwarding");

			ENABLED = getBoolean("Enabled");
			TO_SERVERS = new ArrayList<>(getStringList("To_Servers"));
			FROM_SERVERS = new ArrayList<>(getStringList("From_Servers"));
		}
	}

	/**
	 * Third party plugin support
	 *
	 */
	public static class BungeeIntegration {

		public static Boolean PARTIES_ENABLED;
		public static String PARTIES_PLAYER_NAME;

		private static void init() {
			setPathPrefix("Integration.Parties");

			PARTIES_ENABLED = getBoolean("Enabled");
			PARTIES_PLAYER_NAME = getString("Player_Name");
		}
	}

	/**
	 * Say command
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
	 * Me command
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

	public static Boolean ENABLE_FORWARD_COMMAND;
	public static Boolean MAKE_CHAT_LINKS_CLICKABLE;
	public static Boolean REDIS_INTEGRATION;
	private static Map<String, String> SERVER_ALIASES;

	/**
	 * A helper method to use SERVER_ALIASES or return the default server name if alias not set
	 *
	 * @param serverName
	 * @return
	 */
	public static String getServerNameAlias(final String serverName) {
		return ProxySettings.SERVER_ALIASES.getOrDefault(serverName, serverName);
	}

	private static void init() {
		ENABLE_FORWARD_COMMAND = getBoolean("Enable_Forward_Command");
		MAKE_CHAT_LINKS_CLICKABLE = getBoolean("Make_Chat_Links_Clickable");
		REDIS_INTEGRATION = getBoolean("Redis_Integration");
		SERVER_ALIASES = getMap("Server_Aliases", String.class, String.class);

		// Warn about a "bug" in Velocity
		final File velocityConfig = new File("velocity.toml");

		if (velocityConfig.exists())
			for (String line : FileUtil.readLinesFromFile(velocityConfig)) {
				line = line.trim();

				if (line.startsWith("bungee-plugin-message-channel")) {
					final String value = line.replace("bungee-plugin-message-channel = ", "");

					if (value.contains("true"))
						CommonCore.logFramed(
								"VelocityControl has detected a communication issue:",
								"",
								"Set 'bungee-plugin-message-channel' to false in",
								"in velocity.toml. We need this to broadcast",
								"on BungeeCord channel.",
								"",
								"Notice: We will handle BungeeCord channel for",
								"other plugins so they will continue to function.");

					break;
				}
			}
	}
}
