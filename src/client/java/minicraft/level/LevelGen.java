package minicraft.level;

import minicraft.core.io.Settings;
import minicraft.gfx.Point;

import minicraft.level.biome.Biome;
import minicraft.level.biome.Biomes;
import minicraft.level.noise.LevelNoise;

import minicraft.core.Game;

import minicraft.level.tile.Tiles;
import minicraft.util.Simplex;
import org.tinylog.Logger;


import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LevelGen {
	private static long worldSeed = 0;
	private static final Random random = new Random(worldSeed);
	private static final Simplex noise = new Simplex(worldSeed);
	public double[] values; // An array of doubles, used to help making noise for the map
	private final int w, h; // Width and height of the map
	private static final int stairRadius = 15;

	private static final int NOISE_LAYER_DIFF = 100;

	public static final int SKY_LEVEL = 1, SURFACE_LEVEL = 0, IRON_LEVEL = -1, GOLD_LEVEL = -2, GEM_LEVEL = -3, DUNGEON_LEVEL = -4;

	/**
	 * This creates noise to create random values for level generation
	 */
	public LevelGen(int w, int h, int featureSize) { this(0, 0, w, h, featureSize, 0); }

	public LevelGen(int w, int h, int featureSize, int layer) { this(0, 0, w, h, featureSize, layer); }

	public LevelGen(int xOffset, int yOffset, int w, int h, int featureSize) { this(xOffset, yOffset, w, h, featureSize, 0); }

	public LevelGen(int xOffset, int yOffset, int w, int h, int featureSize, int layer) {
		this.w = w;
		this.h = h;

		values = new double[w * h];

		for(int x = 0; x < w; x++)
			for(int y = 0; y < h; y++) {
				setSample(x, y, noise.noise3((x + xOffset) / (double)featureSize, (y + yOffset) / (double)featureSize, layer * NOISE_LAYER_DIFF));
			}
	}

	private double sample(int x, int y) {
		return values[(x & (w - 1)) + (y & (h - 1)) * w];
	} // This merely returns the value, like Level.getTile(x, y).

	private void setSample(int x, int y, double value) {
		/*
		 * This method is short, but difficult to understand. This is what I think it does:
		 *
		 * The values array is like a 2D array, but formatted into a 1D array; so the basic "x + y * w" is used to access a given value.
		 *
		 * The value parameter is a random number, above set to be a random decimal from -1 to 1.
		 *
		 * From above, we can see that the x and y values passed in range from 0 to the width/height, and increment by a certain constant known as the "featureSize".
		 * This implies that the locations chosen from this array, to put the random value in, somehow determine the size of biomes, perhaps.
		 * The x/y value is taken and AND'ed with the size-1, which could be 127. This just caps the value at 127; however, it shouldn't be higher in the first place, so it is merely a safety measure.
		 *
		 * In other words, this is just "values[x + y * w] = value;"
		 */
		values[(x & (w - 1)) + (y & (h - 1)) * w] = value;
	}

	static void advanceChunk(ChunkManager chunkManager, int x, int y, int level, long seed) {
		int stage = chunkManager.getChunkStage(x, y);
		final int S = ChunkManager.CHUNK_SIZE;
		LevelNoise noise = chunkManager.getChunkNoise(x, y);
		switch(stage) {
			case ChunkManager.CHUNK_STAGE_NONE:
				chunkManager.setChunkNoise(x, y, new LevelNoise(seed, S * x, S * y, 0, S, S));
				chunkManager.setChunkStage(x, y, ChunkManager.CHUNK_STAGE_PRIMED_NOISE);
				break;
			case ChunkManager.CHUNK_STAGE_PRIMED_NOISE:
				for(int tileX = 0; tileX < S; tileX++)
					for(int tileY = 0; tileY < S; tileY++) {
						Biome biome = Biomes.getLayerBiomes(level).getClosestBiome(noise, tileX, tileY);
						chunkManager.setBiome(tileX + S * x, tileY + S * y, biome);
					}
				chunkManager.setChunkStage(x, y, ChunkManager.CHUNK_STAGE_ASSIGNED_BIOMES);
				break;
			case ChunkManager.CHUNK_STAGE_ASSIGNED_BIOMES:
				for(int tileX = 0; tileX < S; tileX++)
					for(int tileY = 0; tileY < S; tileY++) {
						chunkManager.getBiome(tileX + S * x, tileY + S * y).generate(chunkManager, tileX + S * x, tileY + S * y);
					}
				chunkManager.setChunkStage(x, y, ChunkManager.CHUNK_STAGE_UNFINISHED_STAIRS);
				break;
		}
	}

	static ChunkManager createAndValidateMap(int w, int h, int level, long seed) {
		ChunkManager cm = new ChunkManager();
		for(int i = 0; i < w / ChunkManager.CHUNK_SIZE; i++)
			for(int j = 0; j < h / ChunkManager.CHUNK_SIZE; j++)
				for(int k = ChunkManager.CHUNK_STAGE_NONE; k < ChunkManager.CHUNK_STAGE_UNFINISHED_STAIRS; k++)
					advanceChunk(cm, i, j, level, seed);
		return cm;
	}

	/**
	 * Dev tool for previewing generated maps; requires Swing and is disabled on the web port.
	 */
	public static void main(String[] args) {
	}

	// Used to easily interface with a list of chunks
}
