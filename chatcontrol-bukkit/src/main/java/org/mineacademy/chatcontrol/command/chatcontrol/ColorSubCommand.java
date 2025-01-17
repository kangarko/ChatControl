package org.mineacademy.chatcontrol.command.chatcontrol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.MainSubCommand;
import org.mineacademy.chatcontrol.menu.ColorMenu;
import org.mineacademy.chatcontrol.model.Colors;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.model.CompChatColor;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.settings.Lang;

public final class ColorSubCommand extends MainSubCommand {

	public ColorSubCommand() {
		super("color/c");

		this.setMinArguments(1);
		this.setPermission(Permissions.Command.COLOR);
		this.setUsage(Lang.component("command-color-usage"));
		this.setDescription(Lang.component("command-color-description"));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		final List<SimpleComponent> usages = new ArrayList<>();
		final boolean hasHex = MinecraftVersion.atLeast(V.v1_16);

		usages.add(Lang.component("command-color-usages-1"));

		if (hasHex)
			usages.add(Lang.component("command-color-usages-hex-1"));

		usages.add(Lang.component("command-color-usages-2"));

		if (hasHex)
			usages.add(Lang.component("command-color-usages-hex-2"));

		return SimpleComponent.join(usages);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		final boolean hasHex = MinecraftVersion.atLeast(V.v1_16);

		this.checkUsage(this.args.length < 4);
		this.checkBoolean(this.isPlayer() || (this.args.length > ("status".equals(this.args[0]) ? 1 : 2)), Lang.component("command-console-missing-player-name"));

		this.pollCache(this.args.length == 2 && "status".equals(this.args[0]) ? this.args[1] : this.args.length == 3 ? this.args[2] : this.getPlayer().getName(), cache -> {
			final String playerName = cache.getPlayerName();

			final String colorName = this.args[0];
			final String decorationName = this.args.length >= 2 ? this.args[1] : "";

			final boolean self = playerName.equals(this.audience.getName());

			boolean colorReset = false;
			boolean colorSet = false;

			boolean decorationReset = false;
			boolean decorationSet = false;

			if (!self)
				this.checkPerm(Permissions.Command.COLOR_OTHERS);

			if ("menu".equals(colorName)) {
				this.checkConsole();

				ColorMenu.showTo(this.getPlayer());
				return;

			} else if ("status".equals(colorName)) {
				this.checkBoolean(cache.hasChatColor() || cache.hasChatDecoration(), Lang.component("command-color-not-saved", "player", cache.getPlayerName()));

				String message = cache.getPlayerName() + " " + Lang.plain("part-has") + " ";

				if (cache.hasChatColor())
					message += cache.getChatColor().toColorizedChatString() + " " + Lang.legacy("command-color-chat-color");

				if (cache.hasChatDecoration())
					message += (cache.hasChatColor() ? " " + Lang.plain("part-and") + " " : "") + cache.getChatDecoration().getName() + " " + Lang.legacy("command-color-decoration");

				this.tellInfo(message + ".");
				return;

			} else if ("reset".equals(colorName) || "default".equals(colorName)) {
				if ("reset".equals(colorName)) {
					cache.setChatColorNoSave(null);

					colorReset = true;
				}

			} else {
				CompChatColor color;

				try {
					color = CompChatColor.fromString(colorName);

				} catch (final IllegalArgumentException ex) {
					this.tellError(Lang.component("command-color-invalid-color-" + (hasHex ? "hex" : "legacy"), "available", Colors.getGuiColorsForPermission(this.getSender())));

					return;
				}

				this.checkBoolean(color.getColor() != null, Lang.component("command-color-decoration-not-allowed"));
				this.checkPerm(Colors.getReadableGuiColorPermission(this.getSender(), color));

				cache.setChatColorNoSave(color);
				colorSet = true;
			}

			if ("reset".equals(decorationName)) {
				cache.setChatDecorationNoSave(null);

				decorationReset = true;

			} else if (!"".equals(decorationName)) {
				CompChatColor decoration;

				try {
					decoration = CompChatColor.fromString(decorationName);

				} catch (final IllegalArgumentException ex) {
					this.tellError(Lang.component("command-color-invalid-decoration", "available", Colors.getGuiDecorationsForPermission(this.getSender())));

					return;
				}

				this.checkBoolean(decoration.getColor() == null, Lang.component("command-color-color-not-allowed"));
				this.checkPerm(Permissions.Color.GUICOLOR + decoration.getName().toLowerCase());

				cache.setChatDecorationNoSave(decoration);
				decorationSet = true;
			}

			cache.upsert();

			String modeLangPrefix = null;

			if (colorSet) {
				modeLangPrefix = decorationSet ? "set-color-set-decoration" : decorationReset ? "set-color-reset-decoration" : "set-color";

			} else if (colorReset)
				modeLangPrefix = decorationSet ? "reset-color-set-decoration" : decorationReset ? "reset-color-reset-decoration" : "reset-color";

			else if (decorationSet || decorationReset)
				modeLangPrefix = decorationSet ? "set-decoration" : "reset-decoration";
			else
				modeLangPrefix = "no-change";

			if (modeLangPrefix != null) {
				this.tellSuccess(Lang.component("command-color-" + modeLangPrefix,
						"player", playerName,
						"color", (colorSet ? cache.getChatColor().toColorizedChatString() : decorationSet ? cache.getChatDecoration().toColorizedChatString() : ""),
						"decoration", (decorationSet ? cache.getChatDecoration().toColorizedChatString() : "")));
			}

			this.updateProxyData(cache);
		});
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (this.args.length == 1)
			return this.completeLastWord(CommonCore.convertList(Colors.getGuiColorsForPermission(this.getSender()), CompChatColor::getName), Arrays.asList("status", "reset", "menu", "default"));

		if (this.args.length == 2) {
			final String label = this.args[0];

			if ("status".equals(label))
				return this.completeLastWordPlayerNames();
			else if (!"menu".equals(label))
				return this.completeLastWord(CommonCore.joinLists(CommonCore.convertList(Colors.getGuiDecorationsForPermission(this.getSender()), CompChatColor::getName), Arrays.asList("reset")));
		}

		if (this.args.length == 3 && !"status".equals(this.args[0]) && this.hasPerm(Permissions.Command.COLOR_OTHERS))
			return this.completeLastWordPlayerNames();

		return NO_COMPLETE;
	}
}
