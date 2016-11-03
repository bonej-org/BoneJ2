
package org.bonej.ops;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.imagej.ImageJ;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.vecmath.Tuple3d;
import org.scijava.vecmath.Vector3d;

/**
 * Unit tests for the CentroidLinAlg3d Op
 *
 * @author Richard Domander
 */
public class CentroidLinAlg3dTest {

	private static final ImageJ IMAGE_J = new ImageJ();
	private static UnaryFunctionOp<Collection<? extends Tuple3d>, Vector3d> centroidOp;

	@BeforeClass
	public static void oneTimeSetUp() {
		centroidOp = (UnaryFunctionOp) Functions.unary(IMAGE_J.op(),
			CentroidLinAlg3d.class, Tuple3d.class, List.class);
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}

	@Test(expected = NullPointerException.class)
	public void
		testCentroidLinAlg3dThrowsNullPointerExceptionIfCollectionIsNull()
	{
		centroidOp.compute1(null);
	}

	@Test
	public void testCentroidLinAlg3dWithEmptyCollection() {
		final List<Vector3d> emptyVectors = new ArrayList<>();

		final Tuple3d result = centroidOp.compute1(emptyVectors);

		assertTrue("Result should be (NaN, NaN, NaN) - x is not", Double.isNaN(
			result.x));
		assertTrue("Result should be (NaN, NaN, NaN) - y is not", Double.isNaN(
			result.y));
		assertTrue("Result should be (NaN, NaN, NaN) - z is not", Double.isNaN(
			result.z));
	}

	@Test
	public void testCentroidLinAlg3dWithSingleVector() {
		final Vector3d expected = new Vector3d(1.0, 2.0, 3.0);
		final List<Vector3d> vectors = Collections.singletonList(expected);

		final Tuple3d result = centroidOp.compute1(vectors);

		assertEquals("Result should equal the single input vector", expected,
			result);
	}

	@Test
	public void testCentroidLinAlg3d() {
		final Vector3d expected = new Vector3d(0.5, 0.5, 0.5);
		final List<Vector3d> cubeVectors = asList(new Vector3d(0.0, 0.0, 0.0),
			new Vector3d(1.0, 0.0, 0.0), new Vector3d(1.0, 1.0, 0.0), new Vector3d(
				0.0, 1.0, 0.0), new Vector3d(0.0, 0.0, 1.0), new Vector3d(1.0, 0.0,
					1.0), new Vector3d(1.0, 1.0, 1.0), new Vector3d(0.0, 1.0, 1.0));

		final Tuple3d result = centroidOp.compute1(cubeVectors);

		assertEquals("Incorrect centroid vector", expected, result);
	}
}
