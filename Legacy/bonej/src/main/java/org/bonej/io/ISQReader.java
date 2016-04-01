/*
 * #%L
 * BoneJ: open source tools for trabecular geometry and whole bone shape analysis.
 * %%
 * Copyright (C) 2007 - 2016 Michael Doube, BoneJ developers. See also individual class @authors.
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
package org.bonej.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import org.doube.util.UsageReporter;

/*
 History:
 1.9.12		Decoder for Creation Time in Scanco header implemented
 (vms quadword, big endian, converted to unix timestamp and finally date/time).
 30.08.12	corrected errors in the Scanco header information
 updated a few comments
 added the most meaningful file header information to the ImagePlus property "Info" which can be retrieved with
 the menu command "Show Info"
 26.08.12   removed unnecessary comments
 added missing {} in if-else-statements
 formated Scanco header information to ease readability
 14.09.09 added information about the name[] field in the header (nameStringInHeader)
 22.03.08 old and now irrelevant_code_removed
 23.03.08 KHKs_Scanco_ISQ_FileReader : Downsampling by factor 2 in x, y and z direction by calculating the average of the pixels which are reduced to one new pixel
 including adjustment of fi.width, fi.height,fi.depth and fi.unit
 16.02.08 Bugfix for files larger than 2 GB revisited
 29.06.06 pixeldistance is taken from the orginal ISQ file header and used for metric scaling in ImageJ (fi.info)
 02.08.06 Bugfix for files larger than 2 GB (adjustment of offset and longOffset so that Skip will be correct)

 This file is nothing else then copying the modules which are used by ImageJ to import
 raw data or files into this one single file.
 It helped me a lot to understand ImageJ
 and it made it easier to develop this tool

 The usage should be self-explanatory.

 In short you select a ROI for the import by entering the upper left and the lower right coordinate
 of the rectangle.

 In order to obtain these coordinates you can decide how many slices you want to import and with which slice
 you start the import.

 Then you take paper and pencil and note the required coordinates for the second import attempt.

 Note: this tool was written because we have only limited memory on our Windows PCs. It was my very first Java program!

 Meaning of the checkboxes:

 "Downsample by factor 2 in x,y,z (method = average:)"
 for a view overview on a computer with limited RAM the stack can be downscaled by the factor of 2 in x and y and z direction
 The downsampling is made by averaging 8 pixels into 1 new pixel

 Please consider: this tool was written by a dentist
 -> and modified by a veterinary surgeon ;-)

 /* Scanco ISQ Header Information: - note: Scanco uses OpenVMS on Alpha workstations

 Little endian byte order (the least significant bit occupies the lowest memory position.

 00   char    check[16];              // CTDATA-HEADER_V1
 16   int     data_type;
 20   int     nr_of_bytes;
 24   int     nr_of_blocks;
 28   int     patient_index;          //p.skip(28);
 32   int     scanner_id;				//p.skip(32);
 36   int     creation_date[2];		//P.skip(36);
 44   int     dimx_p;					//p.skip(44);
 48   int     dimy_p;
 52   int     dimz_p;
 56   int     dimx_um;				//p.skip(56);
 60   int     dimy_um;
 64   int     dimz_um;
 68   int     slice_thickness_um;		//p.skip(68);
 72   int     slice_increment_um;		//p.skip(72);
 76   int     slice_1_pos_um;
 80   int     min_data_value;
 84   int     max_data_value;
 88   int     mu_scaling;             //p.skip(88);  /* p(x,y,z)/mu_scaling = value [1/cm]
 92	int     nr_of_samples;
 96	int     nr_of_projections;
 100  int     scandist_um;
 104  int     scanner_type;
 108  int     sampletime_us;
 112  int     index_measurement;
 116  int     site;                   //coded value
 120  int     reference_line_um;
 124  int     recon_alg;              //coded value
 128  char    name[40]; 		 		//p.skip(128);
 168  int     energy;        /* V     //p.skip(168);
 172  int     intensity;     /* uA    //p.skip(172);

 ...

 508 int     data_offset;     /* in 512-byte-blocks  //p.skip(508);

 * So the first 16 bytes are a string 'CTDATA-HEADER_V1', used to identify
 * the type of data. The 'int' are all 4-byte integers.
 *
 * dimx_p is the dimension in pixels, dimx_um the dimensions in micrometer
 *
 * So dimx_p is at byte-offset 40, then dimy_p at 44, dimz_p (=number of
 * slices) at 48.
 *
 * The microCT calculates so called 'x-ray linear attenuation' values. These
 * (float) values are scaled with 'mu_scaling' (see header, e.g. 4096) to
 * get to the signed 2-byte integers values that we save in the .isq file.
 *
 * e.g. Pixel value 8192 corresponds to lin. att. coeff. of 2.0 [1/cm]
 * (8192/4096)
 *
 * Following to the headers is the data part. It is in 2-byte short integers
 * (signed) and starts from the top-left pixel of slice 1 to the left, then
 * the next line follows, until the last pixel of the last sclice in the
 * lower right.
 */

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.io.FileInfo;
import ij.io.OpenDialog;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

