/*-
 * #%L
 * Mavenized version of the BoneJ1 plugins
 * %%
 * Copyright (C) 2015 - 2020 Michael Doube, BoneJ developers
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
/*
BSD 2-Clause License
Copyright (c) 2020, Michael Doube
All rights reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.bonej.plugins;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bonej.util.Multithreader;
import org.eclipse.collections.api.block.procedure.primitive.IntProcedure;
import org.eclipse.collections.api.iterator.IntIterator;
import org.eclipse.collections.api.iterator.MutableIntIterator;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.tuple.primitive.IntObjectPair;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.map.mutable.primitive.IntIntHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

/**
 * Performs connected components labelling (sequential region labelling) in two-passes.
 * 
 * This implementation scales as O(n) or slightly better and is well suited to large images.
 * 
 * @author Michael Doube
 * @see <a href="https://doi.org/10.1098/rsos.201784">doi:10.1098/rsos.201784</a>
 *
 */
public class ConnectedComponents {

	/** Foreground value */
	public static final int FORE = -1;
	/** Background value */
	public static final int BACK = 0;
	/** 2^23 - greatest integer that can be represented precisely by a float */
	public static final int MAX_FINAL_LABEL = 8388608;
	/** maximum label value to use during intermediate processing */
	static final int MAX_LABEL = Integer.MAX_VALUE;

	/** number of particle labels */
	private static int nParticles;

	/** array of binary pixels */
	private static byte[][] workArray;

	/** Constructor */
	public ConnectedComponents() {

	}

	/**
	 * Run connected components filter on a binary image
	 * 
	 * @param imp   Input ImagePlus, must be 2D or 3D and binary (0 or 255)
	 * @param phase either foreground (this.FORE) or background (this.BACK)
	 * @return 2D int array with the same dimensions as the input image, with
	 *         individual connected components labelled with a unique, consecutive
	 *         label.
	 */
	public int[][] run(final ImagePlus imp, final int phase) {
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int nSlices = imp.getImageStackSize();
		final int nProcessors = Runtime.getRuntime().availableProcessors();
		final int minSlicesPerChunk = 10;

		// set up number of chunks and chunk sizes
		int nChunks = 1;
		int slicesPerChunk = nSlices;
		if (nSlices < minSlicesPerChunk) {
			slicesPerChunk = nSlices;
			nChunks = 1;
		} else if (nSlices <= minSlicesPerChunk * nProcessors) {
			slicesPerChunk = minSlicesPerChunk;
			nChunks = (int) Math.ceil((double) nSlices / (double) minSlicesPerChunk); 
		} else if (nSlices > minSlicesPerChunk * nProcessors) {
			nChunks = nProcessors;
			slicesPerChunk = (int) Math.floor((double) nSlices / (double) nChunks);
		}
		
		// set up start slice array
		final int[] startSlices = new int[nChunks];
		for (int i = 0; i < nChunks; i++) {
			startSlices[i] = i * slicesPerChunk;
		}

		// set up label offsets to avoid collisions between chunks
		final int chunkLabelSpace = MAX_LABEL / nChunks;
		final int[] chunkIDOffsets = new int[nChunks];
		for (int i = 0; i < nChunks; i++) {
			chunkIDOffsets[i] = i * chunkLabelSpace;
		}

		// set up a map split into one per chunk
		final ArrayList<MutableList<IntHashSet>> chunkMaps = new ArrayList<>(nChunks);
		// assume there is a new particle label for every 10000 pixels
		final int initialArrayCapacity = 1 + (int)((long) w * (long) h * slicesPerChunk / 10000);
		
		for (int chunk = 0; chunk < nChunks; chunk++) {
			final MutableList<IntHashSet> map = FastList.newList(initialArrayCapacity);
			chunkMaps.add(map);
		}

		// set up the work array
		makeWorkArray(imp);

		//do a first labelling and map first degree neighbours
		int[][] particleLabels = firstIDAttribution(chunkMaps, chunkIDOffsets, startSlices, w, h, nSlices, phase);

		//merge neighbour networks and generate a LUT
		final int[][] lut = generateLut(chunkMaps, chunkIDOffsets);
		
		// rewrite the pixel values using the LUT
		applyLUT(particleLabels, lut, chunkIDOffsets, startSlices, nSlices);

		return particleLabels;
	}

	/**
	 * Generate a label replacement LUT
	 * 
	 * @param chunkMaps list of HashMaps
	 * @param chunkIDOffsets ID offsets
	 * @return LUTs, one per image chunk
	 */
	private static int[][] generateLut(ArrayList<MutableList<IntHashSet>> chunkMaps, int[] chunkIDOffsets) {
		// merge labels between the HashSets, handling the chunk offsets and indexes
		bucketFountain(chunkMaps, chunkIDOffsets);
		
		IntIntHashMap lutMap = makeLutMap(chunkMaps);

		return lutFromLutMap(lutMap, chunkMaps, chunkIDOffsets);
	}

