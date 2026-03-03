/*-
 * #%L
 * High-level BoneJ2 commands.
 * %%
 * Copyright (C) 2015 - 2025 Michael Doube, BoneJ developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */


package org.bonej.wrapperPlugins;

import static org.bonej.wrapperPlugins.CommonMessages.NOT_BINARY;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;
import static org.bonej.wrapperPlugins.CommonMessages.WEIRD_SPATIAL;
import static org.bonej.wrapperPlugins.wrapperUtils.Common.cancelMacroSafe;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.ops.OpService;
import net.imagej.units.UnitService;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;


import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.RoiManagerUtil;
import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.ResultUtils;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

//import ij.plugin.frame.RoiManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * This command estimates the size of the given sample by counting its
 * foreground elements, and the whole stack by counting all the elements (bone)
 * and the whole image stack. In the case of a 2D image "size" refers to areas,
 * and in 3D volumes. The plugin displays the sizes and their ratio in the
 * results table. Results are shown in calibrated units, if possible.
 *
 * @author Richard Domander
 * @author Michael Doube
 */
@Plugin(type = Command.class,
	menuPath = "Plugins>BoneJ>Fraction>Area/Volume fraction")
public class ElementFractionWrapper<T extends RealType<T> & NativeType<T>> extends BoneJCommand
{

	@Parameter(validater = "validateImage")
	private ImgPlus<T> inputImage;
	@Parameter
	private OpService opService;
	@Parameter
	private UnitService unitService;
	@Parameter
	private StatusService statusService;

	/** Header of the foreground (bone) volume column in the results table */
	private String boneSizeHeader;
	/** Header of the total volume column in the results table */
	private String totalSizeHeader;
	/** Header of the size ratio column in the results table */
	private String ratioHeader;
	/** The calibrated size of an element in the image */
	private double elementSize;
	

	@Override
	public void run() {
		statusService.showStatus("Element fraction: initializing");
		prepareResultDisplay();
		final String name = inputImage.getName();
		
		//get the number of slices, channels and time points to iterate over
        int zIdx = axisIndex(inputImage, Axes.Z);
        int tIdx = axisIndex(inputImage, Axes.TIME);
        int cIdx = axisIndex(inputImage, Axes.CHANNEL);
//        System.out.println("axisIndex (w,h,d,t,c) = ("+xIdx+", "+yIdx+", "+zIdx+", "+tIdx+", "+cIdx+")");
        
        //if an axis is missing, set its size to 1, otherwise get its size.
        int cSize = (cIdx >= 0) ? (int) inputImage.dimension(cIdx) : 1;
        int zSize = (zIdx >= 0) ? (int) inputImage.dimension(zIdx) : 1;
        int tSize = (tIdx >= 0) ? (int) inputImage.dimension(tIdx) : 1;

		long fg = 0;
		long total = 0;
        //iterate over all the timepoints and channels, and for each iterate over z.
		long start = System.nanoTime();
		
        for (int t = 0; t < tSize; t++) {
        	final int time = t;
        	for (int c = 0; c < cSize; c++) {
        		final int channel = c;    	        
        		for (int z = 0; z < zSize; z++) {
        			statusService.showStatus("Element fraction: channel "+c+", time "+t+", z "+z);

        			//create a 2D view into the data
        			RandomAccessibleInterval<T> xyView = get2DSlice(inputImage, z, time, channel);

        			//If the ROI Manager contains ROIs, use them
        			if (!RoiManagerUtil.roiManagerIsEmpty()) {

        				//get a mask for this xyView from ROIs in the ROI Manager
        				RandomAccessibleInterval<BitType> mask = 
        						RoiManagerUtil.unionMaskFromRoiManager(xyView, z + 1, t + 1, c + 1);

        				//don't process slices that lack a mask
        				if (mask == null) continue;

        				//Iterate over the mask and the slice
        				Cursor<T> sliceCursor = Views.flatIterable(xyView).cursor();
        				Cursor<BitType> maskCursor = Views.flatIterable(mask).cursor();

        				while (maskCursor.hasNext()) {
        					maskCursor.fwd();
        					sliceCursor.fwd();
        					//if we are inside an ROI
        					if (maskCursor.get().get()) {
        						total++;
        						final double v = sliceCursor.get().getRealDouble();
        						//if foreground
        						if (v == 255.0) {
        							fg++;
        						} else if (v != 0.0) {
        							cancelMacroSafe(this, NOT_BINARY);
        						}
        					}
        				}
        			//Otherwise process all pixels in the image
        			} else {
        				Cursor<T> sliceCursor = Views.flatIterable(xyView).cursor();
        				while (sliceCursor.hasNext()) {
        					sliceCursor.fwd();
        					total++;
        					final double v = sliceCursor.get().getRealDouble();
    						//if foreground
    						if (v == 255.0) {
    							fg++;
    						} else if (v != 0.0) {
    							cancelMacroSafe(this, NOT_BINARY);
    						}
        				}
        			}
        		}
    			
        		long end = System.nanoTime();
        		
        		System.out.println("Volume fraction took "+(end-start) / 1E6+" ms");
        		
        		//don't show any results for cancelled plugins
    			if (this.isCanceled()) return;

                double tv = total * elementSize;
                double bv = fg * elementSize;
        
                String label = name;
                
                //add a channel suffix if there is more than one channel
                label = cSize > 1 ? label + " Channel: " + c : label; 
                //add a comma between channel and timepoint if both are more than one
                label = (cSize > 1 && tSize > 1) ? label +"," : label;
                //add a time suffix if there is more than one timepoint
                label = tSize > 1 ? label + " Time: " + t : label;
                
        		addResults(label, bv, tv, bv/tv);
   		
        	}
        }
		
		resultsTable = SharedTable.getTable();
	}

