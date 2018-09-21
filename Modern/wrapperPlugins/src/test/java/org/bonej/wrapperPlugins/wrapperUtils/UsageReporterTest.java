
package org.bonej.wrapperPlugins.wrapperUtils;

import static org.bonej.wrapperPlugins.wrapperUtils.UsageReporterOptions.OPTINKEY;
import static org.bonej.wrapperPlugins.wrapperUtils.UsageReporterOptions.OPTINSET;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.bonej.wrapperPlugins.SlowWrapperTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.scijava.command.CommandModule;
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
	public void testIsAllowedPermissionNotSought() throws ExecutionException,
		InterruptedException
	{
		// SETUP
		final PrefService prefs = mock(PrefService.class);
		when(prefs.getBoolean(UsageReporterOptions.class, OPTINSET, false))
			.thenReturn(false);
		when(prefs.getBoolean(UsageReporterOptions.class, OPTINKEY, false))
			.thenReturn(false);
		final PluginService plugins = mock(PluginService.class);
		@SuppressWarnings("unchecked")
		final Future<CommandModule> future = mock(Future.class);
		final CommandModule module = mock(CommandModule.class);
		when(module.isCanceled()).thenReturn(false);
		when(future.get()).thenReturn(module);
		final CommandService commands = mock(CommandService.class);
		when(commands.run(UsageReporterOptions.class, true)).thenReturn(future);
		final UsageReporter reporter = UsageReporter.getInstance(prefs, plugins,
			commands);

		// EXECUTE
		final boolean allowed = reporter.isAllowed();

		// VERIFY
		// UsageReporterOptions should have been run
		verify(commands, timeout(1000)).run(UsageReporterOptions.class, true);
		// OPTINSET save data should been queried
		verify(prefs, timeout(1000)).getBoolean(UsageReporterOptions.class,
			OPTINSET, false);
		// OPTINKEY save data should been queried
		verify(prefs, timeout(1000)).getBoolean(UsageReporterOptions.class,
			OPTINKEY, false);
		assertFalse(allowed);
	}

	@Test
	public void testIsAllowedPermissionSought() throws ExecutionException,
		InterruptedException
	{
		// SETUP
		final PrefService prefs = mock(PrefService.class);
		when(prefs.getBoolean(UsageReporterOptions.class, OPTINSET, false))
			.thenReturn(true);
		when(prefs.getBoolean(UsageReporterOptions.class, OPTINKEY, false))
			.thenReturn(false);
		final PluginService plugins = mock(PluginService.class);
		@SuppressWarnings("unchecked")
		final Future<CommandModule> future = mock(Future.class);
		final CommandModule module = mock(CommandModule.class);
		when(module.isCanceled()).thenReturn(false);
		when(future.get()).thenReturn(module);
		final CommandService commands = mock(CommandService.class);
		when(commands.run(UsageReporterOptions.class, true)).thenReturn(future);
		final UsageReporter reporter = UsageReporter.getInstance(prefs, plugins,
			commands);

		// EXECUTE
		reporter.isAllowed();

		// VERIFY
		// UsageReporterOptions should not have been run
		verify(commands, timeout(1000).times(0)).run(UsageReporterOptions.class,
			true);
	}
}
