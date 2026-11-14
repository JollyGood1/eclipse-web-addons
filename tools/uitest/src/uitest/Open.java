package uitest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Predicate;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.e4.ui.css.swt.theme.IThemeEngine;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.ide.IDE;

import io.github.jollygood1.syntaxthemes.ThemeApplier;
import io.github.jollygood1.syntaxthemes.ThemeRegistry;

/** Throwaway UI test driver for the colour theme page. Not part of the addon. */
public class Open implements IStartup {
	static final String OUT = System.getProperty("uitest.out");
	Display display;

	@Override
	public void earlyStartup() {
		display = PlatformUI.getWorkbench().getDisplay();
		display.asyncExec(() -> display.timerExec(15000, () -> {
			try {
				run();
			} catch (Throwable t) {
				log("FAILED " + t);
				t.printStackTrace(System.out);
			}
			PlatformUI.getWorkbench().close();
		}));
	}

	void log(String s) {
		System.out.println("UITEST " + s);
	}

	void pump(int ms) {
		long end = System.currentTimeMillis() + ms;
		display.timerExec(ms, () -> { });
		while (System.currentTimeMillis() < end) {
			if (!display.readAndDispatch()) {
				display.timerExec(200, () -> { });
				display.sleep();
			}
		}
	}

	void shot(Shell s, String name) {
		Point size = s.getSize();
		Image img = new Image(display, size.x, size.y);
		GC gc = new GC(img);
		s.print(gc);
		gc.dispose();
		ImageLoader l = new ImageLoader();
		l.data = new ImageData[] { img.getImageData() };
		l.save(OUT + "\\" + name + ".png", SWT.IMAGE_PNG);
		img.dispose();
	}

	Control find(Composite parent, Predicate<Control> test) {
		for (Control c : parent.getChildren()) {
			if (test.test(c)) {
				return c;
			}
			if (c instanceof Composite comp) {
				Control hit = find(comp, test);
				if (hit != null) {
					return hit;
				}
			}
		}
		return null;
	}

	Button button(Shell s, String text) {
		return (Button) find(s, c -> c instanceof Button b && b.getText().replace("&", "").equals(text));
	}

	void click(Shell s, String text) {
		Button b = button(s, text);
		if ((b.getStyle() & SWT.CHECK) != 0) {
			b.setSelection(!b.getSelection());
		}
		b.notifyListeners(SWT.Selection, null);
		pump(1500);
	}

	void select(Shell s, String theme) {
		org.eclipse.swt.widgets.List list = (org.eclipse.swt.widgets.List) find(s, c -> c instanceof org.eclipse.swt.widgets.List);
		log("list items " + String.join(" | ", list.getItems()));
		list.setSelection(new String[] { theme });
		list.notifyListeners(SWT.Selection, null);
		pump(800);
	}

	String state() {
		return "current=" + ThemeApplier.currentThemeId()
				+ " php.keyword=" + InstanceScope.INSTANCE.getNode("org.eclipse.php.ui").get("editorColorKeyword", null)
				+ " editors.bg=" + InstanceScope.INSTANCE.getNode("org.eclipse.ui.editors").get("AbstractTextEditor.Color.Background", null)
				+ " tm.dark=" + InstanceScope.INSTANCE.getNode("org.eclipse.tm4e.ui").get("org.eclipse.tm4e.ui.themes.defaultDarkTheme", null)
				+ " tm.light=" + InstanceScope.INSTANCE.getNode("org.eclipse.tm4e.ui").get("org.eclipse.tm4e.ui.themes.defaultLightTheme", null)
				+ " installwide.php.keyword=" + ConfigurationScope.INSTANCE.getNode("org.eclipse.php.ui").get("editorColorKeyword", null);
	}

	/** The on/off keys of semantic highlights, one or two per editor, and how many such keys are set in all. */
	String semantic() {
		StringBuilder b = new StringBuilder("option=" + ThemeApplier.semanticEnabled());
		String php = "semanticHighlighting.org.eclipse.php.internal.ui.editor.highlighters.";
		String[][] keys = { { "org.eclipse.jdt.ui", "semanticHighlighting.localVariable.enabled" },
				{ "org.eclipse.jdt.ui", "semanticHighlighting.method.enabled" },
				{ "org.eclipse.wst.jsdt.ui", "semanticHighlighting.localVariable.enabled" },
				{ "org.eclipse.php.ui", php + "ParameterVariableHighlighting.enabled" },
				{ "org.eclipse.php.ui", php + "MethodHighlighting.enabled" } };
		for (String[] k : keys) {
			b.append(' ').append(k[0].substring(k[0].lastIndexOf('.', k[0].length() - 4) + 1)).append(':')
					.append(k[1].replace(php, "").replace("semanticHighlighting.", "")).append('=')
					.append(InstanceScope.INSTANCE.getNode(k[0]).get(k[1], null));
		}
		int count = 0;
		try {
			for (String plugin : new String[] { "org.eclipse.jdt.ui", "org.eclipse.wst.jsdt.ui", "org.eclipse.php.ui" }) {
				for (String key : InstanceScope.INSTANCE.getNode(plugin).keys()) {
					if (key.startsWith("semanticHighlighting.") && key.endsWith(".enabled")) {
						count++;
					}
				}
			}
		} catch (Exception e) {
			b.append(' ').append(e);
		}
		return b.append(" enabledKeys=").append(count).toString();
	}

