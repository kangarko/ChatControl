package org.mineacademy.chatcontrol.command.chatcontrol;

import java.util.ArrayList;
import java.util.List;

import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.MainSubCommand;
import org.mineacademy.chatcontrol.model.Migrator;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.operator.Tag;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.settings.Lang;

public final class TagSubCommand extends MainSubCommand {

	public TagSubCommand() {
		super("tag");

		this.setMinArguments(1);
		this.setPermission(Permissions.Command.TAG_ADMIN);
		this.setUsage(Lang.component("command-tag-admin-usage"));
		this.setDescription(Lang.component("command-tag-admin-description"));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		return Lang.component("command-tag-admin-usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		this.checkBoolean(!Settings.Tag.APPLY_ON.isEmpty(), Lang.component("command-tag-disabled"));

		if ("list".equals(this.args[0])) {
			this.checkUsage(this.args.length <= 2);

			final Tag.Type type = this.args.length == 2 ? this.findTag(this.args[1]) : null;
			final List<SimpleComponent> list = new ArrayList<>();

			this.pollCaches(caches -> {

				for (final PlayerCache cache : caches) {
					final String playerName = cache.getPlayerName();

					// Has no tags what so evar or doesnt hath tags we did giventh
					if (cache.getTags().isEmpty() || (type != null && !cache.hasTag(type)))
						continue;

					final String nick = Settings.Tag.APPLY_ON.contains(Tag.Type.NICK) ? cache.getTag(Tag.Type.NICK) : null;
					final String prefix = Settings.Tag.APPLY_ON.contains(Tag.Type.PREFIX) ? cache.getTag(Tag.Type.PREFIX) : null;
					final String suffix = Settings.Tag.APPLY_ON.contains(Tag.Type.SUFFIX) ? cache.getTag(Tag.Type.SUFFIX) : null;

					SimpleComponent component = SimpleComponent.fromMiniNative(" <gray>-<white> ");

					if (prefix != null)
						component = component
								.appendMiniAmpersand(prefix + " ")
								.onHover(Lang.component("command-tag-admin-tooltip-remove", "type", "prefix", "player", playerName))
								.onClickRunCmd("/" + this.getLabel() + " " + this.getSublabel() + " prefix " + playerName + " off");

					component = component.appendMiniAmpersand((nick != null ? nick : playerName) + " ");

					if (nick != null)
						component = component
								.onHover(Lang.component("command-tag-admin-tooltip-remove", "type", "nick", "player", playerName))
								.onClickRunCmd("/" + this.getLabel() + " " + this.getSublabel() + " nick " + playerName + " off");

					if (suffix != null)
						component = component
								.appendMiniAmpersand(suffix + " ")
								.onHover(Lang.component("command-tag-admin-tooltip-remove", "type", "suffix", "player", playerName))
								.onClickRunCmd("/" + this.getLabel() + " " + this.getSublabel() + " suffix " + playerName + " off");

					if (nick != null)
						component = component.appendMiniNative("<gray>(" + playerName + ")");

					list.add(component);
				}

				this.checkBoolean(!list.isEmpty(), Lang.component("command-tag-no-tags"));

				new ChatPaginator()
						.setFoundationHeader(Lang.legacy("command-tag-admin-list"))
						.setPages(list)
						.send(this.audience);
			});

			return;
		}

		this.checkArgs(2, Lang.component("command-tag-admin-invalid-params"));
		final Tag.Type type = this.findTag(this.args[0]);

		this.pollCache(this.args[1], cache -> {
			String tag = this.joinArgs(2);

			if (tag.contains("&#"))
				tag = Migrator.convertAmpersandToMiniHex(tag);

			if (tag.contains("#"))
				tag = Migrator.convertHexToMiniHex(tag);

			if (tag == null || tag.isEmpty()) {
				this.tellInfo(Lang.component("command-tag-admin-status",
						"player", cache.getPlayerName(),
						"possessive_form", Lang.component("player-possessive-form", "player", cache.getPlayerName()),
						"type", type.getKey(),
						"tag", CommonCore.getOrDefault(cache.getTag(type), Lang.plain("part-none"))));

				return;
			}

			this.setTag(type, cache, tag);
		});
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (this.args.length == 1)
			return this.completeLastWord(Settings.Tag.APPLY_ON, "list");

		if (this.args.length == 2)
			if ("list".equals(this.args[0]))
				return this.completeLastWord(Settings.Tag.APPLY_ON);
			else
				return this.completeLastWordPlayerNames();

		if (this.args.length == 3 && !"list".equals(this.args[0]))
			return this.completeLastWord("off");

		return NO_COMPLETE;
	}
}
