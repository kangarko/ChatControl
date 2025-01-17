package org.mineacademy.chatcontrol.proxy;

import java.util.List;
import java.util.UUID;

import org.mineacademy.fo.platform.FoundationPlayer;
import org.mineacademy.fo.settings.DataFileConfig;
import org.mineacademy.fo.settings.YamlConfig;

import lombok.Getter;

/**
 * The file storing various data information
 */
public final class ProxyServerCache {

	@Getter
	private static final ProxyServerCache instance = new ProxyServerCache();

	/**
	 * A list of players who got "caught" up by this plugin,
	 * used for first join messages.
	 */
	private List<UUID> registeredPlayers;

	/**
	 * Load the file
	 */
	private ProxyServerCache() {
		this.onLoad();
	}

	/**
	 * Load the values in the file
	 */
	private void onLoad() {
		final YamlConfig dataFile = DataFileConfig.getInstance();

		this.registeredPlayers = dataFile.getList("Players", UUID.class);
	}

	public void save() {
		final YamlConfig dataFile = DataFileConfig.getInstance();

		dataFile.save("Players", this.registeredPlayers);
	}

	/**
	 * Register the player as "played" on the server
	 *
	 * @param audience
	 */
	public void registerPlayer(final FoundationPlayer audience) {
		this.registerPlayer(audience.getUniqueId());
	}

	/**
	 * Register the player as "played" on the server
	 *
	 * @param uniqueId
	 */
	public void registerPlayer(final UUID uniqueId) {
		this.registeredPlayers.add(uniqueId);

		this.save();
	}

	/**
	 * Is the player registered yet?
	 *
	 * @param audience
	 * @return
	 */
	public boolean isPlayerRegistered(final FoundationPlayer audience) {
		return this.isPlayerRegistered(audience.getUniqueId());
	}

	/**
	 * Is the player registered yet?
	 *
	 * @param playerUniqueId
	 * @return
	 */
	public boolean isPlayerRegistered(final UUID playerUniqueId) {
		return this.registeredPlayers.contains(playerUniqueId);
	}
}
