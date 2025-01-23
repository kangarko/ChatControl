package org.mineacademy.chatcontrol.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.util.LogUtil;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.ProxyUtil;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.ChatImage;
import org.mineacademy.fo.model.CompChatColor;
import org.mineacademy.fo.model.CompToastStyle;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.platform.FoundationPlayer;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.Lang;

import lombok.Getter;
import net.kyori.adventure.bossbar.BossBar;

/**
 * Handles announcements
 */
public final class Announce {

	/**
	 * Process an announcement message from proxy
	 *
	 * @param type
	 * @param message
	 * @param params
	 */
	public static void sendFromProxy(final AnnounceType type, final String message, final SerializedMap params) {
		send(null, type, message, params);
	}

	/**
	 * Send announcement message from the local sender
	 *
	 * @param sender
	 * @param type
	 * @param message
	 * @param params
	 */
	public static void send(@Nullable final CommandSender sender, final AnnounceType type, final String message, final SerializedMap params) {
		Consumer<FoundationPlayer> function = null;
		boolean broadcastOnThisServer = true;
		boolean stayOnThisServer = false;
		final String serverToBroadcastOn = params.getString("server");

		if (serverToBroadcastOn != null) {
			if (!Settings.Proxy.ENABLED) {
				Messenger.error(sender, Lang.component("command-no-proxy"));

				return;
			}

			if (!SyncedCache.doesServerExist(serverToBroadcastOn)) {
				Messenger.error(sender, Lang.component("command-invalid-server", "server", serverToBroadcastOn, "available", String.join(", ", SyncedCache.getServers())));

				return;
			}

			if (!serverToBroadcastOn.equals(Platform.getCustomServerName()))
				broadcastOnThisServer = false;
			else
				stayOnThisServer = true;
		}

		if (type == AnnounceType.CHAT) {
			if (message.toLowerCase().startsWith("[json]"))
				function = player -> {
					player.sendMessage(SimpleComponent.fromAdventureJson(message.substring(6).trim(), MinecraftVersion.olderThan(V.v1_16)));
				};

			else if ("raw".equals(params.getString("type"))) {
				function = player -> {
					for (final String line : message.split("\\|"))
						player.sendMessage(SimpleComponent.fromMiniAmpersand(Variables.builder(player).replaceLegacy(line)));
				};

			} else {
				String[] lines = message.split("\\|");

				// Try load it up from a file
				if (lines.length == 1 && lines[0].endsWith(".txt")) {
					final File file = FileUtil.getFile(message);

					if (!file.exists()) {
						if (sender != null)
							Messenger.error(sender, Lang.component("command-announce-invalid-file", "file", file.getPath()));

						return;
					}

					lines = FileUtil.readLinesFromFile(file).stream().toArray(String[]::new);
				}

				// Add body variable
				String linesVariables = "";

				for (final String line : lines)
					linesVariables += "" + CompChatColor.fromString(Lang.plain("command-announce-chat-body-line-color")) + line + "\n";

				// Add space variables
				String spaceVariables = "";

				for (int i = lines.length; i < 6; i++)
					spaceVariables += "&r \n";

				// Compile the  message to announce
				final String fullMessage = Lang.legacy("command-announce-chat-body",
						"type", Lang.plain("command-announce-type" + (Settings.Proxy.ENABLED ? "-network" : "")),
						"lines", linesVariables,
						"remaining_space", spaceVariables);

				// Broadcast
				function = player -> {
					for (final String line : fullMessage.split("\n"))
						CommonCore.tell(player, Variables.builder(player).replaceLegacy(line));

					Settings.Announcer.CHAT_SOUND.play(player);
				};
			}

			if (!stayOnThisServer && sender != null && Settings.Proxy.ENABLED)
				ProxyUtil.sendPluginMessage(ChatControlProxyMessage.ANNOUNCEMENT, type.getKey(), message, params);
		}

		else if (type == AnnounceType.IMAGE) {
			ValidCore.checkBoolean(params.containsKey("imageFile") || params.containsKey("imageLines"),
					"Announcement type IMAGE lacked both image file and image lines! Got: " + params);

			final File imageFile = params.containsKey("imageFile") ? FileUtil.getFile("images/" + params.getString("imageFile")) : null;
			final String[] imageLines = params.containsKey("imageLines") ? CommonCore.toArray((List<String>) params.getObject("imageLines")) : null;
			final int height = params.getInteger("height");

			try {
				final ChatImage image = ChatImage.builder();
				String[] lines = null;

				// Load the image. When sending from proxy we send the lines right away
				// because the image file may not exist on another server
				if (imageFile != null)
					lines = image.height(height).fillerCharacter(ChatImage.FillerCharacter.DARK_SHADE).drawFromFile(imageFile).toString(message.split("\\|"));

				else
					lines = imageLines;

				// Send to all receivers
				final String[] finalLines = lines;

				function = player -> {
					for (final String line : finalLines)
						player.sendMessage(SimpleComponent.fromMiniAmpersand(Variables.builder(player).replaceLegacy(line)));
				};

				if (!stayOnThisServer && sender != null && imageFile != null && Settings.Proxy.ENABLED)
					ProxyUtil.sendPluginMessage(ChatControlProxyMessage.ANNOUNCEMENT, type.getKey(), message, SerializedMap.fromArray("height", height, "imageLines", Arrays.asList(lines)));

			} catch (final Exception ex) {
				ex.printStackTrace();

				Messenger.error(sender, Lang.component("command-announce-image-error",
						"file", imageFile.toPath().toString(),
						"error", ex.toString()));
				return;
			}
		}

		else if (type == AnnounceType.TITLE) {
			final String[] parts = message.split("\\|");
			final String title = parts[0];
			final String subtitle = parts.length > 1 ? CommonCore.joinRange(1, parts) : null;

			final int stay = params.getInteger("stay", 2 * 20);
			final int fadeIn = params.getInteger("fadein", 20);
			final int fadeOut = params.getInteger("fadeout", 20);

			function = player -> player.showTitle(fadeIn, stay, fadeOut, Variables.builder(player).replaceLegacy(title), subtitle == null || subtitle.isEmpty() || "null".equals(subtitle) ? null : Variables.builder(player).replaceLegacy(subtitle));

			if (!stayOnThisServer && sender != null && Settings.Proxy.ENABLED)
				ProxyUtil.sendPluginMessage(ChatControlProxyMessage.ANNOUNCEMENT, type.getKey(), title + "|" + subtitle, SerializedMap.fromArray("stay", stay, "fadein", fadeIn, "fadeout", fadeOut));
		}

		else if (type == AnnounceType.ACTIONBAR) {
			function = player -> player.sendActionBar(Variables.builder(player).replaceLegacy(message));

			if (!stayOnThisServer && sender != null && Settings.Proxy.ENABLED)
				ProxyUtil.sendPluginMessage(ChatControlProxyMessage.ANNOUNCEMENT, type.getKey(), message, new SerializedMap());

		} else if (type == AnnounceType.BOSSBAR) {
			final int time = params.getInteger("time", 5);
			final BossBar.Color color = params.get("color", BossBar.Color.class, BossBar.Color.WHITE);
			final BossBar.Overlay overlay = params.get("overlay", BossBar.Overlay.class, BossBar.Overlay.PROGRESS);

			function = player -> player.showBossbarTimed(Variables.builder(player).replaceLegacy(message), time, 1F, color, overlay);

			if (!stayOnThisServer && sender != null && Settings.Proxy.ENABLED)
				ProxyUtil.sendPluginMessage(ChatControlProxyMessage.ANNOUNCEMENT, type.getKey(), message, SerializedMap.fromArray("time", time, "color", color, "style", overlay));
		}

		else if (type == AnnounceType.TOAST) {
			final CompMaterial icon = params.get("icon", CompMaterial.class, CompMaterial.BOOK);
			final CompToastStyle style = params.get("style", CompToastStyle.class, CompToastStyle.GOAL);

			function = player -> {
				throw new RuntimeException("Unhandled Toast announcement");
			};

			if (!stayOnThisServer && sender != null && Settings.Proxy.ENABLED)
				ProxyUtil.sendPluginMessage(ChatControlProxyMessage.ANNOUNCEMENT, type.getKey(), message, SerializedMap.fromArray("icon", icon, "style", style));
		}

		if (function == null)
			throw new FoException("Announcing '" + type + "' not implemented!");

		else if (sender != null && !(sender instanceof Player))
			Messenger.success(sender, Lang.component("command-announce-success"));

		// Iterate and broadcast to players who enabled it
		if (broadcastOnThisServer) {
			final List<FoundationPlayer> receivers = new ArrayList<>();

			for (final Player online : Players.getOnlinePlayersWithLoadedDb()) {
				final PlayerCache cache = PlayerCache.fromCached(online);

				if (Settings.Toggle.APPLY_ON.contains(ToggleType.ANNOUNCEMENT) && cache.hasToggledPartOff(ToggleType.ANNOUNCEMENT)) {
					LogUtil.logOnce("timed-no-perm", "Not showing timed message broadcast to " + online + " because he has toggled announcements off with /chc toggle.");

					continue;
				}

				if (!online.hasPermission(Permissions.Receive.ANNOUNCER)) {
					LogUtil.logOnce("timed-no-perm", "Not showing timed message broadcast to " + online + " because he lacks " + Permissions.Receive.ANNOUNCER + " permission.");

					continue;
				}

				receivers.add(Platform.toPlayer(online));
			}

			// Special case for Toasts
			if (type == AnnounceType.TOAST) {
				final CompMaterial icon = params.get("icon", CompMaterial.class, CompMaterial.BOOK);
				final CompToastStyle style = params.get("style", CompToastStyle.class, CompToastStyle.GOAL);

				if (!CompMaterial.isAir(icon))
					Remain.sendToastToAudience(receivers, receiver -> Variables.builder(receiver).replaceLegacy(message), icon, style);
				else
					for (final FoundationPlayer receiver : receivers)
						receiver.sendMessage(SimpleComponent.fromMiniAmpersand(Variables.builder(receiver).replaceLegacy(message)));

			} else
				for (final FoundationPlayer receiver : receivers)
					function.accept(receiver);

		} else
			Messenger.info(sender, Lang.component("command-announce-success-network", "server", serverToBroadcastOn));
	}

