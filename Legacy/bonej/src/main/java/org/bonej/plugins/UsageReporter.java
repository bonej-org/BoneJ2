/*-
 * #%L
 * Mavenized version of the BoneJ1 plugins
 * %%
 * Copyright (C) 2015 - 2020 Michael Doube, BoneJ developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
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

package org.bonej.plugins;

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

import org.scijava.util.VersionUtils;

import ij.IJ;
import ij.Prefs;

/**
 * Prepare and send a report to be logged by Google Analytics event tracking
 * <p>
 * Should be called in a PlugIn's run() method as
 * UsageReporter.reportEvent(this).send()
 * </p>
 * 
 * @author Michael Doube
 */
public final class UsageReporter {

	private static final UsageReporter INSTANCE = new UsageReporter();

	private static final String ga = "https://www.google-analytics.com/__utm.gif?";
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
	private static final Random random = new Random();
	private static String utmcnr = "";
	private static String utme;
	private static String utmn;
	private static String utmsr;
	private static String utmvp;
	private static String utmsc;
	private static int session = 0;
	private static String utms = "utms=" + session + "&";
	private static String utmcc;
	private static long thisTime = 0;
	private static long lastTime = 0;
	private static String bonejSession = Prefs.get(ReporterOptions.SESSIONKEY,
		Integer.toString(new Random().nextInt(1000)));
	private static String utmhid;

	/**
	 * Constructor used by singleton pattern. Report variables that relate to
	 * single sessions are set here
	 */
	private UsageReporter() {
		if (!Prefs.get(ReporterOptions.OPTOUTKEY, false)) return;
		bonejSession = Prefs.get(ReporterOptions.SESSIONKEY, Integer.toString(
			new Random().nextInt(1000)));
		int inc = Integer.parseInt(bonejSession);
		inc++;
		bonejSession = Integer.toString(inc);
		Prefs.set(ReporterOptions.SESSIONKEY, inc);

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
	}

	/**
	 * Send the report to Google Analytics in the form of an HTTP request for a
	 * 1-pixel GIF with lots of parameters set
	 */
	public void send() {
		if (!isAllowed()) return;
		try {
			final URL url = new URL(ga + utmwv + utms + utmn + utmhn + utmt + utme + utmcs +
				utmsr + utmvp + utmsc + utmul + utmje + utmfl + utmcnr + utmdt +
				utmhid + utmr + utmp + utmac + utmcc);
			final URLConnection uc = url.openConnection();
			uc.setRequestProperty("User-Agent", userAgentString());
			//the next line appears to be necessary to complete the HTTP request
			try (final BufferedReader reader = new BufferedReader(new InputStreamReader(
				uc.getInputStream()))) {
					if (IJ.debugMode) {
						IJ.log(url.toString());
						IJ.log(uc.getRequestProperty("User-Agent"));
						reader.lines().forEach(IJ::log);
					}
			} catch (final IOException e) {
				IJ.error(e.getMessage());
			}
		}
		catch (final IOException e) {
			if (IJ.debugMode) {
				IJ.error(e.getMessage());
			}
		}
	}

	/**
	 * Sets the instance variables to appropriate values based on the system
	 * parameters and method arguments.
	 *
	 * @param category Google Analytics event category classification
	 * @param action Google Analytics event action classification
	 * @param label Google Analytics event label classification
	 * @param value Google Analytics event value - an integer used for sum and
	 *          average statistics
	 * @return The instance of UsageReporter ready to send() a report
	 */
	private static UsageReporter reportEvent(final String category,
		final String action, final String label, final Integer value)
	{
		if (!Prefs.get(ReporterOptions.OPTOUTKEY, false)) return INSTANCE;
		utms = "utms=" + session + "&";
		session++;
		final String val = (value == null) ? "" : "(" + value + ")";
		final String lab = (label == null) ? "" : label;
		utme = "utme=5(" + category + "*" + action + "*" + lab + ")" + val + "&";
		utmn = "utmn=" + random.nextInt(Integer.MAX_VALUE) + "&";
		utmhid = "utmhid=" + random.nextInt(Integer.MAX_VALUE) + "&";

		final long time = System.currentTimeMillis() / 1000;
		lastTime = thisTime;
		if (lastTime == 0) lastTime = time;
		thisTime = time;

		if ("".equals(utmcnr)) utmcnr = "utmcn=1&";
		else utmcnr = "utmcr=1&";

		utmcc = getCookieString();
		return INSTANCE;
	}

	/**
	 * Prepare the instance for sending a report on a specific class; {@link Class#getName}
	 * is added to the 'action' field of the report,
	 * category is "Plugin Usage" and label is the version string generated by
	 * {@link org.scijava.util.VersionUtils#getVersion}.
	 *
	 * @param o Class to report on
	 * @return The instance of UsageReporter ready to send() a report
	 */
	public static UsageReporter reportEvent(final Object o) {
		return reportEvent("Plugin%20Usage", o.getClass().getName(), VersionUtils.getVersion(o.getClass()),
			null);
	}

	/**
	 * Create a string of cookie data for the gif URL
	 *
	 * @return cookie string
	 */
	private static String getCookieString() {
		// seems to be a bug in Prefs.getInt, so are Strings wrapped in
		// Integer.toString()
		final String cookie = Prefs.get(ReporterOptions.COOKIE, Integer.toString(
			random.nextInt(Integer.MAX_VALUE)));
		final String cookie2 = Prefs.get(ReporterOptions.COOKIE2, Integer.toString(
			random.nextInt(Integer.MAX_VALUE)));
		final String firstTime = Prefs.get(ReporterOptions.FIRSTTIMEKEY, Integer
			.toString(random.nextInt(Integer.MAX_VALUE)));
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

	private boolean isAllowed() {
		if (!Prefs.get(ReporterOptions.OPTOUTSET, false)) new ReporterOptions().run(
			"");
		return Prefs.get(ReporterOptions.OPTOUTKEY, true);
	}

	private static String userAgentString() {
		final String os;
		if (IJ.isMacintosh()) {
			// Handle Mac OSes on PPC and Intel
			String arch = System.getProperty("os.arch");
			if (arch.contains("x86") || arch.contains("i386")) arch = "Intel";
			else if (arch.contains("ppc")) arch = arch.toUpperCase();
			os = "Macintosh; " + arch + " " + System.getProperty("os.name") + " " +
				System.getProperty("os.version");
		}
		else if (IJ.isWindows()) {
			// Handle Windows using the NT version number
			os = "Windows NT " + System.getProperty("os.version");
		}
		else {
			// Handle Linux and everything else
			os = System.getProperty("os.name") + " " + System.getProperty(
				"os.version") + " " + System.getProperty("os.arch");
		}

		final String browser = "Java/" + System.getProperty("java.version");
		final String vendor = System.getProperty("java.vendor");
		final String locale = getLocaleString();

		return browser + " (" + os + "; " + locale + ") " + vendor;
	}
}
