package org.mineacademy.chatcontrol.velocity.listener;

import org.mineacademy.chatcontrol.proxy.Redis;

import com.imaginarycode.minecraft.redisbungee.events.PubSubMessageEvent;
import com.velocitypowered.api.event.Subscribe;

public final class RedisListener {

	/**
	 * Listen to plugin messages across network
	 *
	 * @param event
	 */
	@Subscribe
	public void onPubSubMessage(final PubSubMessageEvent event) {
		Redis.handlePubSubMessage(event);
	}
}
