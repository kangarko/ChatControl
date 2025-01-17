package org.mineacademy.chatcontrol.model;

import java.util.UUID;

import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.proxy.ProxyMessage;

import lombok.Getter;

/**
 * Represents proxy plugin message.
 */
public enum ChatControlProxyMessage implements ProxyMessage {

	/**
	 * Send announcement message
	 */
	ANNOUNCEMENT(String.class /* type */, String.class /* message */, SerializedMap.class /* params */ ),

	/**
	 * Send a plain message to all fools
	 */
	BROADCAST(SimpleComponent.class /* message */),

	/**
	 * Broadcast a message in a channel.
	 */
	CHANNEL(String.class /* channel */, String.class /* sender name */, UUID.class /* sender uuid */, SimpleComponent.class /* formatted message */, String.class /* console format */, Boolean.class /* mute bypass */, Boolean.class /* ignore bypass */, Boolean.class /* log bypass */ ),

	/**
	 * Clears the game chat
	 */
	CLEAR_CHAT(SimpleComponent.class /* broadcast message */, Boolean.class /* forced */),

	/**
	 * Used to display join/switch messages.
	 */
	DATABASE_READY(String.class /* player name */, UUID.class /* player uuid */, SerializedMap.class /* SyncedCache. Data for all SyncType, key is synced type and value is its value */),

	/**
	 * Indicates MySQL has changed for player and we need pulling it again
	 */
	DATABASE_UPDATE(String.class /* origin server */, UUID.class /* player UUID */, SerializedMap.class /* data */, SimpleComponent.class /* optional message */ ) {
		@Override
		public boolean includeSelfServer() {
			return true;
		}
	},

	/**
	 * Indicates tag has changed for player and we need to update it
	 */
	TAG_UPDATE(String.class /* origin server */, UUID.class /* player UUID */, SerializedMap.class /* player cache data to reload */, SimpleComponent.class /* message */),

	/**
	 * Forward commands to proxy or other server
	 */
	FORWARD_COMMAND(String.class /* server */, String.class /* command */),

	/**
	 * Forward a player format
	 */
	FORWARD_FORMAT(String.class /* target player */, Boolean.class /* reload */, String.class /* format */, String.class /* message */),

	/**
	 * Broadcast the /me command
	 */
	ME(UUID.class /* sender uuid */, Boolean.class /* reach bypass */, SimpleComponent.class /* message */),

	/**
	 * Send a plain message to the given receiver
	 */
	MESSAGE(UUID.class /* receiver */, SimpleComponent.class /* message */),

	/**
	 * Send motd to the given receiver
	 */
	MOTD(UUID.class /* receiver uuid */ ),

	/**
	 * Update mute status
	 */
	MUTE(String.class /* type */, String.class /* player, channel or server name */, String.class /* duration */, SimpleComponent.class /* announce message */),

	/**
	 * Rules notify handling
	 */
	NOTIFY(String.class /* permission */, SimpleComponent.class /* message */ ),

	/**
	 * Remove the given message from the players screen if he has received it.
	 */
	REMOVE_MESSAGE(String.class /* remove mode */, UUID.class /* message id */),

	/**
	 * Indicates that the player should have his reply player
	 * updated
	 */
	REPLY_UPDATE(UUID.class /* player to update uuid */, String.class /* reply player name */, UUID.class /* reply player uuid */ ),

	/**
	 * Broadcast server alias set on proxy downstream
	 */
	SERVER_ALIAS(String.class /* server name */, String.class /* server alias */),

	/**
	 * Send a sound to a player
	 */
	SOUND(UUID.class /* receiver UUID */, String.class /* SimpleSound#serialize() */),

	/**
	 * Broadcast message to spying players
	 */
	SPY_UUID(String.class /* spy type */, UUID.class /* sender uuid */, String.class /* channel name */ , Boolean.class /* proxy mode */, SimpleComponent.class /* message */, SimpleComponent.class /* format */, String.class /* json UUID list of of players we should ignore */, Boolean.class /* was denied silently? */ ),

	/**
	 * Sync of data between servers using proxy
	 */
	SYNCED_CACHE_BY_UUID(String.class, /* sync type */ SerializedMap.class /* key = player uuid, value = sync type value */ ),

	/**
	 * Sync of data between proxy servers (name-uuid map)
	 */
	SYNCED_CACHE_HEADER(SerializedMap.class /* name-uuid map */),

	/**
	 * Send a toast message
	 */
	TOAST(UUID.class /* receiver UUID */, String.class /* toggle type */, String.class /* message */, String.class, /* CompMaterial */ String.class /* CompToastStyle */ );

	/**
	 * Stores all valid values, the names of them are only used
	 * in the error message when the length of data does not match
	 */
	@Getter
	private final Class<?>[] content;

	/**
	 * Constructs a new proxy action
	 *
	 * @param validValues
	 */
	ChatControlProxyMessage(final Class<?>... validValues) {
		this.content = validValues;
	}

	/**
	 * Send the message to the server that the sending player is on?
	 *
	 * @return
	 */
	public boolean includeSelfServer() {
		return false;
	}
}