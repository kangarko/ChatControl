package org.mineacademy.chatcontrol.proxy.operator;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.mineacademy.fo.model.RuleSetReader;

import lombok.Getter;

/**
 * Manages proxy command rules loaded from rules/command.rs.
 */
public final class ProxyRules extends RuleSetReader<ProxyRule> {

	@Getter
	private static final ProxyRules instance = new ProxyRules();

	/**
	 * The loaded rules
	 */
	private final List<ProxyRule> rules = new ArrayList<>();

	private ProxyRules() {
		super("match");
	}

	@Override
	public void load() {
		this.rules.clear();
		this.rules.addAll(this.loadFromFile("rules/command.rs"));
	}

	@Override
	protected ProxyRule createRule(final File file, final String value) {
		return new ProxyRule(value);
	}

	/**
	 * Return an immutable list of loaded rules
	 *
	 * @return
	 */
	public List<ProxyRule> getRules() {
		return Collections.unmodifiableList(this.rules);
	}
}