	/**
	 * Create a work array and store it as a field of this instance, which can be
	 * retrieved with getWorkArray
	 *
	 * @param imp an image.
	 */
	static void makeWorkArray(final ImagePlus imp) {
		final int s = imp.getStackSize();
		final int p = imp.getWidth() * imp.getHeight();
		workArray = new byte[s][p];
		final ImageStack stack = imp.getStack();

		AtomicInteger ai = new AtomicInteger(0);

		final Thread[] threads = Multithreader.newThreads();
		for (int thread = 0; thread < threads.length; thread++) {
			threads[thread] = new Thread(() -> {
				for (int z = ai.getAndIncrement(); z < s; z = ai.getAndIncrement()) {
					final ImageProcessor ip = stack.getProcessor(z + 1);
					for (int i = 0; i < p; i++) {
						workArray[z][i] = (byte) ip.get(i);
					}
				}
			});
		}
		Multithreader.startAndJoin(threads);
	}

	/**
	 * Go through all pixels and assign initial particle label.
	 *
	 * @param chunkMaps collision recording lists
	 * @param chunkIDOffsets ID offsets 
	 * @param startSlices first slice of each chunk
	 * @param w image width
	 * @param h image height
	 * @param nSlices number of slices
	 * @param phase     FORE or BACK for foreground of background respectively
	 * @return particleLabels int[] array containing label associating every pixel
	 *         with a particle
	 */
	private static int[][] firstIDAttribution(final ArrayList<MutableList<IntHashSet>> chunkMaps,
			final int[] chunkIDOffsets, final int[] startSlices, final int w, final int h, final int nSlices,
			final int phase) {

		final int nChunks = chunkIDOffsets.length;
		final int wh = w * h;
		// set up the particle label stack
		final int[][] particleLabels = new int[nSlices][wh];

		// set up the threads (one thread per chunk)
		final Thread[] threads = new Thread[nChunks];

		for (int thread = 0; thread < nChunks; thread++) {
			// each chunk is processed in a single thread
			final int chunk = thread;
			// the starting ID for each chunk is the offset
			final int IDoffset = chunkIDOffsets[chunk];
			final int nextIDOffset = chunk < (chunkIDOffsets.length - 1) ? chunkIDOffsets[chunk + 1] : MAX_LABEL;
			threads[chunk] = new Thread(() -> {
				// get the Array of HashSets that relate to this image chunk
				final MutableList<IntHashSet> chunkMap = chunkMaps.get(chunk);

				// label image IDs have the chunk ID offset
				int ID = IDoffset;

				if (ID == 0) {
					//set up the background ID = 0
					chunkMap.add(new IntHashSet(1));
					chunkMap.get(0).add(0);
					ID = 1;
				}

				final int startSlice = startSlices[chunk];

				// final slice of the chunk is the next chunk's start slice minus one for all
				// but the last chunk
				final int endSlice = chunk + 1 < nChunks ? startSlices[chunk + 1] - 1 : nSlices - 1;

				if (phase == FORE) {
					// first slice of the chunk - use 4 neighbourhood to not
					// bleed into prior chunk
					final int[] sliceNbh = new int[4];
					for (int y = 0; y < h; y++) {
						final int rowIndex = y * w;
						for (int x = 0; x < w; x++) {
							final int arrayIndex = rowIndex + x;
							if (workArray[startSlice][arrayIndex] == FORE) {
								// Find the minimum particleLabel in the
								// neighbouring pixels
								get4Neighborhood(sliceNbh, particleLabels, x, y, startSlice, w, h, nSlices);

								final int minTag = getMinTag(sliceNbh, ID);

								// add neighbourhood to map
								addNeighboursToMap(chunkMap, sliceNbh, minTag, IDoffset);

								// assign the smallest particle label from the
								// neighbours to the pixel
								particleLabels[startSlice][arrayIndex] = minTag;

								// increment the particle label
								if (minTag == ID) {
									ID++;
									expandMap(chunkMap, ID, IDoffset, nextIDOffset);
								}
							}
						}
					}

					// use 13 neighbourhood for all but first slice
					final int[] nbh = new int[13];
					boolean lastPixelWasForeground = false;
					final int wm1 = w - 1;
					final int hm1 = h - 1;
					int centre = 0;
					for (int z = startSlice + 1; z <= endSlice; z++) {
						for (int y = 0; y < h; y++) {
							final int rowIndex = y * w;
							for (int x = 0; x < w; x++) {
								final int arrayIndex = rowIndex + x;
								if (workArray[z][arrayIndex] == FORE) {
									
									//for pixels in the middle of the slice,
									if (lastPixelWasForeground && x > 0 && y > 0 && x < wm1 && y < hm1) {
										 //slide the neighbourhood and 
										// pick up 4 new labels from the label array
										get13Neighborhood(nbh, particleLabels, x, y, z, w, centre);
									} else {
										// Find the minimum particleLabel in the
										// neighbouring pixels - 13 pixels from label array
										get13Neighborhood(nbh, particleLabels, x, y, z, w, h, nSlices);
									}

									centre = getMinTag(nbh, ID);

									// add neighbourhood to map
									addNeighboursToMap(chunkMap, nbh, centre, IDoffset);

									// assign the smallest particle label from the
									// neighbours to the pixel
									particleLabels[z][arrayIndex] = centre;
									
									// increment the particle label
									if (centre == ID) {
										ID++;
										expandMap(chunkMap, ID, IDoffset, nextIDOffset);
									}
									lastPixelWasForeground = true;
								} else {
									lastPixelWasForeground = false;
								}
							}
						}
					}
				}

				else if (phase == BACK) {
					// first slice of the chunk - use 2 neighbourhood to not
					// bleed into prior chunk
					final int[] sliceNbh = new int[2];
					for (int y = 0; y < h; y++) {
						final int rowIndex = y * w;
						for (int x = 0; x < w; x++) {
							final int arrayIndex = rowIndex + x;
							if (workArray[startSlice][arrayIndex] == BACK) {
								// Find the minimum particleLabel in the
								// neighbouring pixels
								get2Neighborhood(sliceNbh, particleLabels, x, y, startSlice, w, h, nSlices);

								final int minTag = getMinTag(sliceNbh, ID);

								// add neighbourhood to map
								addNeighboursToMap(chunkMap, sliceNbh, minTag, IDoffset);

								// assign the smallest particle label from the
								// neighbours to the pixel
								particleLabels[startSlice][arrayIndex] = minTag;
								// increment the particle label
								if (minTag == ID) {
									ID++;
									expandMap(chunkMap, ID, IDoffset, nextIDOffset);
								}
							}
						}
					}

					// use 3-neighbourhood for all but the first slice
					final int[] nbh = new int[3];
					for (int z = startSlice + 1; z <= endSlice; z++) {
						for (int y = 0; y < h; y++) {
							final int rowIndex = y * w;
							for (int x = 0; x < w; x++) {
								final int arrayIndex = rowIndex + x;
								if (workArray[z][arrayIndex] == BACK) {

									// Find the minimum particleLabel in the
									// neighbouring pixels
									get3Neighborhood(nbh, particleLabels, x, y, z, w, h, nSlices);

									final int minTag = getMinTag(nbh, ID);

									addNeighboursToMap(chunkMap, nbh, minTag, IDoffset);

									// assign the smallest particle label from the
									// neighbours to the pixel
									particleLabels[z][arrayIndex] = minTag;
									// increment the particle label
									if (minTag == ID) {
										ID++;
										expandMap(chunkMap, ID, IDoffset, nextIDOffset);
									}
								}
							}
						}
					}
				}
				// there is always one too many IDs per chunk, so trim the last one off
				// unless the map is empty
				if (chunkMap.size() > 0 ) {
					//don't remove the background element
					if (ID == 1 && chunkMap.size() == 1) {}
					else chunkMap.remove(chunkMap.size() - 1);
				}
			});
		}
		Multithreader.startAndJoin(threads);

		// find neighbours in the previous chunk
		// this will result in occasional HashSet values less than
		// the chunk's IDoffset, which indicate linkage between chunks
		final Thread[] stitchingThreads = new Thread[nChunks];
		for (int thread = 0; thread < nChunks; thread++) {
			final int chunk = thread;
			stitchingThreads[thread] = new Thread(() -> {

				// need only one z per thread
				final int z = startSlices[chunk];
				final MutableList<IntHashSet> chunkMap = chunkMaps.get(chunk);
				final int IDoffset = chunkIDOffsets[chunk];

				if (chunk > 0) {
					if (phase == FORE) {
						final int[] nbh = new int[9];
						for (int y = 0; y < h; y++) {
							final int rowIndex = y * w;
							for (int x = 0; x < w; x++) {
								final int arrayIndex = rowIndex + x;
								if (workArray[z][arrayIndex] == FORE) {
									final int label = particleLabels[z][arrayIndex];
									get9Neighborhood(nbh, particleLabels, x, y, z, w, h, nSlices);
									addChunkNeighboursToMap(chunkMap, nbh, label - IDoffset);
								}
							}
						}
					}

					if (phase == BACK) {
						final int[] nbh = new int[1];
						for (int y = 0; y < h; y++) {
							final int rowIndex = y * w;
							for (int x = 0; x < w; x++) {
								final int arrayIndex = rowIndex + x;
								if (workArray[z][arrayIndex] == BACK) {
									final int label = particleLabels[z][arrayIndex];
									get1Neighborhood(nbh, particleLabels, x, y, z, w);
									addChunkNeighboursToMap(chunkMap, nbh, label - IDoffset);
								}
							}
						}
					}
				}
			});
		}
		Multithreader.startAndJoin(stitchingThreads);

		return particleLabels;
	}

