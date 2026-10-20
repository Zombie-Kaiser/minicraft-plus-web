package org.tinylog;

/**
 * Web port stub of tinylog Logger. Only the API surface used by the game is provided.
 */
public final class Logger {
	private Logger() {
	}

	public static TaggedLogger tag(String tag) {
		return new TaggedLogger(tag);
	}

	public static TaggedLogger tags(String... tags) {
		if (tags == null || tags.length == 0) {
			return new TaggedLogger(null);
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < tags.length; i++) {
			if (i > 0) {
				sb.append('/');
			}
			sb.append(tags[i]);
		}
		return new TaggedLogger(sb.toString());
	}
}
