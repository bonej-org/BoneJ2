package org.bonej.wrapperPlugins.wrapperUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests for {@link UsageReporter}.
 *
 * @author Richard Domander
 */
public class UsageReporterTest {

	@Test
	public void testReporterDoesNotReportIfUserHasOptedOut() {
		// SETUP
		final FakePrefsAccess prefsAccess = new FakePrefsAccess(false, true);
		final UsageReporter reporter = UsageReporter.getInstance(prefsAccess, new CountingPrompter());

		// EXECUTE
		final boolean allowed = reporter.isAllowed();

		// VERIFY
		assertFalse(allowed);
	}

	@Test
	public void testReporterReportsIfUserHasOptedIn() {
		// SETUP
		final FakePrefsAccess prefsAccess = new FakePrefsAccess(true, true);
		final UsageReporter reporter = UsageReporter.getInstance(prefsAccess, new CountingPrompter());

		// EXECUTE
		final boolean allowed = reporter.isAllowed();

		// VERIFY
		assertTrue(allowed);
	}

	@Test
	public void testReporterPromptsOptInIfNotPromptedYet() {
		// SETUP
		final CountingPrompter prompter = new CountingPrompter();
		final FakePrefsAccess prefsAccess = new FakePrefsAccess(false, false);
		final UsageReporter reporter = UsageReporter.getInstance(prefsAccess, prompter);

		// EXECUTE
		reporter.isAllowed();

		// VERIFY
		assertEquals("User should have been prompted for opt in (once)", 1, prompter.promptCount);
	}

	@Test
	public void testReporterDoesNotPromptOptInIfAlreadyOptedOut() {
		// SETUP
		final CountingPrompter prompter = new CountingPrompter();
		final FakePrefsAccess prefsAccess = new FakePrefsAccess(false, true);
		final UsageReporter reporter = UsageReporter.getInstance(prefsAccess, prompter);

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

	private static class FakePrefsAccess implements UsagePrefsAccess {

		private final boolean optedIn;
		private final boolean optInPrompted;

		private FakePrefsAccess(final boolean optedIn, final boolean optInPrompted) {
			this.optedIn = optedIn;
			this.optInPrompted = optInPrompted;
		}

		@Override
		public boolean readOptedIn() {
			return optedIn;
		}

		@Override
		public boolean readOptInPrompted() {
			return optInPrompted;
		}

		@Override
		public int readCookie() {
			return 0;
		}

		@Override
		public int readCookie2() {
			return 0;
		}

		@Override
		public long readFirstTime() {
			return 0;
		}

		@Override
		public int readSessionKey() {
			return 0;
		}

		@Override
		public void writeSessionKey(int key) {}
	}
}
