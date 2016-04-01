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

package org.doube.bonej.pqct.selectroi;

//Vector, Collections
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Vector;

//image data
import org.doube.bonej.pqct.io.ImageAndAnalysisDetails;
import org.doube.bonej.pqct.io.ScaledImageData;

//ImagePlus
import ij.ImagePlus;

@SuppressWarnings(value = { "serial", "unchecked" }) // Unchecked for obtaining
														// Vector<Object> as a
														// returnvalue

public abstract class RoiSelector {
	public ImageAndAnalysisDetails details;
	public double[] scaledImage;
	public double[] softScaledImage;
	public double[] cortexROI;
	public double minimum;
	public double maximum;
	public Vector<Integer> iit; // indexes for x-coordinates
	public Vector<Integer> jiit; // indexes for y-coordinates
	public Vector<Integer> roiI;
	public Vector<Integer> roiJ;

	public Vector<Integer> boneMarrowRoiI;
	public Vector<Integer> boneMarrowRoiJ;
	public Vector<Integer> cortexRoiI; // For BMD analyses
	public Vector<Integer> cortexRoiJ; // For BMD analyses
	public Vector<Integer> cortexAreaRoiI; // For AREA analyses
	public Vector<Integer> cortexAreaRoiJ; // For AREA analyses
	public Vector<Integer> area;
	public Vector<Integer> length;
	public Vector<Integer> beginnings;

	public int height;
	public int width;
	public int selection;

	public double marrowThreshold;
	public double airThreshold;
	public double fatThreshold;
	public double rotationThreshold;
	public double muscleThreshold;
	public double areaThreshold; // For cortical AREA analyses (CoA, SSI, I) +
									// peeling distal pixels
	public double BMDthreshold; // For cortical BMD analyses
	public double softThreshold; // Thresholding soft tissues + marrow from bone
	public double boneThreshold; // Thresholding bone from the rest and cortical
									// AREA analyses (CoA, SSI, I)

	public double pixelSpacing;
	public byte[] result; // Will contain filled bones
	public byte[] sieve;
	public byte[] softSieve; // Mask for soft tissues
	public ScaledImageData scaledImageData;

	public String imageSaveName;
	public String imageSavePath;
	public ImagePlus imp;
	public int bmcAlfaIndex = 0;

	public RoiSelector(final ScaledImageData dataIn, final ImageAndAnalysisDetails detailsIn, final ImagePlus imp,
			final double boneThreshold, final boolean setRoi) {
		this.scaledImageData = dataIn;
		this.imp = imp;
		details = detailsIn;
		scaledImage = dataIn.scaledImage.clone();
		softScaledImage = dataIn.softScaledImage.clone();
		pixelSpacing = dataIn.pixelSpacing;
		imageSavePath = details.imageSavePath;
		width = dataIn.width;
		height = dataIn.height;

		airThreshold = details.airThreshold;
		fatThreshold = details.fatThreshold;
		rotationThreshold = details.rotationThreshold;
		muscleThreshold = details.muscleThreshold;
		marrowThreshold = details.marrowThreshold;
		areaThreshold = details.areaThreshold; // For cortical AREA analyses
												// (CoA, SSI, I) + peeling
												// distal pixels
		BMDthreshold = details.BMDthreshold; // For cortical BMD analyses
		softThreshold = details.softThreshold; // Thresholding soft tissues +
												// marrow from bone
		this.boneThreshold = boneThreshold;
		minimum = dataIn.minimum;
		maximum = dataIn.maximum;
	}

	/*
	 * A function to get rid of the measurement tube used at UKK-institute with
	 * Stratex XCT3000 device. Needed for soft tissue analysis
	 */
	public byte[] removeSleeve(final double[] scaledImage, byte[] sleeve, final double sleeveThreshold) {
		int i, j;
		i = 10;
		j = 10;
		while ((j < height - 12 && i < width - 11 && scaledImage[i + j * width] < sleeveThreshold)
				|| scaledImage[i + j * width] == 0) {
			i++;
			if (i == width - 11) {
				j++;
				if (j >= height - 12)
					break;
				i = 10;
			}
		}
		// Sleeve found
		sleeve = new byte[width * height];
		final Vector<Integer> initialI = new Vector<Integer>();
		final Vector<Integer> initialJ = new Vector<Integer>();
		initialI.add(i);
		initialJ.add(j);
		while (initialI.size() > 0 && initialI.lastElement() > 0 && initialI.lastElement() < width - 1
				&& initialJ.lastElement() > 0 && initialJ.lastElement() < height - 1) {
			i = initialI.lastElement();
			j = initialJ.lastElement();
			initialI.remove(initialI.size() - 1);
			initialJ.remove(initialJ.size() - 1);
			if (scaledImage[i + j * width] > sleeveThreshold && sleeve[i + j * width] == 0) {
				sleeve[i + j * width] = 1;
			}

			if (scaledImage[i - 1 + j * width] > sleeveThreshold && sleeve[i - 1 + j * width] == 0) {
				initialI.add(new Integer(i - 1));
				initialJ.add(new Integer(j));
			}

			if (scaledImage[i + 1 + j * width] > sleeveThreshold && sleeve[i + 1 + j * width] == 0) {
				initialI.add(new Integer(i + 1));
				initialJ.add(new Integer(j));
			}

			if (scaledImage[i + (j - 1) * width] > sleeveThreshold && sleeve[i + (j - 1) * width] == 0) {
				initialI.add(new Integer(i));
				initialJ.add(new Integer(j - 1));
			}

			if (scaledImage[i + (j + 1) * width] > sleeveThreshold && sleeve[i + (j + 1) * width] == 0) {
				initialI.add(new Integer(i));
				initialJ.add(new Integer(j + 1));
			}

		}
		sleeve = dilate(sleeve, (byte) 1, (byte) 0, (byte) 2);
		sleeve = dilate(sleeve, (byte) 1, (byte) 0, (byte) 2);
		return sleeve;
	}

	public byte[] erode(final byte[] data) {
		// Erode algorithm
		// Modified from the best dilate by one solution taken from
		// http://ostermiller.org/dilate_and_erode.html
		for (int i = 0; i < height; i++) {
			for (int j = 0; j < width; j++) {
				if (data[i * width + j] > 0) {
					if (i > 0 && data[(i - 1) * width + j] == 0 || j > 0 && data[(i) * width + j - 1] == 0
							|| i + 1 < height && data[(i + 1) * width + j] == 0
							|| j + 1 < width && data[(i) * width + j + 1] == 0) {
						data[i * width + j] = 0 - 1;
					} // Erode the pixel if any of the neighborhood pixels is
						// background
				}
			}
		}
		for (int i = 0; i < width * height; i++) {
			if (data[i] < 0) {
				data[i] = 0;
			}
		}
		return data;
	}

