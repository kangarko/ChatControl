package org.mineacademy.chatcontrol.command.chatcontrol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.bukkit.metadata.FixedMetadataValue;
import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.MainSubCommand;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.LogType;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.model.db.Database;
import org.mineacademy.chatcontrol.model.db.Log;
import org.mineacademy.chatcontrol.model.db.Mail;
import org.mineacademy.chatcontrol.model.db.Mail.Recipient;
import org.mineacademy.chatcontrol.operator.Groups;
import org.mineacademy.chatcontrol.operator.Rules;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.SerializeUtilCore.Language;
import org.mineacademy.fo.TimeUtil;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.SimpleBook;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.platform.BukkitPlugin;
import org.mineacademy.fo.settings.Lang;
import org.mineacademy.fo.settings.SimpleSettings;

public final class LogSubCommand extends MainSubCommand {

	public LogSubCommand() {
		super("log/l");

		this.setMinArguments(1);
		this.setPermission(Permissions.Command.LOG);
		this.setUsage(Lang.component("command-log-usage"));
		this.setDescription(Lang.component("command-log-description"));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		final List<SimpleComponent> usages = new ArrayList<>();

		usages.add(Lang.component("command-log-usages-1"));

		if (Settings.Channels.ENABLED && Settings.Log.APPLY_ON.contains(LogType.CHAT))
			usages.add(Lang.component("command-log-usages-channel"));

		if ((Settings.PrivateMessages.ENABLED && Settings.Log.APPLY_ON.contains(LogType.PRIVATE_MESSAGE)) ||
				(Settings.Mail.ENABLED && Settings.Log.APPLY_ON.contains(LogType.MAIL)))
			usages.add(Lang.component("command-log-usages-to"));

		usages.add(Lang.component("command-log-usages-2"));

		if (!Settings.Rules.APPLY_ON.isEmpty())
			usages.add(Lang.component("command-log-usages-rule"));

		usages.add(Lang.component("command-log-usages-3"));

		return SimpleComponent.join(usages);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		final Database database = Database.getInstance();

		final LogType type = "all".equals(this.args[0]) ? null : this.findEnum(LogType.class, this.args[0], log -> Settings.Log.APPLY_ON.contains(log), Lang.component("command-invalid-log", "type", this.args[0], "available", Common.join(Settings.Log.APPLY_ON)));

		final String line = this.joinArgs(1);

		this.tellInfo(Lang.component("command-compiling-data"));

		// Read logs async, then send them to player on the main thread
		this.syncCallback(() -> database.getLogs(), logsRaw -> {

			// Remove those we do not want
			if (type != null)
				logsRaw.removeIf(log -> log.getType() != type);

			final List<Log> filteredLogs = new ArrayList<>();
			final SerializedMap params = this.mapParams(type, line);

			for (final Log log : logsRaw) {
				if (params.containsKey("player") && !params.getString("player").equalsIgnoreCase(log.getSender()))
					continue;

				if (params.containsKey("before") && (System.currentTimeMillis() - params.getLong("before")) < log.getDate())
					continue;

				if (params.containsKey("in") && (System.currentTimeMillis() - params.getLong("in")) > log.getDate())
					continue;

				if (params.containsKey("channel") && !params.getString("channel").equalsIgnoreCase(log.getChannelName()))
					continue;

				if (params.containsKey("label") && !params.getString("label").equalsIgnoreCase(log.getContent().split(" ")[0]))
					continue;

				if (params.containsKey("to") && !ValidCore.isInList(params.getString("to"), log.getReceivers()))
					continue;

				if ((!params.containsKey("rule") && log.getRuleName() != null) || (!params.containsKey("group") && log.getRuleGroupName() != null))
					continue;

				if ((params.containsKey("rule") && log.getRuleName() == null) || (params.containsKey("group") && log.getRuleGroupName() == null))
					continue;

				if (params.containsKey("rule") && "*".equals(params.getString("rule"))) {
					// ok
				} else if (params.containsKey("rule") && !params.getString("rule").equalsIgnoreCase(log.getRuleName()))
					continue;

				if (params.containsKey("group") && "*".equals(params.getString("group"))) {
					// ok
				} else if (params.containsKey("group") && !params.getString("group").equalsIgnoreCase(log.getRuleGroupName()))
					continue;

				filteredLogs.add(log);
			}

			final List<Log> logs = new ArrayList<>();
			this.copyLastEntries(filteredLogs, logs, 10_000);

			this.checkBoolean(!logs.isEmpty(), Lang.component("command-log-no-logs-" + (type != null ? "of-type" : "plain"), "type", type != null ? type.getLangKey() : ""));

			final List<SimpleComponent> pages = new ArrayList<>();

			for (final Log log : logs) {
				SimpleComponent component = SimpleComponent.fromMiniNative("<gray>" + TimeUtil.getFormattedDateMonth(log.getDate()));

				component = component.appendMiniNative(" <white>" + log.getSender());

				{ // Add hover
					final List<SimpleComponent> hover = new ArrayList<>();

					if (log.getChannelName() != null)
						hover.add(Lang.component("command-log-tooltip-channel", "channel", log.getChannelName()));

					if (log.getRuleName() != null)
						hover.add(Lang.component("command-log-tooltip-rule-" + (log.getRuleName().isEmpty() ? "unnamed" : "plain"), "rule", log.getRuleName()));

					if (log.getRuleGroupName() != null)
						hover.add(Lang.component("command-log-tooltip-group", "group", log.getRuleGroupName()));

					if (!hover.isEmpty())
						component = component.onHover(hover);
				}

				final List<String> receivers = log.getReceivers();

				if (!receivers.isEmpty())
					if (receivers.size() == 1)
						component = component.appendMiniNative(" <gold>-> <white>" + receivers.get(0));
					else
						component = component
								.appendMiniNative(" <gold>-> <white>")
								.appendPlain(Lang.numberFormat("case-receiver", receivers.size()))
								.onHoverLegacy(CommonCore.toArray(receivers));

				if (log.getType() == LogType.MAIL) {
					final Mail mail = Mail.deserialize(SerializedMap.fromObject(Language.JSON, log.getContent()));

					final List<SimpleComponent> hover = new ArrayList<>();
					hover.add(Lang.component("command-log-tooltip-recipients"));

					final List<String> recipientNames = database.getPlayerNamesSync(CommonCore.convertList(mail.getRecipients(), Recipient::getUniqueId));
					int amount = 0;

					for (final String recipientName : recipientNames) {
						if (++amount > 10) {
							hover.add(SimpleComponent.fromMiniNative("And " + (recipientNames.size() - 10) + " more..."));

							break;
						}

						hover.add(SimpleComponent.fromMiniNative("<gray>- <white>" + recipientName));
					}

					Collections.addAll(hover, Lang.component("command-log-tooltip-click-to-read"));

					final UUID uniqueId = mail.getBody().getUniqueId();
					SimpleComponent specialPart = SimpleComponent.empty();

					if (this.isPlayer()) {
						this.getPlayer().setMetadata("FoLogBook_" + uniqueId, new FixedMetadataValue(BukkitPlugin.getInstance(), mail.getBody()));

						specialPart = specialPart
								.append(Lang.component("command-log-sent-mail", "subject", mail.getSubject()))
								.onHover(hover)
								.onClickRunCmd("/" + SimpleSettings.MAIN_COMMAND_ALIASES.get(0) + " internal log-book " + uniqueId);
					}

					component = component.append(specialPart);
				}

				else if (log.getType() == LogType.SIGN)
					component = component
							.append(Lang.component("command-log-placed-sign"))
							.appendMiniNative(" <gold>[hover]")
							.onHoverLegacy(log.getContent().split("%FOLINES%"));

				else if (log.getType() == LogType.BOOK) {
					final String copy = log.getContent();

					// Avoiding parsing malformed JSON
					if (copy.contains("{") && copy.contains("}")) {
						final SimpleBook book = SimpleBook.deserialize(SerializedMap.fromObject(Language.JSON, copy));

						if (this.isPlayer()) {
							this.getPlayer().setMetadata("FoLogBook_" + book.getUniqueId(), new FixedMetadataValue(BukkitPlugin.getInstance(), book));

							component = component
									.append(Lang.component("command-log-wrote-book-" + (book.isSigned() ? "signed" : "unsigned"), "title", CommonCore.getOrDefaultStrict(book.getTitle(), "")))
									.onHover(Lang.component("command-log-wrote-book-tooltip"))
									.onClickRunCmd("/" + SimpleSettings.MAIN_COMMAND_ALIASES.get(0) + " internal log-book " + book.getUniqueId());
						} else
							component = component.appendMiniAmpersand(" wrote a book" + (book.getTitle() == null ? "" : " " + book.getTitle()) + ": " + book.getPages());
					}
				}

				else if (log.getType() == LogType.ANVIL) {
					final SerializedMap map = log.getContent().contains("{") ? SerializedMap.fromObject(Language.JSON, log.getContent()) : SerializedMap.fromArray("type", "Anvil", "name", Arrays.asList(log.getContent()));
					final List<String> hover = new ArrayList<>(map.getStringList("name"));

					if (map.containsKey("lore"))
						hover.addAll(map.getStringList("lore"));

					component = component.append(Lang.component("command-log-renamed-item"))
							.appendMiniAmpersand(map.getString("type"))
							.onHoverLegacy(CommonCore.toArray(hover));
				}

				else
					component = component.appendMiniAmpersand("<gray>: <white>" + log.getContent());

				pages.add(component);
			}

			this.checkBoolean(!pages.isEmpty(), Lang.component("command-log-no-logs-matched"));

			new ChatPaginator()
					.setFoundationHeader(Lang.legacy("command-log-listing-header-" + (type == null ? "all" : "of-type"),
							"type", type != null ? ChatUtil.capitalize(type.getLangKey()) : ""))
					.setPages(pages)
					.send(this.audience);
		});
	}

