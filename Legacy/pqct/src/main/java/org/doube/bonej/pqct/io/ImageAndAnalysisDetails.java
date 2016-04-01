/*
 * #%L
 * PQCT: ImageJ density distribution analysis plugin.
 * %%
 * Copyright (C) 2007 - 2016 Timo Rantalainen, Michael Doube, BoneJ developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package org.doube.bonej.pqct.io;

public class ImageAndAnalysisDetails {
	public boolean flipHorizontal;
	public boolean flipVertical;
	public boolean noFiltering;
	public boolean sleeveOn;
	public double scalingFactor;
	public double constant;

	public double airThreshold; // Fat lower threshold
	public double fatThreshold; // Fat higher threshold
	public double muscleThreshold; // Muscle lower threshold
	public double marrowThreshold; // Marrow higher threshold
	public double softThreshold; // Soft tissues higher threshold
	public double areaThreshold; // For cortical AREA analyses (CoA, SSI, I) +
									// peeling distal pixels
	public double rotationThreshold;
	public double BMDthreshold; // For cortical BMD analyses
	public double boneThreshold; // Thresholding bone from the rest and cortical
									// AREA analyses (CoA, SSI, I)

	public boolean cOn; // Basic analyses
	public boolean mOn; // Mass distribution
	public boolean conOn; // Concentric rings analysis
	public boolean dOn; // Distribution analysis
	public boolean stOn; // Soft tissue analysis
	public boolean alphaOn; // Rotation angle

	public int filterSize;
	public int softFilterSize;
	public int sectorWidth;
	public int divisions;
	public int concentricSector;
	public int concentricDivisions;
	public String imageSavePath;
	public String roiChoice;
	public String roiChoiceSt;
	public String rotationChoice;
	public String[] choiceLabels;
	public String[] rotationLabels;
	public boolean preventPeeling;
	public boolean allowCleaving;
	public boolean suppressImages;
	public boolean manualRoi;
	public boolean manualRotation;
	public double manualAlfa;
	public boolean flipDistribution;
	public boolean guessFlip;
	public boolean guessRight;
	public boolean guessLarger;
	public boolean stacked;
	public boolean guessStacked;
	public boolean invertGuess;
	public boolean saveImageOnDisk;

	// ImageJ plugin constructor
	public ImageAndAnalysisDetails(final boolean[] defaultTopValues, final double[] thresholdsAndScaling,
			final String[] alignmentStrings, final String[] choiceLabels, final String[] rotationLabels,
			final boolean[] middleDefaults, final double manualAlfa, final boolean[] bottomDefaults,
			final int[] sectorsAndDivisions, final int[] filterSizes) {
		/* Top booleans */
		int i = 0;
		this.flipHorizontal = defaultTopValues[i];
		++i;
		this.flipVertical = defaultTopValues[i];
		++i;
		this.noFiltering = defaultTopValues[i];
		++i;
		this.sleeveOn = defaultTopValues[i];
		++i;

		/* Thresholds and scaling */
		i = 0;
		this.airThreshold = thresholdsAndScaling[i];
		++i;
		this.fatThreshold = thresholdsAndScaling[i];
		++i;
		this.muscleThreshold = thresholdsAndScaling[i];
		++i;
		this.marrowThreshold = thresholdsAndScaling[i];
		++i;
		this.softThreshold = thresholdsAndScaling[i];
		++i; // Thresholding soft tissues + marrow from bone
		this.rotationThreshold = thresholdsAndScaling[i];
		++i;
		this.areaThreshold = thresholdsAndScaling[i];
		++i; // For cortical AREA analyses (CoA, SSI, I) + peeling distal pixels
		this.BMDthreshold = thresholdsAndScaling[i];
		++i; // For cortical BMD analyses
		this.scalingFactor = thresholdsAndScaling[i];
		++i;
		this.constant = thresholdsAndScaling[i];
		++i;
		this.boneThreshold = areaThreshold;

		/* Alignment */
		i = 0;
		this.roiChoice = alignmentStrings[i];
		++i;
		this.roiChoiceSt = alignmentStrings[i];
		++i;
		this.rotationChoice = alignmentStrings[i];
		++i;
		this.choiceLabels = choiceLabels;
		this.rotationLabels = rotationLabels;

		/* Middle defaults */
		i = 0;
		this.cOn = middleDefaults[i];
		++i; // Basic analyses
		this.mOn = middleDefaults[i];
		++i; // Mass distribution
		this.conOn = middleDefaults[i];
		++i; // Concentric rings analysis
		this.dOn = middleDefaults[i];
		++i; // Distribution analysis
		this.stOn = middleDefaults[i];
		++i; // Soft tissue analysis
		this.preventPeeling = middleDefaults[i];
		++i;
		this.allowCleaving = middleDefaults[i];
		++i;
		this.suppressImages = middleDefaults[i];
		++i;
		this.manualRoi = middleDefaults[i];
		++i;
		this.manualRotation = middleDefaults[i];
		++i;

		/* Manual alpha */
		this.manualAlfa = manualAlfa;

		/* Bottom defaults */
		i = 0;
		this.guessFlip = bottomDefaults[i];
		++i;
		this.guessRight = bottomDefaults[i];
		++i;
		this.guessLarger = bottomDefaults[i];
		++i;
		this.stacked = bottomDefaults[i];
		++i;
		this.guessStacked = bottomDefaults[i];
		++i;
		this.invertGuess = bottomDefaults[i];
		++i;
		this.flipDistribution = bottomDefaults[i];
		++i;
		this.saveImageOnDisk = bottomDefaults[i];
		++i;

		/* Sectors and divisions */
		i = 0;
		this.sectorWidth = sectorsAndDivisions[i];
		++i;
		this.divisions = sectorsAndDivisions[i];
		++i;
		this.concentricSector = sectorsAndDivisions[i];
		++i;
		this.concentricDivisions = sectorsAndDivisions[i];
		++i;

		/* Median filter sizes */
		i = 0;
		this.filterSize = filterSizes[i];
		++i;
		this.softFilterSize = filterSizes[i];
		++i;

		/* Can't remember whether this is needed... */
		this.imageSavePath = new String("");
	}
}
