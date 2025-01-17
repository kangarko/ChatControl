package org.mineacademy.chatcontrol.proxy.command;

import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.ToggleType;
import org.mineacademy.chatcontrol.proxy.settings.ProxySettings;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.command.SimpleCommandCore;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.platform.FoundationPlayer;
import org.mineacademy.fo.platform.Platform;

public final class MeCommand extends SimpleCommandCore {

	public MeCommand() {
		super(ProxySettings.Me.COMMAND_ALIASES);

		this.setPermission("chatcontrol.command.me");
	}

	@Override
	protected void onCommand() {
		String message = this.joinArgs(0);

		if (ProxySettings.MAKE_CHAT_LINKS_CLICKABLE && this.audience.hasPermission(Permissions.Chat.LINKS))
			message = ChatUtil.addMiniMessageUrlTags(message);

		final SimpleComponent format = SimpleComponent.fromMiniAmpersand(Variables.builder(this.audience).placeholder("message", message).replaceLegacy(ProxySettings.Me.FORMAT));

		for (final FoundationPlayer receiver : Platform.getOnlinePlayers()) {
			final SyncedCache cache = SyncedCache.fromUniqueId(receiver.getUniqueId());

			if (cache != null && !cache.hasToggledPartOff(ToggleType.BROADCAST))
				receiver.sendMessage(format);
		}
	}
}
