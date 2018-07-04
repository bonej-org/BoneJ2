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

	/*
	 * Test that the table is filled in the expected order:
	 * Label    Measurement A   Measurement B
	 * Image A            1.0             2.0
	 * Image A            1.0               -
	 * Image B              -             2.0
	 */
	@Test
	public void testAdd() {
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
	public void testAddLabelRepeatedly() {
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
	public void testAddMultipleColumns() {
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
	public void testAddNewLabel() {
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
	public void testAddRowsInsertedAlphabetically() {
		// SETUP
		final String label = "ZZZ";
		final String label2 = "AAA";
		final String label3 = "BBB";

		// EXECUTE
		SharedTable.add(label, "Header", 3.0);
		SharedTable.add(label2, "Header", 1.0);
		SharedTable.add(label3, "Header", 2.0);
		SharedTable.add(label, "Header", 4.0);
		SharedTable.add(label, "Header 2", 4.0);

		// VERIFY
		final Table<DefaultColumn<String>, String> table = SharedTable.getTable();
		assertEquals(4, table.getRowCount());
		assertEquals(label2, table.get(0, 0));
		assertEquals(String.valueOf(1.0), table.get(1, 0));
		assertEquals(label3, table.get(0, 1));
		assertEquals(String.valueOf(2.0), table.get(1, 1));
		assertEquals(label, table.get(0, 2));
		assertEquals(String.valueOf(3.0), table.get(1, 2));
		assertEquals(String.valueOf(4.0), table.get(2, 2));
		assertEquals(label, table.get(0, 3));
		assertEquals(String.valueOf(4.0), table.get(1, 3));
	}

	@Test
	public void testRepeatingHeaderAndLabelAddsARow() {
		SharedTable.add("Image", "Value", 1.0);
		SharedTable.add("Image", "Run", 1);
		SharedTable.add("Image", "Value", 1.0);
		SharedTable.add("Image", "Run", 2);

		final Table<DefaultColumn<String>, String> table = SharedTable.getTable();
		assertEquals(
			"Adding data to the same column, to the row with the same label, should create a new row",
			2, table.getRowCount());
		assertEquals("Values in wrong order, older should be first", "1", table.get(
			"Run").get(0));
		assertEquals("Values in wrong order, older should be first", "2", table.get(
			"Run").get(1));
	}

	@Test
	public void testHasData() {
		SharedTable.add("Label", "Header", 0.0);

		assertTrue(SharedTable.hasData());
	}

	@Test
	public void testHasDataEmptyTable() {
		assertFalse(SharedTable.hasData());
	}

	@Test
	public void testSharedTableIgnoresEmptyHeader() {
		// EXECUTE
		SharedTable.add("Label", "", 1.0);

		// VERIFY
		final Table<DefaultColumn<String>, String> table = SharedTable.getTable();
		assertEquals(1, table.getColumnCount());
		assertEquals(0, table.getRowCount());
	}

	@Test
	public void testSharedTableIgnoresEmptyLabel() {
		// EXECUTE
		SharedTable.add("", "Header", 1.0);

		// VERIFY
		final Table<DefaultColumn<String>, String> table = SharedTable.getTable();
		assertEquals(1, table.getColumnCount());
		assertEquals(0, table.getRowCount());
	}

	@Test
	public void testSharedTableIgnoresNullHeader() {
		// EXECUTE
		SharedTable.add("Label", null, 1.0);

		// VERIFY
		final Table<DefaultColumn<String>, String> table = SharedTable.getTable();
		assertEquals(1, table.getColumnCount());
		assertEquals(0, table.getRowCount());
	}

	@Test
	public void testSharedTableIgnoresNullLabel() {
		// EXECUTE
		SharedTable.add(null, "Header", 1.0);

		// VERIFY
		final Table<DefaultColumn<String>, String> table = SharedTable.getTable();
		assertEquals(1, table.getColumnCount());
		assertEquals(0, table.getRowCount());
	}
}
