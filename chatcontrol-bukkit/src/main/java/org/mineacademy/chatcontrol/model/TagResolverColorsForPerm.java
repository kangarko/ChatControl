package org.mineacademy.chatcontrol.model;

import org.bukkit.command.CommandSender;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.StyleBuilderApplicable;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.ParsingException;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * An improved tag matcher which only resolves colors the player has the permission for.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class TagResolverColorsForPerm implements TagResolver {

	/**
	 * The sender whose permissions are being evaluated
	 */
	private final CommandSender sender;

	/**
	 * Find a color from the given name
	 *
	 * @param colorName
	 * @param args
	 * @param context
	 *
	 * @return
	 */
	@Override
	public Tag resolve(String colorName, final ArgumentQueue args, final Context context) throws ParsingException {

		// If typed <color:red> instead of just <red>
		if (this.hasTagColorLabel(colorName))
			colorName = args.popOr("Expected to find a color parameter: <name>|#RRGGBB").lowerValue();

		final StyleBuilderApplicable color;

		if (colorName.charAt(0) == TextColor.HEX_CHARACTER) {
			color = TextColor.fromHexString(colorName);

			if (color != null && !this.sender.hasPermission(Permissions.Color.HEXCOLOR + color.toString().substring(1)))
				return null;

		} else {
			if (colorName.equals("dark_grey"))
				color = NamedTextColor.DARK_GRAY;

			else if (colorName.equals("grey"))
				color = NamedTextColor.GRAY;

			else
				color = NamedTextColor.NAMES.value(colorName);

			if (color != null && !this.sender.hasPermission(Permissions.Color.COLOR + ((NamedTextColor) color).toString().toLowerCase()))
				return Tag.styling();
		}

		// If no color found, assume sender hasn't got a permission and just return the unparsed tag
		return color != null ? Tag.styling(color) : null;
	}

	/**
	 * Return if the name is a valid color
	 *
	 * @return
	 */
	@Override
	public boolean has(final String param) {
		return this.hasTagColorLabel(param) || TextColor.fromHexString(param) != null || NamedTextColor.NAMES.value(param) != null || param.equals("grey") || param.equals("dark_grey");
	}

	/*
	 * Return if the given tag name is a color such as <color:red>
	 */
	private boolean hasTagColorLabel(final String param) {
		return param.equals("color") || param.equals("colour") || param.equals("c");
	}

	/**
	 * Create a new tag resolver for the given sender
	 *
	 * @param sender
	 * @return
	 */
	public static TagResolver create(final CommandSender sender) {
		return new TagResolverColorsForPerm(sender);
	}
}
