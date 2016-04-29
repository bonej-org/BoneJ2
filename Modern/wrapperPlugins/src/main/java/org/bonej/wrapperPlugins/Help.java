package org.bonej.wrapperPlugins;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import net.imagej.ImageJ;

import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;

/**
 * A plugin for opening the user documentation for BoneJ2
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
        openHelpPage("http://bonej.org/", platformService, uiService, logService);
    }

    public static void main(String... args) {
        final ImageJ imageJ = net.imagej.Main.launch();
        imageJ.command().run(Help.class, true);
    }

    public static void openHelpPage(String url, PlatformService platformService, UIService uiService,
            LogService logService) {
        try {
            URL helpUrl = new URL(url);
            platformService.open(helpUrl);
        } catch (final MalformedURLException mue) {
            uiService.showDialog("Help page could not be opened: invalid address",
                                 DialogPrompt.MessageType.ERROR_MESSAGE);
            logService.error(mue);
        } catch (final IOException e) {
            uiService.showDialog("An unexpected error occurred while trying to open the help page",
                                 DialogPrompt.MessageType.ERROR_MESSAGE);
            logService.error(e);
        }
    }
}
