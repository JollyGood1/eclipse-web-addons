package io.github.jollygood1.syntaxthemes;

import org.eclipse.osgi.util.NLS;

public final class Messages extends NLS {
	public static String page_description;
	public static String page_themes;
	public static String page_preview;
	public static String page_import;
	public static String page_importTitle;
	public static String page_importFailed;
	public static String page_installWide;
	public static String page_semantic;
	public static String page_semanticTip;
	public static String page_current;
	public static String page_author;
	public static String page_none;
	public static String page_applied;
	public static String page_restored;
	public static String page_failed;

	static {
		NLS.initializeMessages("io.github.jollygood1.syntaxthemes.messages", Messages.class); //$NON-NLS-1$
	}

	private Messages() {
	}
}