	PreferenceDialog open() {
		PreferenceDialog d = PreferencesUtil.createPreferenceDialogOn(
				PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "io.github.jollygood1.syntaxthemes.page", null, null);
		d.setBlockOnOpen(false);
		d.open();
		pump(2000);
		return d;
	}

	void run() throws Exception {
		log("start " + state());
		if (Boolean.getBoolean("uitest.java")) {
			javaFile();
			return;
		}
		if (Boolean.getBoolean("uitest.editorsOnly")) {
			editors();
			return;
		}
		pageSteps();
		editors();
	}

	void pageSteps() throws Exception {
		PreferenceDialog d = open();
		Shell s = d.getShell();
		select(s, "Tomorrow Night");
		click(s, "Apply");
		log("after Apply TN: " + state());
		shot(s, "page-applied");

		String semanticBox = "Switch on semantic highlighting (classes, methods, fields, variables) in Java, JavaScript and PHP";
		log("semantic before: " + semantic());
		click(s, semanticBox);
		click(s, "Apply");
		log("semantic ticked + Apply: " + semantic());
		shot(s, "page-semantic");
		click(s, semanticBox);
		click(s, "Apply");
		log("semantic unticked + Apply: " + semantic());

		click(s, "Restore Defaults");
		log("after Restore Defaults: " + state());
		d.close();

		ThemeRegistry.importTheme(Path.of(System.getProperty("uitest.import")));
		d = open();
		s = d.getShell();
		select(s, "Import Test");
		shot(s, "page-imported-light");
		click(s, "Also use as the default for workspaces that have no colours of their own");
		click(s, "Apply");
		log("after Apply Import Test + install-wide: " + state());
		log("tm4e themes pref=" + InstanceScope.INSTANCE.getNode("org.eclipse.tm4e.ui").get("org.eclipse.tm4e.ui.themes", null));

		click(s, "Also use as the default for workspaces that have no colours of their own");
		select(s, "Tomorrow Night");
		button(s, "Apply and Close").notifyListeners(SWT.Selection, null);
		pump(2000);
		log("after switching back to TN with Apply and Close: " + state());
		log("twig leftovers=" + InstanceScope.INSTANCE.getNode("org.eclipse.tm4e.ui").get("org.eclipse.tm4e.ui.themes", null));

		IThemeEngine engine = (IThemeEngine) display.getData("org.eclipse.e4.ui.css.swt.theme");
		log("window theme now " + engine.getActiveTheme().getId());
		engine.setTheme("org.eclipse.e4.ui.css.theme.e4_default", true);
		pump(4000);
		log("after switch to Light: " + state());
		engine.setTheme("org.eclipse.e4.ui.css.theme.e4_dark", true);
		pump(4000);
		log("after switch back to Dark: " + state());

	}

