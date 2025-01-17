package org.mineacademy.chatcontrol.command.chatcontrol;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.MainSubCommand;
import org.mineacademy.chatcontrol.model.Announce;
import org.mineacademy.chatcontrol.model.Announce.AnnounceType;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.ReflectionUtil;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.exception.CommandException;
import org.mineacademy.fo.model.CompToastStyle;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.settings.Lang;

import net.kyori.adventure.bossbar.BossBar;

public final class AnnounceSubCommand extends MainSubCommand {

	/**
	 * The pattern to match key:value pairs in the message
	 */
	private static final Pattern ANNOUNCE_PARAM_PATTERN = Pattern.compile("\\b[a-zA-Z]+:[a-zA-Z0-9_]+\\b(?!(?=[^<]*>))");

	public AnnounceSubCommand() {
		super("announce/a/broadcast/bc");

		this.setUsage(Lang.component("command-announce-usage"));
		this.setDescription(Lang.component("command-announce-description"));
		this.setMinArguments(1);
		this.setPermission(Permissions.Command.ANNOUNCE_TYPE.substring(0, Permissions.Command.ANNOUNCE_TYPE.length() - 1));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		final List<SimpleComponent> usages = new ArrayList<>();

		if (this.hasPerm(Permissions.Command.ANNOUNCE_TYPE + AnnounceType.CHAT.getKey()))
			usages.add(Lang.component("command-announce-usage-chat"));

		if (this.hasPerm(Permissions.Command.ANNOUNCE_TYPE + AnnounceType.IMAGE.getKey()))
			usages.add(Lang.component("command-announce-usage-image"));

		if (this.hasPerm(Permissions.Command.ANNOUNCE_TYPE + AnnounceType.TITLE.getKey()))
			usages.add(Lang.component("command-announce-usage-title"));

		if (this.hasPerm(Permissions.Command.ANNOUNCE_TYPE + AnnounceType.ACTIONBAR.getKey()))
			usages.add(Lang.component("command-announce-usage-actionbar"));

		if (this.hasPerm(Permissions.Command.ANNOUNCE_TYPE + AnnounceType.BOSSBAR.getKey()))
			usages.add(Lang.component("command-announce-usage-bossbar"));

		if (MinecraftVersion.atLeast(V.v1_12) && this.hasPerm(Permissions.Command.ANNOUNCE_TYPE + AnnounceType.TOAST.getKey()))
			usages.add(Lang.component("command-announce-usage-toast"));

		if (usages.isEmpty())
			usages.add(Lang.component("command-announce-usage-no-perms"));

		usages.add(Lang.component("command-announce-usage-footer"));

		return SimpleComponent.join(usages);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		final AnnounceType type = this.findParam(this.args[0]);
		this.checkPerm(Permissions.Command.ANNOUNCE_TYPE + type.getKey());

		if (type == AnnounceType.IMAGE)
			this.checkArgs(3, Lang.component("command-announce-usage-image"));
		else
			this.checkArgs(2, Lang.component("command-announce-no-type"));

		final SerializedMap params = new SerializedMap();
		final String message = this.mapParams(type, params, this.joinArgs(1));

		if (type != AnnounceType.IMAGE)
			this.checkBoolean(!message.isEmpty(), Lang.component("command-announce-empty"));

		Announce.send(this.getSender(), type, message, params);
	}

