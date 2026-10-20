package minicraft.web;

import org.teavm.runtime.fs.VirtualFile;
import org.teavm.runtime.fs.VirtualFileAccessor;
import org.teavm.runtime.fs.VirtualFileSystem;
import org.teavm.runtime.fs.VirtualFileSystemProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeSet;

/**
 * In-memory virtual file system backing java.io.File / FileReader / FileWriter and
 * java.nio.file for the web port. Game-written files are persisted to localStorage;
 * boot-time assets are transient (re-delivered from JS on every launch).
 */
public final class WebVfs implements VirtualFileSystem {
	private static final String STORAGE_KEY = "minicraft.web.vfs.v1";

	private static WebVfs instance;

	private final HashMap<String, byte[]> data = new HashMap<>();   // game-written, persisted
	private final HashMap<String, byte[]> assets = new HashMap<>(); // bundled resources, transient
	private final HashSet<String> dataDirs = new HashSet<>();       // explicitly created dirs
	private final HashMap<String, Long> lastModified = new HashMap<>();
	private boolean persisting;
	private boolean persistScheduled;

	private WebVfs() {
		dataDirs.add("/");
		loadPersisted();
	}

	public static WebVfs install() {
		if (instance == null) {
			instance = new WebVfs();
		}
		VirtualFileSystemProvider.setInstance(instance);
		return instance;
	}

	public static WebVfs get() {
		return instance;
	}

	// --- boot-time asset injection (transient) ---

	public void putAsset(String path, byte[] bytes) {
		assets.put(norm(path), bytes);
	}

	// --- static helpers for game code ---

	public static byte[] readFile(String path) {
		if (instance == null) {
			return null;
		}
		String p = instance.norm(path);
		byte[] d = instance.data.get(p);
		if (d != null) {
			return d;
		}
		return instance.assets.get(p);
	}

	public static void writeFile(String path, byte[] bytes) {
		if (instance == null) {
			return;
		}
		String p = instance.norm(path);
		instance.data.put(p, bytes);
		instance.touch(p);
		instance.ensureDir(parentOf(p));
		instance.persist();
	}

	public static boolean fileExists(String path) {
		if (instance == null) {
			return false;
		}
		String p = instance.norm(path);
		return instance.data.containsKey(p) || instance.assets.containsKey(p);
	}

	public static boolean dirExists(String path) {
		if (instance == null) {
			return false;
		}
		return instance.isDir(instance.norm(path));
	}

	public static String[] listDir(String path) {
		if (instance == null) {
			return new String[0];
		}
		return instance.children(instance.norm(path));
	}

	public static String gameDir() {
		return "/minicraft";
	}

	// --- VirtualFileSystem ---

	@Override
	public String getUserDir() {
		return "/";
	}

	@Override
	public VirtualFile getFile(String path) {
		return new VFile(norm(path));
	}

	@Override
	public boolean isWindows() {
		return false;
	}

	@Override
	public String canonicalize(String path) {
		return norm(path);
	}

	@Override
	public String[] getRoots() {
		return new String[] { "/" };
	}

	// --- internals ---

	private String norm(String p) {
		if (p == null || p.isEmpty()) {
			return "/";
		}
		if (p.charAt(0) != '/') {
			p = "/" + p;
		}
		while (p.length() > 1 && p.endsWith("/")) {
			p = p.substring(0, p.length() - 1);
		}
		while (p.contains("//")) {
			p = p.replace("//", "/");
		}
		return p;
	}

	private static String parentOf(String p) {
		int idx = p.lastIndexOf('/');
		if (idx <= 0) {
			return "/";
		}
		return p.substring(0, idx);
	}

	private byte[] content(String p) {
		byte[] d = data.get(p);
		return d != null ? d : assets.get(p);
	}

