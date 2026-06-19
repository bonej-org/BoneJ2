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
 * Test command to load raw data using DatasetUtil and display it.
 * Use this to verify endianness and calibration settings before running large pipelines.
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Analyze>Load Raw")
public class TestLoadRawCommand implements Command {

    @Parameter(label = "File Path", required = true, style = "file")
    private File filePath;

    @Parameter(label = "Header Offset (bytes)", min = "0")
    private long headerOffset = 0;

    @Parameter(label = "Width (X)", required = true, min = "1")
    private int width;

    @Parameter(label = "Height (Y)", required = true, min = "1")
    private int height;

    @Parameter(label = "Depth (Z)", required = true, min = "1")
    private int depth;

    @Parameter(label = "Pixel Type", choices = {"uint8", "uint16", "int16", "float32"}, required = true)
    private String pixelTypeStr;
    
    @Parameter(label = "(0,1) binary",
    		description= "Convert 8-bit (0,1) binary to ImageJ 8-bit (0,255) binary")
    private boolean zeroOneBinary = false;

    @Parameter(label = "Byte Order", choices = {"Little-endian", "Big-endian"}, required = true)
    private String byteOrderStr;

    @Parameter(label = "Spacing X (mm)", stepSize = "0.00001")
    private double spacingX = 1.0;

    @Parameter(label = "Spacing Y (mm)", stepSize = "0.00001")
    private double spacingY = 1.0;

    @Parameter(label = "Spacing Z (mm)", stepSize = "0.00001")
    private double spacingZ = 1.0;

    @Parameter(type = ItemIO.OUTPUT)
    private Dataset dataset;

    @Parameter
    private DatasetService datasetService;

    @Parameter
    private UIService uiService;

    @Parameter
    private LogService logService;

    @Override
    public void run() {
        try {
            logService.info("Starting test load of: " + filePath.getName());

            // Call the utility method directly
            dataset = DatasetUtil.loadRaw3D(
                filePath,
                headerOffset,
                width,
                height,
                depth,
                pixelTypeStr,
                zeroOneBinary,
                byteOrderStr,
                spacingX,
                spacingY,
                spacingZ,
                datasetService
            );

            if (dataset == null) {
                throw new RuntimeException("DatasetUtil returned null dataset.");
            }

            logService.info("Successfully loaded dataset: " + dataset.getName());
            logService.info("Dimensions: " + width + "x" + height + "x" + depth);
            logService.info("Calibration (mm): X=" + spacingX + ", Y=" + spacingY + ", Z=" + spacingZ);
            
            // Display the result
            if (uiService != null && uiService.isVisible()) {
                uiService.show(dataset);
                logService.info("Dataset displayed in UI.");
            } else {
                logService.warn("UI not available, dataset created but not shown.");
            }

        } catch (IOException e) {
            logService.error("IO Error during load: " + e.getMessage());
            throw new RuntimeException(e);
        } catch (Exception e) {
            logService.error("General Error during load: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