	public byte[] dilate(final byte[] data, final byte dilateVal, final byte min, final byte temp) {
		// Dilate algorithm
		// Best dilate by one solution taken from
		// http://ostermiller.org/dilate_and_erode.html
		for (int i = 0; i < height; i++) {
			for (int j = 0; j < width; j++) {
				if (data[i * width + j] == dilateVal) {
					if (i > 0 && data[(i - 1) * width + j] == min) {
						data[(i - 1) * width + j] = temp;
					}
					if (j > 0 && data[(i) * width + j - 1] == min) {
						data[(i) * width + j - 1] = temp;
					}
					if (i + 1 < height && data[(i + 1) * width + j] == min) {
						data[(i + 1) * width + j] = temp;
					}
					if (j + 1 < width && data[(i) * width + j + 1] == min) {
						data[(i) * width + j + 1] = temp;
					}
				}
			}
		}
		for (int i = 0; i < width * height; i++) {
			if (data[i] == temp) {
				data[i] = dilateVal; // Set to proper value here...
			}
		}
		return data;
	}

	/* DetectedEdges */
	public Vector<Object> getSieve(final double[] tempScaledImage, final double boneThreshold, final String roiChoice,
			final boolean guessStacked, final boolean stacked, final boolean guessFlip, final boolean allowCleaving) {
		final Vector<Object> results = findEdge(tempScaledImage, boneThreshold, allowCleaving); // Trace
																								// bone
																								// edges
		result = (byte[]) results.get(0);
		final Vector<DetectedEdge> edges = (Vector<DetectedEdge>) results.get(1);

		/* Select correct bone outline */
		int selection = 0;

		if (roiChoice.equals(details.choiceLabels[0])) {
			selection = selectRoiBiggestBoneDetectedEdges(edges);
		}
		if (roiChoice.equals(details.choiceLabels[1])) {
			selection = selectRoiSmallestBoneDetectedEdges(edges);
		}
		if (roiChoice.equals(details.choiceLabels[2])) {
			selection = selectRoiLeftMostBone(edges);
		}
		if (roiChoice.equals(details.choiceLabels[3])) {
			selection = selectRoiRightMostBone(edges);
		}
		if (roiChoice.equals(details.choiceLabels[4])) {
			selection = selectRoiTopMostBone(edges);
		}
		if (roiChoice.equals(details.choiceLabels[5])) {
			selection = selectRoiBottomMostBone(edges);
		}
		if (roiChoice.equals(details.choiceLabels[6])) {
			selection = selectRoiCentralBone(edges, tempScaledImage, details.fatThreshold);
		}
		if (roiChoice.equals(details.choiceLabels[7])) {
			selection = selectRoiPeripheralBone(edges, tempScaledImage, details.fatThreshold);
		}
		if (roiChoice.equals(details.choiceLabels[8])) {
			selection = selectRoiSecondLargestBoneDetectedEdges(edges);
		}
		if (roiChoice.equals(details.choiceLabels[9])) {
			selection = selectRoiTwoLargestLeft(edges);
		}
		if (roiChoice.equals(details.choiceLabels[10])) {
			selection = selectRoiTwoLargestRight(edges);
		}
		/* Metatarsals and carpals */
		int i = 11;
		/* From Left */
		if (roiChoice.equals(details.choiceLabels[i])) {
			selection = selectRoiFirstNthFromLeft(edges, i - 11);
		}
		i = i + 1;
		if (roiChoice.equals(details.choiceLabels[i])) {
			selection = selectRoiFirstNthFromLeft(edges, i - 11);
		}
		i = i + 1;
		if (roiChoice.equals(details.choiceLabels[i])) {
			selection = selectRoiFirstNthFromLeft(edges, i - 11);
		}
		i = i + 1;
		if (roiChoice.equals(details.choiceLabels[i])) {
			selection = selectRoiFirstNthFromLeft(edges, i - 11);
		}
		i = i + 1;
		if (roiChoice.equals(details.choiceLabels[i])) {
			selection = selectRoiFirstNthFromLeft(edges, i - 11);
		}
		i = i + 1;
		/* From Top */
		if (roiChoice.equals(details.choiceLabels[i])) {
			selection = selectRoiFirstNthFromTop(edges, i - 16);
		}
		i = i + 1;
		if (roiChoice.equals(details.choiceLabels[i])) {
			selection = selectRoiFirstNthFromTop(edges, i - 16);
		}
		i = i + 1;
		if (roiChoice.equals(details.choiceLabels[i])) {
			selection = selectRoiFirstNthFromTop(edges, i - 16);
		}
		i = i + 1;
		if (roiChoice.equals(details.choiceLabels[i])) {
			selection = selectRoiFirstNthFromTop(edges, i - 16);
		}
		i = i + 1;
		if (roiChoice.equals(details.choiceLabels[i])) {
			selection = selectRoiFirstNthFromTop(edges, i - 16);
		}
		i = i + 1;
		/* Debugging */
		/*
		 * for (int i = 0; i<edges.size();++i){ System.out.println("RoiArea "
		 * +edges.get(i).area); } System.out.println("RoiChoice "+roiChoice+
		 * " selection "+selection+" lengthEdges "+edges.size());
		 *
		 * System.out.println("RoiChoice "+roiChoice+" selection "+selection+
		 * " lengthEdges "+edges.size());
		 */
		// IJ.error(roiChoice + " selection "+selection);
		// Try to guess whether the bones were stacked or not....
		if (guessStacked) {
			final int[] guessingStack = twoLargestBonesDetectedEdges(edges);

			if (Math.abs((double) edges.get(guessingStack[0]).jiit.get(0)
					- (double) edges.get(guessingStack[1]).jiit.get(1)) > 1.1
							* Math.abs((double) edges.get(guessingStack[0]).iit.get(0)
									- (double) edges.get(guessingStack[1]).iit.get(1))) {
				details.stacked = true;
			} else {
				details.stacked = false;
			}
			// IJ.log("Guessing Stacked
			// "+Math.abs((double)edges.get(guessingStack[0]).jiit.get(0)-
			// (double)edges.get(guessingStack[1]).jiit.get(1)) +"
			// "+(1.1*Math.abs((double)edges.get(guessingStack[0]).iit.get(0)-
			// (double)edges.get(guessingStack[1]).iit.get(1)))+" onko
			// "+(Math.abs((double)edges.get(guessingStack[0]).jiit.get(0)-
			// (double)edges.get(guessingStack[1]).jiit.get(1))>1.1*Math.abs((double)edges.get(guessingStack[0]).iit.get(0)-
			// (double)edges.get(guessingStack[1]).iit.get(1)))+" paatos
			// "+details.stacked);
		}

		/* Try to guess whether to flip the distribution */
		if (guessFlip) {
			if (details.guessLarger) {
				details.flipDistribution = guessFlipLarger(edges, stacked);
			} else {
				details.flipDistribution = guessFlipSelection(edges, selection, stacked);
			}
			if (details.invertGuess) { // Flip flip, if roiChoice is smaller or
										// second Largest
				details.flipDistribution = !details.flipDistribution;
			}
		}

		/* fill roiI & roiJ */

		final byte[] tempSieve = fillSieve(edges.get(selection).iit, edges.get(selection).jiit, width, height,
				tempScaledImage, boneThreshold);
		final Vector<Object> returnVector = new Vector<Object>();
		returnVector.add(tempSieve);
		returnVector.add(result);
		returnVector.add(edges);
		returnVector.add(new Integer(selection));
		return returnVector;
	}

