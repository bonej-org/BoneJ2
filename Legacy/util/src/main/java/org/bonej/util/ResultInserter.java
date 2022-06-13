/*-
 * #%L
 * Utility classes for BoneJ1 plugins
 * %%
 * Copyright (C) 2015 - 2022 Michael Doube, BoneJ developers
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
/*
BSD 2-Clause License
Copyright (c) 2018, Michael Doube, Richard Domander, Alessandro Felder
All rights reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.bonej.util;

import ij.ImagePlus;
import ij.measure.ResultsTable;

/**
 * Receive results from analytic methods and insert them into the Results table
 * in a sensible way.
 * <p>
 * Each image gets a line; measurements of different types are added to the same
 * line; repeat measurements on same image go on a new line.
 * </p>
 *
 * @author Michael Doube
 */
public final class ResultInserter {

	private static final ResultInserter INSTANCE = new ResultInserter();
	private static ResultsTable rt;

	private ResultInserter() {}

	/**
	 * Finds the first available space for a result, avoiding lots of empty space
	 * when measurements of different types are made on the same image
	 *
	 * @param imp ImagePlus
	 * @param colHeading column heading
	 * @param value value to insert
	 */
	// TODO use a table other than the system Results table
	public void setResultInRow(final ImagePlus imp, final String colHeading,
		final double value)
	{
		final String title = imp.getTitle();

		// search for the last value that contains the image title
		// and contains no value for the heading
		for (int row = rt.getCounter()-1; row >= 0; row--) {
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
		rt.incrementCounter();
		rt.addLabel(title);
		rt.addValue(colHeading, value);
	}

	/**
	 * Show the table
	 */
	public void updateTable() {
		final String table = "Results";
		rt.show(table);
	}

	public static ResultInserter getInstance() {
		rt = ResultsTable.getResultsTable();
		rt.setNaNEmptyCells(true);
		return INSTANCE;
	}
}