	/** A Java file in a Java project, per theme once without and once with the semantic highlighting option. */
	void javaFile() throws Exception {
		PlatformUI.getWorkbench().getIntroManager().closeIntro(PlatformUI.getWorkbench().getIntroManager().getIntro());
		pump(1500);
		IThemeEngine engine = (IThemeEngine) display.getData("org.eclipse.e4.ui.css.swt.theme");
		log("window theme " + engine.getActiveTheme().getId());
		log("nothing applied: " + semantic());
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("javatest");
		project.create(null);
		project.open(null);
		org.eclipse.core.resources.IProjectDescription desc = project.getDescription();
		desc.setNatureIds(new String[] { org.eclipse.jdt.core.JavaCore.NATURE_ID });
		project.setDescription(desc, null);
		org.eclipse.core.resources.IFolder srcFolder = project.getFolder("src");
		srcFolder.create(true, true, null);
		org.eclipse.jdt.core.JavaCore.create(project).setRawClasspath(new org.eclipse.jdt.core.IClasspathEntry[] {
				org.eclipse.jdt.core.JavaCore.newSourceEntry(srcFolder.getFullPath()),
				org.eclipse.jdt.launching.JavaRuntime.getDefaultJREContainerEntry() }, null);
		String java = "import java.util.List;\n\n/** A doc comment. */\npublic class Example implements Runnable {\n"
				+ "    static final int LIMIT = 42;\n    static int shared;\n    private String text = \"hello\";\n\n"
				+ "    @Override\n    public void run() {\n        int n = count(List.of(text)) + LIMIT;\n"
				+ "        shared = Math.max(n, 1); // a comment\n        System.out.println(toString() + n);\n    }\n\n"
				+ "    <T> int count(List<T> items) {\n        int size = items.size();\n        return size;\n    }\n}\n";
		IFile file = srcFolder.getFile("Example.java");
		file.create(new ByteArrayInputStream(java.getBytes(StandardCharsets.UTF_8)), true, null);
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		Shell main = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
		main.setMaximized(false);
		main.setSize(760, 620);
		for (String name : System.getProperty("uitest.javaThemes", "Tomorrow Night,Solarized Light").split(",")) {
			for (boolean on : new boolean[] { false, true }) {
				ThemeApplier.apply(ThemeRegistry.themes().get(name), false, false, on);
				log(name + (on ? " with option: " : " without option: ") + semantic());
				log("opened in " + IDE.openEditor(page, file).getClass().getName());
				pump(15000);
				shot(main, "java-" + name.toLowerCase().replace(' ', '-') + (on ? "-semantic" : "-plain"));
				page.closeAllEditors(false);
				pump(1000);
			}
		}
		log("done");
	}

	void editors() throws Exception {
		PlatformUI.getWorkbench().getIntroManager().closeIntro(PlatformUI.getWorkbench().getIntroManager().getIntro());
		pump(1500);
		// every bundled theme must apply without an error; then the theme to look at in the editors
		int applied = 0;
		for (io.github.jollygood1.syntaxthemes.ColorTheme t : ThemeRegistry.themes().values()) {
			try {
				ThemeApplier.apply(t, true, false, false);
				applied++;
			} catch (Throwable e) {
				log("apply FAILED for " + t.getName() + ": " + e);
			}
		}
		log("applied each of " + applied + " themes: " + String.join(", ", ThemeRegistry.themes().keySet()));
		String last = System.getProperty("uitest.finalTheme", "Tomorrow Night");
		ThemeApplier.apply(ThemeRegistry.themes().get(last), false, false, false);
		log("final theme " + last + ": " + state());
		log("tm4e themes pref=" + InstanceScope.INSTANCE.getNode("org.eclipse.tm4e.ui").get("org.eclipse.tm4e.ui.themes", null));
		PlatformUI.getWorkbench().getIntroManager().closeIntro(PlatformUI.getWorkbench().getIntroManager().getIntro());
		pump(1500);
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("uitest");
		if (!project.exists()) {
			project.create(null);
		}
		project.open(null);
		String php = "<?php\n// A comment\nclass Example extends Base\n{\n    const LIMIT = 42;\n    private $text = \"hello\";\n\n"
				+ "    public function count(array $items): int\n    {\n        $n = strlen($this->text) + self::LIMIT;\n        return $n;\n    }\n}\n";
		String vue = "<template>\n  <div class=\"box\" :title=\"label\">{{ count }}</div>\n</template>\n\n<script>\n// A comment\n"
				+ "export default {\n  data() {\n    return { count: 42, label: 'hello' };\n  },\n};\n</script>\n\n<style>\n.box { color: #fff; margin: 4px; }\n</style>\n";
		String json = "{\n  \"name\": \"demo\",\n  \"count\": 42,\n  \"ok\": true,\n  \"none\": null\n}\n";
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		Shell main = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
		main.setMaximized(false);
		main.setSize(760, 520);
		for (String[] f : new String[][] { { "a.php", php }, { "b.vue", vue }, { "c.json", json } }) {
			IFile file = project.getFile(f[0]);
			if (!file.exists()) {
				file.create(new ByteArrayInputStream(f[1].getBytes(StandardCharsets.UTF_8)), true, null);
			}
			try {
				log("opened " + f[0] + " in " + IDE.openEditor(page, file).getClass().getName());
				pump(f[0].endsWith("vue") ? 25000 : 12000);
				shot(main, "editor-" + f[0].replace('.', '-'));
			} catch (Throwable t) {
				log("open " + f[0] + " FAILED " + t);
			}
		}
		page.closeAllEditors(false);
		// the same PHP file once more, with the theme's semantic highlights switched on
		ThemeApplier.apply(ThemeRegistry.themes().get(last), false, false, true);
		log("final theme with semantic highlighting: " + semantic());
		IDE.openEditor(page, project.getFile("a.php"));
		pump(12000);
		shot(main, "editor-a-php-semantic");
		page.closeAllEditors(false);
		log("done");
	}
}
