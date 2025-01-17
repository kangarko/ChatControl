package org.mineacademy.chatcontrol.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.PlaceholderPrefix;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.ListPlayers.SortBy;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.platform.FoundationPlayer;
import org.mineacademy.fo.settings.Lang;

public final class CommandList extends ChatControlCommand {

	public CommandList() {
		super(Settings.ListPlayers.COMMAND_ALIASES);

		this.setUsage(Settings.Proxy.ENABLED ? Lang.component("command-list-usage-server") : SimpleComponent.empty());
		this.setDescription(Lang.component("command-list-description"));
		this.setPermission(Permissions.Command.LIST);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		final List<SimpleComponent> usages = new ArrayList<>();

		usages.add(Lang.component("command-list-usage-1-" + (Settings.Proxy.ENABLED ? "network" : "local")));

		if (Settings.Proxy.ENABLED)
			usages.add(Lang.component("command-list-usage-2"));

		return SimpleComponent.join(usages);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		this.checkUsage(this.args.length <= 1);

		final List<SimpleComponent> pages = this.compilePlayers(this.audience);

		new ChatPaginator()
				.setFoundationHeader(Lang.legacy("command-list-header-" + (Settings.Proxy.ENABLED ? "network" : "local"), "pages", pages.size()))
				.setPages(pages)
				.send(this.audience);
	}

	private List<SimpleComponent> compilePlayers(final FoundationPlayer audience) {

		// Group all players by the sorting type
		final Map<String, List<SyncedCache>> grouppedPlayers = new TreeMap<>(String::compareTo);

		// Cache if sender sees vanished players for best performance
		final boolean senderSeeVanished = audience.hasPermission(Permissions.Bypass.VANISH);

		// Compile a list of caches and group them
		for (final SyncedCache syncedCache : SyncedCache.getCaches()) {

			if (syncedCache.isVanished() && !senderSeeVanished)
				continue;

			final SortBy sorter = Settings.ListPlayers.SORT_BY;
			String key;

			// Group by the given key
			if (sorter == SortBy.GROUP)
				key = syncedCache.getGroup();

			else if (sorter == SortBy.NAME)
				key = syncedCache.getPlayerName();

			else if (sorter == SortBy.NICK)
				key = syncedCache.getNameOrNickColorless();

			else if (sorter == SortBy.PREFIX)
				key = syncedCache.getPrefix();

			else
				throw new FoException("Sorting by " + Settings.ListPlayers.SORT_BY + " is unsupported for the list command!");

			// Add to our group
			if (key == null)
				key = "";

			final List<SyncedCache> caches = grouppedPlayers.getOrDefault(key, new ArrayList<>());
			caches.add(syncedCache);

			// Save to main group
			grouppedPlayers.put(key, caches);
		}

		final List<SimpleComponent> sortedPlayers = new ArrayList<>();
		int count = 1;

		for (final List<SyncedCache> caches : grouppedPlayers.values()) {
			Collections.sort(caches, Comparator.comparing(SyncedCache::getPlayerName));

			for (final SyncedCache cache : caches) {
				final Variables variables = Variables.builder().placeholders(cache.getPlaceholders(PlaceholderPrefix.PLAYER)).placeholder("count", count++);

				final String format = variables.replaceLegacy(Settings.ListPlayers.FORMAT);
				final List<String> formatHover = variables.replaceLegacyList(Settings.ListPlayers.FORMAT_HOVER);

				sortedPlayers.add(SimpleComponent.fromMiniAmpersand(format).onHoverLegacy(formatHover));
			}
		}

		return sortedPlayers;
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (this.args.length == 1)
			return Settings.Proxy.ENABLED ? this.completeLastWord(SyncedCache.getServers()) : NO_COMPLETE;

		return NO_COMPLETE;
	}
}
