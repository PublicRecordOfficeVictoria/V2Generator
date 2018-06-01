package VEOGenerator;

import java.util.LinkedList;
import java.util.NoSuchElementException;

/**
 * *************************************************************
 *
 * L I S T D A T A S O U R C E
 *
 * This class represents a data source with the data taken from a linked list of
 * Strings.
 *
 * Andrew Waugh (andrew.waugh@prov.vic.gov.au) Copyright 2009 PROV
 *
 *************************************************************
 */

/**
 * This class is a DataSource in which the data is taken from a linked list
 */
public class ListDataSource extends DataSource {

    LinkedList[] data;	// data passed in
    int row;		// current row

    /**
     * Default constructor
     *
     * @param data
     */
    public ListDataSource(LinkedList[] data) {
        super();
        this.data = data;
        row = 0;
    }

    /**
     * Return true if this data source implements the methods to return document
     * metadata or encoding rows.
     *
     * @return true if this record will return a document or encoding
     */
    @Override
    public boolean isRecord() {
        return true;
    }

    /**
     * Return the type of the current row (DS_File, DS_Record, DS_Document,
     * DS_Encoding, or DS_AtEnd if at the end of the document source).
     *
     * @return type of row.
     */
    @Override
    public int getRowType() {
        String s;

        if (data == null) {
            return DS_AtEnd;
        }
        if (row >= data.length) {
            return DS_AtEnd;
        }
        try {
            s = (String) (data[row].getFirst());
        } catch (NoSuchElementException nsee) {
            return DS_Unknown;
        }
        if (s.equals("f")) {
            return DS_File;
        }
        if (s.equals("r")) {
            return DS_Record;
        }
        if (s.equals("d")) {
            return DS_Document;
        }
        if (s.equals("e")) {
            return DS_Encoding;
        }
        if (s.equals("s")) {
            return DS_SimpleRecord;
        }
        return DS_Unknown;
    }

    /**
     * Returns true if at the end of the data source.
     *
     * @return true if there are no further rows in this data source.
     */
    @Override
    public boolean isAtEnd() {
        if (data == null) {
            return true;
        }
        return row >= data.length;
    }

    /**
     * Move to the next row. The type of the new row is returned, or DS_AtEnd if
     * there are no more rows.
     *
     * @return type of the current row (DS_AtEnd if there are no more rows in
     * data source).
     */
    @Override
    public int getNextRow() {
        row++;
        return getRowType();
    }

    /**
     * This method gets the number of columns in the current row. There is no
     * requirement that each row contains the same number of columns.
     *
     * @return the number of columns in this row.
     */
    @Override
    public int getNoColumns() {
        if (data == null) {
            return 0;
        }
        if (row >= data.length) {
            return 0;
        }
        return data[row].size();
    }

    /**
     * Get the contents of the requested column as a string. The first column is
     * column 1. Return the empty string ("") if the requested column is less
     * than 1, or greater than the number of columns in the row.
     *
     * @param i number of column to return
     * @return the string value of the requested column
     */
    @Override
    public String getColumn(int i) {
        String s;

        if (data == null) {
            return "";
        }
        if (row >= data.length) {
            return "";
        }
        if (i < 1 || i > getNoColumns()) {
            return "";
        }
        try {
            s = (String) data[row].get(i - 1);
        } catch (IndexOutOfBoundsException e) {
            return "";
        }
        return s;
    }

    /**
     * Return a string representation of the DataSource.
     *
     * @return a String representing the current row.
     */
    @Override
    public String toString() {
        StringBuffer sb;
        String s;
        int i, j;

        sb = new StringBuffer();
        if (data == null) {
            return "";
        }
        for (i = 0; i < data.length; i++) {
            sb.append("Row " + i + "\n========\n");
            for (j = 0; j < data[i].size(); j++) {
                try {
                    sb.append(j + ": " + ((String) data[i].get(j)) + "\n");
                } catch (IndexOutOfBoundsException e) {
                    sb.append("\n");
                }
            }
        }
        return sb.toString();
    }
}
