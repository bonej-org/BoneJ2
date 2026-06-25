/*-
 * #%L
 * Utility methods for BoneJ2
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


package org.bonej.utilities;

import static java.util.stream.Collectors.toList;
import static org.bonej.utilities.Streamers.axisStream;
import static org.bonej.utilities.Streamers.spatialAxisStream;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import net.imagej.Dataset;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.TypedAxis;
import net.imagej.space.AnnotatedSpace;
import net.imagej.units.UnitService;

import org.scijava.util.StringUtils;

/**
 * Various utils for inspecting image axis properties
 *
 * @author Richard Domander
 */
public final class AxisUtils {

	private AxisUtils() {}

	/**
	 * Counts the number of spatial dimensions in the given space.
	 *
	 * @param space an N-dimensional space.
	 * @param <S> type of the space.
	 * @param <A> type of axes in the space.
	 * @return number of spatial dimensions in the space.
	 */
	public static <S extends AnnotatedSpace<A>, A extends TypedAxis> long
		countSpatialDimensions(final S space)
	{
		return spatialAxisStream(space).count();
	}

	/**
	 * Returns the common unit of the spatial calibrations of the given space.
	 * <p>
	 * The common unit is the unit of the first spatial axis if it can be
	 * converted to the units of the other axes.
	 * </p>
	 *
	 * @param space an n-dimensional space with calibrated axes.
	 * @param <S> type of the space.
	 * @param <C> type of axis in the space
	 * @param unitService an {@link UnitService} to convert axis calibrations.
	 * @return an optional with the unit of spatial calibration. It's empty if
	 *         there's no conversion between the units of spatial axes. The
	 *         Optional contains an empty string none of the axes have a unit,
	 *         i.e. they're uncalibrated.
	 * @throws IllegalArgumentException if space has no spatial axes.
	 */
	public static <S extends AnnotatedSpace<C>, C extends CalibratedAxis> Optional<String>
		getSpatialUnit(final S space, final UnitService unitService) throws IllegalArgumentException
	{
		if (!hasSpatialDimensions(space)) {
			throw new IllegalArgumentException("Space has no spatial axes.");
		}
		if (!isUnitsConvertible(space, unitService)) {
			return Optional.empty();
		}
		final String unit = space.axis(0).unit();
		return unit == null ? Optional.of("") : Optional.of(unit);
	}

	/**
	 * Checks if the given space has a channel dimension.
	 *
	 * @param space an N-dimensional space.
	 * @param <S> type of the space.
	 * @param <A> type of axes in the space.
	 * @return true if there are any channel type axes.
	 */
	public static <S extends AnnotatedSpace<A>, A extends TypedAxis> boolean
		hasChannelDimensions(final S space)
	{
		return axisStream(space)
				.filter(a -> a != null)
				.anyMatch(a -> a.type() == Axes.CHANNEL);
	}

	/**
	 * Checks if the given space has any spatial dimensions.
	 *
	 * @param space an N-dimensional space.
	 * @param <S> type of the space.
	 * @param <A> type of axes in the space.
	 * @return true if there are any spatial type axes.
	 */
	public static <S extends AnnotatedSpace<A>, A extends TypedAxis> boolean
		hasSpatialDimensions(final S space)
	{
		return axisStream(space)
				.filter(a -> a != null)
				.anyMatch(a -> a.type().isSpatial());
	}
	
	/**
	 * Checks if the given annotated space contains both X and Y axes.
	 *
	 * @param space the annotated space to check.
	 * @param <S> type of the space.
	 * @param <A> type of axes in the space.
	 * @return true if both X and Y axes are present, false otherwise.
	 */
	public static <S extends AnnotatedSpace<A>, A extends TypedAxis> boolean
	    hasXYDimensions(final S space)
	{
	    boolean hasX = false;
	    boolean hasY = false;
	    for (A axis : axisStream(space).collect(Collectors.toList())) {
	    	if (axis == null) continue;
	        if (axis.type() == Axes.X) hasX = true;
	        if (axis.type() == Axes.Y) hasY = true;
	        if (hasX && hasY) return true;
	    }
	    return false;
	}

	/**
	 * Checks if the given space has a time dimension.
	 *
	 * @param space an N-dimensional space.
	 * @param <S> type of the space.
	 * @param <A> type of axes in the space.
	 * @return true if there are any time type axes.
	 */
	public static <S extends AnnotatedSpace<A>, A extends TypedAxis> boolean
		hasTimeDimensions(final S space)
	{
		return axisStream(space)
				.filter(a -> a != null)
				.anyMatch(a -> a.type() == Axes.TIME);
	}

