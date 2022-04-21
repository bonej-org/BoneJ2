/*-
 * #%L
 * High-level BoneJ2 commands.
 * %%
 * Copyright (C) 2015 - 2020 Michael Doube, BoneJ developers
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


package org.bonej.wrapperPlugins.wrapperUtils;

import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;
import static org.scijava.ui.DialogPrompt.OptionType.OK_CANCEL_OPTION;
import static org.scijava.ui.DialogPrompt.Result.OK_OPTION;

import net.imagej.ImgPlus;
import net.imagej.axis.CalibratedAxis;
import net.imagej.display.ColorTables;
import net.imagej.legacy.LegacyService;
import net.imagej.ops.OpEnvironment;
import net.imagej.ops.OpService;
import net.imglib2.img.Img;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.ComplexType;

import org.bonej.utilities.ImagePlusUtil;
import org.scijava.Context;
import org.scijava.command.ContextCommand;
import org.scijava.log.LogService;
import org.scijava.ui.UIService;

import ij.ImagePlus;
import ij.process.LUT;

/**
 * Miscellaneous utility methods.
 *
 * @author Richard Domander
 * @author Michael Doube
 */
public final class Common {

	/**
	 * Generates the 'fire' look-up table (LUT) and returns it as an IJ1 LUT
	 * object
	 *
	 * @return fire LUT
	 */
	public static LUT makeFire() {
		final int colourBytes = 256;
		final byte[][] colors = ColorTables.FIRE.getValues();
		return new LUT(8, colourBytes, colors[0], colors[1], colors[2]);
	}

	/**
	 * Converts the {@link ImgPlus} to a new ImgPlus with {@link BitType}
	 * elements.
	 * <p>
	 * Also copies ImgPlus metadata.
	 * </p>
	 *
	 * @param ops an {@link OpService} to find the necessary ops for conversion.
	 * @param imgPlus an image.
	 * @param <C> type of the elements in the input image.
	 * @return the image converted to bit type.
	 */
	public static <C extends ComplexType<C>> ImgPlus<BitType> toBitTypeImgPlus(
		final OpEnvironment ops, final ImgPlus<C> imgPlus)
	{
		final Img<BitType> convertedImg = ops.convert().bit(imgPlus.getImg());
		final ImgPlus<BitType> convertedImgPlus = new ImgPlus<>(convertedImg);
		copyMetadata(imgPlus, convertedImgPlus);

		return convertedImgPlus;
	}

	/**
	 * Shows a warning dialog about image anisotropy, and asks if the user wants
	 * to continue. If the plugin is running in headless mode, execution continues anyway, and a warning is printed to the log.
	 *
	 * @param image the current image open in ImageJ.
	 * @param uiService used to display the warning dialog.
	 * @param logService handles the warning text if the UI is headless
	 * @return true if user chose OK_OPTION, or image is not anisotropic, or
	 * execution is headless. False if user chose 'cancel' or they closed the dialog.
	 */
	public static boolean warnAnisotropy(final ImagePlus image,
		final UIService uiService, final LogService logService)
	{
		final double anisotropy = ImagePlusUtil.anisotropy(image);
		if (anisotropy < 1E-3) {
			return true;
		}
		final String anisotropyPercent = String.format("(%.1f %%)", anisotropy *
			100.0);
		if (uiService.isHeadless()) {
			logService.warn("The image is anisotropic " +
					anisotropyPercent + ". Continuing anyway, but results "
							+ "may be unreliable");
			return true;
		}
		return uiService.showDialog("The image is anisotropic " +
			anisotropyPercent + ". Continue anyway?", WARNING_MESSAGE,
			OK_CANCEL_OPTION) == OK_OPTION;
	}

	/**
	 * Cancels a command so that it won't show a dialog if a macro is running
	 * <p>
	 * When a macro is running, an error message is logged instead
	 * </p>
	 * @param command Command that needs to be cancelled
	 * @param reason A message that describes why the command is being cancelled
	 * @see ContextCommand#cancel(String)
	 */
	public static void cancelMacroSafe(final ContextCommand command, final String reason) {
		final Context context = command.context();
		final LegacyService legacyService = context.getService(LegacyService.class);
		if (!legacyService.getIJ1Helper().isMacro()) {
			command.cancel(reason);
			return;
		}
		// Cancelling with null won't show a dialog
		command.cancel(null);
		final LogService logService = context.getService(LogService.class);
		logService.error("Plugin cancelled: " + reason);
	}

	/**
	 * Copies image metadata such as name, axis types and calibrations from source
	 * to target.
	 *
	 * @param source source of metadata.
	 * @param target target of metadata.
	 */
	private static void copyMetadata(final ImgPlus<?> source,
		final ImgPlus<?> target)
	{
		target.setName(source.getName());

		final int dimensions = source.numDimensions();
		for (int d = 0; d < dimensions; d++) {
			final CalibratedAxis axis = source.axis(d);
			target.setAxis(axis, d);
		}
	}
}
