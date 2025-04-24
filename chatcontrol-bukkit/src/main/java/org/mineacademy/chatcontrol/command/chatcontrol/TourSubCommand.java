package org.mineacademy.chatcontrol.command.chatcontrol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.MainSubCommand;
import org.mineacademy.chatcontrol.listener.ThirdPartiesListener;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.remain.CompSound;
import org.mineacademy.fo.settings.SimpleSettings;

/**
 * Provides a guide through the plugin setup and use for both
 * beginners and professionals.
 */
public final class TourSubCommand extends MainSubCommand {

	public TourSubCommand() {
		super("tour");

		this.setUsage("");
		this.setDescription("Get started with ChatControl.");
		this.setPermission(Permissions.Command.TOUR);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		final String param = this.args.length == 0 ? "" : this.args[0].toLowerCase();

		//
		// Main command
		//
		if (this.args.length == 0) {
			this.tellNoPrefix("&8" + CommonCore.chatLineSmooth(),
					"<center>&cChatControl Tour",
					" ");

			this.tell(SimpleComponent
					.fromMiniNative(" - <gray>/" + this.getLabel() + " " + this.getSublabel() + " start <white>- Getting started with ChatControl.")
					.onHoverLegacy("<gray>Click to run this command.")
					.onClickRunCmd("/" + this.getLabel() + " " + this.getSublabel() + " start"));

			this.tell(SimpleComponent
					.fromMiniNative(" - <gray>/" + this.getLabel() + " " + this.getSublabel() + " hooks <white>- What other plugins we support.")
					.onHoverLegacy("<gray>Click to run this command.")
					.onClickRunCmd("/" + this.getLabel() + " " + this.getSublabel() + " hooks"));

			this.tell(SimpleComponent
					.fromMiniNative(" - <gray>/" + this.getLabel() + " " + this.getSublabel() + " docs <white>- Where to find our documentation.")
					.onHoverLegacy("<gray>Click to run this command.")
					.onClickRunCmd("/" + this.getLabel() + " " + this.getSublabel() + " docs"));

			this.tell(SimpleComponent
					.fromMiniNative(" - <gray>/" + this.getLabel() + " " + this.getSublabel() + " support <white>- Where to get support.")
					.onHoverLegacy("<gray>Click to run this command.")
					.onClickRunCmd("/" + this.getLabel() + " " + this.getSublabel() + " support"));

			this.tell(SimpleComponent
					.fromMiniNative(" - <gray>/" + SimpleSettings.MAIN_COMMAND_ALIASES.get(0) + " perms <white>- Prints all permissions the plugin supports and those you have.")
					.onHoverLegacy("<gray>Click to run this command.")
					.onClickRunCmd("/" + SimpleSettings.MAIN_COMMAND_ALIASES.get(0) + " perms"));

			this.tell(SimpleComponent
					.fromMiniNative(" - <gray>/" + SimpleSettings.MAIN_COMMAND_ALIASES.get(0) + " ? <white>- Prints all main plugin commands.")
					.onHoverLegacy("<gray>Click to run this command.")
					.onClickRunCmd("/" + SimpleSettings.MAIN_COMMAND_ALIASES.get(0) + " ?"));

			this.tell(SimpleComponent
					.fromMiniNative(" - <gray>/" + Settings.Channels.COMMAND_ALIASES.get(0) + " ? <white>- Prints all channel commands.")
					.onHoverLegacy("<gray>Click to run this command.")
					.onClickRunCmd("/" + Settings.Channels.COMMAND_ALIASES.get(0) + " ?"));

			return;
		}

		else if ("start".equals(param)) {
			final List<String> pages = Arrays.asList(

					"&c> &7What?",
					" ",
					" ChatControl is the most advanced chat filtering and",
					" formatting plugin for Bukkit since 2013.",
					" ",
					"&c> &7Here's what you'll find inside ChatControl/ folder:",
					" ",
					" - &7books/ folder:&f Store books you can open for players",
					"  in many ways. Use '/" + SimpleSettings.MAIN_COMMAND_ALIASES.get(0) + " book' to manage.",
					" ",
					" - &7formats/ folder: &fStores the different ways your chat can",
					"  look. Each format is split into parts. Each part is shown",
					"  depending on many conditions for sender and each receiver.",
					" ",
					" - &7images/ folder: &fStores jpg or png images that are shown",
					"  in places such as '/chc announce'.",
					" ",
					" - &7messages/ folder: &fStores join, quit, death and timed",
					"  broadcast messages you can customize completely. See:",
					"  &o<click:open_url:'https://docs.mineacademy.org/chatcontrol/messages'>https://docs.mineacademy.org/chatcontrol/messages</click>",
					" ",
					" - &7rules/ folder: &fStores filters for game eventy such as",
					"  chat messages or commands. See:",
					"  &o<click:open_url:'https://docs.mineacademy.org/chatcontrol/rules'>https://docs.mineacademy.org/chatcontrol/rules</click>",
					" ",
					" - &7variables/ folder: &fStores JavaScript placeholders",
					"  such as {player_rank} or [item] to be used in chat.",
					" ",
					" - &7sqlite.db: &fStores player data if using SQLite.",
					" ",
					" - &7error.log: &fStores errors in the plugin, if any.",
					" ",
					" - &7settings.yml file: &fOur main configuration file. Do not",
					"  edit! Just kidding, you can edit it as you like :)",
					" ",
					"&c> &7Where to start?",
					" If you're a beginner, read the docs.mineacademy.org and",
					" begin by editing settings.yml. Please see the # comments",
					" as well as the docs articles for examples and help.",
					"",
					"&c> &7A notice on performance",
					" ChatControl has many programmable parts. By default, it",
					" outperforms most chat plugins, but the more stuff you",
					" add to it, to slower it becomes. See the Performance",
					" article in our docs on how to keep the plugin running fast.",
					" ",
					"&c> &7Links",
					" ",
					" - &7Docs: ",
					"   &f&o<click:open_url:'https://docs.mineacademy.org/chatcontrol'>https://docs.mineacademy.org/chatcontrol",
					" ",
					" - &7Issues: ",
					"   &f&o<click:open_url:'https://github.com/kangarko/chatcontrol/issues'>https://github.com/kangarko/chatcontrol/issues",
					"",
					" &6Thank you for reading. To get started using ChatControl",
					" &6type '/" + this.getLabel() + " " + this.getSublabel() + " confirm'.");

			new ChatPaginator(17)
					.setFoundationHeader("Welcome to ChatControl")
					.setPages(CommonCore.toArray(pages))
					.send(this.audience);
		}

		else if ("hooks".equals(param)) {

			class $ {

				SimpleComponent out(final boolean has, final String name) {
					return SimpleComponent.fromMiniNative(" - <gray>" + name + ": " + (has ? "<dark_green>Found" : "<red>Not found") + "<gray>.");
				}
			}

			final $ $ = new $();

			final List<SimpleComponent> pages = new ArrayList<>();

			pages.add(SimpleComponent.fromPlain(" Below you will find all plugins we support, as well"));
			pages.add(SimpleComponent.fromPlain(" as those that we found on your server and hooked into."));
			pages.add(SimpleComponent.empty());

			pages.add($.out(HookManager.isAuthMeLoaded(), "AuthMe"));
			pages.add($.out(HookManager.isBanManagerLoaded(), "BanManager"));
			pages.add($.out(HookManager.isBentoBoxLoaded(), "BentoBox"));
			pages.add($.out(HookManager.isBossLoaded(), "Boss"));
			pages.add($.out(HookManager.isCMILoaded(), "CMI"));
			pages.add($.out(HookManager.isDiscordSRVLoaded(), "DiscordSRV"));
			pages.add($.out(Platform.isPluginInstalled("dynmap"), "dynmap"));
			pages.add($.out(HookManager.isVaultLoaded(), "Vault"));
			pages.add($.out(HookManager.isEssentialsLoaded(), "Essentials"));
			pages.add($.out(HookManager.isFactionsLoaded(), "Factions, FactionsX or FactionsUUID"));
			pages.add($.out(HookManager.isLandsLoaded(), "Lands"));
			pages.add($.out(ThirdPartiesListener.isMcMMOLoaded(), "mcMMO"));
			pages.add($.out(HookManager.isMythicMobsLoaded(), "MythicMobs"));
			pages.add($.out(HookManager.isMultiverseCoreLoaded(), "Multiverse-Core"));
			pages.add($.out(HookManager.isPlaceholderAPILoaded(), "PlaceholderAPI"));
			pages.add($.out(HookManager.isPlotSquaredLoaded(), "PlotSquared"));
			pages.add($.out(HookManager.isProtocolLibLoaded(), "ProtocolLib"));
			pages.add($.out(HookManager.isTownyLoaded(), "Towny"));
			pages.add($.out(HookManager.isTownyChatLoaded(), "TownyChat"));

			pages.add(SimpleComponent.empty());
			pages.add(SimpleComponent.fromPlain(" For more information, please visit:"));
			pages.add(SimpleComponent.fromMiniNative(" <italic><click:open_url:'https://docs.mineacademy.org/chatcontrol/compatibility'>https://docs.mineacademy.org/chatcontrol/compatibility</click></italic>"));

			new ChatPaginator(17)
					.setFoundationHeader("Supported Plugins")
					.setPages(pages)
					.send(this.audience);
		}

		else if ("docs".equals(param))
			this.tellNoPrefix(
					"&8" + CommonCore.chatLineSmooth(),
					"<center>&cLearn About How ChatControl Works",
					" ",
					" For quick help, please see the comments in",
					" almost every file. For extended tutorials and",
					" solving frequent issues/questions, please see:",
					" ",
					" &ohttps://docs.mineacademy.org",
					" ");

		else if ("support".equals(param))
			this.tellNoPrefix(
					"&8" + CommonCore.chatLineSmooth(),
					"<center>&cWhere To Get Support?",
					" ",
					" We're a tiny team and give support in our spare time, all",
					" we ask you do is read the docs first. Not only you save days",
					" waiting for your reply but also free up our time into",
					" development. See &c/{label} {sublabel} docs &ffor more.",
					" ",
					" Having said that, if you have questions, a bug to report",
					" or anything else needing to be addressed, open an issue at:",
					" &ohttps://github.com/kangarko/chatcontrol/wiki/Issues",
					" ",
					" &7Please note for the time being we're not having the capacity",
					" &7to add more features. We're happy to listen, though.");

		else if ("confirm".equals(param)) {
			this.tellNoPrefix(
					"&8" + CommonCore.chatLineSmooth(),
					"<center>&cThank you for using ChatControl.",
					" ",
					" &c[!] &fBy continuing, you indicate that you have fully read",
					" and understood our &cTerms of Service &fand have completed",
					" our tour process. Otherwise you will get no support.",
					" ",
					" &c> &7Please read now:",
					" &f<click:open_url:'https://docs.mineacademy.org/chatcontrol/terms'>https://docs.mineacademy.org/chatcontrol/terms</click>",
					" ",
					" &c> &7If you need support, we're happy to help at this link:",
					" &f<click:open_url:'https://github.com/kangarko/chatcontrol/issues'>https://github.com/kangarko/chatcontrol/issues</click>",
					" ",
					" &6You may now use ChatControl. Happy using!");

			if (this.isPlayer())
				CompSound.ENTITY_ARROW_HIT_PLAYER.play(this.getPlayer());
		}

		else
			this.returnInvalidArgs(param);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		return this.args.length == 1 ? this.completeLastWord("start", "hooks", "docs", "support") : NO_COMPLETE;
	}
}
