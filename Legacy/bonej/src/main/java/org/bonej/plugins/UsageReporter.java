/*
 * #%L
 * BoneJ utility classes.
 * %%
 * Copyright (C) 2007 - 2016 Michael Doube, BoneJ developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package org.bonej.plugins;

import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Random;

/**
 * UsageReporter class
 * Copyright 2012 Michael Doube
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import ij.IJ;
import ij.Prefs;

/**
 * Prepare and send a report to be logged by Google Analytics event tracking
 *
 * Should be called in a PlugIn's run() method as
 * UsageReporter.reportEvent(this).send()
 *
 * @author Michael Doube
 *
 */
public class UsageReporter {
	public static final UsageReporter INSTANCE = new UsageReporter();
	/**
	 * BoneJ version
	 *
	 * FIXME: it is fragile to have the version hard-coded here. Create a
	 * BoneJApp instead.
	 */
	public static final String BONEJ_VERSION = "1.5.0";

	private static final String ga = "http://www.google-analytics.com/__utm.gif?";
	private static final String utmwv = "utmwv=5.2.5&";
	private static final String utmhn = "utmhn=bonej.org&";
	private static final String utmcs = "utmcs=" + Charset.defaultCharset() + "&";
	private static final String utmac = "utmac=UA-366405-8&";
	private static final String utmdt = "utmdt=bonej.org%20Usage%20Statistics&";
	private static final String utmt = "utmt=event&";
	private static final String utmul = "utmul=" + getLocaleString() + "&";
	private static final String utmje = "utmje=0&";
	private static final String utmfl = "utmfl=11.1%20r102&";
	private static final String utmr = "utmr=-&";
	private static final String utmp = "utmp=%2Fstats&";

	private static String bonejSession;
	private static String utmcnr = "";
	private static String utme;
	private static String utmn;
	private static String utms;
	private static String utmsr;
	private static String utmvp;
	private static String utmsc;
	private static int session;
	private static String utmcc;
	private static String cookie;
	private static String cookie2;
	private static String firstTime;
	private static long lastTime = 0;
	private static long thisTime = 0;

	private static Random random;

	private static String utmhid;

	/**
	 * Constructor used by singleton pattern. Report variables that relate to
	 * single sessions are set here
	 */
	private UsageReporter() {
		random = new Random();
		if (!Prefs.get(ReporterOptions.OPTOUTKEY, false))
			return;
		bonejSession = Prefs.get(ReporterOptions.SESSIONKEY, Integer.toString(new Random().nextInt(1000)));
		int inc = Integer.parseInt(bonejSession);
		inc++;
		bonejSession = Integer.toString(inc);
		Prefs.set(ReporterOptions.SESSIONKEY, inc);

		final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		GraphicsEnvironment ge;
		ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		int width = 0;
		int height = 0;
		if (!ge.isHeadlessInstance()) {
			final GraphicsDevice[] screens = ge.getScreenDevices();
			for (int i = 0; i < screens.length; i++) {
				final GraphicsConfiguration[] gc = screens[i].getConfigurations();
				for (final GraphicsConfiguration g : gc) {
					width = Math.max(g.getBounds().x + g.getBounds().width, width);
					height = Math.max(g.getBounds().y + g.getBounds().height, height);
				}
			}
		}

		utmsr = "utmsr=" + screenSize.width + "x" + screenSize.height + "&";
		utmvp = "utmvp=" + width + "x" + height + "&";
		utmsc = "utmsc=24-bit&";
	}

	/**
	 * Sets the instance variables to appropriate values based on the system
	 * parameters and method arguments.
	 *
	 * @param category
	 *            Google Analytics event category classification
	 * @param action
	 *            Google Analytics event action classification
	 * @param label
	 *            Google Analytics event label classification
	 * @param value
	 *            Google Analytics event value - an integer used for sum and
	 *            average statistics
	 * @return The instance of UsageReporter ready to send() a report
	 */
	public static UsageReporter reportEvent(final String category, final String action, final String label,
			final Integer value) {
		if (!Prefs.get(ReporterOptions.OPTOUTKEY, false))
			return INSTANCE;
		utms = "utms=" + session + "&";
		session++;
		final String val = (value == null) ? "" : "(" + value.toString() + ")";
		utme = "utme=5(" + category + "*" + action + "*" + label + ")" + val + "&";
		utmn = "utmn=" + random.nextInt(Integer.MAX_VALUE) + "&";
		utmhid = "utmhid=" + random.nextInt(Integer.MAX_VALUE) + "&";

		final long time = System.currentTimeMillis() / 1000;
		lastTime = thisTime;
		if (lastTime == 0)
			lastTime = time;
		thisTime = time;

		if (utmcnr == "")
			utmcnr = "utmcn=1&";
		else
			utmcnr = "utmcr=1&";

		utmcc = getCookieString();
		return INSTANCE;
	}

