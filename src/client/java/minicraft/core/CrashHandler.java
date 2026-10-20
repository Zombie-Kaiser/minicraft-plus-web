package minicraft.core;

import minicraft.core.CrashHandler.ErrorInfo.ErrorType;
import minicraft.core.io.ClipboardHandler;
import minicraft.util.Logging;
import minicraft.web.Platform;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;

public class CrashHandler {
	public static void crashHandle(Thread thread, Throwable throwable) {
		crashHandle(throwable);
	}

	public static void crashHandle(Throwable throwable) {
		crashHandle(throwable, new ErrorInfo(true));
	}

	/**
	 * This handles application crashing errors by giving notification to the user clearly.<br>
	 * The user can only exit the program.
	 */
	public static void crashHandle(Throwable throwable, ErrorInfo info) {
		Logging.CRASHHANDLER.error(throwable);

		StringWriter string = new StringWriter();
		PrintWriter printer = new PrintWriter(string);
		throwable.printStackTrace(printer);

		Logging.CRASHHANDLER.error("Crash: " + info.type.name + ": " + info.title + (info.message != null ? ": " + info.message : ""));

		ClipboardHandler clip = new ClipboardHandler();
		clip.setClipboardContents(info.type.name + ": " + info.title + (info.message != null ? ": " + info.message : "") + "\n" + string);

		Platform.alert("Crash: " + info.type.name + "\n\n" + info.title +
			(info.message != null && info.message.length() > 0 ? "\n" + info.message : "") +
			"\n\n" + truncate(string.toString()) + "\n\n(Error copied to clipboard.)");

		// Exits the program when the dialog closes.
		Logging.CRASHHANDLER.error("Application closes due to the crash.");
		Platform.exit();
	}

	public static void errorHandle(Throwable throwable) {
		errorHandle(throwable, new ErrorInfo());
	}

	public static void errorHandle(Throwable throwable, ErrorInfo info) {
		errorHandle(throwable, info, null);
	}

	/**
	 * This handles application crashing errors by giving notification to the user clearly.<br>
	 * The user can ignore the error, continue handling the error or exit the program (only in serious errors or error reports).
	 * On the web there is no modal button dialog, so the default action (continue, running the
	 * handling callback when present) is taken automatically.
	 * @param handling The handling function of the error.
	 */
	public static void errorHandle(Throwable throwable, ErrorInfo info, @Nullable Action handling) {
		throwable.printStackTrace();

		StringWriter string = new StringWriter();
		PrintWriter printer = new PrintWriter(string);
		throwable.printStackTrace(printer);

		Logging.CRASHHANDLER.error(info.type.name + ": " + info.title + (info.message != null ? ": " + info.message : ""));

		ClipboardHandler clip = new ClipboardHandler();
		clip.setClipboardContents(info.type.name + ": " + info.title + (info.message != null ? ": " + info.message : "") + "\n" + string);

		Platform.alert("Error: " + info.type.name + "\n\n" + info.title +
			(info.message != null && info.message.length() > 0 ? "\n" + info.message : "") +
			"\n\n" + truncate(string.toString()) + "\n\n(Error copied to clipboard.)");

		// Equivalent of the dialog's default "Continue" button.
		if (handling != null) handling.act();
	}

	private static String truncate(String stackTrace) {
		if (stackTrace.length() <= 2000) {
			return stackTrace;
		}
		return stackTrace.substring(0, 2000) + "\n... (truncated)";
	}

	public static class ErrorInfo {
		public final String title;
		public final ErrorType type;
		public final String message;
		public final boolean serious;

		public ErrorInfo() {
			this(false);
		}

		public ErrorInfo(boolean crashing) {
			this(crashing ? "General Application Crash" : "General Application Error",
				crashing ? ErrorType.DEFAULT : ErrorType.REPORT);
		}

		public ErrorInfo(String topic) {
			this(topic, ErrorType.DEFAULT);
		}

		public ErrorInfo(String topic, ErrorType type) {
			this(topic, type, type.exitCode != 0);
		}

		public ErrorInfo(String topic, ErrorType type, boolean serious) {
			this(topic, type, serious, null);
		}

		public ErrorInfo(String topic, ErrorType type, String message) {
			this(topic, type, type.exitCode < 0, message);
		}

		public ErrorInfo(String topic, ErrorType type, boolean serious, String message) {
			this.title = topic;
			this.type = type;
			this.message = message;
			this.serious = serious;
		}

		/**
		 * The error types. Add more types when needed.
		 */
		public static enum ErrorType {
			DEFAULT(-1, "Unhandled error"),
			UNEXPECTED(-2, "Unexpected error"),
			UNHANDLEABLE(-3, "Unhandleable error"),
			SERIOUS(1, "Serious error"),
			HANDLED(0, "Handled error"),
			REPORT(0, "Error report"),
			;

			/**
			 * The exit codes are referring to https://www.techiedelight.com/exit-codes-java-system-exit-method/
			 */
			public final int exitCode;
			public final String name;

			ErrorType(int exitCode, String message) {
				this.exitCode = exitCode;
				this.name = message;
			}
		}
	}
}
