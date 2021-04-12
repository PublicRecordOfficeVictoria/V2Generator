package VEOGenerator;

import VERSCommon.VEOError;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * *************************************************************
 *
 * F I L E F R A G M E N T
 *
 * This class represents a file, that is the content is held externally in a
 * file. The contents of the file will be copied into the VEO when the VEO is
 * constructed.
 *
 * Andrew Waugh (andrew.waugh@dvc.vic.gov.au) Copyright 2006 PROV
 *
 *************************************************************
 */

/**
 * This class represents dynamic content that is obtained from a file on the
 * file system. This content is copied into the VEO, possibly with some
 * processing.
 * <p>
 * Three types of content can be handled: binary, UTF8, and XML-UTF8. A binary
 * file contains arbitrary binary content. To be included in a VEO, the data is
 * Base64 encoded, and then the characters are encoded in UTF-8. A UTF-8 file
 * contains text already encoded as UTF-8. This is copied byte for byte into the
 * VEO, except that the special XML characters are escaped. These characters are
 * '&lt;', '&gt;', and '&amp;'. An XML-UTF8 file contains an XML document (i.e.
 * any '&lt;', '&gt;', or '&amp;' characters are already escaped. The contents
 * of this file are copied byte for byte to the VEO.
 * <p>
 * The type of the content is specified when the fragment is created.
 * <p>
 * The file name to be included is obtained from a column in the DataSource when
 * this fragment is finalised.
 *
 * @author Andrew Waugh, PROV
 * @version 1.0
 */
public class FileFragment extends Fragment {

    int type;	// type of contents of this file
    int column;	// column to get file name from
    /**
     * The contents of the file is binary and must be Base64 encoded.
     */
    public static int TYPE_BINARY = 0;
    /**
     * The contents of the file is already encoded as UTF-8, but the XML special
     * characters must be encoded.
     */
    public static int TYPE_UTF8 = 1;
    /**
     * The contents of the file is already XML encoded in UTF-8.
     */
    public static int TYPE_XML_UTF8 = 2;

    /**
     * Constructs a new file fragment.
     *
     * @param location location of the substitution that generated this fragment
     * @param column	The DataSource column from which to obtain the file name to
     * include.
     * @param type	The type of the content (binary, UTF-8, XML-UTF-8).
     */
    public FileFragment(String location, int column, int type) {
        super(location);
        this.column = column;
        this.type = type;
    }

    /**
     * Extract the filename to be included from the specified column from the
     * DataSource and copy the contents to the VEO, possibly encoding the
     * contents on the way.
     * <p>
     * To speed up processing all characters are actually handled as bytes. This
     * uses a trick in that UTF-8 has the special property that any byte in a
     * UTF-8 file with a value 0x00 to 0x7f is the same character as ASCII.
     * Since all the characters in a base64 encoding, and the special characters
     * in UTF8, are actually ASCII characters, the byte values are easily
     * identifable.
     *
     * @param data	The DataSource from which the file name is to be obtained.
     * @param veo	The VEOGenerator representing the constructed veo.
     * @throws VEOError
     */
    @Override
    public void finalise(DataSource data, VEOGenerator veo)
            throws VEOError {
        String name = "FileFragment.finalise(): ";
        String s;
        File file;
        int c, i;
        B64 b64;
        FileInputStream fis;
        BufferedInputStream bis;
        byte bin[], bout[];
        byte lessthan[] = {0x26, 0x6c, 0x74, 0x3b};
        byte greaterthan[] = {0x26, 0x67, 0x74, 0x3b};
        byte ampersand[] = {0x26, 0x61, 0x6d, 0x70, 0x3b};
        byte lf[] = {0x0a, 0x0d};

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

        // get information about the file to include
        file = new File(s);
        if (!file.exists()) {
            throw new VEOError(location
                    + "file '" + s + "' does not exist");
        }
        if (!file.isFile()) {
            throw new VEOError(location
                    + "file '" + s + "' is not a normal file");
        }

        // open input file as a buffered binary file
        try {
            fis = new FileInputStream(file);
        } catch (FileNotFoundException fnfe) {
            throw new VEOError(location
                    + " file '" + s + "' not found");
        }
        bis = new BufferedInputStream(fis);

        // if type is binary
        try {
            if (type == TYPE_BINARY) {
                b64 = new B64();

                // for each byte in source
                b64.toBase64(bis, veo);

                // if type is utf8
            } else if (type == TYPE_UTF8) {
                bin = new byte[1];

                // for each byte in source
                while (bis.read(bin) != -1) {
                    // if special character replace by escaped version
                    switch (bin[0]) {
                        // i.e. ascii '<'
                        case 0x3c:
                            veo.outputDataToVeo(lessthan);
                            break;
                        // i.e. ascii '>'
                        case 0x3e:
                            veo.outputDataToVeo(greaterthan);
                            break;
                        // i.e. ascii '&'
                        case 0x26:
                            veo.outputDataToVeo(ampersand);
                            break;
                        default:
                            veo.outputDataToVeo(bin);
                            break;
                    }
                }

                // if type is xml-utf8
            } else if (type == TYPE_XML_UTF8) {
                // for each byte in source write to VEO and signatures
                bin = new byte[1];
                while (bis.read(bin) != -1) {
                    veo.outputDataToVeo(bin);
                }

            }
        } catch (IOException ioe) {
            throw new VEOError(location + "Error reading input file: "
                    + ioe.getMessage());
        }

        try {
            bis.close();
            fis.close();
        } catch (IOException ioe) {
            /* ignore */ }

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
        s = "File Fragment: column: " + column + ", type: " + type + "\n";
        if (next != null) {
            s += next.toString();
        }
        return s;
    }
}