	private void copyLastEntries(final List<Log> rawLogs, final List<Log> logs, final int numEntries) {
		final int entriesToCopy = Math.min(numEntries, rawLogs.size());
		final int startIndex = rawLogs.size() - entriesToCopy;

		logs.clear();

		for (int i = startIndex; i < rawLogs.size(); i++)
			logs.add(rawLogs.get(i));
	}

	/*
	 * Map chat key:value pairs parameters
	 */
	private SerializedMap mapParams(@Nullable final LogType type, final String line) {
		final SerializedMap params = new SerializedMap();
		final String[] words = line.split(" ");

		for (final String word : words) {
			if (word.isEmpty())
				continue;

			this.checkBoolean(word.contains(":"), Lang.component("command-log-invalid-syntax", "value", word));

			final String[] split = word.split("\\:");
			final String key = split[0];
			Object value = CommonCore.joinRange(1, split);

			this.checkBoolean(!value.toString().isEmpty(), Lang.component("command-log-invalid-value", "value", key));

			if ("player".equals(key)) {
				// ok

			} else if ("before".equals(key) || "in".equals(key))
				try {
					value = TimeUtil.toMilliseconds(value.toString());

				} catch (final IllegalArgumentException ex) {
					this.returnTell(Lang.component("command-log-invalid-key", "key", key, "value", value));
				}
			else if ("channel".equals(key)) {
				this.checkBoolean(type == null || type == LogType.CHAT, Lang.component("command-log-cannot-use-channel"));

				final Channel channel = Channel.findChannel(value.toString());
				this.checkNotNull(channel, Lang.component("command-invalid-channel",
						"channel", value,
						"available", Channel.getChannelNames()));

				value = channel.getName();

			} else if ("label".equals(key)) {
				this.checkBoolean(type == null || type == LogType.COMMAND, Lang.component("command-log-cannot-use-label"));
				value = value.toString().charAt(0) == '/' ? value : "/" + value;

			} else if ("to".equals(key))
				this.checkBoolean(type == null || type == LogType.PRIVATE_MESSAGE, Lang.component("command-log-cannot-use-to"));

			else if ("rule".equals(key) || "group".equals(key)) {
				// ok

			} else
				this.returnTell(Lang.component("command-invalid-param-short", "param", word));

			params.put(key, value);
		}

		return params;
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (this.args.length == 1)
			return this.completeLastWord(CommonCore.joinLists(Arrays.asList(LogType.values()), Arrays.asList("all")));

		if (this.args.length > 1) {
			final String word = this.args[this.args.length - 1];

			if (word.contains(":") && !word.equals(":")) {
				final String key = word.split("\\:")[0];
				Collection<String> tab = new ArrayList<>();

				if ("player".equals(key))
					tab = Players.getPlayerNamesForTabComplete(this.getSender());

				else if ("before".equals(key) || "in".equals(key))
					tab = Arrays.asList("15m", "1h");

				else if ("channel".equals(key))
					tab = Channel.getChannelNames();

				else if ("label".equals(key))
					tab = Arrays.asList("tell", "me");

				else if ("rule".equals(key))
					tab = CommonCore.convertList(Rules.getInstance().getRulesWithName(), org.mineacademy.chatcontrol.operator.Rule::getName);

				else if ("group".equals(key))
					tab = Groups.getInstance().getGroupNames();

				if (!tab.isEmpty())
					return this.completeLastWord(CommonCore.convertList(tab, completed -> key + ":" + completed));

			} else
				return this.completeLastWord("player:", "before:", "in:", "channel:", "label:", "to:", "rule:", "group:");
		}

		return NO_COMPLETE;
	}
}
