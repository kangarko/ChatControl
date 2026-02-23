package org.mineacademy.chatcontrol.command;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.ChannelMode;
import org.mineacademy.chatcontrol.model.ChatControlProxyMessage;
import org.mineacademy.chatcontrol.model.PlayerMessageType;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.model.RuleType;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.chatcontrol.model.db.Database;
import org.mineacademy.chatcontrol.model.db.PlayerCache;
import org.mineacademy.chatcontrol.operator.Group;
import org.mineacademy.chatcontrol.operator.Groups;
import org.mineacademy.chatcontrol.operator.PlayerMessage;
import org.mineacademy.chatcontrol.operator.PlayerMessages;
import org.mineacademy.chatcontrol.operator.Rule;
import org.mineacademy.chatcontrol.operator.Rules;
import org.mineacademy.chatcontrol.operator.Tag;
import org.mineacademy.chatcontrol.operator.Tag.Type;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.ProxyUtil;
import org.mineacademy.fo.command.SharedBukkitCommandCore;
import org.mineacademy.fo.command.SimpleCommandCore;
import org.mineacademy.fo.exception.CommandException;
import org.mineacademy.fo.model.CompChatColor;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.platform.Platform;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.Lang;

import lombok.NonNull;

public interface SharedChatControlCommandCore extends SharedBukkitCommandCore {

	/**
	 * Return a channel by name or send error message to player if wrong name
	 *
	 * @param channelName
	 * @return
	 */
	default Channel findChannel(@NonNull final String channelName) {
		final Channel channel = Channel.findChannel(channelName);
		this.checkBoolean(channel != null, Lang.component("command-invalid-channel", "channel", channelName, "available", CommonCore.joinAnd(Channel.getChannelNames())));

		return channel;
	}

	/**
	 * Return channel mode by name or send error to player if wrong mode
	 *
	 * @param modeName
	 * @return
	 */
	default ChannelMode findMode(@NonNull final String modeName) {
		try {
			return ChannelMode.fromKey(modeName);

		} catch (final IllegalArgumentException ex) {
			this.returnTell(Lang.component("command-invalid-mode", "mode", modeName, "available", ChannelMode.values()));

			return null;
		}
	}

	/**
	 * Return a rule by the name or send error message to the player
	 *
	 * @param ruleName
	 * @return
	 */
	default Rule findRule(final String ruleName) {
		final List<Rule> namedRules = Rules.getInstance().getRulesWithName();
		final Rule rule = Rules.getInstance().findRule(ruleName);
		final String available = namedRules.isEmpty() ? Lang.plain("part-none") : CommonCore.join(namedRules, ", ", Rule::getName);

		this.checkBoolean(ruleName != null && !ruleName.isEmpty(), Lang.component("command-no-rule-name", "available", available));
		this.checkBoolean(rule != null, Lang.component("command-invalid-rule", "rule", ruleName, "available", available));

		return rule;
	}

	/**
	 * Parse rule type from name or send error message to the player
	 *
	 * @param typeName
	 * @return
	 */
	default RuleType findRuleType(final String typeName) {
		try {
			return RuleType.fromKey(typeName);

		} catch (final IllegalArgumentException ex) {
			this.returnTell(Lang.component("command-invalid-type", "type", "rule", "value", typeName, "available", RuleType.values()));

			return null;
		}
	}

	/**
	 * Return a rule group by the name or send error message to the player
	 *
	 * @param groupName
	 * @return
	 */
	default Group findRuleGroup(final String groupName) {
		final List<String> groups = Groups.getInstance().getGroupNames();
		final Group group = Groups.getInstance().findGroup(groupName);
		final String available = groups.isEmpty() ? Lang.plain("part-none") : CommonCore.join(groups);

		this.checkBoolean(groupName != null && !groupName.isEmpty(), Lang.component("command-no-group-name", "available", available));
		this.checkBoolean(group != null, Lang.component("command-invalid-group", "group", groupName, "available", available));

		return group;
	}

