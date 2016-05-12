package org.bonej.menuWrappers;

import org.bonej.util.ImageCheck;
import org.bonej.plugins.UsageReporter;

import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;

/**
 * A simple wrapper plugin to add the AnalyzeSkeleton_ plugin under
 * Plugins>BoneJ menu path
 *
 * Displays additional incompatibility warnings to the user
 *
 * @author Michael Doube
 * @author Richard Domander
 * @deprecated Replaced by Replaced by AnalyseSkeletonWrapper in BoneJ2
 */
@Deprecated
public class AnalyseSkeleton implements PlugIn {
	private final AnalyzeSkeleton_ analyser = new AnalyzeSkeleton_();

	@Override
	public void run(String arg) {
		if (arg.equals("about")) {
			// show about dialog and exit
			analyser.setup(arg, null);
			return;
		}

		if (!ImageCheck.checkIJVersion()) {
			return;
		}

		final ImagePlus image;

		try {
			image = IJ.getImage();
		} catch (RuntimeException e) {
			// If no image is open, getImage() throws an exception
			return;
		}

		analyser.setup(arg, image);
		analyser.run(null);

		UsageReporter.reportEvent(this).send();
	}
}
