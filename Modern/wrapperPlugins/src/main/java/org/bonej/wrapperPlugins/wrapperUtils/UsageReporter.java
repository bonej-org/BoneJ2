/*-
 * #%L
 * High-level BoneJ2 commands.
 * %%
 * Copyright (C) 2015 - 2022 Michael Doube, BoneJ developers
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


package org.bonej.wrapperPlugins.wrapperUtils;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.log.Logger;
import org.scijava.plugin.PluginService;
import org.scijava.prefs.PrefService;

/**
 * Prepares and sends a report about BoneJ usage to be logged by 
 * Google Analytics event tracking following the <a
 * href="https://developers.google.com/analytics/devguides/collection/protocol/ga4/reference?client_type=gtag>Measurement
 * Protocol Reference</a>.
 *
 * @author Michael Doube
 * @author Richard Domander
 */
// Don't make class final - breaks Mockito
public class UsageReporter {

	private static final String ga = "https://www.google-analytics.com/mp/collect";
	//It is in general poor form to include secrets hardcoded in plaintext, however,
	//there seems to be little point in obscuring them because they can easily be revealed
	//by anyone with access to this source (i.e. everyone) by calling e.g. HttpPost.getURI().
	private static final String api_secret = "api_secret=xYQ4ppd_QLG4R0RypUrV3A";
	private static final String measurement_id = "measurement_id=G-3VNZY4BX8Y";
	private static final Random random = new Random();
	private static String class_name;
	private static String display_size;
	private static boolean isFirstRun = true;
	private static PrefService prefs;
	private static PluginService plugins;
	private static CommandService commandService;
	private static UsageReporter instance;
	private static Logger logger;
	private static CloseableHttpClient httpClient;

	private UsageReporter() {}
	
	@SuppressWarnings("hiding")
	public static UsageReporter getInstance(final PrefService prefs,
		final PluginService plugins, final CommandService commandService, final Logger logger)
	{
		if (prefs == null || plugins == null || commandService == null || logger == null) {
			throw new NullPointerException("Services cannot be null");
		}
		if (instance == null) {
			instance = new UsageReporter();
		}
		UsageReporter.commandService = commandService;
		UsageReporter.plugins = plugins;
		UsageReporter.prefs = prefs;
		UsageReporter.logger = logger;
		UsageReporter.httpClient = HttpClients.createDefault();
		return instance;
	}

	/**
	 * Reports a the usage of a plug-in to bonej.org
	 *
	 * @param className Name of the reporting plug-in's class
	 */
	public void reportEvent(final String className) {
		if (!isAllowed()) {
			logger.debug("Usage reporting forbidden by user");
			return;
		}

		if (isFirstRun) {
			initSessionVariables();
		}

		class_name = className;
		
		send();
	}

	private static void initSessionVariables() {
		logger.debug("First run of Usage Reporter for this BoneJ session");
		final int bonejSession = prefs.getInt(UsageReporterOptions.class,
			UsageReporterOptions.SESSIONKEY, 0);
		logger.debug("bonejSession = " + bonejSession);
		prefs.put(UsageReporterOptions.class, UsageReporterOptions.SESSIONKEY,
			bonejSession + 1);
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
		display_size = width + "x" + height;
		isFirstRun = false;
	}

	/**
	 * Send the report to Google Analytics in the form of a POST request
	 * with parameters set in JSON format
	 */
	private static void send() {
		logger.debug("Sending report");
		try {
			logger.debug("Usage reporting approved by user, preparing POST request");
			HttpPost httpPost = new HttpPost(ga + "?" + measurement_id + "&" + api_secret);
			
			//set the headers
			httpPost.setProtocolVersion(HttpVersion.HTTP_1_1);
			httpPost.setHeader(HttpHeaders.HOST, "www.google-analytics.com");
			httpPost.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
			httpPost.setHeader(HttpHeaders.USER_AGENT, userAgentString());
			logger.debug(httpPost.toString());
			
			// add JSON parameters
			String json = makeJsonBody();
			
			StringEntity entity = new StringEntity(json);
			httpPost.setEntity(entity);
			logger.debug(EntityUtils.toString(httpPost.getEntity()));
			
			//send the POST request and retrieve the response
			final CloseableHttpResponse httpResponse = httpClient.execute(httpPost);
			logger.debug(httpResponse.getStatusLine());

		}
		catch (final Exception e) {
			logger.trace(e.getMessage());
		}
	}

	private static String makeJsonBody() {

		JSONObject jo = new JSONObject();

		try {
			jo.put("client_id", ""+prefs.getInt(UsageReporterOptions.class,
				UsageReporterOptions.CLIENTID, random.nextInt(Integer.MAX_VALUE)));
			jo.put("user_id", ""+prefs.getInt(UsageReporterOptions.class,
				UsageReporterOptions.USERID, random.nextInt(Integer.MAX_VALUE)));
			jo.put("timestamp_micros", ""+System.currentTimeMillis()*1000L);
			jo.put("non_personalized_ads", true);
			jo.put("events", getEventProperties());
		}
		catch (JSONException exc) {
			logger.trace(exc.getMessage());
		}
		
		return jo.toString();
	}

	private static JSONArray getEventProperties() {
		JSONArray eventArray = new JSONArray();
		JSONObject eventHeadings = new JSONObject();
		JSONObject eventParameters = new JSONObject();
		
		final String version = plugins.getPlugin(class_name).getVersion();

		try {
			eventHeadings.put("name", "plugin_usage");
			
			//list custom event parameters here
			eventParameters.put("plugin", class_name);
			eventParameters.put("version", version);
			eventParameters.put("localisation", getLocaleString());
			eventParameters.put("display_size", display_size);
			
			eventHeadings.put("params", eventParameters);
			
			eventArray.put(eventHeadings);
		}
		catch (JSONException exc) {
			exc.printStackTrace();
		}
		
		return eventArray;
	}

	private static String getLocaleString() {
		String locale = Locale.getDefault().toString();
		locale = locale.replace("_", "-");
		locale = locale.toLowerCase(Locale.ENGLISH);
		return locale;
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
		final boolean permissionSought = prefs.getBoolean(
			UsageReporterOptions.class, UsageReporterOptions.OPTINSET, false);
		if (!permissionSought) {
			logger.debug("User permission has not been sought, requesting it...");
			try {
				final CommandModule module = commandService.run(
					UsageReporterOptions.class, true).get();
				if (module.isCanceled()) {
					return false;
				}
			}
			catch (final InterruptedException | ExecutionException e) {
				logger.trace(e);
				return false;
			}
		}
		return prefs.getBoolean(UsageReporterOptions.class,
			UsageReporterOptions.OPTINKEY, false);
	}
}
