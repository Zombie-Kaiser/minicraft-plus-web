package minicraft.web;

import minicraft.core.CrashHandler;
import minicraft.core.Game;
import minicraft.core.Initializer;
import minicraft.util.Logging;
import org.teavm.jso.JSBody;

/**
 * TeaVM entry point. Registers the JS bridges, receives bundled assets from
 * main.js, then boots the game when delivery completes. The rAF-driven frame
 * loop calls {@link #onTick()} after boot.
 */
public final class WebMain {
	private static boolean booted;
	private static boolean crashed;
	private static boolean started;

	private WebMain() {
	}

	@JSBody(script = "window.__platform.onJavaMainStarted();")
	static native void markStarted();

	public static void main(String[] args) {
		if (started) return; // guard against auto-run + explicit main() double boot
		started = true;
		markStarted();

		WebVfs.install();

		Bridge.install(
			WebMain::onBootBegin,
			WebMain::onAsset,
			WebMain::onImage,
			WebMain::onReady,
			WebMain::onTick,
			WebMain::onKey,
			WebMain::onTyped,
			WebVfs::flushPersist
		);

		Logging.GAMEHANDLER.info("Web platform ready; waiting for assets.");
		Bridge.notifyJavaReady();
	}

	private static void onBootBegin(int count) {
		Logging.GAMEHANDLER.info("Receiving " + count + " bundled assets.");
	}

	private static void onAsset(String path, byte[] data) {
		WebVfs.get().putAsset(path, data);
	}

	private static void onImage(String path, int width, int height, int[] argb) {
		ImageStore.put(path, width, height, argb);
	}

	private static void onReady() {
		if (booted || crashed) {
			return;
		}
		booted = true;

		Logging.GAMEHANDLER.info("All assets delivered; booting game core.");
		String[] args = Platform.isDebugEnabled() ? new String[] { "--debug-level" } : new String[0];
		try {
			Game.main(args);
			Logging.GAMEHANDLER.info("Game core booted; web tick loop now drives the game.");
		} catch (Throwable t) {
			crashed = true;
			CrashHandler.crashHandle(t);
		}
	}

	private static void onTick() {
		if (!booted || crashed) {
			return;
		}
		try {
			Initializer.step();
		} catch (Throwable t) {
			crashed = true;
			CrashHandler.crashHandle(t);
		}
	}

	private static void onKey(String code, boolean down) {
		if (!booted || crashed || Game.input == null) {
			return;
		}
		try {
			Game.input.receiveKey(code, down);
		} catch (Throwable t) {
			crashed = true;
			CrashHandler.crashHandle(t);
		}
	}

	private static void onTyped(String text) {
		if (!booted || crashed || Game.input == null || text == null || text.isEmpty()) {
			return;
		}
		try {
			Game.input.receiveTyped(text);
		} catch (Throwable t) {
			crashed = true;
			CrashHandler.crashHandle(t);
		}
	}
}
