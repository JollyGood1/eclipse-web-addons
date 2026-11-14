package io.github.jollygood1.syntaxthemes;

import org.eclipse.core.runtime.ILog;
import org.eclipse.ui.PlatformUI;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.prefs.BackingStoreException;

/**
 * Registered as an OSGi service (OSGI-INF/windowThemeListener.xml) for the e4 "theme changed" event.
 * Switching the window theme (General &gt; Appearance) makes Eclipse overwrite editor colours with the
 * new window theme's own; this puts the chosen colour theme back.
 */
public class WindowThemeListener implements EventHandler {

	@Override
	public void handleEvent(Event event) {
		if (!PlatformUI.isWorkbenchRunning() || ThemeApplier.currentThemeId() == null) {
			return;
		}
		// Eclipse's own handler for this event is the one that overwrites the colours, and the order of
		// handlers is not defined. asyncExec runs after the event has been delivered to all of them.
		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			try {
				ThemeApplier.reapplyCurrent();
			} catch (BackingStoreException | RuntimeException e) {
				ILog.of(WindowThemeListener.class).error("Could not re-apply the editor colour theme", e); //$NON-NLS-1$
			}
		});
	}
}
