package org.mineacademy.chatcontrol.model.db;

import java.sql.SQLException;

import javax.annotation.Nullable;

import org.mineacademy.chatcontrol.api.MuteEvent;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.database.Row;
import org.mineacademy.fo.database.SimpleResultSet;
import org.mineacademy.fo.database.Table;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.platform.Platform;

import lombok.NonNull;

/**
 * Represents data file portion used for server-wide information
 */
public final class ServerSettings extends Row {

	/**
	 * The cached instance of this class.
	 */
	private static ServerSettings instance;

	/**
	 * The cached proxy instance of this class.
	 */
	private static ServerSettings proxy;

	/**
	 * The name of the server where this data is stored.
	 */
	private final String server;

	/**
	 * If the server is muted, this is the unmute time in the future
	 * where it will no longer be muted.
	 */
	private Long unmuteTime;

	ServerSettings(final SimpleResultSet resultSet) throws SQLException {
		this.server = resultSet.getString("Server");
		this.unmuteTime = resultSet.getLong("Unmute_Time");
	}

	ServerSettings() {
		this.server = Platform.getCustomServerName();
	}

	ServerSettings(String server) {
		this.server = server;
	}

	@Override
	public SerializedMap toMap() {
		return SerializedMap.fromArray(
				"Server", this.server,
				"Unmute_Time", this.unmuteTime);
	}

	@Override
	public Table getTable() {
		return ChatControlTable.SETTINGS;
	}

	@Override
	public Tuple<String, Object> getUniqueColumn() {
		return new Tuple<>("Server", this.server);
	}

	/* ------------------------------------------------------------------------------- */
	/* Getters and setters */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Return true if the player is muted
	 *
	 * @return
	 */
	public boolean isMuted() {
		return this.unmuteTime != null && this.unmuteTime > System.currentTimeMillis();
	}

	/**
	 * Return the time left until the server is unmuted
	 *
	 * @return
	 */
	public long getUnmuteTimeRemaining() {
		return this.unmuteTime != null && this.unmuteTime > System.currentTimeMillis() ? this.unmuteTime - System.currentTimeMillis() : 0;
	}

	/**
	 * Set the mute for this player
	 *
	 * @param duration how long, null to unmute
	 */
	public void setMuted(@Nullable SimpleTime duration) {
		final MuteEvent event = MuteEvent.server(duration);

		if (Platform.callEvent(event)) {
			duration = event.getDuration();
			this.unmuteTime = duration == null ? null : System.currentTimeMillis() + duration.getTimeSeconds() * 1000;

			this.upsert();
		}
	}

	/**
	 * Set the mute for this player
	 *
	 * @deprecated internal use only
	 * @param timestamp when to unmute
	 */
	@Deprecated
	public void setUnmuteTimeNoSave(final long timestamp) {
		this.unmuteTime = timestamp;
	}

	/**
	 * Set the server settings instance.
	 *
	 * @param settings
	 */
	public static void setInstance(@NonNull final ServerSettings settings) {
		ServerSettings.instance = settings;
	}

	/**
	 * Get the server settings instance.
	 *
	 * @return
	 */
	public static ServerSettings getInstance() {
		ValidCore.checkNotNull(instance, "ServerSettings have not been loaded yet! Use Database class first to load it.");

		return instance;
	}

	/**
	 * Set the server settings instance.
	 *
	 * @param settings
	 */
	public static void setProxy(@NonNull final ServerSettings settings) {
		ServerSettings.proxy = settings;
	}

	/**
	 * Get the server settings instance.
	 *
	 * @return
	 */
	public static ServerSettings getProxy() {
		ValidCore.checkNotNull(proxy, "ServerSettings have not been loaded yet! Use Database class first to load it.");

		return proxy;
	}

	/**
	 * Return true if the proxy settings are loaded
	 *
	 * @return
	 */
	public static boolean isProxyLoaded() {
		return proxy != null;
	}

	/**
	 * Get the proxy settings instance or force sets the instance if the proxy is not loaded
	 *
	 * @deprecated internal use only
	 * @return
	 */
	@Deprecated
	public static ServerSettings getProxyOrOverload() {
		if (proxy == null)
			proxy = new ServerSettings("proxy");

		return proxy;
	}
}
