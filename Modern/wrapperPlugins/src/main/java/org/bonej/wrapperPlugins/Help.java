package org.bonej.wrapperPlugins;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import javax.annotation.Nullable;

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

	@Override
	public void run() {
		openHelpPage("http://bonej.org/", platformService, uiService, logService);
	}

	public static void main(String... args) {
		final ImageJ imageJ = net.imagej.Main.launch();
		imageJ.command().run(Help.class, true);
	}

	/**
	 * Opens the given help page using the PlatformService
	 *
	 * @see PlatformService#open(URL)
	 * @param url
	 *            The address of the help page
	 * @param platformService
	 *            PlatformService of the context
	 * @param uiService
	 *            UIService to show potential error messages
	 * @param logService
	 *            LogService to log potential exceptions, e.g.
	 *            MalformedURLException
	 */
	public static void openHelpPage(final String url, @Nullable final PlatformService platformService,
			@Nullable final UIService uiService, @Nullable final LogService logService) {
		if (platformService == null) {
			showErrorSafely(uiService, "Help page could not be opened: no platform service");
			if (logService != null) {
				logService.error("Platform service is null");
			}
			return;
		}

		try {
			URL helpUrl = new URL(url);
			platformService.open(helpUrl);
		} catch (final MalformedURLException mue) {
			showErrorSafely(uiService, "Help page could not be opened: invalid address (" + url + ")");
			if (logService != null) {
				logService.error(mue);
			}
		} catch (final IOException e) {
			showErrorSafely(uiService, "An unexpected error occurred while trying to open the help page");
			if (logService != null) {
				logService.error(e);
			}
		}
	}

	private static void showErrorSafely(@Nullable final UIService uiService, @Nullable final String message) {
		if (uiService == null) {
			return;
		}

		uiService.showDialog(message, DialogPrompt.MessageType.ERROR_MESSAGE);
	}
}
