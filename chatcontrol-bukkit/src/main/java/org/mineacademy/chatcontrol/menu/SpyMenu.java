package org.mineacademy.chatcontrol.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Spy;
import org.mineacademy.chatcontrol.model.Spy.Type;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.menu.Menu;
import org.mineacademy.fo.menu.MenuPaged;
import org.mineacademy.fo.menu.button.Button;
import org.mineacademy.fo.menu.button.ButtonMenu;
import org.mineacademy.fo.menu.model.ItemCreator;
import org.mineacademy.fo.remain.CompColor;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.settings.Lang;
import org.mineacademy.fo.settings.SimpleSettings;

/**
 * Menu for spying control panel
 */
public final class SpyMenu extends Menu {

	/**
	 * The player we are editing spying settings for
	 */
	private final PlayerCache cache;
	private final List<Button> spyButtons = new ArrayList<>();
	private final Button toggleButton;

	/*
	 * Initiate this menu for the given player cache,
	 * so that admins can change spying settings for
	 * other players, too.
	 */
	private SpyMenu(final Player viewer, final PlayerCache cache) {
		this.cache = cache;

		this.setViewer(viewer);
		this.setTitle("Spying Menu");
		this.setSize(9 * 2);

		this.registerSpyButtons();

		this.toggleButton = Button.makeSimple(ItemCreator
				.fromMaterial(this.cache.isSpyingSomething() ? CompMaterial.LAVA_BUCKET : CompMaterial.BUCKET)
				.name(Lang.legacy("menu-spy-title-" + (this.cache.isSpyingSomething() ? "disable" : "enable")))
				.lore(Lang.legacy("menu-spy-toggle-lore")), player -> {

					if (this.cache.isSpyingSomething())
						this.cache.setSpyingOff();
					else
						this.cache.setSpyingOn(player);

					this.newInstance().displayTo(player);
				});
	}

	private void registerSpyButtons() {
		this.spyButtons.clear();

		if (this.canSpy(Type.CHAT)) {
			final List<String> lore = new ArrayList<>();

			Collections.addAll(lore, Lang.legacy("menu-spy-button-lore-1").split("\n"));

			if (!this.cache.getSpyingChannels().isEmpty()) {
				Collections.addAll(lore, Lang.legacy("menu-spy-button-lore-channel").split("\n"));

				for (final String channelName : this.cache.getSpyingChannels())
					lore.add(" - " + channelName);
			}

			this.spyButtons.add(new ButtonMenu(ChannelsMenu::new, ItemCreator.from(
					CompMaterial.PLAYER_HEAD,
					Lang.legacy("menu-spy-button-title"))
					.lore(lore)));
		}

		this.addClickButton(Spy.Type.COMMAND, CompMaterial.COMMAND_BLOCK);
		this.addClickButton(Spy.Type.PRIVATE_MESSAGE, CompMaterial.PAPER);
		this.addClickButton(Spy.Type.MAIL, CompMaterial.BOOK);
		this.addClickButton(Spy.Type.SIGN, CompMaterial.OAK_SIGN);
		this.addClickButton(Spy.Type.BOOK, CompMaterial.WRITABLE_BOOK);
		this.addClickButton(Spy.Type.ANVIL, CompMaterial.ANVIL);
	}

	private void addClickButton(final Spy.Type type, final CompMaterial material) {
		if (!this.canSpy(type))
			return;

		this.spyButtons.add(Button.makeSimple(ItemCreator
				.fromMaterial(material)
				.name(ChatUtil.capitalize(type.getLangKey()))
				.lore(Lang.legacy("menu-spy-button-toggle-lore",
						"label", type.getLangKey(),
						"status", Lang.component("menu-spy-status-" + (this.cache.isSpying(type) ? "on" : "off"))))
				.glow(this.cache.isSpying(type)),

				player -> {
					final boolean isSpying = this.cache.isSpying(type);

					this.cache.setSpying(type, !isSpying);
					this.registerSpyButtons();

					this.restartMenu(Lang.legacy("menu-spy-status-toggle-" + (isSpying ? "off" : "on"), "type", type.getLangKey()));
				}));

	}