	/**
	 * Empty each HashSet into a lower-indexed HashSet, based on the lowest label
	 * found in the HashSet and the HashSets' indices. Resolve inter-chunk label
	 * collisions. Transitively connected labels are joined when they are added to the same, 
	 * lower-indexed, HashSet.
	 * <br/>
	 * By iterating downwards from the highest index, this algorithm resembles buckets
	 * splashing water from the uppermost to the lowermost.
	 * It is named after <i>Bucket Fountain</i>, a kinetic sculpture by Burren and Keen (1969),
	 * installed in Cuba Mall, Wellington, New Zealand.
	 * 
	 * @see <a href="https://en.wikipedia.org/wiki/Bucket_Fountain">Wikipedia: Bucket Fountain</a>
	 * @param chunkMaps list of collisions between labels
	 * @param chunkIDOffsets ID offsets
	 */
	private static void bucketFountain(final ArrayList<MutableList<IntHashSet>> chunkMaps, final int[] chunkIDOffsets) {
		// iterate backwards through the chunk maps

		final int nChunks = chunkIDOffsets.length;
		final int[][] bucketLUT = new int[nChunks][];
		
		//for each chunk set up a thread
		final Thread[] threads = new Thread[nChunks];
		for (int thread = 0; thread < nChunks; thread++) {
			final int chunk = thread;
			final MutableList<IntHashSet> map = chunkMaps.get(chunk);
			final int nBuckets = map.size();
			final int[] threadBucketLUT = new int[nBuckets];
			final int IDoffset = chunkIDOffsets[chunk];
			for (int i = 0; i < nBuckets; i++) {
				//default is that each label sits in its same-numbered bucket minus IDoffset
				threadBucketLUT[i] = i + IDoffset;
			}
			threads[thread] = new Thread(() -> {
				for (int i = map.size() - 1; i >= 0; i--) {
					final IntHashSet set = map.get(i);
					if (!set.isEmpty()) {
						//get the minimum that is greater than or equal to ID offset
						int minLabel = Integer.MAX_VALUE;
						//this iteration strategy may be slow
						IntIterator intiter = set.intIterator();
						while (intiter.hasNext()) {
							final int label = intiter.next();
							if (label >= IDoffset && label < minLabel) {
								minLabel = label;
							}
						}
						// move whole set's contents to a lower position in the map
						if (minLabel < i + IDoffset) {
							map.get(minLabel - IDoffset).addAll(set);
							set.clear();
						}
					}
				}
				//now fill the LUT
				boolean doItAgain = true;
				while (doItAgain == true) {
					doItAgain = false;
					for (int i = 0; i < nBuckets; i++) {
						final IntHashSet set = map.get(i);
						if (set.isEmpty()) continue;
						IntIterator iter = set.intIterator();
						while (iter.hasNext()) {
							final int label = iter.next();
							if (label < IDoffset) continue; //ignore labels from lower chunks
							final int labelID = label - IDoffset;
							//check the current ID in the LUT
							final int currentID = threadBucketLUT[labelID];
							if (currentID == labelID) { //the normal case, each label ought to be present in the map only once and in a smaller or same value bucket
								threadBucketLUT[labelID] = i;
							} else if (currentID < i) { //this label was already found in a lower bucket
								//maybe need to update the LUT for the set members prior to shifting
								//to reduce false hits and iterations
								map.get(currentID).addAll(set);
								set.clear();
								doItAgain = true;
							}
						}
					}
				}
			});
			bucketLUT[chunk] = threadBucketLUT;
		}
		Multithreader.startAndJoin(threads);

		for (int chunk = nChunks - 1; chunk >= 0; chunk--) {
			final MutableList<IntHashSet> map = chunkMaps.get(chunk);
			final int priorChunk = chunk > 0 ? chunk - 1 : 0;
			final MutableList<IntHashSet> priorMap = chunkMaps.get(priorChunk);
			final int IDoffset = chunkIDOffsets[chunk];
			final int priorIDoffset = chunkIDOffsets[priorChunk];
			final int[] threadBucketLUT = bucketLUT[priorChunk];
			for (int i = map.size() - 1; i >= 0; i--) {
				final IntHashSet set = map.get(i);
				if (!set.isEmpty()) {
					// find the minimum label in the set
					int minLabel = set.min();
					
					// if minimum label is less than this chunk's offset, need
					// to move set to previous chunk's map
					if (minLabel < IDoffset) {
						final int priorLabelID = minLabel - priorIDoffset;
						final int targetSet = threadBucketLUT[priorLabelID] - priorIDoffset;
						priorMap.get(targetSet).addAll(set);
						set.clear();
						continue;
					}
					// move whole set's contents to a lower position in the map
					if (minLabel < i + IDoffset) {
						map.get(minLabel - IDoffset).addAll(set);
						set.clear();
					}
				}
			}
		}
	}

