package minicraft.network;

import minicraft.core.Action;
import minicraft.core.Game;
import minicraft.core.VersionInfo;
import minicraft.saveload.Version;
import minicraft.entity.Entity;
import minicraft.level.Level;
import minicraft.util.Logging;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

public class Network extends Game {
	private Network() {
	}

	private static final Random random = new Random();

	private static VersionInfo latestVersion = null;


	@Nullable
	public static VersionInfo getLatestVersion() {
		return latestVersion;
	}

	/** Offline on the web: the current version is always the latest. */
	public static void findLatestVersion(Action callback) {
		Logging.NETWORK.debug("Version check skipped on web; using current version.");
		latestVersion = new VersionInfo(VERSION, "", "");
		callback.act(); // finished.
	}

	@Nullable
	public static Entity getEntity(int eid) {
		for (Level level : levels) {
			if (level == null) continue;
			for (Entity e : level.getEntityArray())
				if (e.eid == eid)
					return e;
		}

		return null;
	}

	public static int generateUniqueEntityId() {
		int eid;
		int tries = 0; // Just in case it gets out of hand.
		do {
			tries++;
			if (tries == 1000)
				Logging.NETWORK.info("Note: Trying 1000th time to find valid entity id...(Will continue)");

			eid = random.nextInt();
		} while (!idIsAvailable(eid));

		return eid;
	}

	public static boolean idIsAvailable(int eid) {
		if (eid == 0) return false; // This is reserved for the main player... kind of...
		if (eid < 0) return false; // ID's must be positive numbers.

		for (Level level : levels) {
			if (level == null) continue;
			for (Entity e : level.getEntityArray()) {
				if (e.eid == eid)
					return false;
			}
		}

		return true;
	}


}