	private boolean canSpy(final Spy.Type type) {
		return this.getViewer().hasPermission(Permissions.Spy.TYPE + type.getKey()) && Settings.Spy.APPLY_ON.contains(type);
	}

	/**
	 * @see org.mineacademy.fo.menu.Menu#getButtonsToAutoRegister()
	 */
	@Override
	protected List<Button> getButtonsToAutoRegister() {
		return this.spyButtons;
	}

	/**
	 * @see org.mineacademy.fo.menu.Menu#getItemAt(int)
	 */
	@Override
	public ItemStack getItemAt(final int slot) {

		if (this.spyButtons.size() > slot)
			return this.spyButtons.get(slot).getItem();

		if (slot == this.getSize() - 2)
			return this.toggleButton.getItem();

		if (this.spyButtons.isEmpty() && slot == this.getCenterSlot())
			return ItemCreator.from(CompMaterial.BARRIER,
					"&cNo Permissions",
					Lang.legacy("command-spy-cannot-toggle-anything-no-perms",
							"permission", Permissions.Spy.TYPE + "{type}"))
					.make();

		return null;
	}

	/**
	 * @see org.mineacademy.fo.menu.Menu#getInfoButtonPosition()
	 */
	@Override
	protected int getInfoButtonPosition() {
		return this.getSize() - 1;
	}

	/**
	 * @see org.mineacademy.fo.menu.Menu#getInfo()
	 */
	@Override
	protected String[] getInfo() {
		return Lang.legacy("menu-spy-help", "label", SimpleSettings.MAIN_COMMAND_ALIASES.get(0)).split("\n");
	}

	/**
	 * @see org.mineacademy.fo.menu.Menu#newInstance()
	 */
	@Override
	public Menu newInstance() {
		return new SpyMenu(this.getViewer(), this.cache);
	}

	/**
	 * Show this menu to the given player with the given player data
	 *
	 * @param forWhom
	 * @param player
	 */
	public static void showTo(final PlayerCache forWhom, final Player player) {
		new SpyMenu(player, forWhom).displayTo(player);
	}

	/* ------------------------------------------------------------------------------- */
	/* Subclasses */
	/* ------------------------------------------------------------------------------- */

	private final class ChannelsMenu extends MenuPaged<Channel> {

		private int colorIndex = 0;

		public ChannelsMenu() {
			super(SpyMenu.this, Channel.getChannels(), true);
		}

		/**
		 * @see org.mineacademy.fo.menu.MenuPaged#convertToItemStack(java.lang.Object)
		 */
		@Override
		protected ItemStack convertToItemStack(final Channel item) {
			final CompColor[] colors = CompColor.values();

			if (this.colorIndex-- <= 0)
				this.colorIndex = colors.length - 1;

			return ItemCreator
					.fromMaterial(CompMaterial.WHITE_WOOL)
					.name(item.getName())
					.lore(Lang.legacy("menu-spy-channel-toggle-lore",
							"status", Lang.component("menu-spy-status-" + (SpyMenu.this.cache.isSpyingChannel(item) ? "on" : "off"))))

					.glow(SpyMenu.this.cache.isSpyingChannel(item))
					.color(colors[this.colorIndex]).make();

		}

		/**
		 * @see org.mineacademy.fo.menu.MenuPaged#onPageClick(org.bukkit.entity.Player, java.lang.Object, org.bukkit.event.inventory.ClickType)
		 */
		@Override
		protected void onPageClick(final Player player, final Channel item, final ClickType click) {
			final boolean isSpying = SpyMenu.this.cache.isSpyingChannel(item);

			SpyMenu.this.cache.setSpyingChannel(item, !isSpying);
			this.animateTitle(Lang.legacy("menu-spy-channel-toggle-" + (isSpying ? "off" : "on")));
		}

		/**
		 * @see org.mineacademy.fo.menu.Menu#getInfo()
		 */
		@Override
		protected String[] getInfo() {
			return null;
		}
	}
}
