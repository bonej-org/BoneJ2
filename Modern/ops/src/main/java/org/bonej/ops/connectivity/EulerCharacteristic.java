package org.bonej.ops.connectivity;

import net.imagej.ImgPlus;
import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractUnaryFunctionOp;
import net.imglib2.type.BooleanType;
import org.scijava.plugin.Plugin;

/**
 * An Op which calculates the euler characteristic (χ) of the given image.
 * Euler characteristic is a number that describes a topological space's structure.
 * <p>
 * The algorithm here are based on the following articles:
 * Toriwaki J, Yonekura T (2002) Euler Number and Connectivity Indexes of a Three Dimensional Digital Picture.
 * Forma 17: 183-209.
 * <a href="http://www.scipress.org/journals/forma/abstract/1703/17030183.html">
 * http://www.scipress.org/journals/forma/abstract/1703/17030183.html</a>
 *
 * @implNote Assuming that there's only one continuous foreground particle in the image
 * @author Michael Doube
 * @author Richard Domander 
 */
@Plugin(type = Op.class, name = "eulerCharacteristic")
public class EulerCharacteristic<B extends BooleanType> extends AbstractUnaryFunctionOp<ImgPlus<B>, Integer> {
    @Override
    public Integer compute1(final ImgPlus<B> input) {
        return 0;
    }
}