	/* DetectedEdge */
	public int[] twoLargestBonesRetainOrderDetectedEdges(final Vector<DetectedEdge> edges) {
		// Identify the two longest circumferences
		final Vector<DetectedEdge> temp3 = new Vector<DetectedEdge>();
		for (int iii = 0; iii < edges.size(); ++iii) {
			temp3.add(edges.get(iii));
		}
		Collections.sort(temp3);
		int counter = 0;
		final int[] twoLongest = new int[2];
		while (edges.get(counter).area != temp3.get(temp3.size() - 1).area) {
			++counter;
		}
		twoLongest[0] = counter;
		counter = 0;
		if (temp3.size() > 1) {
			while (edges.get(counter).area != temp3.get(temp3.size() - 2).area || counter == twoLongest[0]) {
				++counter;
			}
			twoLongest[1] = counter;
		} else {
			twoLongest[1] = 0;
		}
		Arrays.sort(twoLongest);
		return twoLongest;
	}

	/* DetectedEdge */
	public int[] twoLargestBonesDetectedEdges(final Vector<DetectedEdge> edges) {
		// Identify the two longest circumferences
		final Vector<DetectedEdge> temp3 = new Vector<DetectedEdge>();
		for (int iii = 0; iii < edges.size(); ++iii) {
			temp3.add(edges.get(iii));
		}
		Collections.sort(temp3);
		int counter = 0;
		final int[] twoLongest = new int[2];
		while (edges.get(counter).area != temp3.get(temp3.size() - 1).area) {
			++counter;
		}
		twoLongest[0] = counter;
		counter = 0;
		if (temp3.size() > 1) {
			while (edges.get(counter).area != temp3.get(temp3.size() - 2).area || counter == twoLongest[0]) {
				++counter;
			}
			twoLongest[1] = counter;
		} else {
			twoLongest[1] = 0;
		}
		return twoLongest;
	}

	/* DetectedEdge */
	boolean guessFlipLarger(final Vector<DetectedEdge> edges, final boolean stacked) {
		final Vector<Integer> temp = new Vector<Integer>();
		final Vector<Integer> temp2 = new Vector<Integer>();
		// IJ.log("Checking number of edges");
		for (int iii = 0; iii < edges.size(); iii++) {
			temp.add(edges.get(iii).area);
			temp2.add(edges.get(iii).area);
		}
		Collections.sort(temp);

		final int[] counter = { 0, 0 };
		// IJ.log("Sorting edges c "+counter[0]+" t2 "+temp2.get(counter[0])+"
		// tMax "+temp.get(temp.size()-1)+" true "+(temp2.get(counter[0]) !=
		// temp.get(temp.size()-1)));
		while ((temp2.get(counter[0])) != (temp.get(temp.size() - 1))) {
			++counter[0];
			// IJ.log("Sorting edges c "+counter[0]+" t2
			// "+temp2.get(counter[0])+" tMax "+temp.get(temp.size()-1)+" true
			// "+(temp2.get(counter[0]) !=temp.get(temp.size()-1)));
		}
		// IJ.log("Found largest");
		boolean returnValue = false;
		if (temp.size() > 1) {
			while ((temp2.get(counter[1])) != (temp.get(temp.size() - 2))) {
				++counter[1];
			}
			if (stacked) {
				// IJ.log("Decision
				// "+(((int)edges.get(counter[0]).jiit.get(0))<((int)edges.get(counter[1]).jiit.get(0))));
				if ((edges.get(counter[0]).jiit.get(0)) < (edges.get(counter[1]).jiit.get(0))) {
					returnValue = false;
				} else {
					returnValue = true;
				}
			} else {
				if ((edges.get(counter[0]).iit.get(0)) < (edges.get(counter[1]).iit.get(0))) {
					returnValue = false;
				} else {
					returnValue = true;
				}
			}
		}
		// IJ.log("Done with largest "+returnValue);
		// IJ.error("RV "+returnValue+" c0
		// "+iit.get(beginning.get(counter[0]))+" c1
		// "+iit.get(beginning.get(counter[1])));
		return returnValue;
	}

	/* DetectedEdge Only two biggest bone will be considered.. */
	boolean guessFlipSelection(final Vector<DetectedEdge> edges, final int selection, final boolean stacked) {

		final int[] considered = twoLargestBonesDetectedEdges(edges);
		if (selection != considered[0] && selection != considered[1]) { // selection
																		// is
																		// not
																		// the
																		// biggest
																		// or
																		// the
																		// second
																		// biggest
																		// bone
																		// ->
																		// can't
																		// make
																		// a
																		// guess,
																		// return
																		// false
			// IJ.error("Aborted guess..."+" select "+selection+" con0
			// "+considered[0]+" con1 "+considered[1]);
			return false;
		}

		final int[] possibleCoords = new int[2];
		int selectionCoord = 0;
		if (stacked) {
			selectionCoord = edges.get(selection).jiit.get(0);
		} else {
			selectionCoord = edges.get(selection).iit.get(0);
		}
		for (int i = 0; i < 2; ++i) {
			if (stacked) {
				possibleCoords[i] = edges.get(considered[i]).jiit.get(0);
			} else {
				possibleCoords[i] = edges.get(considered[i]).iit.get(0);
			}
		}

		boolean returnValue = false;
		if (selection == considered[0]) {
			if (selectionCoord > possibleCoords[1]) {
				returnValue = true;
			}
		}
		if (selection == considered[1]) {
			if (selectionCoord > possibleCoords[0]) {
				returnValue = true;
			}
		}
		// IJ.error("Select RV "+returnValue+" s0 "+selectionCoord+" c0
		// "+possibleCoords[0]+" c1 "+possibleCoords[1]+" select "+selection+"
		// con0 "+considered[0]+" con1 "+considered[1]);
		return returnValue;
	}