	/**
	 * Check the labels within HashSets for consistency and merge them if needed.
	 * Generate a label replacement LUT based on the index of the HashSet each label is
	 * found within.
	 * 
	 * @param chunkMaps list of collisions between labels
	 * @return lutMap initial mapping of partial region labels to final labels
	 */
	private static IntIntHashMap makeLutMap(final ArrayList<MutableList<IntHashSet>> chunkMaps) {
		// count unique labels and particles
		int labelCount = 0;
		for (MutableList<IntHashSet> map : chunkMaps) {
			for (IntHashSet set : map) {
				if (!set.isEmpty())
					labelCount += set.size();
			}
		}

		// set up a 1D HashMap of HashSets with the minimum label
		// set as the 'root' (key) of the hashMap
		IntObjectHashMap<IntHashSet> hashMap = new IntObjectHashMap<>(labelCount);
		for (MutableList<IntHashSet> map : chunkMaps) {
			for (IntHashSet set : map) {
				if (!set.isEmpty())
					hashMap.put(set.min(), set);
			}
		}

		// set up a LUT to keep track of the minimum replacement value for each label
		final IntIntHashMap lutMap = new IntIntHashMap(labelCount);
		for (MutableList<IntHashSet> map : chunkMaps) {
			for (IntHashSet set : map) {
				// start so that each label looks up itself
				set.forEach(label -> lutMap.put(label, label));
			}
		}

		// check the hashMap for duplicate appearances and merge sets downwards
		boolean somethingChanged = true;
		while (somethingChanged) {
			somethingChanged = false;
			Iterator<IntObjectPair<IntHashSet>> iterator2 = hashMap.keyValuesView().iterator();
			while (iterator2.hasNext()) {
			//for all the keyvalue pairs
				IntObjectPair<IntHashSet> next = iterator2.next();
				final int key = next.getOne();
				final IntHashSet set = next.getTwo();
				MutableIntIterator iterator = set.intIterator();
				while (iterator.hasNext()) {
					final int label = iterator.next();
					int lutValue = lutMap.get(label);
					// lower the lut lookup value to the root of this set
					if (lutValue > key) {
						lutMap.put(label, key);
						somethingChanged = true;
						continue;
					}
					// looks like there is a value in the wrong place
					if (lutValue < key) {
						// move all the set's labels to the lower root
						hashMap.get(lutValue).addAll(set);
						// update all the set's lut lookups with the new root
						MutableIntIterator iter = set.intIterator();
						while (iter.hasNext()) {
							final int l = iter.next();
							lutMap.put(l, lutValue);
						}
						set.clear();
						somethingChanged = true;
						break;
					}
				}
			}
		}
		return lutMap;
	}