	/**
	 * Checks whether the given annotated space contains any dimension (axis)
	 * that is not one of the standard X, Y, Z, Channel, or Time axes.
	 *
	 * <p>This method is useful for detecting the presence of non-standard axes
	 * (e.g., Angle, Lambda, Phase, etc.) in an image or dataset, which may require
	 * special handling in algorithms or processing pipelines.</p>
	 *
	 * @param <S>  the type of the annotated space, which must extend {@code AnnotatedSpace<A>}
	 * @param <A>  the type of the axis, which must extend {@code TypedAxis}
	 * @param space the annotated space (e.g., image or dataset) to check for non-standard dimensions
	 * @return     {@code true} if the space contains at least one axis that is not
	 *             X, Y, Z, Channel, or Time; {@code false} otherwise
	 *
	 * @see AnnotatedSpace
	 * @see TypedAxis
	 * @see Axes
	 */
	public static <S extends AnnotatedSpace<A>, A extends TypedAxis> boolean
		hasNonXYZCTDimension(final S space)
	{
		return axisStream(space)
				.filter(a -> a != null)
				.anyMatch(a -> (
			a.type() != Axes.X &&
			a.type() != Axes.Y &&
			a.type() != Axes.Z &&
			a.type() != Axes.CHANNEL &&
			a.type() != Axes.TIME
		));
	}
	
	/**
	 * Checks if the spatial axes in the space have the same i.e. isotropic
	 * scaling.
	 * <p>
	 * If calibrations are isotropic, then the values returned by
	 * {@link CalibratedAxis#averageScale(double, double)} differ only within the
	 * given tolerance. For example, if <em>X-axis scale = 1.00</em>, <em>Y-axis
	 * scale = 1.02</em>, and <em>tolerance = 0.03</em>, then calibration is
	 * isotropic.
	 * </p>
	 * <p>
	 * NB if the calibrations of the axes are not in the same unit, the method
	 * tries to convert them to the unit of the first spatial axis (x-axis).
	 * </p>
	 *
	 * @param <S> type of the space
	 * @param <A> type of the axes in the space
	 * @param space a space with spatial axes
	 * @param tolerance tolerance for anisotropy in scaling
	 * @param unitService service to convert between units of calibration
	 * @return true if spatial calibrations are isotropic within tolerance
	 * @throws IllegalArgumentException if tolerance is negative or NaN, or
	 *           calibration units cannot be converted
	 */
	public static <S extends AnnotatedSpace<A>, A extends CalibratedAxis> boolean
		isSpatialCalibrationsIsotropic(final S space, final double tolerance,
			final UnitService unitService) throws IllegalArgumentException
	{
		if (tolerance < 0.0) {
			throw new IllegalArgumentException("Tolerance cannot be negative");
		}
		if (Double.isNaN(tolerance)) {
			throw new IllegalArgumentException("Tolerance cannot be NaN");
		}
		final Optional<String> commonUnit = getSpatialUnit(space, unitService);
		if (!commonUnit.isPresent()) {
			throw new IllegalArgumentException(
				"Isotropy cannot be determined: units of spatial calibrations are inconvertible");
		}
		
		final double anisotropy = getAnisotropy(space, unitService);
		
		if (anisotropy - tolerance > 1e-12) {
			return false;
		}
		
		return true;
	}

	/**
	 * Calculates the worst-case anisotropy deviation between spatial axes in the given space.
	 * <p>
	 * This method determines the ratio of the coarsest resolution (largest scale) to the finest
	 * resolution (smallest scale) among all spatial dimensions, after converting them to a common unit.
	 * The result represents the relative deviation from isotropy:
	 * </p>
	 * <ul>
	 *   <li><b>{@code 0.0}</b>: The space is perfectly isotropic (all spatial scales are equal).</li>
	 *   <li><b>{@code > 0.0}</b>: The space is anisotropic. A value of {@code X} indicates that the
	 *       coarsest axis is {@code (X * 100)}% larger than the finest axis.
	 *       (e.g., {@code 2.0} means the coarsest voxel is 3 times larger than the finest).</li>
	 *   <li><b>{@code Double.POSITIVE_INFINITY}</b>: Returned if the finest scale is zero or negative,
	 *       indicating undefined or infinite anisotropy.</li>
	 * </ul>
	 * <p>
	 * The calculation formula is: {@code (max_scale / min_scale) - 1.0}.
	 * </p>
	 *
	 * @param <S> the type of the annotated space (e.g., {@link Dataset})
	 * @param <A> the type of the calibrated axis
	 * @param space the annotated space containing calibration data
	 * @param unitService the {@link UnitService} used to convert axis units to a common denominator
	 * @return the anisotropy deviation value (0.0 for isotropic, positive for anisotropic, infinity if min scale is &lt;= 0)
	 * @throws IllegalArgumentException if:
	 *         <ul>
	 *           <li>{@code space} has no spatial dimensions (X, Y, or Z)</li>
	 *           <li>the spatial units cannot be converted to a common unit (e.g., mixing time and length)</li>
	 *           <li>{@code unitService} is null</li>
	 *         </ul>
	 * 
	 * @see #getSpatialUnit(AnnotatedSpace, UnitService)
	 * @see CalibratedAxis#averageScale(long, long)
	 */
	public static <S extends AnnotatedSpace<A>, A extends CalibratedAxis> double getAnisotropy(
			final S space, final UnitService unitService) throws IllegalArgumentException {
		
		if (!hasSpatialDimensions(space)) {
			throw new IllegalArgumentException("Isotropy cannot be determined: no spatial axes");
		}
		
		final Optional<String> commonUnit = getSpatialUnit(space, unitService);
		if (!commonUnit.isPresent()) {
			throw new IllegalArgumentException(
				"Isotropy cannot be determined: units of spatial calibrations are inconvertible");
		}
		
		final String outputUnit = commonUnit.get();
		
		final double[] scales = spatialAxisStream(space).mapToDouble(
				axis -> unitService.value(axis.averageScale(0, 1), axis.unit(),
					outputUnit)).sorted().toArray();
		
		if (scales.length < 2)
			return 0;
		
	    double minScale = scales[0];
	    double maxScale = scales[scales.length - 1];
	    
	    if (minScale <= 0.0) {
	        return Double.POSITIVE_INFINITY;
	    }
	    
	    return (maxScale / minScale) - 1.0;
	}
	
