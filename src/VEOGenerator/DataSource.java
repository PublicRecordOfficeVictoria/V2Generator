package VEOGenerator;

/**
 * *************************************************************
 *
 * D A T A S O U R C E
 *
 * This class represents a data source.
 *
 * Andrew Waugh (andrew.waugh@dvc.vic.gov.au) Copyright 2006 PROV
 *
 *************************************************************
 */
/**
 * This class represents an abstraction of a source of data for constructing
 * VEOs.
 * <p>
 * The abstraction is that the data source is a table. Each row represents the
 * data associated with record or file metadata, document metadata, or an
 * encoding. The rows are divided into columns.
 * <p>
 * Each row may indicate the type of metadata that it contains. By convention,
 * this is signalled by the contents of the first column in a row:
 * <ul>
 * <li>'r': Data for a vers:RecordMetadata element</li>
 * <li>'f': Data for a vers:FileMetadata element</li>
 * <li>'d': Data for a vers:DocumentMetadata element</li>
 * <li>'e': Data for a vers:EncodingMetadata element</li>
 * <li>'s': Data for a simple record (i.e. the template contains all the
 * information necessary to build the record metadata, document, and encoding
 * </li>
 * </ul>
 * The type of the row is returned when it is read, and can also be queried by
 * the method {@link #getRowType}.
 * <p>
 * The end of a data source is indicated by the return of the value DS_AtEnd,
 * and can also be tested using the method {@link #isAtEnd}
 * <p>
 * The remaining columns can contain any data, and there is no requirement that
 * each row have the same number of columns. The number of columns in the
 * current row can be obtained using {@link #getNoColumns}.
 */
abstract public class DataSource {

    /**
     * Type of row (that is, DS_Record, DS_File, DS_Document or DS_Encoding).
     * This can only be accessed by subclasses of DataSource. Methods that use
     * DataSource (or a subclass) must use {@link #getRowType}.
     */
    protected int rowType;
    /**
     * Type of metadata not indicated.
     */
    public static int DS_Unknown = 0;
    /**
     * Row contains data for file metadata.
     */
    public static int DS_File = 1;
    /**
     * Row contains data for record metadata.
     */
    public static int DS_Record = 2;
    /**
     * Row contains data for document metadata.
     */
    public static int DS_Document = 3;
    /**
     * Row contains data for an encoding.
     */
    public static int DS_Encoding = 4;
    /**
     * Row contains data for a simple record
     */
    public static int DS_SimpleRecord = 5;
    /**
     * At end of data source; there are no more valid rows.
     */
    public static int DS_AtEnd = -1;
    /**
     * True when at the end of the data source and there are no more valid rows.
     * This can only be used by subclasses of DataSource. Methods that use
     * DataSource (or a subclass) must use {@link #isAtEnd}.
     */
    protected boolean atEnd;
    /**
     * The columns of data in the current row. This can only be used by
     * subclasses of DataSource. Methods that use DataSource (or a subclass)
     * must use {@link #getNoColumns}.
     */
    protected String[] column;

    /**
     * Default constructor
     */
    public DataSource() {
        atEnd = true;
        rowType = DS_AtEnd;
        column = null;
    }

    /**
     * Construct a record Metadata DataSource from an array of Strings.
     *
     * @param column
     */
    public DataSource(String[] column) {
        atEnd = false;
        rowType = DS_Record;
        this.column = column;
    }

    /**
     * Return true if this data source implements the methods to return document
     * metadata or encoding rows.
     *
     * @return true if this record will return a document or encoding
     */
    public boolean isRecord() {
        return false;
    }

    /**
     * Return the type of the current row (DS_File, DS_Record, DS_Document,
     * DS_Encoding, or DS_AtEnd if at the end of the document source).
     *
     * @return type of row.
     */
    public int getRowType() {
        return rowType;
    }

    /**
     * Returns true if at the end of the data source.
     *
     * @return true if there are no further rows in this data source.
     */
    public boolean isAtEnd() {
        return atEnd;
    }

    /**
     * Move to the next row. The type of the new row is returned, or DS_AtEnd if
     * there are no more rows.
     *
     * @return type of the current row (DS_AtEnd if there are no more rows in
     * data source).
     */
    abstract public int getNextRow();

    /**
     * This method gets the number of columns in the current row. There is no
     * requirement that each row contains the same number of columns.
     *
     * @return the number of columns in this row.
     */
    public int getNoColumns() {
        return column.length;
    }

    /**
     * Get the contents of the requested column as a string. The first column is
     * column 1. Return the empty string ("") if the requested column is less
     * than 1, or greater than the number of columns in the row.
     *
     * @param i number of column to return
     * @return the string value of the requested column
     */
    public String getColumn(int i) {
        if (atEnd || i < 1 || column.length < i) {
            return "";
        }
        return column[i - 1];
    }

    /**
     * Escape all the XML special characters in a String. XML requires the
     * following characters to be escaped in character data: '&' to '&amp;',
     * '<' to '&lt;' and '>' to 'gt;'. This routine is used by sub-classes.
     *
     * @param s the string to convert
     * @return the converted string
     */
    protected String translateSpecialCharacters(String s) {
        s = s.replaceAll("&", "&amp;");
        s = s.replaceAll("<", "&lt;");
        s = s.replaceAll(">", "&gt;");
        return s;
    }

    /**
     * Return a string representation of the DataSource.
     *
     * @return a String representing the current row.
     */
    @Override
    public String toString() {
        String s;
        int i;

        s = "Column data\n";
        for (i = 0; i < column.length; i++) {
            s += i + ": '" + column[i] + "'\n";
        }
        return s;
    }
}
