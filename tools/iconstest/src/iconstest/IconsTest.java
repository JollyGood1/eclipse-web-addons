package iconstest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.navigator.CommonNavigator;

import io.github.jollygood1.fileicons.IconRegistry;

/** Throwaway driver for the File Icons plug-in. Prints lines starting with ICONTEST and saves PNGs. */
public class IconsTest implements IStartup {
	static final boolean WITH = !Boolean.getBoolean("uitest.baseline");
	Display display;
	Shell main;
	Tree explorer;

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
		System.out.println("ICONTEST " + s);
	}

	void pump(int ms) {
		long end = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < end) {
			if (!display.readAndDispatch()) {
				display.timerExec(200, () -> { });
				display.sleep();
			}
		}
	}

	void save(Image img, String name) {
		ImageLoader l = new ImageLoader();
		l.data = new ImageData[] { img.getImageData() };
		l.save(System.getProperty("uitest.out") + "/" + name + ".png", SWT.IMAGE_PNG);
		img.dispose();
	}

	void shot(Control c, String name) {
		Point size = c.getSize();
		Image img = new Image(display, size.x, size.y);
		GC gc = new GC(img);
		c.print(gc);
		gc.dispose();
		save(img, name);
	}

	/** One strip: the explorer's file icons, then the editor tab icons, each enlarged 6 times. */
	void strip(String name) {
		List<Object[]> cells = new ArrayList<>();
		for (TreeItem project : explorer.getItems()) {
			for (TreeItem item : project.getItems()) {
				cells.add(new Object[] { item.getImage(), "view " + item.getText() });
			}
		}
		tabs(main, cells);
		Image img = new Image(display, Math.max(1, cells.size()) * 110, 125);
		GC gc = new GC(img);
		gc.setBackground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
		gc.fillRectangle(img.getBounds());
		gc.setForeground(display.getSystemColor(SWT.COLOR_WHITE));
		int x = 0;
		for (Object[] cell : cells) {
			Image i = (Image) cell[0];
			if (i != null) {
				Rectangle b = i.getBounds();
				gc.drawImage(i, 0, 0, b.width, b.height, x + 7, 4, 96, 96);
			}
			gc.drawText((String) cell[1], x + 3, 105, true);
			x += 110;
		}
		gc.dispose();
		save(img, name);
	}

	void tabs(Composite parent, List<Object[]> out) {
		for (Control c : parent.getChildren()) {
			if (c instanceof CTabFolder f) {
				for (CTabItem i : f.getItems()) {
					if (i.getText().contains(".")) {
						out.add(new Object[] { i.getImage(), "tab " + i.getText() });
					}
				}
			}
			if (c instanceof Composite comp) {
				tabs(comp, out);
			}
		}
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

	void click(Shell s, String text) {
		Button b = (Button) find(s, c -> c instanceof Button x && x.getText().replace("&", "").equals(text));
		log("click " + text + " enabled=" + b.isEnabled());
		b.notifyListeners(SWT.Selection, null);
		pump(1200);
	}

	/** High resolution: every tab icon exactly as the 200% device pixels. */
	void hidpi() {
		List<Object[]> cells = new ArrayList<>();
		tabs(main, cells);
		for (Object[] cell : cells) {
			ImageLoader l = new ImageLoader();
			ImageData data = ((Image) cell[0]).getImageData(200);
			l.data = new ImageData[] { data };
			l.save(System.getProperty("uitest.out") + "/hd-" + ((String) cell[1]).replace(' ', '-') + ".png", SWT.IMAGE_PNG);
			log("hidpi " + cell[1] + " " + data.width + "x" + data.height);
		}
	}

	void cellsStrip(List<Object[]> cells, String name) {
		Image img = new Image(display, Math.max(1, cells.size()) * 110, 125);
		GC gc = new GC(img);
		gc.setBackground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
		gc.fillRectangle(img.getBounds());
		gc.setForeground(display.getSystemColor(SWT.COLOR_WHITE));
		int x = 0;
		for (Object[] cell : cells) {
			Image i = (Image) cell[0];
			if (i != null) {
				Rectangle b = i.getBounds();
				gc.drawImage(i, 0, 0, b.width, b.height, x + 7, 4, 96, 96);
			}
			gc.drawText((String) cell[1], x + 3, 105, true);
			x += 110;
		}
		gc.dispose();
		save(img, name);
	}

	/** PHP Explorer and the Open Resource dialog. */
	void extraViews(IWorkbenchPage page) {
		try {
			IViewPart php = page.showView("org.eclipse.php.ui.explorer");
			Object viewer = php.getClass().getMethod("getTreeViewer").invoke(php);
			((org.eclipse.jface.viewers.TreeViewer) viewer).expandToLevel(2);
			pump(4000);
			List<Object[]> cells = new ArrayList<>();
			for (TreeItem project : ((org.eclipse.jface.viewers.TreeViewer) viewer).getTree().getItems()) {
				for (TreeItem item : project.getItems()) {
					cells.add(new Object[] { item.getImage(), "phpx " + item.getText() });
				}
			}
			cellsStrip(cells, "9-php-explorer");
			log("php explorer items " + cells.size());
		} catch (Throwable t) {
			log("php explorer FAILED " + t);
		}
		try {
			org.eclipse.ui.dialogs.FilteredResourcesSelectionDialog dialog = new org.eclipse.ui.dialogs.FilteredResourcesSelectionDialog(main, false,
					ResourcesPlugin.getWorkspace().getRoot(), IResource.FILE);
			dialog.setInitialPattern("*.*");
			dialog.setBlockOnOpen(false);
			dialog.open();
			pump(6000);
			Table table = (Table) find(dialog.getShell(), c -> c instanceof Table);
			List<Object[]> cells = new ArrayList<>();
			for (int i = 0; i < Math.min(table.getItemCount(), 9); i++) {
				cells.add(new Object[] { table.getItem(i).getImage(), "open " + table.getItem(i).getText() });
			}
			cellsStrip(cells, "9-open-resource");
			log("open resource items " + table.getItemCount());
			dialog.close();
		} catch (Throwable t) {
			log("open resource FAILED " + t);
		}
		if (Boolean.getBoolean("uitest.hidpi")) {
			hidpi();
		}
	}

	void run() throws Exception {
		PlatformUI.getWorkbench().getIntroManager().closeIntro(PlatformUI.getWorkbench().getIntroManager().getIntro());
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("icons");
		if (!project.exists()) {
			if (!Boolean.getBoolean("uitest.nogit")) {
				java.io.File dir = ResourcesPlugin.getWorkspace().getRoot().getLocation().append("icons").toFile();
				dir.mkdirs();
				log("git init exit " + new ProcessBuilder("git", "init").directory(dir).inheritIO().start().waitFor());
			}
			project.create(null);
		}
		project.open(null);
		IProjectDescription desc = project.getDescription();
		desc.setNatureIds(new String[] { "org.eclipse.php.core.PHPNature" });
		project.setDescription(desc, null);
		pump(5000);
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		main = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
		main.setMaximized(false);
		main.setSize(900, 560);
		String[][] files = { { "a.php", "<?php\necho 'x';\n" }, { "b.vue", "<template><div/></template>\n" },
				{ "c.json", "{ \"a\": 1 }\n" }, { "d.txt", "plain\n" }, { "e.php", "<?php\nfunction ( { syntax error here\n" },
				{ "package.json", "{ \"name\": \"x\" }\n" }, { "f.md", "# title" }, { "x.d.ts", "export {};\n" } };
		for (String[] f : files) {
			IFile file = project.getFile(f[0]);
			if (!file.exists()) {
				// another job (Git connect, build) may hold the workspace for a moment: wait and try again
				for (int attempt = 0;; attempt++) {
					try {
						file.create(new ByteArrayInputStream(f[1].getBytes(StandardCharsets.UTF_8)), true, null);
						break;
					} catch (org.eclipse.core.runtime.OperationCanceledException e) {
						if (attempt == 15) {
							throw e;
						}
						pump(2000);
					}
				}
			}
		}
		project.build(IncrementalProjectBuilder.FULL_BUILD, null);
		IViewPart view = page.showView("org.eclipse.ui.navigator.ProjectExplorer");
		CommonNavigator nav = (CommonNavigator) view;
		nav.getCommonViewer().expandToLevel(2);
		explorer = nav.getCommonViewer().getTree();
		for (String name : new String[] { "a.php", "b.vue", "c.json", "d.txt", "e.php" }) {
			log("opened " + name + " in " + IDE.openEditor(page, project.getFile(name)).getClass().getSimpleName());
			pump(4000);
		}
		pump(12000);
		log("error markers on e.php: " + project.getFile("e.php").findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO).length);
		strip("1-start");
		shot(main, "1-window");
		extraViews(page);
		if (!WITH) {
			log("done (baseline)");
			return;
		}

		// the preference page
		PreferenceDialog d = PreferencesUtil.createPreferenceDialogOn(main, "io.github.jollygood1.fileicons.page", null, null);
		d.setBlockOnOpen(false);
		d.open();
		pump(2000);
		Shell s = d.getShell();
		log("page " + d.getSelectedPage().getClass().getSimpleName());
		Table table = (Table) find(s, c -> c instanceof Table);
		log("rows " + table.getItemCount());
		shot(s, "2-page");
		// json -> Eclipse's own icon
		for (int i = 0; i < table.getItemCount(); i++) {
			if (table.getItem(i).getText(0).equals("json")) {
				table.setSelection(i);
			}
		}
		table.notifyListeners(SWT.Selection, null);
		click(s, "Use Eclipse's Icon");
		// vue -> pick another icon through the picker dialog (modal: driven from a timer)
		table = (Table) find(s, c -> c instanceof Table);
		for (int i = 0; i < table.getItemCount(); i++) {
			if (table.getItem(i).getText(0).equals("vue")) {
				table.setSelection(i);
			}
		}
		table.notifyListeners(SWT.Selection, null);
		display.timerExec(2500, () -> {
			Shell picker = display.getActiveShell();
			Table icons = (Table) find(picker, c -> c instanceof Table);
			log("picker shell '" + picker.getText() + "' icons " + icons.getItemCount());
			for (int i = 0; i < icons.getItemCount(); i++) {
				if (icons.getItem(i).getText().startsWith("docker ")) {
					icons.setSelection(i);
					icons.showSelection();
				}
			}
			shot(picker, "3-picker");
			((Button) find(picker, c -> c instanceof Button b && b.getText().equals("OK"))).notifyListeners(SWT.Selection, null);
		});
		click(s, "Change Icon...");
		shot(s, "4-page-changed");
		click(s, "Apply");
		pump(3000);
		log("stored set=" + IconRegistry.selectedSetId() + " overrides=" + IconRegistry.overrides());
		strip("5-after-apply");
		d.close();

		// imported image for txt, through the same code the Import button uses
		Map<String, String> o = new TreeMap<>(IconRegistry.overrides());
		o.put("txt", IconRegistry.importImage(Path.of(System.getProperty("uitest.import"))));
		IconRegistry.save(IconRegistry.selectedSetId(), o);
		pump(3000);
		log("after import overrides=" + IconRegistry.overrides());
		strip("6-after-import");

		// no icon set and no choices: everything back to Eclipse's icons
		IconRegistry.save(IconRegistry.NO_SET, new TreeMap<>());
		pump(3000);
		strip("7-none");
		// back to defaults for the next run
		IconRegistry.save(IconRegistry.sets().get(0).id(), new TreeMap<>());
		pump(2000);
		strip("8-defaults");
		page.closeAllEditors(false);
		log("done");
	}
}
