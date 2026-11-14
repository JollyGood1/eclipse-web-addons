package io.github.jollygood1.syntaxthemes;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

/**
 * Writes a theme into the editor plug-ins' preference nodes (workspace scope) and undoes it again.
 * Every value that is overwritten is first saved under this plug-in's own node ("backup/&lt;plug-in&gt;"),
 * so switching themes or restoring never leaves keys of an earlier theme behind.
 */
public final class ThemeApplier {

	/** What an apply did: editor plug-ins written, and mappings skipped because the editor is not installed. */
	public record Result(List<String> applied, List<String> skipped) {
	}

	private static final String CURRENT = "currentTheme"; //$NON-NLS-1$
	private static final String SEMANTIC = "enableSemanticHighlighting"; //$NON-NLS-1$
	private static final String BACKUP = "backup"; //$NON-NLS-1$
	private static final String ABSENT = "A"; //$NON-NLS-1$
	private static final String VALUE = "V"; //$NON-NLS-1$
	/** Key in which the e4 CSS engine lists the preferences it set for the window theme (comma separated). */
	private static final String CSS_OVERRIDDEN = "overriddenByCSS"; //$NON-NLS-1$
	private static final String TM4E = "org.eclipse.tm4e.ui"; //$NON-NLS-1$
	private static final String TM4E_THEMES = "org.eclipse.tm4e.ui.themes"; //$NON-NLS-1$

	private ThemeApplier() {
	}

	private static IEclipsePreferences own() {
		return InstanceScope.INSTANCE.getNode(ThemeRegistry.PLUGIN_ID);
	}

	/** Id of the theme applied in this workspace, or null. */
	public static String currentThemeId() {
		return own().get(CURRENT, null);
	}

	/**
	 * @param allMappings      also write mappings whose editor plug-in is not installed
	 * @param alsoInstallWide  also write the values at configuration scope (default for workspaces
	 *                         that have no colours of their own); that copy is not covered by restore
	 * @param enableSemantic   also switch on the semantic highlights the theme colours (see
	 *                         {@link EditorMapping#valuesFor}); remembered for {@link #reapplyCurrent()}
	 */
	public static Result apply(ColorTheme theme, boolean allMappings, boolean alsoInstallWide, boolean enableSemantic)
			throws BackingStoreException {
		restore();
		own().putBoolean(SEMANTIC, enableSemantic);
		return writeAll(theme, allMappings, alsoInstallWide);
	}

	/** Whether the theme of this workspace was applied with semantic highlighting switched on. */
	public static boolean semanticEnabled() {
		return own().getBoolean(SEMANTIC, false);
	}

	/**
	 * Writes the workspace's current theme again. Needed after the window theme was switched while Eclipse
	 * runs: the new window theme's stylesheet then overwrites every editor colour it defines.
	 */
	public static void reapplyCurrent() throws BackingStoreException {
		String id = currentThemeId();
		ColorTheme theme = id == null ? null : ThemeRegistry.themes().get(id);
		if (theme != null) {
			writeAll(theme, false, false);
		}
	}

	private static Result writeAll(ColorTheme theme, boolean allMappings, boolean alsoInstallWide)
			throws BackingStoreException {
		Result result = new Result(new ArrayList<>(), new ArrayList<>());
		Preferences backup = own().node(BACKUP);
		for (EditorMapping mapping : ThemeRegistry.mappings()) {
			String plugin = mapping.getPlugin();
			if (!allMappings && Platform.getBundle(plugin) == null) {
				result.skipped.add(plugin);
				continue;
			}
			write(plugin, mapping.valuesFor(theme, semanticEnabled()), backup, alsoInstallWide);
			result.applied.add(plugin);
		}
		if (allMappings || Platform.getBundle(TM4E) != null) {
			Map<String, String> tm = textmateValues(theme);
			if (!tm.isEmpty()) {
				write(TM4E, tm, backup, alsoInstallWide);
				result.applied.add(TM4E);
			}
		}
		own().put(CURRENT, theme.getId());
		own().flush();
		return result;
	}

