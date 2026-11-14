package io.github.jollygood1.syntaxthemes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;

/** Finds themes (extension point + imported files) and editor mappings (extension point). */
public final class ThemeRegistry {

	public static final String PLUGIN_ID = "io.github.jollygood1.syntaxthemes"; //$NON-NLS-1$
	private static final String TM_SUFFIX = ".tmtheme.json"; //$NON-NLS-1$

	private ThemeRegistry() {
	}

	/** All themes by id, sorted by name. An imported theme replaces a bundled one with the same name. */
	public static Map<String, ColorTheme> themes() {
		List<ColorTheme> list = new ArrayList<>();
		for (IConfigurationElement el : Platform.getExtensionRegistry().getConfigurationElementsFor(PLUGIN_ID + ".themes")) { //$NON-NLS-1$
			try (InputStream in = open(el)) {
				ColorTheme theme = ColorTheme.read(in, el.getAttribute("file")); //$NON-NLS-1$
				theme.textmateThemeId = el.getAttribute("textmateThemeId"); //$NON-NLS-1$
				list.add(theme);
			} catch (IOException | RuntimeException e) {
				log(el, e);
			}
		}
		Path dir = importDir();
		if (Files.isDirectory(dir)) {
			try (Stream<Path> files = Files.list(dir)) {
				for (Path xml : files.filter(p -> p.toString().endsWith(".xml")).toList()) { //$NON-NLS-1$
					try (InputStream in = Files.newInputStream(xml)) {
						ColorTheme theme = ColorTheme.read(in, baseName(xml));
						Path tm = dir.resolve(baseName(xml) + TM_SUFFIX);
						if (Files.exists(tm)) {
							theme.textmateFile = tm;
						}
						list.add(theme);
					} catch (IOException | RuntimeException e) {
						ILog.of(ThemeRegistry.class).error("Cannot read colour theme " + xml, e); //$NON-NLS-1$
					}
				}
			} catch (IOException e) {
				ILog.of(ThemeRegistry.class).error("Cannot list " + dir, e); //$NON-NLS-1$
			}
		}
		list.sort(Comparator.comparing(ColorTheme::getName, String.CASE_INSENSITIVE_ORDER));
		Map<String, ColorTheme> out = new LinkedHashMap<>();
		list.forEach(t -> out.put(t.getId(), t));
		return out;
	}

	public static List<EditorMapping> mappings() {
		List<EditorMapping> out = new ArrayList<>();
		for (IConfigurationElement el : Platform.getExtensionRegistry().getConfigurationElementsFor(PLUGIN_ID + ".mappings")) { //$NON-NLS-1$
			try (InputStream in = open(el)) {
				out.add(EditorMapping.read(in));
			} catch (IOException | RuntimeException e) {
				log(el, e);
			}
		}
		return out;
	}

	/** Imported themes live in the configuration area, so every workspace of this Eclipse sees them. */
	public static Path importDir() {
		URL url = Platform.getConfigurationLocation().getURL();
		return new File(url.getPath()).toPath().resolve(PLUGIN_ID).resolve("themes"); //$NON-NLS-1$
	}

	/**
	 * Copies a palette XML into the import folder. A TextMate theme next to it with the same base name
	 * (name.tmtheme.json or name.json) is copied too.
	 */
	public static ColorTheme importTheme(Path xml) throws IOException {
		ColorTheme theme;
		try (InputStream in = Files.newInputStream(xml)) {
			theme = ColorTheme.read(in, baseName(xml));
		}
		Path dir = importDir();
		Files.createDirectories(dir);
		String base = baseName(xml);
		Files.copy(xml, dir.resolve(base + ".xml"), StandardCopyOption.REPLACE_EXISTING); //$NON-NLS-1$
		for (String suffix : new String[] { TM_SUFFIX, ".json" }) { //$NON-NLS-1$
			Path tm = xml.resolveSibling(base + suffix);
			if (Files.exists(tm)) {
				theme.textmateFile = dir.resolve(base + TM_SUFFIX);
				Files.copy(tm, theme.textmateFile, StandardCopyOption.REPLACE_EXISTING);
				break;
			}
		}
		return theme;
	}

	private static String baseName(Path file) {
		String n = file.getFileName().toString();
		return n.substring(0, n.lastIndexOf('.'));
	}

	private static InputStream open(IConfigurationElement el) throws IOException {
		Bundle bundle = Platform.getBundle(el.getContributor().getName());
		URL url = bundle == null ? null : bundle.getEntry(el.getAttribute("file")); //$NON-NLS-1$
		if (url == null) {
			throw new IOException("File not found in bundle: " + el.getAttribute("file")); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return url.openStream();
	}

	private static void log(IConfigurationElement el, Exception e) {
		ILog.of(ThemeRegistry.class).error("Bad contribution from " + el.getContributor().getName() + ": " //$NON-NLS-1$ //$NON-NLS-2$
				+ el.getAttribute("file"), e); //$NON-NLS-1$
	}
}
