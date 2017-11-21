
package org.bonej.ops.ellipsoid;

import static org.junit.Assert.assertEquals;

import java.util.List;

import clojure.core.Vec;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import org.junit.AfterClass;
import org.junit.Test;
import org.scijava.vecmath.Vector3d;

/**
 * Tests for {@link FindEllipsoidOp}.
 * <p>
 * A bit uncertain of how much error margin I can attribute to voxelization
 * issues.
 * </p>
 *
 * @author Alessandro Felder
 */
public class FindEllipsoidOpTest {

	private static final ImageJ IMAGE_J = new ImageJ();

	@Test
	public void testEllipsoidInSmallSphere() {
		long[] imageDimensions = { 12, 12, 12 };
		Vector3d imageCentrePixel = new Vector3d(Math.floor(imageDimensions[0] /
			2.0), Math.floor(imageDimensions[1] / 2.0), Math.floor(
				imageDimensions[2] / 2.0));
		long[] semiAxes = { 4, 4, 4 };

		final Img<BitType> img = ArrayImgs.bits(imageDimensions[0],
			imageDimensions[1], imageDimensions[2]);
		final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Ellipsoid test image");
		Cursor<BitType> cursor = imgPlus.localizingCursor();

		while (cursor.hasNext()) {
			cursor.fwd();
			long[] coordinates = new long[3];
			cursor.localize(coordinates);
			if (coordinates[2] == 0 || coordinates[2] == imageDimensions[2] - 1)
				continue;
			double x = imageCentrePixel.getX() - coordinates[0];
			double y = imageCentrePixel.getY() - coordinates[1];
			double z = imageCentrePixel.getZ() - coordinates[2];

			boolean inEllipsoid = ((x * x) / (semiAxes[0] * semiAxes[0]) + (y * y) /
				(semiAxes[1] * semiAxes[1]) + (z * z) / (semiAxes[2] *
					semiAxes[2]) <= 1.0);
			if (inEllipsoid) {
				cursor.get().setOne();
			}
		}

		FindEllipsoidOp<BitType> findEllipsoidOp = new FindEllipsoidOp<>();
		findEllipsoidOp.setEnvironment(IMAGE_J.op());

		findEllipsoidOp.setInput1(imgPlus);
		Vector3d imageCentrePoint = new Vector3d(imageCentrePixel);
		imageCentrePoint.add(new Vector3d(0.5, 0.5, 0.5));
		findEllipsoidOp.setInput2(imageCentrePoint);

		Ellipsoid maxEllipsoid = findEllipsoidOp.calculate();

		assertEquals(semiAxes[0], maxEllipsoid.getA(), 1.0 + 1.0e-12);
		assertEquals(semiAxes[1], maxEllipsoid.getB(), 1.0 + 1.0e-12);
		assertEquals(semiAxes[2], maxEllipsoid.getC(), 1.0 + 1.0e-12);
	}

	@Test
	public void testContactPointsInCylinder() {
		long[] imageDimensions = { 101, 101, 101 };
		Vector3d imageCentre = new Vector3d(Math.floor(imageDimensions[0] / 2.0),
			Math.floor(imageDimensions[1] / 2.0), Math.floor(imageDimensions[2] /
				2.0));
		long cylinderRadius = 8;

		final Img<BitType> img = ArrayImgs.bits(imageDimensions[0],
			imageDimensions[1], imageDimensions[2]);
		final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Cylinder test image");
		Cursor<BitType> cursor = imgPlus.localizingCursor();

		while (cursor.hasNext()) {
			cursor.fwd();
			long[] coordinates = new long[3];
			cursor.localize(coordinates);

			if (coordinates[2] == 0 || coordinates[2] == imageDimensions[2] - 1)
				continue;
			double x = imageCentre.getX() - coordinates[0];
			double y = imageCentre.getY() - coordinates[1];
			double distanceFromCentreLine = x * x + y * y;
			if (distanceFromCentreLine <= cylinderRadius * cylinderRadius) cursor
				.get().setOne();
		}

		FindEllipsoidOp<BitType> findEllipsoidOp = new FindEllipsoidOp<>();
		findEllipsoidOp.setEnvironment(IMAGE_J.op());

		findEllipsoidOp.setInput1(imgPlus);
		findEllipsoidOp.setInput2(imageCentre);

		Ellipsoid maxEllipsoid = findEllipsoidOp.calculate();

		// expected value is pixel next to axis-aligned circle.
		assertEquals(cylinderRadius, maxEllipsoid.getA(), 1 + 1e-12);
		assertEquals(cylinderRadius, maxEllipsoid.getB(), 1 + 1e-12);
		assertEquals(Math.floor(imageDimensions[2] / 2.0) - 1, maxEllipsoid.getC(),
			1e-12);
	}

