package importtest;

import java.util.function.Predicate;

import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;

/**
 * Throwaway driver: presses the two Import buttons. The native file dialogs they open are answered from
 * outside (importtest.ps1, Windows UI Automation), because SWT code cannot reach into them.
 */
public class ImportTest implements IStartup {
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
		System.out.println("IMPORTTEST " + s);
		System.out.flush();
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

	PreferenceDialog open(String pageId) {
		PreferenceDialog d = PreferencesUtil.createPreferenceDialogOn(
				PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), pageId, null, null);
		d.setBlockOnOpen(false);
		d.open();
		pump(2000);
		return d;
	}

	void run() {
		// 1. colour themes: Import... opens a native file dialog and returns when it is answered
		PreferenceDialog d = open("io.github.jollygood1.syntaxthemes.page");
		Shell s = d.getShell();
		org.eclipse.swt.widgets.List list = (org.eclipse.swt.widgets.List) find(s, c -> c instanceof org.eclipse.swt.widgets.List);
		log("themes before: " + String.join(" | ", list.getItems()));
		log("pressing theme Import...");
		button(s, "Import...").notifyListeners(SWT.Selection, null);
		pump(1500);
		list = (org.eclipse.swt.widgets.List) find(s, c -> c instanceof org.eclipse.swt.widgets.List);
		log("themes after: " + String.join(" | ", list.getItems()) + " ; selected: " + String.join(",", list.getSelection()));
		log("page error message: " + ((org.eclipse.jface.preference.PreferencePage) d.getSelectedPage()).getErrorMessage());
		d.close();

		// 2. file icons: row "md" > Change Icon... > (picker) Import Image File... > OK
		d = open("io.github.jollygood1.fileicons.page");
		Shell page = d.getShell();
		Table table = (Table) find(page, c -> c instanceof Table);
		for (int i = 0; i < table.getItemCount(); i++) {
			if (table.getItem(i).getText(0).equals("md")) {
				table.setSelection(i);
			}
		}
		table.notifyListeners(SWT.Selection, null);
		display.timerExec(2500, () -> {
			Shell picker = display.getActiveShell();
			Table icons = (Table) find(picker, c -> c instanceof Table);
			log("picker '" + picker.getText() + "' icons before: " + icons.getItemCount());
			log("pressing Import Image File...");
			button(picker, "Import Image File...").notifyListeners(SWT.Selection, null);
			pump(1500);
			icons = (Table) find(picker, c -> c instanceof Table);
			String sel = icons.getSelectionCount() == 0 ? "(none)" : icons.getSelection()[0].getText();
			log("picker icons after: " + icons.getItemCount() + " ; selected: " + sel + " ; has image: "
					+ (icons.getSelectionCount() > 0 && icons.getSelection()[0].getImage() != null));
			button(picker, "OK").notifyListeners(SWT.Selection, null);
		});
		button(page, "Change Icon...").notifyListeners(SWT.Selection, null);
		pump(1500);
		table = (Table) find(page, c -> c instanceof Table);
		for (int i = 0; i < table.getItemCount(); i++) {
			if (table.getItem(i).getText(0).equals("md")) {
				log("row md now: " + table.getItem(i).getText(1) + " ; has image: " + (table.getItem(i).getImage(1) != null));
			}
		}
		button(page, "Apply").notifyListeners(SWT.Selection, null);
		pump(2000);
		d.close();
		log("done");
	}
}