	private void addResults(final String label, final double foregroundSize,
		final double totalSize, final double ratio)
	{
		SharedTable.add(label, boneSizeHeader, foregroundSize);
		SharedTable.add(label, totalSizeHeader, totalSize);
		SharedTable.add(label, ratioHeader, ratio);
	}

	// region -- Helper methods --
	private void prepareResultDisplay() {
		final char exponent = ResultUtils.getExponent(inputImage);
		final String unitHeader = ResultUtils.getUnitHeader(inputImage, unitService,
			String.valueOf(exponent));
		final String sizeDescription = ResultUtils.getSizeDescription(inputImage);

		boneSizeHeader = "B" + sizeDescription + " " + unitHeader;
		totalSizeHeader = "T" + sizeDescription + " " + unitHeader;
		ratioHeader = "B" + sizeDescription + "/T" + sizeDescription;
		elementSize = ElementUtil.calibratedSpatialElementSize(inputImage,
			unitService);

	}

	@SuppressWarnings("unused")
	private void validateImage() {
		if (inputImage == null) {
			cancelMacroSafe(this, NO_IMAGE_OPEN);
			return;
		}

		final long spatialDimensions = AxisUtils.countSpatialDimensions(inputImage);
		if (spatialDimensions < 2 || spatialDimensions > 3) {
			inputImage = null;
			cancelMacroSafe(this, WEIRD_SPATIAL);
			return;
		}

		T type = inputImage.firstElement();
		//enforce 8-bit (IJ1 binary is 0 and 255)
		if (type instanceof UnsignedByteType)
			return;
		else {
			System.out.println("Cancelled non-binary at initial validation");
			inputImage = null;
			cancelMacroSafe(this, NOT_BINARY);
			return;
		}
	}
	
	/** Return the index of the given axis in the ImgPlus, or -1 if absent. */
    private static int axisIndex(ImgPlus<?> img, AxisType axis) {
        for (int d = 0; d < img.numDimensions(); d++) {
            if (img.axis(d).type().equals(axis)) return d;
        }
        return -1;
    }
    
    public static <T> RandomAccessibleInterval<T> get2DSlice(ImgPlus<T> img, int z, int t, int c) {
        // Get the indices of Z, TIME, and CHANNEL
        int zIdx = axisIndex(img, Axes.Z);
        int tIdx = axisIndex(img, Axes.TIME);
        int cIdx = axisIndex(img, Axes.CHANNEL);

        // Collect all non-spatial dimensions (Z, TIME, CHANNEL) and their target slice indices
        List<DimensionSlice> dimensionsToSlice = new ArrayList<>();
        if (zIdx >= 0 && img.dimension(zIdx) > 1) dimensionsToSlice.add(new DimensionSlice(zIdx, z));
        if (tIdx >= 0 && img.dimension(tIdx) > 1) dimensionsToSlice.add(new DimensionSlice(tIdx, t));
        if (cIdx >= 0 && img.dimension(cIdx) > 1) dimensionsToSlice.add(new DimensionSlice(cIdx, c));

        // Sort dimensions by index in descending order
        Collections.sort(dimensionsToSlice, Comparator.comparingInt(ds -> -ds.index));

        // Slice along dimensions in descending order of their indices
        RandomAccessibleInterval<T> view = img;
        for (DimensionSlice ds : dimensionsToSlice) {
            view = Views.hyperSlice(view, ds.index, ds.slice);
        }

        // The result is now a 2D XY slice
        return view;
    }
    
    // Helper class to store dimension index and slice index
    private static class DimensionSlice {
        int index;
        int slice;

        DimensionSlice(int index, int slice) {
            this.index = index;
            this.slice = slice;
        }
    }
}
