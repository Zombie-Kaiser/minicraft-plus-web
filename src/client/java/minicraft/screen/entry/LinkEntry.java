package minicraft.screen.entry;

import minicraft.core.Game;
import minicraft.core.io.Localization;
import minicraft.screen.Menu;
import minicraft.screen.PopupDisplay;
import minicraft.screen.RelPos;
import minicraft.screen.TempDisplay;
import minicraft.web.Platform;

public class LinkEntry extends SelectEntry {

	private static final String openMsg = "minicraft.display.gui.link_opening";

	private final int color;

	// note that if the failMsg should be localized, such must be done before passing them as parameters, for this class will not do it since, by default, the failMsg contains a url.

	public LinkEntry(int color, String urlText) {
		this(color, urlText, urlText, false);
	}

	public LinkEntry(int color, String text, String url) {
		this(color, text, url, true);
	}

	public LinkEntry(int color, String text, String url, String failMsg) {
		this(color, text, url, failMsg, true);
	}

	public LinkEntry(int color, String text, String url, boolean localize) {
		this(color, text, url, Localization.getLocalized("Go to") + ": " + url, localize);
	}

	public LinkEntry(int color, String text, String url, String failMsg, boolean localize) {
		super(text, () -> {
			// try to open the link directly from the browser.
			Game.setDisplay(new TempDisplay(3000, false, true, new Menu.Builder(true, 0, RelPos.CENTER, new StringEntry(Localization.getLocalized(openMsg))).createMenu()));
			Platform.openUrl(url);
		}, localize);

		this.color = color;
	}

	@Override
	public int getColor(boolean isSelected) {
		return color;
	}
}
