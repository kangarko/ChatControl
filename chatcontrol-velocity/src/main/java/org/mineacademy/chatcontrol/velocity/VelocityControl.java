package org.mineacademy.chatcontrol.velocity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.proxy.ChatControlProxyListenerProxy;
import org.mineacademy.chatcontrol.proxy.ProxyEvents;
import org.mineacademy.chatcontrol.proxy.Redis;
import org.mineacademy.chatcontrol.proxy.operator.ProxyPlayerMessages;
import org.mineacademy.chatcontrol.proxy.settings.ProxySettings;
import org.mineacademy.chatcontrol.velocity.listener.PlayerListener;
import org.mineacademy.chatcontrol.velocity.listener.RedisListener;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.command.ReloadCommand;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.platform.VelocityPlugin;
import org.mineacademy.fo.remain.Remain;
import org.slf4j.Logger;

import com.google.inject.Inject;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

/**
* The main plugin class.
*/
public final class VelocityControl extends VelocityPlugin {

	@Inject
	public VelocityControl(final ProxyServer proxyServer, final Logger logger, final @DataDirectory Path dataDirectory) {
		super(proxyServer, logger, dataDirectory);
	}

	@Override
	protected void onPluginLoad() {
		PlayerUtil.setIsVanished(player -> {
			final SyncedCache synced = SyncedCache.fromUniqueId(player.getUniqueId());

			return synced != null && synced.isVanished();
		});
	}

	@Override
	protected void onPluginStart() {
		if (Platform.isPluginInstalled("RedisBungee") && ProxySettings.REDIS_INTEGRATION) {
			Redis.setEnabled(true);

			this.registerEvents(new RedisListener());

			Remain.setServerGetter(() -> {
				final Collection<String> names = Redis.getServers();
				final List<RegisteredServer> servers = new ArrayList<>();

				for (final String name : names) {
					final RegisteredServer server = this.getProxy().getServer(name).orElse(null);

					if (server != null)
						servers.add(server);
				}

				// Fall back to Velocity's native server list when Redis returns no servers.
				// This prevents message forwarding from silently breaking when RedisBungee
				// temporarily cannot see online players (e.g. during Redis reconnection).
				if (servers.isEmpty()) {
					final List<RegisteredServer> fallback = new ArrayList<>(this.getProxy().getAllServers());

					Common.warning("Redis returned 0 servers from online players, falling back to " + fallback.size() + " Velocity-registered servers");

					return fallback;
				}

				return servers;
			});

			CommonCore.log("Hooked into: RedisBungee");
		}

		ProxyEvents.registerCommands();

		this.registerEvents(new PlayerListener());
		this.registerCommand(new ReloadCommand("vcreload", "chatcontrol.command.reload"));

		ChatControlProxyListenerProxy.getInstance().scheduleSyncTask();

		this.onPluginReload();
	}

	@Override
	protected void onPluginReload() {
		ProxyPlayerMessages.getInstance().load();
	}

	@Override
	public String getSentryDsn() {
		return "https://f3e0e6f4236a18360bf321211866ae6f@o4508048573661184.ingest.us.sentry.io/4508052468269056";
	}
}
