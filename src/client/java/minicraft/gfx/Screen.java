package minicraft.gfx;

import minicraft.core.Renderer;
import minicraft.core.Updater;
import minicraft.gfx.SpriteLinker.LinkedSprite;
import minicraft.gfx.SpriteLinker.SpriteType;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Screen {

	public static final int w = Renderer.WIDTH; // Width of the screen
	public static final int h = Renderer.HEIGHT; // Height of the screen
	public static final Point center = new Point(w / 2, h / 2);

	private static final int MAXDARK = 128;

	/// x and y offset of screen:
	private int xOffset;
	private int yOffset;

	// Used for mirroring an image:
	private static final int BIT_MIRROR_X = 0x01; // Written in hexadecimal; binary: 01
	private static final int BIT_MIRROR_Y = 0x02; // Binary: 10

	private final int[] pixels;

	private final ArrayDeque<Rendering> renderings = new ArrayDeque<>();
	private final LightOverlay lightOverlay;
	private ClearRendering lastClearRendering = null;

	// Outdated Information:
	// Since each sheet is 256x256 pixels, each one has 1024 8x8 "tiles"
	// So 0 is the start of the item sheet 1024 the start of the tile sheet, 2048 the start of the entity sheet,
	// And 3072 the start of the gui sheet

	public Screen(int[] pixels) {
		/// Screen width and height are determined by the actual game window size, meaning the screen is only as big as the window.
		this.pixels = pixels;
		lightOverlay = new LightOverlay();
	}

	private interface Rendering {
		/** Invoked by {@link Renderer#render()}. */
		void render();
	}

	private void queue(Rendering rendering) {
		renderings.add(rendering);
	}

	private static abstract class ClearRendering implements Rendering {}

	private static class SolidClearRendering extends ClearRendering {
		private final int color;

		public SolidClearRendering(int color) {
			this.color = color;
		}

		@Override
		public void render() {
			Arrays.fill(pixels(), color & 0xFF_FF_FF);
		}
	}

	private static class PlainClearRendering extends ClearRendering {
		@Override
		public void render() {
			Arrays.fill(pixels(), 0);
		}
	}

	private static int[] pixels() {
		return Renderer.framePixels;
	}

	private class SpriteRendering implements Rendering {
		private final int xp, yp, xt, yt, tw, th, mirrors, whiteTint, color;
		private final boolean fullBright;
		private final MinicraftImage sheet;

		public SpriteRendering(int xp, int yp, int xt, int yt, int tw, int th,
		                       int mirrors, int whiteTint, boolean fullBright, int color, MinicraftImage sheet) {
			this.xp = xp;
			this.yp = yp;
			this.xt = xt;
			this.yt = yt;
			this.tw = tw;
			this.th = th;
			this.mirrors = mirrors;
			this.whiteTint = whiteTint;
			this.fullBright = fullBright;
			this.color = color;
			this.sheet = sheet;
		}

		@Override
		public void render() {
			int toffs = xt + yt * sheet.width;
			// Determines if the image should be mirrored...
			boolean mirrorX = (mirrors & BIT_MIRROR_X) > 0; // Horizontally.
			boolean mirrorY = (mirrors & BIT_MIRROR_Y) > 0; // Vertically.
			for (int y = 0; y < th; ++y) { // Relative
				if (y + yp < 0) continue; // If the pixel is out of bounds, then skip the rest of the loop.
				if (y + yp >= h) break;
				int sy = mirrorY ? th - 1 - y : y; // Source relative; reverse if necessary
				for (int x = 0; x < tw; ++x) { // Relative
					if (x + xp < 0) continue; // Skip rest if out of bounds.
					if (x + xp >= w) break;
					int sx = mirrorX ? tw - 1 - x : x; // Source relative; reverse if necessary
					int col = sheet.pixels[toffs + sx + sy * sheet.width]; // Gets the color of the current pixel from the value stored in the sheet.
					if (col >> 24 != 0) { // if not transparent
						int index = (xp + x) + (yp + y) * w;
						if (whiteTint != -1 && col == 0x1FFFFFF) {
							// If this is white, write the whiteTint over it
							pixels[index] = Color.upgrade(whiteTint);
						} else {
							// Inserts the colors into the image
							if (fullBright) {
								pixels[index] = Color.WHITE;
							} else {
								if (color != 0) {
									pixels[index] = color;
								} else {
									pixels[index] = Color.upgrade(col);
								}
							}
						}
					}
				}
			}
		}
	}

	private static class FillRectRendering implements Rendering {
		private final int xp, yp, w, h, color;

		public FillRectRendering(int xp, int yp, int w, int h, int color) {
			this.xp = xp;
			this.yp = yp;
			this.w = w;
			this.h = h;
			this.color = color;
		}

		@Override
		public void render() {
			int x0 = Math.max(0, xp);
			int y0 = Math.max(0, yp);
			int x1 = Math.min(Screen.w, xp + w);
			int y1 = Math.min(Screen.h, yp + h);
			int c = color & 0xFF_FF_FF;
			int[] px = pixels();
			for (int y = y0; y < y1; y++) {
				int rowStart = y * Screen.w;
				for (int x = x0; x < x1; x++) {
					px[x + rowStart] = c;
				}
			}
		}
	}

	private static class DrawRectRendering implements Rendering {
		private final int xp, yp, w, h, color;

		public DrawRectRendering(int xp, int yp, int w, int h, int color) {
			this.xp = xp;
			this.yp = yp;
			this.w = w;
			this.h = h;
			this.color = color;
		}

		@Override
		public void render() {
			int c = color & 0xFF_FF_FF;
			int[] px = pixels();
			int x0 = Math.max(0, xp);
			int y0 = Math.max(0, yp);
			int x1 = Math.min(Screen.w - 1, xp + w);
			int y1 = Math.min(Screen.h - 1, yp + h);

			for (int x = x0; x <= x1; x++) {
				px[x + y0 * Screen.w] = c;
				px[x + y1 * Screen.w] = c;
			}
			for (int y = y0; y <= y1; y++) {
				px[x0 + y * Screen.w] = c;
				px[x1 + y * Screen.w] = c;
			}
		}
	}

	private static class DrawLineRendering implements Rendering {
		private final int x0, y0, x1, y1, color;

		public DrawLineRendering(int x0, int y0, int x1, int y1, int color) {
			this.x0 = x0;
			this.y0 = y0;
			this.x1 = x1;
			this.y1 = y1;
			this.color = color;
		}

		@Override
		public void render() {
			int c = color & 0xFF_FF_FF;
			int[] px = pixels();
			int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
			int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
			int err = dx - dy;
			int x = x0, y = y0;
			while (true) {
				if (x >= 0 && x < Screen.w && y >= 0 && y < Screen.h) {
					px[x + y * Screen.w] = c;
				}
				if (x == x1 && y == y1) break;
				int e2 = 2 * err;
				if (e2 > -dy) {
					err -= dy;
					x += sx;
				}
				if (e2 < dx) {
					err += dx;
					y += sy;
				}
			}
		}
	}

	/** Placeholder way, for Sign cursor rendering */
	private static class DrawLineSpecialRendering implements Rendering {
		private final int x0, y0, l;
		private final @MagicConstant(intValues = {0, 1}) int axis; // 0: x-axis; 1: Y-axis

		public DrawLineSpecialRendering(int x0, int y0, int l, int axis) {
			this.x0 = x0;
			this.y0 = y0;
			this.l = l;
			this.axis = axis;
		}

		@Override
		public void render() {
			int[] px = pixels();
			switch (axis) {
				case 0:
					for (int i = 0; i < l; i++) { // 1 pixel high and 8 pixel wide
						int idx = x0 + i + y0 * Screen.w;
						px[idx] = Color.getLightnessFromRGB(px[idx]) >= .5 ? Color.BLACK : Color.WHITE;
					}
					break;
				case 1:
					for (int i = 0; i < l; i++) { // 8 pixel high and 1 pixel wide
						int idx = x0 + (y0 + i) * Screen.w;
						px[idx] = Color.getLightnessFromRGB(px[idx]) >= .5 ? Color.BLACK : Color.WHITE;
					}
					break;
			}
		}
	}

	private class OverlayRendering implements Rendering {
		private final int currentLevel, xa, ya;
		private final double darkFactor;

		private OverlayRendering(int currentLevel, int xa, int ya, double darkFactor) {
			this.currentLevel = currentLevel;
			this.xa = xa;
			this.ya = ya;
			this.darkFactor = darkFactor;
		}

		@Override
		public void render() {
			double alpha = lightOverlay.getOverlayOpacity(currentLevel, darkFactor);
			int[] overlayPx = lightOverlay.render(xa, ya);

			float lighten = 0.02f; // AlphaComposite.SRC_OVER with white at 2%
			float shade = (float) alpha;
			for (int i = 0; i < pixels.length; i++) {
				int p = pixels[i];
				int r = (p >> 16) & 0xFF;
				int g = (p >> 8) & 0xFF;
				int b = p & 0xFF;

				r = Math.round(255 * lighten + r * (1 - lighten));
				g = Math.round(255 * lighten + g * (1 - lighten));
				b = Math.round(255 * lighten + b * (1 - lighten));

				int ova = (int) ((overlayPx[i] >>> 24) * shade); // Combined overlay opacity 0..255
				if (ova > 0) {
					r = Math.round(r * (255 - ova) / 255f);
					g = Math.round(g * (255 - ova) / 255f);
					b = Math.round(b * (255 - ova) / 255f);
				}

				pixels[i] = (r << 16) | (g << 8) | b;
			}
		}
	}

	/**
	 * Clears all the colors on the screen
	 */
	public void clear(int color) {
		// Turns each pixel into a single color (clearing the screen!)
		if (color == 0) {
			queueClearRendering(new PlainClearRendering());
		} else {
			queueClearRendering(new SolidClearRendering(color));
		}
	}

	private void queueClearRendering(ClearRendering clearRendering) {
		lastClearRendering = clearRendering;
		queue(clearRendering);
	}

	public void flush() {
		Rendering rendering;
		do { // Skips until the latest clear rendering is obtained.
			rendering = renderings.poll(); // This can prevent redundant renderings operated.
			if (rendering == null) return;
		} while (rendering != lastClearRendering);
		do { // Renders all renderings until all are operated.
			rendering.render();
		} while ((rendering = renderings.poll()) != null);
	}

	public void render(int xp, int yp, int xt, int yt, int bits, MinicraftImage sheet) {
		render(xp, yp, xt, yt, bits, sheet, -1);
	}

	public void render(int xp, int yp, int xt, int yt, int bits, MinicraftImage sheet, int whiteTint) {
		render(xp, yp, xt, yt, bits, sheet, whiteTint, false);
	}

	/**
	 * This method takes care of assigning the correct spritesheet to assign to the sheet variable
	 **/
	public void render(int xp, int yp, int xt, int yt, int bits, MinicraftImage sheet, int whiteTint, boolean fullbright) {
		render(xp, yp, xt, yt, bits, sheet, whiteTint, fullbright, 0);
	}

	public void render(int xp, int yp, LinkedSprite sprite) {
		render(xp, yp, sprite.getSprite());
	}

	public void render(int xp, int yp, Sprite sprite) {
		render(xp, yp, sprite, false);
	}

	public void render(int xp, int yp, Sprite sprite, boolean fullbright) {
		render(xp, yp, sprite, 0, fullbright, 0);
	}

	public void render(int xp, int yp, Sprite sprite, int mirror, boolean fullbright) {
		render(xp, yp, sprite, mirror, fullbright, 0);
	}

	public void render(int xp, int yp, Sprite sprite, int mirror, boolean fullbright, int color) {
		boolean mirrorX = (mirror & BIT_MIRROR_X) > 0; // Horizontally.
		boolean mirrorY = (mirror & BIT_MIRROR_Y) > 0; // Vertically.
		for (int r = 0; r < sprite.spritePixels.length; r++) {
			int lr = mirrorY ? sprite.spritePixels.length - 1 - r : r;
			for (int c = 0; c < sprite.spritePixels[lr].length; c++) {
				Sprite.Px px = sprite.spritePixels[lr][mirrorX ? sprite.spritePixels[lr].length - 1 - c : c];
				render(xp + c * 8, yp + r * 8, px, mirror, sprite.color, fullbright, color);
			}
		}
	}

	public void render(int xp, int yp, Sprite.Px pixel) {
		render(xp, yp, pixel, -1);
	}

	public void render(int xp, int yp, Sprite.Px pixel, int whiteTint) {
		render(xp, yp, pixel, 0, whiteTint);
	}

	public void render(int xp, int yp, Sprite.Px pixel, int mirror, int whiteTint) {
		render(xp, yp, pixel, mirror, whiteTint, false);
	}

	public void render(int xp, int yp, Sprite.Px pixel, int mirror, int whiteTint, boolean fullbright) {
		render(xp, yp, pixel, mirror, whiteTint, fullbright, 0);
	}

	public void render(int xp, int yp, Sprite.Px pixel, int mirror, int whiteTint, boolean fullbrightness, int color) {
		render(xp, yp, pixel.x, pixel.y, pixel.mirror ^ mirror, pixel.sheet, whiteTint, fullbrightness, color);
	}

	/**
	 * Renders an object from the sprite sheet based on screen coordinates, tile (SpriteSheet location), colors, and bits (for mirroring). I believe that xp and yp refer to the desired position of the upper-left-most pixel.
	 */
	public void render(int xp, int yp, int xt, int yt, int bits, MinicraftImage sheet, int whiteTint, boolean fullbrightness, int color) {
		if (sheet == null) return; // Verifying that sheet is not null.

		// xp and yp are originally in level coordinates, but offset turns them to screen coordinates.
		// xOffset and yOffset account for screen offset
		render(xp - xOffset, yp - yOffset, xt * 8, yt * 8, 8, 8, sheet, bits, whiteTint, fullbrightness, color);
	}

	public void render(int xp, int yp, int xt, int yt, int tw, int th, MinicraftImage sheet) {
		render(xp, yp, xt, yt ,tw, th, sheet, 0);
	}
	public void render(int xp, int yp, int xt, int yt, int tw, int th, MinicraftImage sheet, int mirrors) {
		render(xp, yp, xt, yt ,tw, th, sheet, mirrors, -1);
	}
	public void render(int xp, int yp, int xt, int yt, int tw, int th, MinicraftImage sheet, int mirrors, int whiteTint) {
		render(xp, yp, xt, yt, tw, th, sheet, mirrors, whiteTint, false);
	}
	public void render(int xp, int yp, int xt, int yt, int tw, int th, MinicraftImage sheet, int mirrors, int whiteTint, boolean fullbright) {
		render(xp, yp, xt, yt, tw, th, sheet, mirrors, whiteTint, fullbright, 0);
	}
	// Any single pixel from the image can be rendered using this method.
	public void render(int xp, int yp, int xt, int yt, int tw, int th, MinicraftImage sheet, int mirrors, int whiteTint, boolean fullBright, int color) {
		if (sheet == null) return; // Verifying that sheet is not null.

		// Validation check
		if (xt + tw > sheet.width && yt + th > sheet.height) {
			render(xp, yp, 0, 0, mirrors, Renderer.spriteLinker.missingSheet(SpriteType.Item));
			return;
		}

		queue(new SpriteRendering(xp, yp, xt, yt, tw, th, mirrors, whiteTint, fullBright, color, sheet));
	}

	public void fillRect(int xp, int yp, int w, int h, int color) {
		queue(new FillRectRendering(xp, yp, w, h, color));
	}

	public void drawRect(int xp, int yp, int w, int h, int color) {
		queue(new DrawRectRendering(xp, yp, w, h, color));
	}

	/**
	 * Draw a straight line along an axis.
	 * @param axis The axis to draw along: {@code 0} for x-axis; {@code 1} for y-axis
	 * @param l The length of the line
	 */
	public void drawAxisLine(int xp, int yp, @MagicConstant(intValues = {0, 1}) int axis, int l, int color) {
		switch (axis) {
			case 0: queue(new DrawLineRendering(xp, yp, xp + l, yp, color)); break;
			case 1: queue(new DrawLineRendering(xp, yp, xp, yp + l, color)); break;
		}
	}

	public void drawLine(int x0, int y0, int x1, int y1, int color) {
		queue(new DrawLineRendering(x0, y0, x1, y1, color));
	}

	/** Placeholder line drawing method specialized for sign cursor drawing */
	public void drawLineSpecial(int x0, int y0, @MagicConstant(intValues = {0, 1}) int axis, int l) {
		queue(new DrawLineSpecialRendering(x0, y0, l, axis));
	}

	/**
	 * Sets the offset of the screen
	 */
	public void setOffset(int xOffset, int yOffset) {
		// This is called in few places, one of which is level.renderBackground, right before all the tiles are rendered. The offset is determined by the Game class (this only place renderBackground is called), by using the screen's width and the player's position in the level.
		// In other words, the offset is a conversion factor from level coordinates to screen coordinates. It makes a certain coord in the level the upper left corner of the screen, when subtracted from the tile coord.

		this.xOffset = xOffset;
		this.yOffset = yOffset;
	}

	/* Used for the scattered dots at the edge of the light radius underground.

		These values represent the minimum light level, on a scale from 0 to 25 (255/10), 0 being no light, 25 being full light (which will be portrayed as transparent on the overlay lightScreen pixels) that a pixel must have in order to remain lit (not black).
		each row and column is repeated every 4 pixels in the proper direction, so the pixel lightness minimum varies. It's highly worth note that, as the rows progress and loop, there's two sets or rows (1,4 and 2,3) whose values in the same column add to 15. The exact same is true for columns (sets are also 1,4 and 2,3), execpt the sums of values in the same row and set differ for each row: 10, 18, 12, 20. Which... themselves... are another set... adding to 30... which makes sense, sort of, since each column totals 15+15=30.
		In the end, "every other every row", will need, for example in column 1, 15 light to be lit, then 0 light to be lit, then 12 light to be lit, then 3 light to be lit. So, the pixels of lower light levels will generally be lit every other pixel, while the brighter ones appear more often. The reason for the variance in values is to provide EVERY number between 0 and 15, so that all possible light levels (below 16) are represented fittingly with their own pattern of lit and not lit.
		16 is the minimum pixel lighness required to ensure that the pixel will always remain lit.
	*/

	/**
	 * Overlays the screen with pixels
	 */
	public void overlay(int currentLevel, int xa, int ya) {
		double darkFactor = 0;
		if (currentLevel >= 3 && currentLevel < 5) {
			int transTime = Updater.dayLength / 4;
			double relTime = (Updater.tickCount % transTime) * 1.0 / transTime;

			switch (Updater.getTime()) {
				case Morning:
					darkFactor = Updater.pastDay1 ? (1 - relTime) * MAXDARK : 0;
					break;
				case Day:
					darkFactor = 0;
					break;
				case Evening:
					darkFactor = relTime * MAXDARK;
					break;
				case Night:
					darkFactor = MAXDARK;
					break;
			}

			if (currentLevel > 3) darkFactor -= (darkFactor < 10 ? darkFactor : 10);
		} else if (currentLevel >= 5)
			darkFactor = MAXDARK;

		// The Integer array of pixels to overlay the screen with.
		queue(new OverlayRendering(currentLevel, xa, ya, darkFactor));
	}

	public void renderLight(int x, int y, int r) {
		// Applies offsets:
		lightOverlay.renderLight(x - xOffset, y - yOffset, r);
	}

	/**
	 * Software reimplementation of the original AWT-based light overlay. The radial
	 * gradient alpha at stop k (0..50) equals the original BigDecimal computation
	 * {@code 255 - i^4*255}, and pixels are painted SrcOver-white into the gray
	 * buffer just like the Java2D original.
	 */
	private static class LightOverlay {
		private static final int[] dither = new int[] {
			0, 8, 2, 10,
			12, 4, 14, 6,
			3, 11, 1, 9,
			15, 7, 13, 5
		};

		public final int[] graAlphas; // 51 gradient stops: white with alpha 255 - (k/50)^4 * 255.
		public final int[] buffer; // Gray lightness grades 0..255.
		public final int[] olPixels; // ARGB alpha-only overlay pixels.
		public final HashMap<@NotNull Point, @NotNull Integer> lights = new HashMap<>();

		public LightOverlay() {
			buffer = new int[w * h];
			olPixels = new int[w * h];
			graAlphas = new int[51];
			for (int k = 0; k <= 50; k++) {
				// Exact equivalent of BigDecimal ONE.divide(50) ^ 4 * 255, truncated.
				long v = (long) k * k * k * k * 255 / (50L * 50 * 50 * 50);
				graAlphas[k] = 255 - (int) v;
			}
		}

		/**
		 * Gets the overlay light darkness opacity instantly.
		 * @param currentLevel the current level index of the target
		 * @param darkFactor the tint factor to darken, from {@code 1} to {@code 128}
		 * @return opacity of darkness from {@code 0} to {@code 1}
		 */
		public double getOverlayOpacity(int currentLevel, double darkFactor) {
			// The above if statement is simply comparing the light level stored in oPixels with the minimum light level stored in dither. if it is determined that the oPixels[i] is less than the minimum requirements, the pixel is considered "dark", and the below is executed...
			if (currentLevel < 3) { // if in caves...
				// in the caves, not being lit means being pitch black.
				return 1;
			} else {
				// Outside the caves, not being lit simply means being darker.
				return darkFactor / 160; // darkens the color one shade.
			}
		}

		public void renderLight(int x, int y, int r) {
			lights.put(new Point(x, y), r);
		}

		public int[] render(int xa, int ya) {
			Arrays.fill(buffer, 0);
			for (Map.Entry<Point, Integer> e : lights.entrySet()) {
				int x = e.getKey().x, y = e.getKey().y, r = e.getValue();
				boolean[] surrounds = new boolean[8];
				for (int xx = -16; xx < 17; ++xx) {
					for (int yy = -16; yy < 17; ++yy) {
						if (xx != 0 || yy != 0) {
							Point pp = new Point(x + xx, y + yy);
							if (lights.containsKey(pp) && r <= lights.get(pp)) {
								double theta = Math.atan2(yy, xx);
								if (theta < 0) theta += 2 * Math.PI; // Ensures it is positive.
								surrounds[(int) (theta * 4 / Math.PI)] = true;
							}
						}
					}
				}

				// Reduce lighting circles on screen
				if (allSurrounded(surrounds)) {
					fillWhiteRect(x - 8, y - 8, 16, 16);
				} else {
					paintGradient(x, y, r);
				}
			}
			lights.clear();

			for (int x = 0; x < w; ++x) {
				for (int y = 0; y < h; ++y) {
					int i = x + y * w;
					// Grade of lightness
					int grade = buffer[i] & 0xFF;
					// (a + b) & 3 acts like (a + b) % 4
					if (grade / 10 <= dither[((x + xa) & 3) + ((y + ya) & 3) * 4]) {
						olPixels[i] = (255 - grade) << 24;
					} else {
						olPixels[i] = 0;
					}
				}
			}
			return olPixels;
		}

		private static boolean allSurrounded(boolean[] surrounds) {
			for (boolean s : surrounds) {
				if (!s) return false;
			}
			return true;
		}

		private void fillWhiteRect(int xp, int yp, int width, int height) {
			int x0 = Math.max(0, xp);
			int y0 = Math.max(0, yp);
			int x1 = Math.min(w, xp + width);
			int y1 = Math.min(h, yp + height);
			for (int y = y0; y < y1; y++) {
				int rowStart = y * w;
				for (int x = x0; x < x1; x++) {
					buffer[x + rowStart] = 255;
				}
			}
		}

		/**
		 * Fills the aliased circle of radius {@code r} centered on ({@code cx},{@code cy})
		 * with the radial gradient, compositing SrcOver-white into the gray buffer.
		 */
		private void paintGradient(int cx, int cy, int r) {
			if (r <= 0) return;
			int x0 = Math.max(0, cx - r);
			int y0 = Math.max(0, cy - r);
			int x1 = Math.min(w - 1, cx + r);
			int y1 = Math.min(h - 1, cy + r);
			double rr = (double) r * r;

			for (int y = y0; y <= y1; y++) {
				double dy = y + 0.5 - cy;
				for (int x = x0; x <= x1; x++) {
					double dx = x + 0.5 - cx;
					double t2 = (dx * dx + dy * dy) / rr;
					if (t2 > 1.0) continue; // Outside the oval; no antialiasing.

					double a;
					if (t2 >= 1.0) {
						a = graAlphas[50];
					} else {
						double t = Math.sqrt(t2);
						double pos = t * 50.0;
						int k = (int) pos;
						if (k > 49) k = 49;
						double f = pos - k;
						a = graAlphas[k] + (graAlphas[k + 1] - graAlphas[k]) * f;
					}

					int i = x + y * w;
					int g = buffer[i];
					// SrcOver: white source with alpha a over gray destination g.
					int out = (int) (a + g - a * g / 255.0 + 0.5);
					if (out > 255) out = 255;
					buffer[i] = out;
				}
			}
		}
	}
}
