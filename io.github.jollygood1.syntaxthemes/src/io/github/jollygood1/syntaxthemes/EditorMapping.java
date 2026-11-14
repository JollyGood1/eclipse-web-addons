package io.github.jollygood1.syntaxthemes;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Which preference keys of one editor plug-in get which palette colour, and in which text format. */
public final class EditorMapping {

	private record Entry(String key, String themeKey, String template, String constant) {
	}

	private final String plugin;
	/** How this editor stores bold/italic: "wst" inside the value, "java" as keys with a suffix, "" not at all. */
	private String styles = ""; //$NON-NLS-1$
	private final List<Entry> entries = new ArrayList<>();

	private EditorMapping(String plugin) {
		this.plugin = plugin;
	}

	public static EditorMapping read(InputStream in) throws IOException {
		try {
			Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in).getDocumentElement();
			EditorMapping mapping = new EditorMapping(root.getAttribute("plugin")); //$NON-NLS-1$
			String template = root.getAttribute("template"); //$NON-NLS-1$
			mapping.styles = root.getAttribute("styles"); //$NON-NLS-1$
			for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
				if (!(n instanceof Element e)) {
					continue;
				}
				if ("map".equals(e.getTagName())) { //$NON-NLS-1$
					String own = e.getAttribute("template"); //$NON-NLS-1$
					mapping.entries.add(new Entry(e.getAttribute("key"), e.getAttribute("theme"), //$NON-NLS-1$ //$NON-NLS-2$
							own.isEmpty() ? template : own, null));
				} else if ("set".equals(e.getTagName())) { //$NON-NLS-1$
					mapping.entries.add(new Entry(e.getAttribute("key"), null, null, e.getAttribute("value"))); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
			return mapping;
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException(e.getMessage(), e);
		}
	}

	/**
	 * Web editors keep the styles in the value: colour | background | bold | italic | strikethrough | underline.
	 * A style the theme states replaces the template's field; a field the theme says nothing about stays.
	 */
	private static String withInlineStyles(String value, ColorTheme theme, String themeKey) {
		List<String> fields = new ArrayList<>(List.of(value.split(" [|] ", -1))); //$NON-NLS-1$
		for (int i = 0; i < ColorTheme.STYLES.length; i++) {
			Boolean on = theme.style(themeKey, ColorTheme.STYLES[i]);
			if (on == null) {
				continue;
			}
			while (fields.size() <= i + 2) {
				fields.add("null"); //$NON-NLS-1$
			}
			fields.set(i + 2, on.toString());
		}
		return String.join(" | ", fields); //$NON-NLS-1$
	}

	/** The preference node (plug-in id) the values go into. */
	public String getPlugin() {
		return plugin;
	}

	/**
	 * Preference key to value for this theme. Keys whose colour the theme does not define are left out.
	 *
	 * @param enableSemantic also switch on every semantic highlight that gets a colour ("x.enabled" next to
	 *                       "x.color"); otherwise the editor's own on/off setting stays
	 */
	public Map<String, String> valuesFor(ColorTheme theme, boolean enableSemantic) {
		Map<String, String> out = new LinkedHashMap<>();
		for (Entry e : entries) {
			if (e.constant != null) {
				out.put(e.key, e.constant);
				continue;
			}
			String hex = theme.hex(e.themeKey);
			if (hex == null) {
				continue;
			}
			int[] c = theme.rgb(e.themeKey);
			String value = e.template.replace("{hex}", hex).replace("{rgb}", c[0] + "," + c[1] + "," + c[2]); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			boolean semantic = e.key.endsWith(".color"); //$NON-NLS-1$
			if ("wst".equals(styles) && !semantic) { //$NON-NLS-1$
				value = withInlineStyles(value, theme, e.themeKey);
			}
			out.put(e.key, value);
			// separate keys: semanticHighlighting.x.bold next to semanticHighlighting.x.color, java_keyword_bold next to java_keyword
			String base = semantic ? e.key.substring(0, e.key.length() - 6) + "." : "java".equals(styles) ? e.key + "_" : null; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			if (semantic && enableSemantic) {
				out.put(base + "enabled", "true"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			if (base != null) {
				for (String style : ColorTheme.STYLES) {
					Boolean on = theme.style(e.themeKey, style);
					if (on != null) {
						out.put(base + style, on.toString());
					}
				}
			}
		}
		return out;
	}
}
