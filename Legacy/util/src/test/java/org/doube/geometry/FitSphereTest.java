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
package org.doube.geometry;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class FitSphereTest {

	@Test
	public void testFitSphere() {
		for (double r = 1; r < 5; r += Math.random()) {
			for (double x = -2; x < 2; x += Math.random()) {
				for (double y = -2; y < 2; y += Math.random()) {
					for (double z = -2; z < 2; z += Math.random()) {
						for (double theta = -Math.PI; theta < Math.PI; theta += Math.random()) {
							final double[][] points = FitEllipsoid.testEllipsoid(r, r, r, theta, x, y, z, 0.001, 10,
									true);
							final double[] sphere = FitSphere.fitSphere(points);
							final double[] expected = { x, y, z, r };
							assertArrayEquals(expected, sphere, 1e-2);
						}
					}
				}
			}
		}
	}

}