	/**
	 * Translate the LUT coded as a HashMap into a primitive array for rapid pixel relabelling
	 * 
	 * @param lutMap hashmap LUT
	 * @param chunkMaps list of labels
	 * @param chunkIDOffsets ID offsets
	 * @return LUT as a 2D int array, with an int[] array per chunk
	 */
	private static int[][] lutFromLutMap(final IntIntHashMap lutMap,
			final ArrayList<MutableList<IntHashSet>> chunkMaps, final int[] chunkIDOffsets) {
		// count number of unique labels in the LUT
		IntHashSet lutLabels = new IntHashSet();
		lutMap.forEachValue(value -> {
			lutLabels.add(value);
		});
		final int nLabels = lutLabels.size();
		nParticles = nLabels;

		// assign incremental replacement values
		// translate old
		final IntIntHashMap lutLut = new IntIntHashMap(nLabels);
		final AtomicInteger value = new AtomicInteger(1);
		lutLabels.forEach(lutValue -> {
			if (lutValue == 0) {
				lutLut.put(0, 0);
			} else {
			  lutLut.put(lutValue, value.getAndIncrement());
			}
		});

		// lutLut now contains mapping from the old lut value (the lutLut 'key') to the
		// new lut value (lutLut 'value')
		lutMap.forEachKey(key -> {
			final int oldLutValue = lutMap.get(key);
			final int newLutValue = lutLut.get(oldLutValue);
			lutMap.put(key, newLutValue);
		});

		// translate the HashMap LUT to a chunkwise LUT, to be used in combination
		// with the IDoffsets.
		final int nChunks = chunkIDOffsets.length;
		int[][] lut = new int[nChunks][];
		for (int chunk = 0; chunk < nChunks; chunk++) {
			final int nChunkLabels = chunkMaps.get(chunk).size();
			final int IDoffset = chunkIDOffsets[chunk];
			int[] chunkLut = new int[nChunkLabels];
			for (int i = 0; i < nChunkLabels; i++) {
				chunkLut[i] = lutMap.get(i + IDoffset);
			}
			lut[chunk] = chunkLut;
		}
		return lut;
	}

