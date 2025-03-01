package org.mineacademy.chatcontrol.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.mineacademy.chatcontrol.model.Packets.RemoveMode;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.SerializeUtilCore;
import org.mineacademy.fo.SerializeUtilCore.Language;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.exception.FoScriptException;
import org.mineacademy.fo.model.ChatImage;
import org.mineacademy.fo.model.CompChatColor;
import org.mineacademy.fo.model.ConfigSerializable;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.JavaScriptExecutor;
import org.mineacademy.fo.model.RequireVariable;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.model.Variables.ToLegacyMode;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.ConfigItems;
import org.mineacademy.fo.settings.YamlConfig;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Represents a single chat format
 */
public final class Format extends YamlConfig {

	/**
	 * A list of all loaded formats
	 */
	private static final ConfigItems<Format> loadedFormats = ConfigItems.fromFolder("formats", Format.class);

	/**
	 * The name of the format, or the entire format for legacy
	 */
	@Getter
	private final String name;

	/**
	 * If true, each format part will being on a new line automatically.
	 */
	private boolean newLinePerPart = false;

	/**
	 * The format parts
	 */
	private List<Part> parts = new ArrayList<>();

	/*
	 * Construct a new format (called automatically when loading from a file)
	 */
	private Format(final String name) {
		this(name, false);
	}

	/*
	 * Construct a new format (called manually)
	 */
	private Format(final String name, final boolean legacy) {
		this.name = name;

		if (!legacy)
			this.loadAndExtract("prototype/format.yml", "formats/" + name + ".yml");

		this.setUncommentedSections(Arrays.asList("Parts"));
	}

	/**
	 * @see org.mineacademy.fo.settings.YamlConfig#onLoad()
	 */
	@Override
	protected void onLoad() {
		this.newLinePerPart = this.getBoolean("New_Line_Per_Part", false);

		for (final Entry<String, Object> entry : this.getMap("Parts", String.class, Object.class).entrySet()) {
			if (entry.getValue() instanceof String) {
				new IllegalArgumentException(this.getFileName() + " has invalid format part in Parts." + entry.getKey() + " - it cannot be a string! See https://github.com/kangarko/ChatControl/wiki/formats for configuration.").printStackTrace();

			} else
				try {
					this.parts.add(Part.deserialize(SerializedMap.fromObject(entry.getValue()), this.getFile().getName(), entry.getKey()));

				} catch (final FoException ex) {
					ex.printStackTrace();
				}
		}
	}

	@Override
	public void onSave() {
		throw new UnsupportedOperationException("Cannot save formats.");
	}

	/**
	 * Compile the format for the given message and his message
	 *
	 * @param wrapped
	 *
	 * @return
	 */
	public SimpleComponent build(final WrappedSender wrapped) {
		return this.build(wrapped, new HashMap<>());
	}

	/**
	 * Compile the format for the given message and his message, and insert given variables
	 *
	 * @param sender
	 * @param placeholders
	 * @return
	 */
	public SimpleComponent build(final WrappedSender sender, @NonNull final Map<String, Object> placeholders) {
		SimpleComponent component = SimpleComponent.empty();
		UUID messageId;
		int index = 0;

		if (!placeholders.containsKey("sender")) {
			placeholders.put("sender", sender == null ? "" : sender.getName());
			placeholders.put("sender_name", sender == null ? "" : sender.getName());
		}

		if (sender != null && sender.isPlayer())
			for (final Map.Entry<String, Object> data : sender.getPlayerCache().getRuleData().entrySet())
				placeholders.put("data_" + data.getKey(), SerializeUtilCore.serialize(Language.YAML, data.getValue()).toString());

		if (!placeholders.containsKey("message_uuid")) {
			messageId = UUID.randomUUID();

			placeholders.put("message_uuid", messageId.toString());

		} else {
			final Object obj = placeholders.get("message_uuid");

			messageId = obj instanceof UUID ? (UUID) obj : UUID.fromString(obj.toString());
		}

		final Variables variables = Variables.builder(sender != null ? sender.getAudience() : null).cache(true).placeholders(placeholders);

		for (final Part part : this.parts) {
			SimpleComponent partComponent = null;

			try {
				partComponent = part.build(sender, variables);
			} catch (final FoScriptException ex) {
				ex.printStackTrace();
			}

			if (partComponent != null) {
				component = component.append(partComponent);

				if (this.newLinePerPart && index++ != this.parts.size() - 1)
					component = component.append(SimpleComponent.empty().appendNewLine());
			}
		}

		// Add the secret remove code at the front of the message
		final Component removeIdentifier = Component.text(RemoveMode.SPECIFIC_MESSAGE.getPrefix() + "_" + messageId + (sender != null && sender.isPlayer() ? " " + RemoveMode.ALL_MESSAGES_FROM_SENDER.getPrefix() + "_" + sender.getUniqueId() : ""));

		return component.append(Component.empty().hoverEvent(removeIdentifier));
	}

