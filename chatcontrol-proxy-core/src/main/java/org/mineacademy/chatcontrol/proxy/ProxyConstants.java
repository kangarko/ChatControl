package org.mineacademy.chatcontrol.proxy;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Holds proxy related constants
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProxyConstants {

	/**
	 * The default channel we are broadcasting, legacy format.
	 */
	public static final String BUNGEECORD_CHANNEL = "BungeeCord";

	/**
	 * The channel we are broadcasting at, new format.
	 */
	public static final String CHATCONTROL_CHANNEL = "plugin:chcred";

	/**
	 * The redis channel we are broadcasting at
	 */
	public static final String REDIS_CHANNEL = "redischcred";
}
