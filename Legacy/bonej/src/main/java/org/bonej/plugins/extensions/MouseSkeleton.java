package org.bonej.plugins.extensions;

import java.awt.event.MouseEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.gui.NonBlockingGenericDialog;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij3d.Content;
import ij3d.Image3DUniverse;
import ij3d.behaviors.InteractiveBehavior;

public class MouseSkeleton implements PlugIn {

	private Image3DUniverse univ;
	private String[] itemList = {"clavicle", "humerus", "radius", "ulna", "femur", "tibia", "other"};
	
	@Override
	public void run(String arg) {
		
		if (Image3DUniverse.universes.size() == 0 || !IJ.isResultsWindow()) {
			IJ.showMessage("Missing data", "Label Elements (3D) expects an open Results table and 3D Viewer, \n" +
				"each containing the output from BoneJ's Particle Analyser.");
			return;
		}
		
		univ = Image3DUniverse.universes.get(0);
		
		IJ.showMessage("Label Elements (3D)", 
			"Hold down Ctrl and left click on surfaces in the 3D Viewer to label them.");
		
		univ.addInteractiveBehavior(new CustomBehavior(univ));
		
	}

	class CustomBehavior extends InteractiveBehavior {
		CustomBehavior(Image3DUniverse univ){
			super(univ);
		}
		
		public void doProcess(MouseEvent e) {
			if (!e.isControlDown() ||
					e.getID() != MouseEvent.MOUSE_PRESSED) {
				super.doProcess(e);
				return;
			}
			Content c = univ.getSelected();
			if (c == null)
				return;
			String name = c.getName();
			Pattern p = Pattern.compile("\\d+");
			Matcher m = p.matcher(name);
			int boneID = -1;
			boolean wasFound = false;
			while (m.find()) {
				boneID = Integer.parseInt(m.group());
				wasFound = true;
			}
			if (!wasFound)
				return;
			
			IJ.log("Picked "+name+" and extracted number "+boneID);
			//need to put a dialog here
			showDialogAndUpdateResults(boneID);
		}

		private void showDialogAndUpdateResults(final int boneID) {
			//set up the dialog
			GenericDialog gd = new NonBlockingGenericDialog("Set particle label");
			int nRows = 10;
			int nCols = (int) Math.floor(itemList.length / (double) nRows) + 1;
			gd.addRadioButtonGroup("Select name for particle "+boneID,
				itemList, Math.min(nRows, itemList.length), nCols, itemList[0]);
			gd.addTextAreas("", null, 1, 12);
			gd.showDialog();
			
			if (gd.wasCanceled())
				return;
			
			//get the info and add it to the result table
			String selectedItem = gd.getNextRadioButton();
			if (selectedItem.equals("other"))
				selectedItem = gd.getNextText();
			
			ResultsTable rt = ResultsTable.getResultsTable();
			rt.setValue("particle name",( boneID - 1 ), selectedItem);
			rt.updateResults();
			rt.show(rt.getTitle());
		}
	}
}
