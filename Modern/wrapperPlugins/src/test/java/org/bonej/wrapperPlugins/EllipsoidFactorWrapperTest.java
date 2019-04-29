package org.bonej.wrapperPlugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import net.imagej.ImgPlus;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.array.ArrayLocalizingCursor;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import org.bonej.ops.ellipsoid.QuickEllipsoid;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.junit.Test;

public class EllipsoidFactorWrapperTest {


    /**
     * test for {@link EllipsoidFactorWrapper#isInvalid(QuickEllipsoid, ArrayList, int, int, int)}
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
        double [][] vectors = new double[2][3];
        vectors[0][2] = -1; //-z-direction
        vectors[1][1] = -1; //-y-direction
        final double[][] surfacePoints = tooFarOutOfBounds.getSurfacePoints(vectors);
        final ArrayList<double[]> surfacePointList = new ArrayList<>();
        surfacePointList.addAll(Arrays.asList(surfacePoints));

        final EllipsoidFactorWrapper wrapper = new EllipsoidFactorWrapper();
        wrapper.stackVolume = 100;// -> too large condition depends on stackVolume
        wrapper.nVectors = 2;// -> out of bounds conditions depends on nVectors

        //EXECUTE
        boolean tooSmallInvalid = wrapper.isInvalid(tooSmall, new ArrayList<>(),100,100,100);
        boolean tooFarOutInvalid = wrapper.isInvalid(tooFarOutOfBounds, surfacePointList, 100,100,100);
        boolean tooLargeInvalid = wrapper.isInvalid(tooLarge, new ArrayList<>(), 100,100,100);



        //VERIFY
        assertTrue("Too small ellipsoid is valid.", tooSmallInvalid);
        assertTrue("Too large ellipsoid is valid.", tooLargeInvalid);
        assertTrue("Too far out ellipsoid is valid.", tooFarOutInvalid);
    }
    /**
     * test for {@link EllipsoidFactorWrapper#getAnchors(QuickEllipsoid[], ImgPlus)}
     *
     * tests the method on a simple 3x3x3 foreground cube
     * where one side of the cube is completely contained in an ellipsoid.
     */
    @Test
    public void testGetAnchors(){
        //SET-UP
        final ImgPlus<UnsignedByteType> cube3x3x3 = new ImgPlus<>(get3x3x3CubeIn5x5x5Img());
        double[] radii = new double[]{0.5, 3.0 / 2.0 * Math.sqrt(2.0), 3.0 / 2.0 * Math.sqrt(2.0)};
        double[] centre = new double[]{1.5, 2.5, 2.5};
        final QuickEllipsoid[] ellipsoids = {new QuickEllipsoid(radii, centre, new double[][]{{1, 0, 0}, {0, 1, 0}, {0, 0,
                1}})};

        //EXECUTE
        List<Vector3d> anchors = EllipsoidFactorWrapper.getAnchors(ellipsoids, cube3x3x3);

        //VERIFY
        assertTrue("Cube centre should not be an anchor.",anchors.stream().noneMatch(a -> a.x==2.5 && a.y==2.5 && a.z==2.5));
        assertTrue("Pixels with x==1.5 should be within ellipsoid, and therefore not anchors", anchors.stream().noneMatch(a -> a.x==1.5));
        assertEquals("Unexpected number of anchors found.", 17, anchors.size());
    }

    /**
     * @return a 5x5x5 ArrayImg which contains a 3x3x3 foreground cube at its centre.
     */
    private ArrayImg<UnsignedByteType, ByteArray> get3x3x3CubeIn5x5x5Img() {
        final ArrayImg<UnsignedByteType, ByteArray> cube3x3x3 = ArrayImgs.unsignedBytes(5, 5, 5);
        final ArrayLocalizingCursor<UnsignedByteType> cursor = cube3x3x3.localizingCursor();
        while(cursor.hasNext()){
            cursor.fwd();

            long[] position = new long[3];
            cursor.localize(position);
            Arrays.sort(position);
            if(position[0]>0 && position[2]<4){
                cursor.get().setInteger(255);
            }
        }
        return cube3x3x3;
    }

