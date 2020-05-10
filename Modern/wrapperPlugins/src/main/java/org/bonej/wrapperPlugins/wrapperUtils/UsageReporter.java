/*
BSD 2-Clause License
Copyright (c) 2018, Michael Doube, Richard Domander, Alessandro Felder
All rights reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.bonej.wrapperPlugins.wrapperUtils;

import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Random;

import org.scijava.command.CommandService;
import org.scijava.plugin.PluginService;
import org.scijava.prefs.PrefService;
import org.scijava.util.VersionUtils;

/**
 * Prepares and sends a report about BoneJ usage to be logged by <a href=
 * "https://developers.google.com/analytics/resources/concepts/gaConceptsTrackingOverview">
 * Google Analytics event tracking</a>
 *
 * @author Michael Doube
 * @author Richard Domander
 */
// TODO Instead of System.out print debug stuff with LogService
// TODO Instead of System.out print status messages with StatusService
// Don't make class final - breaks Mockito
public class UsageReporter {

	private static final String ga = "http://www.google-analytics.com/__utm.gif?";
	private static final String utmwv = "utmwv=5.2.5&";
	private static final String utmhn = "utmhn=bonej.org&";
	private static final String utmcs = "utmcs=" + Charset.defaultCharset() + "&";
	private static final String utmac = "utmac=UA-366405-8&";
	private static final String utmdt = "utmdt=bonej.org%20Usage%20Statistics&";
	private static final String utmt = "utmt=event&";
	private static final String utmul = "utmul=" + getLocaleString() + "&";
	private static final String utmje = "utmje=0&";
	private static final String utmr = "utmr=-&";
	private static final String utmp = "utmp=%2Fstats&";
	private static final Random random = new Random();
	private static String utmcnr = "";
	private static String utmsr;
	private static String utmvp;
	private static String utmsc;
	/** Incremented on each new event */
	private static int session;
	private static long thisTime;
	private static long lastTime;
	private static boolean isFirstRun = true;
	private static UsageReporter instance;
	private static String utms;
	private static String utmn;
	private static String utme;
	private static String utmhid;
	private static String utmcc;
	private OptInPrompter optInPrompter;
	private static UsagePrefsAccess usagePrefsAccess;

	private UsageReporter() {}

	/**
	 * Reports a the usage of a plug-in to bonej.org
	 *
	 * @param className Name of the reporting plug-in's class
	 */
	public void reportEvent(final String className) {
		if (!isAllowed()) {
			System.out.println("Usage reporting forbidden by user\n");
			return;
		}
		final String version = findVersion(className);
		reportEvent(className, version);
	}

	private String findVersion(final String pluginClassName) {
		String version = null;
		try {
			final Class<?> pluginClass = Class.forName(pluginClassName);
			version = VersionUtils.getVersion(pluginClass);
		} catch (ClassNotFoundException e) {
			// TODO Log
		}
		return version == null ? "" : version;
	}

	public static UsageReporter getInstance(final PrefService prefs, final PluginService pluginService,
											final CommandService commandService) {
		return UsageReporter.getInstance(new UsageIJ2PrefsAccess(prefs), new CommandOptInPrompter(commandService));
	}

	public static UsageReporter getInstance(final UsagePrefsAccess prefs,
											final OptInPrompter optInPrompter) {
		if (prefs == null) {
			throw new NullPointerException("PrefService cannot be null");
		}
		if (instance == null) {
			instance = new UsageReporter();
		} else {
			instance.optInPrompter = optInPrompter;
		}
		UsageReporter.usagePrefsAccess = prefs;
		return instance;
	}

	/**
	 * Create a string of cookie data for the gif URL
	 *
	 * @return cookie string
	 */
	private static String getCookieString() {
		final int cookie = usagePrefsAccess.readCookie();
		final int cookie2 = usagePrefsAccess.readCookie2();
		final long firstTime = usagePrefsAccess.readFirstTime();
		final int boneJSession = usagePrefsAccess.readSessionKey();
		// thisTime is not correct, but a best guess
		return "utmcc=__utma%3D" + cookie + "." + cookie2 + "." + firstTime + "." +
			lastTime + "." + thisTime + "." + boneJSession + "%3B%2B__utmz%3D" +
			cookie + "." + thisTime +
			".79.42.utmcsr%3Dgoogle%7Cutmccn%3D(organic)%7C" +
			"utmcmd%3Dorganic%7Cutmctr%3DBoneJ%20Usage%20Reporter%3B";
	}

	private static String getLocaleString() {
		String locale = Locale.getDefault().toString();
		locale = locale.replace("_", "-");
		locale = locale.toLowerCase(Locale.ENGLISH);
		return locale;
	}