	// region -- Helper methods --

	/**
	 * Returns true if the scales of the all the axes can be converted to each
	 * other
	 * <p>
	 * NB Returns true also when all axes are uncalibrated (no units)
	 * </p>
	 */
	private static <T extends AnnotatedSpace<C>, C extends CalibratedAxis> boolean
		isUnitsConvertible(final T space, final UnitService unitService)
	{
		final long spatialDimensions = countSpatialDimensions(space);
		final long uncalibrated = spatialAxisStream(space).map(CalibratedAxis::unit)
			.filter(StringUtils::isNullOrEmpty).count();
		if (uncalibrated == spatialDimensions) {
			return true;
		}
		if (uncalibrated > 0) {
			return false;
		}
		// DefaultUnitService handles microns as "um" instead of "µm",
		final List<String> units = spatialAxisStream(space).map(
			CalibratedAxis::unit).distinct().map(s -> s.replaceFirst("^µ[mM]$", "um"))
			.collect(toList());
		for (int i = 0; i < units.size(); i++) {
			for (int j = i; j < units.size(); j++) {
				try {
					unitService.value(1.0, units.get(i), units.get(j));
				}
				catch (final IllegalArgumentException e) {
					return false;
				}
			}
		}
		return true;
	}
	
	/**
	 * Get the interval spacing in real units along an axis
	 * 
	 * @param ds the dataset to inspect
	 * @param type the type of axis to check
	 * @return the average sample spacing in calibrated units along the given axis
	 */
	public static double getScale(Dataset ds, AxisType type) {
		
	    long numPixels = ds.dimension(type);
	    if (numPixels <= 1) return 1.0;
	    
	    Optional<CalibratedAxis> opt = ds.axis(type);

	    if (opt.isPresent()) {
	        CalibratedAxis axis = opt.get();
	        return Math.abs(axis.calibratedValue(numPixels - 1)
	        		- axis.calibratedValue(0)) / (numPixels - 1);
	    }
	    return 1.0;
	}
	
	/**
	 * Check whether the dataset has exactly 3 spatial dimensions.
	 * 
	 * Ignores whether there are (or are not) any other dimensions.
	 * 
	 * @param ds the dataset to check
	 * @return true if the dataset has exactly 3 spatial dimensions
	 */
	public static boolean has3SpatialDimensions(Dataset ds) {
		return hasNSpatialDimensions(ds, 3);
	}
	
	/**
	 * Check whether the dataset has exactly 2 spatial dimensions.
	 * 
	 * Ignores whether there are (or are not) any other dimensions.
	 * 
	 * @param ds the dataset to check
	 * @return true if the dataset has exactly 2 spatial dimensions
	 */
	public static boolean has2SpatialDimensions(Dataset ds) {
		return hasNSpatialDimensions(ds, 2);
	}	
	
	/**
	 * Check whether the dataset has exactly N spatial dimensions.
	 * 
	 * Ignores whether there are (or are not) any other dimensions.
	 * 
	 * @param ds the dataset to check
	 * @param n the number of spatial dimensions required
	 * @return true if the dataset has exactly n spatial dimensions
	 */
	public static boolean hasNSpatialDimensions(Dataset ds, int n) {
		final int nD = ds.numDimensions();
		if (nD < n) return false;
		
		int nSpatialAxes = 0;
		for (int d = 0; d < nD; d++) {
			if (ds.axis(d).type().isSpatial())
				nSpatialAxes++;
		}
		
		if (nSpatialAxes == n)
			return true;
		
		return false;
	}	
	
}