    /**
     * test for {@link EllipsoidFactorWrapper#findContactPoints(QuickEllipsoid, ArrayList, byte[][], int, int, int)}
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
        EllipsoidFactorWrapper wrapper = new EllipsoidFactorWrapper();
        final ArrayList<double[]> contactPoints = new ArrayList<>();
        wrapper.findContactPointsForGivenDirections(e, contactPoints, vectors, cubeImage,6,6,6);

        //VERIFY
        assertEquals(3, contactPoints.size());
        Stream.of(0,1,2).forEach
                (i -> Stream.of(0,1,2).forEach(
                        j -> assertEquals(expectedContact[i][j],contactPoints.get(i)[j],1e-12)));
    }

    /**
     * test for {@link EllipsoidFactorWrapper#calculateTorque(QuickEllipsoid, Iterable)}
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
        EllipsoidFactorWrapper wrapper = new EllipsoidFactorWrapper();
        final ArrayList<double[]> contactPoints = new ArrayList<>();
        wrapper.findContactPointsForGivenDirections(e, contactPoints, vectors, cubeImage,6,6,6);
        final double[] torque = EllipsoidFactorWrapper.calculateTorque(e, contactPoints);

        assertEquals(0,torque[0],1e-12);
        assertEquals(0,torque[1],1e-12);
        assertEquals(0,torque[2],1e-12);
    }

    /**
     * test for {@link EllipsoidFactorWrapper#wiggle(QuickEllipsoid)} in a constrained setting
     */
    @Test
    public void testWiggleSurfacePoint() {
        double[] radii = {1,2,3};
        double[] centre = {0,0,0};
        QuickEllipsoid e = new QuickEllipsoid(radii,centre,new double[][]{{1,0,0},{0,1,0},{0,0,1}});
        final EllipsoidFactorWrapper.AnchorConstrain anchorConstrain = new EllipsoidFactorWrapper.AnchorConstrain();
        anchorConstrain.preConstrain(e, new Vector3d(1,0,0));
        EllipsoidFactorWrapper.wiggle(e);
        anchorConstrain.postConstrain(e);
        assertTrue("Wiggle does not preserve surface point.",onSurface(e, new double[]{1,0,0}));
    }

    /**
     * test for @link EllipsoidFactorWrapper#bump(QuickEllipsoid, Collection, double, double, double)}
     */
    @Test
    public void testBumpSurfacePoint() {
        double[] radii = {1,2,3};
        double[] centre = {0,0,0};
        QuickEllipsoid e = new QuickEllipsoid(radii, centre, new double[][]{{1,0,0},{0,1,0},{0,0,1}});

        final EllipsoidFactorWrapper wrapper = new EllipsoidFactorWrapper();
        final ArrayList<double[]> contactPoints = new ArrayList<>();
        contactPoints.add(new double[]{0,0,3});
        final EllipsoidFactorWrapper.AnchorConstrain anchorConstrain = new EllipsoidFactorWrapper.AnchorConstrain();
        anchorConstrain.preConstrain(e, new Vector3d(1,0,0));
        wrapper.bump(e, contactPoints, new double[]{e.getCentre()[0], e.getCentre()[1], e.getCentre()[2]});
        anchorConstrain.postConstrain(e);
        assertTrue("Bump does not preserve surface point.",onSurface(e, new double[]{1,0,0}));
    }

    /**
     * test for @link EllipsoidFactorWrapper#turn(QuickEllipsoid, ArrayList, byte[][], int, int, int)}
     */
    @Test
    public void testTurnSurfacePoint() {
        double[] radii = {1,2,3};
        double[] centre = {0,0,0};
        QuickEllipsoid e = new QuickEllipsoid(radii, centre,new double[][]{{1,0,0},{0,1,0},{0,0,1}});

        final EllipsoidFactorWrapper wrapper = new EllipsoidFactorWrapper();
        final ArrayList<double[]> contactPoints = new ArrayList<>();
        contactPoints.add(new double[]{0,0,3});
        final EllipsoidFactorWrapper.AnchorConstrain anchorConstrain = new EllipsoidFactorWrapper.AnchorConstrain();
        anchorConstrain.preConstrain(e, new Vector3d(1,0,0));
        wrapper.turn(e,contactPoints, getCuboidImage(),6,6,6);
        anchorConstrain.postConstrain(e);

        assertTrue("Bump does not preserve surface point.",onSurface(e, new double[]{1,0,0}));
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
                    if (x != 0 && x != 5 && y != 0 && y != 5 && z != 5) //part of z==0 plane is on img boundary and FG
                    {
                        cubeImage[z][y * dimension + x] = (byte) 255;//will be -1 as byte has values in [-128,127]
                    }
                }
            }
        }
        return cubeImage;
    }
}
