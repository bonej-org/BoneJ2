/*-
 * #%L
 * High-level BoneJ2 commands.
 * %%
 * Copyright (C) 2015 - 2025 Michael Doube, BoneJ developers
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
package org.bonej.wrapperPlugins.headless;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

import net.imagej.table.DefaultResultsTable;

import org.bonej.wrapperPlugins.IntertrabecularAngleWrapper;
import org.junit.Test;
import org.scijava.command.CommandModule;
import org.scijava.table.DefaultColumn;
import org.scijava.table.DefaultGenericTable;
import org.scijava.table.DoubleColumn;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;

/**
 * Tests for BoneJ Wrapper Plugins that do not rely on a GUI.
 * 
 * Its intention is to validate output when plugin wrappers are called
 * from scripts and macros.
 * 
 * @author Michael Doube
 *
 */
public class IntertrabecularAnglesWrapperHeadlessTest extends AbstractWrapperHeadlessTest {

	@Test
	public void testInterTrabecularAngles() throws InterruptedException, ExecutionException {
		//SETUP
		assertTrue(imageJ().ui().isHeadless());
		final Predicate<Double> nonEmpty = Objects::nonNull;

		final URL resource = getClass().getClassLoader().getResource(
				"test-skelly.zip");
		assert resource != null;
		final ImagePlus skelly = IJ.openImage(resource.getFile());
		Calibration cal = new Calibration();
		cal.pixelDepth = 0.1; 
		cal.pixelHeight = 0.1;
		cal.pixelWidth = 0.1;
		skelly.setCalibration(cal);

		//EXECUTE
		final CommandModule module = command().run(
			IntertrabecularAngleWrapper.class, false, "inputImage", skelly,
			"minimumValence", 3, "maximumValence", 50, "minimumTrabecularLength", 0.2,
			"marginCutOff", 0, "useClusters", true, "printCentroids", true,
			"iteratePruning", false, "showSkeleton", true).get();

		Map<String,Object> outputs = module.getOutputs();

		//VERIFY
		assertNotNull(outputs);
		logOutputNameAndClass(module);
		
		//check the output image exists and is an ImagePlus
		assertNotNull(module.getOutput("skeletonImage"));
		assertTrue(outputs.get("skeletonImage") instanceof ImagePlus);

		//check that the angle table exists and is a DefaultGenericTable
		assertNotNull(module.getOutput("resultsTable"));
		assertTrue(module.getOutput("resultsTable") instanceof DefaultGenericTable);

		//Check the angle table contains the expected values.
		final DefaultGenericTable table = (DefaultGenericTable) module.getOutput("resultsTable");
		assertEquals(2, table.size());
		@SuppressWarnings("unchecked")
		final DefaultColumn<Double> threeColumn = (DefaultColumn<Double>) table.get(0);
		assertEquals("3", threeColumn.getHeader());
		assertEquals(10, threeColumn.size());
		assertEquals(3, threeColumn.stream().filter(nonEmpty).count());
		assertEquals(2, threeColumn.stream().filter(nonEmpty).distinct().count());
		@SuppressWarnings("unchecked")
		final DefaultColumn<Double> fiveColumn = (DefaultColumn<Double>) table.get(1);
		assertEquals("5", fiveColumn.getHeader());
		assertEquals(10, fiveColumn.size());
		assertEquals(10, fiveColumn.stream().filter(nonEmpty).count());
		assertEquals(6, fiveColumn.stream().filter(nonEmpty).distinct().count());
		assertEquals(1, fiveColumn.stream().filter(nonEmpty)
			.filter(d -> d == Math.PI).count());
		assertEquals(2, fiveColumn.stream().filter(nonEmpty)
			.filter(d -> d == Math.PI / 2).count());
		assertEquals(2, fiveColumn.stream().filter(nonEmpty)
			.filter(d -> d == 0.8621700546672264).count());
		assertEquals(2, fiveColumn.stream().filter(nonEmpty)
			.filter(d -> d == 2.279422598922567).count());
		assertEquals(2, fiveColumn.stream().filter(nonEmpty)
			.filter(d -> d == 2.4329663814621227).count());
		
		//check that the centroid table exists and is a DefaulResultsTable
		assertNotNull(module.getOutput("centroidTable"));
		assertTrue(module.getOutput("centroidTable") instanceof DefaultResultsTable);
		
		//check that the centroid table contains the expected values
		final DefaultResultsTable centroidTable = (DefaultResultsTable) module.getOutput("centroidTable");
		assertEquals(6, centroidTable.size());
		assertEquals(7, centroidTable.getRowCount());
		final DoubleColumn v1x = centroidTable.get(0);
		assertEquals(2, v1x.stream().filter(d -> d == 2).count());
		assertEquals(1, v1x.stream().filter(d -> d == 9).count());
		assertEquals(2, v1x.stream().filter(d -> d == 17).count());
		assertEquals(2, v1x.stream().filter(d -> d == 24).count());
	}
	
	/**
	 * Print the names and object types in the output list to the console
	 *  
	 * @param module
	 */
	private void logOutputNameAndClass(CommandModule module) {
		
		Map<String,Object> outputs = module.getOutputs();
		
		System.out.println("Output list for " + module.getInfo().getClassName());
		
		for (Map.Entry<String, Object> entry : outputs.entrySet()) {
			if (Objects.isNull(entry))
				continue;
			if (Objects.isNull(entry.getKey()) || Objects.isNull(entry.getValue()))
				continue;
			System.out.println(entry.getKey() + " " + entry.getValue().getClass().getName());
		}
	}
}
