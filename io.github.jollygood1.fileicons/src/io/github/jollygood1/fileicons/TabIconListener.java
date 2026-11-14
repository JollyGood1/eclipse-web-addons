package io.github.jollygood1.fileicons;

import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.workbench.IPresentationEngine;
import org.eclipse.e4.ui.workbench.UIEvents;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.PlatformUI;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

/**
 * Editor tab icons. An editor publishes its title image to the window model under OVERRIDE_ICON_IMAGE_KEY,
 * and again whenever its title changes (save, rename). This listener (registered as an OSGi service in
 * OSGI-INF/tabIconListener.xml) sees that and puts the chosen icon there instead.
 */
public class TabIconListener implements EventHandler {

	private static final String EDITOR = "org.eclipse.e4.ui.compatibility.editor"; //$NON-NLS-1$

	@Override
	public void handleEvent(Event event) {
		if (!(event.getProperty(UIEvents.EventTags.ELEMENT) instanceof MPart part) || !EDITOR.equals(part.getElementId())
				|| !PlatformUI.isWorkbenchRunning()) {
			return;
		}
		Object current = part.getTransientData().get(IPresentationEngine.OVERRIDE_ICON_IMAGE_KEY);
		if (current == null || IconRegistry.isOurs(current)) {
			return;
		}
		// later, so the editor has finished publishing its own image
		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			Image ours = IconRegistry.imageFor(part.getLabel(), IconRegistry.fileOf(part));
			if (ours != null && part.getTransientData().get(IPresentationEngine.OVERRIDE_ICON_IMAGE_KEY) != ours) {
				part.getTransientData().put(IPresentationEngine.OVERRIDE_ICON_IMAGE_KEY, ours);
			}
		});
	}
}
