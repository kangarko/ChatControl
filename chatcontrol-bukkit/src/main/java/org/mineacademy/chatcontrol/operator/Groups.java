package org.mineacademy.chatcontrol.operator;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mineacademy.fo.model.RuleSetReader;

import lombok.Getter;

/**
 * Represents a way to store/load rule groups
 */
public final class Groups extends RuleSetReader<Group> {

	@Getter
	private static final Groups instance = new Groups();

	/**
	 * The loaded rule groups sorted by name
	 */
	private final Map<String, Group> groups = new HashMap<>();

	/*
	 * Create this class
	 */
	private Groups() {
		super("group");
	}

	/**
	 * Reloads rules and handlers.
	 */
	@Override
	public void load() {
		this.groups.clear();

		for (final Group group : this.loadFromFile("rules/groups.rs"))
			this.groups.put(group.getGroup(), group);
	}

	/**
	 * Return group by name, or null if such group not exist
	 *
	 * @param groupName
	 * @return
	 */
	public Group findGroup(final String groupName) {
		return this.groups.get(groupName);
	}

	/**
	 * Return group name list
	 *
	 * @return
	 */
	public List<String> getGroupNames() {
		return new ArrayList<>(this.groups.keySet());
	}

	/**
	 * @see org.mineacademy.fo.model.RuleSetReader#createRule(java.io.File, java.lang.String)
	 */
	@Override
	protected Group createRule(final File file, final String value) {
		return new Group(value);
	}
}
