package de.soderer.restclient.image;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Shell;

import de.soderer.utilities.VisibleException;

public class ImageManager {
	private static ImageManager instance = null;

	private final Shell shell;
	private final Map<String, Image> store = new HashMap<>();

	public ImageManager(final Shell shell) {
		this.shell = shell;
		instance = this;
	}

	public static Image getImage(final String name) throws VisibleException {
		if (instance == null) {
			throw new VisibleException("ImageManager needs to be initialized before usage");
		}

		if (!instance.store.containsKey(name)) {
			final String resourcePath = "/images/icons/" + name;
			try (InputStream resourceStream = instance.getClass().getResourceAsStream(resourcePath)) {
				if (resourceStream == null) {
					throw new VisibleException("Image resource not found: '" + resourcePath + "'");
				}
				instance.store.put(name, new Image(instance.shell.getDisplay(), resourceStream));
			} catch (final IOException e) {
				throw new VisibleException("Cannot read image resource '" + resourcePath + "': " + e.getMessage());
			}
		}
		
		return instance.store.get(name);
	}

	/**
	 * Releases the native handles of all cached images. SWT Image objects wrap a native OS
	 * resource that is not reclaimed by the garbage collector - without calling this when the
	 * ImageManager (and its Shell) is no longer needed, every cached icon leaks its native handle.
	 */
	public void dispose() {
		for (final Image image : store.values()) {
			if (image != null && !image.isDisposed()) {
				image.dispose();
			}
		}
		store.clear();
		if (instance == this) {
			instance = null;
		}
	}
}
