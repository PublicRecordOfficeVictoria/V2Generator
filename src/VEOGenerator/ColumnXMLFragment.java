package VEOGenerator;

import VERSCommon.VEOError;

/**
 * *************************************************************
 *
 * C O L U M N X M L F R A G M E N T
 *
 * This class represents a dynamic fragment that will be finalised with the
 * value from a column. The value of the column is XML - no XML encoding of the
 * characters will be done.
 *
 * Andrew Waugh (andrew.waugh@dvc.vic.gov.au) Copyright 2010 PROV
 *
 *************************************************************
 */
/**
 * This fragment represents dynamic content that is obtained from a column in a
 * DataSource.The value of the column is XML - no XML encoding of the characters
 * will be done.
 * <p>
 * The model of operation is of a table. The rows of the table represent the
 * data associated with individual VEOs, and the columns specific pieces of
 * metatata.
 * <p>
 * This is implemented using a callback mechanism. When this fragment is
 * finalised, the passed method is called with the column being given by an
 * argument. The value of this column in the current row is returned as a
 * string.
 */
public class ColumnXMLFragment extends Fragment {

    int column; // the column to obtain value from

    /**
     * Constructor.
     *
     * @param location the source of the fragment (file/line number)
     * @param column the column to obtain the value to finalise this fragment
     */
    public ColumnXMLFragment(String location, int column) {
        super(location);
        this.column = column;
    }

    /**
     * Extract the specified column from the DataSource and output it to the
     * VEO.
     *
     * @throws VEOError
     */
    @Override
    public void finalise(DataSource data, VEOGenerator veo)
            throws VEOError {
        String s;
        String name = "ColumnFragment.finalise(): ";

        // ask data source for value of specific column
        if (data.getNoColumns() < column) {
            throw new VEOError(location
                    + "column " + column
                    + " is not available from the data source");
        }
        s = data.getColumn(column);
        if (s == null) {
            throw new VEOError(location
                    + " failed trying to extract column " + column
                    + " from data source");
        }

        // output content to VEO
        veo.outputDataToVeo(cs.encode(s));

        // finalise any trailing fragments (if any)
        if (next != null) {
            next.finalise(data, veo);
        }
    }

    /**
     * Outputs this fragment as a string.
     *
     * @return
     */
    @Override
    public String toString() {
        String s;
        s = "Column Fragment: column: " + column + "\n";
        if (next != null) {
            s += next.toString();
        }
        return s;
    }
}
