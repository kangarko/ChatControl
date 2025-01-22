package org.mineacademy.chatcontrol.listener.chat;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.mineacademy.chatcontrol.model.Colors;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.platform.BukkitPlugin;

import io.papermc.paper.event.player.AsyncChatEvent;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * The over the top complicated adventure powered chat because of high developer arrogance.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdventureChatListener implements EventExecutor, Listener {

	private static AdventureChatListener instance = new AdventureChatListener();

	@Override
	public void execute(final Listener listener, final Event event) throws EventException {
		//final long nano = System.nanoTime();
		final AsyncChatEvent chatEvent = (AsyncChatEvent) event;
		final Set<Player> bukkitRecipients = new HashSet<>();

		for (final Audience viewer : chatEvent.viewers())
			if (viewer instanceof Player)
				bukkitRecipients.add((Player) viewer);

		// Ignore, other plugin might have removed recipients on purpose
		if (bukkitRecipients.isEmpty())
			return;

		String miniMessage = SimpleComponent.MINIMESSAGE_PARSER.serialize(chatEvent.message());

		// There is no way for us to differentiate player tags and tags from plugins editing this event
		// before us, so we merge them and remove colors players dont have permissions for
		if (Settings.Colors.APPLY_ON.contains(Colors.Type.CHAT))
			miniMessage = miniMessage.replace("\\\\<", "\\<").replace("\\<", "<");

		final ChatHandler.State state = new ChatHandler.State(chatEvent.getPlayer(), bukkitRecipients, miniMessage, chatEvent.isCancelled());

		ChatHandler.handle(state);

		final Component chatComponent = SimpleComponent.MINIMESSAGE_PARSER.deserialize(state.getChatMessage());

		if (state.isMessageChanged())
			chatEvent.message(chatComponent);

		chatEvent.setCancelled(state.isCancelled());
		chatEvent.viewers().removeIf(viewer -> viewer instanceof ConsoleCommandSender || (viewer instanceof Player && !bukkitRecipients.contains(viewer)));
		chatEvent.viewers().add(new ChatControlConsoleSender(state));

		// Fix console not broadcasting if event is cancelled
		if (state.isCancelled())
			logToConsole(state, chatComponent);

		//LagCatcher.took(nano, "modern chat");
	}

	public static void register() {
		try {
			Bukkit.getPluginManager().registerEvent(AsyncChatEvent.class, instance, Settings.CHAT_LISTENER_PRIORITY.getKey(), instance, BukkitPlugin.getInstance(), true);

		} catch (final IllegalPluginAccessException ex) {
			if (ex.getMessage().startsWith("Plugin attempted to register") && ex.getMessage().endsWith("while not enabled")) {
				// ignore
			} else
				Common.error(ex, "Error registering " + instance.getClass().getSimpleName());
		}
	}

	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	private static class ChatControlConsoleSender implements Audience {

		private final ChatHandler.State state;

		@Override
		public void sendMessage(final Identity source, final Component message, final MessageType type) {
			logToConsole(this.state, message);
		}
	}

	private static void logToConsole(ChatHandler.State state, Component message) {
		final String console = state.getConsoleFormat();

		if (console != null) {
			final SimpleComponent consoleComponent = SimpleComponent.fromMiniAmpersand(console);

			if (!consoleComponent.toPlain().trim().isEmpty() && !"none".equals(console))
				Bukkit.getConsoleSender().sendMessage(consoleComponent.toLegacySection());

		} else
			Bukkit.getConsoleSender().sendMessage("<" + state.getPlayer().getName() + "> " + PlainTextComponentSerializer.plainText().serialize(message));
	}
}
