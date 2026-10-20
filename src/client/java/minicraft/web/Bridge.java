package minicraft.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSByRef;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * JavaScript-to-Java callback registration. main.js stores the functors on window.__mc
 * and invokes them for asset delivery, the frame loop, and keyboard events.
 */
public final class Bridge {
	private Bridge() {
	}

	@JSFunctor
	public interface OnBootBegin extends JSObject {
		void begin(int count);
	}

	@JSFunctor
	public interface OnAsset extends JSObject {
		void asset(String path, @JSByRef byte[] data);
	}

	@JSFunctor
	public interface OnImage extends JSObject {
		void image(String path, int width, int height, @JSByRef int[] argb);
	}

	@JSFunctor
	public interface OnReady extends JSObject {
		void ready();
	}

	@JSFunctor
	public interface OnTick extends JSObject {
		void tick();
	}

	@JSFunctor
	public interface OnKey extends JSObject {
		void key(String code, boolean down);
	}

	@JSFunctor
	public interface OnTyped extends JSObject {
		void typed(String text);
	}

	@JSFunctor
	public interface OnFlush extends JSObject {
		void flush();
	}

	@JSBody(params = { "begin", "asset", "image", "ready", "tick", "key", "typed", "flush" },
		script = "window.__mc = { begin: begin, asset: asset, image: image, ready: ready, tick: tick, key: key, typed: typed, flush: flush };")
	public static native void install(OnBootBegin begin, OnAsset asset, OnImage image, OnReady ready, OnTick tick, OnKey key, OnTyped typed, OnFlush flush);

	@JSBody(script = "window.__platform.onJavaReady();")
	public static native void notifyJavaReady();
}