	/**
	 * Apply the LUT in multiple threads
	 * 
	 * @param particleLabels label array
	 * @param lut label LUT
	 * @param chunkIDOffsets ID offsets 
	 * @param startSlices start slices per chunk
	 * @param d image depth
	 */
	private static void applyLUT(final int[][] particleLabels, final int[][] lut, final int[] chunkIDOffsets,
			final int[] startSlices, final int d) {
		final int nChunks = chunkIDOffsets.length;

		final Thread[] threads = new Thread[nChunks];
		for (int thread = 0; thread < nChunks; thread++) {
			final int chunk = thread;
			threads[thread] = new Thread(() -> {
				final int startSlice = startSlices[chunk];
				final int endSlice = chunk + 1 < nChunks ? startSlices[chunk + 1] - 1 : d - 1;
				final int IDoffset = chunkIDOffsets[chunk];
				final int[] chunkLut = lut[chunk];
				for (int z = startSlice; z <= endSlice; z++) {
					final int[] slice = particleLabels[z];
					final int l = slice.length;
					for (int i = 0; i < l; i++) {
						final int label = slice[i];
						if (label == 0)
							continue;
						slice[i] = chunkLut[label - IDoffset];
					}
				}
			});
		}
		Multithreader.startAndJoin(threads);
	}

	/**
	 * Add all the neighbouring labels of a pixel to the map, except 0 (background)
	 * and the pixel's own label, which is already in the map.
	 * 
	 * This chunked version of the map stores label IDs ('centre') in the HashSet
	 * and uses label ID minus per chunk ID offset as the List index.
	 * 
	 * In this version the non-zero neighbours' labels are always bigger than the
	 * centre, so the centre value is added to the neighbours' map indices.
	 *
	 * @param map      a map of LUT values.
	 * @param nbh      a neighbourhood in the image.
	 * @param centre   current pixel's label (with offset)
	 * @param IDoffset chunk's ID offset
	 */
	private static void addNeighboursToMap(final List<IntHashSet> map, final int[] nbh, final int centre,
			final int IDoffset) {

		int lastNonZero = -1;
		for (final int val : nbh) {
			// skip background, self-similar, and the last label added
			// adding them again is a redundant waste of time
			if (val == 0 || val == centre || val == lastNonZero)
				continue;
			map.get(val - IDoffset).add(centre);
			lastNonZero = val;
		}
	}

	/**
	 * Add all the neighbouring labels of a pixel to the map, except 0 (background).
	 * The LUT gets updated with the minimum neighbour found, but this is only
	 * within the first neighbours and not the minimum label in the pixel's
	 * neighbour network
	 *
	 * @param map    a map of LUT values.
	 * @param nbh    a neighbourhood in the image.
	 * @param centre current pixel's map index (label - IDoffset)
	 */
	private static void addChunkNeighboursToMap(final List<IntHashSet> map, final int[] nbh, final int centre) {
		final IntHashSet set = map.get(centre);
		int lastNonZero = -1;
		for (final int val : nbh) {
			// skip background
			// and the last non-zero value (already added)
			if (val == 0 || val == lastNonZero)
				continue;
			set.add(val);
			lastNonZero = val;
		}
	}

	/**
	 * Get 13 neighborhood of a pixel in a 3D image (0 border conditions) Longhand,
	 * hard-coded for speed. This neighbourhood contains the set of pixels that have
	 * already been visited by the cursor as it raster scans in an x-y-z order.
	 *
	 * @param neighborhood a neighbourhood in the image.
	 * @param image        3D image (int[][])
	 * @param x            x- coordinate
	 * @param y            y- coordinate
	 * @param z            z- coordinate (in image stacks the indexes start at 1)
	 * @param w            width of the image.
	 * @param h            height of the image.
	 * @param d            depth of the image.
	 */
	private static void get13Neighborhood(final int[] neighborhood, final int[][] image, final int x, final int y,
			final int z, final int w, final int h, final int d) {
		final int xm1 = x - 1;
		final int xp1 = x + 1;
		final int ym1 = y - 1;
		final int yp1 = y + 1;
		final int zm1 = z - 1;

		neighborhood[0] = getPixel(image, xm1, ym1, zm1, w, h, d);
		neighborhood[1] = getPixel(image, x, ym1, zm1, w, h, d);
		neighborhood[2] = getPixel(image, xp1, ym1, zm1, w, h, d);

		neighborhood[3] = getPixel(image, xm1, y, zm1, w, h, d);
		neighborhood[4] = getPixel(image, x, y, zm1, w, h, d);
		neighborhood[5] = getPixel(image, xp1, y, zm1, w, h, d);

		neighborhood[6] = getPixel(image, xm1, yp1, zm1, w, h, d);
		neighborhood[7] = getPixel(image, x, yp1, zm1, w, h, d);
		neighborhood[8] = getPixel(image, xp1, yp1, zm1, w, h, d);

		neighborhood[9] = getPixel(image, xm1, ym1, z, w, h, d);
		neighborhood[10] = getPixel(image, x, ym1, z, w, h, d);
		neighborhood[11] = getPixel(image, xp1, ym1, z, w, h, d);

		neighborhood[12] = getPixel(image, xm1, y, z, w, h, d);
	}
	
