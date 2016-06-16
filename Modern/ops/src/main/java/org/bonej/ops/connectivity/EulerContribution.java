package org.bonej.ops.connectivity;

import net.imagej.ImgPlus;
import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractUnaryFunctionOp;
import net.imglib2.type.BooleanType;
import org.scijava.plugin.Plugin;

/**
 * An Op which calculates the images's contribution to the euler characteristic of the structure
 * to which it's connected.
 * <p>
 * Calculated by counting the intersections of foreground elements and the edges of the "stack".
 *
 * @author Michael Doube
 * @author Richard Domander 
 */
@Plugin(type = Op.class, name = "eulerContribution")
public class EulerContribution<B extends BooleanType> extends AbstractUnaryFunctionOp<ImgPlus<B>, Integer> {
    @Override
    public Integer compute1(final ImgPlus<B> imgPlus) {
        return Integer.MIN_VALUE;
    }
}