	/** Puts back the values from before the first apply. Returns false when no theme was applied. */
	public static boolean restore() throws BackingStoreException {
		IEclipsePreferences own = own();
		if (!own.nodeExists(BACKUP)) {
			return false;
		}
		Preferences backup = own.node(BACKUP);
		for (String plugin : backup.childrenNames()) {
			Preferences saved = backup.node(plugin);
			IEclipsePreferences node = InstanceScope.INSTANCE.getNode(plugin);
			for (String key : saved.keys()) {
				String old = saved.get(key, ABSENT);
				if (key.equals(CSS_OVERRIDDEN)) {
					continue;
				}
				if (old.startsWith(VALUE)) {
					node.put(key, old.substring(1));
				} else {
					node.remove(key);
				}
			}
			// last, because Eclipse strips a key from this list whenever the key is written
			String list = saved.get(CSS_OVERRIDDEN, null);
			if (list != null) {
				node.put(CSS_OVERRIDDEN, list.substring(1));
			}
			node.flush();
		}
		backup.removeNode();
		own.remove(CURRENT);
		own.remove(SEMANTIC);
		own.flush();
		return true;
	}

	private static void write(String plugin, Map<String, String> values, Preferences backup, boolean alsoInstallWide)
			throws BackingStoreException {
		IEclipsePreferences node = InstanceScope.INSTANCE.getNode(plugin);
		Preferences saved = backup.node(plugin);
		// The window theme's CSS removes the keys on its list at shutdown and on a theme switch.
		// Save the list as it was (for restore), then take our keys off it.
		String overridden = node.get(CSS_OVERRIDDEN, null);
		if (overridden != null && saved.get(CSS_OVERRIDDEN, null) == null) {
			saved.put(CSS_OVERRIDDEN, VALUE + overridden);
		}
		values.forEach((key, value) -> put(node, saved, key, value));
		String now = node.get(CSS_OVERRIDDEN, null);
		if (now != null) {
			node.put(CSS_OVERRIDDEN, withoutKeys(now, values.keySet()));
		}
		node.flush();
		if (alsoInstallWide) {
			IEclipsePreferences shared = ConfigurationScope.INSTANCE.getNode(plugin);
			values.forEach(shared::put);
			shared.flush();
		}
	}

	private static void put(IEclipsePreferences node, Preferences saved, String key, String value) {
		if (saved.get(key, null) == null) {
			String old = node.get(key, null);
			saved.put(key, old == null ? ABSENT : VALUE + old);
		}
		node.put(key, value);
	}

	static String withoutKeys(String list, Set<String> keys) {
		String kept = Arrays.stream(list.split(",")).filter(k -> !k.isEmpty() && !keys.contains(k)) //$NON-NLS-1$
				.collect(Collectors.joining(",")); //$NON-NLS-1$
		// keep the engine's own style: it writes a separator on both sides of every name
		return list.startsWith(",") ? "," + kept + (kept.isEmpty() ? "" : ",") : kept; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

	/** TM4E colours the Generic Code Editor; its default theme is chosen by id. */
	private static Map<String, String> textmateValues(ColorTheme theme) {
		String id = theme.textmateThemeId;
		Map<String, String> out = new java.util.LinkedHashMap<>();
		Path file = theme.textmateFile;
		String kind = " (imported)"; //$NON-NLS-1$
		if (file == null && (id == null || id.isEmpty())) {
			// no TextMate theme comes with this palette: build one from its colours
			try {
				file = TextMateGenerator.write(theme);
				kind = " (generated)"; //$NON-NLS-1$
			} catch (IOException | RuntimeException e) {
				ILog.of(ThemeApplier.class).error("Cannot write a TextMate theme for " + theme.getName(), e); //$NON-NLS-1$
			}
		}
		if (file != null) {
			// a file on disk: register it by path in TM4E's list of user themes
			id = theme.getName() + kind;
			String list = Platform.getPreferencesService().getString(TM4E, TM4E_THEMES, "[]", null).trim(); //$NON-NLS-1$
			String idJson = "\"id\":" + json(id); //$NON-NLS-1$
			if (!list.replace(" ", "").contains(idJson)) { //$NON-NLS-1$ //$NON-NLS-2$
				String entry = "{" + idJson + ",\"path\":" + json(file.toString()) + ",\"dark\":" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						+ theme.isDark() + "}"; //$NON-NLS-1$
				boolean empty = list.length() < 3 || !list.endsWith("]"); //$NON-NLS-1$
				out.put(TM4E_THEMES, empty ? "[" + entry + "]" : list.substring(0, list.length() - 1) + "," + entry + "]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			}
		}
		if (id != null && !id.isEmpty()) {
			// TM4E picks "dark" or "light" by the window theme, not by this palette: set both
			out.put(TM4E_THEMES + ".defaultDarkTheme", id); //$NON-NLS-1$
			out.put(TM4E_THEMES + ".defaultLightTheme", id); //$NON-NLS-1$
		}
		return out;
	}

	private static String json(String s) {
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
	}
}
