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

import org.scijava.prefs.PrefService;

/**
 * Prepare and send a report to be logged by 
 * <a href="https://developers.google.com/analytics/resources/concepts/gaConceptsTrackingOverview">
 * Google Analytics event tracking</a>
 * <p>
 * Should be called in a PlugIn's run() method as
 * UsageReporter.reportEvent(this).send()
 * </p>
 * 
 * @author Michael Doube
 * @author Richard Domander
 */
public class UsageReporter {
	/**
	 * BoneJ version FIXME: it is fragile to have the version hard-coded here.
	 * Create a BoneJApp instead.
	 */
	private static final String BONEJ_VERSION = "2.0.0-experimental";
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
	private static String utme;
	private static String utmn;
	private static String utmsr;
	private static String utmvp;
	private static String utmsc;
	/** iterated on each new event */
	private static int session = 0;
	private static String utms = "utms=" + session + "&";
	private static String utmcc;
	private static long thisTime = 0;
	private static long lastTime = 0;

	private static boolean isFirstRun = true;

	private static String utmhid;
	private static PrefService prefs;
	private static UsageReporter instance;

	private UsageReporter() {}

	public static UsageReporter getInstance(final PrefService prefs) {
		if (prefs == null) {
			throw new NullPointerException("PrefService cannot be null");
		}
		if (instance == null) {
			instance = new UsageReporter();
		}
		UsageReporter.prefs = prefs;
		return instance;
	}

	/**
	 * Send the report to Google Analytics in the form of an HTTP request for a
	 * 1-pixel GIF with lots of parameters set
	 */
	private void send() {
	}

	/**
	 * Sets the instance variables to appropriate values based on the system
	 * parameters and method arguments and makes the URL request to Google
	 *
	 * @param category Google Analytics event category classification
	 * @param action Google Analytics event action classification
	 * @param label Google Analytics event label classification
	 * @param value Google Analytics event value - an integer used for sum and
	 *          average statistics
	 */
	private void reportEvent(final String category,
							 final String action, final String label, final Integer value,
							 final PrefService prefs)
	{
		//check if user has opted in and die if user has opted out
		if (!isAllowed(prefs)) {
			System.out.println("Usage reporting forbidden by user\n");
			return;
		}
		if (isFirstRun) {
			initSessionVariables(prefs);
		}
		
		//set 
		utms = "utms=" + session + "&";
		session++;
		final String val = (value == null) ? "" : "(" + value + ")";
		utme = "utme=5(" + category + "*" + action + "*" + label + ")" + val + "&";
		utmn = "utmn=" + random.nextInt(Integer.MAX_VALUE) + "&";
		utmhid = "utmhid=" + random.nextInt(Integer.MAX_VALUE) + "&";

		final long time = System.currentTimeMillis() / 1000;
		lastTime = thisTime;
		if (lastTime == 0) lastTime = time;
		thisTime = time;

		if ("".equals(utmcnr)) utmcnr = "utmcn=1&";
		else utmcnr = "utmcr=1&";

		utmcc = getCookieString(prefs);
		
		//make the connection and send the report as a long URL
		System.out.println("Sending report.\n");
		
		final URL url;
		final URLConnection uc;
		try {
			System.out.println("Usage reporting approved by user, preparing URL");
			url = new URL(ga + utmwv + utms + utmn + utmhn + utmt + utme +
				utmcs + utmsr + utmvp + utmsc + utmul + utmje + utmcnr + utmdt +
				utmhid + utmr + utmp + utmac + utmcc);
			uc = url.openConnection();
		}
		catch (final IOException e) {
			System.out.println(e.getMessage()+"\n");
			throw new AssertionError("Check your static Strings!");
//			if (logger.getLevel() >= LogLevel.INFO) {
//				logService.error(e.getMessage());
//			}
		}
		
		uc.setRequestProperty("User-Agent", userAgentString());
//			if (logger.getLevel() < LogLevel.INFO) {
////				return;
//			}
			
//			logService.info(url.toString());
			System.out.println(url.toString()+"\n");
//			logService.info(uc.getRequestProperty("User-Agent"));
			System.out.println(uc.getRequestProperty("User-Agent")+"\n");
		try (final BufferedReader reader = new BufferedReader(
				new InputStreamReader(uc.getInputStream())))
		{
//				logService.info(reader.lines());
//				System.out.println(reader.lines());
				reader.lines().forEachOrdered(item -> System.out.println(item));
		}
		catch (final IOException e) {
//			if (logger.getLevel() >= LogLevel.INFO) {
//				logService.error(e.getMessage());
//			}
			System.out.println(e.getMessage()+"\n");
		}
		send();
	}

	private static void initSessionVariables(final PrefService prefs) {
		System.out.println("First run of Usage Reporter for this BoneJ session.\n");
		final int bonejSession = prefs.getInt(UsageReporterOptions.class,
				UsageReporterOptions.SESSIONKEY, 0);
		System.out.print("bonejSession = "+bonejSession+"\n");
		prefs.put(UsageReporterOptions.class,
				UsageReporterOptions.SESSIONKEY, bonejSession + 1);
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
	 * Reports a the usage of a plug-in to bonej.org
	 *
	 * @param className Name of the reporting plug-in's class
	 */
	public void reportEvent(final String className) {
		reportEvent("Plugin%20Usage", className, BONEJ_VERSION, null, prefs);
	}

	/**
	 * Create a string of cookie data for the gif URL
	 *
	 * @return cookie string
	 */
	private static String getCookieString(final PrefService prefs) {
		final int cookie = prefs.getInt(UsageReporterOptions.class,
			UsageReporterOptions.COOKIE, random.nextInt(Integer.MAX_VALUE));
		final int cookie2 = prefs.getInt(UsageReporterOptions.class,
			UsageReporterOptions.COOKIE2, random.nextInt(Integer.MAX_VALUE));
		final long firstTime = prefs.getInt(UsageReporterOptions.class,
			UsageReporterOptions.FIRSTTIMEKEY, random.nextInt(Integer.MAX_VALUE));
		final int bonejSession = prefs.getInt(UsageReporterOptions.class,
				UsageReporterOptions.SESSIONKEY, 0);
		// thisTime is not correct, but a best guess
		return "utmcc=__utma%3D" + cookie + "." + cookie2 + "." + firstTime + "." +
			lastTime + "." + thisTime + "." + bonejSession + "%3B%2B__utmz%3D" +
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

	private static String userAgentString() {
		final String os;
		String osname = System.getProperty("os.name");
		boolean isWin = osname.startsWith("Windows");
		boolean isMac = !isWin && osname.startsWith("Mac");
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
			os = osname + " " + System.getProperty(
				"os.version") + " " + System.getProperty("os.arch");
		}

		final String browser = "Java/" + System.getProperty("java.version");
		final String vendor = System.getProperty("java.vendor");
		final String locale = getLocaleString();

		return browser + " (" + os + "; " + locale + ") " + vendor;
	}

	/**
	 * Check whether user has given permission to collect usage data
	 * 
	 * @return true only if the user has given explicit permission to send usage data
	 */
	private static boolean isAllowed(final PrefService prefs) {
		final boolean permissionSought = prefs.getBoolean(UsageReporterOptions.class,
			UsageReporterOptions.OPTINSET, false);
		if (!permissionSought) {
			System.out.println("User permission has not been sought, requesting it...\n");
			new UsageReporterOptions().run();
		}
		return prefs.getBoolean(UsageReporterOptions.class,
			UsageReporterOptions.OPTINKEY, false);
	}
}