	/**
	 * Prepare the instance for sending a report on a specific class; its name
	 * (.getClass().getName()) is added to the 'action' field of the report,
	 * category is "Plugin Usage" and label is the BoneJ version string
	 *
	 * @param o
	 *            Class to report on
	 * @return The instance of UsageReporter ready to send() a report
	 */
	public static UsageReporter reportEvent(final Object o) {
		return reportEvent("Plugin%20Usage", o.getClass().getName(), BONEJ_VERSION, null);
	}

	/**
	 * Create a string of cookie data for the gif URL
	 *
	 * @return cookie string
	 */
	private static String getCookieString() {
		// seems to be a bug in Prefs.getInt, so are Strings wrapped in
		// Integer.toString()
		cookie = Prefs.get(ReporterOptions.COOKIE, Integer.toString(random.nextInt(Integer.MAX_VALUE)));
		cookie2 = Prefs.get(ReporterOptions.COOKIE2, Integer.toString(random.nextInt(Integer.MAX_VALUE)));
		firstTime = Prefs.get(ReporterOptions.FIRSTTIMEKEY, Integer.toString(random.nextInt(Integer.MAX_VALUE)));
		final String cc = "utmcc=__utma%3D" + cookie + "." + cookie2 + "." + firstTime + "." + lastTime + "." + thisTime
				+ "." + bonejSession + "%3B%2B__utmz%3D" + cookie + "." + thisTime // not
																					// correct,
																					// but
																					// a
																					// best
																					// guess
				+ ".79.42.utmcsr%3Dgoogle%7Cutmccn%3D(organic)%7C"
				+ "utmcmd%3Dorganic%7Cutmctr%3DBoneJ%20Usage%20Reporter%3B";
		return cc;
	}

	/**
	 * Send the report to Google Analytics in the form of an HTTP request for a
	 * 1-pixel GIF with lots of parameters set
	 */
	public void send() {
		if (!isAllowed())
			return;
		try {
			final URL url = new URL(ga + utmwv + utms + utmn + utmhn + utmt + utme + utmcs + utmsr + utmvp + utmsc
					+ utmul + utmje + utmfl + utmcnr + utmdt + utmhid + utmr + utmp + utmac + utmcc);
			if (IJ.debugMode)
				IJ.log(url.toString());
			final URLConnection uc = url.openConnection();
			uc.setRequestProperty("User-Agent", userAgentString());
			if (IJ.debugMode)
				IJ.log(uc.getRequestProperty("User-Agent"));
			final BufferedReader in = new BufferedReader(new InputStreamReader(uc.getInputStream()));
			String inputLine;
			while ((inputLine = in.readLine()) != null) {
				if (IJ.debugMode)
					IJ.log(inputLine);
				inputLine.length();
			}
			in.close();
		} catch (final MalformedURLException e) {
			if (IJ.debugMode)
				e.printStackTrace();
		} catch (final IOException e) {
			if (IJ.debugMode)
				e.printStackTrace();
		}
	}

	private String userAgentString() {
		String os = "";

		// Handle Mac OSes on PPC and Intel
		if (IJ.isMacintosh()) {
			String arch = System.getProperty("os.arch");
			if (arch.contains("x86") || arch.contains("i386"))
				arch = "Intel";
			else if (arch.contains("ppc"))
				arch = arch.toUpperCase();
			os = "Macintosh; " + arch + " " + System.getProperty("os.name") + " " + System.getProperty("os.version");
			// Handle Windows using the NT version number
		} else if (IJ.isWindows()) {
			os = "Windows NT " + System.getProperty("os.version");
			// Handle Linux and everything else
		} else {
			os = System.getProperty("os.name") + " " + System.getProperty("os.version") + " "
					+ System.getProperty("os.arch");
		}

		final String browser = "Java/" + System.getProperty("java.version");
		final String vendor = System.getProperty("java.vendor");
		final String locale = getLocaleString();

		final String ua = browser + " (" + os + "; " + locale + ") " + vendor;

		return ua;
	}

	private static String getLocaleString() {
		String locale = Locale.getDefault().toString();
		locale = locale.replace("_", "-");
		locale = locale.toLowerCase(Locale.ENGLISH);
		return locale;
	}

	private boolean isAllowed() {
		if (!Prefs.get(ReporterOptions.OPTOUTSET, false))
			new ReporterOptions().run("");
		return Prefs.get(ReporterOptions.OPTOUTKEY, true);
	}
}
