package org.mineacademy.chatcontrol.velocity.listener;

import org.mineacademy.chatcontrol.model.PlayerMessageType;
import org.mineacademy.chatcontrol.proxy.ProxyEvents;
import org.mineacademy.chatcontrol.proxy.settings.ProxySettings;
import org.mineacademy.fo.platform.Platform;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent.ChatResult;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.TabCompleteEvent;

public final class PlayerListener {

	/**
	 * Notify downstream what server name alias we configured here
	 * and handle join message.
	 *
	 * @param event
	 */
	@Subscribe
	public void onJoin(final ServerConnectedEvent event) {
		ProxyEvents.handleConnect(Platform.toPlayer(event.getPlayer()), Platform.toServer(event.getServer()));
	}

	/**
	 * Handle server switch messages.
	 *
	 * @param event
	 */
	@Subscribe(order = PostOrder.LATE)
	public void onSwitch(final ServerConnectedEvent event) {
		ProxyEvents.handleSwitch(Platform.toPlayer(event.getPlayer()), Platform.toServer(event.getServer()));
	}

	/**
	 * Handle post connect.
	 *
	 * @param event
	 */
	@Subscribe
	public void onPostConnected(final ServerPostConnectEvent event) {
		ProxyEvents.handlePostConnect(Platform.toPlayer(event.getPlayer()), Platform.toServer(event.getPlayer().getCurrentServer().get()));
	}

	/**
	 * Handle quit messages.
	 *
	 * @param event
	 */
	@Subscribe(order = PostOrder.LATE)
	public void onDisconnect(final DisconnectEvent event) {
		ProxyEvents.handleDisconnect(PlayerMessageType.QUIT, Platform.toPlayer(event.getPlayer()));
	}

	/**
	 * Handle kick messages.
	 *
	 * @param event
	 */
	@Subscribe(order = PostOrder.LATE)
	public void onKick(final KickedFromServerEvent event) {
		ProxyEvents.handleDisconnect(PlayerMessageType.KICK, Platform.toPlayer(event.getPlayer()));
	}

	/**
	 * Filter tab
	 *
	 * @param event
	 */
	@Subscribe(order = PostOrder.LATE)
	public void onTabComplete(final TabCompleteEvent event) {
		ProxyEvents.handleTabComplete(event.getPartialMessage().trim(), event.getSuggestions());
	}

	/**
	 * Forward chat messages
	 *
	 * @param event
	 */
	@Subscribe(order = PostOrder.LATE)
	public void onChatEvent(final PlayerChatEvent event) {
		if (!ProxySettings.ChatForwarding.ENABLED || event.getResult() == ChatResult.denied())
			return;

		ProxyEvents.handleChatForwarding(Platform.toPlayer(event.getPlayer()), event.getMessage());
	}
}
