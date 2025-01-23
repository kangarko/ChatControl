package org.mineacademy.chatcontrol;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.command.CommandDummy;
import org.mineacademy.chatcontrol.command.CommandIgnore;
import org.mineacademy.chatcontrol.command.CommandList;
import org.mineacademy.chatcontrol.command.CommandMail;
import org.mineacademy.chatcontrol.command.CommandMe;
import org.mineacademy.chatcontrol.command.CommandMotd;
import org.mineacademy.chatcontrol.command.CommandMute;
import org.mineacademy.chatcontrol.command.CommandRealName;
import org.mineacademy.chatcontrol.command.CommandReply;
import org.mineacademy.chatcontrol.command.CommandSay;
import org.mineacademy.chatcontrol.command.CommandSpy;
import org.mineacademy.chatcontrol.command.CommandTag;
import org.mineacademy.chatcontrol.command.CommandTell;
import org.mineacademy.chatcontrol.command.CommandToggle;
import org.mineacademy.chatcontrol.listener.BookListener;
import org.mineacademy.chatcontrol.listener.CommandListener;
import org.mineacademy.chatcontrol.listener.PlayerListener;
import org.mineacademy.chatcontrol.listener.TabListener;
import org.mineacademy.chatcontrol.listener.ThirdPartiesListener;
import org.mineacademy.chatcontrol.listener.chat.AdventureChatListener;
import org.mineacademy.chatcontrol.listener.chat.LegacyChatListener;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Colors;
import org.mineacademy.chatcontrol.model.Format;
import org.mineacademy.chatcontrol.model.Migrator;
import org.mineacademy.chatcontrol.model.Newcomer;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.model.ProxyChat;
import org.mineacademy.chatcontrol.model.WarningPoints;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.model.db.Database;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.operator.Groups;
import org.mineacademy.chatcontrol.operator.PlayerMessages;
import org.mineacademy.chatcontrol.operator.Rules;
import org.mineacademy.chatcontrol.operator.Tag;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.RandomUtil;
import org.mineacademy.fo.command.DebugSubCommand;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleBook;
import org.mineacademy.fo.model.Variable;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.platform.BukkitPlugin;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.region.DiskRegion;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.YamlConfig;
import org.mineacademy.fo.visual.VisualizedRegion;

import github.scarsz.discordsrv.DiscordSRV;

/**
 * ChatControl is a simple chat management plugin.
 *
 * @since last major code audit November 2024
 */
public final class ChatControl extends BukkitPlugin {

	@Override
	public String[] getStartupLogo() {
		return new String[] {
				"&c ____ _  _ ____ ___ ____ ____ _  _ ___ ____ ____ _     ",
				"&4 |    |__| |__|  |  |    |  | |\\ |  |  |__/ |  | |    ",
				"&4 |___ |  | |  |  |  |___ |__| | \\|  |  |  \\ |__| |___",
		};
	}

	@Override
	protected void onPluginLoad() {
		Variable.PROTOTYPE_PATH = fileName -> {

			// Return different prototypes for different variable types since MESSAGE
			// variables do not support all keys
			final File file = FileUtil.createIfNotExists("variables/" + fileName + ".yml");
			final YamlConfig config = YamlConfig.fromFile(file);
			final String type = config.getString("Type", "FORMAT").toLowerCase();

			return "prototype/" + "variable-" + ("format".equals(type) ? "format" : "message") + ".yml";
		};

		// Add Sentry tags
		Debugger.addSentryTag(() -> {
			final Map<String, String> tags = new HashMap<>();

			tags.put("db_type", Settings.Database.TYPE.getKey());
			tags.put("db_player_type", Settings.UUID_LOOKUP ? "uuid" : "name");
			tags.put("proxy_enabled", Settings.Proxy.ENABLED ? "true" : "false");

			return tags;
		});

		// Set how to get the region for tools
		DiskRegion.setCreatedPlayerRegionGetter(player -> SenderCache.from(player).getCreatedRegion());
		DiskRegion.setCreatedPlayerRegionResetter(player -> SenderCache.from(player).setCreatedRegion(new VisualizedRegion()));
	}

	@Override
	protected void onPluginPreStart() {
		Migrator.migrateV10Settings(this);
	}

