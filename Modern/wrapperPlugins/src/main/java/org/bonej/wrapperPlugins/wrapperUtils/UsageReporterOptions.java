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

import java.util.Random;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.PluginService;
import org.scijava.prefs.PrefService;
import org.scijava.widget.Button;

/**
 * Handles persistent settings such as user permission state.
 * 
 * Preferences are stored in their native format (long, int, boolean, etc.)
 * 
 * @author Michael Doube
 * @author Richard Domander
 *
 */
@Plugin(type = Command.class, menuPath = "Edit>Options>BoneJ Usage (Modern)")
public class UsageReporterOptions extends ContextCommand {

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String message1 = "Allow usage data collection?";
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String message2 = "BoneJ would like to collect data on";
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String message3 = "which plugins are being used, to direct development"; 
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String message4 = "and promote BoneJ to funders.";
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String message5 = "If you agree to participate please check the box.";
	@Parameter(label = "Opt in to usage data collection",
		description = "Can BoneJ send usage data?", persistKey = OPTINKEY)
	private boolean optIn;
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String helpMessage = "For more information click Help.";
	@Parameter(label = "Help")
	private Button button;
	@Parameter
	private PrefService prefs;
	@Parameter
	private LogService logService;
	@Parameter
	private PluginService pluginService;

	/** set to true if user permission has been requested */
	static final String OPTINSET = "bonej2.report.option.set";
	/** Set to false if reporting is not allowed */
	static final String OPTINKEY = "bonej2.allow.reporter";
	static final String COOKIE = "bonej2.report.cookie";
  static final String COOKIE2 = "bonej2.report.cookie2";
  /** time of first visit in seconds */
	static final String FIRSTTIMEKEY = "bonej2.report.firstvisit";
	/** unique ID for this particular BoneJ session */
	static final String SESSIONKEY = "bonej2.report.bonejsession";
	private static final String IJSESSIONKEY = "bonej2.report.ijsession";
	private UsageReporter reporter;

	@Override
	public void run() {
		if (!optIn) {
			//Wipe persistent data on opt-out
			logService.debug("User has opted out of data collection\n");
			prefs.clear(getClass());
			prefs.put(getClass(), OPTINSET, true);
			return;
		}

		logService.debug("User has opted in to data collection\n");
		prefs.put(getClass(), OPTINKEY, true);
		prefs.put(getClass(), COOKIE,
			new Random().nextInt(Integer.MAX_VALUE));
		prefs.put(getClass(), COOKIE2,
			new Random().nextInt(Integer.MAX_VALUE));
		prefs.put(getClass(), FIRSTTIMEKEY,
			System.currentTimeMillis() / 1000);
		prefs.put(getClass(), SESSIONKEY, 1);
		prefs.put(getClass(), IJSESSIONKEY, 1);
		prefs.put(getClass(), OPTINSET, true);
		if (reporter == null) {
			reporter = UsageReporter.getInstance(prefs, pluginService);
		}
		reporter.reportEvent(getClass().getName());
	}
}
