/**
 * Density-weighted 2D polar second moments.
 *
 * <p>
 * This implementation incorporates pixel values directly into the
 * per-pixel second-moment contribution, such that density influences
 * both centroid location and second-moment weighting.
 * </p>
 *
 * <p>
 * The resulting quantities should be interpreted as voxel-scale
 * structural descriptors derived from calibrated images, rather than
 * literal physical moments of inertia.
 * </p>
 *
 * <p>
 * This plugin is experimental and provided as an opt-in alternative
 * to the existing Slice Geometry calculations.
 * </p>
 *
 * @author Jonathan Williams
 * @author Michael Doube
 */


package org.bonej.plugins;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.awt.AWTEvent;
import java.awt.Choice;
import java.awt.TextField;
import java.util.Vector;

/**
 * PolarMoments2D
 * - Slice-wise pMOA (geometric) and pMOI (density-weighted) from thresholded 2D slices
 * - Optional verbose outputs: Ixx/Iyy/Ixy, Imin/Imax, theta for pMOA & pMOI
 * - Optional plotting of pMOA/pMOI as line graphs
 * - Optional creation of pMOA/pMOI contribution heatmap stacks (32-bit)
 * - Plan to add option for pMOA/pMOI contribution histograms collour matched to heatmaps (already in previous Python implementation)
 * Density calibration UI (Option 3):
 *   Main dialog: choose mode (None / Coefficients / Phantom points) and optionally open a secondary dialog
 *   Secondary dialog:
 *     - Coefficients: slope + intercept
 *     - Phantom points: 2–5 points, >=3 uses least-squares
 */
public class PolarMoments2D implements PlugIn {

	// ------------------------------------------------------------
	// Density calibration UI/state (persist between runs)
	// ------------------------------------------------------------
	private enum DensityCalMode {
		NONE_UNIFORM,
		COEFFICIENTS,
		PHANTOM_POINTS
	}

	private static DensityCalMode lastMode = DensityCalMode.PHANTOM_POINTS;

	// Coefficients mode (mgHA/cm^3 = m*grey + c)
	private static double lastSlope = 0.0;      // mgHA/cm^3 per grey
	private static double lastIntercept = 0.0;  // mgHA/cm^3

	// Phantom points mode
	private static int lastTotalPoints = 2;
	private static double lastG1 = 0.0,   lastD1 = 0.0;
	private static double lastG2 = 255.0, lastD2 = 1000.0;
	private static double lastG3 = 0.0,   lastD3 = 0.0;
	private static double lastG4 = 0.0,   lastD4 = 0.0;
	private static double lastG5 = 0.0,   lastD5 = 0.0;

	private static String modeToLabel(final DensityCalMode m) {
		switch (m) {
			case NONE_UNIFORM:   return "None (uniform density)";
			case COEFFICIENTS:   return "Coefficients (slope + intercept)";
			case PHANTOM_POINTS: return "Phantom points (2-5)";
			default:             return "Phantom points (2-5)";
		}
	}

	private static DensityCalMode labelToMode(final String s) {
		if (s == null) return DensityCalMode.PHANTOM_POINTS;
		final String t = s.trim().toLowerCase();
		if (t.startsWith("none")) return DensityCalMode.NONE_UNIFORM;
		if (t.startsWith("coeff")) return DensityCalMode.COEFFICIENTS;
		return DensityCalMode.PHANTOM_POINTS;
	}

	// ------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------
	private static boolean isPixelUnits(final String units) {
		if (units == null) return true;
		final String u = units.trim().toLowerCase();
		return u.isEmpty() || u.equals("pixel") || u.equals("pixels");
	}

