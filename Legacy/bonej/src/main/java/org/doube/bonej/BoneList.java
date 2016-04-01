/*
 * #%L
 * BoneJ: open source tools for trabecular geometry and whole bone shape analysis.
 * %%
 * Copyright (C) 2007 - 2016 Michael Doube, BoneJ developers. See also individual class @authors.
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
package org.doube.bonej;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ij.ImagePlus;

/**
 * Utility methods for making lists of bones and guessing which bone is in an
 * image
 *
 * @author Michael Doube
 *
 */
public class BoneList {

	/**
	 * List of bone names
	 */
	// Only add new bone names to the END of this list
	private final static String[] boneList = { "unknown", "scapula", "humerus", "radius", "ulna", "metacarpal",
			"pelvis", "femur", "tibia", "fibula", "metatarsal", "calcaneus", "tibiotarsus", "tarsometatarsal",
			"sacrum" };

	/**
	 * Return the array of bone names
	 *
	 * @return array of bone names
	 */
	public final static String[] get() {
		return boneList;
	}

	/**
	 * Guess from the image title which bone is in the image
	 *
	 * @param imp
	 * @return integer code relating to the position of the bone's name in the
	 *         bone list
	 */
	public static int guessBone(final ImagePlus imp) {
		final String boneString = imp.getTitle();
		return guessBone(boneString);
	}

	/**
	 * Return the boneID of a bone in boneList that matches the input string
	 *
	 * @param boneString
	 * @return
	 */
	public static int guessBone(final String boneString) {
		int boneID = 0;
		for (int n = 0; n < boneList.length; n++) {
			final Pattern p = Pattern.compile(boneList[n], Pattern.CASE_INSENSITIVE);
			final Matcher m = p.matcher(boneString);
			if (m.find()) {
				boneID = n;
				continue;
			}
		}
		return boneID;
	}
}
