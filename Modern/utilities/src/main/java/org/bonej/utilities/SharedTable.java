
package org.bonej.utilities;

import java.util.Collection;
import java.util.stream.IntStream;

import net.imagej.table.DefaultColumn;
import net.imagej.table.DefaultGenericTable;
import net.imagej.table.Table;

/**
 * Stores a {@link Table}, which is ordered according to the following rules:
 * <ol>
 * <li>Each row starts with "Label" column. The labels identify the rows, and
 * tell you which image was measured, e.g. "bat-cochlea-volume.tif"</li>
 * <li>Each column has a header, which tells you the type of the measurement,
 * e.g. "Volume"</li>
 * <li>If there are no rows with the given label, then add a new row</li>
 * <li>If there are rows with the given label, but there is not a column with
 * the given heading, then add a column, and set its value on the first row with
 * the label.</li>
 * <li>If there are rows with the given label, and there's a column with the
 * given heading, then find the first empty cell (equals {@link #EMPTY_CELL}),
 * and add the new value there. If there are no empty cells, then add a new
 * row.</li>
 * <li>Labels and columns are kept in alphabetical order</li>
 * <li>If there are multiple rows with the same labels & non-empty cells, the
 * last inserted value comes last</li>
 * </ol>
 *
 * @author Richard Domander
 */
public class SharedTable {

	public static final String LABEL_HEADER = "Label";
	public static final String EMPTY_CELL = "";

	private static Table<DefaultColumn<String>, String> table = createTable();

	private SharedTable() {}

	/** Returns the shared table instance */
	public static Table<DefaultColumn<String>, String> getTable() {
		// TODO Return a clone of the table?
		return table;
	}

	public static boolean hasData() {
		return table.stream().flatMap(Collection::stream).anyMatch(s -> s != null &&
			!EMPTY_CELL.equals(s));
	}

	/**
	 * Adds new value as a {@link String} to the shared table
	 * 
	 * @see #add(String, String, String)
	 */
	public static void add(final String label, final String header,
		final long value)
	{
		add(label, header, String.valueOf(value));
	}

	/**
	 * Adds new value as a {@link String} to the shared table
	 * 
	 * @see #add(String, String, String)
	 */
	public static void add(final String label, final String header,
		final double value)
	{
		add(label, header, String.valueOf(value));
	}

	/**
	 * Adds new data to the shared table according to the policy described in
	 * {@link SharedTable}
	 * <p>
	 * Empty or null labels and headers are ignored
	 * </p>
	 *
	 * @param label The row label of the new data
	 * @param header The column heading of the new data
	 * @param value The value of the new data
	 */
	public static void add(final String label, final String header,
		final String value)
	{
		// TODO Replace with StringUtils.isNullOrEmpty
		if (label == null || label.isEmpty()) {
			return;
		}
		if (header == null || header.isEmpty()) {
			return;
		}

		final int columns = table.getColumnCount();
		final int columnIndex = alphabeticalHeaderIndex(header);

		if (columnIndex == columns) {
			appendEmptyColumn(header);
		}
		else if (!table.get(columnIndex).getHeader().equals(header)) {
			insertEmptyColumn(columnIndex, header);
		}

		insertIntoNextFreeRow(label, columnIndex, value);
	}

	/** Initializes the table into a new empty table */
	public static void reset() {
        table = createTable();
	}

	// region -- Helper methods --

	@SuppressWarnings("unchecked")
	private static Table<DefaultColumn<String>, String> createTable() {
		final Table newTable = new DefaultGenericTable();
		newTable.appendColumn(LABEL_HEADER);
		return newTable;
	}

	private static void appendEmptyColumn(final String header) {
		table.appendColumn(header);
		final int lastColumn = table.getColumnCount() - 1;
		fillEmptyColumn(lastColumn);
	}

	private static void insertEmptyColumn(final int column, final String header) {
		table.insertColumn(column, header);
		fillEmptyColumn(column);
	}

	private static void fillEmptyColumn(final int columnIndex) {
		final DefaultColumn<String> column = table.get(columnIndex);
		IntStream.range(0, column.size()).forEach(i -> column.set(i, EMPTY_CELL));
	}

	private static void insertEmptyRow(final String label, final int rowIndex) {
		table.insertRow(rowIndex);
		fillEmptyRow(label, rowIndex);
	}

	private static void fillEmptyRow(final String label, final int row) {
		table.get(LABEL_HEADER).set(row, label);
		final int columns = table.getColumnCount();
		IntStream.range(1, columns).forEach(column -> table.set(column, row,
			EMPTY_CELL));
	}

	private static int alphabeticalHeaderIndex(final String header) {
		final int cols = table.getColumnCount();
		return IntStream.range(1, cols).filter(i -> table.get(i).getHeader().equals(
			header)).findFirst().orElse(cols);
	}

	private static void insertIntoNextFreeRow(final String label,
		final int columnIndex, final String value)
	{
		final int rows = table.getRowCount();
		final DefaultColumn<String> labelColumn = table.get(LABEL_HEADER);
		int alphabeticalIndex = IntStream.range(0, rows).filter(i -> labelColumn
			.get(i).compareTo(label) >= 0).findFirst().orElse(rows);
		while (alphabeticalIndex < rows && labelColumn.get(alphabeticalIndex)
			.equals(label))
		{
			if (EMPTY_CELL.equals(table.get(columnIndex).get(alphabeticalIndex))) {
				table.set(columnIndex, alphabeticalIndex, value);
				return;
			}
			alphabeticalIndex++;
		}
		insertEmptyRow(label, alphabeticalIndex);
		table.set(columnIndex, alphabeticalIndex, value);
	}
	// endregion
}
