package org.bonej.wrapperPlugins;

import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imglib2.type.logic.BitType;
import org.bonej.ops.EulerCharacteristic;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * A wrapper UI class for the Connectivity Ops
 *
 * @author Richard Domander 
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Connectivity")
public class ConnectivityWrapper extends ContextCommand {
    @Parameter
    private ImgPlus<BitType> inputImage;

    @Parameter
    private OpService opService;

    @Override
    public void run() {
        final Integer eulerCharacteristic = (Integer) opService.run(EulerCharacteristic.class, inputImage);
    }
}