	/**
	 * Get 13 neighborhood of a pixel in a 3D image.
	 * This neighbourhood lookup assumes that the centre is raster scanning
	 * in an x-y-z order and copies 9 values from the previous neighbourhood, picking only
	 * the next 4 new values from the label array, and performs no out of bounds checking.
	 *
	 * @param neighborhood a neighbourhood in the image.
	 * @param image        3D image (int[][])
	 * @param x            x- coordinate
	 * @param y            y- coordinate
	 * @param z            z- coordinate (in image stacks the indexes start at 1)
	 * @param w            width of the image.
	 * @param lastCentre centre label from the last neighbourhood
	 */
	private static void get13Neighborhood(final int[] neighborhood, final int[][] image, final int x, final int y,
			final int z, final int w, final int lastCentre) {

		final int xp1 = x + 1;
		final int ym1 = y - 1;
		final int zm1 = z - 1;

		neighborhood[0] = neighborhood[1];
		neighborhood[1] = neighborhood[2];
		neighborhood[2] = getPixel(image, xp1, ym1, zm1, w);

		neighborhood[3] = neighborhood[4];
		neighborhood[4] = neighborhood[5];
		neighborhood[5] = getPixel(image, xp1, y, zm1, w);

		neighborhood[6] = neighborhood[7];
		neighborhood[7] = neighborhood[8];
		neighborhood[8] = getPixel(image, xp1, y + 1, zm1, w);

		neighborhood[9] = neighborhood[10];
		neighborhood[10] = neighborhood[11];
		neighborhood[11] = getPixel(image, xp1, ym1, z, w);

		neighborhood[12] = lastCentre;
	}

	/**
	 * Get 9 neighborhood of a pixel in a 3D image (0 border conditions) Longhand,
	 * hard-coded for speed. This neighbourhood contains the set of pixels in
	 * previous plane (z-1) of the pixel's 26-neighbourhood
	 *
	 * @param neighborhood a neighbourhood in the image.
	 * @param image        3D image (int[][])
	 * @param x            x- coordinate
	 * @param y            y- coordinate
	 * @param z            z- coordinate (in image stacks the indexes start at 1)
	 * @param w            width of the image.
	 * @param h            height of the image.
	 * @param d            depth of the image.
	 */
	private static void get9Neighborhood(final int[] neighborhood, final int[][] image, final int x, final int y,
			final int z, final int w, final int h, final int d) {
		final int xm1 = x - 1;
		final int xp1 = x + 1;
		final int ym1 = y - 1;
		final int yp1 = y + 1;
		final int zm1 = z - 1;

		neighborhood[0] = getPixel(image, xm1, ym1, zm1, w, h, d);
		neighborhood[1] = getPixel(image, x, ym1, zm1, w, h, d);
		neighborhood[2] = getPixel(image, xp1, ym1, zm1, w, h, d);

		neighborhood[3] = getPixel(image, xm1, y, zm1, w, h, d);
		neighborhood[4] = getPixel(image, x, y, zm1, w, h, d);
		neighborhood[5] = getPixel(image, xp1, y, zm1, w, h, d);

		neighborhood[6] = getPixel(image, xm1, yp1, zm1, w, h, d);
		neighborhood[7] = getPixel(image, x, yp1, zm1, w, h, d);
		neighborhood[8] = getPixel(image, xp1, yp1, zm1, w, h, d);
	}

	/**
	 * Get 4 neighborhood of a pixel in a 3D image (0 border conditions) Longhand,
	 * hard-coded for speed. This neighbourhood contains the set of pixels that have
	 * already been visited by the cursor in the current plane as it raster scans in
	 * an x-y order.
	 *
	 * @param neighborhood a neighbourhood in the image.
	 * @param image        3D image (int[][])
	 * @param x            x- coordinate
	 * @param y            y- coordinate
	 * @param z            z- coordinate (in image stacks the indexes start at 1)
	 * @param w            width of the image.
	 * @param h            image height
	 * @param d            image depth
	 */
	private static void get4Neighborhood(final int[] neighborhood, final int[][] image, final int x, final int y,
			final int z, final int w, final int h, final int d) {
		final int xm1 = x - 1;
		final int xp1 = x + 1;
		final int ym1 = y - 1;

		neighborhood[0] = getPixel(image, xm1, ym1, z, w, h, d);
		neighborhood[1] = getPixel(image, x, ym1, z, w, h, d);
		neighborhood[2] = getPixel(image, xp1, ym1, z, w, h, d);

		neighborhood[3] = getPixel(image, xm1, y, z, w, h, d);
	}

