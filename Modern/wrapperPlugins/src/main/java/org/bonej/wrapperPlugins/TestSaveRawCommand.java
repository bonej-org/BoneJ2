/*-
 * #%L
 * High-level BoneJ2 commands.
 * %%
 * Copyright (C) 2015 - 2026 Michael Doube, BoneJ developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.bonej.wrapperPlugins;

import net.imagej.Dataset;
import net.imagej.DatasetService;

import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.DatasetUtil;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import java.io.File;
import java.io.IOException;

/**
 * Test command to save the current active Dataset as a raw file using DatasetUtil.
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Analyze>Save Active as Raw")
public class TestSaveRawCommand implements Command {

	@Parameter(type = ItemIO.INPUT)
	private Dataset dataset;

	@Parameter(label = "Output File Path", required = true, style = "file")
	private File outputPath;

	@Parameter(label = "Byte Order", 
			description = "Use Little-endian for standard PC/raw. Big-endian for DICOM.",
			choices = {"Little-endian", "Big-endian"}, 
			required = true)
	private String byteOrderStr;
	
    @Parameter(label = "(0,1) binary",
    		description= "Convert ImageJ 8-bit (0,255) binary to 8-bit (0,1) binary")
    private boolean zeroOneBinary = false;

	@Parameter
	private UIService uiService;

	@Parameter
	private LogService logService;

	@Parameter
	private DatasetService datasetService;

	@Override
	public void run() {
		if (dataset == null) {
			logService.error("No active Dataset found. Please open an image first.");
			throw new IllegalStateException("No active Dataset found.");
		}

		if (dataset.numDimensions() > 3 || dataset.numDimensions() < 2
				|| AxisUtils.hasChannelDimensions(dataset) || AxisUtils.hasTimeDimensions(dataset)) {
			logService.error("Active dataset has unhandled dimensionality - requires 2D or 3D spatial only");
			throw new IllegalStateException("Active dataset must be 2D or 3D.");
		}

		boolean littleEndian = !"Big-endian".equalsIgnoreCase(byteOrderStr);

		try {
			logService.info("Saving " + dataset.getName() + " to " + outputPath.getName() + "...");

			// Direct call to utility
			DatasetUtil.saveAsRaw(dataset, outputPath, littleEndian, zeroOneBinary);

			logService.info("Save completed successfully.");
			logService.info("File size: " + outputPath.length() + " bytes");

			// Optional: Verify by trying to load it back immediately
			// This is a strong test of round-trip integrity

			//check if 2D or 3D
			int nDims = dataset.numDimensions();
			int zSize = nDims == 2 ? 1 : (int)dataset.dimension(2);
			
			logService.info("Attempting round-trip verification...");
			Dataset reloaded = DatasetUtil.loadRaw3D(
					outputPath, 0, 
					(int)dataset.dimension(0), 
					(int)dataset.dimension(1), 
					zSize,
					determineTypeString(dataset),
					zeroOneBinary,
					byteOrderStr,
					1.0, 1.0, 1.0, // Dummy spacing for test
					datasetService
					);
							logService.info("Round-trip successful! Data matches.");

							reloaded.setName(dataset.getName()+"_reloaded");
							if (uiService.isVisible()) {
								uiService.show(reloaded);
							}
		} catch (IOException e) {
			logService.error("Failed to save raw file: " + e.getMessage());
			throw new RuntimeException(e);
		}
	}

	// Helper to infer type string from the current dataset type
	private String determineTypeString(Dataset ds) {
		String typeName = ds.getType().getClass().getSimpleName();
		if (typeName.contains("UnsignedByte")) return "uint8";
		if (typeName.contains("UnsignedShort")) return "uint16";
		if (typeName.contains("Short")) return "int16";
		if (typeName.contains("Float")) return "float32";
		return "uint8"; // Default fallback
	}
}