	// ------–------–------–------–------–------–------–------–------–------–------–------–
	// Classes
	// ------–------–------–------–------–------–------–------–------–------–------–------–

	/**
	 * Represents a part of a format.
	 */
	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	private final static class Part implements ConfigSerializable {

		/**
		 * The parent format name
		 */
		private final String formatName;

		/**
		 * The name of this format option
		 */
		@Getter
		private final String name;

		/**
		 * The message this format prints to the chat
		 */
		private List<String> messages;

		/**
		 * The permission the sender must have to show the part
		 */
		private String senderPermission;

		/**
		 * The JavaScript condition that must return true to show to part
		 */
		private String senderCondition;

		/**
		 * The variable that must equal the given value to show this part
		 */
		private RequireVariable senderRequireVariable;

		/**
		 * The permission receiver must have to see the part
		 */
		private String receiverPermission;

		/**
		 * The JavaScript condition that must return true to show to part for receiver
		 */
		private String receiverCondition;

		/**
		 * The variable that must equal the given value to show this part
		 */
		private String receiverRequireVariable;

		/**
		 * The hover text or null if not set split by \n
		 */
		private String hoverText;

		/**
		 * The JavaScript pointing to a particular {@link ItemStack}
		 */
		private String hoverItem;

		/**
		 * What URL should be opened on click? Null if none
		 */
		private String openUrl;

		/**
		 * What command should be suggested on click? Null if none
		 */
		private String suggestCommand;

		/**
		 * What command should be run on click? Null if none
		 */
		private String runCommand;

		/**
		 * What text to insert into the chat? Null if none
		 */
		private String insertion;

		/**
		 * What text to copy to clipboard? Null if none
		 */
		private String copyToClipboard;

		/**
		 * Gradient from-to colors
		 */
		private Tuple<CompChatColor, CompChatColor> gradient;

		/**
		 * The image file name in images/ folder
		 */
		private String imageFile;

		/**
		 * The image player name, placeholder can be used here
		 */
		private String imageHead;

		/**
		 * The remote URL to get the image (blocking!)
		 */
		private String imageUrl;

		/**
		 * The image height
		 */
		private Integer imageHeight;

		/**
		 * The image type
		 */
		private ChatImage.FillerCharacter imageFillterCharacter;

		/**
		 * Turn this class into a saveable format to the file
		 */
		@Override
		public SerializedMap serialize() {
			final SerializedMap map = new SerializedMap();

			map.put("Message", this.messages.size() == 1 ? this.messages.get(0) : this.messages);
			map.putIfExists("Sender_Permission", this.senderPermission);
			map.putIfExists("Sender_Condition", this.senderCondition);
			map.putIfExists("Sender_Variable", this.senderRequireVariable != null ? this.senderRequireVariable.getVariable() + " " + this.senderRequireVariable.getRequiredValue() : null);
			map.putIfExists("Receiver_Permission", this.receiverPermission);
			map.putIfExists("Receiver_Condition", this.receiverCondition);
			map.putIfExists("Receiver_Variable", this.receiverRequireVariable);
			map.putIfExists("Hover", this.hoverText != null && !this.hoverText.isEmpty() ? this.hoverText : null);
			map.putIfExists("Hover_Item", this.hoverItem);
			map.putIfExists("Open_Url", this.openUrl);
			map.putIfExists("Suggest_Command", this.suggestCommand);
			map.putIfExists("Run_Command", this.runCommand);
			map.putIfExists("Insertion", this.insertion);
			map.putIfExists("Copy_To_Clipboard", this.copyToClipboard);
			map.putIfExists("Gradient", this.gradient != null ? this.gradient.getKey().toSaveableString() + " - " + this.gradient.getValue().toSaveableString() : null);
			map.putIfExists("Image_File", this.imageFile);
			map.putIfExists("Image_Head", this.imageHead);
			map.putIfExists("Image_Url", this.imageUrl);
			map.putIfExists("Image_Height", this.imageHeight);
			map.putIfExists("Image_Type", this.imageFillterCharacter);

			return map;
		}

		/**
		 * Builds this format part into a component.
		 *
		 * @param sender
		 * @param variables
		 * @return
		 */
		public SimpleComponent build(final WrappedSender sender, final Variables variables) {

			// Ugly hack for channel name to avoid having to parse variables in variables, we just hardcode support for this one
			final String channelName = (String) variables.placeholders().getOrDefault("channel", "");

			// Replace ItemsAdder inside the {message} placeholder
			if (variables.placeholders().containsKey("message") && sender != null) {
				final Object messageRaw = variables.placeholders().get("message");

				if (messageRaw instanceof SimpleComponent) {
					SimpleComponent component = (SimpleComponent) messageRaw;

					component = HookManager.replaceFontImages(sender.getPlayer(), component);
					variables.placeholder("message", component);

				} else if (messageRaw instanceof String) {
					String message = (String) messageRaw;

					message = HookManager.replaceFontImagesLegacy(sender.getPlayer(), message);
					variables.placeholder("message", message);
				} else
					throw new FoException("Invalid message type: " + messageRaw.getClass() + " in " + this);
			}

			if (this.senderPermission != null && sender != null && !sender.hasPermission(this.senderPermission))
				return null;

			variables.toLegacyMode(ToLegacyMode.PLAIN);

			if (this.senderCondition != null) {
				final String replacedScript = variables.replaceLegacy(this.senderCondition.replace("{channel}", channelName));

				try {
					if (sender == null)
						return null;

					final Object result = JavaScriptExecutor.run(replacedScript, sender.getAudience());

					if (result != null) {
						ValidCore.checkBoolean(result instanceof Boolean, "JavaScript Sender_Condition must return boolean not " + (result == null ? "null" : result.getClass()) + " in format " + this.formatName + "." + this.name);

						if (!((boolean) result))
							return null;
					}

				} catch (final FoScriptException ex) {
					CommonCore.logFramed(
							"Error parsing Sender_Condition in format!",
							"Path: " + this.formatName + "." + name + " (line " + ex.getErrorLine() + ")",
							"Sender: " + (sender != null ? sender.getName() : "null"),
							"Raw code: " + this.senderCondition,
							"Executed code: " + replacedScript,
							"Error: " + ex.getMessage(),
							"",
							"Check that Sender_Conditon is a JavaScript",
							"code returning a valid Boolean!");

					throw ex;
				}
			}

			if (this.senderRequireVariable != null)
				if (!this.senderRequireVariable.matches(value -> variables.replaceLegacy(value.replace("{channel}", channelName))))
					return null;

			variables.toLegacyMode(ToLegacyMode.MINI);

			// Support chat images
			final ChatImage image = ChatImage.builder();

			if (this.imageHeight != null) {
				if (this.imageHeight < 2) {
					Common.logFramed("Image height cannot be less than 2. Setting to 2. Format: " + this.formatName + "." + this.name);

					this.imageHeight = 2;
				}

				image.height(this.imageHeight);
			}

			if (this.imageFillterCharacter != null)
				image.fillerCharacter(this.imageFillterCharacter);

			if (this.imageHead != null)
				try {
					image.drawFromHead(variables.replaceLegacy(this.imageHead));

				} catch (final IOException | NullPointerException ex) {
					CommonCore.logTimed(60 * 30, "(This error only shows every 30min) Unable to look up player head from head: " + this.imageHead + ". Error: " + ex.toString() + " Format part: " + this);
				}

			if (this.imageUrl != null)
				try {
					image.drawFromUrl(variables.replaceLegacy(this.imageUrl));

				} catch (final IOException | NullPointerException ex) {
					CommonCore.logTimed(60 * 30, "(This error only shows every 30min) Unable to look up player head from URL: " + this.imageUrl + ". Error: " + ex.toString() + " Format part: " + this);
				}

			if (this.imageFile != null)
				try {
					image.drawFromFile(FileUtil.getFile("images/" + this.imageFile));

				} catch (final IOException | NullPointerException ex) {
					CommonCore.logTimed(60 * 30, "(This error only shows every 30min) Unable to look up player head from file: " + this.imageFile + ". Error: " + ex.toString() + " Format part: " + this);
				}

			// Get the actual message, support multilineň
			final List<String> textLines = new ArrayList<>();

			// Compile the list
			for (String line : this.messages) {

				// Replace ItemsAdder in the actual format part Message key
				if (sender != null)
					line = HookManager.replaceFontImagesLegacy(sender.getPlayer(), line);

				line = variables.replaceLegacy(line);

				if (line.startsWith("<center>"))
					textLines.add(ChatUtil.center(line.substring(8).trim()));
				else
					textLines.add(line);
			}

			String message = image.hasLines() ? String.join("\n", image.toString(textLines)) : String.join("\n", textLines);

			// Use MiniMessage for gradients
			if (this.gradient != null) {
				final String from = this.gradient.getKey().asHexString();
				final String to = this.gradient.getValue().asHexString();

				// Remove alpha channels
				message = "<gradient:#" + from.substring(3) + ":#" + to.substring(3) + ">" + message + "</gradient>";
			}

			// Adventure bug workaround: The Componennt#replaceText() does not work in gradients, hover, click and other events,
			// so we need to replace variables at the legacy level.
			{
				final List<String> parts = new ArrayList<>();
				int start = 0;

				while (true) {
					final int from = message.indexOf("<gradient:", start);

					if (from == -1)
						break;

					final int to = message.indexOf(">", from);

					if (to == -1)
						break;

					final String beforeGradient = message.substring(start, from);
					final String gradient = message.substring(from, to + 1);

					final int end = message.indexOf("</gradient>", to);

					if (end == -1)
						break;

					final String text = message.substring(to + 1, end);

					parts.add(beforeGradient + gradient + variables.replaceLegacy(text));
					start = end + "</gradient>".length();
				}

				parts.add(message.substring(start));

				message = String.join("", parts);
			}

			SimpleComponent component = SimpleComponent.fromMiniAmpersand(message);

			// This is about 2x faster but click/hover are lost
			//SimpleComponent component = SimpleComponent.fromSection(CompChatColor.convertMiniToLegacy(message));

			if (this.receiverCondition != null && !this.receiverCondition.isEmpty())
				component = component.viewCondition(this.receiverCondition
						.replace("{channel}", channelName)
						.replace("{channel_name}", channelName)
						.replace("{sender}", sender == null ? "" : sender.getName())
						.replace("{sender_name}", sender == null ? "" : sender.getName()));

			if (!ValidCore.isNullOrEmpty(this.receiverPermission))
				component = component.viewPermission(this.receiverPermission
						.replace("{channel}", channelName)
						.replace("{channel_name}", channelName)
						.replace("{sender}", sender == null ? "" : sender.getName())
						.replace("{sender_name}", sender == null ? "" : sender.getName()));

			if (this.receiverRequireVariable != null)
				component = component.viewRequireVariable(RequireVariable.fromLine(this.receiverRequireVariable
						.replace("{channel}", channelName)
						.replace("{channel_name}", channelName)
						.replace("{sender}", sender == null ? "" : sender.getName())
						.replace("{sender_name}", sender == null ? "" : sender.getName())));

			final boolean gradientSupport = Settings.Performance.SUPPORT_GRADIENTS_IN_HOVER;

			variables.toLegacyMode(gradientSupport ? ToLegacyMode.MINI : ToLegacyMode.LEGACY);

			if (this.hoverText != null && !this.hoverText.isEmpty()) {
				String replacedHoverText = "<gray>" + variables.replaceLegacy(this.hoverText);

				if (gradientSupport)
					replacedHoverText = CompChatColor.convertLegacyToMini(replacedHoverText, true);
				else
					replacedHoverText = CompChatColor.convertMiniToLegacy(ChatColor.translateAlternateColorCodes('&', replacedHoverText));

				final String[] lines = replacedHoverText.split("\n");

				Component joined = Component.empty();

				for (int i = 0; i < lines.length; i++) {
					String line = lines[i];

					// Receiver conditions will be lost
					try {
						if (MinecraftVersion.hasVersion() && MinecraftVersion.olderThan(V.v1_13) && MiniMessage.miniMessage().stripTags(line).length() > SimpleComponent.LEGACY_HOVER_LINE_LENGTH_LIMIT)
							line = String.join("\n", CommonCore.split(line, SimpleComponent.LEGACY_HOVER_LINE_LENGTH_LIMIT));

					} catch (final NoSuchMethodError err) {
						// Really old, and using CraftBukkit
					}

					if (gradientSupport)
						joined = joined.append(SimpleComponent.MINIMESSAGE_PARSER.deserialize(line));
					else
						joined = joined.append(Component.text(line));

					if (i < lines.length - 1)
						joined = joined.append(Component.newline());
				}

				component = component.onHover(joined);
			}

			variables.toLegacyMode(ToLegacyMode.PLAIN);

			if (this.hoverItem != null && !this.hoverItem.isEmpty()) {
				if (sender == null || !sender.isPlayer() && this.hoverItem.contains("player.")) {
					// Ignore

				} else
					try {
						final Object result = JavaScriptExecutor.run(variables.replaceLegacy(this.hoverItem), sender.getAudience());

						if (result != null) {
							ValidCore.checkBoolean(result instanceof ItemStack, "Hover_Item must return ItemStack not " + result.getClass() + " for format " + this.formatName + "." + this.name);

							component = component.onHover(Remain.convertItemStackToHoverEvent((ItemStack) result));
						}

					} catch (final FoScriptException ex) {
						CommonCore.logFramed(
								"Error parsing Hover_Item in format!",
								"Format: " + this.formatName,
								"Option: " + this.name,
								"Line: " + ex.getErrorLine(),
								"Sender: " + sender,
								"Error: " + ex.getMessage(),
								"",
								"Check that Hover_Item is a JavaScript",
								"code returning a valid ItemStack!");

						throw ex;
					}
			}

			if (this.openUrl != null && !this.openUrl.isEmpty())
				component = component.onClickOpenUrl(variables.replaceLegacy(this.openUrl));

			if (this.suggestCommand != null && !this.suggestCommand.isEmpty())
				component = component.onClickSuggestCmd(variables.replaceLegacy(this.suggestCommand));

			if (this.runCommand != null && !this.runCommand.isEmpty())
				component = component.onClickRunCmd(variables.replaceLegacy(this.runCommand));

			if (this.insertion != null && !this.insertion.isEmpty())
				component = component.onClickInsert(variables.replaceLegacy(this.insertion));

			if (this.copyToClipboard != null && !this.copyToClipboard.isEmpty())
				component = component.onClickCopyToClipboard(variables.replaceLegacy(this.copyToClipboard));

			variables.toLegacyMode(ToLegacyMode.MINI);

			return component;
		}

		public static Part deserialize(final SerializedMap map, final String formatName, final String partName) {
			final Part part = new Part(formatName, partName);

			// Check for invalid entries
			map.setRemoveOnGet(true);

			part.messages = map.getStringList("Message");

			if (part.messages == null)
				throw new FoException("Please set the 'Message' key in " + formatName + " format in part '" + partName + "'. Skipping part...", false);

			part.senderPermission = map.getString("Sender_Permission");
			part.senderCondition = map.getString("Sender_Condition");
			part.senderRequireVariable = map.containsKey("Sender_Variable") ? RequireVariable.fromLine(map.getString("Sender_Variable")) : null;
			part.receiverPermission = map.getString("Receiver_Permission");
			part.receiverCondition = map.getString("Receiver_Condition");
			part.receiverRequireVariable = map.getString("Receiver_Variable");
			part.hoverText = map.containsKey("Hover") ? String.join("\n", map.getStringList("Hover")) : null;
			part.hoverItem = map.getString("Hover_Item");
			part.openUrl = map.getString("Open_Url");
			part.suggestCommand = map.getString("Suggest_Command");

			// Educate people that Minecraft only supports running 1 command if they attempt to put a list there
			{
				Object runCommand = map.getObject("Run_Command");

				if (runCommand instanceof List) {
					final List<?> runCommands = (List<?>) runCommand;

					if (!runCommands.isEmpty()) {
						if (runCommands.size() != 1)
							throw new FoException("Minecraft only supports running 1 command in Run_Command, got: " + runCommand, false);

						runCommand = runCommands.get(0);
					}
				}

				if (runCommand instanceof String)
					part.runCommand = runCommand.toString();
			}

			part.insertion = map.getString("Insertion");
			part.copyToClipboard = map.getString("Copy_To_Clipboard");

			if (map.containsKey("Gradient")) {
				final String line = map.getString("Gradient");

				if (line.split(" - ").length != 2)
					CommonCore.warning("Invalid 'Gradient' syntax! Usage: <from color> - <to color> (can either be named chat color or RGB). Got: " + line);
				else {
					try {
						part.gradient = Tuple.deserialize(line, CompChatColor.class, CompChatColor.class);

					} catch (final IllegalArgumentException ex) {
						CommonCore.logFramed("Invalid 'Gradient' syntax! Usage: <from color> - <to color> (can either be named chat color or RGB in #132456 format (without <> tags)). Got: " + line);
					}

					CommonCore.warning("Using Gradient option in format " + formatName + " part '" + partName + "' is unsafe as it will break if colors are inside the 'Message', this is not a bug, but how the Adventure chat library works. Use MiniMessage strict tags instead.");
				}
			}

			part.imageFile = map.getString("Image_File");
			part.imageHead = map.getString("Image_Head");
			part.imageUrl = map.getString("Image_Url");
			part.imageHeight = map.getInteger("Image_Height");
			part.imageFillterCharacter = map.get("Image_Type", ChatImage.FillerCharacter.class);

			if (part.imageFile != null)
				ValidCore.checkBoolean(FileUtil.getFile("images/" + part.imageFile).exists(), "Image file '" + part.imageFile + "' not found in images/ folder for part: " + part);

			if (!map.isEmpty())
				Common.warning("Format " + formatName + " has part '" + partName + "' with unrecognized keys '" + map.keySet() + "', see https://github.com/kangarko/chatcontrol/wiki/Formats for what keys you can use in formats.");

			return part;
		}

		/**
		 * Return a format part from the given legacy text
		 *
		 * @param legacyText
		 * @return
		 */
		public static Part fromLegacy(final String legacyText) {
			final Part format = new Part("legacy-format", "legacy-part=" + legacyText);

			format.messages = Arrays.asList(legacyText);

			return format;
		}

		/**
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return this.serialize().toStringFormatted();
		}
	}

	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj) {
		return obj instanceof Format && ((Format) obj).getName().equals(this.getName());
	}

	/**
	 * @see org.mineacademy.fo.settings.YamlConfig#toString()
	 */
	@Override
	public String toString() {
		return "Format{" + this.getName() + ", options=" + this.parts + "}";
	}

	// ------–------–------–------–------–------–------–------–------–------–------–------–
	// Static
	// ------–------–------–------–------–------–------–------–------–------–------–------–

	/**
	 * Attempt to parse a format or a legacy text if the format by the given name
	 * does not exist we count it as text itself.
	 *
	 * @param formatOrLiteral
	 * @return
	 */
	public static Format parse(@NonNull final String formatOrLiteral) {
		final Format format = findFormat(formatOrLiteral);

		return format != null ? format : fromLiteral(formatOrLiteral);
	}

	/**
	 * Returns a new format from the literal text.
	 *
	 * @param literal
	 * @return
	 */
	public static Format fromLiteral(final String literal) {
		final Format format = new Format("literal-" + literal, true);

		format.parts = Arrays.asList(Part.fromLegacy(literal));
		return format;
	}

	/**
	 * @see ConfigItems#loadItems()
	 */
	public static void loadFormats() {
		loadedFormats.loadItems();

		// Initialize to prevent that big delay first time a player is chatting
		initFormats();
	}

	/*
	 * Initialize all formats
	 */
	private static void initFormats() {
		final List<Format> formats = loadedFormats.getItems();

		// We need a null sender due to a Paper bug: https://github.com/PaperMC/Paper/issues/11752
		final WrappedSender sender = null;

		for (final Format format : formats)
			try {
				format.build(sender);

			} catch (final Throwable t) {
				CommonCore.throwError(t, "Error initializing format " + format.getName());
			}
	}

	/**
	 * @param format
	 * @see ConfigItems#removeItem(org.mineacademy.fo.settings.YamlConfig)
	 */
	public static void removeFormat(final Format format) {
		loadedFormats.removeItem(format);
	}

	/**
	 * @param name
	 * @return
	 * @see ConfigItems#isItemLoaded(String)
	 */
	public static boolean isFormatLoaded(final String name) {
		return loadedFormats.isItemLoaded(name);
	}

	/**
	 * @param name
	 * @return
	 * @see ConfigItems#findItem(String)
	 */
	public static Format findFormat(@NonNull final String name) {
		return loadedFormats.findItem(name);
	}

	/**
	 * @return
	 * @see ConfigItems#getItems()
	 */
	public static Collection<Format> getFormats() {
		return loadedFormats.getItems();
	}

	/**
	 * @return
	 * @see ConfigItems#getItemNames()
	 */
	public static List<String> getFormatNames() {
		return loadedFormats.getItemNames();
	}

	/**
	 * Return if the message starts with \<actionbar\>, \<toast\>, \<title\> or \<bossbar\>
	 *
	 * @param message
	 * @return
	 */
	public static boolean isInteractiveChat(final String message) {
		return message.startsWith("<actionbar>") || message.startsWith("<toast>") || message.startsWith("<title>") || message.startsWith("<bossbar>");
	}
}
