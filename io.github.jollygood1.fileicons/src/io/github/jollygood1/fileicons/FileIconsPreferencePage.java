package io.github.jollygood1.fileicons;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.window.Window;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.osgi.service.prefs.BackingStoreException;

/** General &gt; Appearance &gt; File Icons: pick an icon set and change the icon of single file types. */
public class FileIconsPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	/** Working copy; stored on OK/Apply. */
	private String setId;
	private final Map<String, String> overrides = new TreeMap<>();

	private Combo setCombo;
	private Table table;
	private Button changeButton;
	private Button eclipseButton;
	private Button resetButton;

	@Override
	public void init(IWorkbench workbench) {
		setDescription(Messages.page_description);
	}

	@Override
	protected Control createContents(Composite parent) {
		setId = IconRegistry.selectedSetId();
		overrides.putAll(IconRegistry.overrides());

		Composite page = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(2, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		page.setLayout(layout);

		Composite setRow = new Composite(page, SWT.NONE);
		setRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		GridLayout rowLayout = new GridLayout(2, false);
		rowLayout.marginWidth = 0;
		setRow.setLayout(rowLayout);
		new Label(setRow, SWT.NONE).setText(Messages.page_set);
		setCombo = new Combo(setRow, SWT.READ_ONLY | SWT.DROP_DOWN);
		setCombo.add(Messages.page_noSet);
		List<IconRegistry.IconSet> sets = IconRegistry.sets();
		sets.forEach(s -> setCombo.add(s.name()));
		setCombo.addListener(SWT.Selection, e -> {
			int i = setCombo.getSelectionIndex();
			setId = i <= 0 ? IconRegistry.NO_SET : sets.get(i - 1).id();
			fill(null);
		});

		table = new Table(page, SWT.BORDER | SWT.SINGLE | SWT.FULL_SELECTION | SWT.V_SCROLL);
		table.setHeaderVisible(true);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
		tableData.heightHint = convertHeightInCharsToPixels(14);
		table.setLayoutData(tableData);
		TableColumn typeColumn = new TableColumn(table, SWT.LEFT);
		typeColumn.setText(Messages.page_colType);
		typeColumn.setWidth(convertWidthInCharsToPixels(24));
		TableColumn iconColumn = new TableColumn(table, SWT.LEFT);
		iconColumn.setText(Messages.page_colIcon);
		iconColumn.setWidth(convertWidthInCharsToPixels(44));
		table.addListener(SWT.Selection, e -> updateButtons());
		table.addListener(SWT.DefaultSelection, e -> changeIcon(selectedType()));

		Composite buttons = new Composite(page, SWT.NONE);
		buttons.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
		GridLayout buttonLayout = new GridLayout(1, false);
		buttonLayout.marginWidth = 0;
		buttonLayout.marginHeight = 0;
		buttons.setLayout(buttonLayout);
		button(buttons, Messages.page_add).addListener(SWT.Selection, e -> addType());
		changeButton = button(buttons, Messages.page_change);
		changeButton.addListener(SWT.Selection, e -> changeIcon(selectedType()));
		eclipseButton = button(buttons, Messages.page_useEclipse);
		eclipseButton.addListener(SWT.Selection, e -> {
			overrides.put(selectedType(), IconRegistry.ECLIPSE);
			fill(selectedType());
		});
		resetButton = button(buttons, Messages.page_reset);
		resetButton.addListener(SWT.Selection, e -> {
			String type = selectedType();
			overrides.remove(type);
			fill(type);
		});

		fill(null);
		applyDialogFont(page);
		return page;
	}

	private Button button(Composite parent, String text) {
		Button b = new Button(parent, SWT.PUSH);
		b.setText(text);
		b.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		return b;
	}

	private void fill(String select) {
		List<IconRegistry.IconSet> sets = IconRegistry.sets();
		int index = 0;
		for (int i = 0; i < sets.size(); i++) {
			if (sets.get(i).id().equals(setId)) {
				index = i + 1;
			}
		}
		setCombo.select(index);
		table.removeAll();
		IconRegistry.combine(setId, overrides).forEach((type, choice) -> {
			TableItem item = new TableItem(table, SWT.NONE);
			item.setText(0, type);
			item.setImage(1, IconRegistry.image(choice));
			item.setText(1, describe(type, choice));
			if (type.equals(select)) {
				table.setSelection(item);
				table.showItem(item);
			}
		});
		updateButtons();
	}

	private String describe(String type, String choice) {
		if (IconRegistry.ECLIPSE.equals(choice)) {
			return Messages.page_eclipse;
		}
		if (!overrides.containsKey(type)) {
			return Messages.page_fromSet;
		}
		return NLS.bind(Messages.page_yourChoice, IconRegistry.label(choice));
	}

	private String selectedType() {
		TableItem[] selection = table.getSelection();
		return selection.length == 0 ? null : selection[0].getText(0);
	}

	private void updateButtons() {
		String type = selectedType();
		changeButton.setEnabled(type != null);
		eclipseButton.setEnabled(type != null && !IconRegistry.ECLIPSE.equals(overrides.get(type)));
		resetButton.setEnabled(type != null && overrides.containsKey(type));
	}

	private void addType() {
		InputDialog dialog = new InputDialog(getShell(), Messages.page_addTitle, Messages.page_addPrompt, "", //$NON-NLS-1$
				text -> text.isBlank() || text.matches(".*[\\s/\\\\:*?\"<>|=].*") ? Messages.page_addInvalid : null); //$NON-NLS-1$
		if (dialog.open() == Window.OK) {
			String type = dialog.getValue().trim().toLowerCase(Locale.ROOT);
			if (type.startsWith("*.")) { //$NON-NLS-1$
				type = type.substring(2);
			}
			changeIcon(type);
		}
	}

	private void changeIcon(String type) {
		if (type == null) {
			return;
		}
		IconPickerDialog dialog = new IconPickerDialog(getShell(), IconRegistry.combine(setId, overrides).get(type));
		if (dialog.open() == Window.OK) {
			overrides.put(type, dialog.getChoice());
			fill(type);
		}
	}

	@Override
	public boolean performOk() {
		if (table == null) {
			return true;
		}
		try {
			IconRegistry.save(setId, overrides);
			return true;
		} catch (BackingStoreException e) {
			setErrorMessage(NLS.bind(Messages.page_failed, e.getMessage()));
			return false;
		}
	}

	/** Restore Defaults: the first icon set, no own choices. */
	@Override
	protected void performDefaults() {
		List<IconRegistry.IconSet> sets = IconRegistry.sets();
		setId = sets.isEmpty() ? IconRegistry.NO_SET : sets.get(0).id();
		overrides.clear();
		fill(null);
		super.performDefaults();
	}
}
