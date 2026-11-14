package io.github.jollygood1.syntaxthemes;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.osgi.service.prefs.BackingStoreException;

/** General &gt; Appearance &gt; Syntax Colour Theme. */
public class ColorThemePreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	private static final String SAMPLE = """
			// A comment
			class Example {
			    String text = "hello";
			    int count = 42;
			}
			"""; //$NON-NLS-1$
	/** Sample fragment, palette key. Later entries win where fragments overlap. */
	private static final String[][] SAMPLE_COLORS = { { "// A comment", "singleLineComment" }, { "class", "keyword" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			{ "int", "keyword" }, { "Example", "class" }, { "String", "class" }, { "\"hello\"", "string" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
			{ "42", "number" }, { "text", "field" }, { "count", "field" } }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$

	private Map<String, ColorTheme> themes;
	private org.eclipse.swt.widgets.List list;
	private StyledText preview;
	private Label current;
	private Label author;
	private Label status;
	private Button installWide;
	private Button semantic;

	@Override
	public void init(IWorkbench workbench) {
		setDescription(Messages.page_description);
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite page = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(2, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		page.setLayout(layout);

		Label themesLabel = new Label(page, SWT.NONE);
		themesLabel.setText(Messages.page_themes);
		Label previewLabel = new Label(page, SWT.NONE);
		previewLabel.setText(Messages.page_preview);

		list = new org.eclipse.swt.widgets.List(page, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
		GridData listData = new GridData(SWT.FILL, SWT.FILL, false, true);
		listData.widthHint = convertWidthInCharsToPixels(30);
		listData.heightHint = convertHeightInCharsToPixels(10);
		list.setLayoutData(listData);
		list.addListener(SWT.Selection, e -> updatePreview());

		preview = new StyledText(page, SWT.BORDER | SWT.READ_ONLY | SWT.MULTI);
		preview.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		// keep the window theme's stylesheet from repainting the preview in the dialog's colours
		preview.setData("org.eclipse.e4.ui.css.disabled", Boolean.TRUE); //$NON-NLS-1$
		preview.setText(SAMPLE);
		preview.setMargins(8, 8, 8, 8);

		Button importButton = new Button(page, SWT.PUSH);
		importButton.setText(Messages.page_import);
		importButton.addListener(SWT.Selection, e -> importTheme());
		importButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1));
		author = new Label(page, SWT.WRAP);
		GridData authorData = new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1);
		authorData.widthHint = convertWidthInCharsToPixels(60);
		author.setLayoutData(authorData);
		current = new Label(page, SWT.NONE);
		current.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		status = new Label(page, SWT.WRAP);
		GridData statusData = new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1);
		statusData.widthHint = convertWidthInCharsToPixels(60);
		status.setLayoutData(statusData);

		semantic = new Button(page, SWT.CHECK);
		semantic.setText(Messages.page_semantic);
		semantic.setToolTipText(Messages.page_semanticTip);
		semantic.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		semantic.setSelection(ThemeApplier.semanticEnabled());

		installWide = new Button(page, SWT.CHECK);
		installWide.setText(Messages.page_installWide);
		installWide.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		fill(ThemeApplier.currentThemeId());
		applyDialogFont(page);
		return page;
	}

	private void fill(String select) {
		themes = ThemeRegistry.themes();
		list.setItems(themes.keySet().toArray(String[]::new));
		if (select != null && themes.containsKey(select)) {
			list.setSelection(new String[] { select });
		}
		String applied = ThemeApplier.currentThemeId();
		current.setText(NLS.bind(Messages.page_current, applied == null ? Messages.page_none : applied));
		updatePreview();
	}

	private void showStatus(String text) {
		status.setText(text);
		status.getParent().layout(true, true);
	}

	private ColorTheme selected() {
		String[] sel = list.getSelection();
		return sel.length == 0 ? null : themes.get(sel[0]);
	}

	private void updatePreview() {
		ColorTheme theme = selected();
		author.setText(theme == null || theme.getAuthor().isBlank() ? "" //$NON-NLS-1$
				: NLS.bind(Messages.page_author, theme.getAuthor(), theme.getWebsite()).trim());
		author.getParent().layout(true, true);
		if (theme == null) {
			preview.setStyleRanges(new StyleRange[0]);
			preview.setBackground(null);
			preview.setForeground(null);
			return;
		}
		preview.setBackground(color(theme, "background")); //$NON-NLS-1$
		preview.setForeground(color(theme, "foreground")); //$NON-NLS-1$
		ArrayList<StyleRange> ranges = new ArrayList<>();
		for (String[] pair : SAMPLE_COLORS) {
			int at = SAMPLE.indexOf(pair[0]);
			Color c = color(theme, pair[1]);
			if (at >= 0 && c != null) {
				StyleRange range = new StyleRange(at, pair[0].length(), c, null);
				range.fontStyle = (Boolean.TRUE.equals(theme.style(pair[1], "bold")) ? SWT.BOLD : SWT.NORMAL) //$NON-NLS-1$
						| (Boolean.TRUE.equals(theme.style(pair[1], "italic")) ? SWT.ITALIC : SWT.NORMAL); //$NON-NLS-1$
				range.underline = Boolean.TRUE.equals(theme.style(pair[1], "underline")); //$NON-NLS-1$
				range.strikeout = Boolean.TRUE.equals(theme.style(pair[1], "strikethrough")); //$NON-NLS-1$
				ranges.add(range);
			}
		}
		ranges.sort((a, b) -> Integer.compare(a.start, b.start));
		preview.setStyleRanges(ranges.toArray(StyleRange[]::new));
	}

	private Color color(ColorTheme theme, String key) {
		int[] c = theme.rgb(key);
		return c == null ? null : new Color(getShell().getDisplay(), c[0], c[1], c[2]);
	}

	private void importTheme() {
		FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
		dialog.setText(Messages.page_importTitle);
		dialog.setFilterExtensions(new String[] { "*.xml" }); //$NON-NLS-1$
		String file = dialog.open();
		if (file == null) {
			return;
		}
		try {
			fill(ThemeRegistry.importTheme(Path.of(file)).getId());
			setErrorMessage(null);
		} catch (IOException | RuntimeException e) {
			setErrorMessage(NLS.bind(Messages.page_importFailed, e.getMessage()));
		}
	}

	/** OK applies only a changed choice, so colours tuned by hand afterwards are not overwritten by a later OK. */
	@Override
	public boolean performOk() {
		ColorTheme theme = list == null ? null : selected();
		if (theme == null || (theme.getId().equals(ThemeApplier.currentThemeId()) && !installWide.getSelection()
				&& semantic.getSelection() == ThemeApplier.semanticEnabled())) {
			return true;
		}
		return apply(theme);
	}

	/** Apply always writes the selected theme again. */
	@Override
	protected void performApply() {
		ColorTheme theme = selected();
		if (theme != null) {
			apply(theme);
		}
	}

	private boolean apply(ColorTheme theme) {
		try {
			ThemeApplier.Result result = ThemeApplier.apply(theme, false, installWide.getSelection(), semantic.getSelection());
			fill(theme.getId());
			setErrorMessage(null);
			showStatus(NLS.bind(Messages.page_applied, theme.getName(), Integer.valueOf(result.applied().size())));
			return true;
		} catch (BackingStoreException e) {
			setErrorMessage(NLS.bind(Messages.page_failed, e.getMessage()));
			return false;
		}
	}

	/** Restore Defaults: back to the colours from before a theme was applied. */
	@Override
	protected void performDefaults() {
		try {
			if (ThemeApplier.restore()) {
				showStatus(Messages.page_restored);
			}
			list.deselectAll();
			installWide.setSelection(false);
			semantic.setSelection(false);
			fill(null);
		} catch (BackingStoreException e) {
			setErrorMessage(NLS.bind(Messages.page_failed, e.getMessage()));
		}
		super.performDefaults();
	}
}
