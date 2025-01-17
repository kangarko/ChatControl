package org.mineacademy.chatcontrol.operator;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.mineacademy.chatcontrol.model.RuleType;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.TimeUtil;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.RuleSetReader;

import lombok.Getter;

/**
 * Represents the core engine for rules
 * @param <T>
 */
@Getter
public final class Rules<T extends Rule> extends RuleSetReader<T> {

	/**
	 * The singleton instance of this class
	 */
	@Getter
	private static final Rules<? extends Rule> instance = new Rules<>();

	/**
	 * The loaded rules sorted by type
	 */
	private final Map<RuleType, List<T>> rules = new HashMap<>();

	/**
	* Defines what other rules the rules on keys should import?
	*/
	private final Map<RuleType, List<RuleType>> imports = new HashMap<>();

	/*
	* Create this class
	*/
	private Rules() {
		super("match");
	}

	/**
	 * Reloads rules and handlers.
	 */
	@Override
	public void load() {
		this.rules.clear();
		this.imports.clear();

		for (final RuleType ruleType : RuleType.values()) {
			this.rules.put(ruleType, this.loadFromFile("rules/" + ruleType.getKey() + ".rs"));

			// Reverse this to correct order of checking below
			Collections.reverse(this.imports.getOrDefault(ruleType, new ArrayList<>()));
		}
	}

	/**
	 * @see org.mineacademy.fo.model.RuleSetReader#onNoMatchLineParse(java.io.File, java.lang.String)
	 */
	@Override
	protected boolean onNoMatchLineParse(final File file, final String line) {

		// Enable importing of other rules
		if (line.startsWith("@import ")) {
			final String ruleType = line.replace("@import ", "");

			final RuleType current = RuleType.fromKey(FileUtil.getFileName(file));
			final RuleType imported;

			try {
				imported = RuleType.fromKey(ruleType);

			} catch (final IllegalArgumentException ex) {
				throw new FoException("Your @import operator in " + file + " refers to unknown rule type: " + ruleType + ". Available: " + CommonCore.join(RuleType.values()));
			}

			final List<RuleType> importedTypes = this.imports.getOrDefault(current, new ArrayList<>());
			importedTypes.add(imported);

			this.imports.put(current, importedTypes);

			return true;
		}

		return false;
	}

	/**
	 * Attempt to create a new rule and append it at the end of the file
	 * @param type
	 * @param name
	 * @param group
	 * @param match
	 *
	 * @return the newly created rule
	 */
	public Rule createRule(final RuleType type, final String match, @Nullable final String name, @Nullable final String group) {
		ValidCore.checkBoolean(this.findRuleByMatch(type, match) == null, "Rule type " + type + " with match '" + match + "' already exists!");

		final File file = FileUtil.createIfNotExists("rules/" + type.getKey() + ".rs");
		final List<String> lines = FileUtil.readLinesFromFile(file);

		lines.add("");
		lines.add("# Automatically created on " + TimeUtil.getFormattedDate());
		lines.add("match " + match);

		if (name != null)
			lines.add("name " + name);

		if (group != null) {
			ValidCore.checkBoolean(Groups.getInstance().findGroup(group) != null, "Rule type " + type + " match '" + match + "' refered to non-existing group: " + group);

			lines.add("group " + group);
		}

		this.saveAndLoad(file, lines);
		return this.findRuleByMatch(type, match);
	}

	/**
	 * @see org.mineacademy.fo.model.RuleSetReader#createRule(java.io.File, java.lang.String)
	 */
	@Override
	protected T createRule(final File file, final String value) {
		final RuleType type = RuleType.fromKey(FileUtil.getFileName(file));

		return (T) (type == RuleType.TAG ? new Tag(type, value) : new Rule(type, value));
	}

	/**
	 * Attempt to find a rule by name
	 *
	 * @param ruleName
	 * @return
	 */
	public Rule findRule(final String ruleName) {
		for (final Rule rule : this.getRulesWithName())
			if (rule.getName().equalsIgnoreCase(ruleName))
				return rule;

		return null;
	}

	/**
	 * Return a rule of a certain type for the given match
	 *
	 * @param type
	 * @param match
	 * @return
	 */
	public Rule findRuleByMatch(final RuleType type, final String match) {
		for (final Rule rule : this.rules.get(type))
			if (rule.getMatch().equals(match))
				return rule;

		return null;
	}

	/**
	 * Return a list of all rules that have names assigned
	 *
	 * @return
	 */
	public List<Rule> getRulesWithName() {
		final List<Rule> namedRules = new ArrayList<>();

		for (final List<T> rules : this.rules.values())
			for (final Rule rule : rules)
				if (!rule.getName().isEmpty())
					namedRules.add(rule);

		return namedRules;
	}

	/**
	 * Return immutable list of rules of given type
	 *
	 * @param type
	 * @return
	 */
	public List<Rule> getRules(final RuleType type) {
		ValidCore.checkBoolean(this.rules.containsKey(type), "Rules do not contain type " + type + " (or not loaded yet -- check for plugin errors during startup)");

		return Collections.unmodifiableList(this.rules.get(type));
	}
}
