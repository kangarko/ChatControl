package org.mineacademy.chatcontrol.bungee.listener;

import org.mineacademy.chatcontrol.model.PlayerMessageType;
import org.mineacademy.chatcontrol.proxy.ProxyEvents;
import org.mineacademy.chatcontrol.proxy.settings.ProxySettings;
import org.mineacademy.fo.platform.Platform;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.event.TabCompleteEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

public final class PlayerListener implements Listener {

	/**
	 * Notify downstream what server name alias we configured here
	 *
	 * @param event
	 */
	@EventHandler
	public void onJoin(final ServerConnectedEvent event) {
		ProxyEvents.handleConnect(Platform.toPlayer(event.getPlayer()), Platform.toServer(event.getServer()));
		ProxyEvents.handlePostConnect(Platform.toPlayer(event.getPlayer()), Platform.toServer(event.getServer()));
	}

	/**
	 * Handle server switch messages.
	 *
	 * @param event
	 */
	@EventHandler(priority = 64)
	public void onSwitch(final ServerSwitchEvent event) {
		ProxyEvents.handleSwitch(Platform.toPlayer(event.getPlayer()), Platform.toServer(event.getPlayer().getServer()));
	}

	/**
	 * Handle quit messages.
	 *
	 * @param event
	 */
	@EventHandler(priority = 64)
	public void onDisconnect(final PlayerDisconnectEvent event) {
		ProxyEvents.handleDisconnect(PlayerMessageType.QUIT, Platform.toPlayer(event.getPlayer()));
	}

	/**
	 * Handle kick messages.
	 *
	 * @param event
	 */
	@EventHandler(priority = 64)
	public void onKick(final ServerKickEvent event) {
		ProxyEvents.handleDisconnect(PlayerMessageType.KICK, Platform.toPlayer(event.getPlayer()));
	}

	/**
	 * Filter tab
	 *
	 * @param event
	 */
	@EventHandler(priority = EventPriority.HIGH)
	public void onTabComplete(final TabCompleteEvent event) {
		ProxyEvents.handleTabComplete(event.getCursor(), event.getSuggestions());
	}

	/**
	 * Forward chat messages
	 *
	 * @param event
	 */
	@EventHandler(priority = EventPriority.HIGH)
	public void onChatEvent(final ChatEvent event) {
		if (!ProxySettings.ChatForwarding.ENABLED || event.isCancelled() || !(event.getSender() instanceof ProxiedPlayer))
			return;

		ProxyEvents.handleChatForwarding(Platform.toPlayer(event.getSender()), event.getMessage());
	}
}
