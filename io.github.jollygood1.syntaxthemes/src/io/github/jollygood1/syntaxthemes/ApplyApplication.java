package io.github.jollygood1.syntaxthemes;

import java.util.Map;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

/**
 * Applies a theme without the UI (the same code as the preference page):
 * <pre>eclipsec -nosplash -data &lt;workspace&gt; -application io.github.jollygood1.syntaxthemes.apply -theme "Tomorrow Night"</pre>
 * Options: -list, -restore, -all (also write mappings of editors that are not installed),
 * -semantic (also switch on the semantic highlights the theme colours).
 */
public class ApplyApplication implements IApplication {

	@Override
	public Object start(IApplicationContext context) throws Exception {
		String[] args = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
		String themeName = null;
		boolean all = false;
		boolean semantic = false;
		boolean restore = false;
		boolean list = false;
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
			case "-theme" -> themeName = i + 1 < args.length ? args[++i] : null; //$NON-NLS-1$
			case "-all" -> all = true; //$NON-NLS-1$
			case "-semantic" -> semantic = true; //$NON-NLS-1$
			case "-restore" -> restore = true; //$NON-NLS-1$
			case "-list" -> list = true; //$NON-NLS-1$
			default -> { /* launcher arguments */ }
			}
		}
		Map<String, ColorTheme> themes = ThemeRegistry.themes();
		if (list) {
			themes.keySet().forEach(System.out::println);
		} else if (restore) {
			System.out.println(ThemeApplier.restore() ? "Previous colours restored." : "No theme was applied."); //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			ColorTheme theme = themeName == null ? null : themes.get(themeName);
			if (theme == null) {
				System.err.println("Unknown theme: " + themeName + ". Available: " + themes.keySet()); //$NON-NLS-1$ //$NON-NLS-2$
				return Integer.valueOf(1);
			}
			ThemeApplier.Result result = ThemeApplier.apply(theme, all, false, semantic);
			System.out.println("Applied " + theme.getName() + " to " + result.applied()); //$NON-NLS-1$ //$NON-NLS-2$
			System.out.println("Skipped (editor not installed): " + result.skipped()); //$NON-NLS-1$
		}
		InstanceScope.INSTANCE.getNode("").flush(); //$NON-NLS-1$
		return IApplication.EXIT_OK;
	}

	@Override
	public void stop() {
		// nothing to stop
	}
}
