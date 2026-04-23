package org.mineacademy.chatcontrol.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.model.CompChatColor;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.platform.Platform;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Class holding color-related utilities
 */
public final class Colors {

	/**
	 * If the interactive chat plugin is enabled
	 */
	private static Boolean isInteractiveChatEnabled = null;

	/**
	 * Stores the permissions for each decoration tag.
	 */
	public static final Map<String, String> DECORATION_PERMISSIONS = Common.newHashMap(
			"b", "bold",
			"bold", "bold",
			"i", "italic",
			"italic", "italic",
			"u", "underlined",
			"underlined", "underlined",
			"st", "strikethrough",
			"strikethrough", "strikethrough",
			"obf", "obfuscated",
			"obfuscated", "obfuscated");

	/**
	 * Return list of colors the sender has permission for
	 *
	 * @param sender
	 * @return
	 */
	public static List<CompChatColor> getGuiColorsForPermission(final CommandSender sender) {
		return loadGuiColorsForPermission(sender, CompChatColor.getColors());
	}

	/**
	 * Return list of decorations the sender has permission for
	 *
	 * @param sender
	 * @return
	 */
	public static List<CompChatColor> getGuiDecorationsForPermission(final CommandSender sender) {
		return loadGuiColorsForPermission(sender, CompChatColor.getDecorations());
	}

	/**
	 * Compile the permission for the color, if HEX or not, for the given sender
	 *
	 * @param sender
	 * @param color
	 * @return
	 */
	public static String getReadableGuiColorPermission(final CommandSender sender, final CompChatColor color) {
		final String name = color.getName();

		if (color.isHex())
			return Permissions.Color.HEXGUICOLOR + color.getName().substring(1);

		return Permissions.Color.GUICOLOR + name.toLowerCase();
	}

	/**
	 * Converts the legacy colors to mini and removes tags the sender does not have permission for.
	 *
	 * @param sender
	 * @param message
	 * @param type
	 * @return
	 */
	public static String removeColorsNoPermission(final CommandSender sender, String message, final Colors.Type type) {
		if (HookManager.isItemsAdderLoaded())
			message = message.replace("§f", "").replace("§r", "");

		return filterTagsByPermission(sender, CompChatColor.convertLegacyToMini(message, true), type);
	}

	/*
	 * Filter out tags that the sender does not have permission for
	 */
	private static String filterTagsByPermission(final CommandSender sender, final String message, final Colors.Type type) {
		if (isInteractiveChatEnabled == null)
			isInteractiveChatEnabled = Platform.isPluginInstalled("InteractiveChat");

		final boolean canUse = Settings.Colors.APPLY_ON.contains(type) && sender.hasPermission(Permissions.Color.USE + type.getKey());
		final boolean hasAllColors = sender.hasPermission(Permissions.Color.COLOR + "colors");
		final boolean hasAllDecorations = sender.hasPermission(Permissions.Color.COLOR + "decorations");

		final StringBuilder result = new StringBuilder();
		final Stack<String> tagStack = new Stack<>();
		int i = 0;

		while (i < message.length()) {
			final char c = message.charAt(i);

			if (c == '\\' && i + 1 < message.length() && message.charAt(i + 1) == '<') {
				result.append("\\<");

				i += 2;

			} else if (c == '<') {
				final int start = i;
				final int end = findClosingBracket(message, i);

				if (end == -1) {
					result.append(c);

					i++;

				} else {
					final String tag = message.substring(i + 1, end);
					final String tagName = getTagName(tag);
					final boolean isClosingTag = tagName.startsWith("/");
					final String tagCheckName = isClosingTag ? tagName.substring(1) : tagName;

					if (!canUse || !hasPermissionForTag(sender, tagCheckName, hasAllColors, hasAllDecorations, message))
						i = end + 1;

					else {
						result.append(message, start, end + 1);

						if (!isClosingTag)
							tagStack.push(tagCheckName);

						else if (!tagStack.isEmpty())
							tagStack.pop();

						i = end + 1;
					}
				}

			} else {
				result.append(c);

				i++;
			}
		}
		return result.toString();
	}

