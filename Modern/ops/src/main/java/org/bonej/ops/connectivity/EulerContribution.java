package org.bonej.ops.connectivity;

import net.imagej.ImgPlus;
import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractUnaryFunctionOp;
import net.imglib2.type.BooleanType;
import org.scijava.plugin.Plugin;

/**
 * An Op which calculates the edge correction needed for the Euler characteristic of the image to approximate its
 * contribution to the whole image. That is, it's assumed that the image is a small part cut from a larger image.
 * <p>
 * Based on the article
 * Odgaard A, Gundersen HJG (1993) Quantification of connectivity in cancellous bone,
 * with special emphasis on 3-D reconstructions.
 * Bone 14: 173-182.
 * <a href="http://dx.doi.org/10.1016/8756-3282(93)90245-6">doi:10.1016/8756-3282(93)90245-6</a>
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
