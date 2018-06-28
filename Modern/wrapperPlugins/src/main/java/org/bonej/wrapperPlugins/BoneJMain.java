
package org.bonej.wrapperPlugins;

import net.imagej.ImageJ;

import org.scijava.Gateway;

/**
 * A main class for quickly testing the wrapper plugins
 *
 * @author Richard Domander
 */
public final class BoneJMain {

	public static void main(final String... args) {
		final Gateway imageJ = new ImageJ();
		imageJ.launch(args);
	}
}