	/*
	 * Find the closing bracket for the tag
	 */
	private static int findClosingBracket(final String message, final int startIndex) {
		int i = startIndex + 1;
		int depth = 1;

		while (i < message.length()) {
			final char c = message.charAt(i);

			if (c == '<')
				depth++;

			else if (c == '>')
				depth--;

			if (depth == 0)
				return i;

			i++;
		}

		return -1;
	}

	/*
	 * Get the tag name from the tag content
	 */
	private static String getTagName(final String tagContent) {
		final int spaceIndex = tagContent.indexOf(' ');
		final int colonIndex = tagContent.indexOf(':');
		final int endIndex = spaceIndex != -1 ? spaceIndex : colonIndex != -1 ? colonIndex : tagContent.length();

		return tagContent.substring(0, endIndex).trim();
	}

	/*
	 * Check if the sender has permission for the given tag
	 */
	private static boolean hasPermissionForTag(final CommandSender sender, String tag, final boolean hasAllColors, final boolean hasAllDecorations, final String message) {
		if (tag.startsWith("#")) {
			if (tag.length() == 7)
				return sender.hasPermission(Permissions.Color.HEXCOLOR + tag.substring(1));

			return false;
		}

		// Fix InteractiveChat
		if ((tag.startsWith("chat=") || tag.startsWith("cmd=")) && isInteractiveChatEnabled) {
			Common.logTimed(3 * 60 * 60, "Found InteractiveChat tag " + tag + " in a message. If this causes issues with UUIDs being caught by our rules, try lowering Chat_Listener_Priority in settings.yml to 'LOWEST'. Unfortunately there is not a fix for commands. This message only shows once per 3 hours.");

			return true;

		} else if ("pride".equals(tag))
			return false;

		else if ("grey".equals(tag))
			tag = "gray";

		else if ("dark_grey".equals(tag))
			tag = "dark_gray";

		else if ("insert".equals(tag))
			tag = "insertion";

		else if (tag.contains(":"))
			tag = tag.split(":", 2)[0];

		if ("reset".equals(tag) || "gradient".equals(tag))
			return sender.hasPermission(Permissions.Color.COLOR + tag);

		if ("hover".equals(tag)
				|| "head".equals(tag)
				|| "click".equals(tag)
				|| "insertion".equals(tag)
				|| "rainbow".equals(tag)
				|| "shadow".equals(tag)
				|| "lang".equals(tag)
				|| "transition".equals(tag)
				|| "font".equals(tag))

			return sender.hasPermission(Permissions.Color.ACTION + tag);

		if (NamedTextColor.NAMES.value(tag) != null) {
			final boolean canUse = sender.hasPermission(Permissions.Color.COLOR + tag);

			if (!canUse)
				return false;

			return hasAllColors || canUse;
		}

		final String decoration = DECORATION_PERMISSIONS.get(tag);

		if (decoration != null) {
			final boolean canUse = sender.hasPermission(Permissions.Color.COLOR + decoration);

			if (!canUse)
				return false;

			return hasAllDecorations || canUse;
		}

		if (Settings.FILTER_UNKNOWN_MINI_TAGS) {
			Common.warning("Filtering unknown tag '" + tag + "' in message '" + message + "'. To allow it, set Filter_Unknown_Mini_Tags to false in settings.yml.");

			return false;
		}

		return true;
	}

	/*
	 * Compile list of colors the sender has permission to use
	 */
	private static List<CompChatColor> loadGuiColorsForPermission(final CommandSender sender, final List<CompChatColor> list) {
		final List<CompChatColor> selected = new ArrayList<>();

		for (final CompChatColor color : list)
			if (color.isHex()) {
				if (sender.hasPermission(Permissions.Color.HEXGUICOLOR + color.getName().toLowerCase()))
					selected.add(color);

			} else if (sender.hasPermission(Permissions.Color.GUICOLOR + color.getName().toLowerCase()))
				selected.add(color);

		return selected;
	}

