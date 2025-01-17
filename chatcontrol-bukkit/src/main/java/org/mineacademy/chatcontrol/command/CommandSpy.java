package org.mineacademy.chatcontrol.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.menu.SpyMenu;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Spy;
import org.mineacademy.chatcontrol.model.Spy.Type;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.settings.Lang;

public final class CommandSpy extends ChatControlCommand {

	public CommandSpy() {
		super(Settings.Spy.COMMAND_ALIASES);

		this.setMinArguments(1);
		this.setPermission(Permissions.Command.SPY);
		this.setUsage(Lang.component("command-spy-usage"));
		this.setDescription(Lang.component("command-spy-description"));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		final List<SimpleComponent> usages = new ArrayList<>();

		usages.add(Lang.component("command-spy-usages-1"));

		if (Settings.Channels.ENABLED && Settings.Spy.APPLY_ON.contains(Spy.Type.CHAT))
			usages.add(Lang.component("command-spy-usages-2"));

		usages.add(Lang.component("command-spy-usages-3"));

		return SimpleComponent.join(usages);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		final String param = this.args[0];

		if ("list".equals(param)) {

			this.pollCaches(caches -> {
				this.checkBoolean(!caches.isEmpty(), Lang.component("command-spy-no-stored"));

				final List<SimpleComponent> list = new ArrayList<>();

				for (final PlayerCache diskCache : caches) {
					final boolean spyingSomething = diskCache.isSpyingSomethingEnabled();
					final String playerName = diskCache.getPlayerName();

					if (spyingSomething)
						list.add(SimpleComponent
								.fromMiniNative(" <gray>- ")

								.appendMiniNative("<dark_gray>[<dark_red>X<dark_gray>]")
								.onHover(Lang.component("command-spy-list-tooltip", "player", playerName))
								.onClickRunCmd("/" + this.getLabel() + " off " + playerName)

								.appendMiniNative(" <white>" + playerName)
								.onHover(this.getSpyingStatus(diskCache))

						);
				}

				new ChatPaginator()
						.setFoundationHeader(Lang.legacy("command-spy-list-header"))
						.setPages(list)
						.send(this.audience);
			});

			return;
		}

		if ("toggle".equals(param)) {
			this.checkArgs(2, Lang.component("command-spy-toggle-no-type", "available", Settings.Spy.APPLY_ON));

			final Spy.Type type = this.findEnum(Spy.Type.class, this.args[1], condition -> Settings.Spy.APPLY_ON.contains(condition), Lang.component("command-invalid-type",
					"type", "spy type",
					"value", this.args[1],
					"available", Settings.Spy.APPLY_ON));

			this.checkPerm(Permissions.Spy.TYPE + type.getKey());

			final boolean isChat = type == Type.CHAT;

			Channel channel = null;

			if (isChat) {
				this.checkArgs(3, Lang.component("command-spy-toggle-no-channel", "available", Channel.getChannelNames()));

				channel = this.findChannel(this.args[2]);
			}

			final Channel finalChannel = channel;

			this.checkBoolean(this.isPlayer() || this.args.length == (isChat ? 4 : 3), Lang.component("command-console-missing-player-name"));

			this.pollCache(this.args.length == (isChat ? 4 : 3) ? this.args[isChat ? 3 : 2] : this.audience.getName(), cache -> {
				final boolean isSpying = isChat ? cache.isSpyingChannel(finalChannel) : cache.isSpying(type);

				if (isChat)
					cache.setSpyingChannel(finalChannel, !isSpying);
				else
					cache.setSpying(type, !isSpying);

				this.tellSuccess(Lang.component("command-spy-" + (isSpying ? "disable" : "enable"),
						"player", cache.getPlayerName(),
						"type", isChat ? Lang.component("command-spy-type-channel").appendPlain(" " + finalChannel.getName()) : type.getLangKey()));
			});

			return;
		}

		if (this.args.length > 2)
			this.returnInvalidArgs(this.joinArgs(3));

		this.checkBoolean(this.isPlayer() || this.args.length == 2, Lang.component("command-console-missing-player-name"));

		this.pollDiskCacheOrSelf(this.args.length == 2 ? this.args[1] : this.audience.getName(), cache -> {
			if ("status".equals(param)) {
				this.checkBoolean(cache.isSpyingSomething(), Lang.component("command-spy-no-spying", "player", cache.getPlayerName()));

				this.tellNoPrefix(Lang.component("command-spy-status-1", "player", cache.getPlayerName()));

				for (final SimpleComponent component : this.getSpyingStatus(cache))
					this.tellNoPrefix(component);

				this.tellNoPrefix(Lang.component("command-spy-status-2", "player", cache.getPlayerName()));
			}

			else if ("menu".equals(param)) {
				this.checkConsole();

				SpyMenu.showTo(cache, this.getPlayer());
			}

			else if ("off".equals(param)) {
				this.checkBoolean(cache.isSpyingSomethingEnabled(), Lang.component("command-spy-no-spying", "player", cache.getPlayerName()));

				cache.setSpyingOff();
				this.updateProxyData(cache);

				this.tellSuccess(Lang.component("command-spy-toggle-off", "player", cache.getPlayerName()));

			} else if ("on".equals(param)) {
				final boolean atLeastOne = cache.setSpyingOn(cache.toPlayer());
				this.updateProxyData(cache);

				if (atLeastOne)
					this.tellSuccess(Lang.component("command-spy-toggle-on", "player", cache.getPlayerName()));
				else
					this.tellError(Lang.component("command-spy-cannot-toggle-anything-no-perms", "perm", Permissions.Spy.TYPE + "{type}"));

			} else
				this.returnInvalidArgs(param);
		});
	}

	private List<SimpleComponent> getSpyingStatus(final PlayerCache diskCache) {
		final List<SimpleComponent> hover = new ArrayList<>();

		if (!diskCache.getSpyingSectors().isEmpty()) {
			hover.add(Lang.component("command-spy-status-sectors"));

			for (final Spy.Type type : diskCache.getSpyingSectors())
				if (type != Type.CHAT && Settings.Spy.APPLY_ON.contains(type))
					hover.add(SimpleComponent.fromMiniAmpersand(" <gray>-<white> " + type.getLangKey()));
		}

		if (Settings.Spy.APPLY_ON.contains(Spy.Type.CHAT))
			if (!diskCache.getSpyingChannels().isEmpty()) {
				if (!hover.isEmpty())
					hover.add(SimpleComponent.fromMiniNative("<reset>"));

				hover.add(Lang.component("command-spy-status-channel"));

				for (final String channelName : diskCache.getSpyingChannels())
					hover.add(SimpleComponent.fromMiniNative(" <gray>-<white> " + channelName));
			}

		return hover;
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (this.args.length == 1)
			return this.completeLastWord("status", "menu", "off", "toggle", "list");

		if (this.args.length == 2 && Arrays.asList("status", "menu", "off", "on").contains(this.args[0]))
			return this.completeLastWordPlayerNames();

		if (this.args.length > 1 && "toggle".equals(this.args[0])) {
			if (this.args.length == 2)
				return this.completeLastWord(Settings.Spy.APPLY_ON);

			if (this.args.length == 3)
				return "chat".equals(this.args[1]) && Settings.Spy.APPLY_ON.contains(Spy.Type.CHAT) ? this.completeLastWord(Channel.getChannelNames()) : this.completeLastWordPlayerNames();

			if (this.args.length == 4 && "chat".equals(this.args[1]))
				return this.completeLastWordPlayerNames();
		}

		return NO_COMPLETE;
	}
}
