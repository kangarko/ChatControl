package org.mineacademy.chatcontrol.command.chatcontrol;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.command.SharedChatControlCommandCore;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.annotation.AutoRegister;
import org.mineacademy.fo.command.PermsSubCommand;
import org.mineacademy.fo.command.SimpleCommand;
import org.mineacademy.fo.command.SimpleCommandGroup;
import org.mineacademy.fo.command.SimpleSubCommand;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.platform.BukkitPlugin;
import org.mineacademy.fo.settings.Lang;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Holds all /chc main commands
 */
@AutoRegister
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ChatControlCommands extends SimpleCommandGroup {

	/**
	 * The singleton of this class
	 */
	@Getter
	private final static SimpleCommandGroup instance = new ChatControlCommands();

	/**
	 * @see org.mineacademy.fo.command.SimpleCommandGroup#getHeaderPrefix()
	 */
	@Override
	protected String getHeaderPrefix() {
		return "<bold><gradient:#db0000:#fb00ff>";
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommandGroup#registerSubcommands()
	 */
	@Override
	protected void registerSubcommands() {
		this.registerSubcommand(MainSubCommand.class);
		this.registerSubcommand(new PermsSubCommand(Permissions.class));
		this.registerDefaultSubcommands();
	}

	// ------------------------------------------------------------------------------------------------------------
	// Classes
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Represents the core for a bunch of other commands having same flags
	 * such as -proxy -silent etc.
	 */
	public static abstract class CommandFlagged extends MainSubCommand {

		protected CommandFlagged(final String label, final SimpleComponent usage, final SimpleComponent description) {
			super(label);

			this.setUsage(usage);
			this.setDescription(description);
		}

		@Override
		public final void onCommand() {
			final List<String> params = Arrays.asList(this.args);

			final boolean console = params.contains("-console") || params.contains("-c");
			final boolean silent = params.contains("-silent") || params.contains("-s");
			final boolean anonymous = params.contains("-anonymous") || params.contains("-a");
			final boolean force = params.contains("-force") || params.contains("-f");

			final String reason = String.join(" ", params.stream().filter(param -> param.charAt(0) != '-').collect(Collectors.toList()));

			if (console && (silent || anonymous || force))
				this.returnTell(Lang.component("command-clear-no-arguments-while-console"));

			if (!console && !silent && !anonymous && !force && !params.isEmpty() && reason.isEmpty())
				this.returnInvalidArgs(this.joinArgs(1));

			if (silent && !reason.isEmpty())
				this.returnTell(Lang.component("command-no-reason-while-silent"));

			if (silent && anonymous)
				this.returnTell(Lang.component("command-no-silent-and-anonymous"));

			this.execute(console, anonymous, silent, force, reason);
		}

		/**
		 * Execute this command
		 *
		 * @param console
		 * @param anonymous
		 * @param silent
		 * @param forced
		 *
		 * @param reason
		 */
		protected abstract void execute(boolean console, boolean anonymous, boolean silent, boolean forced, String reason);

		/**
		 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
		 */
		@Override
		protected List<String> tabComplete() {
			return NO_COMPLETE;
		}
	}

	/**
	 * Represents the foundation for plugin commands
	 */
	public static abstract class ChatControlCommand extends SimpleCommand implements SharedChatControlCommandCore {

		protected ChatControlCommand(final String label) {
			super(label);
		}

		protected ChatControlCommand(final List<String> labels) {
			super(labels);
		}

		/**
		 * @see org.mineacademy.fo.command.SimpleCommand#completeLastWordPlayerNames()
		 */
		@Override
		protected final List<String> completeLastWordPlayerNames() {
			return CommonCore.tabComplete(this.getLastArg(), Players.getPlayerNamesForTabComplete(this.getSender()));
		}

		public final List<String> completeLastWordPlayerNames(boolean includeNetwork) {
			return CommonCore.tabComplete(this.getLastArg(), Players.getPlayerNamesForTabComplete(this.getSender(), !includeNetwork));
		}

		/**
		 * @see org.mineacademy.fo.command.SimpleCommand#findPlayerInternal(java.lang.String)
		 */
		@Override
		public final Player findPlayerInternal(final String name) {
			return Players.findPlayer(name);
		}
	}

	/**
	 * Represents the foundation for plugin commands
	 * used for /channel and /chc subcommands
	 */
	public static abstract class MainSubCommand extends GenericSubCommand {

		protected MainSubCommand(final String sublabel) {
			super(BukkitPlugin.getInstance().getDefaultCommandGroup(), sublabel);
		}
	}

	/**
	 * Represents the foundation for plugin commands
	 * used for /channel and /chc subcommands
	 */
	public static abstract class GenericSubCommand extends SimpleSubCommand implements SharedChatControlCommandCore {

		protected GenericSubCommand(final SimpleCommandGroup group, final String sublabel) {
			super(group, sublabel);
		}

		/**
		 * @see org.mineacademy.fo.command.SimpleCommand#completeLastWordPlayerNames()
		 */
		@Override
		protected final List<String> completeLastWordPlayerNames() {
			return CommonCore.tabComplete(this.getLastArg(), Players.getPlayerNamesForTabComplete(this.getSender()));
		}

		/**
		 * @see org.mineacademy.fo.command.SimpleCommand#findPlayerInternal(java.lang.String)
		 */
		@Override
		public final Player findPlayerInternal(final String name) {
			return Players.findPlayer(name);
		}
	}
}