/**
 * This plugin implements the Import > Scanco ISQ command.
 *
 * @author B. Koller, SCANCO Medical AG, April 2005
 * @author K.-H. Kunzelmann, Operative Dentistry, LMU-München, Ger, April 2006
 * @author Michael Doube, RVC, London, UK. Refactoring for BoneJ 2012 2014
 */
public class ISQReader implements PlugIn {

	private static final String MAGIC = "CTDATA-HEADER_V1";

	private long skipCount;
	private int bufferSize, byteCount, nPixels;
	private int eofErrorCount;

	@Override
	public void run(final String arg) {

		// the ISQ-File is selected
		final OpenDialog od = new OpenDialog("Open ISQ...", arg);
		final String directory = od.getDirectory();
		final String fileName = od.getFileName();
		final String path = directory + fileName;
		if (fileName == null)
			return;
		if (!isScancoISQ(path)) {
			IJ.error("ISQ Reader", "Not an ISQ file. Magic number (" + getMagic(path) + ")" + " does not match.");
			return;
		}

		final int[] imageSize = getImageSize(path);
		final int width = imageSize[0];
		final int height = imageSize[1];
		final int depth = imageSize[2];

		final GenericDialog gd = new GenericDialog("Import Scanco ISQ file");
		final String name = getPatientName(path);
		gd.addMessage("Patient:" + name + "\n");
		gd.addMessage(
				"\nEnter the coordinates for the bounding rectangle\n" + "to crop the microCT stack during import");

		gd.addNumericField("Upper_left_X: ", 0, 0);
		gd.addNumericField("Upper_left_Y: ", 0, 0);
		gd.addNumericField("Lower_right_X", width - 1, 0);
		gd.addNumericField("Lower_right_Y: ", height - 1, 0);
		gd.addNumericField("First_slice: ", 0, 0);
		gd.addNumericField("Number_of_slices: ", depth, 0);
		gd.addCheckbox("Downsample 2x", false);

		gd.showDialog();
		if (gd.wasCanceled())
			return;

		final int startX = (int) gd.getNextNumber();
		final int startY = (int) gd.getNextNumber();
		final int endX = (int) gd.getNextNumber();
		final int endY = (int) gd.getNextNumber();
		final int startZ = (int) gd.getNextNumber();
		final int nSlices = (int) gd.getNextNumber();
		final boolean downsample = gd.getNextBoolean();
		// Open the file
		try {
			final ImagePlus imp = openScancoISQ(path, downsample, startX, startY, endX, endY, startZ, nSlices);

			final String scancoHeaderdata = getHeaderData(path);
			imp.setProperty("Info", appendToFileInfo(imp, scancoHeaderdata));
			imp.show();
			UsageReporter.reportEvent(this).send();
		} catch (final IllegalArgumentException e) {
			IJ.error("ISQ Reader", e.getMessage());
			return;
		}
	}

