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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

//UnsupportedDataTypeException
import javax.activation.UnsupportedDataTypeException;

import org.doube.util.UsageReporter;

import ij.IJ;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

public class Strip_Stratec_File_Header implements PlugIn {

	// Overriding the abstract runnable run method. Apparently plugins run in
	// threads
	@Override
	public void run(final String arg) {
		final GenericDialog dialog = new GenericDialog("Strip Stratec Header");
		/* MeasInfo,PatBirth,PatMenoAge,PatName,PatTitle&Comment */
		dialog.addCheckbox("Strip_MeasInfo", false);
		dialog.addCheckbox("Strip_PatBirth", false);
		dialog.addCheckbox("Strip_PatMenoAge", false);
		dialog.addCheckbox("Strip_PatName", true);
		dialog.addCheckbox("Strip_PatTitleAndComment", false);
		dialog.addStringField("Stratec_file_to_strip", Prefs.getDefaultDirectory() + "I0020001.M01", 60);
		dialog.addStringField("File_save_name", Prefs.getDefaultDirectory() + "I0020001.M01", 60);
		dialog.showDialog();

		if (dialog.wasOKed()) { // Stop in case of cancel..
			final boolean[] toStrip = new boolean[5];
			for (int i = 0; i < toStrip.length; ++i) {
				toStrip[i] = dialog.getNextBoolean();
			}
			final String fileIn = dialog.getNextString();
			final String fileOut = dialog.getNextString();
			if (fileIn == null || fileOut == null) {
				IJ.log("Give both input and output file as parameters");
				return;
			}
			final File test = new File(fileIn);
			if (!test.exists()) {
				IJ.error("Input file didn't exist");
				return;
			}

			try {
				stripFile(fileIn, fileOut, toStrip);
			} catch (final Exception err) {
				IJ.error("Stratec file header stripping failed", err.getMessage());
			}
		}
		UsageReporter.reportEvent(this).send();
	}

	private void stripFile(final String fileNameIn, final String fileNameOut, final boolean[] toStrip)
			throws Exception {
		final File fileIn = new File(fileNameIn);
		final long fileLength = fileIn.length();
		byte[] fileData;
		try {
			final BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(fileIn));
			final DataInputStream dataInputStream = new DataInputStream(inputStream);
			fileData = new byte[(int) fileLength]; // Allocate memory for
													// reading the file into
													// memory
			dataInputStream.read(fileData, 0, (int) fileLength); // Read the
																	// data to
																	// memory
			dataInputStream.close(); // Close the file after reading
		} catch (final Exception e) {
			throw new UnsupportedDataTypeException("Could not read input file.");
		}
		fileData = stripHeader(fileData, toStrip); // Strip header
		writeFile(fileNameOut, fileData);
	}

	public void writeFile(final String fileName, final byte[] fileData) {
		try {
			final FileOutputStream writer = new FileOutputStream(fileName);
			writer.write(fileData);
			writer.close();
		} catch (final Exception err) {
			System.out.println("Saving failed");
		}
	}

	// Writing dummy header containing sufficient details for
	// Distribution_Analysis imageJ plugin, might not suffice for Geanie or
	// Stractec software
	byte[] stripHeader(byte[] data, final boolean[] toStrip) {
		final int[] offsetsToStrip = { 662, 1091, 1095, 1099,
				1141 }; /*
						 * MeasInfo,PatBirth,PatMenoAge,PatName,PatTitle&Comment
						 */
		final int[] stripLengths = { 324, 4, 4, 41, 124 };
		for (int s = 0; s < offsetsToStrip.length; ++s) {
			if (toStrip[s]) {
				data = fillWithZero(data, offsetsToStrip[s], stripLengths[s]);
			}
		}
		return data;
	}

	byte[] fillWithZero(final byte[] data, final int offset, final int zeros) {
		for (int z = 0; z < zeros; ++z) {
			data[offset + z] = (byte) 0;
		}
		return data;
	}
}