	private static int clampInt(final int v, final int lo, final int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	// Map image grey value to material density using user-defined calibration.
	// Clamp negative densities arising from calibration extrapolation
	// to avoid non-physical contributions to the second moments
	
	private static double toDensity(final double rawValue, final double m, final double c) {
		return m * rawValue + c; // mgHA/cm^3
	}

	/**
	 * Compute linear calibration (m, c) mapping grey -> density using:
	 * - exactly 2 points: exact line through points
	 * - >=3 points: least-squares linear regression
	 *
	 * Returns double[]{m, c}
	 */
	private static double[] fitCalibration(final double[] g, final double[] d, final int n) {
		if (n < 2) throw new IllegalArgumentException("Need at least 2 calibration points.");
		if (n == 2) {
			if (g[1] == g[0]) throw new IllegalArgumentException("Grey1 and Grey2 must differ.");
			final double m = (d[1] - d[0]) / (g[1] - g[0]);
			final double c = d[0] - m * g[0];
			return new double[]{m, c};
		}

		// Least-squares fit: d = m*g + c
		double sumG = 0, sumD = 0, sumGG = 0, sumGD = 0;
		for (int i = 0; i < n; i++) {
			sumG += g[i];
			sumD += d[i];
			sumGG += g[i] * g[i];
			sumGD += g[i] * d[i];
		}
		final double denom = n * sumGG - sumG * sumG;
		if (denom == 0) throw new IllegalArgumentException("Calibration points have zero variance in grey.");
		final double m = (n * sumGD - sumG * sumD) / denom;
		final double c = (sumD - m * sumG) / n;
		return new double[]{m, c};
	}

	/**
	 * Principal second moments + orientation for a 2D section from centroidal Ixx, Iyy, Ixy.
	 *
	 * Eigenvalues are standard; theta is computed from the eigenvector of Imax.
	 * Returns double[]{Imin, Imax, thetaRadians} with theta wrapped to [-pi/2, pi/2).
	 */
	private static double[] principal(final double Ixx, final double Iyy, final double Ixy) {

		// Eigenvalues of [[Ixx, Ixy],[Ixy, Iyy]]
		final double avg  = 0.5 * (Ixx + Iyy);
		final double diff = 0.5 * (Ixx - Iyy);
		final double rad  = Math.sqrt(diff * diff + Ixy * Ixy);

		final double Imax = avg + rad;
		final double Imin = avg - rad;

		// Eigenvector for Imax: (Ixx - Imax) vx + Ixy vy = 0
		final double theta;
		final double eps = 1e-30;

		if (Math.abs(Ixy) < eps) {
			theta = (Ixx >= Iyy) ? 0.0 : (Math.PI / 2.0);
		} else {
			final double vx = 1.0;
			final double vy = (Imax - Ixx) / Ixy;
			theta = Math.atan2(vy, vx);
		}

		double t = theta;
		while (t < -Math.PI / 2.0) t += Math.PI;
		while (t >= Math.PI / 2.0) t -= Math.PI;

		return new double[]{Imin, Imax, t};
	}

	// ------------------------------------------------------------
	// Secondary calibration dialog
	// ------------------------------------------------------------
	@SuppressWarnings({"rawtypes"})
	private static boolean configureDensityCalibrationDialog() {

		if (lastMode == DensityCalMode.NONE_UNIFORM) {
			IJ.showMessage("Density calibration",
				"Mode is 'None (uniform density)'.\nNo calibration settings to edit.");
			return true;
		}

		if (lastMode == DensityCalMode.COEFFICIENTS) {
			final GenericDialog cd = new GenericDialog("Density calibration (coefficients)");
			cd.addMessage("Density = m * grey + c  (mgHA/cm^3)");
			cd.addNumericField("Slope m (mgHA/cm^3 per grey):", lastSlope, 6);
			cd.addNumericField("Intercept c (mgHA/cm^3):", lastIntercept, 6);
			cd.showDialog();
			if (cd.wasCanceled()) return false;

			lastSlope = cd.getNextNumber();
			lastIntercept = cd.getNextNumber();
			return true;
		}

		// PHANTOM_POINTS
		final GenericDialog pd = new GenericDialog("Density calibration (phantom points)");
		pd.addMessage("Enter at least 2 points (required).");
		pd.addMessage(">= 3 points uses least-squares fit. (grey -> mgHA/cm^3)");

		pd.addNumericField("Grey1:", lastG1, 6);
		pd.addNumericField("Density1 (mgHA/cm^3):", lastD1, 6);

		pd.addNumericField("Grey2:", lastG2, 6);
		pd.addNumericField("Density2 (mgHA/cm^3):", lastD2, 6);

		final String[] totalChoices = new String[]{"2", "3", "4", "5"};
		pd.addChoice("Total calibration points:", totalChoices, Integer.toString(clampInt(lastTotalPoints, 2, 5)));

		pd.addNumericField("Grey3:", lastG3, 6);
		pd.addNumericField("Density3 (mgHA/cm^3):", lastD3, 6);

		pd.addNumericField("Grey4:", lastG4, 6);
		pd.addNumericField("Density4 (mgHA/cm^3):", lastD4, 6);

		pd.addNumericField("Grey5:", lastG5, 6);
		pd.addNumericField("Density5 (mgHA/cm^3):", lastD5, 6);

		// Enable/disable 3–5 fields based on total points
		final Vector numV = pd.getNumericFields(); // g1,d1,g2,d2,g3,d3,g4,d4,g5,d5
		final TextField g3Field = (TextField) numV.get(4);
		final TextField d3Field = (TextField) numV.get(5);
		final TextField g4Field = (TextField) numV.get(6);
		final TextField d4Field = (TextField) numV.get(7);
		final TextField g5Field = (TextField) numV.get(8);
		final TextField d5Field = (TextField) numV.get(9);

		final Vector choiceV = pd.getChoices();
		final Choice totalChoice = (Choice) choiceV.get(0);

		final DialogListener dl = new DialogListener() {
			@Override
			public boolean dialogItemChanged(final GenericDialog dialog, final AWTEvent e) {
				int total = 2;
				try { total = Integer.parseInt(totalChoice.getSelectedItem().trim()); }
				catch (final Exception ex) { total = 2; }
				total = clampInt(total, 2, 5);

				final boolean en3 = total >= 3;
				final boolean en4 = total >= 4;
				final boolean en5 = total >= 5;

				g3Field.setEnabled(en3);
				d3Field.setEnabled(en3);
				g4Field.setEnabled(en4);
				d4Field.setEnabled(en4);
				g5Field.setEnabled(en5);
				d5Field.setEnabled(en5);

				return true;
			}
		};
		pd.addDialogListener(dl);
		dl.dialogItemChanged(pd, null);

		pd.showDialog();
		if (pd.wasCanceled()) return false;

		lastG1 = pd.getNextNumber();
		lastD1 = pd.getNextNumber();
		lastG2 = pd.getNextNumber();
		lastD2 = pd.getNextNumber();

		int totalPoints;
		try { totalPoints = Integer.parseInt(pd.getNextChoice().trim()); }
		catch (final Exception ex) { totalPoints = 2; }
		lastTotalPoints = clampInt(totalPoints, 2, 5);

		lastG3 = pd.getNextNumber();
		lastD3 = pd.getNextNumber();
		lastG4 = pd.getNextNumber();
		lastD4 = pd.getNextNumber();
		lastG5 = pd.getNextNumber();
		lastD5 = pd.getNextNumber();

		return true;
	}

	// ------------------------------------------------------------
	// Main plugin
	// ------------------------------------------------------------
	@SuppressWarnings({"rawtypes"})
	@Override
	public void run(final String arg) {

		final ImagePlus imp = IJ.getImage();
		if (imp == null) {
			IJ.noImage();
			return;
		}

		final int nSlices = imp.getStackSize();
		if (nSlices < 1) {
			IJ.error("Polar Moments (Slice-wise)", "No image data found.");
			return;
		}

		final String units = imp.getCalibration().getUnits();
		final boolean uncalibrated = isPixelUnits(units)
			|| imp.getCalibration().pixelWidth <= 0
			|| imp.getCalibration().pixelHeight <= 0;

		final GenericDialog gd = new GenericDialog("Polar Moments (Slice-wise)");
		gd.addNumericField("Start slice:", 1, 0);
		gd.addNumericField("End slice:", nSlices, 0);

		gd.addMessage("Segmentation (threshold in raw image units)");
		gd.addNumericField("Min:", 1, 2);
		gd.addNumericField("Max:", 255, 2);

		gd.addMessage("Spatial calibration");
		gd.addMessage("Current units: " + (units == null ? "" : units));
		gd.addCheckbox("Override pixel size (enter in microns)", uncalibrated);
		gd.addNumericField("Pixel width (um):", 10.0, 4);
		gd.addNumericField("Pixel height (um):", 10.0, 4);

		gd.addCheckbox("Include pixel self-term (finite pixel correction)", true);
		gd.addCheckbox("Verbose outputs (Ixx, Iyy, Ixy, Imin, Imax, theta for pMOA & pMOI)", false);

		// Plotting + heatmaps
		gd.addCheckbox("Plot pMOA & pMOI (line graphs)", false);
		gd.addCheckbox("Create pMOA/pMOI contribution heatmap stacks", false);
		gd.addCheckbox("Manual heatmap display ranges", false);
		gd.addNumericField("pMOA vmin:", 0.0, 6);
		gd.addNumericField("pMOA vmax:", 1.0, 6);
		gd.addNumericField("pMOI vmin:", 0.0, 6);
		gd.addNumericField("pMOI vmax:", 1.0, 6);

		// Density calibration (Option 3)
		gd.addMessage("Density calibration");
		final String[] modeChoices = new String[] {
			modeToLabel(DensityCalMode.NONE_UNIFORM),
			modeToLabel(DensityCalMode.COEFFICIENTS),
			modeToLabel(DensityCalMode.PHANTOM_POINTS)
		};
		gd.addChoice("Density calibration mode:", modeChoices, modeToLabel(lastMode));
		gd.addCheckbox("Edit density calibration settings...", false);

		// Disable the manual range numeric fields unless Manual heatmap display ranges is checked
		final Vector cbV = gd.getCheckboxes();
		final int idxManualRanges = cbV.size() - 1 - 0; // last checkbox added is "Edit density..." so not last
		// We can't rely on this index, so get by position from the end:
		// Checkboxes added order:
		// 0 overrideMicrons, 1 includeSelfTerm, 2 verbose, 3 plot, 4 heatmaps, 5 manualRanges, 6 editDensity
		final int manualRangesCheckboxIndex = 5;
		final Vector numV = gd.getNumericFields();
		// Numeric fields order:
		// 0 start,1 end,2 min,3 max,4 pxW,5 pxH,6 pmoaVmin,7 pmoaVmax,8 pmoiVmin,9 pmoiVmax
		final TextField pmoaVminField = (TextField) numV.get(6);
		final TextField pmoaVmaxField = (TextField) numV.get(7);
		final TextField pmoiVminField = (TextField) numV.get(8);
		final TextField pmoiVmaxField = (TextField) numV.get(9);

		final DialogListener mainDL = new DialogListener() {
			@Override
			public boolean dialogItemChanged(final GenericDialog dialog, final AWTEvent e) {
				final Vector cbs = dialog.getCheckboxes();
				final boolean manual = ((java.awt.Checkbox) cbs.get(manualRangesCheckboxIndex)).getState();
				pmoaVminField.setEnabled(manual);
				pmoaVmaxField.setEnabled(manual);
				pmoiVminField.setEnabled(manual);
				pmoiVmaxField.setEnabled(manual);
				return true;
			}
		};
		gd.addDialogListener(mainDL);
		mainDL.dialogItemChanged(gd, null);

		gd.showDialog();
		if (gd.wasCanceled()) return;

		final int startSlice = (int) gd.getNextNumber();
		final int endSlice = (int) gd.getNextNumber();

		final double min = gd.getNextNumber();
		final double max = gd.getNextNumber();

		final boolean overrideMicrons = gd.getNextBoolean();
		final double pxW_um = gd.getNextNumber();
		final double pxH_um = gd.getNextNumber();

		final boolean includeSelfTerm = gd.getNextBoolean();
		final boolean verbose = gd.getNextBoolean();

		final boolean doPlot = gd.getNextBoolean();
		final boolean doHeatmaps = gd.getNextBoolean();
		final boolean manualRanges = gd.getNextBoolean();

		final double pmoaVmin = gd.getNextNumber();
		final double pmoaVmax = gd.getNextNumber();
		final double pmoiVmin = gd.getNextNumber();
		final double pmoiVmax = gd.getNextNumber();

		final DensityCalMode mode = labelToMode(gd.getNextChoice());
		final boolean editCal = gd.getNextBoolean();

		// persist mode for next run
		lastMode = mode;

		if (editCal) {
			if (!configureDensityCalibrationDialog()) return;
		}

		if (startSlice < 1 || endSlice > nSlices || startSlice > endSlice) {
			IJ.error("Invalid slice range");
			return;
		}
		if (min > max) {
			IJ.error("Invalid threshold", "Min must be <= Max.");
			return;
		}
		if (manualRanges) {
			if (pmoaVmin >= pmoaVmax) {
				IJ.error("Invalid heatmap range", "pMOA vmin must be < pMOA vmax.");
				return;
			}
			if (pmoiVmin >= pmoiVmax) {
				IJ.error("Invalid heatmap range", "pMOI vmin must be < pMOI vmax.");
				return;
			}
		}

		// Effective pixel size in mm
		final double pxW = overrideMicrons ? pxW_um / 1000.0 : imp.getCalibration().pixelWidth;
		final double pxH = overrideMicrons ? pxH_um / 1000.0 : imp.getCalibration().pixelHeight;

		if (pxW <= 0 || pxH <= 0) {
			IJ.error("Invalid calibration", "Pixel width/height must be > 0.");
			return;
		}

		final double dA = pxW * pxH; // mm^2

		// Split self-term for Ixx/Iyy (axis-aligned rectangle about its own centroid)
		final double IxxSelf = includeSelfTerm ? dA * (pxH * pxH) / 12.0 : 0.0;
		final double IyySelf = includeSelfTerm ? dA * (pxW * pxW) / 12.0 : 0.0;
		final double pixelSelfTerm = IxxSelf + IyySelf; // fast polar accumulation

		if (!overrideMicrons && uncalibrated) {
			IJ.showMessage("Warning",
				"Image appears uncalibrated (units = pixels).\n\n" +
				"Outputs will be in pixel-based units unless\n" +
				"pixel size override is enabled.");
		}

		// Determine density calibration coefficients m,c (mgHA/cm^3 = m*grey + c)
		final double m, c;
		if (lastMode == DensityCalMode.NONE_UNIFORM) {
			m = 0.0;
			c = 1.0; // unit density
		} else if (lastMode == DensityCalMode.COEFFICIENTS) {
			m = lastSlope;
			c = lastIntercept;
		} else {
			final int totalPoints = clampInt(lastTotalPoints, 2, 5);

			final double[] g = new double[5];
			final double[] d = new double[5];

			g[0] = lastG1; d[0] = lastD1;
			g[1] = lastG2; d[1] = lastD2;
			if (totalPoints >= 3) { g[2] = lastG3; d[2] = lastD3; }
			if (totalPoints >= 4) { g[3] = lastG4; d[3] = lastD4; }
			if (totalPoints >= 5) { g[4] = lastG5; d[4] = lastD5; }

			try {
				final double[] mc = fitCalibration(g, d, totalPoints);
				m = mc[0];
				c = mc[1];
			} catch (final Exception ex) {
				IJ.error("Calibration error", ex.getMessage());
				return;
			}
		}

		// Setup results + optional arrays for plotting
		final ResultsTable rt = new ResultsTable();
		rt.setPrecision(6);

		final int nOut = endSlice - startSlice + 1;
		final double[] pmoaSeries = doPlot ? new double[nOut] : null;
		final double[] pmoiSeries = doPlot ? new double[nOut] : null;
		final double[] sliceSeries = doPlot ? new double[nOut] : null;

		// Optional heatmap stacks
		ImageStack pmoaStack = null;
		ImageStack pmoiStack = null;

		IJ.log("=== Polar Moments (Slice-wise): pMOA + pMOI ===");
		IJ.log("Image: " + imp.getTitle());
		IJ.log("Slices: " + startSlice + " to " + endSlice);
		IJ.log("Threshold (raw): [" + min + ", " + max + "]");
		IJ.log("Pixel size (mm): " + pxW + " x " + pxH);
		IJ.log("Pixel self-term: " + (includeSelfTerm ? "ON" : "OFF"));
		IJ.log("Verbose outputs: " + (verbose ? "ON" : "OFF"));
		IJ.log("Plot line graphs: " + (doPlot ? "ON" : "OFF"));
		IJ.log("Heatmap stacks: " + (doHeatmaps ? "ON" : "OFF"));
		if (doHeatmaps) {
			IJ.log("Manual heatmap ranges: " + (manualRanges ? "ON" : "OFF"));
			if (manualRanges) {
				IJ.log("pMOA range: [" + pmoaVmin + ", " + pmoaVmax + "]");
				IJ.log("pMOI range: [" + pmoiVmin + ", " + pmoiVmax + "]");
			}
		}
		IJ.log("Density calibration mode: " + modeToLabel(lastMode));
		IJ.log("Fitted/used: density = " + m + " * grey + " + c + "  (mgHA/cm^3)");

		final ImageStack stack = imp.getImageStack();

		// We need width/height for heatmaps
		final int w0 = stack.getProcessor(startSlice).getWidth();
		final int h0 = stack.getProcessor(startSlice).getHeight();
		if (doHeatmaps) {
			pmoaStack = new ImageStack(w0, h0);
			pmoiStack = new ImageStack(w0, h0);
		}

		int outIdx = 0;

		for (int z = startSlice; z <= endSlice; z++) {
			IJ.showStatus("Polar moments (2D): slice " + z + "/" + endSlice);
			IJ.showProgress(z - startSlice, Math.max(1, endSlice - startSlice));

			final ImageProcessor ip = stack.getProcessor(z);
			final int w = ip.getWidth();
			final int h = ip.getHeight();

			// Pass 1: accumulate for geometric centroid and density-weighted centroid
			double sumA = 0.0;
			double sumX_A = 0.0;
			double sumY_A = 0.0;

			double sumRhoA = 0.0;     // Σ (rho * dA), rho in mgHA/cm^3
			double sumX_Rho = 0.0;    // Σ (X * rho * dA)
			double sumY_Rho = 0.0;    // Σ (Y * rho * dA)

			double sumDens = 0.0;
			int nPix = 0;
			boolean sawNegDensity = false;

			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					final double v = ip.getf(x, y);
					if (v < min || v > max) continue;

					final double X = (x + 0.5) * pxW;
					final double Y = (y + 0.5) * pxH;

					sumA += dA;
					sumX_A += X * dA;
					sumY_A += Y * dA;

					// Density-weighted centroid accumulation: density influences the origin
					// about which second moments are computed (in addition to weighting the moments themselves).

					final double dens = toDensity(v, m, c); // mgHA/cm^3
					sumDens += dens;
					nPix++;

					double densClamped = dens;
					if (densClamped < 0) {
						densClamped = 0;
						sawNegDensity = true;
					}

					final double rhoA = densClamped * dA;
					sumRhoA += rhoA;
					sumX_Rho += X * rhoA;
					sumY_Rho += Y * rhoA;
				}
			}

			// Prepare heatmap slice processors if needed
			FloatProcessor fpA = null;
			FloatProcessor fpI = null;
			if (doHeatmaps) {
				fpA = new FloatProcessor(w, h);
				fpI = new FloatProcessor(w, h);
			}

			if (nPix == 0) {
				rt.incrementCounter();
				rt.addValue("Slice", z);
				rt.addValue("Ct.Ar (mm^2)", 0.0);
				rt.addValue("Xc_geom (mm)", Double.NaN);
				rt.addValue("Yc_geom (mm)", Double.NaN);
				rt.addValue("Xc_dens (mm)", Double.NaN);
				rt.addValue("Yc_dens (mm)", Double.NaN);
				rt.addValue("pMOA (mm^4)", 0.0);
				rt.addValue("MeanDens (mgHA/cm^3)", Double.NaN);
				rt.addValue("pMOI ((mgHA/cm^3)*mm^4)", 0.0);

				if (verbose) {
					rt.addValue("Ixx_pMOA (mm^4)", Double.NaN);
					rt.addValue("Iyy_pMOA (mm^4)", Double.NaN);
					rt.addValue("Ixy_pMOA (mm^4)", Double.NaN);
					rt.addValue("Imin_pMOA (mm^4)", Double.NaN);
					rt.addValue("Imax_pMOA (mm^4)", Double.NaN);
					rt.addValue("theta_pMOA (deg)", Double.NaN);

					rt.addValue("Ixx_pMOI ((mgHA/cm^3)*mm^4)", Double.NaN);
					rt.addValue("Iyy_pMOI ((mgHA/cm^3)*mm^4)", Double.NaN);
					rt.addValue("Ixy_pMOI ((mgHA/cm^3)*mm^4)", Double.NaN);
					rt.addValue("Imin_pMOI ((mgHA/cm^3)*mm^4)", Double.NaN);
					rt.addValue("Imax_pMOI ((mgHA/cm^3)*mm^4)", Double.NaN);
					rt.addValue("theta_pMOI (deg)", Double.NaN);
				}

				if (doPlot) {
					sliceSeries[outIdx] = z;
					pmoaSeries[outIdx] = 0.0;
					pmoiSeries[outIdx] = 0.0;
					outIdx++;
				}

				if (doHeatmaps) {
					// blank slices
					pmoaStack.addSlice("pMOA_" + z, fpA);
					pmoiStack.addSlice("pMOI_" + z, fpI);
				}

				continue;
			}

			final double xcA = sumX_A / sumA;
			final double ycA = sumY_A / sumA;

			// If all densities clamped to 0, fall back to geometric centroid
			final double xcR = (sumRhoA > 0) ? (sumX_Rho / sumRhoA) : xcA;
			final double ycR = (sumRhoA > 0) ? (sumY_Rho / sumRhoA) : ycA;

			// Pass 2: fast (polar) OR verbose (Ixx/Iyy/Ixy + principal)
			double pMOA = 0.0;
			double pMOI = 0.0;

			double IxxA = 0.0, IyyA = 0.0, IxyA = 0.0;
			double IxxR = 0.0, IyyR = 0.0, IxyR = 0.0;

			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					final double v = ip.getf(x, y);
					if (v < min || v > max) continue;

					final double X = (x + 0.5) * pxW;
					final double Y = (y + 0.5) * pxH;

					final double dxA = X - xcA;
					final double dyA = Y - ycA;

					// Density enters directly into the per-pixel second-moment integrand,
					// rather than being applied only as a post-hoc scaling of geometric moments.

					final double dens = toDensity(v, m, c);
					final double densClamped = dens < 0 ? 0 : dens;

					final double dxR = X - xcR;
					final double dyR = Y - ycR;

					if (!verbose) {
						// FAST POLAR PATH (also used to populate heatmaps if requested)
						final double baseA = (dxA * dxA + dyA * dyA) * dA + pixelSelfTerm;
						pMOA += baseA;

						final double baseR = (dxR * dxR + dyR * dyR) * dA + pixelSelfTerm;
						pMOI += densClamped * baseR;

						if (doHeatmaps) {
							fpA.setf(x, y, (float) baseA);
							fpI.setf(x, y, (float) (densClamped * baseR));
						}
					} else {
						// VERBOSE: CARTESIAN SECOND MOMENTS
						IxxA += (dyA * dyA) * dA + IxxSelf;
						IyyA += (dxA * dxA) * dA + IyySelf;
						IxyA += (dxA * dyA) * dA;

						IxxR += densClamped * ((dyR * dyR) * dA + IxxSelf);
						IyyR += densClamped * ((dxR * dxR) * dA + IyySelf);
						IxyR += densClamped * ((dxR * dyR) * dA);

						if (doHeatmaps) {
							// In verbose mode, store per-pixel contributions consistent with the fast path:
							// pMOA contrib = (dx^2+dy^2)*dA + pixelSelfTerm
							// pMOI contrib = dens * ((dx^2+dy^2)*dA + pixelSelfTerm)
							final double baseA = (dxA * dxA + dyA * dyA) * dA + pixelSelfTerm;
							final double baseR = (dxR * dxR + dyR * dyR) * dA + pixelSelfTerm;
							fpA.setf(x, y, (float) baseA);
							fpI.setf(x, y, (float) (densClamped * baseR));
						}
					}
				}
			}

			double IminA = Double.NaN, ImaxA = Double.NaN, thetaA = Double.NaN;
			double IminR = Double.NaN, ImaxR = Double.NaN, thetaR = Double.NaN;

			if (verbose) {
				// Polar is trace of the tensor
				pMOA = IxxA + IyyA;
				pMOI = IxxR + IyyR;

				final double[] pa = principal(IxxA, IyyA, IxyA);
				IminA = pa[0]; ImaxA = pa[1]; thetaA = pa[2];

				final double[] pr = principal(IxxR, IyyR, IxyR);
				IminR = pr[0]; ImaxR = pr[1]; thetaR = pr[2];
			}

			final double meanDens = sumDens / nPix;

			rt.incrementCounter();
			rt.addValue("Slice", z);
			rt.addValue("Ct.Ar (mm^2)", sumA);
			rt.addValue("Xc_geom (mm)", xcA);
			rt.addValue("Yc_geom (mm)", ycA);
			rt.addValue("Xc_dens (mm)", xcR);
			rt.addValue("Yc_dens (mm)", ycR);

			rt.addValue("pMOA (mm^4)", pMOA);
			rt.addValue("MeanDens (mgHA/cm^3)", meanDens);
			rt.addValue("pMOI ((mgHA/cm^3)*mm^4)", pMOI);

			if (verbose) {
				rt.addValue("Ixx_pMOA (mm^4)", IxxA);
				rt.addValue("Iyy_pMOA (mm^4)", IyyA);
				rt.addValue("Ixy_pMOA (mm^4)", IxyA);
				rt.addValue("Imin_pMOA (mm^4)", IminA);
				rt.addValue("Imax_pMOA (mm^4)", ImaxA);
				rt.addValue("theta_pMOA (deg)", Math.toDegrees(thetaA));

				rt.addValue("Ixx_pMOI ((mgHA/cm^3)*mm^4)", IxxR);
				rt.addValue("Iyy_pMOI ((mgHA/cm^3)*mm^4)", IyyR);
				rt.addValue("Ixy_pMOI ((mgHA/cm^3)*mm^4)", IxyR);
				rt.addValue("Imin_pMOI ((mgHA/cm^3)*mm^4)", IminR);
				rt.addValue("Imax_pMOI ((mgHA/cm^3)*mm^4)", ImaxR);
				rt.addValue("theta_pMOI (deg)", Math.toDegrees(thetaR));
			}

			if (sawNegDensity) {
				IJ.log("Warning (slice " + z + "): negative densities encountered; clamped to 0 for centroid/pMOI.");
			}

			if (doPlot) {
				sliceSeries[outIdx] = z;
				pmoaSeries[outIdx] = pMOA;
				pmoiSeries[outIdx] = pMOI;
				outIdx++;
			}

			if (doHeatmaps) {
				pmoaStack.addSlice("pMOA_" + z, fpA);
				pmoiStack.addSlice("pMOI_" + z, fpI);
			}
		}

		rt.show("PolarMoments2D");
		IJ.showStatus("Polar moments complete.");
		IJ.showProgress(1.0);

		// Plotting
		if (doPlot && sliceSeries != null) {
			final Plot p1 = new Plot("pMOA (slice-wise)", "Slice", "pMOA (mm^4)", sliceSeries, pmoaSeries);
			p1.show();

			final Plot p2 = new Plot("pMOI (slice-wise)", "Slice", "pMOI ((mgHA/cm^3)*mm^4)", sliceSeries, pmoiSeries);
			p2.show();
		}

		// Heatmap stacks
		if (doHeatmaps && pmoaStack != null && pmoiStack != null) {
			final ImagePlus pmoaImp = new ImagePlus("pMOA_contrib_stack_" + imp.getTitle(), pmoaStack);
			final ImagePlus pmoiImp = new ImagePlus("pMOI_contrib_stack_" + imp.getTitle(), pmoiStack);

			// Apply display ranges if requested
			if (manualRanges) {
				pmoaImp.setDisplayRange(pmoaVmin, pmoaVmax);
				pmoiImp.setDisplayRange(pmoiVmin, pmoiVmax);
			}

			// Apply a reasonable LUT (works in Fiji; harmless if LUT missing)
			try {
				IJ.run(pmoaImp, "Fire", "");
				IJ.run(pmoiImp, "Fire", "");
			} catch (final Exception ignored) {
				// no-op
			}

			pmoaImp.show();
			pmoiImp.show();
		}
	}
}