	/**
	 * Opens a Scanco ISQ file as an ImageJ ImagePlus
	 *
	 * @param path
	 * @param downsample
	 * @param startX
	 * @param startY
	 * @param endX
	 * @param endY
	 * @param startZ
	 * @param nSlices
	 * @return
	 */
	public ImagePlus openScancoISQ(final String path, final boolean downsample, final int startX, final int startY,
			final int endX, final int endY, final int startZ, int nSlices) {

		final int[] imageSize = getImageSize(path);
		final int width = imageSize[0];
		final int height = imageSize[1];
		final int depth = imageSize[2];
		final double[] pixelSize = getPixelSize(path);
		final int offset = getOffset(path);
		if (startX < 0 || startX >= width || startY < 0 || startY >= height || endX < 0 || endX >= width || endY < 0
				|| endY >= height || startZ < 0 || startZ >= depth || nSlices < 1 || nSlices > depth - startZ)
			throw new IllegalArgumentException("Crop parameters fall outside image bounds");

		// FileInfo
		final FileInfo fi = new FileInfo();
		fi.fileName = new File(path).getName();
		fi.directory = new File(path).getParent() + ((IJ.isWindows()) ? "\\" : "/");
		fi.width = width;
		fi.height = height;
		fi.offset = offset;

		if (startZ > 0) {
			final long area = width * height;
			final long sliceTimesArea = area * startZ;
			// multiplication * 2 because a "short" value is 2 bytes long
			final long sliceTimesAreaTimes2 = sliceTimesArea * 2;
			final long dummy = fi.offset + sliceTimesAreaTimes2;

			if (dummy <= Integer.MAX_VALUE && dummy > 0) {
				// 2 is hardcoded no. of bytesPerPixel (short)
				fi.offset += (startZ * width * height * 2);
			} else {
				fi.longOffset = fi.offset + sliceTimesAreaTimes2;
			}
		}
		if (nSlices > getImageSize(path)[2] - startZ) {
			nSlices = getImageSize(path)[2] - startZ;
		}

		if (offset <= Integer.MAX_VALUE && offset > 0) {
			fi.offset = offset;
		}
		if (offset > Integer.MAX_VALUE) {
			fi.longOffset = offset;
		}
		fi.nImages = nSlices;
		fi.gapBetweenImages = 0;
		fi.intelByteOrder = true;
		fi.whiteIsZero = false;
		fi.fileType = FileInfo.GRAY16_SIGNED;
		fi.pixelWidth = pixelSize[0];
		fi.pixelHeight = pixelSize[1];
		fi.pixelDepth = pixelSize[2];
		fi.unit = "mm";

		int widthStack;
		int heightStack;

		final int widthROI = endX - startX + 1;
		final int heightROI = endY - startY + 1;

		if (downsample == true) {

			fi.pixelWidth = fi.pixelWidth * 2;
			fi.pixelHeight = fi.pixelHeight * 2;
			widthStack = widthROI / 2;
			heightStack = heightROI / 2;
		} else {
			widthStack = widthROI;
			heightStack = heightROI;
		}

		// modified to match the size of the ROI
		final ImageStack stack = new ImageStack(widthStack, heightStack);
		long skip = fi.longOffset > 0 ? fi.longOffset : fi.offset;
		skip += startZ * width * height * 2;

		try {
			final FileInputStream is = new FileInputStream(path);

			for (int i = 1; i <= nSlices; i++) {
				IJ.showStatus("Reading: " + i + "/" + nSlices);

				// read the whole slice into an array
				// this is really inefficient if only a small ROI is needed
				// as must read all pixels off disk then throw them away
				final short[] pixels = readPixels(is, skip, width, height);

				// get pixels for ROI only
				int indexCountPixels = startY * width + startX;
				int indexCountROI = 0;

				final short[] pixelsROI = new short[widthROI * heightROI];

				for (int u = 0; u < heightROI; u++) {
					System.arraycopy(pixels, indexCountPixels, pixelsROI, indexCountROI, widthROI);
					indexCountPixels += width;
					indexCountROI += widthROI;
				}

				if (pixels == null)
					break;

				if (downsample) {
					final ImageProcessor ip = new ShortProcessor(widthROI, heightROI);
					ip.setPixels(pixelsROI);
					ip.setInterpolationMethod(ImageProcessor.BICUBIC);
					final ImageProcessor downsizedIp = ip.resize(widthROI / 2, heightROI / 2, true);
					stack.addSlice("microCT-Import_by_KHK_w_" + widthROI + "_h_" + heightROI + "_slice." + i,
							downsizedIp);

				} else {

					for (int index = 0; index < widthROI * heightROI; index++) {
						pixelsROI[index] = (short) (pixelsROI[index] - 32768);
						if (pixelsROI[index] < 0)
							pixelsROI[index] = 0;
					}

					stack.addSlice("microCT-Import_by_KHK_w_" + widthROI + "_h_" + heightROI + "_slice." + i,
							pixelsROI);
				}

				skip = fi.gapBetweenImages;
				IJ.showProgress((double) i / nSlices);
			}
			is.close();
		} catch (final Exception e) {
			IJ.log("" + e);
		} catch (final OutOfMemoryError e) {
			IJ.outOfMemory(fi.fileName);
			stack.trim();
		}
		if (stack.getSize() == 0)
			return null;
		if (fi.sliceLabels != null && fi.sliceLabels.length <= stack.getSize()) {
			for (int i = 0; i < fi.sliceLabels.length; i++)
				stack.setSliceLabel(fi.sliceLabels[i], i + 1);
		}
		final ImagePlus imp = new ImagePlus(fi.fileName, stack);
		final Calibration cal = imp.getCalibration();

		if (fi.info != null)
			imp.setProperty("Info", fi.info);
		imp.setFileInfo(fi);

		cal.pixelWidth = (downsample) ? pixelSize[0] * 2 : pixelSize[0];
		cal.pixelHeight = (downsample) ? pixelSize[1] * 2 : pixelSize[1];
		cal.pixelDepth = pixelSize[2];
		cal.setUnit("mm");
		cal.xOrigin = -startX;
		cal.yOrigin = -startY;
		cal.zOrigin = -startZ;
		cal.setFunction(Calibration.STRAIGHT_LINE, new double[] { 0, 1.0 / getMuScaling(path) }, "1/cm");
		imp.setCalibration(cal);
		// set display range
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		final int n = stack.getSize();
		for (int i = 1; i <= n; i++) {
			IJ.showStatus("Calculating stack min and max: " + i + "/" + n);
			final ImageProcessor ip = stack.getProcessor(i);
			max = Math.max(max, ip.getMax());
			min = Math.min(min, ip.getMin());
		}
		imp.getProcessor().setMinAndMax(min, max);
		IJ.showProgress(1.0);
		return imp;
	}

