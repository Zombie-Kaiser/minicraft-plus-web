package minicraft.core.io;

import minicraft.core.CrashHandler;
import minicraft.util.Logging;
import minicraft.web.Platform;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Optional;

public class Sound {
	// Creates sounds from their respective files
	private static final HashMap<String, Sound> sounds = new HashMap<>();
	private static final LinkedList<AudioPointer> pointers = new LinkedList<>();

	/*
	 * Only 2/16/44100 and 1/16/44100 PCM_SIGNED are supported.
	 */

	// Web port: the JS side provides a 44100 Hz, 16-bit stereo PCM sink.
	private static final int SAMPLE_RATE = 44100;
	private static final int FRAME_SIZE = 4;
	private static final int internalBufferSize = (SAMPLE_RATE / 2) * FRAME_SIZE;
	private static final int MAX_BUFFER_SIZE = 4096;

	private final short[] raw;

	private class AudioPointer {
		private int offset = 0;

		public Optional<Short> getData() {
			if (offset == raw.length) return Optional.empty();
			return Optional.of(raw[offset++]);
		}
	}

	private Sound(short[] raw) {
		this.raw = raw;
	}

	public static void resetSounds() {
		sounds.clear();
	}

	public static void loadSound(String key, InputStream in, String pack) {
		try {
			// Web port: RIFF/WAVE parsing in place of AudioSystem.getAudioInputStream.
			byte[] all = readAllBytes(in);

			if (all.length < 12 || all[0] != 'R' || all[1] != 'I' || all[2] != 'F' || all[3] != 'F'
				|| all[8] != 'W' || all[9] != 'A' || all[10] != 'V' || all[11] != 'E') {
				Logging.RESOURCEHANDLER_SOUND.error("Unsupported audio format of file \"{}\" in pack \"{}\": not a RIFF/WAVE file",
					key, pack);
				return;
			}

			int channels = 0, sampleRate = 0, bits = 0;
			byte[] data = null;
			int pos = 12;
			while (pos + 8 <= all.length) {
				boolean isFmt = all[pos] == 'f' && all[pos + 1] == 'm' && all[pos + 2] == 't' && all[pos + 3] == ' ';
				boolean isData = all[pos] == 'd' && all[pos + 1] == 'a' && all[pos + 2] == 't' && all[pos + 3] == 'a';
				int size = (all[pos + 4] & 0xFF) | ((all[pos + 5] & 0xFF) << 8) | ((all[pos + 6] & 0xFF) << 16) | ((all[pos + 7] & 0xFF) << 24);
				int body = pos + 8;
				if (isFmt && body + 16 <= all.length) {
					int format = (all[body] & 0xFF) | ((all[body + 1] & 0xFF) << 8);
					channels = (all[body + 2] & 0xFF) | ((all[body + 3] & 0xFF) << 8);
					sampleRate = (all[body + 4] & 0xFF) | ((all[body + 5] & 0xFF) << 8) | ((all[body + 6] & 0xFF) << 16) | ((all[body + 7] & 0xFF) << 24);
					bits = (all[body + 14] & 0xFF) | ((all[body + 15] & 0xFF) << 8);
					if (format != 1) { // 1 = PCM
						Logging.RESOURCEHANDLER_SOUND.error("Unsupported audio format of file \"{}\" in pack \"{}\": non-PCM encoding {}",
							key, pack, format);
						return;
					}
				} else if (isData) {
					int end = Math.min(body + size, all.length);
					data = new byte[Math.max(0, end - body)];
					System.arraycopy(all, body, data, 0, data.length);
				}
				pos = body + size + (size & 1); // Chunks are word-aligned.
			}

			if (data == null || channels == 0) {
				Logging.RESOURCEHANDLER_SOUND.error("Could not load audio \"{}\" in pack \"{}\": missing fmt/data chunk",
					key, pack);
				return;
			}

			if (channels > 2 || sampleRate != 44100 || bits != 16) {
				Logging.RESOURCEHANDLER_SOUND.error("Unsupported audio format of file \"{}\" in pack \"{}\": {} Hz, {}-bit, {} channel(s)",
					key, pack, sampleRate, bits, channels);
			}

			short[] raw0 = new short[data.length / 2];
			for (int i = 0; i < raw0.length; ++i)
				raw0[i] = (short) (((data[i * 2] & 0xFF)) | ((data[i * 2 + 1] & 0xFF) << 8)); // WAVE PCM is little-endian

			short[] raw1;
			if (channels == 1) {
				raw1 = new short[raw0.length * 2];
				for (int i = 0; i < raw0.length; ++i) {
					raw1[i * 2] = raw0[i];
					raw1[i * 2 + 1] = raw0[i];
				}
			} else if (channels != 2) { // This should not be executed.
				Logging.RESOURCEHANDLER_SOUND.error(
					"Audio source \"{}\" in pack \"{}\" is neither mono nor stereo, which is not supported.",
					key, pack);
				return;
			} else {
				raw1 = raw0;
			}

			sounds.put(key, new Sound(raw1));
		} catch (IOException e) {
			CrashHandler.errorHandle(e, new CrashHandler.ErrorInfo("Audio Could not Load", CrashHandler.ErrorInfo.ErrorType.REPORT,
				String.format("Could not load audio: %s in pack: %s", key, pack)));
		}
	}

