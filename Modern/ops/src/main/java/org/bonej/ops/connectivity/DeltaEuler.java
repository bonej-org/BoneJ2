package org.bonej.ops.connectivity;

import net.imagej.ImgPlus;
import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imglib2.type.BooleanType;
import org.scijava.plugin.Plugin;

/**
 * An Op which calculates the images's contribution to the euler characteristic of the structure
 * to which it's connected (Δ(χ)).
 * <p>
 * Calculated by counting the intersections of foreground elements and the edges of the "stack".
 *
 * @see EulerCharacteristic EulerCharacteristic
 * @author Michael Doube
 * @author Richard Domander 
 */
@Plugin(type = Op.class, name = "deltaEuler")
public class DeltaEuler<B extends BooleanType> extends AbstractBinaryFunctionOp<ImgPlus<B>, Integer, Integer> {
    @Override
    public Integer compute2(final ImgPlus<B> imgPlus, final Integer eulerCharacteristic) {
        return 0;
    }
}
