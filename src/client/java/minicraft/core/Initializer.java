package minicraft.core;

import minicraft.core.io.FileHandler;
import minicraft.core.io.Localization;
import minicraft.util.Logging;
import minicraft.web.Platform;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class Initializer extends Game {
	private Initializer() {
	}

	static int fra, tik; // These store the number of frames and ticks in the previous second; used for fps, at least.

	public static int getCurFps() {
		return fra;
	}

	static void parseArgs(String[] args) {
		// Parses command line arguments
		@Nullable
		String saveDir = null;
		for (int i = 0; i < args.length; i++) {
			if (args[i].equalsIgnoreCase("--savedir") && i + 1 < args.length) {
				i++;
				saveDir = args[i];
			} else if (args[i].equalsIgnoreCase("--fullscreen")) {
				Updater.FULLSCREEN = true;
			} else if (args[i].equalsIgnoreCase("--debug-log-time")) {
				Logging.logTime = true;
			} else if (args[i].equalsIgnoreCase("--debug-log-thread")) {
				Logging.logThread = true;
			} else if (args[i].equalsIgnoreCase("--debug-log-trace")) {
				Logging.logTrace = true;
			} else if (args[i].equalsIgnoreCase("--debug-level")) {
				Logging.logLevel = true;
			} else if (args[i].equalsIgnoreCase("--debug-filelog-full")) {
				Logging.fileLogFull = true;
			} else if (args[i].equalsIgnoreCase("--debug-locale")) {
				Localization.isDebugLocaleEnabled = true;
			} else if (args[i].equalsIgnoreCase("--debug-unloc-tracing")) {
				Localization.unlocalizedStringTracing = true;
			}
		}

		FileHandler.determineGameDir(saveDir);
	}

	private static boolean loopStarted;
	private static boolean exitSignaled;
	private static long lastTick;
	private static long lastRender;
	private static double unprocessed;
	private static int frames;
	private static int ticks;
	private static long lastTimer1;

	/**
	 * Marks the main loop as started; the web frame loop then drives {@link #step()}.
	 */
	static void run() {
		lastTick = System.nanoTime();
		lastRender = lastTick;
		lastTimer1 = System.currentTimeMillis();
		loopStarted = true;
	}

	/**
	 * One iteration of the original main loop (tick catch-up + render + fps stats),
	 * without the sleep, driven once per browser animation frame.
	 */
	public static void step() {
		if (!loopStarted) return;
		if (!running) {
			signalExit();
			return;
		}

		long now = System.nanoTime();
		double nsPerTick = 1E9D / Updater.normSpeed; // Nanosecs per sec divided by ticks per sec = nanosecs per tick
		if (currentDisplay == null) nsPerTick /= Updater.gamespeed;
		unprocessed += (now - lastTick) / nsPerTick; // Figures out the unprocessed time between now and lastTick.
		lastTick = now;
		while (unprocessed >= 1) { // If there is unprocessed time, then tick.
			ticks++;
			Updater.tick(); // Calls the tick method (in which it calls the other tick methods throughout the code.
			unprocessed--;
		}

		now = System.nanoTime();
		if (now >= lastRender + 1E9D / MAX_FPS / 1.01) {
			frames++;
			lastRender = now;
			Renderer.render();
		}

		if (System.currentTimeMillis() - lastTimer1 > 1000) { //updates every 1 second
			long interval = System.currentTimeMillis() - lastTimer1;
			lastTimer1 = System.currentTimeMillis(); // Adds a second to the timer

			fra = (int) Math.round(frames * 1000D / interval); // Saves total frames in last second
			tik = (int) Math.round(ticks * 1000D / interval); // Saves total ticks in last second
			frames = 0; // Resets frames
			ticks = 0; // Resets ticks; ie, frames and ticks only are per second
		}
	}

	private static void signalExit() {
		if (exitSignaled) return;
		exitSignaled = true;
		Logging.GAMEHANDLER.debug("Main game loop ended; Terminating application...");
		Platform.exit();
	}

	static void createAndDisplayFrame() {
		// The browser canvas is created by web/main.js.
	}

	/**
	 * Launching the main window.
	 */
	static void launchWindow() {
		Platform.setTitle(NAME);
	}

	/**
	 * Provides a String representation of the provided Throwable's stack trace
	 * that is extracted via PrintStream.
	 * @param throwable Throwable/Exception from which stack trace is to be
	 * 	extracted.
	 * @return String with provided Throwable's stack trace.
	 */
	public static String getExceptionTrace(final Throwable throwable) {
		final ByteArrayOutputStream bytestream = new ByteArrayOutputStream();
		final PrintStream printStream = new PrintStream(bytestream);
		throwable.printStackTrace(printStream);
		String exceptionStr;
		try {
			exceptionStr = bytestream.toString("UTF-8");
		} catch (Exception ex) {
			exceptionStr = "Unavailable";
		}
		return exceptionStr;
	}
}
