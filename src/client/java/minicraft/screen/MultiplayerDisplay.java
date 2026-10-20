package minicraft.screen;

import minicraft.core.Game;
import minicraft.core.io.InputHandler;
import minicraft.gfx.Color;
import minicraft.gfx.Ellipsis;
import minicraft.gfx.Ellipsis.SequentialEllipsis;
import minicraft.gfx.Font;
import minicraft.gfx.FontStyle;
import minicraft.gfx.Screen;

/**
 * @deprecated As multiplayer mode removed. This class is not localized.
 * On the web there is no account server or multiplayer; the display immediately
 * falls back to the offline "enter ip" state so the legacy UI remains reachable.
 */
@Deprecated
public class MultiplayerDisplay extends Display {

	public static String savedIP = "";
	public static String savedUUID = "";
	public static String savedUsername = "";

	private String waitingMessage = "waiting...";
	private String loadingMessage = "nothing";
	private String errorMessage = "";

	private String typing = "";
	private boolean inputIsValid = false;

	private boolean online = false;

	private Ellipsis ellipsis = new SequentialEllipsis();

	private enum State {
		WAITING, ENTERIP, LOADING, ERROR
	}

	private State curState;

	public MultiplayerDisplay() {
		this(true);
	}

	public MultiplayerDisplay(boolean pingSite) {
		if (savedUUID == null) savedUUID = "";
		if (savedUsername == null) savedUsername = "";

		if (pingSite)
			contactAccountServer(() -> {
			});
	}

	private void contactAccountServer(Action sitePingCallback) {
		// Offline on the web: mirror the original connection-failed path.
		if (savedUsername.length() == 0 || savedUUID.length() == 0) {
			// couldn't validate username, and can't enter offline mode b/c there is no username
			setError("could not connect to playminicraft account server, but no login data saved; cannot enter offline mode.", false);
			return;
		}

		// there is a saved copy of the uuid and username of the last player; use it for offline mode.
		typing = savedIP;
		curState = State.ENTERIP;

		sitePingCallback.act();
	}

	@Override
	public void tick(InputHandler input) {
	}

	public void setWaitMessage(String message) {
		waitingMessage = message;
		curState = State.WAITING;
	}

	public void setLoadingMessage(String msg) {
		curState = State.LOADING;
		loadingMessage = msg;
	}

	public void setError(String msg) {
		setError(msg, true);
	}

	private void setError(String msg, boolean overrideMenu) {
		if (curState == State.ERROR) return; // keep original message
		this.curState = State.ERROR;
		errorMessage = msg;
	}

	@Override
	public void render(Screen screen) {
		screen.clear(0);

		switch (curState) {
			case ENTERIP:
				Font.drawCentered("Logged in as: " + savedUsername, screen, 6, Color.get(1, 102, 255, 102));

				if (!online)
					Font.drawCentered("Offline mode: local servers only", screen, Screen.h / 2 - Font.textHeight() * 6, Color.get(1, 153, 153, 255));

				Font.drawCentered("Enter ip address to connect to:", screen, Screen.h / 2 - Font.textHeight() * 2 - 2, Color.get(1, 255));
				Font.drawCentered(typing, screen, Screen.h / 2 - Font.textHeight(), Color.get(1, 255, 255, 102));

				Font.drawCentered("Press Shift-Escape to logout", screen, Screen.h - Font.textHeight() * 7, Color.get(1, 204));
				break;

			case WAITING:
				Font.drawCentered(waitingMessage + ellipsis.updateAndGet(), screen, Screen.h / 2, Color.WHITE);
				break;

			case LOADING:
				Font.drawCentered("Loading " + loadingMessage + " from server" + ellipsis.updateAndGet(), screen, Screen.h / 2, Color.WHITE);
				break;

			case ERROR:
				Font.drawCentered("Could not connect to server:", screen, Screen.h / 2 - 6, Color.RED);
				FontStyle style = new FontStyle(Color.get(1, 255, 51, 51)).setYPos(Screen.h / 2 + 6);
				Font.drawParagraph(errorMessage, screen, style, 1);
				break;
		}

		if (curState == State.ENTERIP || curState == State.ERROR) {
			Font.drawCentered("Press " + Game.input.getMapping("exit") + " to return", screen, Screen.h - Font.textHeight() * 2, Color.GRAY);
		}
	}

	private interface Action {
		void act();
	}
}
