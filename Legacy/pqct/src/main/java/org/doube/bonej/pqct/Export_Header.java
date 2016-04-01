/*
 * #%L
 * PQCT: ImageJ density distribution analysis plugin.
 * %%
 * Copyright (C) 2007 - 2016 Timo Rantalainen, Michael Doube, BoneJ developers.
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

package org.doube.bonej.pqct;

//Vector
import java.util.StringTokenizer;

import org.doube.util.UsageReporter;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.PlugIn;
import ij.plugin.filter.Info;
import ij.text.TextPanel;

public class Export_Header implements PlugIn {
	String imageInfo;

	@Override
	public void run(final String arg) {
		final ImagePlus imp = WindowManager.getCurrentImage();
		if (imp == null)
			return;
		if (imp.getType() != ImagePlus.GRAY16) {
			IJ.error("Distribution analysis expects 16-bit greyscale data");
			return;
		}
		imageInfo = new Info().getImageInfo(imp, imp.getChannelProcessor());
		String imageName;
		if (getInfoProperty(imageInfo, "File Name") != null) {
			imageName = getInfoProperty(imageInfo, "File Name");
		} else {
			if (imp.getImageStackSize() == 1) {
				imageName = imp.getTitle();
				imageInfo += "File Name:" + imageName + "\n";
			} else {
				imageName = imageInfo.substring(0, imageInfo.indexOf("\n"));
				imageInfo += "File Name:" + imageName + "\n";
			}
		}

		TextPanel textPanel = IJ.getTextPanel();
		if (textPanel == null) {
			textPanel = new TextPanel();
		}
		if (textPanel.getLineCount() == 0) {
			writeHeader(textPanel);
		}

		String results = "";
		results = printResults(results, imp);
		textPanel.appendLine(results);
		textPanel.updateDisplay();
		UsageReporter.reportEvent(this).send();
	}

	void writeHeader(final TextPanel textPanel) {
		final String[] propertyNames = { "File Name", "File Path", "Patient's Name", "Patient ID",
				"Patient's Birth Date", "Acquisition Date", "Pixel Spacing", "Object Length" };
		String headings = "";
		for (int i = 0; i < propertyNames.length; ++i) {
			headings += propertyNames[i] + "\t";
		}
		textPanel.setColumnHeadings(headings);
	}

	String printResults(String results, final ImagePlus imp) {
		final String[] propertyNames = { "File Name", "File Path", "Patient's Name", "Patient ID",
				"Patient's Birth Date", "Acquisition Date", "Pixel Spacing", "ObjLen" };
		if (imp != null) {
			if (getInfoProperty(imageInfo, "File Name") != null) {
				results += getInfoProperty(imageInfo, "File Name") + "\t";
			} else {
				if (imp.getImageStackSize() == 1) {
					results += getInfoProperty(imageInfo, "Title") + "\t";
				} else {
					results += imageInfo.substring(0, imageInfo.indexOf("\n")) + "\t";
				}
			}
			for (int i = 1; i < propertyNames.length; ++i) {
				results += getInfoProperty(imageInfo, propertyNames[i]) + "\t";
			}
		}
		return results;
	}

	String getInfoProperty(final String properties, final String propertyToGet) {
		final String toTokenize = properties;
		final StringTokenizer st = new StringTokenizer(toTokenize, "\n");
		String currentToken = null;
		while (st.hasMoreTokens()) {
			currentToken = st.nextToken();
			if (currentToken.indexOf(propertyToGet) != -1) {
				break;
			}
		}
		if (currentToken.indexOf(propertyToGet) != -1) {
			final StringTokenizer st2 = new StringTokenizer(currentToken, ":");
			String token2 = null;
			while (st2.hasMoreTokens()) {
				token2 = st2.nextToken();
			}
			return token2.trim();
		}
		return null;
	}
}