	private boolean isDir(String p) {
		if (p.equals("/")) {
			return true;
		}
		if (dataDirs.contains(p)) {
			return true;
		}
		String prefix = p + "/";
		for (String k : data.keySet()) {
			if (k.startsWith(prefix)) {
				return true;
			}
		}
		for (String k : assets.keySet()) {
			if (k.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	private String[] children(String p) {
		String prefix = p.equals("/") ? "/" : p + "/";
		TreeSet<String> names = new TreeSet<>();
		collectChildren(data.keySet(), prefix, names);
		collectChildren(assets.keySet(), prefix, names);
		collectChildren(dataDirs, prefix, names);
		String[] result = new String[names.size()];
		int i = 0;
		for (String n : names) {
			result[i++] = n;
		}
		return result;
	}

	private static void collectChildren(Iterable<String> keys, String prefix, TreeSet<String> out) {
		for (String k : keys) {
			if (!k.startsWith(prefix) || k.length() <= prefix.length()) {
				continue;
			}
			int slash = k.indexOf('/', prefix.length());
			if (slash < 0) {
				out.add(k.substring(prefix.length()));
			} else if (slash < k.length() - 1) {
				out.add(k.substring(prefix.length(), slash));
			}
		}
	}

	private void touch(String p) {
		lastModified.put(p, System.currentTimeMillis());
	}

	private void ensureDir(String dir) {
		if (!dir.equals("/")) {
			dataDirs.add(dir);
			ensureDir(parentOf(dir));
		}
	}

	/**
	 * Schedules a single persistence pass on the browser event loop. A game save
	 * writes dozens of files in one burst; without coalescing, each write would
	 * re-serialize the whole VFS to localStorage on the same tick (a visible
	 * hitch). With this, one burst = one serialization, after the tick.
	 */
	private void persist() {
		if (persisting || persistScheduled) {
			return;
		}
		persistScheduled = true;
		Platform.scheduleLater(() -> {
			persistScheduled = false;
			doPersist();
		}, 0);
	}

	/** Writes the VFS to storage immediately; called on page unload. */
	public static void flushPersist() {
		if (instance != null) {
			instance.doPersist();
		}
	}

	private void doPersist() {
		if (persisting) {
			return;
		}
		persisting = true;
		try {
			StringBuilder sb = new StringBuilder();
			sb.append('v').append(1).append('\n');
			for (String key : new TreeSet<>(data.keySet())) {
				sb.append(key).append('\t').append(base64(data.get(key))).append('\n');
			}
			Platform.storageSet(STORAGE_KEY, sb.toString());
		} catch (Throwable t) {
			Platform.log("VFS persist failed: " + t);
		} finally {
			persisting = false;
		}
	}

	private void loadPersisted() {
		try {
			String s = Platform.storageGet(STORAGE_KEY);
			if (s == null || s.length() < 2) {
				return;
			}
			int pos = 0;
			int lineStart = 0;
			int version = 0;
			boolean first = true;
			while (lineStart < s.length()) {
				int lineEnd = s.indexOf('\n', lineStart);
				if (lineEnd < 0) {
					lineEnd = s.length();
				}
				if (first) {
					version = s.startsWith("v1", lineStart) ? 1 : 0;
					first = false;
					if (version != 1) {
						return;
					}
				} else if (lineEnd > lineStart) {
					int tab = s.indexOf('\t', lineStart);
					if (tab > lineStart && tab < lineEnd) {
						String path = s.substring(lineStart, tab);
						String b64 = s.substring(tab + 1, lineEnd);
						byte[] bytes = unbase64(b64);
						if (bytes != null) {
							data.put(path, bytes);
							ensureDir(parentOf(path));
						}
					}
				}
				lineStart = lineEnd + 1;
			}
			pos = 0; // keep compiler quiet about unused
		} catch (Throwable t) {
			Platform.log("VFS restore failed: " + t);
		}
	}

	private static final char[] B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

	private static String base64(byte[] bytes) {
		StringBuilder sb = new StringBuilder((bytes.length + 2) / 3 * 4);
		int i = 0;
		while (i + 2 < bytes.length) {
			int n = ((bytes[i] & 0xFF) << 16) | ((bytes[i + 1] & 0xFF) << 8) | (bytes[i + 2] & 0xFF);
			sb.append(B64[(n >> 18) & 63]).append(B64[(n >> 12) & 63]).append(B64[(n >> 6) & 63]).append(B64[n & 63]);
			i += 3;
		}
		int rem = bytes.length - i;
		if (rem == 1) {
			int n = (bytes[i] & 0xFF) << 16;
			sb.append(B64[(n >> 18) & 63]).append(B64[(n >> 12) & 63]).append("==");
		} else if (rem == 2) {
			int n = ((bytes[i] & 0xFF) << 16) | ((bytes[i + 1] & 0xFF) << 8);
			sb.append(B64[(n >> 18) & 63]).append(B64[(n >> 12) & 63]).append(B64[(n >> 6) & 63]).append('=');
		}
		return sb.toString();
	}

	private static byte[] unbase64(String s) {
		try {
			int len = s.length();
			while (len > 0 && s.charAt(len - 1) == '=') {
				len--;
			}
			if (len == 0) {
				return new byte[0];
			}
			int byteLen = len * 6 / 8;
			byte[] out = new byte[byteLen];
			int acc = 0;
			int bits = 0;
			int idx = 0;
			for (int i = 0; i < len; i++) {
				int v = b64Val(s.charAt(i));
				if (v < 0) {
					return null;
				}
				acc = (acc << 6) | v;
				bits += 6;
				if (bits >= 8) {
					bits -= 8;
					if (idx < byteLen) {
						out[idx++] = (byte) ((acc >> bits) & 0xFF);
					}
				}
			}
			return out;
		} catch (Throwable t) {
			return null;
		}
	}

	private static int b64Val(char c) {
		if (c >= 'A' && c <= 'Z') return c - 'A';
		if (c >= 'a' && c <= 'z') return c - 'a' + 26;
		if (c >= '0' && c <= '9') return c - '0' + 52;
		if (c == '+') return 62;
		if (c == '/') return 63;
		return -1;
	}

	/** VirtualFile view over the two maps. */
	private final class VFile implements VirtualFile {
		private final String path;

		VFile(String path) {
			this.path = path;
		}

		@Override
		public String getName() {
			return path.equals("/") ? "/" : path.substring(path.lastIndexOf('/') + 1);
		}

		@Override
		public boolean isDirectory() {
			return isDir(path);
		}

		@Override
		public boolean isFile() {
			return data.containsKey(path) || assets.containsKey(path);
		}

		@Override
		public String[] listFiles() {
			if (!isDirectory()) {
				return null;
			}
			return children(path);
		}

		@Override
		public VirtualFileAccessor createAccessor(boolean readable, boolean writable, boolean append) {
			byte[] cur = content(path);
			if (cur == null) {
				if (!writable) {
					return null;
				}
				cur = new byte[0];
				ensureDir(parentOf(path));
			} else {
				cur = cur; // no copy on read; readers must not mutate
			}
			return new Acc(path, cur, writable, append);
		}

		@Override
		public boolean createFile(String fileName) throws IOException {
			if (!isDirectory()) {
				throw new IOException("Parent directory does not exist: " + path);
			}
			String target = childPath(fileName);
			if (data.containsKey(target)) {
				return false;
			}
			data.put(target, new byte[0]);
			touch(target);
			dataDirs.add(path);
			ensureDir(path);
			persist();
			return true;
		}

		@Override
		public boolean createDirectory(String fileName) {
			if (!isDirectory()) {
				return false;
			}
			String target = childPath(fileName);
			if (dataDirs.contains(target) || data.containsKey(target)) {
				return false;
			}
			dataDirs.add(target);
			persist();
			return true;
		}

		@Override
		public boolean delete() {
			if (data.containsKey(path) || dataDirs.contains(path)) {
				data.remove(path);
				dataDirs.remove(path);
				lastModified.remove(path);
				String prefix = path + "/";
				ArrayList<String> doomed = new ArrayList<>();
				for (String k : data.keySet()) {
					if (k.startsWith(prefix)) {
						doomed.add(k);
					}
				}
				for (String k : doomed) {
					data.remove(k);
					lastModified.remove(k);
				}
				doomed.clear();
				for (String k : dataDirs) {
					if (k.startsWith(prefix)) {
						doomed.add(k);
					}
				}
				for (String k : doomed) {
					dataDirs.remove(k);
				}
				persist();
				return true;
			}
			// Deleting a bundled asset: record nothing (transient), report success.
			if (assets.containsKey(path)) {
				return true;
			}
			return false;
		}

		@Override
		public boolean adopt(VirtualFile file, String fileName) {
			if (!(file instanceof VFile) || !isDirectory()) {
				return false;
			}
			VFile src = (VFile) file;
			String target = childPath(fileName);
			if (data.containsKey(src.path)) {
				data.put(target, data.remove(src.path));
				Long lm = lastModified.remove(src.path);
				if (lm != null) {
					lastModified.put(target, lm);
				}
			} else if (assets.containsKey(src.path)) {
				data.put(target, assets.get(src.path));
			} else if (isDir(src.path)) {
				String srcPrefix = src.path.equals("/") ? "/" : src.path + "/";
				String dstPrefix = target + "/";
				HashMap<String, byte[]> moved = new HashMap<>();
				for (String k : data.keySet()) {
					if (k.startsWith(srcPrefix)) {
						moved.put(dstPrefix + k.substring(srcPrefix.length()), data.get(k));
					}
				}
				for (Map.Entry<String, byte[]> e : moved.entrySet()) {
					data.put(e.getKey(), e.getValue());
				}
				for (String k : moved.keySet()) {
					data.remove(k);
				}
				for (String k : new ArrayList<>(dataDirs)) {
					if (k.startsWith(srcPrefix)) {
						dataDirs.remove(k);
						dataDirs.add(dstPrefix + k.substring(srcPrefix.length()));
					}
				}
			} else {
				return false;
			}
			dataDirs.add(path);
			ensureDir(parentOf(target));
			persist();
			return true;
		}

		@Override
		public boolean canRead() {
			return exists();
		}

		@Override
		public boolean canWrite() {
			return true;
		}

		@Override
		public long lastModified() {
			Long v = lastModified.get(path);
			return v == null ? 0 : v;
		}

		@Override
		public boolean setLastModified(long value) {
			lastModified.put(path, value);
			return true;
		}

		@Override
		public boolean setReadOnly(boolean readOnly) {
			return true;
		}

		@Override
		public int length() {
			byte[] d = content(path);
			return d == null ? 0 : d.length;
		}

		private String childPath(String name) {
			return path.equals("/") ? "/" + name : path + "/" + name;
		}
	}

	/** Byte-level accessor over one file; write-through to the map, persist on flush/close. */
	private final class Acc implements VirtualFileAccessor {
		private final String path;
		private byte[] buf;
		private int pos;
		private final boolean writable;

		Acc(String path, byte[] initial, boolean writable, boolean append) {
			this.path = path;
			this.buf = initial;
			this.writable = writable;
			this.pos = append ? initial.length : 0;
		}

		@Override
		public int read(byte[] buffer, int offset, int limit) {
			if (pos >= buf.length) {
				return -1;
			}
			int n = Math.min(limit, buf.length - pos);
			System.arraycopy(buf, pos, buffer, offset, n);
			pos += n;
			return n;
		}

		@Override
		public void write(byte[] buffer, int offset, int limit) {
			ensure(pos + limit);
			System.arraycopy(buffer, offset, buf, pos, limit);
			pos += limit;
			if (writable) {
				data.put(path, buf);
				touch(path);
				ensureDir(parentOf(path));
			}
		}

		private void ensure(int size) {
			if (buf.length < size) {
				int newLen = Math.max(size, buf.length * 2);
				byte[] nb = new byte[newLen];
				System.arraycopy(buf, 0, nb, 0, buf.length);
				buf = nb;
			}
		}

		@Override
		public int tell() {
			return pos;
		}

		@Override
		public void seek(int target) {
			if (target > buf.length) {
				ensure(target);
			}
			pos = target;
		}

		@Override
		public void skip(int amount) {
			seek(pos + amount);
		}

		@Override
		public int size() {
			return buf.length;
		}

		@Override
		public void resize(int size) {
			byte[] nb = new byte[size];
			System.arraycopy(buf, 0, nb, 0, Math.min(buf.length, size));
			buf = nb;
			if (pos > size) {
				pos = size;
			}
			if (writable) {
				data.put(path, buf);
			}
		}

		@Override
		public void close() {
			if (writable) {
				data.put(path, buf);
				persist();
			}
		}

		@Override
		public void flush() {
			if (writable) {
				data.put(path, buf);
				persist();
			}
		}
	}
}