	private static void initSessionVariables() {
		System.out.println("First run of Usage Reporter for this BoneJ session.\n");
		final int bonejSession = usagePrefsAccess.readSessionKey();
		System.out.print("bonejSession = " + bonejSession + "\n");
		usagePrefsAccess.writeSessionKey(bonejSession + 1);
		final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		final GraphicsEnvironment ge = GraphicsEnvironment
			.getLocalGraphicsEnvironment();
		int width = 0;
		int height = 0;
		if (!ge.isHeadlessInstance()) {
			final GraphicsDevice[] screens = ge.getScreenDevices();
			for (final GraphicsDevice screen : screens) {
				final GraphicsConfiguration[] gc = screen.getConfigurations();
				for (final GraphicsConfiguration g : gc) {
					width = Math.max(g.getBounds().x + g.getBounds().width, width);
					height = Math.max(g.getBounds().y + g.getBounds().height, height);
				}
			}
		}
		utmsr = "utmsr=" + screenSize.width + "x" + screenSize.height + "&";
		utmvp = "utmvp=" + width + "x" + height + "&";
		utmsc = "utmsc=24-bit&";
		isFirstRun = false;
	}

	/**
	 * Sets the instance variables to appropriate values based on the system
	 * parameters and method arguments and makes the URL request to Google
	 *
	 * @param action Google Analytics event action classification
	 * @param label Google Analytics event label classification
	 */
	private static void reportEvent(final String action, final String label) {
		if (isFirstRun) {
			initSessionVariables();
		}

		// set
		utms = "utms=" + session + "&";
		session++;
		utme = "utme=5(" + "Plugin%20Usage" + "*" + action + "*" + label + ")&";
		utmn = "utmn=" + random.nextInt(Integer.MAX_VALUE) + "&";
		utmhid = "utmhid=" + random.nextInt(Integer.MAX_VALUE) + "&";

		final long time = System.currentTimeMillis() / 1000;
		lastTime = thisTime;
		if (lastTime == 0) lastTime = time;
		thisTime = time;

		if ("".equals(utmcnr)) utmcnr = "utmcn=1&";
		else utmcnr = "utmcr=1&";

		utmcc = getCookieString();
		send();
	}

	/**
	 * Send the report to Google Analytics in the form of an HTTP request for a
	 * 1-pixel GIF with lots of parameters set
	 */
	private static void send() {
		System.out.println("Sending report.\n");
		final URL url;
		final URLConnection uc;
		try {
			System.out.println("Usage reporting approved by user, preparing URL");
			url = new URL(ga + utmwv + utms + utmn + utmhn + utmt + utme + utmcs +
				utmsr + utmvp + utmsc + utmul + utmje + utmcnr + utmdt + utmhid + utmr +
				utmp + utmac + utmcc);
			uc = url.openConnection();
		}
		catch (final IOException e) {
			System.out.println(e.getMessage() + "\n");
			throw new AssertionError("Check your static Strings!");
		}
		uc.setRequestProperty("User-Agent", userAgentString());
		System.out.println(url + "\n");
		System.out.println(uc.getRequestProperty("User-Agent") + "\n");
		try (final BufferedReader reader = new BufferedReader(new InputStreamReader(
			uc.getInputStream())))
		{
			reader.lines().forEachOrdered(System.out::println);
		}
		catch (final IOException e) {
			System.out.println(e.getMessage() + "\n");
		}
	}

	private static String userAgentString() {
		final String os;
		final String osName = System.getProperty("os.name");
		final boolean isWin = osName.startsWith("Windows");
		final boolean isMac = !isWin && osName.startsWith("Mac");
		if (isMac) {
			// Handle Mac OSes on PPC and Intel
			String arch = System.getProperty("os.arch");
			if (arch.contains("x86") || arch.contains("i386")) arch = "Intel";
			else if (arch.contains("ppc")) arch = arch.toUpperCase();
			os = "Macintosh; " + arch + " " + System.getProperty("os.name") + " " +
				System.getProperty("os.version");
		}
		else if (isWin) {
			// Handle Windows using the NT version number
			os = "Windows NT " + System.getProperty("os.version");
		}
		else {
			// Handle Linux and everything else
			os = osName + " " + System.getProperty("os.version") + " " + System
				.getProperty("os.arch");
		}

		final String browser = "Java/" + System.getProperty("java.version");
		final String vendor = System.getProperty("java.vendor");
		final String locale = getLocaleString();

		return browser + " (" + os + "; " + locale + ") " + vendor;
	}

	/**
	 * Check whether user has given permission to collect usage data
	 * 
	 * @return true only if the user has given explicit permission to send usage
	 *         data
	 */
	boolean isAllowed() {
		if (!usagePrefsAccess.readOptInPrompted()) {
			optInPrompter.promptUser();
		}
		return usagePrefsAccess.readOptedIn();
	}
}
