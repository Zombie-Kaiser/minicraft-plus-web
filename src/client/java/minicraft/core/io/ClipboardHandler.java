package minicraft.core.io;

import minicraft.web.Platform;

public final class ClipboardHandler {

	private String lastCopied = "";

	/**
	 * Give the system clipboard data.
	 */
	public void setClipboardContents(String string) {
		lastCopied = string;
		Platform.clipboardWrite(string);
	}

	/**
	 * Get the string from the system clipboard data.
	 * Web port: browsers cannot read the clipboard synchronously; the JS side keeps
	 * a cached copy from permission-granted reads, falling back to what we copied ourselves.
	 * @return A string with the contents.
	 */
	public String getClipboardContents() {
		String cached = Platform.clipboardRead();
		if (cached != null && !cached.isEmpty()) return cached;
		return lastCopied;
	}
}
