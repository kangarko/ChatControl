package org.mineacademy.chatcontrol.bungee.hook;

import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.PlaceholderPrefix;
import org.mineacademy.chatcontrol.proxy.settings.ProxySettings;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.platform.FoundationPlayer;
import org.mineacademy.fo.platform.Platform;

import de.simonsator.partyandfriends.api.pafplayers.DisplayNameProvider;
import de.simonsator.partyandfriends.api.pafplayers.OnlinePAFPlayer;
import de.simonsator.partyandfriends.api.pafplayers.PAFPlayer;
import de.simonsator.partyandfriends.api.pafplayers.PAFPlayerClass;

public final class PartiesAndFriendsHook implements DisplayNameProvider {

	@Override
	public String getDisplayName(final OnlinePAFPlayer player) {
		return this.getDisplayName(player);
	}

	@Override
	public String getDisplayName(final PAFPlayer player) {
		final FoundationPlayer audience = Platform.getPlayer(player.getName());

		return Variables.builder(audience).placeholders(SyncedCache.getPlaceholders(audience, PlaceholderPrefix.PLAYER)).replaceLegacy(ProxySettings.BungeeIntegration.PARTIES_PLAYER_NAME);
	}

	public static void register() {
		final PartiesAndFriendsHook hook = new PartiesAndFriendsHook();

		PAFPlayerClass.setDisplayNameProvider(hook);
	}
}