	/**
	 * ***********************************************************************
	 **/
	/**
	 * from ImageReader.java: Skips the specified number of bytes, then reads an
	 * image and returns the pixel array (byte, short, int or float). Returns
	 * null if there was an IO exception. Does not close the InputStream.
	 */
	private short[] readPixels(final FileInputStream in, final long skipCount, final int width, final int height) {
		this.skipCount = skipCount;
		final short[] pixels = readPixels(in, width, height);
		if (eofErrorCount > 0)
			return null;
		return pixels;
	}

	/**
	 * Reads the image from the InputStream and returns the pixel array (byte,
	 * short, int or float). Returns null if there was an IO exception. Does not
	 * close the InputStream.
	 *
	 * @param in
	 * @param width
	 * @param height
	 * @return
	 */
	private short[] readPixels(final FileInputStream in, final int width, final int height) {
		try {
			skip(in, width, height);
			return read16bitImage(in);

		} catch (final IOException e) {
			IJ.log("" + e);
			return null;
		}
	}

	/**
	 * Reads a 16-bit image. Signed pixels are converted to unsigned by adding
	 * 32768.
	 *
	 * @param in
	 * @return
	 * @throws IOException
	 */
	private short[] read16bitImage(final FileInputStream in) throws IOException {
		int pixelsRead;
		final byte[] buffer = new byte[bufferSize];
		final short[] pixels = new short[nPixels];
		int totalRead = 0;
		int base = 0;
		int count;

		while (totalRead < byteCount) {
			if ((totalRead + bufferSize) > byteCount)
				bufferSize = byteCount - totalRead;

			count = in.read(buffer, 0, bufferSize);

			if (count == -1) {
				eofErrorCount++;
				Arrays.fill(pixels, Short.MAX_VALUE);
				return pixels;
			}
			totalRead += bufferSize;
			// divide by two because there are 2 bytes per pixel
			pixelsRead = bufferSize / 2;
			for (int i = base, j = 0; i < (base + pixelsRead); i++, j += 2)
				pixels[i] = (short) ((((buffer[j + 1] & 0xff) << 8) | (buffer[j] & 0xff)) + 32768);
			base += pixelsRead;
		}
		return pixels;
	}

