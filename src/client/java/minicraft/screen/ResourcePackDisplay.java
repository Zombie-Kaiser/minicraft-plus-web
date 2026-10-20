package minicraft.screen;

import minicraft.core.CrashHandler;
import minicraft.core.Game;
import minicraft.core.Renderer;
import minicraft.core.io.FileHandler;
import minicraft.core.io.InputHandler;
import minicraft.core.io.Localization;
import minicraft.core.io.Sound;
import minicraft.gfx.Color;
import minicraft.gfx.Font;
import minicraft.gfx.MinicraftImage;
import minicraft.gfx.Point;
import minicraft.gfx.Screen;
import minicraft.gfx.SpriteAnimation;
import minicraft.gfx.SpriteLinker;
import minicraft.gfx.SpriteLinker.SpriteType;
import minicraft.saveload.Save;
import minicraft.screen.entry.ListEntry;
import minicraft.screen.entry.SelectEntry;
import minicraft.util.BookData;
import minicraft.util.Logging;
import minicraft.web.ImageStore;
import minicraft.web.WebVfs;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ResourcePackDisplay extends Display {
	/* Resource Pack
	 * Current complete structure of resource packs:
	 * <root>
	 * 	├──	pack.json
	 * 	├──	pack.png
	 * 	└──	assets TODO Restructure the structure with the new IDS later
	 * 		├──	books
	 * 		│	└──	<name>.txt
	 * 		├──	localization
	 * 		│	└──	<locale>.json
	 * 		├──	sound
	 * 		│	└──	<name>.wav
	 * 		└──	textures
	 * 			├──	entity
	 * 			│	└──	<entity_name>.png
	 * 			├──	item
	 * 			│	└──	<item_name>.png
	 * 			├──	tile
	 * 			│	├──	<tile_name>.png
	 * 			│	└──	[<tile_name>.png.json]
	 * 			└──	gui
	 * 				├──	font.png
	 * 				├──	hud.png
	 * 				└──	title.png
	 *
	 * pack.json
	 * 	├──	(name) String
	 * 	├──	(description) String
	 * 	├──	pack_format int
	 * 	└──	(language object)
	 * 		└──	<locale>
	 * 			├──	name String
	 * 			└──	region String
	 */


	private static final ArrayList<ResourcePack> resourcePacks = new ArrayList<>(); // List of all packs, in order of loading priority
	private static final File FOLDER_LOCATION = new File(FileHandler.gameDir + "/resourcepacks");
	@SuppressWarnings("unused")
	private static final int VERSION = 1;

	private static final ResourcePack defaultPack; // Used to check if the resource pack default.
	private static final MinicraftImage defaultLogo;

	private static final int padding = 10;

	private ArrayList<ListEntry> entries = new ArrayList<>(), toggleIndicators = new ArrayList<>();
	private Menu.Builder builder, indicatorsBuilder;
	private boolean changed = false;

	static { // Initializing the default pack and logo.
		// Web port: the bundled resources are mounted at the root of the virtual file system.
		try {
			byte[] meta = WebVfs.readFile("/pack.json");
			if (meta == null) throw new IOException("Bundled pack.json not found in the virtual file system.");

			defaultPack = Objects.requireNonNull(loadPackFromStream("/", new ByteArrayInputStream(meta), "Default"));
			defaultPack.enable();
			defaultPack.lock();
			resourcePacks.add(defaultPack);

			MinicraftImage logoImg = ImageStore.load("/resources/default_pack.png");
			if (logoImg == null) throw new IOException("Bundled default_pack.png was not decoded.");
			defaultLogo = MinicraftImage.createDefaultCompatible(logoImg.pixels, logoImg.width, logoImg.height);
		} catch (IOException | MinicraftImage.MinicraftImageDimensionIncompatibleException e) {
			CrashHandler.crashHandle(e);
			//noinspection ProhibitedExceptionThrown
			throw new RuntimeException(e);
		}
	}

	/**
	 * Initializing the Display.
	 */
	public ResourcePackDisplay() {
		super(true, true);
		initPacks();

		indicatorsBuilder = new Menu.Builder(false, 2, RelPos.LEFT).setPositioning(new Point(20, 60), RelPos.BOTTOM_RIGHT);
		builder = new Menu.Builder(false, 2, RelPos.LEFT).setPositioning(new Point(20, 60), RelPos.BOTTOM_LEFT);

		reloadEntries();

		menus = new Menu[] {
			indicatorsBuilder.setEntries(toggleIndicators).createMenu(),
			builder.setEntries(entries).createMenu()
		};

		selection = 1;

		if (menus[1].getBounds().getLeft() - menus[0].getBounds().getRight() < padding)
			menus[1].translate(menus[0].getBounds().getRight() - menus[1].getBounds().getLeft() + padding, 0);
	}

	@Override
	protected void onSelectionChange(int oldSel, int newSel) {
		super.onSelectionChange(oldSel, newSel);
		if (oldSel == newSel)
			return; // this also serves as a protection against access to menus[0] when such may not exist.
		if (menus[1].getBounds().getLeft() - menus[0].getBounds().getRight() < padding)
			menus[1].translate(menus[0].getBounds().getRight() - menus[1].getBounds().getLeft() + padding, 0);
	}

	/**
	 * Reloading the entries to refresh the current pack list.
	 */
	private void reloadEntries() {
		// Entries will hold selectable resource pack entries,
		// toggleIndicators just indicate whether a pack is enabled
		entries.clear();
		toggleIndicators.clear();
		for (ResourcePack pack : resourcePacks) { // List all available resource packs
			entries.add(new SelectEntry(pack.name, () -> { pack.toggle(); changed = true; }, false));
			toggleIndicators.add(new SelectEntry("✓", () -> {}, false) {
				@Override
				public String toString() {
					if(pack.isEnabled()) return super.toString();
					else return "";
				}

				@Override
				public int getColor(boolean isSelected) {
					if(pack == defaultPack) return COL_UNSLCT;
					else return COL_SLCT;
				}
			});
			toggleIndicators.get(toggleIndicators.size()-1).setSelectable(false);
		}
		entries.get(toggleIndicators.size()-1).setSelectable(false);
	}

	/**
	 * Applying the reloaded entries into the display.
	 */
	private void refreshEntries() {
		reloadEntries();
		Menu[] newMenus = new Menu[] {
			indicatorsBuilder.setEntries(toggleIndicators).createMenu(),
			builder.setEntries(entries).createMenu()
		};

		// Reapplying selections.
		newMenus[0].setSelection(menus[0].getSelection());
		newMenus[1].setSelection(menus[1].getSelection());

		menus = newMenus;

		// Translate position.
		onSelectionChange(selection ^ 1, selection);
	}

	@Override
	public void onExit() {
		releaseUnloadedPacks();
		new Save();
		if (changed) reloadResources();
	}

	@Override
	public void tick(InputHandler input) {
		// Overrides the default tick handler.
		if (input.getMappedKey("cursor-right").isClicked()) { // Move the selected pack down.
			if (menus[1].getSelection() < resourcePacks.size() - 2) { // Only if it has space to move down (and stay above default)
				int i = menus[1].getSelection();
				ResourcePack swap = resourcePacks.get(i + 1);
				resourcePacks.set(i + 1, resourcePacks.get(i));
				resourcePacks.set(i, swap);
				menus[1].setSelection(i + 1);
				changed = true;
				refreshEntries();
				Sound.play("select");
			}

			return;
		} else if (input.getMappedKey("cursor-left").isClicked()) { // Move the selected pack up.
			if (menus[1].getSelection() > 0 && resourcePacks.get(menus[1].getSelection()) != defaultPack) {
				int i = menus[1].getSelection();
				ResourcePack swap = resourcePacks.get(i - 1);
				resourcePacks.set(i - 1, resourcePacks.get(i));
				resourcePacks.set(i, swap);
				menus[1].setSelection(i - 1);
				changed = true;
				refreshEntries();
				Sound.play("select");
			}

			return;
		} else if (input.getMappedKey("menu").isClicked()) {
			ResourcePack pack = resourcePacks.get(menus[1].getSelection());
			Game.setDisplay(new PopupDisplay(null, pack.name, pack.description));
		}

		super.tick(input);
	}

	@Override
	public void render(Screen screen) {
		super.render(screen);

		// Title
		Font.drawCentered(Localization.getLocalized("minicraft.displays.resource_packs.display.title"), screen, 6, Color.WHITE);

		// Info text at the bottom.
		if (Game.input.anyControllerConnected())
			Font.drawCentered(Localization.getLocalized("minicraft.displays.resource_packs.display.help.keyboard_needed"), screen, Screen.h - 41, Color.DARK_GRAY);
		Font.drawCentered(Localization.getLocalized("minicraft.displays.resource_packs.display.help.move", Game.input.getMapping("cursor-up"), Game.input.getMapping("cursor-down")), screen, Screen.h - 33, Color.DARK_GRAY);
		Font.drawCentered(Localization.getLocalized("minicraft.displays.resource_packs.display.help.reorder", Game.input.getMapping("cursor-left"), Game.input.getMapping("cursor-right")), screen, Screen.h - 25, Color.DARK_GRAY);
		Font.drawCentered(Localization.getLocalized("minicraft.displays.resource_packs.display.help.select", Game.input.getMapping("select")), screen, Screen.h - 17, Color.DARK_GRAY);
		Font.drawCentered(Localization.getLocalized("minicraft.displays.resource_packs.display.help.info", Game.input.getMapping("menu")), screen, Screen.h - 9, Color.DARK_GRAY);

		if (resourcePacks.size() > 0) { // If there is any pack that can be selected.
			@SuppressWarnings("resource")
			MinicraftImage logo = resourcePacks.get(menus[1].getSelection()).logo;
			int h = logo.height / 8;
			int w = logo.width / 8;
			int xo = (Screen.w - logo.width) / 2;
			int yo = 36 - logo.height / 2;

			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					// Resource pack logo
					screen.render(xo + x * 8, yo + y * 8, x, y, 0, logo);
				}
			}
		}
	}

	/**
	 * The object representation of resource pack.
	 */
	private static class ResourcePack implements Closeable {
		private final String packRoot; // Absolute path in the virtual file system.

		/**
		 * 0 - before 2.2.0; 1 - 2.2.0-latest
		 */
		@SuppressWarnings("unused")
		private final int packFormat; // The pack format of the pack.
		private final String name; // The name of the pack.
		private final String description; // The description of the pack.
		private MinicraftImage logo; // The logo of the pack.

		private boolean enabled = false, locked = false;

		private ResourcePack(String packRoot, int packFormat, String name, String desc) {
			this.packRoot = packRoot;
			this.packFormat = packFormat;
			this.name = name;
			this.description = desc;
			refreshPack();
		}

		/** Resolves a pack-relative path to an absolute virtual file system path. */
		private String abs(String relPath) {
			String root = packRoot.equals("/") ? "" : packRoot;
			String p = root + "/" + relPath;
			while (p.contains("//")) {
				p = p.replace("//", "/");
			}
			return p;
		}

		/**
		 * This does not include metadata refresh.
		 */
		public void refreshPack() {
			// Refresh pack logo.png.
			try {
				MinicraftImage logoImg = ImageStore.load(abs("pack.png"));
				if (logoImg == null) throw new IOException("Pack logo not decoded: " + abs("pack.png"));
				logo = MinicraftImage.createDefaultCompatible(logoImg.pixels, logoImg.width, logoImg.height);

				// Logo size verification.
				int h = logo.height;
				int w = logo.width;
				if (h == 0 || w == 0 || h % 8 != 0 || w % 8 != 0 ||
					h > 32 || w > Screen.w) {
					throw new IOException(String.format("Unacceptable logo size: %s;%s", w, h));
				}
			} catch (IOException | NullPointerException | MinicraftImage.MinicraftImageDimensionIncompatibleException e) {
				Logging.RESOURCEHANDLER_RESOURCEPACK.warn(e, "Unable to load logo in pack: {}, loading default logo instead.", name);
				if (this == defaultPack) {
					MinicraftImage img = ImageStore.load("/resources/logo.png");
					if (img == null) {
						CrashHandler.crashHandle(new NullPointerException("/resources/logo.png was not decoded."));
					} else {
						logo = img;
					}
				} else logo = defaultLogo;
			}
		}

		private boolean isEnabled() {
			return enabled;
		}

		private void lock() { locked = true; }
		private void enable() {
			if(locked) return;
			enabled = true;
		}
		private void toggle() {
			if(locked) return;
			enabled = !enabled;
		}

		/**
		 * Opens the pack for resource reading.
		 * @return {@code true} if the pack root exists in the virtual file system.
		 */
		private boolean openStream() {
			return WebVfs.dirExists(packRoot);
		}

		/**
		 * Closing the pack if opened. (No-op on the web port.)
		 */
		@Override
		public void close() {
		}

		/**
		 * Getting the stream by the path.
		 * @param path The path of the entry.
		 * @return The input stream of the specified entry.
		 * @throws IOException if an I/O error has occurred.
		 */
		private InputStream getResourceAsStream(String path) throws IOException {
			byte[] data = WebVfs.readFile(abs(path));
			if (data == null) throw new IOException("Resource not found: " + abs(path));
			return new ByteArrayInputStream(data);
		}

		@FunctionalInterface
		private static interface FilesFilter { // Literally functioned.
			boolean check(String path, boolean isDir);
		}

		/**
		 * Getting the subfiles under the specified entry directory.
		 * @param path The directory to be listed.
		 * @param filter The filter to be applied.
		 * @return The filtered (if any) subfile and subfolder list. Empty if not or invalid path.
		 */
		@NotNull
		private ArrayList<String> getFiles(String path, FilesFilter filter) {
			ArrayList<String> paths = new ArrayList<>();

			String base = path.endsWith("/") ? path : path + "/";
			if (!WebVfs.dirExists(abs(base))) return paths;

			// The queried directory itself, as the original depth-1 folder walk does.
			if (filter == null || filter.check(base, true))
				paths.add(base);

			String[] children = WebVfs.listDir(abs(base));
			if (children != null) {
				for (String child : children) {
					String rel = base + child;
					boolean isDir = WebVfs.dirExists(abs(rel));
					if (filter == null || filter.check(isDir ? rel + "/" : rel, isDir)) {
						paths.add(isDir ? rel + "/" : rel);
					}
				}
			}

			return paths;
		}
	}

	/**
	 * Reading the string from the input stream.
	 * @param in The input stream to be read.
	 * @return The returned string.
	 */
	public static String readStringFromInputStream(InputStream in) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int length;
			while ((length = in.read(buf)) != -1) {
				out.write(buf, 0, length);
			}

			// Matches the original BufferedReader.lines() + String.join("\n") behavior.
			return out.toString("UTF-8").replace("\r\n", "\n").replace('\r', '\n');
		} catch (IOException e) {
			return "";
		}
	}

	/**
	 * Construct a resource pack from a location, pack.json input stream, and default filename
	 * @param root The source location of the resource pack
	 * @param packMeta The input stream of a pack.json file
	 * @param filename The name to default to if no "name" key found in pack.json
	 */
	public static ResourcePack loadPackFromStream(String root, InputStream packMeta, String filename) throws IOException {
		JSONObject meta = new JSONObject(readStringFromInputStream(packMeta));
		return new ResourcePack(
			root,
			meta.getInt("pack_format"),
			meta.optString("name", filename),
			meta.optString("description", "No description")
		);
	}

	/**
	 * Loading pack metadata of the pack.
	 * @param path The path of the pack in the virtual file system.
	 * @return The loaded pack with metadata.
	 */
	public static ResourcePack loadPackMetadata(String path) {
		// Web port: only unpacked folder packs are supported.
		try {
			File file = new File(path);
			if (file.isDirectory()) {
				byte[] meta = WebVfs.readFile(new File(file, "pack.json").getPath());
				if (meta == null) throw new NullPointerException("pack.json is missing."); // pack.json is missing.
				return loadPackFromStream(path, new ByteArrayInputStream(meta), file.getName());
			}
		} catch (JSONException | IOException e) {
			CrashHandler.errorHandle(e, new CrashHandler.ErrorInfo("Cannot Load Resource Pack",
				CrashHandler.ErrorInfo.ErrorType.REPORT, String.format("Unable to load resource pack: %s.", path)));
		} catch (NullPointerException e) { // pack.json is missing.
			CrashHandler.errorHandle(e, new CrashHandler.ErrorInfo("Resource Pack not Supported",
				CrashHandler.ErrorInfo.ErrorType.HANDLED, String.format("Missing pack.json in resource pack: %s.", path)));
		}

		return null;
	}

	/**
	 * Intializing the packs from directory and loaded.
	 */
	public static void initPacks() {
		// Generate resource packs folder
		if (FOLDER_LOCATION.mkdirs()) {
			Logging.RESOURCEHANDLER_RESOURCEPACK.info("Created resource packs folder at {}.", FOLDER_LOCATION);
		}

		ArrayList<String> paths = new ArrayList<>();
		paths.add(defaultPack.packRoot); // Add default pack first

		// Add already recognized resourcePacks
		for (ResourcePack pack : resourcePacks) {
			if (!paths.contains(pack.packRoot)) {
				paths.add(pack.packRoot);
			}
		}

		// Read and add the folder (that contains a pack.json) to the resource pack list.
		// Web port: .zip packs are not supported; only unpacked folders in the virtual file system.
		File[] files = FOLDER_LOCATION.listFiles();
		if (files != null) {
			for (File file : files) {
				if (file.isDirectory() && WebVfs.fileExists(new File(file, "pack.json").getPath())) {
					String path = file.getPath();
					if (!paths.contains(path))
						paths.add(path);
				}
			}
		}

		refreshResourcePacks(paths);
	}

	/**
	 * Finding the pack by pack's path.
	 * @param path The path for query.
	 * @return The found resource pack. {@code null} if not found.
	 */
	private static ResourcePack findPackByPath(String path) {
		for (ResourcePack pack : resourcePacks) {
			if (pack.packRoot.equals(path)) {
				return pack;
			}
		}

		return null;
	}

	/**
	 * Refreshing the pack list by the paths.
	 * @param paths The packs' paths to be refreshed.
	 */
	private static void refreshResourcePacks(List<String> paths) {
		// Reverse from entry-order (default last) to priority order (default first)
		Collections.reverse(resourcePacks);

		Logging.RESOURCEHANDLER_RESOURCEPACK.debug("Refreshing from " + paths);
		for (String path : paths) {
			ResourcePack pack = findPackByPath(path);
			if (pack != null) { // Re-check and refresh if it exists
				if (pack == defaultPack || WebVfs.dirExists(path))
					pack.refreshPack();
				else // Remove if it doesn't exist
					resourcePacks.remove(pack);
			} else { // Add as a new pack
				pack = loadPackMetadata(path);
				if (pack != null) {
					resourcePacks.add(pack);
				}
			}
		}

		// Reverse from priority-order (default first) to entry order (default last)
		Collections.reverse(resourcePacks);
	}

	/**
	 * Releasing the unloaded packs.
	 */
	public static void releaseUnloadedPacks() {
		for(int i = 0; i < resourcePacks.size(); i++) {
			if(!resourcePacks.get(i).isEnabled()) {
				resourcePacks.remove(i--); // Releases unloaded packs.
			}
		}
	}

	/**
	 * Loading the resource packs when loading preferences. This should only be called by {@link minicraft.saveload.Load}.
	 * @param names The names of the packs.
	 */
	public static void loadResourcePacks(String[] names) {
		for (String name : names) {
			for (ResourcePack pack : resourcePacks) {
				if (pack.packRoot.equals(new File(FOLDER_LOCATION, name).getPath())) {
					pack.enable();
				}
			}
		}
	}

	/**
	 * Getting the names of the loaded packs. This should only be called by {@link minicraft.saveload.Save}.
	 * @return The names of currently loaded packs.
	 */
	public static ArrayList<String> getLoadedPacks() {
		ArrayList<String> packs = new ArrayList<>();
		for (ResourcePack pack : resourcePacks) {
			if (pack != defaultPack && pack.isEnabled()) {
				packs.add(new File(pack.packRoot).getName());
			}
		}

		return packs;
	}

	/**
	 * Reloading all the resources with the currently packs to be loaded.
	 */
	public static void reloadResources() {
		ArrayList<ResourcePack> loadQuery = new ArrayList<>();
		loadQuery.addAll(resourcePacks);
		Collections.reverse(loadQuery);

		// Clear all previously loaded resources.
		Renderer.spriteLinker.resetSprites();
		Localization.resetLocalizations();
		BookData.resetBooks();
		Sound.resetSounds();
		SpriteAnimation.resetMetadata();
		for (ResourcePack pack : loadQuery) {
			if (pack.openStream()) {
				try {
					loadTextures(pack);
					loadLocalization(pack);
					loadBooks(pack);
					loadSounds(pack);
					pack.close();
				} catch (IOException e) {
					CrashHandler.errorHandle(e);
				}
			}
		}

		SpriteAnimation.refreshAnimations();
		Renderer.spriteLinker.updateLinkedSheets();
		Localization.loadLanguage();

		// Refreshing skins
		SkinDisplay.refreshSkins();
		SkinDisplay.releaseSkins();
	}

	/**
	 * Loading the textures of the pack.
	 * @param pack The pack to be loaded.
	 * @throws IOException if I/O exception occurs.
	 */
	private static void loadTextures(ResourcePack pack) throws IOException {
		for (String t : pack.getFiles("assets/textures/", null)) {
			switch (t) {
				case "assets/textures/entity/":
					loadTextures(pack, SpriteType.Entity);
					break;
				case "assets/textures/gui/":
					loadTextures(pack, SpriteType.Gui);
					break;
				case "assets/textures/item/":
					loadTextures(pack, SpriteType.Item);
					break;
				case "assets/textures/tile/":
					loadTextures(pack, SpriteType.Tile);
					break;
			}
		}
	}

	/**
	 * Loading the categories of textures from the pack.
	 * @param pack The pack to be loaded.
	 * @param type The category of textures.
	 * @throws IOException if I/O exception occurs.
	 */
	private static void loadTextures(ResourcePack pack, SpriteType type) throws IOException {
		String path = "assets/textures/";
		switch (type) {
			case Entity:
				path += "entity/";
				break;
			case Gui:
				path += "gui/";
				break;
			case Item:
				path += "item/";
				break;
			case Tile:
				path += "tile/";
				break;
		}

		ArrayList<String> pngs = pack.getFiles(path, (p, isDir) -> p.endsWith(".png") && !isDir);
		if (type == SpriteType.Tile) {
			// Loading sprite sheet metadata.
			for (String m : pack.getFiles(path, (p, isDir) -> p.endsWith(".png.json") && !isDir)) {
				try {
					JSONObject obj = new JSONObject(readStringFromInputStream(pack.getResourceAsStream(m)));
					SpriteLinker.SpriteMeta meta = new SpriteLinker.SpriteMeta();
					String imgName = m.substring(0, m.length() - 5);
					pngs.remove(imgName);
					MinicraftImage image = ImageStore.load(pack.abs(imgName));
					if (image == null)
						throw new IOException("Image not decoded by the web platform: " + pack.abs(imgName));

					// Applying animations.
					MinicraftImage sheet;
					JSONObject animation = obj.optJSONObject("animation");
					if (animation != null) {
						meta.frametime = animation.getInt("frametime");
						meta.frames = image.height / 16;
						if (meta.frames == 0) throw new IOException(new IllegalArgumentException(String.format(
							"Invalid frames 0 detected with {} in pack: {}", m, pack.name)));
						validateImageAsset(pack, imgName, image.width, image.height, 16, 16 * meta.frames);
						sheet = ImageStore.load(pack.abs(imgName), 16, 16 * meta.frames);
					} else {
						validateImageAsset(pack, imgName, image.width, image.height, 16, 16);
						sheet = ImageStore.load(pack.abs(imgName), 16, 16);
					}
					Renderer.spriteLinker.setSprite(type, m.substring(path.length(), m.length() - 9), sheet);

					JSONObject borderObj = obj.optJSONObject("border");
					if (borderObj != null) {
						meta.border = borderObj.optString("key");
						if (meta.border.isEmpty()) meta.border = null;
						if (meta.border != null) {
							String borderK = path + meta.border + ".png";
							pngs.remove(borderK);
							try {
								MinicraftImage img = ImageStore.load(pack.abs(borderK));
								if (img == null) throw new IOException("Image not decoded by the web platform: " + pack.abs(borderK));
								validateImageAsset(pack, borderK, img.width, img.height, 24, 24);
								Renderer.spriteLinker.setSprite(type, meta.border, ImageStore.load(pack.abs(borderK), 24, 24));
							} catch (IOException e) {
								Logging.RESOURCEHANDLER_RESOURCEPACK.warn(e, "Unable to read {} with {} in pack: {}", borderK, m, pack.name);
								meta.border = null;
							}
						}

						meta.corner = borderObj.optString("corner");
						if (meta.corner.isEmpty()) meta.corner = null;
						if (meta.corner != null) {
							String cornerK = path + meta.corner + ".png";
							pngs.remove(cornerK);
							try {
								MinicraftImage img = ImageStore.load(pack.abs(cornerK));
								if (img == null) throw new IOException("Image not decoded by the web platform: " + pack.abs(cornerK));
								validateImageAsset(pack, cornerK, img.width, img.height, 16, 16);
								Renderer.spriteLinker.setSprite(type, meta.corner, ImageStore.load(pack.abs(cornerK), 16, 16));
							} catch (IOException e) {
								Logging.RESOURCEHANDLER_RESOURCEPACK.warn(e, "Unable to read {} with {} in pack: {}", cornerK, m, pack.name);
								meta.corner = null;
							}
						}
					}

					SpriteAnimation.setMetadata(m.substring(path.length(), m.length() - 9), meta);
				} catch (JSONException | IOException e) {
					Logging.RESOURCEHANDLER_RESOURCEPACK.warn(e, "Unable to read {} in pack: {}", m, pack.name);
				}
			}

		}

		// Loading the left pngs.
		for (String p : pngs) {
			try {
				MinicraftImage image = ImageStore.load(pack.abs(p));
				if (image == null)
					throw new IOException("Image not decoded by the web platform: " + pack.abs(p));
				MinicraftImage sheet;
				if (type == SpriteType.Item) {
					validateImageAsset(pack, p, image.width, image.height, 8, 8);
					sheet = ImageStore.load(pack.abs(p), 8, 8); // Set the minimum tile sprite size.
				} else if (type == SpriteType.Tile) {
					validateImageAsset(pack, p, image.width, image.height, 16, 16);
					sheet = ImageStore.load(pack.abs(p), 16, 16); // Set the minimum item sprite size.
				} else {
					validateImageAsset(pack, p, image.width, image.height);
					sheet = image;
				}

				Renderer.spriteLinker.setSprite(type, p.substring(path.length(), p.length() - 4), sheet);
			} catch (IOException e) {
				Logging.RESOURCEHANDLER_RESOURCEPACK.warn("Unable to load {} in pack : {}", p, pack.name);
			}
		}
	}

	private static void validateImageAsset(ResourcePack pack, String key, int width, int height) {
		try {
			MinicraftImage.validateImageDimension(width, height);
		} catch (MinicraftImage.MinicraftImageDimensionIncompatibleException e) {
			Logging.RESOURCEHANDLER_RESOURCEPACK.warn("Potentially incompatible image detected: {} in pack: {}: "+
				"image size ({}x{}) is not in multiple of 8.", key, pack.name, e.getWidth(), e.getHeight());
		}
	}

	private static void validateImageAsset(ResourcePack pack, String key, int width, int height, int reqWidth, int reqHeight) {
		validateImageAsset(pack, key, width, height);
		try {
			MinicraftImage.validateImageDimension(width, height, reqWidth, reqHeight);
		} catch (MinicraftImage.MinicraftImageRequestOutOfBoundsException e) {
			Logging.RESOURCEHANDLER_RESOURCEPACK.warn("Potentially incompatible image detected: {} in pack: {}: "+
				"image size ({}x{}) is smaller than the required ({}x{}).", key, pack.name,
				e.getSourceWidth(), e.getSourceHeight(), e.getRequestedWidth(), e.getRequestedHeight());
		}
	}

	/**
	 * Loading localization from the pack.
	 * @param pack The pack to be loaded.
	 */
	private static void loadLocalization(ResourcePack pack) {
		JSONObject langJSON = null;
		try {
			langJSON = new JSONObject(readStringFromInputStream(pack.getResourceAsStream("pack.json"))).optJSONObject("language");
		} catch (JSONException | IOException e1) {
			Logging.RESOURCEHANDLER_RESOURCEPACK.debug(e1, "Unable to load pack.json in pack: {}", pack.name);
		}

		if (langJSON != null) {
			for (String loc : langJSON.keySet()) {
				try {
					Locale locale = Localization.localeFromTag(loc);
					JSONObject info = langJSON.getJSONObject(loc);
					Localization.addLocale(locale, new Localization.LocaleInformation(locale, info.getString("name"), info.getString("region")));
				} catch (JSONException e) {
					Logging.RESOURCEHANDLER_RESOURCEPACK.debug(e, "Invalid localization configuration in pack: {}", pack.name);
				}
			}
		}

		for (String f : pack.getFiles("assets/localization/", (path, isDir) -> path.endsWith(".json") && !isDir)) {
			String str = f.substring(f.lastIndexOf('/') + 1);
			try { // JSON verification.
				String json = readStringFromInputStream(pack.getResourceAsStream(f));
				JSONObject obj = new JSONObject(json);
				for (String k : obj.keySet()) {
					obj.getString(k);
				}

				// Add verified localization.
				Localization.addLocalization(Localization.localeFromTag(str.substring(0, str.length() - 5)), json);
			} catch (IOException e) {
				Logging.RESOURCEHANDLER_LOCALIZATION.debug(e, "Unable to load localization: {} in pack : {}", f, pack.name);
			} catch (JSONException e) {
				Logging.RESOURCEHANDLER_LOCALIZATION.debug(e, "Invalid JSON format detected in localization: {} in pack : {}", f, pack.name);
			}
		}
	}

	/**
	 * Loading the books from the pack.
	 * @param pack The pack to be loaded.
	 */
	private static void loadBooks(ResourcePack pack) {
		for (String path : pack.getFiles("assets/books", (path, isDir) -> path.endsWith(".txt") && !isDir)) {
			try {
				String book = BookData.loadBook(readStringFromInputStream(pack.getResourceAsStream(path)));
				switch (path) {
					case "assets/books/about.txt":
						BookData.about = () -> book;
						break;
					case "assets/books/credits.txt":
						BookData.credits = () -> book;
						break;
					case "assets/books/instructions.txt":
						BookData.instructions = () -> book;
						break;
					case "assets/books/antidous.txt":
						BookData.antVenomBook = () -> book;
						break;
					case "assets/books/game_guide.txt":
						BookData.storylineGuide = () -> book;
						break;
				}
			} catch (IOException e) {
				Logging.RESOURCEHANDLER_LOCALIZATION.debug(e, "Unable to load book: {} in pack : {}", path, pack.name);
			}
		}
	}

	/**
	 * Loading sounds from the pack.
	 * @param pack The pack to be loaded.
	 */
	private static void loadSounds(ResourcePack pack) {
		for (String f : pack.getFiles("assets/sound/", (path, isDir) -> path.endsWith(".wav") && !isDir)) {
			String name = f.substring(f.lastIndexOf('/') + 1);
			try {
				Sound.loadSound(name.substring(0, name.length() - 4), pack.getResourceAsStream(f), pack.name);
			} catch (IOException e) {
				Logging.RESOURCEHANDLER_LOCALIZATION.debug(e, "Unable to load audio: {} in pack : {}", f, pack.name);
			}
		}
	}
}
