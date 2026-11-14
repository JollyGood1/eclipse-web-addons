package io.github.jollygood1.fileicons;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.ILog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.resource.ResourceManager;
import org.eclipse.jface.viewers.BaseLabelProvider;
import org.eclipse.jface.viewers.DecorationContext;
import org.eclipse.jface.viewers.DecorationOverlayIcon;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.IDecorationContext;
import org.eclipse.jface.viewers.ILabelDecorator;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.ui.PlatformUI;

/**
 * Swaps the whole icon of a file in views that run the workbench decorators (Project Explorer, Open
 * Resource and others).
 * <p>
 * Eclipse applies the lightweight decorators (Git state and the like) BEFORE full decorators such as this
 * one, so the image handed in already carries their marks and replacing it would drop them. To keep them,
 * the marks Eclipse computed for the element are fetched and drawn on the new icon. One of the lightweight
 * decorators is Eclipse's own "file icons based on content analysis", which supplies a replacement base
 * icon (IDecoration.REPLACE); that one is left out, otherwise it would win over the chosen icon.
 * <p>
 * There is no public way to ask for the lightweight marks only, so two internal members of the workbench
 * are read by reflection. If a future Eclipse changes them, the icons still work and only those marks are
 * missing (one warning in the log).
 */
public class FileIconDecorator extends BaseLabelProvider implements ILabelDecorator {

	private final ResourceManager resources = new LocalResourceManager(JFaceResources.getResources());
	private Object scheduler;
	private Method getResult;
	private Field descriptors;
	private boolean marksUnavailable;
	private final Map<ImageDescriptor, Boolean> wholeIcons = new HashMap<>();

	@Override
	public Image decorateImage(Image image, Object element) {
		IResource resource = Adapters.adapt(element, IResource.class);
		if (!(resource instanceof IFile file)) {
			return null;
		}
		Image ours = IconRegistry.imageFor(file.getName(), file);
		return ours == null ? null : withLightweightMarks(ours, element, resource == element ? null : resource);
	}

	private Image withLightweightMarks(Image base, Object element, Object adapted) {
		if (marksUnavailable) {
			return base;
		}
		try {
			if (getResult == null) {
				Object manager = PlatformUI.getWorkbench().getDecoratorManager();
				Field field = manager.getClass().getDeclaredField("scheduler"); //$NON-NLS-1$
				field.setAccessible(true);
				scheduler = field.get(manager);
				getResult = scheduler.getClass().getDeclaredMethod("getResult", Object.class, Object.class, //$NON-NLS-1$
						IDecorationContext.class);
				getResult.setAccessible(true);
			}
			// null while Eclipse is still computing the marks; it refreshes the label when they are ready
			Object result = getResult.invoke(scheduler, element, adapted, DecorationContext.DEFAULT_CONTEXT);
			if (result == null) {
				return base;
			}
			if (descriptors == null) {
				descriptors = result.getClass().getDeclaredField("descriptors"); //$NON-NLS-1$
				descriptors.setAccessible(true);
			}
			ImageDescriptor[] marks = (ImageDescriptor[]) descriptors.get(result);
			if (marks == null) {
				return base;
			}
			marks = marks.clone();
			if (marks.length > IDecoration.REPLACE) {
				marks[IDecoration.REPLACE] = null;
			}
			Rectangle bounds = base.getBounds();
			boolean any = false;
			for (int i = 0; i < marks.length; i++) {
				// some plug-ins replace a file's icon by "overlaying" a whole icon (package.json); not a mark
				if (marks[i] != null && isWholeIcon(marks[i], bounds)) {
					marks[i] = null;
				}
				any |= marks[i] != null;
			}
			if (!any) {
				return base;
			}
			return resources.create(new DecorationOverlayIcon(base, marks, new Point(bounds.width, bounds.height)));
		} catch (ReflectiveOperationException | RuntimeException e) {
			marksUnavailable = true;
			ILog.of(FileIconDecorator.class).warn("Git and similar marks cannot be drawn on replaced file icons", e); //$NON-NLS-1$
			return base;
		}
	}

	private boolean isWholeIcon(ImageDescriptor mark, Rectangle bounds) {
		return wholeIcons.computeIfAbsent(mark, m -> {
			ImageData data = m.getImageData(100);
			return Boolean.valueOf(data != null && data.width >= bounds.width && data.height >= bounds.height);
		}).booleanValue();
	}

	@Override
	public String decorateText(String text, Object element) {
		return null;
	}

	@Override
	public void dispose() {
		resources.dispose();
		super.dispose();
	}
}
