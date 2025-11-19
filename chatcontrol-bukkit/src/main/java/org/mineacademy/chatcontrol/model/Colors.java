package org.mineacademy.chatcontrol.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.bukkit.command.CommandSender;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.model.CompChatColor;
import org.mineacademy.fo.model.HookManager;
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
