package org.mineacademy.chatcontrol.listener.chat;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.platform.BukkitPlugin;
import org.mineacademy.fo.platform.Platform;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * A simple chat event.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LegacyChatListener implements EventExecutor, Listener {

	private static LegacyChatListener instance = new LegacyChatListener();

	@Override
	public void execute(final Listener listener, final Event event) throws EventException {
		//final long nano = System.nanoTime();
		final AsyncPlayerChatEvent chatEvent = (AsyncPlayerChatEvent) event;

		// Ignore, other plugin might have removed recipients on purpose
		if (chatEvent.getRecipients().isEmpty())
			return;

		final ChatHandler.State state = new ChatHandler.State(chatEvent.getPlayer(), chatEvent.getRecipients(), chatEvent.getMessage(), chatEvent.isCancelled());

		ChatHandler.handle(state);

		// Note that viewers are synced automatically
		chatEvent.setCancelled(state.isCancelled());

		if (state.isMessageChanged())
			chatEvent.setMessage(SimpleComponent.fromMiniSection(state.getChatMessage()).toLegacySection());

		final String console = state.getConsoleFormat();

		if (console != null) {
			final SimpleComponent consoleComponent = SimpleComponent.fromMiniAmpersand(console);
			final String consoleMessage = consoleComponent.toLegacySection().replace("%", "%%");

			chatEvent.setFormat(consoleMessage);

			if (consoleComponent.toPlain().trim().isEmpty() || "none".equals(console))
				chatEvent.setCancelled(true);

			else {
				if (chatEvent.isCancelled())
					Platform.log(consoleMessage);
			}
		}

		//LagCatcher.took(nano, "legacy chat event");
	}

	public static void register() {
		try {
			Bukkit.getPluginManager().registerEvent(AsyncPlayerChatEvent.class, instance, Settings.CHAT_LISTENER_PRIORITY.getKey(), instance, BukkitPlugin.getInstance(), true);

		} catch (final IllegalPluginAccessException ex) {
			if (ex.getMessage().startsWith("Plugin attempted to register") && ex.getMessage().endsWith("while not enabled")) {
				// ignore
			} else
				Common.error(ex, "Error registering " + instance.getClass().getSimpleName());
		}
	}
}