	// ----------------------------------------------------------------------------------------------------
	// Gradients
	// ----------------------------------------------------------------------------------------------------

	/**
	 * All preconfigured gradients available in the GUI.
	 */
	private static final List<Gradient> PRECONFIGURED_GRADIENTS = Arrays.asList(
			// Warm
			new Gradient("Sunset", "#FF512F", "#F09819"),
			new Gradient("Fire", "#F12711", "#F5AF19"),
			new Gradient("Lava", "#FF0844", "#FFB199"),
			new Gradient("Honey", "#F7971E", "#FFD200"),
			new Gradient("Coral", "#FF9966", "#FF5E62"),
			new Gradient("Rose", "#EC008C", "#FC6767"),
			// Cool
			new Gradient("Ocean", "#00C6FF", "#0072FF"),
			new Gradient("Ice", "#74EBD5", "#9FACE6"),
			new Gradient("Sky", "#56CCF2", "#2F80ED"),
			new Gradient("Mint", "#00B09B", "#96C93D"),
			new Gradient("Forest", "#11998E", "#38EF7D"),
			new Gradient("Aurora", "#00D2FF", "#928DAB"),
			// Mystical
			new Gradient("Twilight", "#654EA3", "#EAAFC8"),
			new Gradient("Berry", "#8E2DE2", "#4A00E0"),
			new Gradient("Sakura", "#FF6B6B", "#FFC3A0"));

	/**
	 * Return the preconfigured gradients.
	 *
	 * @return
	 */
	public static List<Gradient> getPreconfiguredGradients() {
		return PRECONFIGURED_GRADIENTS;
	}

	/**
	 * Return the preconfigured gradients the player has permission to use
	 * via the gradient GUI (chatcontrol.guigradient.{name}).
	 *
	 * @param player
	 * @return
	 */
	public static List<Gradient> getPreconfiguredGradientsForPermission(final Player player) {
		final List<Gradient> list = new ArrayList<>();

		for (final Gradient gradient : PRECONFIGURED_GRADIENTS)
			if (player.hasPermission(Permissions.Color.GUIGRADIENT + gradient.permissionName))
				list.add(gradient);

		return list;
	}

	/**
	 * Return true if the player has permission for a preconfigured gradient
	 * matching the given color tuple.
	 *
	 * @param player
	 * @param gradient
	 * @return
	 */
	public static boolean hasPermissionForGradient(final Player player, final Tuple<CompChatColor, CompChatColor> gradient) {
		for (final Gradient preconfigured : PRECONFIGURED_GRADIENTS)
			if (preconfigured.from.getName().equalsIgnoreCase(gradient.getKey().getName())
					&& preconfigured.to.getName().equalsIgnoreCase(gradient.getValue().getName()))
				if (player.hasPermission(Permissions.Color.GUIGRADIENT + preconfigured.permissionName))
					return true;

		return false;
	}

	/**
	 * Return a display name for the given gradient tuple.
	 * Uses the preconfigured name if matched, otherwise plain hex codes.
	 *
	 * @param gradient
	 * @return
	 */
	public static String getGradientDisplayName(final Tuple<CompChatColor, CompChatColor> gradient) {
		for (final Gradient preconfigured : PRECONFIGURED_GRADIENTS)
			if (preconfigured.from.getName().equalsIgnoreCase(gradient.getKey().getName())
					&& preconfigured.to.getName().equalsIgnoreCase(gradient.getValue().getName()))
				return preconfigured.displayName;

		return toLegacyGradient(gradient.getKey().getName(), gradient.getKey(), gradient.getKey())
				+ " - "
				+ toLegacyGradient(gradient.getValue().getName(), gradient.getValue(), gradient.getValue());
	}

