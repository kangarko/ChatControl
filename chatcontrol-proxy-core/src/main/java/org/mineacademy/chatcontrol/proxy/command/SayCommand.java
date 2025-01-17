package org.mineacademy.chatcontrol.proxy.command;

import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.PlaceholderPrefix;
import org.mineacademy.chatcontrol.model.ToggleType;
import org.mineacademy.chatcontrol.proxy.settings.ProxySettings;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.command.SimpleCommandCore;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.platform.FoundationPlayer;
import org.mineacademy.fo.platform.Platform;

public final class SayCommand extends SimpleCommandCore {

	public SayCommand() {
		super(ProxySettings.Say.COMMAND_ALIASES);

		this.setPermission("chatcontrol.command.say");
	}

	@Override
	protected void onCommand() {
		String message = this.joinArgs(0);

		if (ProxySettings.MAKE_CHAT_LINKS_CLICKABLE && this.audience.hasPermission(Permissions.Chat.LINKS))
			message = ChatUtil.addMiniMessageUrlTags(message);

		for (final FoundationPlayer receiver : Platform.getOnlinePlayers()) {
			final SyncedCache cache = SyncedCache.fromUniqueId(receiver.getUniqueId());

			if (cache != null && !cache.hasToggledPartOff(ToggleType.BROADCAST))
				receiver.sendMessage(SimpleComponent.fromMiniAmpersand(Variables
						.builder(receiver)
						.placeholders(cache.getPlaceholders(PlaceholderPrefix.RECEIVER))
						.placeholder("message", message)
						.replaceLegacy(ProxySettings.Say.FORMAT)));
		}
	}
}