	/**
	 * Return a rule by the name or send error message to the player
	 *
	 * @param type
	 * @param groupName
	 * @return
	 */
	default PlayerMessage findMessage(final PlayerMessageType type, final String groupName) {
		final Collection<String> itemNames = PlayerMessages.getInstance().getMessageNames(type);
		final PlayerMessage items = PlayerMessages.getInstance().findMessage(type, groupName);
		final String available = itemNames.isEmpty() ? Lang.plain("part-none") : CommonCore.join(itemNames);

		this.checkBoolean(groupName != null && !groupName.isEmpty(), Lang.component("command-no-message-name", "available", available));
		this.checkBoolean(items != null, Lang.component("command-invalid-type", "type", "message", "value", groupName, "available", available));

		return items;
	}

	/**
	 * Parse rule type from name or send error message to the player
	 *
	 * @param typeName
	 * @return
	 */
	default PlayerMessageType findMessageType(final String typeName) {
		try {
			final PlayerMessageType type = PlayerMessageType.fromKey(typeName);

			if (!Settings.Messages.APPLY_ON.contains(type))
				throw new IllegalArgumentException();

			return type;

		} catch (final IllegalArgumentException ex) {
			this.returnTell(Lang.component("command-invalid-type", "type", "message", "value", typeName, "available", PlayerMessageType.values()));

			return null;
		}
	}

	/**
	 * Return the disk cache for name or if name is null and we are not console then return it for the sender
	 *
	 * @param nameOrNick
	 * @param syncCallback
	 *
	 * @throws CommandException
	 */
	default void pollDiskCacheOrSelf(@Nullable final String nameOrNick, final Consumer<PlayerCache> syncCallback) throws CommandException {

		if (nameOrNick == null) {
			this.checkBoolean(this.isPlayer(), Lang.component("command-console-missing-player-name"));

			syncCallback.accept(PlayerCache.fromCached(this.getPlayer()));
			return;
		}

		this.pollCache(nameOrNick, syncCallback);
	}

	/**
	 * Attempts to get a player cache by his name or nick from either
	 * data.db or database. Since this is a blocking operation, we have a synced
	 * callback here.
	 *
	 * @param nameOrNick
	 * @param syncCallback
	 */
	default void pollCache(final String nameOrNick, final Consumer<PlayerCache> syncCallback) {
		final SenderCache senderCache = SenderCache.from(this.getSender());

		// Prevent calling this again when loading
		senderCache.setQueryingDatabase(true);

		final Player entityContext = this.getSender() instanceof Player ? (Player) this.getSender() : null;

		PlayerCache.poll(nameOrNick, cache -> {
			handleCallbackCommand(this.getSender(),
					() -> {
						this.checkBoolean(cache != null, Lang.component("player-not-stored", "player", nameOrNick));

						syncCallback.accept(cache);

					}, () -> {
						senderCache.setQueryingDatabase(false);
					});
		}, entityContext);
	}

	/**
	 * Get certain information async and then process this information in the sync callback
	 *
	 * @param <T>
	 * @param asyncGetter
	 * @param syncCallback
	 */
	default <T> void syncCallback(final Supplier<T> asyncGetter, final Consumer<T> syncCallback) {
		final SenderCache senderCache = SenderCache.from(this.getSender());

		// Prevent calling this again when loading
		senderCache.setQueryingDatabase(true);

		// Run the getter async
		asyncCallbackCommand(this.getSender(), () -> {
			final T value = asyncGetter.get();

			// Then run callback on the main thread
			syncCallbackCommand(this.getSender(),
					() -> syncCallback.accept(value),
					() -> senderCache.setQueryingDatabase(false));

		}, () -> senderCache.setQueryingDatabase(false));
	}