	/*
	 * Map chat key:value pairs parameters
	 */
	private String mapParams(final AnnounceType type, final SerializedMap map, String line) {
		final Matcher matcher = ANNOUNCE_PARAM_PATTERN.matcher(line);

		if (type == AnnounceType.IMAGE) {
			final int height = this.findNumber(2, Lang.component("command-announce-image-lines"));
			this.checkBoolean(height >= 2 && height <= 35, Lang.component("command-announce-invalid-image-height", "min", 2, "max", 35));

			final File imageFile = FileUtil.getFile("images/" + this.args[1]);
			this.checkBoolean(imageFile.exists(), Lang.component("command-announce-invalid-image", "file", imageFile.toPath().toString()));

			map.put("height", height);
			map.put("imageFile", imageFile.getName());

			line = CommonCore.joinRange(3, this.args);
		}

		while (matcher.find()) {
			final String word = matcher.group();
			final String[] split = word.split("\\:");

			if (split.length != 2)
				continue;

			final String key = split[0];
			Object value = CommonCore.joinRange(1, split);

			if (!type.isCompatible())
				continue;

			if ("server".equals(key)) {
				// ok
			}

			else if (type == AnnounceType.CHAT) {
				if ("type".equals(key))
					this.checkBoolean("raw".equals(value), Lang.component("command-announce-invalid-raw-type"));
				else
					this.returnTell(Lang.component("command-invalid-param-short", "param", word));
			}

			else if (type == AnnounceType.TITLE) {
				if ("stay".equals(key) || "fadein".equals(key) || "fadeout".equals(key))
					try {
						value = Integer.parseInt(value.toString());

					} catch (final NumberFormatException ex) {
						this.returnTell(Lang.component("command-announce-invalid-time-ticks"));
					}
				else
					this.returnTell(Lang.component("command-invalid-param-short", "param", word));
			}

			else if (type == AnnounceType.BOSSBAR) {
				if ("time".equals(key))
					try {
						value = Integer.parseInt(value.toString());

					} catch (final NumberFormatException ex) {
						this.returnTell(Lang.component("command-announce-invalid-time-seconds"));
					}
				else if ("color".equals(key))
					try {
						value = ReflectionUtil.lookupEnum(BossBar.Color.class, value.toString());

					} catch (final IllegalArgumentException ex) {
						this.returnTell(Lang.component("command-announce-invalid-key",
								"key", key,
								"value", value,
								"available", BossBar.Color.values()));
					}
				else if ("overlay".equals(key))
					try {
						value = ReflectionUtil.lookupEnum(BossBar.Overlay.class, value.toString());

					} catch (final IllegalArgumentException ex) {
						this.returnTell(Lang.component("command-announce-invalid-key",
								"key", key,
								"value", value,
								"available", BossBar.Overlay.values()));
					}
				else
					this.returnTell(Lang.component("command-invalid-param-short", "param", word));

			} else if (type == AnnounceType.TOAST) {
				if ("icon".equals(key)) {
					final CompMaterial material = CompMaterial.fromString(value.toString());
					this.checkNotNull(material, Lang.component("command-invalid-material", "material", key));

					value = material;

				} else if ("style".equals(key))
					try {
						value = CompToastStyle.fromKey(value.toString());

					} catch (final IllegalArgumentException ex) {
						this.returnTell(Lang.component("command-announce-invalid-key",
								"key", key,
								"value", value,
								"available", CompToastStyle.values()));
					}
				else
					this.returnTell(Lang.component("command-invalid-param-short", "param", word));

			} else if (type == AnnounceType.IMAGE) {
				// ok

			} else
				this.returnTell(Lang.component("command-invalid-param-short", "param", word));

			map.put(key, value);
			line = line.replace(word + (line.contains(word + " ") ? " " : ""), "").trim();
		}

		return line.trim();
	}

	/*
	 * Lookup a parameter by its label and automatically return on error
	 */
	private AnnounceType findParam(final String label) {
		for (final AnnounceType param : AnnounceType.values())
			if (param.getLabels().contains(label)) {
				this.checkBoolean(MinecraftVersion.atLeast(param.getMinimumVersion()), "Sending " + param.getKey() + " messages requires Minecraft " + param.getMinimumVersion() + " or greater.");

				return param;
			}

		this.returnTell(Lang.component("command-invalid-type", "type", "announcement", "value", label, "available", AnnounceType.getAvailableParams()));
		return null;
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		if (this.args.length == 1)
			return this.completeLastWord(AnnounceType
					.getAvailableParams()
					.stream()
					.filter(type -> this.hasPerm(Permissions.Command.ANNOUNCE_TYPE + type.getKey()))
					.collect(Collectors.toList()));

		if (this.args.length >= 2) {
			final String lastWord = this.args[this.args.length - 1];

			AnnounceType param;

			try {
				param = this.findParam(this.args[0]);

			} catch (final CommandException ex) {
				// Do not send tab complete error message to player
				return NO_COMPLETE;
			}

			if (!param.isCompatible())
				return NO_COMPLETE;

			if (lastWord.startsWith("server:"))
				return this.completeLastWord(SyncedCache.getServers());

			if (param == AnnounceType.TITLE)
				return this.completeLastWord("stay:", "fadein:", "fadeout:", "server:");

			else if (param == AnnounceType.BOSSBAR) {
				if (lastWord.startsWith("color:"))
					return this.completeLastWord(CommonCore.convertArrayToList(BossBar.Color.values(), color -> "color:" + color.toString().toLowerCase()));

				else if (lastWord.startsWith("overlay:"))
					return this.completeLastWord(CommonCore.convertArrayToList(BossBar.Overlay.values(), color -> "overlay:" + color.toString().toLowerCase()));

				return this.completeLastWord("time:", "color:", "overlay:", "server:");
			}

			else if (param == AnnounceType.TOAST) {
				if (lastWord.startsWith("icon:"))
					return this.completeLastWord(CommonCore.convertArrayToList(CompMaterial.values(), mat -> "icon:" + mat.toString()));

				else if (lastWord.startsWith("style:"))
					return this.completeLastWord(CommonCore.convertArrayToList(CompToastStyle.values(), mat -> "style:" + mat.toString()));

				return this.completeLastWord("icon:", "style:", "server:");
			}

			else if (param == AnnounceType.ACTIONBAR)
				return this.completeLastWord("server:");

			else if (param == AnnounceType.IMAGE)
				if (this.args.length == 2) {
					File file = FileUtil.getFile("images/" + this.args[1]);
					String[] files = file.list();

					if (files == null) {
						final String path = file.toPath().toString();
						final int lastDir = path.lastIndexOf('/');

						file = new File(lastDir == -1 ? path : path.substring(0, lastDir));
						files = file.list();
					}

					if (file != null)
						return this.completeLastWord(file.list());
				}

				else if (this.args.length == 3)
					return this.completeLastWord("6", "10", "20", "server:");
		}

		return NO_COMPLETE;
	}
}
