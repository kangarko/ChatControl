package org.mineacademy.chatcontrol.bungee;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.mineacademy.chatcontrol.bungee.hook.PartiesAndFriendsHook;
import org.mineacademy.chatcontrol.bungee.listener.PlayerListener;
import org.mineacademy.chatcontrol.bungee.listener.RedisListener;
import org.mineacademy.chatcontrol.proxy.ChatControlProxyListenerProxy;
import org.mineacademy.chatcontrol.proxy.ProxyEvents;
import org.mineacademy.chatcontrol.proxy.ProxyServerCache;
import org.mineacademy.chatcontrol.proxy.Redis;
import org.mineacademy.chatcontrol.proxy.operator.ProxyPlayerMessages;
import org.mineacademy.chatcontrol.proxy.operator.ProxyRules;
import org.mineacademy.chatcontrol.proxy.settings.ProxySettings;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.command.ReloadCommand;
import org.mineacademy.fo.platform.BungeePlugin;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.remain.Remain;

import lombok.Getter;
import net.md_5.bungee.api.config.ServerInfo;

/**
* The main BungeeControl Red plugin class.
*/
public final class BungeeControl extends BungeePlugin {

	/**
	 * Is the Parties plugin installed?
	 */
	@Getter
	private static boolean partiesFound = false;

	@Override
	protected void onPluginLoad() {
		final File oldFile = new File(this.getDataFolder().getParentFile(), "BungeeControl-Red");

		if (oldFile.exists()) {
			oldFile.renameTo(this.getDataFolder());

			CommonCore.log("Migrated BungeeControl-Red folder to " + this.getDataFolder().getName() + ", you can delete the old folder.");
		}
	}

	@Override
	public void onPluginStart() {
		ProxyServerCache.getInstance();

		if (Platform.isPluginInstalled("RedisBungee")) {
			Redis.setEnabled(true);

			this.registerEvents(new RedisListener());

			Remain.setServerGetter(() -> {
				final Collection<String> names = Redis.getServers();
				final List<ServerInfo> servers = new ArrayList<>();

				for (final String name : names) {
					final ServerInfo server = this.getProxy().getServerInfo(name);

					if (server != null)
						servers.add(server);
				}

				return servers;
			});

			CommonCore.log("&fHooked into: &3RedisBungee");
		}

		if (ProxySettings.BungeeIntegration.PARTIES_ENABLED) {
			if (Platform.isPluginInstalled("Parties")) {
				partiesFound = true;

				CommonCore.log("&fHooked into: &3Parties");
			}

			if (Platform.isPluginInstalled("PartyAndFriends"))
				PartiesAndFriendsHook.register();
		}

		ProxyEvents.registerCommands();

		this.registerEvents(new PlayerListener());
		this.registerCommand(new ReloadCommand("bcreload", "chatcontrol.command.reload"));

		ChatControlProxyListenerProxy.getInstance().scheduleSyncTask();

		this.onPluginReload();
	}

	@Override
	protected void onPluginReload() {
		ProxyPlayerMessages.getInstance().load();
		ProxyRules.getInstance().load();
	}

	@Override
	public int getFoundedYear() {
		return 2015;
	}

	@Override
	public String getSentryDsn() {
		return "https://f3e0e6f4236a18360bf321211866ae6f@o4508048573661184.ingest.us.sentry.io/4508052468269056";
	}
}