	/**
	 * Get a 3 neighbourhood from the label array
	 * 
	 * @param neighborhood the neighbour labels
	 * @param image label array
	 * @param x x position of the centre
	 * @param y y position of the centre
	 * @param z z position of the centre
	 * @param w image width
	 * @param h image height
	 * @param d image depth
	 */
	private static void get3Neighborhood(final int[] neighborhood, final int[][] image, final int x, final int y,
			final int z, final int w, final int h, final int d) {
		neighborhood[0] = getPixel(image, x - 1, y, z, w, h, d);
		neighborhood[1] = getPixel(image, x, y - 1, z, w, h, d);
		neighborhood[2] = getPixel(image, x, y, z - 1, w, h, d);
	}

	/**
	 * Get a 2 neighbourhood from the label array
	 * 
	 * @param neighborhood the neighbour labels
	 * @param image label array
	 * @param x x position of the centre
	 * @param y y position of the centre
	 * @param z z position of the centre
	 * @param w image width
	 * @param h image height
	 * @param d image depth
	 */
	private static void get2Neighborhood(final int[] neighborhood, final int[][] image, final int x, final int y,
			final int z, final int w, final int h, final int d) {
		neighborhood[0] = getPixel(image, x - 1, y, z, w, h, d);
		neighborhood[1] = getPixel(image, x, y - 1, z, w, h, d);
	}

	/**
	 * Get a 1 neighbourhood from the label array
	 * 
	 * @param neighborhood the neighbour labels
	 * @param image label array
	 * @param x x position of the centre
	 * @param y y position of the centre
	 * @param z z position of the centre
	 * @param w image width
	 */
	private static void get1Neighborhood(final int[] neighborhood, final int[][] image, final int x, final int y,
			final int z, final int w) {
		neighborhood[0] = image[z - 1][x + y * w];
	}

	/**
	 * Get pixel in 3D image (0 border conditions)
	 *
	 * @param image 3D image
	 * @param x     x- coordinate
	 * @param y     y- coordinate
	 * @param z     z- coordinate (in image stacks the indexes start at 1)
	 * @param w     width of the image.
	 * @param h     height of the image.
	 * @param d     depth of the image.
	 * @return corresponding pixel (0 if out of image)
	 */
	private static int getPixel(final int[][] image, final int x, final int y, final int z, final int w, final int h,
			final int d) {
		if (withinBounds(x, y, z, w, h, d))
			return image[z][x + y * w];

		return 0;
	}
	
	/**
	 * Get pixel in 3D image  - no bounds checking
	 *
	 * @param image 3D image
	 * @param x     x- coordinate
	 * @param y     y- coordinate
	 * @param z     z- coordinate (in image stacks the indexes start at 1)
	 * @param w     width of the image.
	 * @return corresponding pixel (0 if out of image)
	 */
	private static int getPixel(final int[][] image, final int x, final int y, final int z, final int w) {
			return image[z][x + y * w];
	}

	/**
	 * checks whether a pixel at (m, n, o) is within the image boundaries
	 * 
	 * 26- and 6-neighbourhood version
	 * 
	 * @param m x coordinate
	 * @param n y coordinate
	 * @param o z coordinate
	 * @param w image width
	 * @param h image height
	 * @param d image depth
	 * @return true if the pixel is within the image bounds
	 */
	private static boolean withinBounds(final int m, final int n, final int o, final int w, final int h, final int d) {
		return (m >= 0 && m < w && n >= 0 && n < h && o >= 0 && o < d);
	}

	/**
	 * Find the minimum value among neighbours and the current ID value
	 * 
	 * @param neighbourhood a label neighbourhood
	 * @param ID current ID counter
	 * @return minimum label of neighbours and ID
	 */
	private static int getMinTag(final int[] neighbourhood, final int ID) {
		int minTag = ID;
		for (final int tagv : neighbourhood) {
			if (tagv == 0)
				continue;
			if (tagv < minTag)
				minTag = tagv;
		}
		return minTag;
	}

	/**
	 * Increase the length of the list of label HashSets to accommodate the full
	 * range of IDs
	 * 
	 * @param map Single chunk's list of label buckets
	 * @param ID current ID value
	 * @param IDoffset this chunk's ID offset
	 * @param nextIDOffset ID offset of the next chunk
	 */
	private static void expandMap(final List<IntHashSet> map, final int ID,
			final int IDoffset, final int nextIDOffset) {
		if (ID >= nextIDOffset) {
			throw new IllegalArgumentException("ID "+ID+" is greater than the allowed range (max "+(nextIDOffset-1)+")");
		}
		while (ID - IDoffset >= map.size()) {
			final IntHashSet set = new IntHashSet();
			set.add(map.size() + IDoffset);
			map.add(set);
		}
	}

	/**
	 * @return number of particles in the image
	 */
	public int getNParticles() {
		return nParticles;
	}

	/**
	 * @return binary work array containing foreground and background pixels
	 */
	public byte[][] getWorkArray() {
		return workArray;
	}

}
