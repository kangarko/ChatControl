package org.mineacademy.chatcontrol.util;

import java.util.HashSet;
import java.util.Set;

import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.CommonCore;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LogUtil {

	/**
	 * What console messages we already showed to the console? This is not stored in any
	 * file and resets every reload/restart. Used to prevent console message spam to
	 * only make certain messages show once.
	 */
	private final static Set<String> shownConsoleMessages = new HashSet<>();

	/**
	 * Logs the given message once per plugin session
	 *
	 * @param section
	 * @param message
	 */
	public static void logOnce(final String section, final String message) {
		if (Settings.SHOW_TIPS && !shownConsoleMessages.contains(section)) {
			CommonCore.logTimed(60 * 60 * 3, message + " This message only shows once per 3 hours.");

			shownConsoleMessages.add(section);
		}
	}

	/**
	 * Show a less but still important informational message that the user
	 * can toggle off in settings.yml
	 *
	 * @param message
	 */
	public static void logTip(final String message) {
		if (Settings.SHOW_TIPS)
			CommonCore.log(message);
	}
}
