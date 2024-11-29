package org.bonej.wrapperPlugins;

import java.awt.Checkbox;
import java.util.List;

import org.bonej.plus.DeviceCheck;
import org.jocl.cl_device_id;
import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import ij.gui.GenericDialog;

@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Plus>Check GPUs")
public class GPUCheckerWrapper implements Command {
	
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
			gd.addMessage(platformNames[p]);
			for (int d = 0; d < devices[p].length; d++) {
				gd.addCheckbox(d+": "+ deviceNames[p][d], isCompliant[p][d]);
			}
		}
		
		gd.addCheckbox("Ignore selection and use all GPUs", false);
		
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

		//save the selection to the preferences (in what format - platform + device = true or false to use?)
	}
}