	@Override
	protected void onPluginStart() {
		this.checkSecureProfile();

		// Update and create tables
		Database.getInstance().prepareTables();

		// Reload database cache for online players
		for (final Player player : Remain.getOnlinePlayers()) {
			final SenderCache senderCache = SenderCache.from(player);

			if (!senderCache.isDatabaseLoaded() && senderCache.getCacheLoadingTask() == null)
				Database.getInstance().loadAndStoreCache(player, senderCache, cache -> {
				});
		}

		// Register third party early to prevent duplication on reload
		ThirdPartiesListener.registerEvents();

		if (Settings.Ignore.ENABLED)
			this.registerCommand(new CommandIgnore());

		if (Settings.Mail.ENABLED)
			this.registerCommand(new CommandMail());

		if (Settings.Me.ENABLED)
			this.registerCommand(new CommandMe());

		if (Settings.Say.ENABLED)
			this.registerCommand(new CommandSay());

		if (Settings.ListPlayers.ENABLED)
			this.registerCommand(new CommandList());

		if (Settings.Motd.ENABLED)
			this.registerCommand(new CommandMotd());

		if (Settings.Mute.ENABLED)
			this.registerCommand(new CommandMute());

		if (!Settings.Tag.APPLY_ON.isEmpty())
			this.registerCommand(new CommandTag());

		if (Settings.Tag.APPLY_ON.contains(Tag.Type.NICK))
			this.registerCommand(new CommandRealName());

		if (!Settings.Spy.APPLY_ON.isEmpty())
			this.registerCommand(new CommandSpy());

		if (!Settings.Toggle.APPLY_ON.isEmpty()) {
			final boolean deregister = !Platform.isPluginInstalled("PvPManager");

			new CommandToggle().register(deregister, deregister);
		}

		if (Settings.PrivateMessages.ENABLED) {
			this.registerCommand(new CommandReply());

			new CommandTell().register(!Platform.isPluginInstalled("Towny"));
		}

		this.registerCommand(new CommandDummy());

		if (Remain.hasAdventureChatEvent() && Settings.CHAT_LISTENER_PRIORITY.getValue())
			AdventureChatListener.register();
		else
			LegacyChatListener.register();

		this.registerEvents(CommandListener.getInstance());
		this.registerEvents(PlayerListener.getInstance());

		if (Settings.TabComplete.ENABLED && MinecraftVersion.atLeast(V.v1_13))
			this.registerEvents(TabListener.getInstance());

		if (Remain.hasBookEvent())
			this.registerEvents(BookListener.getInstance());

		this.loadData();

		// Run tasks
		WarningPoints.scheduleTask();
		Newcomer.scheduleTask();
		ProxyChat.scheduleTask();

		// Add more info to debug zip feature
		DebugSubCommand.addDebugLines(
				"Proxy: " + Settings.Proxy.ENABLED,
				"Database: " + Settings.Database.TYPE);

		if (HookManager.isLiteBansLoaded() && !HookManager.isVaultLoaded())
			CommonCore.warning("Please install Vault plugin to enable prefix/suffix/group variables since you have LiteBans installed.");

		if (Remain.isFolia() && !HookManager.isProtocolLibLoaded())
			CommonCore.warning("Please install ProtocolLib when on Folia otherwise parts of the plugin might not work.");

		if (Platform.isPluginInstalled("TAB"))
			CommonCore.warning("TAB detected. Use %chatcontrol_player_nick_section%, %chatcontrol_player_prefix_section% and %chatcontrol_player_suffix_section% variables in groups.yml to ensure compatibility.");

		if (Platform.isPluginInstalled("InteractiveChat")) {
			CommonCore.warning("InteractiveChat detected. If having issues, adjust our Chat_Listener_Priority key in settings.yml (try setting it to LOWEST, without -MODERN prefix).");

			if (Platform.isPluginInstalled("Tebex"))
				CommonCore.warning("Tebex detected. If your network is making money, please purchase the plugin at mineacademy.org/chatcontrol to get support support its development.");

			if (Settings.Colors.APPLY_ON.contains(Colors.Type.CHAT)) {
				final File interactiveChatFile = new File(this.getDataFolder().getParent(), "InteractiveChat/config.yml");

				if (interactiveChatFile.exists()) {
					final YamlConfig interactiveChatConfig = YamlConfig.fromFile(interactiveChatFile);
					final List<String> formats = interactiveChatConfig.getStringList("Settings.FormattingTags.AdditionalRGBFormats");

					if (formats.contains("#([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])"))
						CommonCore.logFramed(
								"INCOMPATIBILITY WITH INTERACTIVECHAT DETECTED",
								"",
								"ChatControl hex colors are incompatible with the",
								"Settings.FormattingTags.AdditionalRGBFormats key in",
								"InteractiveChat's config.yml containing:",
								"#([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])",
								"",
								"Either remove that key from InteractiveChat's config.yml",
								"or remove 'chat' (and possibly others) from Colors.Apply_On",
								"in ChatControl's settings.yml.");
				}
			}
		}

		if (HookManager.isDiscordSRVLoaded() && Settings.Channels.ENABLED && Settings.Discord.ENABLED)
			if (DiscordSRV.config().getBoolean("DiscordChatChannelMinecraftToDiscord"))
				CommonCore.logFramed(
						"Warning: The key DiscordChatChannelMinecraftToDiscord",
						"is set on true in your DiscordSRV/config.yml file.",
						"",
						"Since you have Channels and Discord integration enabled",
						"in ChatControl, this will produce duplicated messages.",
						"Set the key to false to resolve this.");

		if (Settings.SHOW_TIPS)
			CommonCore.log("",
					"For documentation & tutorial, see:",
					"&chttps://github.com/kangarko/chatcontrol/wiki",
					"",
					"Loaded! Random joke: " + this.getRandomSplash(),
					"");

		Platform.runTask(() -> {
			for (final String fileName : Arrays.asList("usermap.csv", "blocked-commands.log")) {
				final File file = new File(this.getDataFolder(), fileName);

				if (file.exists()) {
					file.delete();

					CommonCore.log("Deleted " + fileName + " file. It's no longer used by ChatControl.");
				}
			}
		});
	}

