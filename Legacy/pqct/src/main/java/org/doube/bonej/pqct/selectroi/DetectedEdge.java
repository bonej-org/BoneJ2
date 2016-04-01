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
import java.util.Vector;

@SuppressWarnings(value = { "serial", "unchecked" }) // Unchecked for obtaining
														// Vector<Object> as a
														// returnvalue

public class DetectedEdge implements Comparable<DetectedEdge> {
	public Vector<Integer> iit; // indexes for x-coordinates
	public Vector<Integer> jiit; // indexes for y-coordinates
	public int area;
	public int length;

	public DetectedEdge(final Vector<Integer> iit, final Vector<Integer> jiit, final int area) {
		this.iit = iit;
		this.jiit = jiit;
		this.length = iit.size();
		this.area = area;
	}

	@Override
	public int compareTo(final DetectedEdge o) {
		final int returnValue = 0;
		if (o == null || this == null) {
			throw new NullPointerException();
		}
		if (this.area == o.area) {
			return 0;
		}
		return this.area < o.area ? -1 : 1;
	}

}
