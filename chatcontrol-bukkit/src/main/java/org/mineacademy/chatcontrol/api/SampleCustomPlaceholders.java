/*package org.mineacademy.chatcontrol.api;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.SimpleExpansion;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.platform.FoundationPlayer;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;


// This class is automatically registered if your plugin uses Foundation,
// if not then call {@link Variables#addExpansion(SimpleExpansion)} in your onEnable()
// manually.
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SampleCustomPlaceholders extends SimpleExpansion {

	@Getter
	private static final SimpleExpansion instance = new SampleCustomPlaceholders();

	@Override
	protected SimpleComponent onReplace(FoundationPlayer audience, String identifier) {

		// You can get the Bukkit sender and Players from the audience object
		final CommandSender sender = audience.getSender();
		final Player player = audience.isPlayer() ? audience.getPlayer() : null;

		// use {my_variable} or {chatcontrol_my_variable} if using PlaceholderAPI in your messages
		if ("my_variable".equals(identifier))
			return SimpleComponent.fromMiniAmpersand("<red>My &evariable");

		else if ("my_section_variable".equals(identifier))
			return SimpleComponent.fromAmpersand("&cMy variable");

		else if ("my_adventure_variable".equals(identifier))
			return SimpleComponent.fromAdventure(Component.text("My variable").color(NamedTextColor.RED));

		else if ("my_plaintext_variable".equals(identifier))
			return SimpleComponent.fromPlain("My variable");

		return null;
	}
}*/
