package minicraft.web;

import minicraft.gfx.MinicraftImage;

import java.util.HashMap;

/**
 * Decoded-image registry. main.js decodes every bundled PNG and delivers the raw
 * ARGB pixels via Bridge.OnImage; game code loads them by resource path instead
 * of using ImageIO.
 */
public final class ImageStore {
	private static final class Entry {
		final int width;
		final int height;
		final int[] argb;

		Entry(int width, int height, int[] argb) {
			this.width = width;
			this.height = height;
			this.argb = argb;
		}
	}

	private static final HashMap<String, Entry> images = new HashMap<>();

	private ImageStore() {
	}

	public static void put(String path, int width, int height, int[] argb) {
		if (path == null || argb == null || width < 1 || height < 1) {
			return;
		}
		images.put(path, new Entry(width, height, argb));
	}

	public static boolean exists(String path) {
		return images.containsKey(path);
	}

	public static int getWidth(String path) {
		Entry e = images.get(path);
		return e == null ? 0 : e.width;
	}

	public static int getHeight(String path) {
		Entry e = images.get(path);
		return e == null ? 0 : e.height;
	}

	/** Full-size load; null if the image was never delivered. */
	public static MinicraftImage load(String path) {
		Entry e = images.get(path);
		if (e == null) {
			return null;
		}
		return new MinicraftImage(e.argb, e.width, e.height);
	}

	/**
	 * Loads the top-left {@code width}x{@code height} region, zero-padded when the
	 * source is smaller, matching the old {@code MinicraftImage(BufferedImage, w, h)}
	 * constructor semantics.
	 */
	public static MinicraftImage load(String path, int width, int height) {
		Entry e = images.get(path);
		if (e == null) {
			return null;
		}

		MinicraftImage img = new MinicraftImage(width, height);
		int copyW = Math.min(width, e.width);
		int copyH = Math.min(height, e.height);
		for (int y = 0; y < copyH; y++) {
			for (int x = 0; x < copyW; x++) {
				int argb = e.argb[x + y * e.width];
				if (argb >>> 24 == 0) {
					continue; // stays 0 = transparent in minicraft encoding
				}
				img.pixels[x + y * width] = (1 << 24) | (argb & 0xFF_FF_FF);
			}
		}
		return img;
	}
}
