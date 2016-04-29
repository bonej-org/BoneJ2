package org.bonej.wrapperPlugins;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.net.MalformedURLException;

import org.junit.Test;
import org.python.apache.xerces.util.URI;
import org.scijava.log.LogService;
import org.scijava.platform.PlatformService;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;

/**
 * @author Richard Domander
 */
public class HelpTest {
	@Test
	public void testOpenHelpPageOpensDialogWhenURLisMalformed() throws Exception {
		final PlatformService platformService = mock(PlatformService.class);
		doThrow(new MalformedURLException()).when(platformService).open(null);
		final UIService uiService = mock(UIService.class);
		final LogService logService = mock(LogService.class);

		Help.openHelpPage(null, platformService, uiService, logService);

		// verify that showDialog was called
		verify(uiService).showDialog("Help page could not be opened: invalid address",
				DialogPrompt.MessageType.ERROR_MESSAGE);

		// verify that the exception was logged
		verify(logService).error(any(URI.MalformedURIException.class));
	}

}