package minicraft.gfx;

import minicraft.gfx.SpriteLinker.LinkedSprite;
import minicraft.util.Logging;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.Objects;

/**
 * Although we have SpriteLinker, we still need SpriteSheet for buffering.
 * Our current rendering system still depends on this array.
 */
public class MinicraftImage {
	/**
	 * Each sprite tile size.
	 */
	public static final int boxWidth = 8;

	public final int width, height; // Width and height of the sprite sheet
	public final int[] pixels; // Integer array of the image's pixels

	/**
	 * Initializes a {@code MinicraftImage} instance from the provided size.
	 * All values are filled with zero after construction.
	 * @param width the final width of this image
	 * @param height the final height of this image
	 * @throws IllegalArgumentException if either {@code width} or {@code height} is zero or negative
	 */
	public MinicraftImage(int width, int height) {
		if (width < 1) throw new IllegalArgumentException("width cannot be zero or negative");
		if (height < 1) throw new IllegalArgumentException("height cannot be zero or negative");

		this.width = width;
		this.height = height;
		pixels = new int[width * height];
	}

	/**
	 * Constructs a {@code MinicraftImage} from a decoded ARGB pixel array
	 * (as delivered by the web image decoder).
	 * @param argb the source pixels in {@code 0xAARRGGBB} form; length must equal {@code width*height}
	 * @param width  the width of this image, must be a non-zero natural number
	 * @param height  the height of this image, must be a non-zero natural number
	 * @throws IllegalArgumentException if either {@code width} or {@code height} is zero or negative
	 * @see LinkedSprite
	 */
	public MinicraftImage(@NotNull int[] argb, int width, int height) {
		Objects.requireNonNull(argb, "argb");
		if (width < 1) throw new IllegalArgumentException("width cannot be zero or negative");
		if (height < 1) throw new IllegalArgumentException("height cannot be zero or negative");
		if (argb.length < width * height) throw new IllegalArgumentException("source pixel array is too small");

		this.width = width;
		this.height = height;
		pixels = new int[width * height];

		// Applying the RGB array into Minicraft rendering system 25 bits RBG array.
		for (int i = 0; i < pixels.length; i++) { // Loops through all the pixels
			int red;
			int green;
			int blue;

			// This should be a number from 0-255 that is the red of the pixel
			red = (argb[i] & 0xff0000);

			// Same, but green
			green = (argb[i] & 0xff00);

			// Same, but blue
			blue = (argb[i] & 0xff);

			// This stuff is to figure out if the pixel is transparent or not
			int transparent = 1;

			// A value of 0 means transparent, a value of 1 means opaque
			if (argb[i] >> 24 == 0x00) {
				transparent = 0;
			}

			// Actually put the data in the array
			// Uses 25 bits to store everything (8 for red, 8 for green, 8 for blue, and 1 for alpha)
			pixels[i] = (transparent << 24) + red + green + blue;
		}
	}

	/**
	 * Creates a {@link MinicraftImage} from decoded ARGB pixels with default dimension validation.
	 * @param argb the source pixels in {@code 0xAARRGGBB} form
	 * @param width the image width
	 * @param height the image height
	 * @return the constructed image
	 * @throws MinicraftImageDimensionIncompatibleException if the image's dimension is not a multiple of 8
	 */
	public static MinicraftImage createDefaultCompatible(int[] argb, int width, int height) throws MinicraftImageDimensionIncompatibleException {
		validateImageDimension(width, height);
		return new MinicraftImage(argb, width, height);
	}

	/**
	 * Validates if the provided image is compatible with the game's general sprite rendering system.
	 * @param width The image width
	 * @param height The image height
	 * @throws MinicraftImageDimensionIncompatibleException if the image's dimension is not a multiple of 8
	 */
	public static void validateImageDimension(int width, int height) throws MinicraftImageDimensionIncompatibleException {
		if (height % 8 != 0 || width % 8 != 0)
			throw new MinicraftImageDimensionIncompatibleException(width, height);
	}

	/**
	 * Validates if the provided image is respective to the required size.
	 * @param srcWidth The source width
	 * @param srcHeight The source height
	 * @param reqWidth The requested width
	 * @param reqHeight The requested height
	 * @throws MinicraftImageRequestOutOfBoundsException if the requested size is out of the image's dimension
	 */
	public static void validateImageDimension(int srcWidth, int srcHeight, int reqWidth, int reqHeight)
		throws MinicraftImageRequestOutOfBoundsException {
		if (srcWidth < reqWidth || srcHeight < reqHeight)
			throw new MinicraftImageRequestOutOfBoundsException(srcWidth, srcHeight, reqWidth, reqHeight);
	}

	public static class MinicraftImageDimensionIncompatibleException extends Exception {
		private final int width, height;

		public MinicraftImageDimensionIncompatibleException(int width, int height) {
			this.width = width;
			this.height = height;
		}

		public final int getWidth() { return width; }
		public final int getHeight() { return height; }
	}

	public static class MinicraftImageRequestOutOfBoundsException extends Exception {
		private final int srcW, srcH, rqtW, rqtH;
		public MinicraftImageRequestOutOfBoundsException(int srcW, int srcH, int rqtW, int rqtH) {
			this.srcW = srcW;
			this.srcH = srcH;
			this.rqtW = rqtW;
			this.rqtH = rqtH;
		}

		public final int getSourceWidth() { return srcW; }
		public final int getSourceHeight() { return srcH; }
		public final int getRequestedWidth() { return rqtW; }
		public final int getRequestedHeight() { return rqtH; }
	}
}