	/*
	 * Warn if secure profile is enabled
	 */
	private void checkSecureProfile() {
		final File serverProperties = new File("server.properties");
		final Properties properties = new Properties();

		try {
			properties.load(new FileInputStream(serverProperties));
		} catch (final IOException ex) {
			ex.printStackTrace();
		}

		final boolean enforceSecureProfile = Boolean.parseBoolean(properties.getProperty("enforce-secure-profile", "false"));

		if (enforceSecureProfile)
			CommonCore.warning("It is advised you set 'enforce-secure-profile' to false in server.properties for best performance and improved player privacy.");
	}

	@Override
	protected void onPluginPreReload() {

		// Reload database before its instance is replaced by settings being reloaded
		Database.getInstance().disconnect();
	}

	@Override
	protected void onPluginReload() {
		Variables.setDoubleParse(Settings.Performance.SUPPORT_VARIABLES_IN_VARIABLES);

		this.loadData();

		for (final Player online : Remain.getOnlinePlayers()) {
			final SenderCache senderCache = SenderCache.from(online);

			if (senderCache.isDatabaseLoaded()) {
				final WrappedSender wrapped = WrappedSender.fromPlayerCaches(online, PlayerCache.fromCached(online), senderCache);

				if (Settings.Channels.ENABLED)
					Channel.autoJoin(online, wrapped.getPlayerCache());

				Players.setTablistName(wrapped);
			}
		}
	}

	/*
	 * A common call for startup and reloading
	 */
	private void loadData() {
		SimpleBook.copyDefaults();

		// Load parts of the plugin
		Variable.loadVariables();
		Channel.loadChannels();
		Format.loadFormats();

		// Load rule system
		Rules.getInstance().load();
		Groups.getInstance().load();
		PlayerMessages.getInstance().load();

		// Copy sample image but only if folder doesn't exist so people can remove it
		if (!FileUtil.getFile("images").exists())
			FileUtil.extractRaw("images/creeper-head.png");
	}

	/*
	 * Time for some fun!
	 */
	private String getRandomSplash() {
		return RandomUtil.nextItem(
				"Requires at least 32Gb of RAM! #unfortunatelyNotAJoke #joke",
				"Never closes database connections #dailyLeaks #patched",
				"Uses outdated log4j! #joke",
				"Try '/me is the best' today! #seriously",
				"Cracked by kangarko (MineAcademy.org) #joke",
				"Censored for stating the obvious! #elonMusk");
	}

	@Override
	public Set<String> getConsoleFilter() {
		return Settings.ConsoleFilter.ENABLED ? Settings.ConsoleFilter.MESSAGES : new HashSet<>();
	}

	@Override
	public boolean isRegexCaseInsensitive() {
		return Settings.RULES_CASE_INSENSITIVE;
	}

	@Override
	public boolean isRegexUnicode() {
		return Settings.RULES_UNICODE;
	}

	/**
	 * The inception year -- whoa long time ago!
	 *
	 * @return the year
	 */
	@Override
	public int getFoundedYear() {
		return 2013;
	}

	@Override
	public String getSentryDsn() {
		return "https://f3e0e6f4236a18360bf321211866ae6f@o4508048573661184.ingest.us.sentry.io/4508052468269056";
	}

	@Override
	public int getBStatsPluginId() {
		return 13100;
	}

	@Override
	public int getBuiltByBitId() {
		return 18217;
	}

	@Override
	protected boolean useFullPlaceholderAPIParser() {
		return Settings.Performance.SUPPORT_FULL_PLACEHOLDERAPI_SYNTAX;
	}
}
