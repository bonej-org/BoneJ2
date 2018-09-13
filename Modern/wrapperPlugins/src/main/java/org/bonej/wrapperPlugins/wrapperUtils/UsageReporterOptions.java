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

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
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
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String helpMessage = "For more information click Help.";
	
	@Parameter(label = "Opt in to usage data collection", description = "Can BoneJ send usage data?")
  private boolean optIn = false;
	
	@Parameter(label = "Help")
	private Button button;
	
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
	static final String IJSESSIONKEY = "bonej2.report.ijsession";

	@Override
	public void run() {

		//TODO link to detailed online info
    //For more information click Help.";
		
		//Wipe persistent data on opt-out
		if (!optIn) {
			System.out.print("User has opted out of data collection\n");
			imagej.prefs().clear(this.getClass());
		}

		else {
			System.out.print("User has opted in to data collection\n");
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
		System.out.println("User permission has been sought.\n");
		imagej.prefs().put(this.getClass(), OPTINSET, true);
		
		System.out.println("URO Sending usage report...");
		UsageReporter.reportEvent(this).send();
	}
	
	/**
	 * Check whether user has given permission to collect usage data
	 * 
	 * @return true only if the user has given explicit permission to send usage data
	 */
	public boolean isAllowed() {
		imagej = new ImageJ();
		final boolean permissionSought = imagej.prefs().getBoolean(this.getClass(), OPTINSET, true);
		if (!permissionSought) {
			System.out.println("User permission has not been sought, requesting it...\n");
			run();
		}
			
		if (imagej.prefs().getBoolean(this.getClass(), OPTINKEY, false)) {
			System.out.println("User permission has been granted\n");
		  return true;
		}
		return false;
	}
}
