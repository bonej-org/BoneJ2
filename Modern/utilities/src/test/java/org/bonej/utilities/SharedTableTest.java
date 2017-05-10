
package org.bonej.utilities;

import static org.bonej.utilities.SharedTable.EMPTY_CELL;
import static org.bonej.utilities.SharedTable.LABEL_HEADER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.imagej.table.DefaultColumn;
import net.imagej.table.Table;

import org.junit.After;
import org.junit.Test;

/**
 * Tests for {@link SharedTable}
 *
 * @author Richard Domander
 */
public class SharedTableTest {

	@After
	public void tearDown() {
		SharedTable.reset();
	}

	@Test
	public void testSharedTableIgnoresNullLabel() throws Exception {
		// EXECUTE
		SharedTable.add(null, "Header", 1.0);

		// VERIFY
		final Table<DefaultColumn<String>, String> table = SharedTable.getTable();
		assertEquals(1, table.getColumnCount());
		assertEquals(0, table.getRowCount());
	}

	@Test
	public void testSharedTableIgnoresEmptyLabel() throws Exception {
		// EXECUTE
		SharedTable.add("", "Header", 1.0);

		// VERIFY
		final Table<DefaultColumn<String>, String> table = SharedTable.getTable();
		assertEquals(1, table.getColumnCount());
		assertEquals(0, table.getRowCount());
	}

	@Test
	public void testSharedTableIgnoresNullHeader() throws Exception {
		// EXECUTE
		SharedTable.add("Label", null, 1.0);

		// VERIFY
		final Table<DefaultColumn<String>, String> table = SharedTable.getTable();
		assertEquals(1, table.getColumnCount());
		assertEquals(0, table.getRowCount());
	}

	@Test
	public void testSharedTableIgnoresEmptyHeader() throws Exception {
		// EXECUTE
		SharedTable.add("Label", "", 1.0);

		// VERIFY
		final Table<DefaultColumn<String>, String> table = SharedTable.getTable();
		assertEquals(1, table.getColumnCount());
		assertEquals(0, table.getRowCount());
	}

	@Test
	public void testAddNewLabel() throws Exception {
		// SETUP
		final String label = "Image";
		final String header = "Pixels";
		final Double value = 1.0;

		// EXECUTE
		SharedTable.add(label, header, value);

		// VERIFY
		final Table<DefaultColumn<String>, String> table = SharedTable.getTable();
		assertEquals(1, table.getRowCount());
		assertEquals(2, table.getColumnCount());
		assertEquals(LABEL_HEADER, table.get(0).getHeader());
		assertEquals(label, table.get(LABEL_HEADER).get(0));
		assertEquals(header, table.get(1).getHeader());
		assertEquals(String.valueOf(value), table.get(header).get(0));
	}

	@Test
	public void testAddLabelRepeatedly() throws Exception {
		// SETUP
		final String label = "Image";
		final String header = "Pixels";

		// EXECUTE
		SharedTable.add(label, header, 2.0);
		SharedTable.add(label, header, 3.0);
		SharedTable.add(label, header, 1.0);

		// VERIFY
		final Table<DefaultColumn<String>, String> table = SharedTable.getTable();
		assertEquals(3, table.getRowCount());
	}

	@Test
	public void testAddMultipleColumns() throws Exception {
		// SETUP
		final String label = "Image";
		final String header = "White pixels";
		final String header2 = "Black pixels";

		// EXECUTE
		SharedTable.add(label, header, 1.0);
		SharedTable.add(label, header2, 3.0);

		// VERIFY
		final Table<DefaultColumn<String>, String> table = SharedTable.getTable();
		assertEquals(1, table.getRowCount());
		assertEquals(3, table.getColumnCount());

	}

	@Test
	public void testAddRowsInsertedAlphabetically() throws Exception {
		// SETUP
		final String label = "ZZZ";
		final String label2 = "AAA";
		final String label3 = "BBB";

		// EXECUTE
		SharedTable.add(label, "Header", 3.0);
		SharedTable.add(label2, "Header", 1.0);
		SharedTable.add(label3, "Header", 2.0);
		SharedTable.add(label, "Header 2", 4.0);

		// VERIFY
		final Table<DefaultColumn<String>, String> table = SharedTable.getTable();
		assertEquals(3, table.getRowCount());
		assertEquals(label2, table.get(0, 0));
		assertEquals(String.valueOf(1.0), table.get(1, 0));
		assertEquals(label3, table.get(0, 1));
		assertEquals(String.valueOf(2.0), table.get(1, 1));
		assertEquals(label, table.get(0, 2));
		assertEquals(String.valueOf(3.0), table.get(1, 2));
	}

	/*
	 * Test that the table is filled in the expected order:
	 * Label    Measurement A   Measurement B
	 * Image A            1.0             2.0
	 * Image A            1.0               -
	 * Image B              -             2.0
	 */
	@Test
	public void testAdd() throws Exception {
		// SETUP
		final String label = "Image A";
		final String label2 = "Image B";
		final String header = "Measurement A";
		final String header2 = "Measurement B";

		// EXECUTE
		SharedTable.add(label2, header2, 2.0);
		SharedTable.add(label, header, 1.0);
		SharedTable.add(label, header, 1.0);
		SharedTable.add(label, header2, 2.0);

		// VERIFY
		final Table<DefaultColumn<String>, String> table = SharedTable.getTable();
		assertEquals("Wrong number of rows", 3, table.size());
		final DefaultColumn<String> columnA = table.get(header);
		assertEquals("Cell should be empty", EMPTY_CELL, columnA.get(2));
		assertEquals("Wrong number of empty cells", 1, columnA.stream().filter(
			s -> s.equals(EMPTY_CELL)).count());
		final DefaultColumn<String> columnB = table.get(header2);
		assertEquals("Cell should be empty", EMPTY_CELL, columnB.get(1));
		assertEquals("Wrong number of empty cells", 1, columnB.stream().filter(
			s -> s.equals(EMPTY_CELL)).count());
		assertEquals("Label on the wrong row", label2, table.get(LABEL_HEADER).get(
			2));
	}

	@Test
	public void testHasDataEmptyTable() {
		assertFalse(SharedTable.hasData());
	}

	@Test
	public void testHasData() {
		SharedTable.add("Label", "Header", 0.0);

		assertTrue(SharedTable.hasData());
	}
}
