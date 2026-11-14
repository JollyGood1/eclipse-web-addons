package io.github.jollygood1.fileicons;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.workbench.IPresentationEngine;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.DecorationOverlayIcon;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageDataProvider;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IFileEditorMapping;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.osgi.framework.Bundle;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

/**
 * Which icon a file gets. The answer is built from the selected icon set (file type to image) and, on top
 * of it, the user's own choices per file type. Both are stored per Eclipse install (configuration scope).
 * <p>
 * A "file type" is a lower-case file name (package.json) or extension (php). A "choice" is one of
 * {@code set:<set id>:<image file>}, {@code file:<imported image>}, {@code registry:<file name>} (the icon
 * Eclipse itself uses for such a file), {@code shared:<key>} (a general workbench icon) or {@link #ECLIPSE}.
 */
public final class IconRegistry {

	/** One contributed icon set: its images and its default mapping. */
	public record IconSet(String id, String name, Bundle bundle, String folder, Map<String, String> mapping,
			List<String> images) {
	}

	public static final String PLUGIN_ID = "io.github.jollygood1.fileicons"; //$NON-NLS-1$
	public static final String DECORATOR_ID = PLUGIN_ID + ".decorator"; //$NON-NLS-1$
	/** Set id meaning "no icon set". */
	public static final String NO_SET = "none"; //$NON-NLS-1$
	/** Choice meaning "keep Eclipse's own icon for this type". */
	public static final String ECLIPSE = "eclipse"; //$NON-NLS-1$
	private static final String PREF_SET = "set"; //$NON-NLS-1$
	private static final String PREF_TYPES = "types"; //$NON-NLS-1$
	private static final int SIZE = 16;

	private static List<IconSet> sets;
	private static Map<String, String> effective;
	/** Images by choice. Never disposed: tabs and views may still show an image after a change. */
	private static final Map<String, Image> IMAGES = new HashMap<>();

	private IconRegistry() {
	}

	// ---------- icon sets ----------

	public static synchronized List<IconSet> sets() {
		if (sets == null) {
			List<IconSet> list = new ArrayList<>();
			for (IConfigurationElement el : Platform.getExtensionRegistry().getConfigurationElementsFor(PLUGIN_ID + ".iconSets")) { //$NON-NLS-1$
				try {
					list.add(readSet(el));
				} catch (IOException | RuntimeException e) {
					ILog.of(IconRegistry.class).error("Bad icon set from " + el.getContributor().getName(), e); //$NON-NLS-1$
				}
			}
			sets = list;
		}
		return sets;
	}

