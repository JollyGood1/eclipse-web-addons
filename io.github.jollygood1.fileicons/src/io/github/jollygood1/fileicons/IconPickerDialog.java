package io.github.jollygood1.fileicons;

import java.io.IOException;
import java.nio.file.Path;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;

/** Lists every icon of every icon set plus the imported images; returns the picked one as a choice. */
class IconPickerDialog extends Dialog {

	private static final String DASH = "  -  "; //$NON-NLS-1$
	private Table table;
	private String choice;

	IconPickerDialog(Shell parent, String current) {
		super(parent);
		this.choice = current;
	}

	String getChoice() {
		return choice;
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText(Messages.picker_title);
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite area = (Composite) super.createDialogArea(parent);
		table = new Table(area, SWT.BORDER | SWT.SINGLE | SWT.FULL_SELECTION | SWT.V_SCROLL);
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		data.widthHint = convertWidthInCharsToPixels(55);
		data.heightHint = convertHeightInCharsToPixels(16);
		table.setLayoutData(data);
		table.addListener(SWT.DefaultSelection, e -> okPressed());
		Button importButton = new Button(area, SWT.PUSH);
		importButton.setText(Messages.picker_import);
		importButton.addListener(SWT.Selection, e -> importImage());
		fill();
		return area;
	}

	private void fill() {
		table.removeAll();
		for (IconRegistry.IconSet set : IconRegistry.sets()) {
			for (String image : set.images()) {
				add("set:" + set.id() + ":" + image, image.replace(".png", "") + "  —  " + set.name()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			}
		}
		IconRegistry.builtinChoices().forEach((builtin, label) -> add(builtin, label + DASH + Messages.picker_eclipse));
		for (String imported : IconRegistry.importedChoices()) {
			add(imported, imported.substring(5) + "  —  " + Messages.picker_imported); //$NON-NLS-1$
		}
	}

	private void add(String itemChoice, String label) {
		TableItem item = new TableItem(table, SWT.NONE);
		item.setText(label);
		item.setImage(IconRegistry.image(itemChoice));
		item.setData(itemChoice);
		if (itemChoice.equals(choice)) {
			table.setSelection(item);
			table.showItem(item);
		}
	}

	private void importImage() {
		FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
		dialog.setText(Messages.picker_importTitle);
		dialog.setFilterExtensions(new String[] { "*.png;*.gif;*.jpg;*.jpeg;*.bmp;*.ico" }); //$NON-NLS-1$
		String file = dialog.open();
		if (file == null) {
			return;
		}
		try {
			choice = IconRegistry.importImage(Path.of(file));
			fill();
		} catch (IOException | RuntimeException e) {
			MessageDialog.openError(getShell(), Messages.picker_title, NLS.bind(Messages.picker_importFailed, e.getMessage()));
		}
	}

	@Override
	protected void okPressed() {
		TableItem[] selection = table.getSelection();
		if (selection.length == 0) {
			return;
		}
		choice = (String) selection[0].getData();
		super.okPressed();
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
	}
}
