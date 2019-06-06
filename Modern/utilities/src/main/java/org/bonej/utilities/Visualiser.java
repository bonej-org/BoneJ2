/*
BSD 2-Clause License
Copyright (c) 2019, Michael Doube
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
package org.bonej.utilities;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3d;
import org.scijava.vecmath.Color3f;
import org.scijava.vecmath.Point3f;

import customnode.CustomPointMesh;
import ij3d.Image3DUniverse;

/**
 * Convenience methods for displaying data.
 * 
 * Helpful for debugging and for users to visualise their results.
 * 
 * @author Michael Doube
 *
 */
public class Visualiser {
	/**
	 * Plot a set of 3D coordinates in Benjamin Schmidt's ImageJ 3D Viewer
	 *
	 * @param points
	 *            float[][] n x 3 array of 3D (x,y,z) coordinates
	 * @param title
	 *            String name of the dataset
	 *
	 */
	public static void display3DPoints(final double[][] points, String title) {
		final int nPoints = points.length;
		// Create a CustomMesh from the coordinates
		final List<Point3f> mesh = new ArrayList<>();
		for (int i = 0; i < nPoints; i++) {
			mesh.add(new Point3f((float) points[i][0], (float) points[i][1], (float) points[i][2]));
		}

		final CustomPointMesh cm = new CustomPointMesh(mesh);
		final Color3f green = new Color3f(0.0f, 0.5f, 0.0f);
		cm.setColor(green);
		cm.setPointSize(1);

		// Create a universe
		final Image3DUniverse univ = new Image3DUniverse();
		
		// Add the points
		univ.addCustomMesh(cm, title).setLocked(true);
		
		//show the universe
		univ.show();
	}
	
	public static void display3DPoints(List<Vector3d> vectors, String title) {
		double[][] points = new double[vectors.size()][3];
		int i = 0;
		for (Vector3d v : vectors) {
			points[i][0] = v.x;
			points[i][1] = v.y;
			points[i][2] = v.z;
			i++;
		}
		display3DPoints(points, title);
	}
}