	/**
	 * Handles callback for all caches on disk or database
	 *
	 * @param syncCallback
	 */
	default void pollCaches(final Consumer<List<PlayerCache>> syncCallback) {
		final SenderCache senderCache = SenderCache.from(this.getSender());

		this.tellInfo(Lang.component("command-compiling-data"));

		// Prevent calling this again when loading
		senderCache.setQueryingDatabase(true);

		final Player entityContext = this.getSender() instanceof Player ? (Player) this.getSender() : null;

		PlayerCache.pollAll(caches -> handleCallbackCommand(this.getSender(),
				() -> syncCallback.accept(caches),
				() -> senderCache.setQueryingDatabase(false)), entityContext);
	}

	/**
	 * Parse dog tag or send error message to the player
	 *
	 * @param name
	 * @return
	 */
	default Tag.Type findTag(final String name) {
		try {
			final Tag.Type tag = Tag.Type.fromKey(name);

			if (!Settings.Tag.APPLY_ON.contains(tag))
				throw new IllegalArgumentException();

			return tag;

		} catch (final IllegalArgumentException ex) {
			this.returnTell(Lang.component("command-invalid-tag", "tag", name, "available", Common.join(Settings.Tag.APPLY_ON)));

			return null;
		}
	}

	/**
	 * Special method used for nicks/prefixes/suffixes
	 *
	 * @param type
	 * @param cache
	 * @param tag
	 */
	default void setTag(final Tag.Type type, final PlayerCache cache, String tag) {
		this.checkBoolean(!tag.isEmpty(), Lang.component("command-tag-empty"));

		final boolean remove = "off".equals(tag) || tag.equals(cache.getPlayerName());
		final boolean self = this.getAudience().getName().equals(cache.getPlayerName());

		this.checkBoolean(!remove || cache.hasTag(type), Lang.component("command-tag-no-tag-" + (self ? "self" : "other"),
				"target", cache.getPlayerName(),
				"type", type.getKey()));

		final char firstChar = tag.charAt(0);
		final char lastChar = tag.charAt(tag.length() - 1);

		if (firstChar == '"' || firstChar == '\'')
			tag = tag.substring(1);

		if (lastChar == '"' || lastChar == '\'')
			tag = tag.substring(0, tag.length() - 1);

		// Prevent "legacy chars found" error if not parsed
		if (!Settings.Colors.APPLY_ON.contains(type.getColorType()))
			tag = CompChatColor.convertLegacyToMini(tag, true);

		final String colorlessTag = SimpleComponent.fromMiniAmpersand(tag).toPlain();

		if (type == Type.NICK)
			this.checkBoolean(!tag.contains(" "), Lang.component("command-tag-no-spaces"));

		this.checkBoolean(!colorlessTag.isEmpty(), Lang.component("command-tag-no-colors-only"));

		if (type == Tag.Type.NICK && !colorlessTag.equalsIgnoreCase(this.getAudience().getName())) {
			final String newTagFinal = tag;

			asyncCallbackCommand(this.getSender(), () -> {

				this.checkBoolean(colorlessTag.length() <= Settings.Tag.MAX_NICK_LENGTH, Lang.component("command-tag-exceeded-length",
						"length", colorlessTag.length(),
						"max", Settings.Tag.MAX_NICK_LENGTH));

				if (Settings.Tag.NICK_DISABLE_IMPERSONATION) {
					final OfflinePlayer offline = Bukkit.getOfflinePlayer(colorlessTag);

					this.checkBoolean(!offline.hasPlayedBefore(), Lang.component("command-tag-no-impersonation"));
				}

				if (!cache.getPlayerName().equals(this.getSender().getName()))
					this.checkBoolean(!Database.getInstance().isNickUsed(colorlessTag), Lang.component("command-tag-already-used"));

				syncCallbackCommand(this.getSender(), () -> {
					this.setTag0(type, cache, newTagFinal, remove, self);

				}, () -> {
					// No final callback
				});

			}, () -> {
				// No final callback
			});
		}

		else
			this.setTag0(type, cache, tag, remove, self);
	}