	/* DetectedEdge */
	private int selectRoiBiggestBoneDetectedEdges(final Vector<DetectedEdge> edges) {
		int counter = 0;
		int maxArea = 0;
		int maxPos = 0;

		final Iterator<DetectedEdge> it = edges.iterator();
		while (it.hasNext()) {
			final int a = it.next().area;
			if (a > maxArea) {
				maxArea = a;
				maxPos = counter;
			}
			counter++;
		}

		return maxPos;
	}

	/* DetectedEdge */
	int selectRoiSecondLargestBoneDetectedEdges(final Vector<DetectedEdge> edges) {
		if (edges.size() < 2) {
			return 0;
		}
		final Vector<DetectedEdge> temp = new Vector<DetectedEdge>();
		for (int iii = 0; iii < edges.size(); ++iii) {
			temp.add(edges.get(iii));
		}
		Collections.sort(temp);
		int counter = 0;
		while (edges.get(counter).area != temp.get(temp.size() - 2).area) { // Select
																			// second
																			// largest
			++counter;
		}
		return counter;
	}

	/* DetectedEdge */
	int selectRoiSmallestBoneDetectedEdges(final Vector<DetectedEdge> edges) {
		final Vector<DetectedEdge> temp = new Vector<DetectedEdge>();
		for (int iii = 0; iii < edges.size(); ++iii) {
			temp.add(edges.get(iii));
		}
		Collections.sort(temp);
		int counter = 0;
		while (edges.get(counter).area != temp.get(0).area) { // Select smallest
			++counter;
		}
		return counter;
	}

	/* DetectedEdge */
	int selectRoiTwoLargestLeft(final Vector<DetectedEdge> edges) {
		if (edges.size() < 2) {
			return 0;
		} // In case only one ROI has been found..
		final int[] twoBones = twoLargestBonesRetainOrderDetectedEdges(edges);
		final Vector<DetectedEdge> tempEdges = new Vector<DetectedEdge>();
		for (int i = 0; i < twoBones.length; ++i) {
			tempEdges.add(edges.get(twoBones[i]));
		}

		final int tempSelection = selectRoiLeftMostBone(tempEdges);
		return twoBones[tempSelection];
	}

	/* DetectedEdge */
	int selectRoiTwoLargestRight(final Vector<DetectedEdge> edges) {
		if (edges.size() < 2) {
			return 0;
		} // In case only one ROI has been found..
		final int[] twoBones = twoLargestBonesRetainOrderDetectedEdges(edges);
		final Vector<DetectedEdge> tempEdges = new Vector<DetectedEdge>();
		for (int i = 0; i < twoBones.length; ++i) {
			tempEdges.add(edges.get(twoBones[i]));
		}

		final int tempSelection = selectRoiRightMostBone(tempEdges);
		return twoBones[tempSelection];
	}

	/* DetectedEdge */
	int selectRoiLeftMostBone(final Vector<DetectedEdge> edges) {
		final Vector<Integer> temp = new Vector<Integer>();
		final Vector<Integer> temp2 = new Vector<Integer>();
		for (int iii = 0; iii < edges.size(); iii++) {
			temp.add(edges.get(iii).iit.get(0));
			temp2.add(edges.get(iii).iit.get(0));
		}
		Collections.sort(temp);
		int counter = 0;
		while (temp2.get(counter) != temp.get(0)) {
			++counter;
		}
		return counter;
	}

	/* DetectedEdge , indexing from 0 */
	int selectRoiFirstNthFromLeft(final Vector<DetectedEdge> edges, final int nth) {
		final Vector<Integer> temp = new Vector<Integer>();
		final Vector<Integer> temp2 = new Vector<Integer>();
		for (int iii = 0; iii < edges.size(); iii++) {
			temp.add(edges.get(iii).iit.get(0));
			temp2.add(edges.get(iii).iit.get(0));
		}
		Collections.sort(temp);
		int counter = 0;
		while (temp2.get(counter) != temp.get(nth)) {
			++counter;
		}
		return counter;
	}

	/* DetectedEdge */
	int selectRoiRightMostBone(final Vector<DetectedEdge> edges) {
		final Vector<Integer> temp = new Vector<Integer>();
		final Vector<Integer> temp2 = new Vector<Integer>();
		for (int iii = 0; iii < edges.size(); iii++) {
			temp.add(edges.get(iii).iit.get(0));
			temp2.add(edges.get(iii).iit.get(0));
		}
		Collections.sort(temp);
		int counter = 0;
		while (temp2.get(counter) != temp.get(temp.size() - 1)) {
			++counter;
		}
		return counter;
	}

	/* DetectedEdge, indexing from 0 */
	int selectRoiFirstNthFromTop(final Vector<DetectedEdge> edges, final int nth) {
		final Vector<Integer> temp = new Vector<Integer>();
		final Vector<Integer> temp2 = new Vector<Integer>();
		for (int iii = 0; iii < edges.size(); iii++) {
			temp.add(edges.get(iii).jiit.get(0));
			temp2.add(edges.get(iii).jiit.get(0));
		}
		Collections.sort(temp);
		int counter = 0;
		while (temp2.get(counter) != temp.get(nth)) {
			++counter;
		}
		return counter;
	}

	/* DetectedEdge */
	int selectRoiTopMostBone(final Vector<DetectedEdge> edges) {
		final Vector<Integer> temp = new Vector<Integer>();
		final Vector<Integer> temp2 = new Vector<Integer>();
		for (int iii = 0; iii < edges.size(); iii++) {
			temp.add(edges.get(iii).jiit.get(0));
			temp2.add(edges.get(iii).jiit.get(0));
		}
		Collections.sort(temp);
		int counter = 0;
		while (temp2.get(counter) != temp.get(0)) {
			++counter;
		}
		return counter;
	}

	/* DetectedEdge */
	int selectRoiBottomMostBone(final Vector<DetectedEdge> edges) {
		final Vector<Integer> temp = new Vector<Integer>();
		final Vector<Integer> temp2 = new Vector<Integer>();
		for (int iii = 0; iii < edges.size(); iii++) {
			temp.add(edges.get(iii).jiit.get(0));
			temp2.add(edges.get(iii).jiit.get(0));
		}
		Collections.sort(temp);
		int counter = 0;
		while (temp2.get(counter) != temp.get(temp.size() - 1)) {
			++counter;
		}
		return counter;
	}

	/* DetectedEdge */
	int selectRoiCentralBone(final Vector<DetectedEdge> edges, final double[] tempScaledImage,
			final double fatThreshold) {
		final double[] distanceFromCentreOfLimb = calcDistancesFromCentreOfLimb(edges, tempScaledImage, fatThreshold);
		final double[] temp = distanceFromCentreOfLimb.clone();
		Arrays.sort(temp);
		int counter = 0;
		while (distanceFromCentreOfLimb[counter] != temp[0]) {
			++counter;
		}
		return counter;
	}

