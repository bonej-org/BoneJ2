package org.bonej2.wrapperPlugins;

import net.imagej.ImageJ;

import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import java.io.IOException;
import java.net.URL;

/**
 * A plugin for opening user documentation for BoneJ2
 *
 * @author Richard Domander 
 */
@Plugin(type = Command.class, name = "BoneJ2Help", menuPath = "Help>About Plugins>BoneJ2")
public class Help implements Command {
    @Parameter
    private LogService logService;

    @Parameter
    private PlatformService platformService;

    @Parameter
    private UIService uiService;

    @Override public void run() {
        try {
            URL helpUrl = new URL("http://bonej.org/");
            platformService.open(helpUrl);
        } catch (final IOException e) {
            uiService.showDialog("An error occurred while trying to open the help page");
            logService.error(e);
        }
    }

    public static void main(String... args) {
        final ImageJ imageJ = net.imagej.Main.launch();
        imageJ.command().run(Help.class, true);
    }
}
