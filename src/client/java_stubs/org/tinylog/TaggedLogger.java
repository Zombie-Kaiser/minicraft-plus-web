package org.tinylog;

/**
 * Web port stub of tinylog TaggedLogger. Formats messages with {} placeholders
 * and writes to stderr (mapped to the browser console by TeaVM).
 */
public class TaggedLogger {
	private final String tag;

	TaggedLogger(String tag) {
		this.tag = tag;
	}

	public String getTag() {
		return tag;
	}

	private static String format(String message, Object... arguments) {
		if (message == null) {
			return "null";
		}
		if (arguments == null || arguments.length == 0) {
			return message;
		}

		StringBuilder sb = new StringBuilder(message.length() + 24);
		int argIndex = 0;
		int i = 0;
		while (i < message.length()) {
			char c = message.charAt(i);
			if (c == '{' && i + 1 < message.length() && message.charAt(i + 1) == '}' && argIndex < arguments.length) {
				Object arg = arguments[argIndex++];
				sb.append(arg == null ? "null" : String.valueOf(arg));
				i += 2;
			} else {
				sb.append(c);
				i++;
			}
		}
		return sb.toString();
	}

	private void emit(String level, String message, Object[] arguments, Throwable exception) {
		StringBuilder sb = new StringBuilder();
		sb.append('[').append(level).append(']');
		if (tag != null && !tag.isEmpty()) {
			sb.append(' ').append(tag);
		}
		sb.append(": ").append(format(message, arguments));

		if (exception != null) {
			sb.append(" (").append(exception.getClass().getSimpleName());
			if (exception.getMessage() != null) {
				sb.append(": ").append(exception.getMessage());
			}
			sb.append(')');
		}

		System.err.println(sb);

		if (exception != null) {
			Throwable cause = exception.getCause();
			int depth = 0;
			while (cause != null && depth++ < 10) {
				System.err.println("  Caused by: " + cause);
				cause = cause.getCause();
			}
		}
	}

	public void trace(String message) { emit("TRACE", message, null, null); }
	public void trace(String message, Object... arguments) { emit("TRACE", message, arguments, null); }
	public void trace(String message, Throwable exception) { emit("TRACE", message, null, exception); }
	public void trace(Throwable exception) { emit("TRACE", null, null, exception); }
	public void trace(Throwable exception, String message) { emit("TRACE", message, null, exception); }
	public void trace(Throwable exception, String message, Object... arguments) { emit("TRACE", message, arguments, exception); }

	public void debug(String message) { emit("DEBUG", message, null, null); }
	public void debug(String message, Object... arguments) { emit("DEBUG", message, arguments, null); }
	public void debug(String message, Throwable exception) { emit("DEBUG", message, null, exception); }
	public void debug(Throwable exception) { emit("DEBUG", null, null, exception); }
	public void debug(Throwable exception, String message) { emit("DEBUG", message, null, exception); }
	public void debug(Throwable exception, String message, Object... arguments) { emit("DEBUG", message, arguments, exception); }

	public void info(String message) { emit("INFO", message, null, null); }
	public void info(String message, Object... arguments) { emit("INFO", message, arguments, null); }
	public void info(String message, Throwable exception) { emit("INFO", message, null, exception); }
	public void info(Throwable exception) { emit("INFO", null, null, exception); }
	public void info(Throwable exception, String message) { emit("INFO", message, null, exception); }
	public void info(Throwable exception, String message, Object... arguments) { emit("INFO", message, arguments, exception); }

	public void warn(String message) { emit("WARN", message, null, null); }
	public void warn(String message, Object... arguments) { emit("WARN", message, arguments, null); }
	public void warn(String message, Throwable exception) { emit("WARN", message, null, exception); }
	public void warn(Throwable exception) { emit("WARN", null, null, exception); }
	public void warn(Throwable exception, String message) { emit("WARN", message, null, exception); }
	public void warn(Throwable exception, String message, Object... arguments) { emit("WARN", message, arguments, exception); }

	public void error(String message) { emit("ERROR", message, null, null); }
	public void error(String message, Object... arguments) { emit("ERROR", message, arguments, null); }
	public void error(String message, Throwable exception) { emit("ERROR", message, null, exception); }
	public void error(Throwable exception) { emit("ERROR", null, null, exception); }
	public void error(Throwable exception, String message) { emit("ERROR", message, null, exception); }
	public void error(Throwable exception, String message, Object... arguments) { emit("ERROR", message, arguments, exception); }
}