	private static byte[] readAllBytes(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int length;
		while ((length = in.read(buf)) != -1) {
			out.write(buf, 0, length);
		}
		return out.toByteArray();
	}

	/**
	 * Recommend {@link #play(String)} and {@link #loop(String, int)}.
	 */
	@Nullable
	public static Sound getSound(String key) {
		return sounds.get(key);
	}

	/**
	 * This method does safe check for {@link #play()}.
	 */
	public static void play(String key) {
		Sound sound = sounds.get(key);
		if (sound != null) sound.play();
	}

	/**
	 * This method does safe check for {@link #loop(int)}.
	 */
	public static void loop(String key, int count) {
		Sound sound = sounds.get(key);
		if (sound != null) sound.loop(count);
	}

	public static void tick() {
		// The JS sink reports how many bytes are still queued; proceed data and then buffer into it.
		// For 2/16/44100, 2940 bytes would be proceeded per tick.
		int used = Platform.audioBuffered();
		if (used > MAX_BUFFER_SIZE) return;
		int available = Math.min(internalBufferSize - used, MAX_BUFFER_SIZE) / 2; // in 16bit (short)
		if (available <= 0) return; // Skips tick if buffer is large causing latency
		byte[] buf = new byte[available * 2];
		short[] bufShort = new short[available];
		while (available > 0) {
			/* Audio Mixing Algorithm
			 * Reference Article: https://stackoverflow.com/a/25102339
			 * It is pointed out that, non-linear mixing algorithms are not the correct ways to perform mixing,
			 * but it should be instead handled by (dynamic range) compression.
			 * For now, C = sum{U_i} / n is not enough, but compression should be applied.
			 *
			 * It is noticed that for the quiet sounds, the sounds become quieter when mixed, even overall.
			 * So, an upward compression is required. We now define a quiet sound to be a signal smaller than
			 * a one-fourth of the maximum value.
			 * For each quiet signal, we gain an upward compression factor and finally multiplied altogether to the
			 * resultant value. And we can get the factor by a non-linear equation:
			 *   F = log2((D-U)/D+1)+1, where F is the factor, D is the one-fourth value, U is the signal value.
			 * Note that U ∈ [0, D), (D-U)/D ∈ (0, 1], F ∈ (1, 2].
			 * But we cannot have this too big, so we make this into F^(1/3), where 2^(1/3) is approximately 1.26.
			 * This can make sure that the factor would not go great.
			 * Then, make sure that the factor cannot overflow the value,
			 *   G = sqrt(product{F_i}),
			 * as a final factor, would then be used.
			 *
			 * Finally, if the value really goes close to the maximum value even overflow, like 0.9 of the maximum value,
			 * we can then apply an ultimate equation:
			 *   H = (J) / (J + 1) * E,
			 * where H is the value to add with the cut value (0.9 of the maximum), J is the exceeded value and
			 * E is the certain amount smaller than 0.1 of the maximum, say 0.05 of it here.
			 * We can get a compressed sound and also limited from the maximum, with 0.95x of the maximum.
			 */

			int n = 0;
			int sum = 0;
			double factor = 1;
			for (Iterator<AudioPointer> iterator = pointers.iterator(); iterator.hasNext(); ) {
				AudioPointer pointer = iterator.next();
				Optional<Short> d = pointer.getData();
				if (!d.isPresent()) iterator.remove();
				else {
					int val = d.get(); // Signed
					int net = Math.abs(val); // Bounds are halved for absolute of signed value
					if (net < 8192)
						factor *= Math.pow(Math.log1p((8192 - net) / 8192D) / Math.log(2) + 1, 1D/3);
					sum += val + 32768; // Turning to unsigned
					n++;
				}
			}

			if (n == 0) break; // No more data to be written at the moment
			double val = (double) sum / n - 32768; // To signed
			val *= Math.sqrt(factor);
			double net = Math.abs(val); // Bounds are halved for absolute of signed value
			if (net > 32768*.9D) {
				val = (32768*.9D + ((net - 32768*.9D) / (net - 32768*.9D + 1) * 32768/10D)) * Math.signum(val);
			}

			// val should be between -32768 and 32767 exclusively
			bufShort[bufShort.length - available] = (short) val;
			available--;
		}

		for (int i = 0; i < bufShort.length; i++) { // Little-endian, as expected by the web sink
			buf[i * 2] = (byte) bufShort[i];
			buf[i * 2 + 1] = (byte) (bufShort[i] >> 8);
		}
		Platform.audioWrite(buf);
	}

	public void play() {
		if (!(boolean) Settings.get("sound")) return;
		pointers.add(new AudioPointer());
	}

	/** @deprecated no longer supported, but reserved for future implementation. */
	@Deprecated
	public void loop(int count) {}
}