	default void setTag0(final Tag.Type type, final PlayerCache cache, final String newTag, final boolean remove, final boolean self) {
		cache.setTag(type, remove ? null : newTag);

		final Player cachePlayer = cache.toPlayer();

		if (cachePlayer != null)
			Players.setTablistName(WrappedSender.fromPlayerCaches(cachePlayer, cache, SenderCache.from(cachePlayer)));

		final SimpleComponent component = Lang.component("command-tag-" + (remove ? "remove" : "set") + (self ? "-self" : ""),
				"type", type.getKey(),
				"tag", newTag,
				"target", cache.getPlayerName());

		this.tellInfo(component);

		// Notify proxy so that players connected on another server get their tablist updated
		if (Settings.Proxy.ENABLED)
			ProxyUtil.sendPluginMessage(ChatControlProxyMessage.TAG_UPDATE, Platform.getCustomServerName(), cache.getUniqueId(), cache.toDataSectionOfMap(), component);
	}

	/**
	 * Makes all other servers on the network get database updated for
	 * the given player cache
	 *
	 * @param cache
	 * @throws CommandException
	 */
	default void updateProxyData(final PlayerCache cache) throws CommandException {
		this.updateProxyData(cache, SimpleComponent.empty());
	}

	/**
	 * Makes all other servers on the network get database updated for
	 * the given player cache, sending the player the specified message
	 *
	 * @param cache
	 * @param message
	 * @throws CommandException
	 */
	default void updateProxyData(@NonNull final PlayerCache cache, @NonNull SimpleComponent message) throws CommandException {
		final Player messageTarget = cache.toPlayer();

		if (Settings.Proxy.ENABLED) {
			if (messageTarget != null && !message.isEmpty()) {
				Messenger.info(messageTarget, message);

				message = SimpleComponent.empty();
			}

			ProxyUtil.sendPluginMessage(ChatControlProxyMessage.DATABASE_UPDATE, Platform.getCustomServerName(), cache.getUniqueId(), cache.toDataSectionOfMap(), message);
		}
	}

	/**
	 * @see SimpleCommandCore#tellInfo(String)
	 *
	 * @param message
	 */
	void tellInfo(String message);

	/**
	 * @see SimpleCommandCore#tellInfo(SimpleComponent)
	 *
	 * @param message
	 */
	void tellInfo(SimpleComponent message);

	/*
	 * See below, but everything is wrapped and run async
	 */
	static void asyncCallbackCommand(final CommandSender sender, final Runnable callback, final Runnable finallyCallback) {
		Platform.runTaskAsync(() -> handleCallbackCommand(sender, callback, finallyCallback));
	}

	/*
	 * See below, but everything is wrapped and run on the main thread
	 */
	static void syncCallbackCommand(final CommandSender sender, final Runnable callback, final Runnable finallyCallback) {
		if (Remain.isFolia() && sender instanceof Player)
			Remain.runEntityTask((Player) sender, 0, () -> handleCallbackCommand(sender, callback, finallyCallback));
		else
			Platform.runTask(() -> handleCallbackCommand(sender, callback, finallyCallback));
	}

	/*
	 * Wraps the given callback in try-catch clause and sends all CommandExceptions to the sender,
	 * then runs the finally clause with the finallyCallback
	 */
	static void handleCallbackCommand(final CommandSender sender, final Runnable callback, final Runnable finallyCallback) {
		try {
			callback.run();

		} catch (final CommandException ex) {
			ex.sendErrorMessage(Platform.toPlayer(sender));

		} catch (final Throwable t) {
			Messenger.error(sender, Lang.component("command-error").replaceBracket("error", t.toString()));

			t.printStackTrace();

			throw t;

		} finally {
			try {
				finallyCallback.run();

			} catch (final Throwable t) {
				t.printStackTrace();
			}
		}
	}
}
