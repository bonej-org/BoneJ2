
package org.bonej.wrapperPlugins.wrapperUtils;

import static org.bonej.wrapperPlugins.wrapperUtils.UsageReporterOptions.OPTINKEY;
import static org.bonej.wrapperPlugins.wrapperUtils.UsageReporterOptions.OPTINSET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bonej.wrapperPlugins.SlowWrapperTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.scijava.command.CommandService;
import org.scijava.plugin.PluginService;
import org.scijava.prefs.PrefService;

/**
 * Tests for {@link UsageReporter}.
 *
 * @author Richard Domander
 */
public class UsageReporterTest {

	@Category(SlowWrapperTest.class)
	@Test
	public void testIsAllowedOptInFalse() {
		// SETUP
		final PrefService prefs = mock(PrefService.class);
		when(prefs.getBoolean(UsageReporterOptions.class, OPTINSET, false))
			.thenReturn(true);
		when(prefs.getBoolean(UsageReporterOptions.class, OPTINKEY, false))
			.thenReturn(false);
		final PluginService plugins = mock(PluginService.class);
		final CommandService commands = mock(CommandService.class);
		final UsageReporter reporter = UsageReporter.getInstance(prefs, plugins,
			commands);

		// EXECUTE
		final boolean allowed = reporter.isAllowed();

		// VERIFY
		// OPTINSET save data should been queried
		verify(prefs, timeout(1000)).getBoolean(UsageReporterOptions.class,
			OPTINSET, false);
		// OPTINKEY save data should been queried
		verify(prefs, timeout(1000)).getBoolean(UsageReporterOptions.class,
			OPTINKEY, false);
		assertFalse(allowed);
	}

	@Category(SlowWrapperTest.class)
	@Test
	public void testIsAllowedOptInTrue() {
		// SETUP
		final PrefService prefs = mock(PrefService.class);
		when(prefs.getBoolean(UsageReporterOptions.class, OPTINSET, false))
			.thenReturn(true);
		when(prefs.getBoolean(UsageReporterOptions.class, OPTINKEY, false))
			.thenReturn(true);
		final PluginService plugins = mock(PluginService.class);
		final CommandService commands = mock(CommandService.class);
		final UsageReporter reporter = UsageReporter.getInstance(prefs, plugins,
			commands);

		// EXECUTE
		final boolean allowed = reporter.isAllowed();

		// VERIFY
		// OPTINSET save data should been queried
		verify(prefs, timeout(1000)).getBoolean(UsageReporterOptions.class,
			OPTINSET, false);
		// OPTINKEY save data should been queried
		verify(prefs, timeout(1000)).getBoolean(UsageReporterOptions.class,
			OPTINKEY, false);
		assertTrue(allowed);
	}

	@Category(SlowWrapperTest.class)
	@Test
	public void testIsAllowedPermissionNotSought() {
		// SETUP
		final PrefService prefs = mock(PrefService.class);
		when(prefs.getBoolean(UsageReporterOptions.class, OPTINSET, false))
				.thenReturn(false);
		when(prefs.getBoolean(UsageReporterOptions.class, OPTINKEY, false))
				.thenReturn(false);
		final PluginService plugins = mock(PluginService.class);
		final CountingPrompter prompter = new CountingPrompter();
		final UsageReporter reporter = UsageReporter.getInstance(prefs, plugins, prompter);

		// EXECUTE
		final boolean allowed = reporter.isAllowed();

		// VERIFY
		// UsageReporterOptions should have been run
		assertEquals("User should have been prompted for opt in (once)", 1, prompter.promptCount);
		// OPTINSET save data should been queried
		verify(prefs, timeout(1000)).getBoolean(UsageReporterOptions.class,
				OPTINSET, false);
		// OPTINKEY save data should been queried
		verify(prefs, timeout(1000)).getBoolean(UsageReporterOptions.class,
				OPTINKEY, false);
		assertFalse(allowed);
	}

	@Test
	public void testIsAllowedPermissionSought() {
		// SETUP
		final PrefService prefs = mock(PrefService.class);
		when(prefs.getBoolean(UsageReporterOptions.class, OPTINSET, false))
			.thenReturn(true);
		when(prefs.getBoolean(UsageReporterOptions.class, OPTINKEY, false))
			.thenReturn(false);
		final PluginService plugins = mock(PluginService.class);
		final CountingPrompter prompter = new CountingPrompter();
		final UsageReporter reporter = UsageReporter.getInstance(prefs, plugins, prompter);

		// EXECUTE
		reporter.isAllowed();

		// VERIFY
		// UsageReporterOptions should not have been run
		assertEquals("Usage reporter should not have prompted user", 0, prompter.promptCount);
	}

	private static class CountingPrompter implements OptInPrompter {
		private int promptCount;

		@Override
		public void promptUser() {
			promptCount++;
		}
	}
}
