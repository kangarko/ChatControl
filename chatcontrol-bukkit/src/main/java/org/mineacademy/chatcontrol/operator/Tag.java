package org.mineacademy.chatcontrol.operator;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.mineacademy.chatcontrol.model.Colors;
import org.mineacademy.chatcontrol.model.RuleType;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.fo.CommonCore;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Represents a simple tag
 */
@Getter
public final class Tag extends Rule {

	/**
	 * List of tags this rule only applies to
	 */
	private final Set<Type> requireTags = new HashSet<>();

	/**
	 * Create a new rule of the given type and match operator
	 *
	 * @param type
	 * @param match the regex after the match
	 */
	public Tag(final RuleType type, final String match) {
		super(type, match);
	}

	/**
	 * @see org.mineacademy.chatcontrol.operator.Operator#onParse(java.lang.String, java.lang.String, java.lang.String[])
	 */
	@Override
	protected boolean onParse(final String param, final String theRest, final String[] args) {
		if ("require tag".equals(param) || "require tag".equals(param))
			for (final String typeKey : splitVertically(theRest)) {
				final Type type = Type.fromKey(typeKey);

				this.requireTags.add(type);
			}

		else
			return super.onParse(param, theRest, args);

		return true;
	}

	@Override
	protected Map<String, Object> collectOptions() {
		final Map<String, Object> options = super.collectOptions();

		options.put("Require Tags", this.requireTags);

		return options;
	}

	/* ------------------------------------------------------------------------------- */
	/* Classes */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Represents a tag type
	 */
	@RequiredArgsConstructor
	public enum Type {

		/**
		 * Represents prefix
		 */
		PREFIX("prefix", Colors.Type.PREFIX),

		/**
		 * Represents nick
		 */
		NICK("nick", Colors.Type.NICK),

		/**
		 * Represents suffix
		 */
		SUFFIX("suffix", Colors.Type.SUFFIX);

		/**
		 * The saveable non-obfuscated key
		 */
		@Getter
		private final String key;

		/**
		 * The color type
		 */
		@Getter
		private final Colors.Type colorType;

		/**
		 * Attempt to load an item from the given config key
		 *
		 * @param key
		 * @return
		 */
		public static Type fromKey(final String key) {
			for (final Type mode : values())
				if (mode.key.equalsIgnoreCase(key))
					return mode;

			throw new IllegalArgumentException("No such item type: " + key + ". Available: " + CommonCore.join(values()));
		}

		/**
		 * Returns {@link #getKey()}
		 */
		@Override
		public String toString() {
			return this.key;
		}
	}

	/* ------------------------------------------------------------------------------- */
	/* Static */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Start the rule check for the given rule type, sender and his message
	 *
	 * @param type
	 * @param wrapped
	 * @param tag
	 * @return
	 */
	public static TagCheck filter(final Type type, final WrappedSender wrapped, final String tag) {
		final TagCheck check = new TagCheck(type, wrapped, tag);

		check.start();
		return check;
	}

	/* ------------------------------------------------------------------------------- */
	/* Classes */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Represents a tag check
	 */
	public static class TagCheck extends RuleCheck<Tag> {

		/**
		 * What tag are we checking?
		 */
		private final Type tag;

		/*
		 * Create a new check
		 */
		private TagCheck(final Type tag, @NonNull final WrappedSender wrapped, final String message) {
			super(RuleType.TAG, wrapped, message, null, null);

			this.tag = tag;
		}

		/**
		 * @see org.mineacademy.chatcontrol.operator.Rule.RuleCheck#canFilter(org.mineacademy.chatcontrol.operator.RuleOperator)
		 */
		@Override
		protected boolean canFilter(final RuleOperator operator) {

			if (operator instanceof Tag) {
				final Tag tag = (Tag) operator;

				if (!tag.getRequireTags().isEmpty() && !tag.getRequireTags().contains(this.tag))
					return false;
			}

			return super.canFilter(operator);
		}
	}
}
