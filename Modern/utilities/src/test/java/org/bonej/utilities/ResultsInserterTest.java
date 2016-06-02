package org.bonej.utilities;

import ij.measure.ResultsTable;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for the ResultsInserter class
 *
 * @author Richard Domander
 */
public class ResultsInserterTest {
    private static ResultsInserter resultsInserter;
    private static final String emptyString = "";
    private static final String LABEL = "title";
    private static final String MEASUREMENT_HEADING = "mean";
    private static final double MEASUREMENT_VALUE = 13.0;
    private static final String NEW_MEASUREMENT_HEADING = "max";
    private static final double NEW_MEASUREMENT_VALUE = 1000.0;
    private static final double DELTA = 0.000000001;

    private ResultsTable resultsTable;
    private int beforeCount;
    private int afterCount;

    @BeforeClass
    public static void oneTimeSetup() {
        resultsInserter = new ResultsInserter();
    }

    @Before
    public void setUp() {
        resultsTable = resultsInserter.getResultsTable();
        resultsTable.reset();
        beforeCount = 0;
        afterCount = 0;
    }

    @Test(expected = NullPointerException.class)
    public void testSetResultsTableThrowsNullPointerExceptionWhenResultsTableIsNull() throws Exception {
        resultsInserter.setResultsTable(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetImageMeasurementInFirstFreeRowThrowsExceptionIfRowLabelIsNull() throws Exception {
        resultsInserter.setMeasurementInFirstFreeRow(null, "measurementTitle", 1.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetImageMeasurementInFirstFreeRowThrowsExceptionIfRowLabelIsEmpty() throws Exception {
        resultsInserter.setMeasurementInFirstFreeRow(emptyString, "measurementTitle", 1.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetImageMeasurementInFirstFreeRowThrowsExceptionIfMeasurementTitleIsNull() throws Exception {
        resultsInserter.setMeasurementInFirstFreeRow("Label", null, 1.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetImageMeasurementInFirstFreeRowThrowsExceptionIfMeasurementTitleIsEmpty() throws Exception {
        resultsInserter.setMeasurementInFirstFreeRow("Label", emptyString, 1.0);
    }

    @Test
    public void testSetMeasurementInFirstFreeRowAddsARowForNewLabel() throws Exception {
        final int COLUMN = 0;

        beforeCount = resultsTable.getCounter();
        resultsInserter.setMeasurementInFirstFreeRow(LABEL, MEASUREMENT_HEADING, MEASUREMENT_VALUE);
        afterCount = resultsTable.getCounter();

        assertEquals(0, beforeCount);
        assertEquals("Results table should have one more row after insertion", 1, afterCount);

        int lastRow = resultsTable.getCounter() - 1;
        String label = resultsTable.getLabel(lastRow);
        assertEquals("The new row has the wrong label", LABEL, label);

        String measurementHeading = resultsTable.getColumnHeading(COLUMN);
        assertEquals("The new column has the wrong heading", MEASUREMENT_HEADING, measurementHeading);

        double measurementValue = resultsTable.getValueAsDouble(COLUMN, lastRow);
        assertEquals("The new column has the wrong value", MEASUREMENT_VALUE, measurementValue, DELTA);
    }

    @Test
    public void testSetMeasurementInFirstFreeRowAddsARowForRepeatMeasurement() throws Exception {
        beforeCount = resultsTable.getCounter();
        resultsInserter.setMeasurementInFirstFreeRow(LABEL, MEASUREMENT_HEADING, MEASUREMENT_VALUE);
        resultsInserter.setMeasurementInFirstFreeRow(LABEL, MEASUREMENT_HEADING, MEASUREMENT_VALUE);
        afterCount = resultsTable.getCounter();

        assertEquals(0, beforeCount);
        assertEquals("ResultsInserter should add a new row for a repeat measurement with the same label", 2,
                     afterCount);
    }

    @Test
    public void testSetMeasurementInFirstFreeRowAddsAColumnForNewMeasurement() throws Exception {
        beforeCount = resultsTable.getCounter();
        resultsInserter.setMeasurementInFirstFreeRow(LABEL, MEASUREMENT_HEADING, MEASUREMENT_VALUE);
        resultsInserter.setMeasurementInFirstFreeRow(LABEL, NEW_MEASUREMENT_HEADING, NEW_MEASUREMENT_VALUE);
        afterCount = resultsTable.getCounter();

        assertEquals("Adding multiple measures for the same label should only create one row", beforeCount + 1,
                     afterCount);
    }

    @Test
    public void testSetMeasurementInFirstFreeRowAddsColumnToTheFirstRowWithTheSameLabel() throws Exception {
        resultsInserter.setMeasurementInFirstFreeRow("Another label", MEASUREMENT_HEADING, MEASUREMENT_VALUE);
        resultsInserter.setMeasurementInFirstFreeRow(LABEL, MEASUREMENT_HEADING, MEASUREMENT_VALUE);
        resultsInserter.setMeasurementInFirstFreeRow(LABEL, MEASUREMENT_HEADING, MEASUREMENT_VALUE);
        resultsInserter.setMeasurementInFirstFreeRow(LABEL, NEW_MEASUREMENT_HEADING, NEW_MEASUREMENT_VALUE);

        int lastColumn = resultsTable.getLastColumn();

        assertEquals("The new measurement was inserted on the wrong row", Double.NaN,
                     resultsTable.getValueAsDouble(lastColumn, 2), DELTA);
        assertEquals("The new measurement should have been inserted on the first row with the same label",
                     NEW_MEASUREMENT_VALUE, resultsTable.getValueAsDouble(lastColumn, 1), DELTA);
    }

    @Test
    public void testSetMeasurementInFirstFreeRowAddsColumnValueToTheFirstRowWithNoData() throws Exception {
        resultsInserter.setMeasurementInFirstFreeRow(LABEL, MEASUREMENT_HEADING, MEASUREMENT_VALUE);
        resultsInserter.setMeasurementInFirstFreeRow(LABEL, MEASUREMENT_HEADING, MEASUREMENT_VALUE);
        resultsInserter.setMeasurementInFirstFreeRow(LABEL, MEASUREMENT_HEADING, MEASUREMENT_VALUE);
        resultsInserter.setMeasurementInFirstFreeRow(LABEL, NEW_MEASUREMENT_HEADING, MEASUREMENT_VALUE);
        resultsInserter.setMeasurementInFirstFreeRow(LABEL, NEW_MEASUREMENT_HEADING, NEW_MEASUREMENT_VALUE);

        assertEquals(3, resultsTable.getCounter());
        assertEquals("The new value should be inserted on the first row with no data", NEW_MEASUREMENT_VALUE,
                     resultsTable.getValueAsDouble(1, 1), DELTA);
        assertEquals("The new value was inserted on the wrong row", Double.NaN, resultsTable.getValueAsDouble(1, 2),
                     0.00000001);
    }

    @Test
    public void testSetMeasurementInFirstFreeRowNaNMeasurementIsMarkedAsNegInfinity() throws Exception {
        resultsInserter.setMeasurementInFirstFreeRow(LABEL, MEASUREMENT_HEADING, Double.NaN);

        final double value = resultsTable.getValueAsDouble(0, 0);

        assertEquals("NaN value not marked down as negative infinity", Double.NEGATIVE_INFINITY, value, 1e-12);
    }
}