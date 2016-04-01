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
package org.doube.util;

import java.util.Random;

import ij.Prefs;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

public class ReporterOptions implements PlugIn {

	public static final String OPTOUTSET = "bonej.report.option.set";
	/** Set to false if reporting is not allowed */
	public static final String OPTOUTKEY = "bonej.allow.reporter";
	public static final String COOKIE = "bonej.report.cookie";
	public static final String COOKIE2 = "bonej.report.cookie2";
	public static final String FIRSTTIMEKEY = "bonej.report.firstvisit";
	public static final String SESSIONKEY = "bonej.report.bonejsession";
	public static final String IJSESSIONKEY = "bonej.report.ijsession";

	@Override
	public void run(final String arg) {

		final GenericDialog dialog = new GenericDialog("BoneJ");
		dialog.addMessage("Allow usage data collection?");
		dialog.addMessage("BoneJ would like to collect data on \n"
				+ "which plugins are being used, to direct development\n" + "and promote BoneJ to funders.");
		dialog.addMessage(
				"If you agree to participate please hit OK\n" + "otherwise, cancel. For more information click Help.");
		dialog.addHelp("http://bonej.org/stats");
		dialog.showDialog();
		if (dialog.wasCanceled()) {
			Prefs.set(OPTOUTKEY, false);
			Prefs.set(ReporterOptions.COOKIE, "");
			Prefs.set(ReporterOptions.COOKIE2, "");
			Prefs.set(ReporterOptions.FIRSTTIMEKEY, "");
			Prefs.set(ReporterOptions.SESSIONKEY, "");
			Prefs.set(ReporterOptions.IJSESSIONKEY, "");
		} else {
			Prefs.set(OPTOUTKEY, true);
			Prefs.set(ReporterOptions.COOKIE, new Random().nextInt(Integer.MAX_VALUE));
			Prefs.set(ReporterOptions.COOKIE2, new Random().nextInt(Integer.MAX_VALUE));
			final long time = System.currentTimeMillis() / 1000;
			Prefs.set(ReporterOptions.FIRSTTIMEKEY, Long.toString(time));
			Prefs.set(SESSIONKEY, 1);
		}

		Prefs.set(OPTOUTSET, true);
		Prefs.savePreferences();
		UsageReporter.reportEvent(this).send();
		return;
	}
}
