package minicraft.core.io;

import minicraft.core.CrashHandler;
import minicraft.core.Game;
import minicraft.saveload.Save;
import minicraft.util.Logging;
import minicraft.web.WebVfs;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class FileHandler extends Game {
	private FileHandler() {
	}

	public static final int REPLACE_EXISTING = 0;
	public static final int RENAME_COPY = 1;
	public static final int SKIP = 2;

	public static final String OS = "web";
	private static final String localGameDir = "";
	static final String systemGameDir = "/";
	private static Path jarResourcesPath;

	/**
	 * Determines the path the game will use to store worlds, settings, resource packs, etc.
	 * Web port: everything lives inside the virtual file system, persisted to localStorage.
	 * @param saveDir Value from --savedir argument. Null if it was not set. Ignored on the web.
	 */
	public static void determineGameDir(@Nullable String saveDir) {
		if (saveDir != null)
			Logging.GAMEHANDLER.debug("Ignoring --savedir argument for the web build: " + saveDir);

		gameDir = WebVfs.gameDir();
		Logging.GAMEHANDLER.debug("Determined gameDir: " + gameDir);

		//noinspection ResultOfMethodCallIgnored
		new File(gameDir).mkdirs();
	}

	public static String getSystemGameDir() {
		return systemGameDir;
	}

	public static String getLocalGameDir() {
		return localGameDir;
	}

	private static void deleteFolder(File top) {
		if (top == null) return;
		if (top.isDirectory()) {
			File[] subfiles = top.listFiles();
			if (subfiles != null)
				for (File subfile : subfiles)
					deleteFolder(subfile);
		}

		//noinspection ResultOfMethodCallIgnored
		top.delete();
	}

	public static void copyFolderContents(File origFolder, File newFolder, int ifExisting, boolean deleteOriginal) throws IOException {
		// I can determine the local folder structure with origFolder.relativize(file), then use newFolder.resolve(relative).
		Logging.RESOURCEHANDLER.info("Copying contents of folder " + origFolder + " to new folder " + newFolder);

		copyFolder(origFolder, newFolder, ifExisting);

		if (deleteOriginal)
			deleteFolder(origFolder);
	}

	private static void copyFolder(File origFolder, File newFolder, int ifExisting) {
		File[] files = origFolder.listFiles();
		if (files == null) return;

		//noinspection ResultOfMethodCallIgnored
		newFolder.mkdirs();

		for (File file : files) {
			if (file.isDirectory()) {
				copyFolder(file, new File(newFolder, file.getName()), ifExisting);
				continue;
			}

			File newFile = new File(newFolder, file.getName());
			if (newFile.exists()) {
				if (ifExisting == SKIP)
					continue;
				else if (ifExisting == RENAME_COPY) {
					String newFilename = newFile.getPath().substring(0, newFile.getPath().lastIndexOf("."));
					do {
						newFilename += "(Old)";
					} while (new File(newFilename + Save.extension).exists());
					newFile = new File(newFilename + Save.extension);
				}
			}

			try {
				copyFile(file, newFile);
			} catch (IOException ex) {
				CrashHandler.errorHandle(ex);
			}
		}
	}

	private static void copyFile(File src, File dst) throws IOException {
		try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
			byte[] buf = new byte[8192];
			int length;
			while ((length = in.read(buf)) != -1) {
				out.write(buf, 0, length);
			}
		}
	}

	public static Path getJarResourcesPath() {
		// Web port: the bundled resources are mounted at the root of the virtual file system.
		if (jarResourcesPath == null) {
			jarResourcesPath = Paths.get("/");
		}
		return jarResourcesPath;
	}

	public static ArrayList<String> listAssets() {
		ArrayList<String> names = new ArrayList<>();
		names.add("assets/");
		collectAssets("assets", names);
		return names;
	}

	private static void collectAssets(String dir, ArrayList<String> out) {
		String[] children = WebVfs.listDir("/" + dir);
		if (children == null) return;
		for (String child : children) {
			String rel = dir + "/" + child;
			if (WebVfs.dirExists("/" + rel)) {
				out.add(rel + "/");
				collectAssets(rel, out);
			} else {
				out.add(rel);
			}
		}
	}
}
