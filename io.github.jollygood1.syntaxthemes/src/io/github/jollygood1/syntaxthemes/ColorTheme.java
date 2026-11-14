package io.github.jollygood1.syntaxthemes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** A palette: colour key (keyword, string, background ...) to #RRGGBB. File format: Eclipse Color Theme XML. */
public final class ColorTheme {

	/** Style names in the order the web editors store them: colour | background | bold | italic | strikethrough | underline. */
	public static final String[] STYLES = { "bold", "italic", "strikethrough", "underline" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

	private final String name;
	private final String author;
	private final Map<String, String> colors = new LinkedHashMap<>();
	/** "key.bold", "key.italic", "key.underline", "key.strikethrough" for the settings a theme states. */
	private final Map<String, Boolean> styles = new LinkedHashMap<>();
	private String website = ""; //$NON-NLS-1$
	/** Id of a TM4E theme contributed by a plug-in; null if none. */
	String textmateThemeId;
	/** TextMate theme file on disk (imported themes); null if none. */
	Path textmateFile;

	private ColorTheme(String name, String author) {
		this.name = name;
		this.author = author;
	}

	public static ColorTheme read(InputStream in, String fallbackName) throws IOException {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
			Element root = factory.newDocumentBuilder().parse(in).getDocumentElement();
			String name = root.getAttribute("name").isBlank() ? fallbackName : root.getAttribute("name").trim(); //$NON-NLS-1$ //$NON-NLS-2$
			ColorTheme theme = new ColorTheme(name, root.getAttribute("author")); //$NON-NLS-1$
			theme.website = root.getAttribute("website"); //$NON-NLS-1$
			for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
				if (n instanceof Element e) {
					String color = e.getAttribute("color").trim().toUpperCase(Locale.ROOT); //$NON-NLS-1$
					if (color.matches("#[0-9A-F]{6}")) { //$NON-NLS-1$
						theme.colors.put(e.getTagName(), color);
						for (String style : STYLES) {
							String value = e.getAttribute(style);
							if (!value.isEmpty()) {
								theme.styles.put(e.getTagName() + "." + style, Boolean.valueOf(value)); //$NON-NLS-1$
							}
						}
					}
				}
			}
			if (!theme.colors.containsKey("background") || !theme.colors.containsKey("foreground")) { //$NON-NLS-1$ //$NON-NLS-2$
				throw new IOException("Not a colour theme: no background/foreground colour"); //$NON-NLS-1$
			}
			return theme;
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException(e.getMessage(), e);
		}
	}

	/** The name is also the id (ids in theme files from the old theme site are not reliable). */
	public String getId() {
		return name;
	}

	public String getName() {
		return name;
	}

	public String getAuthor() {
		return author;
	}

	public String getWebsite() {
		return website;
	}

	/** TRUE or FALSE when the theme states this style for the key, null when it says nothing. */
	public Boolean style(String key, String style) {
		return styles.get(key + "." + style); //$NON-NLS-1$
	}

	/** #RRGGBB (upper case) or null when the theme has no such key. */
	public String hex(String key) {
		return colors.get(key);
	}

	public int[] rgb(String key) {
		String h = hex(key);
		return h == null ? null
				: new int[] { Integer.parseInt(h.substring(1, 3), 16), Integer.parseInt(h.substring(3, 5), 16),
						Integer.parseInt(h.substring(5, 7), 16) };
	}

	public boolean isDark() {
		int[] c = rgb("background"); //$NON-NLS-1$
		return (299 * c[0] + 587 * c[1] + 114 * c[2]) / 1000 < 128;
	}
}
