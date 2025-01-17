package org.mineacademy.chatcontrol.command.chatcontrol;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.conversations.Conversable;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationPrefix;
import org.bukkit.conversations.Prompt;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.command.chatcontrol.ChatControlCommands.MainSubCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.RuleType;
import org.mineacademy.chatcontrol.operator.Groups;
import org.mineacademy.chatcontrol.operator.Rule;
import org.mineacademy.chatcontrol.operator.Rules;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.MathUtil;
import org.mineacademy.fo.TimeUtil;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.conversation.SimpleConversation;
import org.mineacademy.fo.conversation.SimplePrefix;
import org.mineacademy.fo.conversation.SimplePrompt;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.remain.CompSound;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.Lang;

/**
 * Represents command related to rules
 */
public final class RuleSubCommand extends MainSubCommand {

	public RuleSubCommand() {
		super("rule/r");

		this.setMinArguments(1);
		this.setPermission(Permissions.Command.RULE);
		this.setUsage(Lang.component("command-rule-usage"));
		this.setDescription(Lang.component("command-rule-description"));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected SimpleComponent getMultilineUsage() {
		return Lang.component("command-rule-usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onCommand() {
		this.checkUsage(this.args.length <= 2);

		final String param = this.args[0];
		final String option = this.args.length >= 2 ? CommonCore.joinRange(1, this.args) : null;

		if ("info".equals(param)) {
			final Rule rule = this.findRule(option);

			this.tellNoPrefix(Lang.component("command-rule-info-1", "rule", rule.getName()));
			this.getSender().sendMessage(rule.toDisplayableString());
			this.tellNoPrefix(Lang.component("command-rule-info-2"));
		}

		else if ("toggle".equals(param)) {
			final Rule rule = this.findRule(option);
			final boolean toggle = !rule.isDisabled();

			Rules.getInstance().toggleMessage(rule, toggle);
			this.tellSuccess(Lang.component("command-rule-" + (toggle ? "disable" : "enable"), "rule", rule.getName()));
		}

		else if ("create".equals(param)) {
			this.checkConsole();
			this.checkBoolean(!this.getPlayer().isConversing(), Lang.component("conversation-already-conversing", "player", this.audience.getName()));

			CreateRuleConversation.showTo(this.getPlayer());
		}

		else if ("import".equals(param)) {
			this.checkConsole();
			this.checkBoolean(!this.getPlayer().isConversing(), Lang.component("conversation-already-conversing", "player", this.audience.getName()));

			ImportRulesConversation.showTo(this.getPlayer());
		}

		else if ("list".equals(param)) {
			this.checkArgs(2, Lang.component("command-rule-list-no-type"));

			final RuleType type = this.findRuleType(this.args[1]);
			final ChatPaginator pages = new ChatPaginator(15);

			final List<Rule> rules = Rules.getInstance().getRules(type);
			this.checkBoolean(!rules.isEmpty(), Lang.component("command-rule-list-no-rules", "type", type.getLangKey()));

			final List<SimpleComponent> lines = new ArrayList<>();

			for (final Rule rule : rules) {
				SimpleComponent component = SimpleComponent.fromMiniNative(" <dark_gray>- ");

				final String name = rule.getName();
				final String match = rule.getMatch();
				final String[] hover = rule.toDisplayableString().split("\n");

				if (!name.isEmpty()) {
					component = component
							.append(Lang.component("command-rule-tooltip-name", "name", name))
							.onHoverLegacy(hover);
				}

				component = component
						.append(Lang.component("command-rule-tooltip-match"))
						.onHoverLegacy(hover);

				final int remainingSpace = MathUtil.range(70 - component.toLegacySection(null).length(), 5, 70);

				component = component
						.appendMiniAmpersand(match.length() > remainingSpace ? match.substring(0, remainingSpace) : match)
						.onHoverLegacy(hover);

				lines.add(component);
			}

			pages
					.setFoundationHeader(Lang.legacy("command-rule-list-header", "amount", rules.size(), "type", ChatUtil.capitalize(type.getLangKey())))
					.setPages(lines)
					.send(this.audience);
		}

		else if ("reload".equals(param)) {
			Rules.getInstance().load();

			this.tellSuccess(Lang.component("command-rule-reloaded"));
		}

		else
			this.returnInvalidArgs(param);
	}

	/**
	 * Represents the data stored in the conversation below
	 */
	private enum Data {
		NAME, FILE, TYPE, ENCODE, MATCH, GROUP
	}

	/**
	 * Represens the creation wizard conversation
	 */
	private final static class CreateRuleConversation extends SimpleConversation {

		/**
		 * The naming question
		 */
		protected final Prompt namePrompt;

		/**
		 * The type question
		 */
		protected final Prompt typePrompt;

		/**
		 * The question whether we should attempt to encode the rule with regex
		 */
		protected final Prompt encodePrompt;

		/**
		 * The match question what should be matched
		 */
		protected final Prompt matchPrompt;

		/**
		 * What group the rule should have, none if null question
		 */
		protected final Prompt groupPrompt;

		/**
		 * @see org.mineacademy.fo.conversation.SimpleConversation#getPrefix()
		 */
		@Override
		protected ConversationPrefix getPrefix() {
			return new SimplePrefix(Lang.legacy("command-rule-rule-creator"));
		}

		/**
		 * @see org.mineacademy.fo.conversation.SimpleConversation#getTimeout()
		 */
		@Override
		protected int getTimeout() {
			return 2 * 60; // two minutes
		}

		/**
		 *
		 */
		private CreateRuleConversation() {
			this.namePrompt = new SimplePrompt() {

				@Override
				protected String getPrompt(final ConversationContext ctx) {
					final Player player = (Player) ctx.getForWhom();
					CompSound.ENTITY_ARROW_HIT_PLAYER.play(player);

					return Lang.legacy("command-rule-rule-creator-welcome");
				}

				@Override
				protected boolean isInputValid(final ConversationContext context, final String input) {
					return Rules.getInstance().findRule(input) == null;
				}

				@Override
				protected String getFailedValidationText(final ConversationContext context, final String invalidInput) {
					return Lang.legacy("command-rule-rule-creator-already-exists", "rule", invalidInput);
				}

				@Override
				protected Prompt acceptValidatedInput(final ConversationContext context, final String input) {
					context.setSessionData(Data.NAME, input);

					return CreateRuleConversation.this.typePrompt;
				}
			};

			this.typePrompt = new SimplePrompt() {

				@Override
				protected String getPrompt(final ConversationContext ctx) {
					return Lang.legacy("command-rule-rule-creator-type",
							"rule", ctx.getSessionData(Data.NAME),
							"available", CommonCore.join(RuleType.values()));
				}

				@Override
				protected boolean isInputValid(final ConversationContext context, final String input) {
					try {
						RuleType.fromKey(input);

						return true;

					} catch (final Exception e) {
						return false;
					}
				}

				@Override
				protected String getFailedValidationText(final ConversationContext context, final String invalidInput) {
					return Lang.legacy("command-rule-invalid-type", "type", invalidInput);
				}

				@Override
				protected Prompt acceptValidatedInput(final ConversationContext context, final String input) {
					context.setSessionData(Data.TYPE, RuleType.fromKey(input));

					return CreateRuleConversation.this.encodePrompt;
				}
			};

			this.encodePrompt = new SimplePrompt() {

				@Override
				protected String getPrompt(final ConversationContext ctx) {
					return Lang.legacy("command-rule-encode");
				}

				@Override
				protected boolean isInputValid(final ConversationContext context, final String input) {
					return Lang.plain("command-rule-encode-yes").equals(input) || Lang.plain("command-rule-encode-no").equals(input);
				}

				@Override
				protected String getFailedValidationText(final ConversationContext context, final String invalidInput) {
					return Lang.legacy("command-rule-invalid-encode",
							"yes", Lang.plain("command-rule-encode-yes"),
							"no", Lang.plain("command-rule-encode-no"));
				}

				@Override
				protected Prompt acceptValidatedInput(final ConversationContext context, final String input) {
					context.setSessionData(Data.ENCODE, Lang.plain("command-rule-encode-yes").equals(input));

					return CreateRuleConversation.this.matchPrompt;
				}
			};

			this.matchPrompt = new SimplePrompt() {

				@Override
				protected String getPrompt(final ConversationContext ctx) {
					return Lang.legacy("command-rule-rule-creator-match");
				}

				@Override
				protected boolean isInputValid(final ConversationContext context, final String input) {
					return Rules.getInstance().findRuleByMatch((RuleType) context.getSessionData(Data.TYPE), this.getWord(context, input)) == null;
				}

				@Override
				protected String getFailedValidationText(final ConversationContext context, final String invalidInput) {
					return Lang.legacy("command-rule-rule-creator-match-already-exists");
				}

				@Override
				protected Prompt acceptValidatedInput(final ConversationContext context, final String input) {
					context.setSessionData(Data.MATCH, this.getWord(context, input));

					return CreateRuleConversation.this.groupPrompt;
				}

				private String getWord(final ConversationContext context, final String input) {
					return (boolean) context.getSessionData(Data.ENCODE) ? Encoder.encodeWord(input) : input;
				}
			};

			this.groupPrompt = new SimplePrompt() {

				@Override
				protected String getPrompt(final ConversationContext ctx) {
					return Lang.legacy("command-rule-group-name", "available", String.join(", ", Groups.getInstance().getGroupNames()));
				}

				@Override
				protected boolean isInputValid(final ConversationContext context, final String input) {
					return "none".equals(input) || Groups.getInstance().findGroup(input) != null;
				}

				@Override
				protected String getFailedValidationText(final ConversationContext context, final String invalidInput) {
					return Lang.legacy("command-rule-invalid-group", "group", invalidInput);
				}

				@Override
				protected Prompt acceptValidatedInput(final ConversationContext context, final String input) {
					context.setSessionData(Data.GROUP, input);

					return Prompt.END_OF_CONVERSATION;
				}
			};
		}

		/**
		 *
		 * @see org.mineacademy.fo.conversation.SimpleConversation#onConversationEnd(org.bukkit.conversations.ConversationAbandonedEvent, boolean)
		 */
		@Override
		protected void onConversationEnd(final ConversationAbandonedEvent event, final boolean cancelledFromInactivity) {
			final ConversationContext context = event.getContext();
			final Conversable conversable = context.getForWhom();
			final Map<Object, Object> data = Remain.getAllSessionData(context);

			if (event.gracefulExit()) {
				final String name = (String) data.get(Data.NAME);
				final RuleType type = (RuleType) data.get(Data.TYPE);
				final String match = (String) data.get(Data.MATCH);
				final String group = (String) data.get(Data.GROUP);

				final Rule rule = Rules.getInstance().createRule(type, match, name, "none".equals(group) ? null : group);

				tell(conversable, Lang.component("command-rule-rule-creator-success"));
				tell(conversable, rule.toDisplayableString());
			} else if (cancelledFromInactivity)
				tell(conversable, Lang.component("command-rule-conversation-cancelled"));
			else
				tell(conversable, Lang.component("command-rule-conversation-abandoned"));
		}

		/**
		 * @see org.mineacademy.fo.conversation.SimpleConversation#getFirstPrompt()
		 */
		@Override
		protected Prompt getFirstPrompt() {
			return this.namePrompt;
		}

		/**
		 * Start the conversation for the given player
		 *
		 * @param player
		 */
		private static void showTo(final Player player) {
			final CreateRuleConversation conversation = new CreateRuleConversation();

			conversation.start(player);
		}
	}

	/**
	 * Represens the import wizard conversation
	 */
	private final static class ImportRulesConversation extends SimpleConversation {

		/**
		 * What file we should import from?
		 */
		protected final Prompt filePrompt;

		/**
		 * What is the type of rules to import?
		 */
		protected final Prompt typePrompt;

		/**
		 * The question whether we should attempt to encode the rules with regex?
		 */
		protected final Prompt encodePrompt;

		/**
		 * What group the rule should have, none if null question
		 */
		protected final Prompt groupPrompt;

		/**
		 * @see org.mineacademy.fo.conversation.SimpleConversation#getPrefix()
		 */
		@Override
		protected ConversationPrefix getPrefix() {
			return new SimplePrefix(Lang.legacy("command-rule-rule-import"));
		}

		/**
		 * @see org.mineacademy.fo.conversation.SimpleConversation#getTimeout()
		 */
		@Override
		protected int getTimeout() {
			return 2 * 60; // two minutes
		}

		/**
		 *
		 */
		private ImportRulesConversation() {
			this.filePrompt = new SimplePrompt() {

				@Override
				protected String getPrompt(final ConversationContext ctx) {
					final Player player = (Player) ctx.getForWhom();
					CompSound.ENTITY_ARROW_HIT_PLAYER.play(player);

					return Lang.legacy("command-rule-rule-import-welcome");
				}

				@Override
				protected boolean isInputValid(final ConversationContext context, final String input) {
					return FileUtil.getFile(input).exists();
				}

				@Override
				protected String getFailedValidationText(final ConversationContext context, final String invalidInput) {
					return Lang.legacy("command-rule-rule-import-invalid-file", "file", FileUtil.getFile(invalidInput).toPath().toString());
				}

				@Override
				protected Prompt acceptValidatedInput(final ConversationContext context, final String input) {
					context.setSessionData(Data.FILE, FileUtil.getFile(input));

					return ImportRulesConversation.this.typePrompt;
				}
			};

			this.typePrompt = new SimplePrompt() {

				@Override
				protected String getPrompt(final ConversationContext ctx) {
					final File file = (File) ctx.getSessionData(Data.FILE);

					return Lang.legacy("command-rule-rule-import-type", "file", file.getName(), "available", RuleType.values());
				}

				@Override
				protected boolean isInputValid(final ConversationContext context, final String input) {
					try {
						RuleType.fromKey(input);

						return true;

					} catch (final Exception e) {
						return false;
					}
				}

				@Override
				protected String getFailedValidationText(final ConversationContext context, final String invalidInput) {
					return Lang.legacy("command-rule-invalid-type", "type", invalidInput);
				}

				@Override
				protected Prompt acceptValidatedInput(final ConversationContext context, final String input) {
					context.setSessionData(Data.TYPE, RuleType.fromKey(input));

					return ImportRulesConversation.this.encodePrompt;
				}
			};

			this.encodePrompt = new SimplePrompt() {

				@Override
				protected String getPrompt(final ConversationContext ctx) {
					return Lang.legacy("command-rule-encode");
				}

				@Override
				protected boolean isInputValid(final ConversationContext context, final String input) {
					return Lang.plain("command-rule-encode-yes").equals(input) || Lang.plain("command-rule-encode-no").equals(input);
				}

				@Override
				protected String getFailedValidationText(final ConversationContext context, final String invalidInput) {
					return Lang.legacy("command-rule-invalid-encode",
							"yes", Lang.plain("command-rule-encode-yes"),
							"no", Lang.plain("command-rule-encode-no"));
				}

				@Override
				protected Prompt acceptValidatedInput(final ConversationContext context, final String input) {
					context.setSessionData(Data.ENCODE, Lang.plain("command-rule-encode-yes").equals(input));

					return ImportRulesConversation.this.groupPrompt;
				}
			};

			this.groupPrompt = new SimplePrompt() {

				@Override
				protected String getPrompt(final ConversationContext ctx) {
					return Lang.legacy("command-rule-group-name", "available", Groups.getInstance().getGroupNames());
				}

				@Override
				protected boolean isInputValid(final ConversationContext context, final String input) {
					return "none".equals(input) || Groups.getInstance().findGroup(input) != null;
				}

				@Override
				protected String getFailedValidationText(final ConversationContext context, final String invalidInput) {
					return Lang.legacy("command-rule-invalid-group", "group", invalidInput);
				}

				@Override
				protected Prompt acceptValidatedInput(final ConversationContext context, final String input) {
					context.setSessionData(Data.GROUP, input);

					return Prompt.END_OF_CONVERSATION;
				}
			};
		}

		/**
		 *
		 * @see org.mineacademy.fo.conversation.SimpleConversation#onConversationEnd(org.bukkit.conversations.ConversationAbandonedEvent, boolean)
		 */
		@Override
		protected void onConversationEnd(final ConversationAbandonedEvent event, final boolean cancelledFromInactivity) {
			final ConversationContext context = event.getContext();
			final Conversable conversable = context.getForWhom();
			final Map<Object, Object> data = Remain.getAllSessionData(context);

			if (event.gracefulExit()) {
				final File file = (File) data.get(Data.FILE);
				final RuleType type = (RuleType) data.get(Data.TYPE);
				final boolean encode = (boolean) data.get(Data.ENCODE);
				String group = (String) data.get(Data.GROUP);

				group = "none".equals(group) ? null : group;
				int count = 0;

				tell(conversable, Lang.component("command-rule-rule-import-start"));

				final File rulesFile = FileUtil.createIfNotExists("rules/" + type.getKey() + ".rs");
				final List<String> lines = FileUtil.readLinesFromFile(rulesFile);

				for (final String line : FileUtil.readLinesFromFile(file)) {
					final String match = encode ? Encoder.encodeWord(line) : ChatUtil.quoteReplacement(line);

					lines.add("");
					lines.add("# Imported '" + line + "' on " + TimeUtil.getFormattedDate());
					lines.add("match " + match);

					if (group != null) {
						ValidCore.checkBoolean(Groups.getInstance().findGroup(group) != null, "Rule type " + type + " match '" + match + "' refered to non-existing group: " + group);

						lines.add("group " + group);
					}

					count++;
				}

				FileUtil.write(rulesFile, lines);

				try {
					Rules.getInstance().load();

					tell(conversable, Lang.component("command-rule-rule-import-success", "amount", count));

				} catch (final Throwable t) {
					CommonCore.error(t, "Failed to import rules to " + rulesFile);

					tell(conversable, Lang.component("command-rule-rule-import-fail"));
				}
			} else if (cancelledFromInactivity)
				tell(conversable, Lang.component("command-rule-conversation-cancelled"));

			else
				tell(conversable, Lang.component("command-rule-conversation-abandoned"));
		}

		/**
		 * @see org.mineacademy.fo.conversation.SimpleConversation#getFirstPrompt()
		 */
		@Override
		protected Prompt getFirstPrompt() {
			return this.filePrompt;
		}

		/**
		 * Start the conversation for the given player
		 *
		 * @param player
		 */
		private static void showTo(final Player player) {
			final ImportRulesConversation conversation = new ImportRulesConversation();

			conversation.start(player);
		}
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		if (this.args.length == 1)
			return this.completeLastWord("info", "toggle", "create", "import", "list", "reload");

		if (this.args.length == 2)
			if ("list".equals(this.args[0]))
				return this.completeLastWord(RuleType.values());
			else if ("info".equals(this.args[0]) || "toggle".equals(this.args[0]))
				return this.completeLastWord(CommonCore.convertList(Rules.getInstance().getRulesWithName(), Rule::getName));

		return NO_COMPLETE;
	}

	final static class Encoder {
		private static final String WORD_BOUNDARY = "\\b";
		private static final String NON_WORD = "[\\W\\d_]*";
		private static final String LOOKAHEAD = "(?=[^\\s]*\\b)";
		private static final String SECTION_TEMPLATE = "{LETTER}+{NON_WORD}";
		private static final String LAST_SECTION_TEMPLATE = "{LETTER}+";

		static String encodeWord(final String word) {
			final StringBuilder encoded = new StringBuilder();

			encoded.append(WORD_BOUNDARY).append("(");

			final Map<Character, String> letterReplacements = new HashMap<>();

			// Special mappings for letters
			letterReplacements.put('a', "[a4]");
			letterReplacements.put('e', "[e3]");
			letterReplacements.put('i', "[i1!]");
			letterReplacements.put('o', "[o0]");
			letterReplacements.put('u', "[u_!@#$%^&*]");
			letterReplacements.put('s', "[s$]");

			for (int i = 0; i < word.length(); i++) {
				final char c = word.charAt(i);
				String letterPattern;

				if (letterReplacements.containsKey(c))
					letterPattern = letterReplacements.get(c);

				else {
					// Escape special regex characters
					if (" \\$()+.-_^".indexOf(c) != -1)
						letterPattern = "\\" + c;
					else
						letterPattern = String.valueOf(c);
				}

				final String sectionTemplate = (i == word.length() - 1) ? LAST_SECTION_TEMPLATE : SECTION_TEMPLATE;

				final String section = sectionTemplate
						.replace("{LETTER}", letterPattern)
						.replace("{NON_WORD}", NON_WORD);

				encoded.append(section);
			}

			encoded.append(")");
			encoded.append(LOOKAHEAD);

			return encoded.toString();
		}
	}
}