	/**
	 *
	 * @param in
	 * @param width
	 * @param height
	 * @throws IOException
	 */
	private void skip(final FileInputStream in, final int width, final int height) throws IOException {

		// This routine is called for every slice

		if (skipCount > 0) {
			long bytesRead = 0;
			int skipAttempts = 0;
			long count;
			while (bytesRead < skipCount) {
				count = in.skip(skipCount - bytesRead);
				skipAttempts++;
				if (count == -1 || skipAttempts > 5)
					break;
				bytesRead += count;

			}
		}
		byteCount = width * height * 2; // 16-bit images so 2 bytes per pixel

		nPixels = width * height;
		bufferSize = byteCount / 25;
		if (bufferSize < 8192)
			bufferSize = 8192;
		else
			bufferSize = (bufferSize / 8192) * 8192;
	}

	/**
	 * Check the magic number to determine if a file is a Scanco ISQ
	 *
	 * @param path
	 * @return true if the file is a Scanco ISQ
	 */
	public boolean isScancoISQ(final String path) {
		if (getMagic(path).equals(MAGIC))
			return true;
		return false;
	}

	/**
	 * Get the magic number from a file
	 *
	 * @param path
	 * @return the first 16 bytes as a character string
	 */
	public String getMagic(final String path) {
		return readString(path, 0, 16);
	}

	public int getPatientIndex(final String path) {
		return readInt(path, 28);
	}

	public int getScannerID(final String path) {
		return readInt(path, 32);
	}

	public Date getCreationDate(final String path) {
		final int[] quadWord = new int[8];

		if (path == null)
			throw new IllegalArgumentException();
		try {
			final File iFile = new File(path);
			final FileInputStream p = new FileInputStream(iFile);
			p.skip(36);
			for (int index = 7; index >= 0; index--) {
				quadWord[index] = p.read();
			}
			p.close();
			return vmsQuadwordToTimestamp(quadWord);
		} catch (final Exception e) {
			IJ.handleException(e);
		}
		return null;
	}

	/**
	 * Get the ISQ creation date as a string
	 *
	 * @param path
	 * @return date in yyyy-MM-dd HH:mm:ss format
	 */
	public String getCreationDateAsString(final String path) {
		final Date date = getCreationDate(path);
		final DateFormat df = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss");
		final String reportDate = df.format(date);
		return reportDate;
	}

	/**
	 * Get the pixel size of the image
	 *
	 * @param path
	 * @return {x, y, z} image size in pixels
	 */
	public int[] getImageSize(final String path) {
		final int[] sizes = { readInt(path, 44), readInt(path, 48), readInt(path, 52) };
		return sizes;
	}

	/**
	 * Get the real size of the image
	 *
	 * @param path
	 * @return {x, y, z} image size in metric units
	 */
	public double[] getRealSize(final String path) {
		final double[] sizes = { readInt(path, 56) / 1000.0, readInt(path, 60) / 1000.0, readInt(path, 64) / 1000.0 };
		return sizes;
	}

	public int getSliceThickness(final String path) {
		return readInt(path, 68);
	}

	public int getSliceIncrement(final String path) {
		return readInt(path, 72);
	}

	public int getScanDistance(final String path) {
		return readInt(path, 76);
	}

	public int getMinDataValue(final String path) {
		return readInt(path, 80);
	}

	public int getMaxDataValue(final String path) {
		return readInt(path, 84);
	}

	public int getMuScaling(final String path) {
		return readInt(path, 88);
	}

	public int getNrSamples(final String path) {
		return readInt(path, 92);
	}

