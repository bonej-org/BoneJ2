
package org.bonej.wrapperPlugins;

import static org.bonej.ops.SolveQuadricEq.QUADRIC_TERMS;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_3D_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.imagej.ops.OpService;
import net.imagej.patcher.LegacyInjector;
import net.imagej.table.DefaultColumn;
import net.imagej.table.Table;

import org.bonej.ops.SolveQuadricEq;
import org.bonej.ops.ellipsoid.Ellipsoid;
import org.bonej.ops.ellipsoid.QuadricToEllipsoid;
import org.bonej.utilities.ImagePlusUtil;
import org.bonej.utilities.RoiManagerUtil;
import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.bonej.wrapperPlugins.wrapperUtils.ResultUtils;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.vecmath.Matrix4d;
import org.scijava.vecmath.Vector3d;

import ij.ImagePlus;
import ij.measure.Calibration;
import ij.plugin.frame.RoiManager;

/**
 * A command that takes point ROIs from the ROI manager, and tries to fit an
 * ellipsoid on them. If the fitting succeeds, it reports the properties of the
 * ellipsoid.
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Fit ellipsoid")
public class FitEllipsoidWrapper extends ContextCommand {

	static {
		// NB: Needed if you mix-and-match IJ1 and IJ2 in a class.
		// And even then: do not use IJ1 classes in the API!
		LegacyInjector.preinit();
	}

	// Take in ImagePlus because there's already legacy dependencies (ROIs), and
	// there's no stable ROI system for ImageJ2 classes yet.
	@Parameter(validater = "validateImage")
	private ImagePlus inputImage;

	/**
	 * The ellipsoid results in a {@link Table}.
	 * <p>
	 * Null if there are no results.
	 * </p>
	 */
	@Parameter(type = ItemIO.OUTPUT, label = "BoneJ results")
	private Table<DefaultColumn<String>, String> resultsTable;

	@Parameter
	private OpService opService;

	@Parameter
	private StatusService statusService;

	@Parameter
	private UIService uiService;

	private List<Vector3d> points;

	@Override
	public void run() {
		if (!initPointROIs()) {
			cancel("Please populate ROI Manager with at least " + QUADRIC_TERMS +
				" point ROIs");
			return;
		}
		statusService.showStatus("Fit ellipsoid: solving ellipsoid equation");
		final Matrix4d quadric = (Matrix4d) opService.run(SolveQuadricEq.class,
			points);
		statusService.showStatus("Fit ellipsoid: determining ellipsoid parameters");
		@SuppressWarnings("unchecked")
		final Optional<Ellipsoid> result = (Optional<Ellipsoid>) opService.run(
			QuadricToEllipsoid.class, quadric);
		if (!result.isPresent()) {
			cancel("Can't fit ellipsoid to points.\n" +
				"Add more point ROI's to the ROI Manager and try again.");
			return;
		}
		addResults(result.get());
		if (SharedTable.hasData()) {
			resultsTable = SharedTable.getTable();
		}
	}

	private void addResults(final Ellipsoid ellipsoid) {
		final String unitHeader = ResultUtils.getUnitHeader(inputImage);
		final String label = inputImage.getTitle();
		final Vector3d centroid = ellipsoid.getCentroid();
		SharedTable.add(label, "Centroid x " + unitHeader, centroid.getX());
		SharedTable.add(label, "Centroid y " + unitHeader, centroid.getY());
		SharedTable.add(label, "Centroid z " + unitHeader, centroid.getZ());
		SharedTable.add(label, "Radius a " + unitHeader, ellipsoid.getA());
		SharedTable.add(label, "Radius b " + unitHeader, ellipsoid.getB());
		SharedTable.add(label, "Radius c " + unitHeader, ellipsoid.getC());
	}

	private boolean initPointROIs() {
		// You can't have a RoiManager as a @Parameter with its own validator method
		final RoiManager manager = RoiManager.getInstance();
		if (manager == null) {
			return false;
		}
		final Calibration calibration = inputImage.getCalibration();
		final Function<Vector3d, Vector3d> calibrate = v -> {
			v.x *= calibration.pixelWidth;
			v.y *= calibration.pixelHeight;
			v.z *= calibration.pixelDepth;
			return v;
		};
		points = RoiManagerUtil.pointROICoordinates(manager).stream().filter(
			p -> !RoiManagerUtil.isActiveOnAllSlices((int) p.z)).map(calibrate)
			.collect(Collectors.toList());
		return points.size() >= QUADRIC_TERMS;
	}

	@SuppressWarnings("unused")
	private void validateImage() {
		if (inputImage == null) {
			cancel(NO_IMAGE_OPEN);
			return;
		}
		if (!ImagePlusUtil.is3D(inputImage)) {
			cancel(NOT_3D_IMAGE);
			return;
		}
		if (!Common.warnAnisotropy(inputImage, uiService)) {
			cancel(null);
		}
	}
}