	/* DetectedEdge */
	int selectRoiPeripheralBone(final Vector<DetectedEdge> edges, final double[] tempScaledImage,
			final double fatThreshold) {
		final double[] distanceFromCentreOfLimb = calcDistancesFromCentreOfLimb(edges, tempScaledImage, fatThreshold);
		final double[] temp = distanceFromCentreOfLimb.clone();
		Arrays.sort(temp);
		int counter = 0;
		while (distanceFromCentreOfLimb[counter] != temp[temp.length - 1]) {
			++counter;
		}
		return counter;
	}

	/* DetectedEdge */
	public double[] calcDistancesFromCentreOfLimb(final Vector<DetectedEdge> edges, final double[] tempScaledImage,
			final double fatThreshold) {
		final double[] softPoints = new double[3];
		for (int j = 0; j < 3; ++j) {
			softPoints[j] = 0;
		}
		final Vector<double[]> bones = new Vector<double[]>();
		for (int i = 0; i < edges.size(); ++i) {
			bones.add(new double[3]);
			for (int j = 0; j < 3; ++j) {
				bones.get(i)[j] = 0;
			}
		}
		/* Find the centre of area of the limb */
		final int maxIndice = selectRoiBiggestBoneDetectedEdges(edges);
		final byte[] limbSieve = new byte[tempScaledImage.length];
		limbSieve[edges.get(maxIndice).iit.get(0) + edges.get(maxIndice).jiit.get(0) * width] = 1;
		/* Dilate muscleSieve, into neighbouring fat pixels */
		int tempDil = 1;
		while (tempDil > 0) {
			tempDil = dilateLimb(limbSieve, (byte) 1, (byte) 0, (byte) 4, fatThreshold, tempScaledImage);
		}

		for (int j = 0; j < height; ++j) {
			for (int i = 0; i < width; ++i) {
				if (limbSieve[i + j * width] == (byte) 1) {
					softPoints[0] += i;
					softPoints[1] += j;
					softPoints[2] += 1;
				}
			}
		}
		softPoints[0] /= softPoints[2]; /*
										 * X coordinate of the centre of area of
										 * the limb... (assuming just one limb)
										 */
		softPoints[1] /= softPoints[2]; /*
										 * Y coordinate of the centre of area of
										 * the limb... (assuming just one limb)
										 */
		/* Find the centres of circumference of the bones */
		final double[] distanceFromCentreOfLimb = new double[edges.size()];
		for (int i = 0; i < edges.size(); ++i) {
			for (int j = 0; j < edges.get(i).iit.size(); j++) {
				bones.get(i)[0] += edges.get(i).iit.get(j);
				bones.get(i)[1] += edges.get(i).jiit.get(j);
				bones.get(i)[2] += 1;
			}
			bones.get(i)[0] /= bones.get(i)[2];
			bones.get(i)[1] /= bones.get(i)[2];
			distanceFromCentreOfLimb[i] = Math.pow(softPoints[0] - bones.get(i)[0], 2.0)
					+ Math.pow(softPoints[1] - bones.get(i)[1],
							2.0); /*
									 * Square root omitted, as it does not
									 * affect the order...
									 */
		}
		return distanceFromCentreOfLimb;
	}

	public int dilateLimb(final byte[] data, final byte dilateVal, final byte min, final byte temp,
			final double threshold, final double[] scaledImage) {
		// Dilate algorithm
		// Best dilate by one solution taken from
		// http://ostermiller.org/dilate_and_erode.html
		int dilated = 0;
		for (int i = 0; i < height; i++) {
			for (int j = 0; j < width; j++) {
				if (data[i * width + j] == dilateVal) {
					if (i > 0 && data[(i - 1) * width + j] == min && scaledImage[(i - 1) * width + j] >= threshold) {
						data[(i - 1) * width + j] = temp;
					}
					if (j > 0 && data[(i) * width + j - 1] == min && scaledImage[(i) * width + j - 1] >= threshold) {
						data[(i) * width + j - 1] = temp;
					}
					if (i + 1 < height && data[(i + 1) * width + j] == min
							&& scaledImage[(i + 1) * width + j] >= threshold) {
						data[(i + 1) * width + j] = temp;
					}
					if (j + 1 < width && data[(i) * width + j + 1] == min
							&& scaledImage[(i) * width + j + 1] >= threshold) {
						data[(i) * width + j + 1] = temp;
					}
				}
			}
		}
		for (int i = 0; i < width * height; i++) {
			if (data[i] == temp) {
				data[i] = dilateVal; // Set to proper value here...
				++dilated;
			}
		}
		return dilated;
	}

	public byte[] fillSieve(final Vector<Integer> roiI, final Vector<Integer> roiJ, final int width, final int height,
			final double[] scaledImage, final double threshold) {
		// Fill the area enclosed by the traced edge contained in roiI,roiJ
		// beginning needs to be within the traced edge
		byte[] sieveTemp = new byte[width * height];
		int z = 0;
		int i, j;
		// IJ.error("Coordinates");
		// TextWindow tw = new
		// TextWindow("coordinates","I\tJ\tr\tno","",200,200);
		for (z = 0; z < roiI.size(); ++z) {
			sieveTemp[roiI.get(z) + roiJ.get(z) * width] = 1;
			// tw.append(roiI.get(z)+"\t"+roiJ.get(z));
		}

		/* Determine the flood fill init */
		int[] tempCoordinates;
		final int tempC = 0;
		while (true) {

			tempCoordinates = findFillInit(sieveTemp, roiI, roiJ, scaledImage, threshold);
			if (tempCoordinates == null) {
				return sieveTemp;
			}
			// tw.append(tempCoordinates[0]+"\t"+tempCoordinates[1]+"\t"+"round"+(++tempC));
			i = tempCoordinates[0];
			j = tempCoordinates[1];

			final Vector<Integer> initialI = new Vector<Integer>();
			final Vector<Integer> initialJ = new Vector<Integer>();
			initialI.add(i);
			initialJ.add(j);
			sieveTemp[i + j * width] = 1;
			final byte[] sieveTemp2 = sieveTemp.clone();
			boolean noLeak = true;
			while (initialI.size() > 0) {
				i = initialI.lastElement();
				j = initialJ.lastElement();
				initialI.remove(initialI.size() - 1);
				initialJ.remove(initialJ.size() - 1);

				if (sieveTemp2[i + j * width] == 0) {
					sieveTemp2[i + j * width] = 1;

				}
				if (i < 1 || i >= width - 1 || j < 1 || j >= height - 1) {
					noLeak = false;
					break;
				}
				// check whether the neighbour to the left should be added to
				// the queue
				if (sieveTemp2[i - 1 + j * width] == 0) {
					initialI.add(i - 1);
					initialJ.add(j);
				}
				// check whether the neighbour to the right should be added to
				// the queue
				if (sieveTemp2[i + 1 + j * width] == 0) {
					initialI.add(i + 1);
					initialJ.add(j);
				}
				// check whether the neighbour below should be added to the
				// queue
				if (sieveTemp2[i + (j - 1) * width] == 0) {
					initialI.add(i);
					initialJ.add(j - 1);
				}
				// check whether the neighbour above should be added to the
				// queue
				if (sieveTemp2[i + (j + 1) * width] == 0) {
					initialI.add(i);
					initialJ.add(j + 1);
				}
			}
			if (noLeak) {
				sieveTemp = sieveTemp2.clone();
			}
		}
		// return sieveTemp;
	}

