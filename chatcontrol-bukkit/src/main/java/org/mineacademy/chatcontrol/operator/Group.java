package org.mineacademy.chatcontrol.operator;

import java.io.File;
import java.util.Map;

import org.mineacademy.fo.FileUtil;

import lombok.Getter;

/**
 * Represents a group holding operators that can be reused by many rules
 */
@Getter
public final class Group extends RuleOperator {

	/**
	 * The name of the group
	 */
	private final String group;

	/**
	 * Create a new rule group by name
	 *
	 * @param group
	 */
	public Group(final String group) {
		this.group = group;
	}

	/**
	 * @see org.mineacademy.fo.model.Rule#getFile()
	 */
	@Override
	public File getFile() {
		return FileUtil.getFile("rules/groups.rs");
	}

	/**
	 * @see org.mineacademy.fo.model.Rule#getUniqueName()
	 */
	@Override
	public String getUniqueName() {
		return this.group;
	}

	@Override
	protected Map<String, Object> collectOptions() {
		final Map<String, Object> map = super.collectOptions();

		map.put("Group", this.group);

		return map;
	}
}
