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

import net.imagej.ImageJ;

import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * Handles persistent settings such as user permission state.
 * 
 * Preferences are stored in their native format (long, int, boolean, etc.)
 * 
 * @author Michael Doube
 * @author Richard Domander
 *
 */
@Plugin(type = Command.class, menuPath = "Options>BoneJ>Usage reporting (Modern)")
public class UsageReporterOptions extends ContextCommand {

	@Parameter(label = "Opt in", description = "Can BoneJ send usage data?")
  private boolean optIn = false;
	
	@Parameter
	private ImageJ imagej;

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

	@Override
	public void run() {

		//TODO chattier dialog box with link to info
//		final GenericDialog dialog = new GenericDialog("BoneJ");
//		dialog.addMessage("Allow usage data collection?");
//		dialog.addMessage("BoneJ would like to collect data on \n" +
//			"which plugins are being used, to direct development\n" +
//			"and promote BoneJ to funders.");
//		dialog.addMessage("If you agree to participate please hit OK\n" +
//			"otherwise, cancel. For more information click Help.");
//		dialog.showDialog();
		
		//Wipe persistent data on opt-out
		if (!optIn)
			imagej.prefs().clear(this.getClass());

		else {
			imagej.prefs().put(this.getClass(), OPTINKEY, true);
			imagej.prefs().put(this.getClass(), COOKIE,
				new Random().nextInt(Integer.MAX_VALUE));
			imagej.prefs().put(this.getClass(), COOKIE2,
				new Random().nextInt(Integer.MAX_VALUE));
			imagej.prefs().put(this.getClass(), FIRSTTIMEKEY,
				System.currentTimeMillis() / 1000);
			imagej.prefs().put(this.getClass(), SESSIONKEY, 1);
			imagej.prefs().put(this.getClass(), IJSESSIONKEY, 1);
		}

		//set that user permissions have been sought
		imagej.prefs().put(this.getClass(), OPTINSET, true);
		
		UsageReporter.reportEvent(this);
	}
	
	/**
	 * Check whether user has given permission to collect usage data
	 * 
	 * @return true only if the user has given explicit permission to send usage data
	 */
	public boolean isAllowed() {
		final boolean permissionSought = imagej.prefs().getBoolean(this.getClass(), OPTINSET, true);
		if (!permissionSought)
			run();
		if (optIn)
		    return true;
		return false;
	}
}
