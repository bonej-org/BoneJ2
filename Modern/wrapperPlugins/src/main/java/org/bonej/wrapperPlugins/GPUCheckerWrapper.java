package org.bonej.wrapperPlugins;

import java.awt.Checkbox;
import java.util.List;

import org.bonej.plus.DeviceCheck;
import org.bonej.plus.Utilities;
import org.jocl.cl_device_id;
import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import ij.Prefs;
import ij.gui.GenericDialog;

@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Plus>Check GPUs")
public class GPUCheckerWrapper implements Command {
	
	final static String PREF_BASE = "BoneJ.";
	
	@Override
	public void run() {
		String[] platformNames = DeviceCheck.getPlatformNames(DeviceCheck.getPlatformIds());
		for (int p = 0; p < platformNames.length; p++)
			System.out.println("Found platform: "+platformNames[p]);
		cl_device_id[][] devices = DeviceCheck.getAllDeviceIds();
		String[][] deviceNames = DeviceCheck.getDeviceNames(devices);
		boolean[][] isCompliant = DeviceCheck.getCompliance(devices);
		
		//clear Prefs of old deviceName and useDevice entries
		//around 10 platforms in existence I think, but may need to be updated
		//to handle severely Frankensteinish hardware configurations
		for (int pl = 0; pl < 20; pl++) {
			int d = 0;
			int noDeviceFoundCount = 0;
			while (noDeviceFoundCount < 10) {
				//if either deviceName or useDevice entry is present, this device was recorded in prefs
				if (Prefs.get(PREF_BASE+"deviceName["+pl+":"+d+"]", null) != null || 
					Prefs.get(PREF_BASE+"useDevice["+pl+":"+d+"]", null) != null) {
					
					System.out.println("Found a device listed in the prefs at "+pl+":"+d);
				
					//setting to null removes the key and the value
					Prefs.set(PREF_BASE+"deviceName["+pl+":"+d+"]", null);
					Prefs.set(PREF_BASE+"useDevice["+pl+":"+d+"]", null);
					//reset the counter - loop will keep going for 10 empty devices after the last hit
					noDeviceFoundCount = 0;
				} else {
//					System.out.println("No device listed in the prefs at "+pl+":"+d);
					noDeviceFoundCount++;
				}
				d++;
			}
		}
		

		GenericDialog gd = new GenericDialog("Select GPUs");
		for (int p = 0; p < platformNames.length; p++) {
			gd.addMessage("Platform "+p+": "+platformNames[p]);
			if (devices[p].length == 0) {
				gd.addMessage("No devices on this platform");
				continue;
			}
			for (int d = 0; d < devices[p].length; d++) {
				//log in Prefs the name of each device
				Prefs.set(PREF_BASE+"deviceName["+p+":"+d+"]", deviceNames[p][d]);
				boolean useDevice = Prefs.get(PREF_BASE+"useDevice["+p+":"+d+"]", isCompliant[p][d]);
				if (Prefs.get(PREF_BASE+"useAllDevices", false))
					useDevice = true;
				gd.addCheckbox("Device_"+p+"-"+d+": "+ deviceNames[p][d], useDevice);
			}
		}
		
		gd.addCheckbox("Use_all compliant GPUs", Prefs.get(PREF_BASE+"useAllDevices", false));
		
		List<?> checkboxes = gd.getCheckboxes();
		int i = 0;
		for (int p = 0; p < platformNames.length; p++) {
			for (int d = 0; d < devices[p].length; d++) {
				Checkbox checkbox = (Checkbox) checkboxes.get(i);
				checkbox.setEnabled(isCompliant[p][d]);
				if (!isCompliant[p][d])
					checkbox.setState(false);
				i++;
			}
		}
		
		gd.showDialog();

		for (int p = 0; p < platformNames.length; p++) {
			for (int d = 0; d < devices[p].length; d++) {
				Prefs.set(PREF_BASE+"useDevice["+p+":"+d+"]", gd.getNextBoolean());
			}
		}
		
		Prefs.set(PREF_BASE+"useAllDevices", gd.getNextBoolean());
		//reset and clear any singleton classes that might be floating about
		//note that multiple calls may lead to VRAM leak on NVIDIA cards, which don't let go of resources well
		Utilities.purgeAll();
	}
}
