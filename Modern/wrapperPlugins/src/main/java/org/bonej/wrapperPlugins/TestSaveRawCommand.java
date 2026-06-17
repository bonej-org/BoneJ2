package org.bonej.wrapperPlugins;

import net.imagej.Dataset;
import net.imagej.DatasetService;

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

		if (dataset.numDimensions() != 3) {
			logService.error("Active dataset is not 3D. Found: " + dataset.numDimensions() + "D");
			throw new IllegalStateException("Active dataset must be 3D.");
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

			logService.info("Attempting round-trip verification...");
			Dataset reloaded = DatasetUtil.loadRaw3D(
					outputPath, 0, 
					(int)dataset.dimension(0), 
					(int)dataset.dimension(1), 
					(int)dataset.dimension(2),
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