	/*
	 * Edge Tracing DetectedEdge trace edge by advancing according to the
	 * previous direction if above threshold, turn to negative direction if
	 * below threshold, turn to positive direction Idea taken from
	 * http://www.math.ucla.edu/~bertozzi/RTG/zhong07/report_zhong.pdf The paper
	 * traced continent edges on map/satellite image
	 */
	Vector<Object> traceEdge(final double[] scaledImage, final byte[] result, final double threshold, int i, int j) {
		final Vector<Integer> iit = new Vector<Integer>();
		final Vector<Integer> jiit = new Vector<Integer>();
		iit.add(i);
		jiit.add(j);
		double direction = 0; // begin by advancing right. Positive angles
								// rotate the direction clockwise.
		double previousDirection;
		final boolean done = false;
		int initI, initJ;
		initI = i;
		initJ = j;

		while (true) {
			int counter = 0;
			previousDirection = direction;
			/*
			 * Handle going out of bounds by considering out of bounds to be
			 * less than threshold
			 */
			if (i + ((int) Math.round(Math.cos(direction))) >= 0 && i + ((int) Math.round(Math.cos(direction))) < width
					&& j + ((int) Math.round(Math.sin(direction))) >= 0
					&& j + ((int) Math.round(Math.sin(direction))) < height
					&& scaledImage[i + ((int) Math.round(Math.cos(direction)))
							+ (j + ((int) Math.round(Math.sin(direction)))) * width] > threshold) {// Rotate
																									// counter
																									// clockwise
				while ((scaledImage[i + ((int) Math.round(Math.cos(direction - Math.PI / 4.0)))
						+ (j + ((int) Math.round(Math.sin(direction - Math.PI / 4.0)))) * width] > threshold)
						&& counter < 8 && i + ((int) Math.round(Math.cos(direction - Math.PI / 4.0))) >= 0
						&& i + ((int) Math.round(Math.cos(direction - Math.PI / 4.0))) < width
						&& j + ((int) Math.round(Math.sin(direction - Math.PI / 4.0))) >= 0
						&& j + ((int) Math.round(Math.sin(direction - Math.PI / 4.0))) < height) {
					direction -= Math.PI / 4.0;
					++counter;
					if (Math.abs(direction - previousDirection) >= 180) {
						break;
					}

				}
			} else {// Rotate clockwise
				while ((i + ((int) Math.round(Math.cos(direction))) < 0
						|| i + ((int) Math.round(Math.cos(direction))) >= width
						|| j + ((int) Math.round(Math.sin(direction))) < 0
						|| j + ((int) Math.round(Math.sin(direction))) >= height
						|| scaledImage[i + ((int) Math.round(Math.cos(direction)))
								+ (j + ((int) Math.round(Math.sin(direction)))) * width] < threshold)
						&& counter < 8) {
					direction += Math.PI / 4.0;
					++counter;
					if (Math.abs(direction - previousDirection) >= 180) {
						break;
					}
				}

			}
			i += (int) Math.round(Math.cos(direction));
			j += (int) Math.round(Math.sin(direction));
			if ((i == initI && j == initJ) || counter > 7 || scaledImage[i + j * width] < threshold
					|| result[i + j * width] == 1 || result[i + j * width] > 3) {
				for (int ii = 0; ii < result.length; ++ii) {
					if (result[ii] > 1) {
						result[ii] = 1;
					}
				}
				final Vector<Object> returnVector = new Vector<Object>();
				returnVector.add(result);
				returnVector.add(iit);
				returnVector.add(jiit);
				/* tempImage.close(); */
				return returnVector;
			} else {
				if (result[i + j * width] == 0) {
					result[i + j * width] = 2;
				} else if (result[i + j * width] != 1) {
					result[i + j * width]++;
				}
				iit.add(i);
				jiit.add(j);

			}
			direction -= Math.PI / 2.0; // Keep steering counter clockwise not
										// to miss single pixel structs...
		}
	}

	Vector<Object> resultFill(int i, int j, final byte[] tempResult) {
		final Vector<Integer> initialI = new Vector<Integer>();
		final Vector<Integer> initialJ = new Vector<Integer>();
		initialI.add(i);
		initialJ.add(j);
		int pixelsFilled = 0;
		while (initialI.size() > 0 && initialI.lastElement() > 0 && initialI.lastElement() < width - 1
				&& initialJ.lastElement() > 0 && initialJ.lastElement() < height - 1) {
			i = initialI.lastElement();
			j = initialJ.lastElement();
			initialI.remove(initialI.size() - 1);
			initialJ.remove(initialJ.size() - 1);

			if (tempResult[i + j * width] == 0) {
				tempResult[i + j * width] = 1;
				++pixelsFilled;
			}

			if (tempResult[i - 1 + j * width] == 0) {
				initialI.add(i - 1);
				initialJ.add(j);
			}

			if (tempResult[i + 1 + j * width] == 0) {
				initialI.add(i + 1);
				initialJ.add(j);
			}

			if (tempResult[i + (j - 1) * width] == 0) {
				initialI.add(i);
				initialJ.add(j - 1);
			}

			if (tempResult[i + (j + 1) * width] == 0) {
				initialI.add(i);
				initialJ.add(j + 1);
			}

		}
		final Vector<Object> returnValue = new Vector<Object>();
		if (initialI.size() > 0 || initialJ.size() > 0) {
			returnValue.add(new Boolean(false));
		} else {
			returnValue.add(new Boolean(true));
		}
		returnValue.add(new Integer(pixelsFilled));
		return returnValue;
	}

