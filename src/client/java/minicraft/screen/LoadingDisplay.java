package minicraft.screen;

import minicraft.core.Game;
import minicraft.core.World;
import minicraft.core.io.InputHandler;
import minicraft.core.io.Localization;
import minicraft.gfx.Color;
import minicraft.gfx.Ellipsis;
import minicraft.gfx.Ellipsis.DotUpdater.TimeUpdater;
import minicraft.gfx.Ellipsis.SmoothEllipsis;
import minicraft.gfx.Font;
import minicraft.gfx.FontStyle;
import minicraft.gfx.Screen;
import minicraft.saveload.Save;

public class LoadingDisplay extends Display {

	private static final int START_DELAY_TICKS = 30; // ~500ms at 60fps, matching the original swing Timer delay.

	private static float percentage = 0;
	private static String progressType = "";
	private static boolean localizeProgressType = true;

	private final Ellipsis ellipsis = new SmoothEllipsis(new TimeUpdater());

	private int startDelay = -1;

	private String msg = "";

	public LoadingDisplay() {
		super(true, false);
	}

	@Override
	public void init(Display parent) {
		if (parent != null && parent.getParent() == this) return; // Undefined behaviour
		super.init(parent);
		percentage = 0;
		progressType = "minicraft.displays.loading.message.world";
		localizeProgressType = true;
		if (WorldSelectDisplay.hasLoadedWorld())
			msg = "minicraft.displays.loading.message.loading";
		else
			msg = "minicraft.displays.loading.message.generating";
		startDelay = 0;
	}

	@Override
	public void tick(InputHandler input) {
		super.tick(input);

		if (startDelay >= 0) {
			startDelay++;
			if (startDelay > START_DELAY_TICKS) {
				startDelay = -1;
				startWorldInit();
			}
		}
	}

	private void startWorldInit() {
		new Thread(() -> { // A new thread is required as this blocks the running thread.
			try {
				World.initWorld();
				Game.setDisplay(null);
			} catch (RuntimeException ex) {
				Throwable t = ex.getCause();
				if (t instanceof InterruptedException) {
					World.onWorldExits();
					Game.exitDisplay(); // Exits the loading display and returns to world select display.
					Game.setDisplay(new PopupDisplay(null, "minicraft.displays.loading.regeneration_cancellation_popup.display"));
				} else
					throw ex;
			}
		}, "World Initialization Thread").start();
	}

	@Override
	public void onExit() {
		percentage = 0;
		if (!WorldSelectDisplay.hasLoadedWorld()) {
			msg = "minicraft.displays.loading.message.saving";
			progressType = "minicraft.displays.loading.message.world";
			localizeProgressType = true;
			new Save(WorldSelectDisplay.getWorldName());
			Game.notifications.clear();
		}
	}

	public static void setPercentage(float percent) {
		percentage = percent;
	}

	public static float getPercentage() {
		return percentage;
	}

	public static void setMessage(String progressType) {
		setMessage(progressType, true);
	}

	public static void setMessage(String progressType, boolean localize) {
		LoadingDisplay.progressType = progressType;
		localizeProgressType = localize;
	}

	public static void progress(float amt) {
		percentage = Math.min(100, percentage + amt);
	}

	@Override
	public void render(Screen screen) {
		super.render(screen);
		int percent = Math.round(percentage);
		Font.drawParagraph(screen, new FontStyle(Color.RED), 6,
			Localization.getLocalized(msg) + (progressType.length() > 0 ? " " + (localizeProgressType ? Localization.getLocalized(progressType) : progressType) : "")
				+ ellipsis.updateAndGet(),
			percent + "%"
		);
	}
}