	public int getNrProjections(final String path) {
		return readInt(path, 96);
	}

	public int getScanDistanceUm(final String path) {
		return readInt(path, 100);
	}

	public int getScannerType(final String path) {
		return readInt(path, 104);
	}

	public int getSampleTimeUs(final String path) {
		return readInt(path, 108);
	}

	public int getMeasurementIndex(final String path) {
		return readInt(path, 112);
	}

	public int getSite(final String path) {
		return readInt(path, 116);
	}

	public int getReferenceLineUm(final String path) {
		return readInt(path, 120);
	}

	public int getReconstructionAlgorithm(final String path) {
		return readInt(path, 124);
	}

	public String getPatientName(final String path) {
		return readString(path, 128, 40);
	}

	public int getEnergy(final String path) {
		return readInt(path, 168);
	}

	public int getIntensity(final String path) {
		return readInt(path, 172);
	}

	// what to do with 176 int fill[83]? Skip?

	public int getOffset(final String path) {
		return readInt(path, 508) * 512 + 512;
	}

	/**
	 * Get the pixel spacing in real units (mm)
	 *
	 * @param path
	 *            location of file in filesystem
	 * @return {x, y, z} pixel spacing in real units (mm)
	 */
	public double[] getPixelSize(final String path) {
		final int[] nPixels = getImageSize(path);
		final double[] realSize = getRealSize(path);
		final double[] pixelSize = { realSize[0] / nPixels[0], realSize[1] / nPixels[1], realSize[2] / nPixels[2] };
		return pixelSize;
	}

	private int readInt(final String path, final int firstByte) {
		if (path == null)
			throw new IllegalArgumentException();
		try {
			final File iFile = new File(path);
			final FileInputStream p = new FileInputStream(iFile);
			p.skip(firstByte);
			final int value = (p.read() + p.read() * 256 + p.read() * 65536 + p.read() * 256 * 65536);
			p.close();
			return value;
		} catch (final IOException e) {
			IJ.handleException(e);
		}
		return -1;
	}

	private String readString(final String path, final int firstByte, final int length) {
		if (path == null)
			throw new IllegalArgumentException();
		try {
			final File iFile = new File(path);
			final FileInputStream p = new FileInputStream(iFile);
			p.skip(firstByte);
			String string = "";
			for (int kh = 0; kh < length; kh++) {
				final char ch = (char) p.read();
				string += ch;
			}
			p.close();
			return string;
		} catch (final IOException e) {
			IJ.handleException(e);
		}
		return null;
	}

	private String getHeaderData(final String path) {
		if (path == null) {
			throw new IllegalArgumentException();
		}
		try {
			final File iFile = new File(path);
			final FileInputStream p = new FileInputStream(iFile);

			final String headerData = "Scanco Header Data\n\n" + "Patient Name: " + getPatientName(path) + "\n"
					+ "Patient Index: " + getPatientIndex(path) + "\n" + "Site: " + getSite(path) + "\n"
					+ "Reference Line: " + getReferenceLineUm(path) + " µm\n" + "Scanner-ID: " + getScannerID(path)
					+ "\n" + "Scanner_type: " + getScannerType(path) + "\n" + "Creation Date: "
					+ getCreationDateAsString(path) + "\n" + "Slice Thickness: " + getSliceThickness(path) + " µm\n"
					+ "Slice Increment: " + getSliceIncrement(path) + " µm\n" + "Min Value: " + getMinDataValue(path)
					+ "\n" + "Max Value: " + getMaxDataValue(path) + "\n" + "µ-Scaling: " + getMuScaling(path) + "\n"
					+ "Scan-Distance: " + getScanDistanceUm(path) + " µm\n" + "Sampletime: " + getSampleTimeUs(path)
					+ " µs\n" + "Samples: " + getNrSamples(path) + "\n" + "Projections: " + getNrProjections(path)
					+ "\n" + "Reconstruction Algorithm: " + getReconstructionAlgorithm(path) + "\n"
					+ "Measurement Index: " + getMeasurementIndex(path) + "\n" + "Energy : " + getEnergy(path) + " V\n"
					+ "Intensity : " + getIntensity(path) + " µA";

			p.close();
			return headerData;

		} catch (final IOException e) {
			IJ.handleException(e);
		}
		return null;
	}

