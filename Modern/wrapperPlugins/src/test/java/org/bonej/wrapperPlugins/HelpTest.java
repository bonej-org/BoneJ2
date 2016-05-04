package org.bonej.wrapperPlugins;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.scijava.ui.DialogPrompt.MessageType.ERROR_MESSAGE;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.junit.Test;
import org.scijava.log.LogService;
import org.scijava.platform.PlatformService;
import org.scijava.ui.UIService;

/**
 * Unit tests for the Help plugin
 *
 * @author Richard Domander
 */
public class HelpTest {
	@Test
	public void testOpenHelpPageOpensDialogWhenPlatformServiceNull() throws Exception {
		final UIService uiService = mock(UIService.class);
		final LogService logService = mock(LogService.class);

		Help.openHelpPage("http://bone.org", null, uiService, logService);

		// verify that showDialog was called
		verify(uiService).showDialog("Help page could not be opened: no platform service", ERROR_MESSAGE);

		// verify that the exception was logged
		verify(logService).error("Platform service is null");
	}

	@Test
	public void testOpenHelpPageOpensDialogWhenURLMalformed() throws Exception {
		final String badAddress = "htp://bone.org";
		final PlatformService platformService = mock(PlatformService.class);
		final UIService uiService = mock(UIService.class);
		final LogService logService = mock(LogService.class);

		Help.openHelpPage(badAddress, platformService, uiService, logService);

		// verify that showDialog was called
		verify(uiService).showDialog("Help page could not be opened: invalid address (" + badAddress + ")",
				ERROR_MESSAGE);

		// verify that the exception was logged
		verify(logService).error(any(MalformedURLException.class));
	}

	@Test
	public void testOpenHelpPageOpensDialogWhenIOException() throws Exception {
		final PlatformService platformService = mock(PlatformService.class);
		doThrow(new IOException()).when(platformService).open(any(URL.class));
		final UIService uiService = mock(UIService.class);
		final LogService logService = mock(LogService.class);

		Help.openHelpPage("http://bone.org", platformService, uiService, logService);

		// verify that showDialog was called
		verify(uiService).showDialog("An unexpected error occurred while trying to open the help page", ERROR_MESSAGE);

		// verify that the exception was logged
		verify(logService).error(any(IOException.class));
	}
}