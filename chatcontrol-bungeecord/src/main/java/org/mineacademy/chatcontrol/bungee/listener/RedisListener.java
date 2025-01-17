package org.mineacademy.chatcontrol.bungee.listener;

import org.mineacademy.chatcontrol.proxy.Redis;

import com.imaginarycode.minecraft.redisbungee.events.PubSubMessageEvent;

import net.md_5.bungee.event.EventHandler;

public final class RedisListener {

	/**
	 * Listen to plugin messages across network
	 *
	 * @param event
	 */
	@EventHandler
	public void onPubSubMessage(final PubSubMessageEvent event) {
		Redis.handlePubSubMessage(event);
	}
}
