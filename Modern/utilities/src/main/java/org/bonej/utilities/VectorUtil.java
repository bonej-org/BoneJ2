package org.bonej.utilities;

import org.joml.Vector3dc;

import java.util.stream.Stream;

public class VectorUtil {

    private VectorUtil() {}

    public static long[] toPixelGrid(final Vector3dc v) {
        return Stream.of(v.x(), v.y(),
                v.z()).mapToLong(x -> (long) x.doubleValue()).toArray();
    }
}
