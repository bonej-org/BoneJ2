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

import java.util.Random;

import ij.Prefs;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

public class ReporterOptions implements PlugIn {

	static final String OPTOUTSET = "bonej.report.option.set";
	/** Set to false if reporting is not allowed */
	static final String OPTOUTKEY = "bonej.allow.reporter";
	static final String COOKIE = "bonej.report.cookie";
	static final String COOKIE2 = "bonej.report.cookie2";
	static final String FIRSTTIMEKEY = "bonej.report.firstvisit";
	static final String SESSIONKEY = "bonej.report.bonejsession";
	private static final String IJSESSIONKEY = "bonej.report.ijsession";

	@Override
	public void run(final String arg) {

		final GenericDialog dialog = new GenericDialog("BoneJ");
		dialog.addMessage("Allow usage data collection?");
		dialog.addMessage("BoneJ would like to collect data on \n" +
			"which plugins are being used, to direct development\n" +
			"and promote BoneJ to funders.");
		dialog.addMessage("If you agree to participate please hit OK\n" +
			"otherwise, cancel. For more information click Help.");
		dialog.addHelp("http://bonej.org/stats");
		dialog.showDialog();
		if (dialog.wasCanceled()) {
			Prefs.set(OPTOUTKEY, false);
			Prefs.set(COOKIE, "");
			Prefs.set(COOKIE2, "");
			Prefs.set(FIRSTTIMEKEY, "");
			Prefs.set(SESSIONKEY, "");
			Prefs.set(IJSESSIONKEY, "");
		}
		else {
			Prefs.set(OPTOUTKEY, true);
			Prefs.set(COOKIE, new Random().nextInt(Integer.MAX_VALUE));
			Prefs.set(COOKIE2, new Random().nextInt(Integer.MAX_VALUE));
			final long time = System.currentTimeMillis() / 1000;
			Prefs.set(FIRSTTIMEKEY, Long.toString(time));
			Prefs.set(SESSIONKEY, 1);
		}

		Prefs.set(OPTOUTSET, true);
		Prefs.savePreferences();
		UsageReporter.reportEvent(this).send();
	}
}