	/* DetectEdge */
	Vector<Object> findEdge(final double[] scaledImage, final double threshold, final boolean allowCleaving) {
		int i, j, tempI, tempJ;
		int len;
		i = 0;
		j = 0;
		byte[] result = new byte[scaledImage.length];
		final Vector<DetectedEdge> edges = new Vector<DetectedEdge>();
		/* Debugging */
		/*
		 * ImagePlus tempImage = new ImagePlus("Edge Trace");
		 * tempImage.setProcessor(new ByteProcessor(width,height));
		 * tempImage.getProcessor().setBackgroundValue(0.0);
		 * tempImage.getProcessor().setValue(255.0); tempImage.show();
		 */
		while ((i < (width - 1)) && (j < (height - 1))) {
			while (j < height - 1 && i < width && scaledImage[i + j * width] < threshold) {
				i++;
				if (result[i + j * width] == 1) {
					while (j < height - 1 && result[i + j * width] > 0) {
						i++;
						if (i == width && j < height - 2) {
							i = 0;
							j++;
						}

					}
				}

				if (i == width) {
					j++;
					if (j >= height - 1)
						break;
					i = 0;
				}
			}
			tempI = i;
			tempJ = j;

			if (i >= width - 1 && j >= height - 1) {
				break; /* Go to end... */
			}
			result[i + j * width] = 1;

			/* Tracing algorithm DetectedEdge */
			final Vector<Object> returned = traceEdge(scaledImage, result, threshold, i, j);
			result = (byte[]) returned.get(0);
			final Vector<Integer> newIit = (Vector<Integer>) returned.get(1);
			final Vector<Integer> newJiit = (Vector<Integer>) returned.get(2);
			len = newIit.size();
			/* Tracing algorithm done... */

			Vector<Vector<Vector<Integer>>> returnedVectors = null;
			if (allowCleaving) {
				returnedVectors = cleaveEdge(result, newIit, newJiit, 3.0, 6.0);
				final byte[] tempRes = new byte[width * height];
				for (int iii = 0; iii < returnedVectors
						.size(); ++iii) { /* Go through all returned edges */
					/* Fill edge within result.. */
					final Vector<Integer> iit = new Vector<Integer>();
					final Vector<Integer> jiit = new Vector<Integer>();
					for (int ii = 0; ii < returnedVectors.get(iii).get(0).size(); ++ii) {
						iit.add(returnedVectors.get(iii).get(0).get(ii));
						jiit.add(returnedVectors.get(iii).get(1).get(ii));
						tempRes[iit.lastElement() + jiit.lastElement() * width] = 1;
					}
					final Vector<Object> results = fillResultEdge(result, iit, jiit, scaledImage, threshold);
					if (results != null) {
						result = (byte[]) results.get(0);
						edges.add(new DetectedEdge((Vector<Integer>) results.get(1), (Vector<Integer>) results.get(2),
								(Integer) results.get(3)));
					}
					// IJ.error("begs "+beginnings.size()+" lengths
					// "+length.size()+"retVects"+returnedVectors.size());
					/*
					 * for (int y = 0; y < height;++y) { for (int x = 0; x <
					 * width;++x) { //if (sieve[x+y*width] == 1){ //Tint roi
					 * area color with violet //if (tempRes[x+y*width] > 0){
					 * //Tint roi area color with violet if (result[x+y*width] >
					 * 0){ //Tint roi area color with violet
					 * tempImage.getProcessor().drawPixel(x,y); } } }
					 * tempImage.updateAndDraw(); IJ.error(" ");
					 */
				}

			} else {
				/* Fill edge within result.. */
				final Vector<Integer> iit = new Vector<Integer>();
				final Vector<Integer> jiit = new Vector<Integer>();
				for (int ii = 0; ii < newIit.size(); ++ii) {
					iit.add(newIit.get(ii));
					jiit.add(newJiit.get(ii));
				}
				final Vector<Object> results = fillResultEdge(result, iit, jiit, scaledImage, threshold);
				if (results != null) {
					result = (byte[]) results.get(0);
					edges.add(new DetectedEdge((Vector<Integer>) results.get(1), (Vector<Integer>) results.get(2),
							(Integer) results.get(3)));
				}
			}
			// Find next empty spot
			i = tempI;
			j = tempJ;
			while (j < height && scaledImage[i + j * width] >= threshold) {
				i++;
				if (i == width) {
					i = 0;
					j++;
				}
			}
		}

		final Vector<Object> returnVector = new Vector<Object>();
		returnVector.add(result);
		returnVector.add(edges);
		return returnVector;
	}

	/*
	 * DetectedEdge. Find fill init by steering clockwise from next to previous
	 */
	int[] findFillInit(final byte[] result, final Vector<Integer> iit, final Vector<Integer> jiit,
			final double[] scaledImage, final double threshold) {
		final int[] returnCoordinates = new int[2];
		final int[][] pixelNeigbourhood = { { 0, -1, -1, -1, -1, 0, 1, 1, 1 }, { 1, 1, 0, -1, -1, -1, 0, 1 } };
		final int[] steer = new int[2];
		for (int j = 0; j < iit.size() - 1; ++j) {
			returnCoordinates[0] = iit.get(j);
			returnCoordinates[1] = jiit.get(j);
			double direction = Math.atan2(jiit.get(j + 1) - returnCoordinates[1],
					iit.get(j + 1) - returnCoordinates[0]);
			for (int i = 0; i < 8; ++i) {
				direction += Math.PI / 4.0;
				steer[0] = (int) Math.round(Math.cos(direction));
				steer[1] = (int) Math.round(Math.sin(direction));
				/* Handle OOB */
				while ((returnCoordinates[0] + steer[0]) < 0 || (returnCoordinates[0] + steer[0]) >= width
						|| (returnCoordinates[1] + steer[1]) < 0 || (returnCoordinates[1] + steer[1]) >= height) {
					direction += Math.PI / 4.0;
					steer[0] = (int) Math.round(Math.cos(direction));
					steer[1] = (int) Math.round(Math.sin(direction));
				}

				if (result[returnCoordinates[0] + steer[0] + (returnCoordinates[1] + steer[1]) * width] == 0
						&& scaledImage[returnCoordinates[0] + steer[0]
								+ (returnCoordinates[1] + steer[1]) * width] >= threshold) {
					returnCoordinates[0] += steer[0];
					returnCoordinates[1] += steer[1];
					return returnCoordinates;
				}
				if (result[returnCoordinates[0] + steer[0] + (returnCoordinates[1] + steer[1]) * width] == 1) {
					break;
				}
			}
		}
		return null;
	}

