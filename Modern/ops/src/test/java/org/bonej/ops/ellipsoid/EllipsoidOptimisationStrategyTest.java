/*-
 * #%L
 * Ops created for BoneJ2
 * %%
 * Copyright (C) 2015 - 2023 Michael Doube, BoneJ developers
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
package org.bonej.ops.ellipsoid;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.stream.Stream;

import net.imagej.ops.special.function.Functions;
import org.bonej.ops.ellipsoid.constrain.AnchorEllipsoidConstrain;
import org.bonej.ops.ellipsoid.constrain.NoEllipsoidConstrain;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.junit.Test;

import net.imagej.ops.AbstractOpTest;

public class EllipsoidOptimisationStrategyTest extends AbstractOpTest {

	@Test
	public void testSkeletonPointOptimisationStrategy() {
		final byte[][] sphere = getSphere(10);
		final QuickEllipsoid ellipsoid = (QuickEllipsoid) ops.run(EllipsoidOptimisationStrategy.class, sphere,
				new Vector3d(20.5, 20.5, 20.5), new double[]{40, 40, 40},new NoEllipsoidConstrain());
		assertNotNull(ellipsoid);
		assertEquals(9.5,ellipsoid.getRadii()[0],1.5);
		assertEquals(9.5,ellipsoid.getRadii()[1],1.5);
		assertEquals(9.5,ellipsoid.getRadii()[2],1.5);
	}

	private byte[][] getSphere(int radius) {
		double centrePointCoordinate = 10 + radius + 0.5;
		Vector3d sphereCentre = new Vector3d(centrePointCoordinate, centrePointCoordinate, centrePointCoordinate);

		int imgDim = 2 * radius + 20;
		byte[][] sphere = new byte[imgDim][imgDim * imgDim];
		for (int z = 0; z < imgDim; z++) {
            for (int y = 0; y < imgDim; y++){
                for (int x = 0; x < imgDim; x++) {
                    final Vector3d position = new Vector3d(x + 0.5, y + 0.5, z + 0.5);
                    position.sub(sphereCentre);
                    if (position.lengthSquared() <= radius * radius) {
                        sphere[z][y * imgDim + x] = (byte) 255;
                    }
                }
            }
		}

		return sphere;
	}

	/**
	 * test for {@link EllipsoidOptimisationStrategy#calculateTorque(QuickEllipsoid, Iterable)}
	 *
	 * see testFindContactPoints in this file for explanation on what contact points are used
	 * based on these points, the torque is expected to be zero
	 *
	 */

	@Test
	public void testCalculateTorque() {
		double[] radii = {1,2.1,3.1};
		double[] centre = {3,3,2};
		QuickEllipsoid e = new QuickEllipsoid(radii,centre,new double[][]{{1,0,0},{0,1,0},{0,0,1}});

		double [][] vectors = new double[7][3];
		vectors[0][0] = 1; //x-direction
		vectors[1][1] = 1; //y-direction
		vectors[2][2] = 1; //z-direction
		vectors[3][0] = -1; //-x-direction
		vectors[4][1] = -1; //-y-direction
		vectors[5][2] = -1; //-z-direction

		final byte[][] cubeImage = getCuboidImage();

		//EXECUTE
		final ArrayList<double[]> contactPoints = new ArrayList<>();
		EllipsoidOptimisationStrategy.findContactPointsForGivenDirections(e, contactPoints, vectors, cubeImage,6,6,6);
		final double[] torque = EllipsoidOptimisationStrategy.calculateTorque(e, contactPoints);

		assertEquals(0,torque[0],1e-12);
		assertEquals(0,torque[1],1e-12);
		assertEquals(0,torque[2],1e-12);
	}

	/**
	 * creates a byte array slice representation of a foreground cuboid that touches the outside of an image on one side (z==0).
	 * @return byte[][] with z index in first index, and xy plane array in second index
	 */
	private byte[][] getCuboidImage() {
		int dimension = 6;
		final byte[][] cubeImage = new byte[dimension][dimension*dimension];

		for(int x=0;x<dimension;x++) {
			for (int y = 0; y < dimension; y++) {
				for (int z = 0; z < dimension; z++) {
					if (x == 0 || x == 5 || y == 0 || y == 5 || z == 5)
					{
						continue;
					}
					cubeImage[z][y * dimension + x] = (byte) 255;//will be -1 as byte has values in [-128,127]
				}
			}
		}
		return cubeImage;
	}

	/**
	 * test for {@link EllipsoidOptimisationStrategy#findContactPoints(QuickEllipsoid, ArrayList, byte[][], int, int, int)}
	 *
	 * uses a 6x6x6 byte array image representation of a cuboid that touches the image boundary at z=0
	 * and otherwise has a surface with 1 pixel distance from the image boundary
	 *
	 * axis-aligned ellipsoid, centred at 3,3,2 and with x,y,z direction axis lengths 1,2.1,3.1
	 * the six search directions are +- x-direction, +- y-direction and +- z-direction
	 * since the ellipsoid is narrower than the FG in x, and the FG touches the image boundary at z=0
	 * the ellipsoid will only find contact points in positive z and in both + and - y directions.
	 * The expected boundary points are (3,5.1,2),(3,3,5.1), and (3,0.9,2)
	 *
	 */
	@Test
	public void testFindContactPoints() {
		//SETUP
		final double[] radii = {1, 2.1, 3.1};
		final double[] centre = {3,3,2};
		QuickEllipsoid e = new QuickEllipsoid(radii,centre,new double[][]{{1,0,0},{0,1,0},{0,0,1}});

		double [][] vectors = new double[6][3];
		vectors[0][0] = 1; //x-direction
		vectors[1][1] = 1; //y-direction
		vectors[2][2] = 1; //z-direction
		vectors[3][0] = -1; //-x-direction
		vectors[4][1] = -1; //-y-direction
		vectors[5][2] = -1; //-z-direction

		final byte[][] cubeImage = getCuboidImage();

		double[][] expectedContact = {{3,5.1,2},{3,3,5.1},{3,0.9,2}};


		//EXECUTE
		final ArrayList<double[]> contactPoints = new ArrayList<>();
		EllipsoidOptimisationStrategy
				.findContactPointsForGivenDirections(e, contactPoints, vectors, cubeImage,6,6,6);

		//VERIFY
		assertEquals(3, contactPoints.size());
		Stream.of(0,1,2).forEach
				(i -> Stream.of(0,1,2).forEach(
						j -> assertEquals(expectedContact[i][j],contactPoints.get(i)[j],1e-12)));
	}

	/**
	 * test for {@link EllipsoidOptimisationStrategy#wiggle(QuickEllipsoid)} in a constrained setting
	 */
	@Test
	public void testWiggleSurfacePoint() {
		double[] radii = {1,2,3};
		double[] centre = {0,0,0};
		QuickEllipsoid e = new QuickEllipsoid(radii,centre,new double[][]{{1,0,0},{0,1,0},{0,0,1}});
		final AnchorEllipsoidConstrain anchorConstrain = new AnchorEllipsoidConstrain();
		anchorConstrain.preConstrain(e, new Vector3d(1,0,0));
		EllipsoidOptimisationStrategy.wiggle(e);
		anchorConstrain.postConstrain(e);
		assertTrue("Wiggle does not preserve surface point.",onSurface(e, new double[]{1,0,0}));
	}

	/**
	 * test for @link EllipsoidOptimisationStrategy#bump(QuickEllipsoid, Collection, double, double, double)}
	 */
	@Test
	public void testBumpSurfacePoint() {
		double[] radii = {1,2,3};
		double[] centre = {0,0,0};
		QuickEllipsoid e = new QuickEllipsoid(radii, centre, new double[][]{{1,0,0},{0,1,0},{0,0,1}});

		final EllipsoidOptimisationStrategy optimisation = (EllipsoidOptimisationStrategy) Functions.binary(ops, EllipsoidOptimisationStrategy.class, QuickEllipsoid.class,
				new byte[10][10],
				new Vector3d(),new long[]{10,10,1},  new AnchorEllipsoidConstrain());
		final ArrayList<double[]> contactPoints = new ArrayList<>();
		contactPoints.add(new double[]{0,0,3});
		final AnchorEllipsoidConstrain anchorConstrain = new AnchorEllipsoidConstrain();
		anchorConstrain.preConstrain(e, new Vector3d(1,0,0));
		optimisation.bump(e, contactPoints, new double[]{e.getCentre()[0], e.getCentre()[1], e.getCentre()[2]});
		anchorConstrain.postConstrain(e);
		assertTrue("Bump does not preserve surface point.",onSurface(e, new double[]{1,0,0}));
	}

	/**
	 * test for @link EllipsoidOptimisationStrategy#turn(QuickEllipsoid, ArrayList, byte[][], int, int, int)}
	 */
	@Test
	public void testTurnSurfacePoint() {
		double[] radii = {1,2,3};
		double[] centre = {0,0,0};
		QuickEllipsoid e = new QuickEllipsoid(radii, centre,new double[][]{{1,0,0},{0,1,0},{0,0,1}});

		final EllipsoidOptimisationStrategy optimisation = (EllipsoidOptimisationStrategy) Functions.binary(ops, EllipsoidOptimisationStrategy.class, QuickEllipsoid.class,
				new byte[10][10],
				new Vector3d(),new long[]{10,10,1},  new AnchorEllipsoidConstrain());final ArrayList<double[]> contactPoints = new ArrayList<>();
		contactPoints.add(new double[]{0,0,3});
		final AnchorEllipsoidConstrain anchorConstrain = new AnchorEllipsoidConstrain();
		anchorConstrain.preConstrain(e, new Vector3d(1,0,0));
		optimisation.turn(e,contactPoints, getCuboidImage(),6,6,6);
		anchorConstrain.postConstrain(e);

		assertTrue("Bump does not preserve surface point.",onSurface(e, new double[]{1,0,0}));
	}



	/**
	 * test for {@link EllipsoidOptimisationStrategy#isInvalid(QuickEllipsoid, int, int, int)}
	 *
	 * isInvalid can be true in three situations (too small, too large, too out of bounds),
	 * each of which are asserted here.
	 */
	@Test
	public void testIsInvalid()
	{
		//SET-UP
		QuickEllipsoid tooSmall = new QuickEllipsoid(new double[]{0.3,0.3,0.3},new double[]{50,50,50},new double[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}});
		QuickEllipsoid tooLarge = new QuickEllipsoid(new double[]{10,10,10},new double[]{50,50,50},new double[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}});
		//by sampling only in directions away from image coordinates, both surface points should be out-of-bounds, and the ellipsoid
		// is invalid as a consequence
		QuickEllipsoid tooFarOutOfBounds = new QuickEllipsoid(new double[]{2,2,2},new double[]{0,0,0},new double[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}});

		final EllipsoidOptimisationStrategy optimisation = (EllipsoidOptimisationStrategy) Functions.binary(ops, EllipsoidOptimisationStrategy.class, QuickEllipsoid.class,
				new byte[10][10],
				new Vector3d(),new long[]{10,10,1},  new NoEllipsoidConstrain(), new OptimisationParameters(2,0,0,0,0));

		//EXECUTE
		boolean tooSmallInvalid = optimisation.isInvalid(tooSmall, 100,100,100);
		boolean tooFarOutInvalid = optimisation.isInvalid(tooFarOutOfBounds, 100,100,100);
		boolean tooLargeInvalid = optimisation.isInvalid(tooLarge, 100,100,100);



		//VERIFY
		assertTrue("Too small ellipsoid is valid.", tooSmallInvalid);
		assertTrue("Too large ellipsoid is valid.", tooLargeInvalid);
		assertTrue("Too far out ellipsoid is valid.", tooFarOutInvalid);
	}

	/**
	 * @param e ellipsoid
	 * @param point point
	 * @return true if point is on ellipsoid surface, false otherwise
	 */
	private boolean onSurface(QuickEllipsoid e, double[] point) {

		final double[][] ev = e.getRotation();
		double[] c = e.getCentre();
		final double[] r = e.getRadii();

		final Matrix3d Q = new Matrix3d(
				ev[0][0],ev[0][1],ev[0][2],
				ev[1][0],ev[1][1],ev[1][2],
				ev[2][0],ev[2][1],ev[2][2]);
		final Matrix3d L = new Matrix3d(
				1.0/r[0]/r[0],0,0,
				0,1.0/r[1]/r[1],0,
				0,0,1.0/r[2]/r[2]);
		Matrix3d A = new Matrix3d();
		L.mul(Q,A);
		Matrix3d QT = new Matrix3d(Q);
		QT.transpose();
		QT.mul(A,A);

		Vector3d APMinusC = new Vector3d();
		Vector3d PMinusC = new Vector3d(point[0]-c[0],point[1]-c[1],point[2]-c[2]);
		A.transform(PMinusC, APMinusC);
		final double oneOnSurface = PMinusC.dot(APMinusC);

		return Math.abs(oneOnSurface-1.0)<1.e-12;
	}



}
