package minicraft.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSByRef;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * Thin Java-to-JavaScript bridge implemented by web/main.js via window.__platform.
 */
public final class Platform {
	private Platform() {
	}

	/** A Java lambda that can be passed to JavaScript as a callback. */
	@JSFunctor
	public interface Task extends JSObject {
		void run();
	}

	/** Runs the task later on the browser event loop; coalesces bursts of work. */
	@JSBody(params = { "task", "ms" }, script = "setTimeout(task, ms);")
	public static native void scheduleLater(Task task, int ms);

	/** Displays a frame of ARGB pixels on the game canvas. */
	@JSBody(params = { "w", "h", "px" }, script = "window.__platform.present(w, h, px);")
	public static native void present(int w, int h, @JSByRef int[] px);

	@JSBody(params = { "k" }, script = "return window.__platform.storageGet(k);")
	public static native String storageGet(String k);

	@JSBody(params = { "k", "v" }, script = "window.__platform.storageSet(k, v);")
	public static native void storageSet(String k, String v);

	@JSBody(params = { "k" }, script = "window.__platform.storageRemove(k);")
	public static native void storageRemove(String k);

	@JSBody(params = { "msg" }, script = "window.__platform.alert(msg);")
	public static native void alert(String msg);

	@JSBody(params = { "msg" }, script = "window.__platform.log(msg);")
	public static native void log(String msg);

	@JSBody(params = { "url" }, script = "window.__platform.openUrl(url);")
	public static native void openUrl(String url);

	@JSBody(params = { "text" }, script = "window.__platform.clipboardWrite(text);")
	public static native void clipboardWrite(String text);

	@JSBody(script = "return window.__platform.now();")
	public static native double now();

	@JSBody(script = "window.__platform.exit();")
	public static native void exit();

	@JSBody(params = { "title" }, script = "window.__platform.setTitle(title);")
	public static native void setTitle(String title);

	/** Queues mixed 16-bit little-endian stereo PCM; returns false if the JS-side buffer is full. */
	@JSBody(params = { "data" }, script = "return window.__platform.audioWrite(data);")
	public static native boolean audioWrite(@JSByRef byte[] data);

	@JSBody(script = "return window.__platform.audioSampleRate();")
	public static native int audioSampleRate();

	@JSBody(script = "return window.__platform.isFullscreen();")
	public static native boolean isFullscreen();

	@JSBody(script = "window.__platform.toggleFullscreen();")
	public static native void toggleFullscreen();

	@JSBody(script = "return window.__platform.isDebugEnabled();")
	public static native boolean isDebugEnabled();

	@JSBody(script = "return window.__platform.autotest();")
	public static native boolean autotest();

	@JSBody(script = "return window.__platform.canvasFocused();")
	public static native boolean canvasFocused();

	@JSBody(params = { "w", "h", "px" }, script = "window.__platform.screenshot(w, h, px);")
	public static native void screenshot(int w, int h, @JSByRef int[] px);

	/** Bytes currently queued in the JS-side audio ring buffer. */
	@JSBody(script = "return window.__platform.audioBuffered();")
	public static native int audioBuffered();

	/** Last known clipboard text (kept current by the JS side); empty string if none. */
	@JSBody(script = "return window.__platform.clipboardRead();")
	public static native String clipboardRead();
}