	/* DetectedEdge version */
	Vector<Object> fillResultEdge(byte[] result, final Vector<Integer> iit, final Vector<Integer> jiit,
			final double[] scaledImage, final double threshold) {
		int pixelsFilled = 0;
		if (iit.size() > 0) {
			final int kai, kaj;
			/*
			 * Set initial fill pixel to the first pixel above threshold not on
			 * the border
			 */
			/* Select the first pixel found */
			boolean possible = true;
			final byte[] tempResult = result.clone();

			int[] tempCoordinates = findFillInit(tempResult, iit, jiit, scaledImage, threshold);
			if (tempCoordinates == null) {
				possible = false;
			}
			while (possible) {
				if (tempCoordinates == null) {
					break;
				} else {
					final Vector<Object> returned = resultFill(tempCoordinates[0], tempCoordinates[1], tempResult);
					possible = (Boolean) returned.get(0);
					pixelsFilled += (Integer) returned.get(1);
				}
				tempCoordinates = findFillInit(tempResult, iit, jiit, scaledImage, threshold);
			}

			if (possible) {
				result = tempResult.clone();
				final Vector<Object> results = new Vector<Object>();
				results.add(result);
				results.add(iit);
				results.add(jiit);
				results.add(new Integer(pixelsFilled));
				return results;

			}
		}
		return null;
	}

	/*
	 * Cleaving is made by looking at the ratios of distances between two points
	 * along the edge and the shortest distance between the points. If the
	 * maximum of the ratio is big enough, the highest ratio points will be
	 * connected with a straigth line and the edge with higher indices will be
	 * removed. E.g. for a circle, the maximum ratio is (pi/2)/d ~= 1.57 and for
	 * square it is 2/sqrt(2) = sqrt(2) ~= 1.41.
	 */
	Vector<Vector<Vector<Integer>>> cleaveEdge(final byte[] result, final Vector<Integer> fatRoiI,
			final Vector<Integer> fatRoiJ, final double minRatio, final double minLength) {
		double distanceAlongTheEdge = 0;
		double distance = 0;
		double ratio;
		final double minEdge = fatRoiI.size() / minLength;
		final int[] cleavingIndices = new int[2];
		boolean nextLoop = true;
		final Vector<Vector<Vector<Integer>>> returnVectorVectorPointer = new Vector<Vector<Vector<Integer>>>();
		while (nextLoop) {
			double highestRatio = minRatio - 0.1;
			/* Go through all point pairs */
			for (int i = 0; i < fatRoiI.size() - 11; ++i) {
				for (int j = i + 10; j < fatRoiI.size(); ++j) {
					distance = Math.sqrt(Math.pow(fatRoiI.get(j) - fatRoiI.get(i), 2.0)
							+ Math.pow(fatRoiJ.get(j) - fatRoiJ.get(i), 2.0));
					distanceAlongTheEdge = min(j - i, (double) fatRoiI.size() - j + i);
					ratio = distanceAlongTheEdge / distance;
					if (ratio > highestRatio && distanceAlongTheEdge > minEdge) {
						highestRatio = ratio;
						cleavingIndices[0] = i;
						cleavingIndices[1] = j;
					}

				}
			}
			// IJ.error("Highest ratio "+highestRatio);
			/*
			 * If ratio is high enough, cleave at the highest ratio point pair
			 */
			if (highestRatio >= minRatio) {
				returnVectorVectorPointer.add(cleave(result, fatRoiI, fatRoiJ, cleavingIndices));
			} else {
				nextLoop = false;
			}
		}
		/* Insert the last retained part to first index. */
		final Vector<Vector<Integer>> returnVectorPair = new Vector<Vector<Integer>>();
		returnVectorPair.add(new Vector<Integer>());
		returnVectorPair.add(new Vector<Integer>());
		returnVectorPair.get(0).addAll(fatRoiI);
		returnVectorPair.get(1).addAll(fatRoiJ);
		if (returnVectorVectorPointer.size() < 1) {
			returnVectorVectorPointer.add(returnVectorPair);
		} else {
			returnVectorVectorPointer.insertElementAt(returnVectorPair, 0);
		}
		return returnVectorVectorPointer;
	}

	/* Remove the extra part from vectors and replace with a straight line */
	Vector<Vector<Integer>> cleave(final byte[] result, final Vector<Integer> fatRoiI, final Vector<Integer> fatRoiJ,
			final int[] cleavingIndices) {
		final int initialLength = fatRoiI.size();
		final int initI = fatRoiI.get(cleavingIndices[0]);
		final int initJ = fatRoiJ.get(cleavingIndices[0]);
		final int targetI = fatRoiI.get(cleavingIndices[1]);
		final int targetJ = fatRoiJ.get(cleavingIndices[1]);
		/* remove cleaved elements */
		int replacementI = fatRoiI.get(cleavingIndices[0]);
		int replacementJ = fatRoiJ.get(cleavingIndices[0]);
		final Vector<Integer> cleavedI = new Vector<Integer>(fatRoiI.subList(cleavingIndices[0] + 1,
				cleavingIndices[1] + 1)); /* the elements to be cleaved */
		final Vector<Integer> cleavedJ = new Vector<Integer>(fatRoiJ.subList(cleavingIndices[0] + 1,
				cleavingIndices[1] + 1)); /* the elements to be cleaved */
		for (int i = cleavingIndices[0]; i < cleavingIndices[1]; ++i) {
			fatRoiI.removeElementAt(
					cleavingIndices[0]); /* Remove the elements to be cleaved */
			fatRoiJ.removeElementAt(
					cleavingIndices[0]); /* Remove the elements to be cleaved */
		}
		/* Insert replacement line */
		final double replacementLength = cleavingIndices[1] - cleavingIndices[0];
		final double repILength = targetI - initI;
		final double repJLength = targetJ - initJ;
		double relativeLength;
		final Vector<Integer> insertionI = new Vector<Integer>();
		final Vector<Integer> insertionJ = new Vector<Integer>();
		insertionI.add(replacementI);
		insertionJ.add(replacementJ);
		for (int k = cleavingIndices[0]; k < cleavingIndices[1]; ++k) {
			relativeLength = ((double) k) - ((double) cleavingIndices[0]);
			replacementI = ((int) (repILength * (relativeLength / replacementLength))) + initI;
			replacementJ = ((int) (repJLength * (relativeLength / replacementLength))) + initJ;
			if (replacementI != insertionI.lastElement() || replacementJ != insertionJ.lastElement()) {
				insertionI.add(replacementI);
				insertionJ.add(replacementJ);
				result[replacementI + replacementJ * width] = 1;
			}
		}
		fatRoiI.addAll(cleavingIndices[0], insertionI);
		fatRoiJ.addAll(cleavingIndices[0], insertionJ);
		Collections.reverse(insertionI);
		Collections.reverse(insertionJ);
		cleavedI.addAll(0, insertionI);
		cleavedJ.addAll(0, insertionJ);
		final Vector<Vector<Integer>> returnVectorPair = new Vector<Vector<Integer>>();
		returnVectorPair.add(new Vector<Integer>());
		returnVectorPair.add(new Vector<Integer>());
		returnVectorPair.get(0).addAll(cleavedI);
		returnVectorPair.get(1).addAll(cleavedJ);
		return returnVectorPair;
	}

	double min(final double a, final double b) {
		return (a < b) ? a : b;
	}
}
