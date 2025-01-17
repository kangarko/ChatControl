package org.mineacademy.chatcontrol.bungee.hook;

import java.util.UUID;

import javax.annotation.Nullable;

import com.alessiodp.parties.api.Parties;
import com.alessiodp.parties.api.interfaces.PartiesAPI;
import com.alessiodp.parties.api.interfaces.PartyPlayer;

import net.md_5.bungee.api.plugin.Listener;

/**
 * Provides integration with the Parties plugin
 */
public final class PartiesHook implements Listener {

	/**
	 * Set the nickname inside Parties plugin from the given nick (coming from ChatControl)
	 *
	 * @param uuid
	 * @param nick
	 */
	public static void setNickName(final UUID uuid, @Nullable final String nick) {
		final PartiesAPI api = Parties.getApi();
		final PartyPlayer player = api.getPartyPlayer(uuid);

		if (player != null) {
			final String oldNick = player.getNickname();

			// Only set the nickname if the user has updated it on ChatControl
			if ((oldNick != null && !oldNick.equals(nick)) || (nick != null && !nick.equals(oldNick)))
				player.setNickname(nick);
		}
	}
}
