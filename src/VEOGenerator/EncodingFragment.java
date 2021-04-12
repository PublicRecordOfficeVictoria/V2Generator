package VEOGenerator;

import VERSCommon.VEOError;
import java.io.File;

/**
 * *************************************************************
 *
 * E N C O D I N G F R A G M E N T
 *
 * This class is a dynamic fragment that represents a whole encoding. When
 * passed a file to be included as an encoding, it constructs the whole encoding
 * (including the textual descriptions).
 *
 * Andrew Waugh (andrew.waugh@dvc.vic.gov.au) Copyright 2006 PROV
 *
 * v1.1 20090608 Changed handling of case where a dot file extension was not
 * present
 *
 *************************************************************
 */

/**
 * This fragment represents dynamic content that is replaced by the contents of
 * a vers:Encoding element.
 */
public class EncodingFragment extends Fragment {

    int column; 		// the column to obtain file to form encoding

    /**
     * Constructor
     *
     * @param location location of the substitution that generated the fragment
     * @param column the column to obtain the value to finalise this fragment.
     */
    public EncodingFragment(String location, int column) {
        super(location);
        this.column = column;
    }

    /**
     * Extract the file name to include as an encoding from the specified column
     * of the DataSource, generate the vers:Encoding element, and output it to
     * the VEO.
     * <p>
     * When calling finalise() for an EncodingFragment, there is a standard
     * usage for the columns in the DataSource:
     * <table>
     * <tr><td>Column</td><td>Value</td></tr>
     * <tr><td>1</td><td>'e'</td></tr>
     * <tr><td>2</td>
     * <td>The file name of the file to include in the encoding as the document
     * data</td></tr>
     * <tr><td>3</td>
     * <td>The revision number for the vers:DocumentData vers:id
     * attribute</td></tr>
     * <tr><td>4</td>
     * <td>The document number for the vers:DocumentData vers:id
     * attribute</td></tr>
     * <tr><td>5</td>
     * <td>The encoding number for the vers:DocumentData vers:id
     * attribute</td></tr>
     * <tr><td>6</td>
     * <td>The file extension for the vers:RenderingKeywords element</td></tr>
     * </table>
     * Note: that if an encoding is being added using the
     * {@link VEOGenerator#addEncoding} method of {@link VEOGenerator} it is
     * only necessary to pass a single colunn.
     *
     * @throws VEOError
     */
    @Override
    public void finalise(DataSource data, VEOGenerator veo)
            throws VEOError {
        String name = "EncodingFragment.finalise(): ";
        File f;
        int i;
        String s, fileType;
        String[] temp;
        Fragment t;

        // get data
        if (data.getNoColumns() < column) {
            throw new VEOError(location
                    + "column " + column + " is not available from data source");
        }

        // extract file name of encoding from strings[column] and check that it
        // exists
        s = data.getColumn(column);
        if (s == null) {
            throw new VEOError(location
                    + "failed trying to extract column " + column
                    + " from data source");
        }
        try {
            f = new File(s);
        } catch (NullPointerException npe) {
            throw new VEOError(location + "file name (column) must not be null");
        }
        if (!f.exists()) {
            throw new VEOError(location
                    + "file '" + s + "' does not exist");
        }
        if (!f.isFile()) {
            throw new VEOError(location
                    + "file '" + s + "' is not a normal file");
        }

        // work out type of file
        fileType = null;
        t = null;
        i = f.getName().lastIndexOf('.');
        if (i != -1) {
            fileType = f.getName().substring(i + 1).toLowerCase();
        }

        // if file type found, get encoding template
        if (fileType != null && !fileType.equals("")) {
            t = veo.getEncodingTemplate(fileType);
        }

        // if no file type, or no template, use unknown file type
        if (t == null) {
            t = veo.getEncodingTemplate("unknown");
            if (t == null) {
                throw new VEOError(location
                        + "File type '" + fileType + "' is not known, and "
                        + "cannot find template for unknown file encoding");
            }

            // add file type as last column
            // if file type is not present (i.e. no dot extension), or is
            // empty, use the text ""
            temp = new String[data.getNoColumns() + 1];
            for (i = 0; i < data.getNoColumns(); i++) {
                temp[i] = data.getColumn(i + 1);
            }
            if (fileType != null && !fileType.equals("")) {
                temp[i] = "." + fileType;
            } else {
                temp[i] = "";
            }
            data = new ArrayDataSource(temp);
        }
        t.finalise(data, veo);

        // finalise any trailing fragments (if any)
        if (next != null) {
            next.finalise(data, veo);
        }
    }

    /**
     * Represent this fragment as a string.
     *
     * @return
     */
    @Override
    public String toString() {
        String s;
        s = "Encoding Fragment: column: " + column + "\n";
        if (next != null) {
            s += next.toString();
        }
        return s;
    }
}
