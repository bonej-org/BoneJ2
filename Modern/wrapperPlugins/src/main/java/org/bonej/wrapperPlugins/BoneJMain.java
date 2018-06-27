
package org.bonej.wrapperPlugins;

import net.imagej.Dataset;
import net.imagej.DefaultDataset;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imglib2.img.array.ArrayImgs;

import java.io.IOException;

/**
 * A main class for quickly testing the wrapper plugins
 *
 * @author Richard Domander
 */
public class BoneJMain {

	public static void main(String... args) throws IOException {
		final ImageJ imageJ = new ImageJ();
		imageJ.launch(args);
		Dataset d = new DefaultDataset(imageJ.context(),new ImgPlus<>(ArrayImgs.floats(5,5,5)));
		//imageJ.io().save(d , "/home/alessandro/Documents/code/BoneJ2-helper-stuff/test-images/bla.tif");
	}
}
