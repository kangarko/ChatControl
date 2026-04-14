package org.mineacademy.chatcontrol.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.mineacademy.chatcontrol.model.Colors;
import org.mineacademy.chatcontrol.model.Colors.Gradient;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.fo.menu.Menu;
import org.mineacademy.fo.menu.MenuPaged;
import org.mineacademy.fo.menu.button.Button;
import org.mineacademy.fo.menu.model.ItemCreator;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.remain.CompColor;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.settings.Lang;

/**
 * Menu for selecting preconfigured chat gradients.
 */
public final class GradientMenu extends MenuPaged<Gradient> {

	private final PlayerCache cache;
	private final Button resetButton;

	/*
	 * Create a new gradient menu
	 */
	private GradientMenu(final Menu parent, final Player player) {
		super(9 * 3, parent, getGradientsForPermission(player));

		this.setTitle(Lang.legacy("menu-gradient-header"));

		this.cache = PlayerCache.fromCached(player);

		if (super.getPages().get(0).isEmpty()) {
			this.resetButton = Button.makeEmpty();
			return;
		}

		this.resetButton = Button.makeSimple(ItemCreator.from(CompMaterial.GLASS, Lang.legacy("menu-gradient-button-reset-title")), clicker -> {
			this.cache.setChatGradientNoSave(null);
			this.cache.upsert();

			this.restartMenu(Lang.legacy("menu-color-color-reset"));
		});
	}

	/*
	 * Return gradients the player has permission for
	 */
	private static List<Gradient> getGradientsForPermission(final Player player) {
		final List<Gradient> list = new ArrayList<>();

		for (final Gradient gradient : Colors.getPreconfiguredGradients())
			if (player.hasPermission(Permissions.Color.GUIGRADIENT + gradient.getPermissionName()))
				list.add(gradient);

		return list;
	}

	/**
	 * @see org.mineacademy.fo.menu.MenuPaged#convertToItemStack(java.lang.Object)
	 */
	@Override
	protected ItemStack convertToItemStack(final Gradient gradient) {
		final boolean selected = this.cache.hasChatGradient()
				&& this.cache.getChatGradient().getKey().getName().equals(gradient.getFrom().getName())
				&& this.cache.getChatGradient().getValue().getName().equals(gradient.getTo().getName());

		return ItemCreator.fromMaterial(CompMaterial.LEATHER_HORSE_ARMOR)
				.name(Colors.toLegacyGradient(gradient.getDisplayName() + " Gradient", gradient.getFrom(), gradient.getTo()))
				.lore(Lang.legacy("menu-gradient-button-gradient-lore",
						"from", Colors.toLegacyGradient(gradient.getFrom().getName(), gradient.getFrom(), gradient.getFrom()),
						"to", Colors.toLegacyGradient(gradient.getTo().getName(), gradient.getTo(), gradient.getTo()),
						"preview", Colors.toLegacyGradient("This is a sample text", gradient.getFrom(), gradient.getTo())))
				.color(CompColor.fromName(gradient.getFrom().getName()))
				.glow(selected)
				.make();
	}

	/**
	 * @see org.mineacademy.fo.menu.MenuPaged#onPageClick(org.bukkit.entity.Player, java.lang.Object, org.bukkit.event.inventory.ClickType)
	 */
	@Override
	protected void onPageClick(final Player player, final Gradient gradient, final ClickType click) {
		this.cache.setChatGradientNoSave(new Tuple<>(gradient.getFrom(), gradient.getTo()));
		this.cache.upsert();

		this.restartMenu(Lang.legacy("menu-gradient-gradient-set", "gradient", gradient.getDisplayName()));
	}

	/**
	 * @see org.mineacademy.fo.menu.MenuPaged#getItemAt(int)
	 */
	@Override
	public ItemStack getItemAt(final int slot) {

		if (slot == this.getSize() - 2)
			return this.resetButton.getItem();

		return super.getItemAt(slot);
	}

	/**
	 * @see org.mineacademy.fo.menu.Menu#getButtonsToAutoRegister()
	 */
	@Override
	protected List<Button> getButtonsToAutoRegister() {
		return Collections.singletonList(this.resetButton);
	}

	/**
	 * @see org.mineacademy.fo.menu.Menu#getInfo()
	 */
	@Override
	protected String[] getInfo() {
		final String gradientDisplay;

		if (this.cache.hasChatGradient())
			gradientDisplay = Colors.getGradientDisplayName(this.cache.getChatGradient());
		else
			gradientDisplay = Lang.plain("part-none");

		return Lang.legacy("menu-gradient-help", "gradient", gradientDisplay).split("\n");
	}

	/**
	 * @see org.mineacademy.fo.menu.Menu#newInstance()
	 */
	@Override
	public Menu newInstance() {
		return new GradientMenu(this.getParent(), this.getViewer());
	}

	/**
	 * Show this menu to the given player, with the given parent
	 *
	 * @param parent
	 * @param player
	 */
	public static void showTo(final Menu parent, final Player player) {
		new GradientMenu(parent, player).displayTo(player);
	}
}
