package org.bonej.wrapperPlugins;

import java.awt.Checkbox;
import java.util.List;

import org.bonej.plus.DeviceCheck;
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

		GenericDialog gd = new GenericDialog("Select GPUs");
		for (int p = 0; p < platformNames.length; p++) {
			gd.addMessage("Platform "+p+": "+platformNames[p]);
			if (devices[p].length == 0) {
				gd.addMessage("No devices on this platform");
				continue;
			}
			for (int d = 0; d < devices[p].length; d++) {
				boolean useDevice = Prefs.get(PREF_BASE+"useDevice["+p+":"+d+"]", isCompliant[p][d]);
				gd.addCheckbox("Device "+d+": "+ deviceNames[p][d], useDevice);
			}
		}
		
		gd.addCheckbox("Ignore selection and use all GPUs", Prefs.get(PREF_BASE+"useAllDevices", false));
		
		List<?> checkboxes = gd.getCheckboxes();
		int i = 0;
		for (int p = 0; p < platformNames.length; p++) {
			for (int d = 0; d < devices[p].length; d++) {
				Checkbox checkbox = (Checkbox) checkboxes.get(i);
				checkbox.setEnabled(isCompliant[p][d]);
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
	}
}
