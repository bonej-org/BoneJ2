/*
 * #%L
 * BoneJ utility classes.
 * %%
 * Copyright (C) 2007 - 2016 Michael Doube, BoneJ developers.
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
package org.doube.util;

import ij.ImagePlus;
import ij.measure.ResultsTable;

/**
 * Receive results from analytic methods and insert them into the Results table
 * in a sensible way.
 *
 * <p>
 * Each image gets a line; measurements of different types are added to the same
 * line; repeat measurements on same image go on a new line.
 * </p>
 *
 * @author Michael Doube
 *
 */
public class ResultInserter {
	public static final ResultInserter INSTANCE = new ResultInserter();
	private static ResultsTable rt;

	private ResultInserter() {
	}

	public static ResultInserter getInstance() {
		rt = ResultsTable.getResultsTable();
		final String table = "Results";
		rt.setNaNEmptyCells(true);
		return INSTANCE;
	}

	/**
	 * Finds the first available space for a result, avoiding lots of empty
	 * space when measurements of different types are made on the same image
	 *
	 * @param imp
	 *            ImagePlus
	 * @param colHeading
	 *            column heading
	 * @param value
	 *            value to insert
	 */
	// TODO use a table other than the system Results table
	public void setResultInRow(final ImagePlus imp, final String colHeading, final double value) {
		final String title = imp.getTitle();

		// search for the first row that contains the image title
		// and contains no value for the heading
		for (int row = 0; row < rt.getCounter(); row++) {
			if (rt.getLabel(row) == null) {
				rt.setLabel(title, row);
			}
			if (rt.getLabel(row).equals(title)) {
				// there could be no column called colHeading
				if (!rt.columnExists(rt.getColumnIndex(colHeading))) {
					// in which case, just insert the value
					rt.setValue(colHeading, row, value);
					return;
				} // but if there is, it might or might not have data in it
				final Double currentValue = rt.getValue(colHeading, row);
				if (currentValue.equals(Double.NaN)) {
					rt.setValue(colHeading, row, value);
					return;
				}
				// look for another row with the right title
			}
		}
		// we got to the end of the table without finding a space to insert
		// the value, so make a new row for it
		final String label = "Image";
		rt.incrementCounter();
		rt.addLabel(label, title);
		rt.addValue(colHeading, value);
	}

	/**
	 * Show the table
	 */
	public void updateTable() {
		final String table = "Results";
		rt.show(table);
	}
}