	@Test
	public void testCubicFGFindsSphere() {
		long[] imageDimensions = { 100, 100, 100 };
		Vector3d imageCentre = new Vector3d(Math.floor(imageDimensions[0] / 2.0),
			Math.floor(imageDimensions[1] / 2.0), Math.floor(imageDimensions[2] /
				2.0));
		long halfSideLength = 25;

		final Img<BitType> img = ArrayImgs.bits(imageDimensions[0],
			imageDimensions[1], imageDimensions[2]);
		final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Cylinder test image");
		Cursor<BitType> cursor = imgPlus.localizingCursor();

		while (cursor.hasNext()) {
			cursor.fwd();
			long[] coordinates = new long[3];
			cursor.localize(coordinates);

			if (coordinates[2] == 0 || coordinates[2] == imageDimensions[2] - 1)
				continue;
			double x = imageCentre.getX() - coordinates[0];
			double y = imageCentre.getY() - coordinates[1];
			double z = imageCentre.getZ() - coordinates[2];
			if (Math.abs(x) <= halfSideLength && Math.abs(y) <= halfSideLength && Math
				.abs(z) <= halfSideLength) cursor.get().setOne();

		}

		FindEllipsoidOp<BitType> findEllipsoidOp = new FindEllipsoidOp<>();
		findEllipsoidOp.setEnvironment(IMAGE_J.op());

		findEllipsoidOp.setInput1(imgPlus);
		findEllipsoidOp.setInput2(imageCentre);

		Ellipsoid maxEllipsoid = findEllipsoidOp.calculate();

		// expected value is pixel next to axis-aligned circle.
		assertEquals(halfSideLength, maxEllipsoid.getA(), 1 + 1e-12);
		assertEquals(halfSideLength, maxEllipsoid.getB(), 1 + 1e-12);
		assertEquals(halfSideLength, maxEllipsoid.getC(), 1 + 1e-12);
	}

	@Test
	public void testRayInEmptyImage() {
		final Img<BitType> img = ArrayImgs.bits(10, 10, 10);
		final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Empty test image");

		FindEllipsoidOp<BitType> FindEllipsoidOp = new FindEllipsoidOp<>();
		FindEllipsoidOp.setInput1(imgPlus);

		Vector3d lastFGVoxelAlongRay = FindEllipsoidOp.findFirstPointInBGAlongRay(
			new Vector3d(1, 0, 0), new Vector3d(5, 5, 5));

		assertEquals(5, lastFGVoxelAlongRay.getX(), 1.0e-12);
		assertEquals(5, lastFGVoxelAlongRay.getY(), 1.0e-12);
		assertEquals(5, lastFGVoxelAlongRay.getZ(), 1.0e-12);
	}

	@Test
	public void testRayInForegroundOnlyImage() {
		final Img<BitType> img = ArrayImgs.bits(10, 10, 10);
		final ImgPlus<BitType> imgPlus = new ImgPlus<>(img,
			"Foreground only test image");
		Cursor<BitType> cursor = imgPlus.cursor();

		cursor.forEachRemaining(BitType::setOne);

		FindEllipsoidOp<BitType> FindEllipsoidOp = new FindEllipsoidOp<>();
		FindEllipsoidOp.setInput1(imgPlus);

		Vector3d lastFGVoxelAlongRay = FindEllipsoidOp.findFirstPointInBGAlongRay(
			new Vector3d(1, 0, 0), new Vector3d(5, 5, 5));

		assertEquals(9, lastFGVoxelAlongRay.getX(), 1.0e-12);
		assertEquals(5, lastFGVoxelAlongRay.getY(), 1.0e-12);
		assertEquals(5, lastFGVoxelAlongRay.getZ(), 1.0e-12);
	}

	@Test
	public void testRayInSphere() {
		final Img<BitType> img = ArrayImgs.bits(101, 101, 101);
		final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Sphere test image");
		IntervalView<BitType> imgCentre = Views.interval(imgPlus, new long[] { 25,
			25, 25 }, new long[] { 75, 75, 75 });
		Cursor<BitType> cursor = imgCentre.localizingCursor();

		while (cursor.hasNext()) {
			cursor.fwd();
			long[] coordinates = new long[3];
			cursor.localize(coordinates);
			if ((50 - coordinates[0]) * (50 - coordinates[0]) + (50 -
				coordinates[1]) * (50 - coordinates[1]) + (50 - coordinates[2]) * (50 -
					coordinates[2]) <= 625) cursor.get().setOne();
		}

		FindEllipsoidOp<BitType> FindEllipsoidOp = new FindEllipsoidOp<>();
		FindEllipsoidOp.setInput1(imgPlus);

		Vector3d ray1 = new Vector3d(-4, -3, -5);
		ray1.normalize();
		Vector3d ray2 = new Vector3d(1, 1, 1);
		ray2.normalize();

		Vector3d alongRay1 = FindEllipsoidOp.findFirstPointInBGAlongRay(ray1,
			new Vector3d(50, 50, 50));
		Vector3d alongRay2 = FindEllipsoidOp.findFirstPointInBGAlongRay(ray2,
			new Vector3d(50, 50, 50));

		alongRay1 = FindEllipsoidOp.getFlooredVector3d(alongRay1);
		alongRay2 = FindEllipsoidOp.getFlooredVector3d(alongRay2);

		alongRay1.scaleAdd(-1.0, new Vector3d(50, 50, 50));
		alongRay2.scaleAdd(-1.0, new Vector3d(50, 50, 50));
		assertEquals(26, alongRay1.length(), 0.2);
		assertEquals(26, alongRay2.length(), 0.2);
	}

	// main method for manual visual testing
	public static void main(String[] args) {
		List<Vector3d> spiralVectors = FindEllipsoidOp
			.getGeneralizedSpiralSetOnSphere(700);
		spiralVectors.stream().map(Vector3d::toString).forEach(System.out::println);
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}
}