	/**
	 * For convenience sake, this models the possible parameters this command can have.
	 */
	@Getter
	public enum AnnounceType {

		/**
		 * Broadcast a simple chat message
		 */
		CHAT(V.v1_3_AND_BELOW, "chat", "c"),

		/**
		 * Broadcast an image!
		 */
		IMAGE(V.v1_3_AND_BELOW, "image", "img"),

		/**
		 * Broadcast a title
		 */
		TITLE(V.v1_7, "title", "t"),

		/**
		 * Broadcast action bar
		 */
		ACTIONBAR(V.v1_8, "actionbar", "ab"),

		/**
		 * Broadcast boss bar
		 */
		BOSSBAR(V.v1_8, "bossbar", "bb"),

		/**
		 * Broadcast a toast message
		 */
		TOAST(V.v1_12, "toast", "tt"),;

		/**
		 * The minimum required MC version
		 */
		private final V minimumVersion;

		/**
		 * All possible labels to use
		 */
		private final List<String> labels;

		/*
		 * Create a new instance with vararg array -- cannot use lombok for that
		 */
		AnnounceType(final V minimumVersion, final String... labels) {
			this.minimumVersion = minimumVersion;
			this.labels = Arrays.asList(labels);
		}

		/**
		 * Get the param label
		 *
		 * @return the label
		 */
		public String getKey() {
			return this.labels.get(0);
		}

		/**
		 * @see java.lang.Enum#toString()
		 */
		@Override
		public String toString() {
			return this.getKey();
		}

		/**
		 * Return true if the given param is compatible with the current MC version
		 *
		 * @return
		 */
		public boolean isCompatible() {
			return MinecraftVersion.atLeast(this.getMinimumVersion());
		}

		/**
		 * Return a list of all available params for the current MC version
		 *
		 * @return
		 */
		public static List<AnnounceType> getAvailableParams() {
			return Arrays.asList(values()).stream().filter(AnnounceType::isCompatible).collect(Collectors.toList());
		}
	}
}
