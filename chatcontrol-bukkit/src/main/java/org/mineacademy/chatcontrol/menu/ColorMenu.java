package org.mineacademy.chatcontrol.menu;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.mineacademy.chatcontrol.model.Colors;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.menu.Menu;
import org.mineacademy.fo.menu.MenuPaged;
import org.mineacademy.fo.menu.button.Button;
import org.mineacademy.fo.menu.model.ItemCreator;
import org.mineacademy.fo.model.CompChatColor;
import org.mineacademy.fo.remain.CompColor;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.settings.Lang;

/**
 * Represents a way for players to self-manage their own colors
 */
public final class ColorMenu extends MenuPaged<CompChatColor> {

	private final PlayerCache cache;
	private final List<Button> decorationButtons = new ArrayList<>();
	private final Button resetColorButton;
	private final Button resetDecorationButton;
	private final Button emptyButton;

	/*
	 * Create a new color menu
	 */
	private ColorMenu(final Player player) {
		super(9 * 3, Colors.getGuiColorsForPermission(player));

		this.setTitle(Lang.legacy("menu-color-header"));

		this.cache = PlayerCache.fromCached(player);
		this.loadDecorations(player);

		if (super.getPages().get(0).isEmpty() && this.decorationButtons.isEmpty()) {
			this.resetColorButton = Button.makeEmpty();
			this.resetDecorationButton = Button.makeEmpty();

			this.emptyButton = Button.makeDummy(ItemCreator.fromMaterial(
					CompMaterial.BUCKET)
					.name(Lang.legacy("menu-color-button-empty-title"))
					.lore(Lang.legacy("menu-color-button-empty-lore")));

			return;
		}

		this.resetColorButton = Button.makeSimple(ItemCreator.from(CompMaterial.GLASS, Lang.legacy("menu-color-button-reset-color-title")), clicker -> {
			this.cache.setChatColorNoSave(null);
			this.cache.upsert();

			this.restartMenu(Lang.legacy("menu-color-color-reset"));
		});

		this.resetDecorationButton = Button.makeSimple(ItemCreator.from(CompMaterial.BEACON, Lang.legacy("menu-color-button-reset-decoration")), clicker -> {
			this.cache.setChatDecorationNoSave(null);
			this.cache.upsert();

			this.loadDecorations(player);

			this.restartMenu(Lang.legacy("menu-color-decoration-reset"));
		});

		this.emptyButton = Button.makeEmpty();
	}

	/*
	 * Load decorations the player can obtain
	 */
	private void loadDecorations(final Player player) {
		this.decorationButtons.clear();

		// Fill in random colors for decoration
		int index = 0;

		for (final CompChatColor decoration : Colors.getGuiDecorationsForPermission(player))
			this.decorationButtons.add(Button.makeSimple(ItemCreator.fromMaterial(
					CompMaterial.WHITE_CARPET)
					.name(ChatUtil.capitalizeFully(Lang.legacy("decoration-" + decoration)) + Lang.legacy("menu-color-button-decoration-title"))
					.lore(Lang.legacy("menu-color-button-decoration-lore", "decoration", decoration.toString()))
					.color(CompColor.values()[index++])
					.glow(this.cache.getChatDecoration() == decoration),
					clicker -> {
						this.cache.setChatDecorationNoSave(decoration);
						this.cache.upsert();

						this.loadDecorations(player);

						this.restartMenu(Lang.legacy("menu-color-decoration-set", "decoration", ChatUtil.capitalizeFully(decoration)));
					}));
	}

	/**
	 * @see org.mineacademy.fo.menu.MenuPaged#convertToItemStack(java.lang.Object)
	 */
	@Override
	protected ItemStack convertToItemStack(final CompChatColor color) {
		return ItemCreator
				.fromMaterial(CompMaterial.WHITE_WOOL)
				.name(ChatUtil.capitalizeFully(Lang.legacy("color-" + color.getName().replace("_", "-"))) + Lang.legacy("menu-color-button-color-title"))
				.lore(Lang.legacy("menu-color-button-color-lore", "color", color.toString()))
				.color(CompColor.fromChatColor(color))
				.glow(this.cache.getChatColor() == color)
				.make();
	}

	/**
	 * @see org.mineacademy.fo.menu.Menu#getButtonsToAutoRegister()
	 */
	@Override
	protected List<Button> getButtonsToAutoRegister() {
		return this.decorationButtons;
	}

	/**
	 * @see org.mineacademy.fo.menu.MenuPaged#getItemAt(int)
	 */
	@Override
	public ItemStack getItemAt(final int slot) {

		if (slot == this.getCenterSlot() && !CompMaterial.isAir(this.emptyButton.getItem().getType()))
			return this.emptyButton.getItem();

		if (slot >= 9 * 3 && slot - 9 * 3 < this.decorationButtons.size())
			return this.decorationButtons.get(slot - 9 * 3).getItem();

		if (slot == this.getSize() - 2)
			return this.resetDecorationButton.getItem();

		if (slot == this.getSize() - 1)
			return this.resetColorButton.getItem();

		return super.getItemAt(slot);
	}

	/**
	 * @see org.mineacademy.fo.menu.MenuPaged#onPageClick(org.bukkit.entity.Player, java.lang.Object, org.bukkit.event.inventory.ClickType)
	 */
	@Override
	protected void onPageClick(final Player player, final CompChatColor item, final ClickType click) {
		this.cache.setChatColorNoSave(item);
		this.cache.upsert();

		this.restartMenu(Lang.legacy("menu-color-color-set", "color", ChatUtil.capitalizeFully(item)));
	}

	/**
	 * @see org.mineacademy.fo.menu.Menu#getInfoButtonPosition()
	 */
	@Override
	protected int getInfoButtonPosition() {
		return 9 * 3 - 1;
	}

	/**
	 * @see org.mineacademy.fo.menu.Menu#getInfo()
	 */
	@Override
	protected String[] getInfo() {
		return Lang.legacy("menu-color-help",
				"color", this.cache.hasChatColor() ? this.cache.getChatColor().toColorizedChatString() : Lang.plain("part-none"),
				"decoration", this.cache.hasChatDecoration() ? this.cache.getChatDecoration().toColorizedChatString() : Lang.plain("part-none"))
				.split("\n");
	}

	/**
	 * @see org.mineacademy.fo.menu.Menu#newInstance()
	 */
	@Override
	public Menu newInstance() {
		return new ColorMenu(this.getViewer());
	}

	/**
	 * Show this menu to the given player
	 *
	 * @param player
	 */
	public static void showTo(final Player player) {
		new ColorMenu(player).displayTo(player);
	}
}
