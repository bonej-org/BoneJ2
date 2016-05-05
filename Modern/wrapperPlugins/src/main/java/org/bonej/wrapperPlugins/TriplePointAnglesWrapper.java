package org.bonej.wrapperPlugins;

import java.io.IOException;

import net.imagej.Dataset;
import net.imagej.ImageJ;

import org.bonej.utilities.ImageCheck;
import org.bonej.utilities.ImagePlusHelper;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.NumberWidget;

import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import sc.fiji.analyzeSkeleton.Graph;
import sc.fiji.skeletonize3D.Skeletonize3D_;
import ij.IJ;
import ij.ImagePlus;

/**
 * A wrapper UI class for the TriplePointAngles Op
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>TriplePointAngles")
public class TriplePointAnglesWrapper extends ContextCommand {
	@Parameter(initializer = "initializeImage")
	private Dataset inputImage;

	@Parameter(label = "Measurement mode", description = "Where to measure the triple point angles", style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE, choices = {
			"Opposite vertex", "Edge point"})
	private String measurementMode;

	@Parameter(label = "Edge point #", min = "0", max = "100", stepSize = "1", description = "Ordinal of the edge point used for measuring", style = NumberWidget.SLIDER_STYLE, persist = false)
	private int edgePoint = 0;

	@Parameter(label = "Show skeleton", description = "Show the skeleton of the image")
	private boolean showSkeleton = false;

	@Parameter(label = "Help", callback = "openHelpPage")
	private Button helpButton;

	@Parameter
	private ConvertService convertService;

	@Parameter
	private LogService logService;

	@Parameter
	private PlatformService platformService;

	@Parameter
	private UIService uiService;

	@Override
	public void run() {
		final Skeletonize3D_ skeletoniser = new Skeletonize3D_();
		final AnalyzeSkeleton_ analyser = new AnalyzeSkeleton_();
		final ImagePlus skeleton = ImagePlusHelper.toImagePlus(convertService, inputImage).get();

		// Skeletonize3D_ only accepts 8-bit greyscale images
		IJ.run(skeleton, "8-bit", "");

		skeletoniser.setup("", skeleton);
		skeletoniser.run(null);

		analyser.setup("", skeleton);
		analyser.run();
		final Graph[] graphs = analyser.getGraphs();

		if (graphs == null || graphs.length == 0) {
			uiService.showDialog("Cannot calculate triple point angles: image contains no skeletons",
					DialogPrompt.MessageType.ERROR_MESSAGE);
		}

		if (showSkeleton) {
			uiService.show(skeleton);
		}

		// TODO call Op

		// TODO present results
	}

	public static void main(String... args) {
		final ImageJ imageJ = net.imagej.Main.launch();

		try {
			final Dataset dataset = imageJ.scifio().datasetIO().open("http://imagej.net/images/clown.jpg");
			imageJ.ui().show(dataset);
			imageJ.command().run(TriplePointAnglesWrapper.class, true);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unused")
	private void initializeImage() {
		if (inputImage == null) {
			cancel("No image open");
			return;
		}

		final long spatialDimensions = ImageCheck.countSpatialDimensions(inputImage);
		if (spatialDimensions < 2 || spatialDimensions > 3) {
			cancel("Need a 2D or 3D image");
			return;
		}

		try {
			if (!convertService.supports(inputImage, ImagePlus.class)) {
				cancel("Image cannot be skeletonised (incompatible with IJ1)");
			}
		} catch (NullPointerException npe) {
			cancel("An unexpected error occurred when running Triple Point Angles (" + npe.getMessage() + ")");
			logService.error(npe);
		}
	}

	@SuppressWarnings("unused")
	private void openHelpPage() {
		Help.openHelpPage("http://bonej.org/triplepointangles", platformService, uiService, logService);
	}
}
