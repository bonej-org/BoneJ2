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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Objects;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.scijava.table.DefaultColumn;
import org.scijava.table.Table;

/**
 * Tests for {@link SharedTable}
 *
 * @author Richard Domander
 * @author Michael Doube
 */
public class SharedTableTest {

	@Rule
	public ExpectedException expectedException = ExpectedException.none();

	@After
	public void tearDown() {
		SharedTable.reset();
	}

	/*
	 * Test that the table is filled in the expected order:
	 *          Measurement 1   Measurement 2
	 * Image B            -               2.0
	 * Image A            1.0             3.0
	 * Image A            1.0             2.0
	 */
	@Test
	public void testAdd() {
		// SETUP
		final String labelA = "Image A";
		final String labelB = "Image B";
		final String header1 = "Measurement 1";
		final String header2 = "Measurement 2";

		// EXECUTE
		SharedTable.add(labelB, header2, 2.0);
		SharedTable.add(labelA, header1, 1.0);
		SharedTable.add(labelA, header1, 1.0);
		SharedTable.add(labelA, header2, 2.0);
		SharedTable.add(labelA, header2, 3.0);

		// VERIFY
		final Table<DefaultColumn<Double>, Double> table = SharedTable.getTable();
		assertEquals("Wrong number of columns", 2, table.getColumnCount());
		assertEquals("Wrong number of rows", 3, table.getRowCount());
		final DefaultColumn<Double> column1 = table.get(header1);
		assertEquals("Cell should be empty", EMPTY_CELL, column1.get(0));
		assertEquals("Wrong number of empty cells", 1, column1.stream().filter(
			Objects::isNull).count());
		final DefaultColumn<Double> column2 = table.get(header2);
		assertEquals("Cell contains wrong value", 3.0, column2.get(1), 1e-12);
		assertEquals("Wrong number of empty cells", 0, column2.stream().filter(
				Objects::isNull).count());
		assertEquals("Label on the wrong row", 0, table.getRowIndex(labelB));
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
		final Table<DefaultColumn<Double>, Double> table = SharedTable.getTable();
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
		final Table<DefaultColumn<Double>, Double> table = SharedTable.getTable();
		assertEquals(1, table.getRowCount());
		assertEquals(2, table.getColumnCount());

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
		final Table<DefaultColumn<Double>, Double> table = SharedTable.getTable();
		assertEquals(1, table.getRowCount());
		assertEquals(1, table.getColumnCount());
		assertEquals(header, table.get(0).getHeader());
		assertEquals(label, table.getRowHeader(0));
		assertEquals(1.0, table.get(header).get(0), 1e-12);
	}

	@Test
	public void testAddThrowsIAEIfEmptyLabel() {
		expectedException.expect(IllegalArgumentException.class);
		expectedException.expectMessage("Label cannot be empty");
		SharedTable.add("", "Header", 1.0);
	}

	@Test
	public void testAddThrowsIAEIfEmptyHeader() {
		expectedException.expect(IllegalArgumentException.class);
		expectedException.expectMessage("Header cannot be empty");
		SharedTable.add("Label", "", 1.0);
	}

	@Test(expected = NullPointerException.class)
	public void testAddThrowsNPEIfNullLabel() {
		SharedTable.add(null, "Header", 1.0);
	}

	@Test(expected = NullPointerException.class)
	public void testAddThrowsNPEIfNullHeader() {
		SharedTable.add("Label", null, 1.0);
	}

	@Test(expected = NullPointerException.class)
	public void testAddThrowsNPEIfNullValue() {
		SharedTable.add("Label", "Header", null);
	}

	@Test
	public void testGetTableCopyDataCleared() {
		final Table<DefaultColumn<Double>, Double> copy = SharedTable.getTable();
		copy.appendRow();
		copy.appendColumn();
		copy.set(0, 0, 13.0);
		final Table<DefaultColumn<Double>, Double> copy2 = SharedTable.getTable();

		assertEquals(0, copy2.size());
		assertEquals(0, copy2.getColumnCount());
		assertEquals(0, copy2.getRowCount());
	}

	@Test
	public void testGetTableCopyPersists() {
		final Table instance1 = SharedTable.getTable();
		final Table instance2 = SharedTable.getTable();

		assertSame(instance1, instance2);
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
}
