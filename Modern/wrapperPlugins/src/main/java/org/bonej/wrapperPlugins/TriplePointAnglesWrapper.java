package org.bonej.wrapperPlugins;

import ij.ImagePlus;
import net.imagej.Dataset;
import net.imagej.ops.OpService;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.IterableInterval;
import org.bonej.ops.TriplePointAngles;
import org.bonej.ops.TriplePointAngles.TriplePoint;
import org.bonej.utilities.ImageCheck;
import org.bonej.utilities.ResultsInserter;
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

import java.util.List;

import static org.bonej.wrapperPlugins.ErrorMessages.*;

/**
 * A wrapper UI class for the TriplePointAngles Op
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>TriplePointAngles")
public class TriplePointAnglesWrapper extends ContextCommand {
    static {
        // NB: Needed if you mix-and-match IJ1 and IJ2 classes.
        // And even then: do not use IJ1 classes in the API!
        LegacyInjector.preinit();
    }

    /** @implNote Use Dataset because it has a conversion to ImagePlus */
    @Parameter(initializer = "initializeImage")
    private Dataset inputImage;

    @Parameter(label = "Measurement mode", description = "Where to measure the triple point angles",
            style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE, choices = {
            "Opposite vertex", "Edge point"})
    private String measurementMode;

    @Parameter(label = "Edge point #", min = "0", max = "100", stepSize = "1",
            description = "Ordinal of the edge point used for measuring", style = NumberWidget.SLIDER_STYLE,
            persist = false)
    private int edgePoint = 0;

    @Parameter(label = "Help", callback = "openHelpPage")
    private Button helpButton;

    @Parameter
    private ConvertService convertService;

    @Parameter
    private LogService logService;

    @Parameter
    private OpService opService;

    @Parameter
    private PlatformService platformService;

    @Parameter
    private UIService uiService;

    @Override
    public void run() {
        //Skeletonise input image
        final ImagePlus skeleton = convertService.convert(inputImage, ImagePlus.class);
        final Skeletonize3D_ skeletoniser = new Skeletonize3D_();
        skeletoniser.setup("", skeleton);
        skeletoniser.run(null);

        /* TODO apply when Skeletonize3D_ 2.0.1 comes out
        final int iterations = skeletoniser.getThinningIterations();
        if (iterations > 1) {
            skeleton.show();
            uiService.showDialog("The image was skeletonised", DialogPrompt.MessageType.INFORMATION_MESSAGE);
        }
        */

        // Analyse skeleton
        final AnalyzeSkeleton_ analyser = new AnalyzeSkeleton_();
        analyser.setup("", skeleton);
        analyser.run();
        final Graph[] graphs = analyser.getGraphs();

        if (graphs == null || graphs.length == 0) {
            uiService.showDialog(
                    "Can't calculate triple point angles: " + NO_SKELETONS,
                    DialogPrompt.MessageType.ERROR_MESSAGE);
            return;
        }

        final List<List<TriplePoint>> results = callTPAOp(graphs);

        showResults(results);
    }

    //region -- Helper methods --
    private List<List<TriplePoint>> callTPAOp(final Graph[] graphs) {
        final int measurementPoint =
                measurementMode.equals("Opposite vertex") ? TriplePointAngles.VERTEX_TO_VERTEX : edgePoint;
        return (List<List<TriplePoint>>) opService.run(TriplePointAngles.class, graphs, measurementPoint);
    }

    private void showResults(final List<List<TriplePoint>> graphList) {
        ResultsInserter resultsInserter = new ResultsInserter();
        String label = inputImage.getName();

        for (List<TriplePoint> triplePointList : graphList) {
            for (TriplePoint triplePoint : triplePointList) {
                resultsInserter.setMeasurementInFirstFreeRow(label, "Skeleton #", triplePoint.getGraphNumber());
                resultsInserter
                        .setMeasurementInFirstFreeRow(label, "Triple point #", triplePoint.getTriplePointNumber());
                final List<Double> angles = triplePoint.getAngles();
                resultsInserter.setMeasurementInFirstFreeRow(label, "α (rad)", angles.get(0));
                resultsInserter.setMeasurementInFirstFreeRow(label, "β (rad)", angles.get(1));
                resultsInserter.setMeasurementInFirstFreeRow(label, "γ (rad)", angles.get(2));
            }
        }

        resultsInserter.updateResults();
    }

    @SuppressWarnings("unused")
    private void initializeImage() {
        if (inputImage == null) {
            cancel(NO_IMAGE_OPEN);
            return;
        }

        final long spatialDimensions = ImageCheck.countSpatialDimensions(inputImage);
        if (spatialDimensions < 2 || spatialDimensions > 3) {
            cancel(NOT_2D_OR_3D_IMAGE);
            return;
        }

        IterableInterval interval = inputImage;
        if (inputImage.getValidBits() != 8 || !ImageCheck.isColorsBinary(interval)) {
            cancel(NOT_8_BIT_BINARY_IMAGE);
            return;
        }

        if (!convertService.supports(inputImage, ImagePlus.class)) {
            cancel(CANNOT_CONVERT_TO_IMAGE_PLUS);
        }
    }

    @SuppressWarnings("unused")
    private void openHelpPage() {
        Help.openHelpPage("http://bonej.org/triplepointangles", platformService, uiService, logService);
    }
    //endregion
}
