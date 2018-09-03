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

package org.bonej.utilities;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.IntStream;

import net.imagej.table.DefaultColumn;
import net.imagej.table.DefaultGenericTable;
import net.imagej.table.Table;

import org.scijava.util.StringUtils;

/**
 * Stores a {@link Table}, which is ordered according to the following rules:
 * <ol>
 * <li>Each row has a header label, which tells you which image was measured,
 * e.g. "bat-cochlea-volume.tif"</li>
 * <li>Each column has a header, which tells you the type of the measurement,
 * e.g. "Volume"</li>
 * <li>If there are no rows with the given label, then add a new row</li>
 * <li>If there are rows with the given label, but there is not a column with
 * the given heading, then append a column, and set its value on the last row
 * with the label.</li>
 * <li>If there are rows with the given label, and there's a column with the
 * given heading, then find the last empty cell (equals {@link #EMPTY_CELL}),
 * and add the new value there. If there are no empty cells, then append a new
 * row.</li>
 * <li>Labels and columns are kept in the order in which they were
 * produced.</li>
 * </ol>
 *
 * @author Richard Domander
 * @author Michael Doube
 */
public final class SharedTable {

	public static final Double EMPTY_CELL = null;

	/**
	 * The table uses Double values. Empty cells are indicated by null
	 */
	private static Table<DefaultColumn<Double>, Double> table = createTable();

	private static Table<DefaultColumn<Double>, Double> publicCopy;

	private SharedTable() {}

	/**
	 * Adds new value as a {@link Double} to the shared table.
	 *
	 * @see #add(String, String, Double)
	 * @param label the row label of the new data.
	 * @param header the column heading of the new data.
	 * @param value the value of the new data.
	 */
	public static void add(final String label, final String header,
		final long value)
	{
		add(label, header, new Double(value));
	}

	/**
	 * Adds new value as a {@link Double} to the shared table
	 *
	 * @see #add(String, String, Double)
	 * @param label the row label of the new data.
	 * @param header the column heading of the new data.
	 * @param value the value of the new data.
	 */
	public static void add(final String label, final String header,
		final double value)
	{
		add(label, header, new Double(value));
	}

	/**
	 * Adds new data to the shared table according to the policy described in
	 * {@link SharedTable}.
	 * <p>
	 * Empty or null labels and headers are ignored.
	 * </p>
	 *
	 * @param label the row label of the new data.
	 * @param header the column heading of the new data.
	 * @param value the value of the new data.
	 */
	public static void add(final String label, final String header,
		final Double value)
	{
		if (StringUtils.isNullOrEmpty(label) || StringUtils.isNullOrEmpty(header)) {
			return;
		}

		final int columns = table.getColumnCount();
		final int columnIndex = headerIndex(header);

		if (columnIndex == columns) {
			appendEmptyColumn(header);
		}
		insertIntoNextFreeRow(label, columnIndex, value);
	}

	/**
	 * Gets a copy of the singleton {@link Table}.
	 * <p>
	 * Returns the same copy instance on every call. However, the contents of the
	 * copy table are always cleared and copied from the actual table. That is, if
	 * you've modified the copy after the previous call, those modifications are
	 * lost.
	 * </p>
	 *
	 * @return the persistent copy instance.
	 */
	public static Table<DefaultColumn<Double>, Double> getTable() {
		if (publicCopy == null) {
			publicCopy = createTable();
		}
		else {
			// Calling publicCopy.clear() would be simpler, but it breaks the tests of
			// the class. However, the tests fail only when run together, individually
			// they pass.
			publicCopy.setRowCount(0);
			publicCopy.setColumnCount(0);
		}
		table.forEach(publicCopy::add);
		// Just calling publicCopy::add is not enough to update size info (ThicknessWrapperTests fail)
		publicCopy.setRowCount(table.getRowCount());
		publicCopy.setColumnCount(table.getColumnCount());
		for (int i = 0; i < table.getRowCount(); i++) {
			publicCopy.setRowHeader(i, table.getRowHeader(i));
		}
		return publicCopy;
	}

	public static boolean hasData() {
		return table.stream().flatMap(Collection::stream).anyMatch(
			Objects::nonNull);
	}

	/** Initializes the table into a new empty table */
	public static void reset() {
		table = createTable();
	}

	// region -- Helper methods --

	private static void appendEmptyColumn(final String header) {
		table.appendColumn(header);
		final int lastColumn = table.getColumnCount() - 1;
		fillEmptyColumn(lastColumn);
	}

	private static void appendEmptyRow(final String label) {
		table.appendRow(label);
		final int lastRow = table.getRowCount() - 1;
		fillEmptyRow(label, lastRow);
	}

	@SuppressWarnings("unchecked")
	private static Table<DefaultColumn<Double>, Double> createTable() {
		return (Table) new DefaultGenericTable();
	}

	private static void fillEmptyColumn(final int columnIndex) {
		final DefaultColumn<Double> column = table.get(columnIndex);
		IntStream.range(0, column.size()).forEach(i -> column.set(i, EMPTY_CELL));
	}

	private static void fillEmptyRow(final String label, final int row) {
		table.setRowHeader(row, label);
		final int columns = table.getColumnCount();
		IntStream.range(0, columns).forEach(column -> table.set(column, row,
			EMPTY_CELL));
	}

	private static int headerIndex(final String header) {
		final int cols = table.getColumnCount();
		return IntStream.range(0, cols).filter(i -> table.get(i).getHeader().equals(
			header)).findFirst().orElse(cols);
	}

	private static void insertIntoNextFreeRow(final String label,
		final int columnIndex, final Double value)
	{
		final int rows = table.getRowCount();
		// iterate up the table from the bottom
		for (int i = rows - 1; i >= 0; i--) {
			// if we find a row with the same label
			if (table.getRowHeader(i).equals(label)) {
				// check whether there is not already a value in columnIndex
				final Double cell = table.get(columnIndex, i);
				if (Objects.equals(cell, EMPTY_CELL)) {
					// add the value to the row and column
					table.set(columnIndex, i, value);
					return;
				}
			}
		}
		// we didn't find the label in the table so make a new row
		appendEmptyRow(label);
		table.set(columnIndex, rows, value);
	}
	// endregion
}