	/**
	 * converts the VMS quadword timestamp to date/time
	 *
	 * OpenVMS and Unix Date and Time Conversions
	 *
	 * The creation date of the Scanco ISQ files is coded as an 8 Byte sequence
	 * which is called a quadword. The byte order of the creation date is
	 * "big endian".
	 *
	 * In summary, the 8 Bytes encode a large number, which represents the
	 * numbers of 100 nanosecond intervals since 00:00 on November 17, 1858
	 * local time; the OpenVMS Epoch.
	 *
	 * To convert this number to a date/time just follow the recommendations of
	 * Stephen Hoffman:
	 *
	 * "To get from the OpenVMS quadword to the C quadword, subtract the OpenVMS
	 * quadword value containing the Unix epoch value for 1-Jan-1970:00:00
	 * (0x007c95674beb4000) from the OpenVMS quadword value, and then divide by
	 * 10000000 to get from the 100ns-unit to the seconds longword."
	 *
	 * For Java the divisor is a bit different as the date functions of Java are
	 * based on ms (milliseconds) and not on s. Therefore we just have to divide
	 * by 10000.
	 *
	 * A word of caution has to be added here:
	 *
	 * It took me a few hours to figure out that the use of the recommended
	 * Calender object and its methods either was not correctly initialized by
	 * me or it is simply buggy. I could not get the correct creation date. The
	 * date was always 1 month off the original value.
	 *
	 * When I changed to the depreciated approach to use the "Date" object
	 * everything worked fine.
	 *
	 * Read the invaluable background information from Stephen Hoffman, Hoffman
	 * Labs. Especially the conversion utility was very helpful for debugging
	 * this routine.
	 *
	 * @see http://labs.hoffmanlabs.com/node/735
	 * @see http://labs.hoffmanlabs.com/node/282
	 * @see http://www.mpp.mpg.de/~huber/util/main/cvdate.html
	 * @param vmsQuadWord
	 *            A VMS quadword - they are 64 bit unsigned integers. The system
	 *            base date is: November 17, 1858 00:00:00.00
	 * @return Date object containing the creation date of the ISQ file
	 */
	private Date vmsQuadwordToTimestamp(final int[] vmsQuadWord) {

		String hexString = "";

		// OpenVMS VAX, OpenVMS Alpha and OpenVMS I64 (as well as all Microsoft
		// Windows implementations)
		// all support and all use the little-endian byte ordering. BUT in our
		// case - found by trial and error -
		// we have to use the big-endian byte order.

		for (int index = 0; index < 8; index++) {

			// if ...else loop: just necessary to format the byte in the string
			// correct for later use
			if (vmsQuadWord[index] > 0xf) {
				hexString += Integer.toHexString(vmsQuadWord[index]);
			} else {
				hexString += "0" + Integer.toHexString(vmsQuadWord[index]);
			}
		}

		BigInteger bi = new BigInteger(hexString, 16);
		final BigInteger epochAsBigInteger = new BigInteger("007C95674BEB4000", 16);
		bi = bi.subtract(epochAsBigInteger);
		final BigInteger divisor = BigInteger.valueOf(10000);
		bi = bi.divide(divisor);
		final long value = bi.longValue();
		final Date date = new Date();
		date.setTime(value);
		return date;
	}

	/**
	 * adds the content of a string to the ImagePlus property which is labeled
	 * "Info" only the content of "Info" is displayed with the "Show Info"
	 * -Command from the menu.
	 */
	private String appendToFileInfo(final ImagePlus imp, final String newinfo) {

		String contentOfImagePlusPropertyInfo = (String) imp.getProperty("Info");

		if (contentOfImagePlusPropertyInfo == null) {
			contentOfImagePlusPropertyInfo = newinfo;
		} else {
			contentOfImagePlusPropertyInfo = contentOfImagePlusPropertyInfo + "\n------------------------\n" + newinfo;
		}

		return contentOfImagePlusPropertyInfo;
	}

}