	/**
	 * Format the given chat color as a #RRGGBB hex string, regardless of whether
	 * it was created from a legacy name or hex input. Useful for the
	 * {player_chat_gradient_from} / {player_chat_gradient_to} placeholders.
	 *
	 * @param color
	 * @return
	 */
	public static String toHexString(final CompChatColor color) {
		return String.format("#%06X", color.getColor().getRGB() & 0xFFFFFF);
	}

	/**
	 * Bake a gradient into per-character legacy hex color codes.
	 * Produces \u00a7x\u00a7R\u00a7R\u00a7G\u00a7G\u00a7B\u00a7Bc for each character, interpolating
	 * linearly between the two colors.
	 *
	 * @param text
	 * @param from
	 * @param to
	 * @return
	 */
	public static String toLegacyGradient(final String text, final CompChatColor from, final CompChatColor to) {
		final int r1 = from.getColor().getRed(), g1 = from.getColor().getGreen(), b1 = from.getColor().getBlue();
		final int r2 = to.getColor().getRed(), g2 = to.getColor().getGreen(), b2 = to.getColor().getBlue();
		final int len = text.length();
		final StringBuilder result = new StringBuilder();

		for (int i = 0; i < len; i++) {
			final float ratio = len > 1 ? (float) i / (len - 1) : 0;
			final int r = Math.round(r1 + (r2 - r1) * ratio);
			final int g = Math.round(g1 + (g2 - g1) * ratio);
			final int b = Math.round(b1 + (b2 - b1) * ratio);
			final String hex = String.format("%02x%02x%02x", r, g, b);

			result.append('\u00a7').append('x');

			for (final char c : hex.toCharArray())
				result.append('\u00a7').append(c);

			result.append(text.charAt(i));
		}

		return result.toString();
	}

	// ----------------------------------------------------------------------------------------------------
	// Inner
	// ----------------------------------------------------------------------------------------------------

	/**
	 * Represents a preconfigured gradient with a display name and two colors.
	 */
	@Getter
	public static final class Gradient {

		private final String displayName;
		private final CompChatColor from;
		private final CompChatColor to;
		private final String permissionName;

		Gradient(final String displayName, final String fromHex, final String toHex) {
			this.displayName = displayName;
			this.from = CompChatColor.fromString(fromHex);
			this.to = CompChatColor.fromString(toHex);
			this.permissionName = displayName.toLowerCase().replace(" ", "_");
		}
	}

	/**
	 * Represents a message type
	 */
	@RequiredArgsConstructor
	public enum Type {

		/**
		 * Use colors on anvil
		 */
		ANVIL("anvil"),

		/**
		 * Use colors in books
		 */
		BOOK("book"),

		/**
		 * Use colors in chat
		 */
		CHAT("chat"),

		/**
		 * Use colors in /me
		 */
		ME("me"),

		/**
		 * Use colors in nicks
		 */
		NICK("nick"),

		/**
		 * Use colors in prefixes
		 */
		PREFIX("prefix"),

		/**
		 * Use colors in PMs
		 */
		PRIVATE_MESSAGE("private_message"),

		/**
		 * Use colors in /say
		 */
		SAY("say"),

		/**
		 * Use colors on signs
		 */
		SIGN("sign"),

		/**
		 * Use colors in custom suffix
		 */
		SUFFIX("suffix")

		;

		/**
		 * The saveable non-obfuscated key
		 */
		@Getter
		private final String key;

		/**
		 * Returns {@link #getKey()}
		 */
		@Override
		public String toString() {
			return this.key;
		}

		/**
		 * Attempt to load a log type from the given config key
		 *
		 * @param key
		 * @return
		 */
		public static Type fromKey(final String key) {
			for (final Type mode : values())
				if (mode.key.equalsIgnoreCase(key))
					return mode;

			throw new IllegalArgumentException("No such message type: " + key + ". Available: " + CommonCore.join(values()));
		}
	}
}