	private static IconSet readSet(IConfigurationElement el) throws IOException {
		Bundle bundle = Platform.getBundle(el.getContributor().getName());
		String folder = el.getAttribute("folder"); //$NON-NLS-1$
		Map<String, String> mapping = new TreeMap<>();
		URL url = bundle.getEntry(folder + "/mapping.properties"); //$NON-NLS-1$
		if (url != null) {
			Properties p = new Properties();
			try (InputStream in = url.openStream()) {
				p.load(in);
			}
			p.forEach((k, v) -> mapping.put(k.toString().toLowerCase(Locale.ROOT), v.toString().trim()));
		}
		List<String> images = new ArrayList<>();
		Enumeration<String> paths = bundle.getEntryPaths(folder);
		while (paths != null && paths.hasMoreElements()) {
			String name = paths.nextElement().substring(folder.length() + 1);
			if (name.endsWith(".png") && !name.contains("@")) { //$NON-NLS-1$ //$NON-NLS-2$
				images.add(name);
			}
		}
		Collections.sort(images);
		return new IconSet(el.getAttribute("id"), el.getAttribute("name"), bundle, folder, mapping, images); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static IconSet set(String id) {
		return sets().stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
	}

	// ---------- stored choices ----------

	private static IEclipsePreferences prefs() {
		return ConfigurationScope.INSTANCE.getNode(PLUGIN_ID);
	}

	/** The selected set's id, {@link #NO_SET}, or the first contributed set when nothing was chosen yet. */
	public static String selectedSetId() {
		String fallback = sets().isEmpty() ? NO_SET : sets().get(0).id();
		String id = prefs().get(PREF_SET, fallback);
		return NO_SET.equals(id) || set(id) != null ? id : fallback;
	}

	/** The user's own choices: file type to choice. */
	public static Map<String, String> overrides() {
		Map<String, String> out = new TreeMap<>();
		try {
			Preferences node = prefs().node(PREF_TYPES);
			for (String type : node.keys()) {
				out.put(type, node.get(type, ECLIPSE));
			}
		} catch (BackingStoreException e) {
			ILog.of(IconRegistry.class).error("Cannot read the file icon choices", e); //$NON-NLS-1$
		}
		return out;
	}

	/** File type to choice for a set plus overrides; types set to {@link #ECLIPSE} stay in the map. */
	public static Map<String, String> combine(String setId, Map<String, String> overrides) {
		Map<String, String> out = new TreeMap<>();
		IconSet set = set(setId);
		if (set != null) {
			set.mapping().forEach((type, image) -> out.put(type, "set:" + set.id() + ":" + image)); //$NON-NLS-1$ //$NON-NLS-2$
		}
		out.putAll(overrides);
		return out;
	}

	/** Stores the choices and refreshes views and editor tabs. */
	public static void save(String setId, Map<String, String> overrides) throws BackingStoreException {
		IEclipsePreferences prefs = prefs();
		prefs.put(PREF_SET, setId);
		prefs.node(PREF_TYPES).removeNode();
		Preferences node = prefs.node(PREF_TYPES);
		overrides.forEach(node::put);
		prefs.flush();
		synchronized (IconRegistry.class) {
			effective = null;
		}
		refreshWorkbench();
	}

	// ---------- lookup ----------

	/**
	 * The replacement icon for a file, or null to leave Eclipse's icon alone. When the file is known and has
	 * error or warning markers, the usual mark is drawn in the bottom left corner, as Eclipse's own icons have it.
	 */
	public static Image imageFor(String fileName, IFile file) {
		String choice = choiceFor(file != null ? file.getName() : fileName);
		Image plain = image(choice);
		int severity = -1;
		try {
			severity = plain == null || file == null || !file.exists() ? -1
					: file.findMaxProblemSeverity(IMarker.PROBLEM, true, IResource.DEPTH_ZERO);
		} catch (CoreException e) {
			// no marker information: plain icon
		}
		if (severity != IMarker.SEVERITY_ERROR && severity != IMarker.SEVERITY_WARNING) {
			return plain;
		}
		synchronized (IconRegistry.class) {
			String key = choice + "|" + severity; //$NON-NLS-1$
			if (!IMAGES.containsKey(key)) {
				ImageDescriptor mark = PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(
						severity == IMarker.SEVERITY_ERROR ? ISharedImages.IMG_DEC_FIELD_ERROR : ISharedImages.IMG_DEC_FIELD_WARNING);
				IMAGES.put(key, new DecorationOverlayIcon(plain, mark, IDecoration.BOTTOM_LEFT).createImage(Display.getDefault()));
			}
			return IMAGES.get(key);
		}
	}

	private static String choiceFor(String fileName) {
		if (fileName == null) {
			return null;
		}
		Map<String, String> map;
		synchronized (IconRegistry.class) {
			if (effective == null) {
				effective = combine(selectedSetId(), overrides());
			}
			map = effective;
		}
		// whole name first (package.json), then every suffix after a dot (d.ts before ts)
		String name = fileName.toLowerCase(Locale.ROOT);
		String choice = map.get(name);
		for (int dot = name.indexOf('.', 1); choice == null && dot >= 0; dot = name.indexOf('.', dot + 1)) {
			choice = map.get(name.substring(dot + 1));
		}
		return choice;
	}

	/** The image for a choice; null for {@link #ECLIPSE} or when the image is missing. */
	public static synchronized Image image(String choice) {
		if (choice == null || ECLIPSE.equals(choice)) {
			return null;
		}
		if (!IMAGES.containsKey(choice)) {
			IMAGES.put(choice, load(choice));
		}
		return IMAGES.get(choice);
	}

	static synchronized boolean isOurs(Object image) {
		return image != null && IMAGES.containsValue(image);
	}

	private static Image load(String choice) {
		try {
			if (choice.startsWith("set:")) { //$NON-NLS-1$
				String[] parts = choice.split(":", 3); //$NON-NLS-1$
				IconSet set = set(parts[1]);
				URL url = set == null ? null : set.bundle().getEntry(set.folder() + "/" + parts[2]); //$NON-NLS-1$
				// createFromURL also uses name@2x.png on high-resolution displays
				return url == null ? null : ImageDescriptor.createFromURL(url).createImage(Display.getDefault());
			}
			if (choice.startsWith("registry:")) { //$NON-NLS-1$
				ImageDescriptor d = PlatformUI.getWorkbench().getEditorRegistry().getImageDescriptor(choice.substring(9));
				return d == null ? null : d.createImage(Display.getDefault());
			}
			if (choice.startsWith("shared:")) { //$NON-NLS-1$
				ImageDescriptor d = PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(choice.substring(7));
				return d == null ? null : d.createImage(Display.getDefault());
			}
			if (choice.startsWith("file:")) { //$NON-NLS-1$
				Path file = importDir().resolve(choice.substring(5));
				if (Files.exists(file)) {
					ImageData source = new ImageData(file.toString());
					ImageDataProvider scaled = zoom -> source.scaledTo(SIZE * zoom / 100, SIZE * zoom / 100);
					return new Image(Display.getDefault(), scaled);
				}
			}
		} catch (RuntimeException e) {
			ILog.of(IconRegistry.class).error("Cannot load file icon " + choice, e); //$NON-NLS-1$
		}
		return null;
	}

	// ---------- Eclipse's own icons ----------

	/**
	 * Icons that are already part of this Eclipse: a few general ones, and the icon of every file type
	 * the installed editors register (one entry per distinct icon). Choice to label, sorted by label.
	 */
	public static Map<String, String> builtinChoices() {
		Map<String, String> out = new LinkedHashMap<>();
		out.put("shared:" + ISharedImages.IMG_OBJ_FILE, Messages.builtin_file); //$NON-NLS-1$
		out.put("shared:" + ISharedImages.IMG_OBJ_ELEMENT, Messages.builtin_element); //$NON-NLS-1$
		out.put("shared:" + ISharedImages.IMG_OBJ_FOLDER, Messages.builtin_folder); //$NON-NLS-1$
		Map<ImageDescriptor, String> seen = new HashMap<>();
		Map<String, String> byLabel = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (IFileEditorMapping mapping : PlatformUI.getWorkbench().getEditorRegistry().getFileEditorMappings()) {
			ImageDescriptor icon = mapping.getImageDescriptor();
			if (icon == null || seen.putIfAbsent(icon, mapping.getLabel()) != null) {
				continue;
			}
			String name = mapping.getName().replace("*", "x"); //$NON-NLS-1$ //$NON-NLS-2$
			String sample = mapping.getExtension().isEmpty() ? name : name + "." + mapping.getExtension(); //$NON-NLS-1$
			byLabel.put(mapping.getLabel(), "registry:" + sample); //$NON-NLS-1$
		}
		byLabel.forEach((label, choice) -> out.put(choice, label));
		return out;
	}

	/** Short text for a choice, for the table on the page. */
	public static String label(String choice) {
		if (choice.startsWith("registry:")) { //$NON-NLS-1$
			return NLS.bind(Messages.builtin_registry, choice.substring(9).replaceFirst("^x[.]", "*.")); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (choice.startsWith("shared:")) { //$NON-NLS-1$
			String known = builtinChoices().get(choice);
			return known != null ? known : choice.substring(7);
		}
		return choice.substring(choice.lastIndexOf(':') + 1);
	}

	// ---------- imported images ----------

	public static Path importDir() {
		URL url = Platform.getConfigurationLocation().getURL();
		return new File(url.getPath()).toPath().resolve(PLUGIN_ID).resolve("icons"); //$NON-NLS-1$
	}

	/** Choices for all images imported so far. */
	public static List<String> importedChoices() {
		List<String> out = new ArrayList<>();
		Path dir = importDir();
		if (Files.isDirectory(dir)) {
			try (Stream<Path> files = Files.list(dir)) {
				files.map(f -> "file:" + f.getFileName()).sorted().forEach(out::add); //$NON-NLS-1$
			} catch (IOException e) {
				ILog.of(IconRegistry.class).error("Cannot list " + dir, e); //$NON-NLS-1$
			}
		}
		return out;
	}

	/** Copies an image file (PNG, GIF, JPG, BMP, ICO) into the install and returns its choice. */
	public static String importImage(Path file) throws IOException {
		try {
			new ImageData(file.toString());
		} catch (RuntimeException e) {
			throw new IOException(e.getMessage(), e);
		}
		Path dir = importDir();
		Files.createDirectories(dir);
		String name = file.getFileName().toString();
		Path target = dir.resolve(name);
		for (int i = 2; Files.exists(target) && Files.mismatch(file, target) != -1; i++) {
			target = dir.resolve(i + "-" + name); //$NON-NLS-1$
		}
		if (!Files.exists(target)) {
			Files.copy(file, target);
		}
		return "file:" + target.getFileName(); //$NON-NLS-1$
	}

	// ---------- refresh ----------

	private static void refreshWorkbench() {
		if (!PlatformUI.isWorkbenchRunning()) {
			return;
		}
		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			PlatformUI.getWorkbench().getDecoratorManager().update(DECORATOR_ID);
			for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
				for (IWorkbenchPage page : window.getPages()) {
					for (IEditorReference ref : page.getEditorReferences()) {
						refreshTab(ref.getPart(false));
					}
				}
			}
		});
	}

	/** The file shown by the editor behind a window-model part, or null. */
	static IFile fileOf(MPart part) {
		for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
			for (IWorkbenchPage page : window.getPages()) {
				for (IEditorReference ref : page.getEditorReferences()) {
					IWorkbenchPart editor = ref.getPart(false);
					if (editor instanceof IEditorPart e && e.getSite().getService(MPart.class) == part) {
						return Adapters.adapt(e.getEditorInput(), IFile.class);
					}
				}
			}
		}
		return null;
	}

	private static void refreshTab(IWorkbenchPart editor) {
		MPart part = editor == null ? null : editor.getSite().getService(MPart.class);
		if (part == null) {
			return;
		}
		Object current = part.getTransientData().get(IPresentationEngine.OVERRIDE_ICON_IMAGE_KEY);
		Image wanted = imageFor(part.getLabel(), editor instanceof IEditorPart e ? Adapters.adapt(e.getEditorInput(), IFile.class) : null);
		if (wanted == null && isOurs(current)) {
			wanted = editor.getTitleImage(); // back to the editor's own icon
		}
		if (wanted != null && wanted != current) {
			part.getTransientData().put(IPresentationEngine.OVERRIDE_ICON_IMAGE_KEY, wanted);
		}
	}
}
