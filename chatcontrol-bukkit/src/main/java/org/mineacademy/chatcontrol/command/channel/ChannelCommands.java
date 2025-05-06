package org.mineacademy.chatcontrol.command.channel;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.GenericSubCommand;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.ChannelMode;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Channels;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.annotation.AutoRegister;
import org.mineacademy.fo.command.SimpleCommandGroup;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.settings.Lang;

import lombok.Getter;

/**
 * Stores all /channel commands
 */
@AutoRegister
public final class ChannelCommands extends SimpleCommandGroup {

	/**
	 * The singleton of this class
	 */
	@Getter
	private final static SimpleCommandGroup instance = new ChannelCommands();

	/**
	 * Initiaze this class, this must be called after settings have loaded
	 */
	private ChannelCommands() {
		super(Settings.Channels.COMMAND_ALIASES);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommandGroup#getHelpHeader()
	 */
	@Override
	protected String[] getHelpHeader() {
		return new String[] {
				"&8",
				"&8" + CommonCore.chatLineSmooth(),
				this.getHeaderPrefix() + "  Channel Commands",
				" ",
				"  &2[] &7= " + Lang.plain("command-label-optional-args"),
				"  &6<> &7= " + Lang.plain("command-label-required-args"),
				" "
		};
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommandGroup#getHeaderPrefix()
	 */
	@Override
	protected String getHeaderPrefix() {
		return "<gradient:#db0000:#fb00ff><bold>";
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommandGroup#getNoParamsHeader(org.bukkit.command.CommandSender)
	 */
	@Override
	protected List<String> getNoParamsHeader() {
		final List<String> messages = new ArrayList<>();

		final boolean isPlayer = this.getAudience().isPlayer();
		final Player player = isPlayer ? (Player) this.getAudience().getPlayer() : null;
		final PlayerCache cache = isPlayer ? PlayerCache.fromCached(player) : null;

		messages.add("&8" + CommonCore.chatLineSmooth());
		messages.add(this.getHeaderPrefix() + " " + Lang.plain("channel-header"));
		messages.add("");

		if (!Settings.Channels.ENABLED) {
			messages.add(" " + Lang.plain("channel-disabled"));

			return messages;
		}

		if (isPlayer && Channels.IGNORE_WORLDS.contains(player.getWorld().getName())) {
			messages.add(" " + Lang.plain("channel-disabled-world").replace("{world}", player.getWorld().getName()));

			return messages;
		}

		if (Channel.getChannels().isEmpty()) {
			messages.add(" " + Lang.plain("channel-no-channels"));

			return messages;
		}

		// Fill in the channels player is in
		{
			boolean atLeastOneJoined = false;

			for (final Channel channel : Channel.getChannels()) {
				final String name = channel.getName();
				final ChannelMode mode = isPlayer ? cache.getChannelMode(channel) : null;

				final String permissionPart = Permissions.Channel.JOIN.replace("{channel}", name);

				// If the player does not have write nor read perm and is not in channel, hide the channel
				if (!this.getAudience().hasPermission(permissionPart.replace("{mode}", "write")) && !this.getAudience().hasPermission(permissionPart.replace("{mode}", "read")) && mode == null)
					continue;

				SimpleComponent component = SimpleComponent.fromMiniNative("<gray> - " + (mode != null ? "<green>" : "<white>") + name + " ");

				if (mode == null) {
					if (this.getAudience().hasPermission(Permissions.Channel.JOIN.replace("{channel}", name).replace("{mode}", "write")))
						component = component
								.onHover(Lang.component("channel-tooltip-join"))
								.onClickRunCmd("/" + Settings.Channels.COMMAND_ALIASES.get(0) + " join " + name + " write");
				} else {
					if (this.getAudience().hasPermission(Permissions.Channel.LEAVE.replace("{channel}", name)))
						component = component
								.onHover(Lang.component("channel-tooltip-leave"))
								.onClickRunCmd("/" + Settings.Channels.COMMAND_ALIASES.get(0) + " leave " + name);
				}

				if (mode != null) {
					component = component.appendMiniNative("<dark_gray>(" + (mode == ChannelMode.WRITE ? "<gold>✎" : "<dark_green>▲") + "<dark_gray>) ");

					if (this.getAudience().hasPermission(Permissions.Channel.JOIN.replace("{channel}", name).replace("{mode}", mode.getKey())))
						component = component
								.onHover(Lang.component("channel-tooltip-switch-to-" + (mode == ChannelMode.WRITE ? "read" : "write")))
								.onClickRunCmd("/" + Settings.Channels.COMMAND_ALIASES.get(0) + " join " + name + " " + (mode == ChannelMode.WRITE ? "read" : "write"));
				}

				messages.add(component.toMini(null));
				atLeastOneJoined = true;
			}

			if (!atLeastOneJoined)
				messages.add(" &7- &o" + Lang.plain("part-none"));
		}

		messages.add("&8" + CommonCore.chatLineSmooth());

		return messages;
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommandGroup#registerSubcommands()
	 */
	@Override
	protected void registerSubcommands() {
		this.registerSubcommand(ChannelSubCommand.class);
	}

	/**
	 * A helper class used by all channel commands
	 */
	public static abstract class ChannelSubCommand extends GenericSubCommand {

		protected ChannelSubCommand(final String sublabel) {
			super(ChannelCommands.getInstance(), sublabel);
		}

		@Override
		public final void onCommand() {
			if (!Channels.ENABLED)
				this.returnTell(Lang.component("channel-disabled"));

			if (this.isPlayer() && Channels.IGNORE_WORLDS.contains(this.getPlayer().getWorld().getName()))
				this.returnTell(Lang.component("channel-disabled-world", "world", this.getPlayer().getWorld().getName()));

			this.onChannelCommand();
		}

		/**
		 * Same as onCommand but we already checked if channels are enabled
		 */
		protected abstract void onChannelCommand();
	}
}
