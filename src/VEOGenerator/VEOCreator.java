package VEOGenerator;

import VERSCommon.PFXUser;
import VERSCommon.VEOError;
import VERSCommon.VEOFatal;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.TimeZone;

/**
 *
 * V E O C R E A T O R
 *
 * This class generates VEOs from template files
 *
 * Andrew Waugh (andrew.waugh@prov.vic.gov.au) Copyright 2006 PROV
 *
 */
/**
 * This class wraps the VEOGenerator class to create a tool that can produce
 * VEOs with no programming.
 * <p>
 * The program generates one or more VEOs under the control of a data file
 * (specified using the -d command line argument) and a set of templates located
 * in the template directory (specified using the -t command line argument). It
 * is also necessary to specify a PFX file containing information about the
 * signer (the -s command line argument). A minimal example of usage is<br>
 * <pre>
 *     veocreator -d data.txt -t templates -s signer.pfx
 * </pre>
 * <p>
 * The following command line arguments are optional:
 * <ul>
 * <li>-p <password> the password for the PFX file (if not present, the program
 * will prompt for the password).</li>
 * <li>-o <outputDir> the directory in which the VEOs are to be created. If not
 * present, the VEOs will be created in the current directory.</li>
 * <li>-v print information about the progress of the program. By default, not
 * set.</li>
 * </ul>
 * <p>
 * The template files required are:
 * <ul>
 * <li>record.txt - the template for the contents of the vers:RecordMetadata
 * element</li>
 * <li>file.txt - the template for the contents of the vers:FileMetadata
 * element</li>
 * <li>document.txt - the template for the contents of the vers:DocumentMetadata
 * element</li>
 * <li>encodingTemplates - a directory containing the templates for the
 * vers:Encoding element</li>
 * </ul>
 * All of these files must exist in the template directory specified by the -t
 * command line argument. The names of the encoding templates follow the format
 * 'pdf.txt' where 'pdf' is the three letter file extension for the type of
 * files described by that template. There must also be a file 'unknown.txt'
 * which is used when an unknown file type is included.
 * <p>
 * The template files contains the <i>contents</i> of the appropriate element.
 * The contents composed of XML text, which will be included explicitly in each
 * VEO, and substitutions. The start of each substitution is marked by '$$' and
 * the end by '$$'. Possible substitutions are:
 * <ul>
 * <li>
 * $$ date $$ - substitute the current date and time in VERS format</li>
 * <li>
 * $$ argument &lt;x&gt; $$ - substitute the contents of command line argument
 * &lt;x&gt;</li>
 * <li>
 * $$ sequenceno $$ - substitute the current sequence number</li>
 * <li>
 * $$ [column] &gt;x&gt; $$ - substitute the contents of column &lt;x&gt;. Note
 * that keyword 'column' is optional.</li>
 * <li>
 * $$ file binary|utf8|xml [column] &lt;x&gt; $$ - include the contents of the
 * file specified in column &lt;x&gt;. The file is encoded depending on the
 * second keyword: a 'binary' file is encoded in Base64; a 'utf8' file has the
 * characters &lt;, &gt;, and &amp; encoded; and an 'xml' file is included as
 * is. Note that keyword 'column' is optional.</li>
 * </ul>
 * <p>
 * The data file contains the information used in the column or file
 * substitutions. A data file consists of one or more rows, each of which
 * consists of one or more columns. A row contains the information for one
 * template. Typically a record VEO will require 3 rows (one row each for the
 * record metadata, the document metadata, and the encoding metadata), while a
 * file VEO will require 1 row (for the file metadata). One data file may
 * contain information about both record and file VEOs.
 * <p>
 * A row in a data file consists of one or more columns. Columns are separated
 * by tabs. The first column contains a single letter indicating the type of
 * row:
 * <ul>
 * <li>f - File metadata row</li>
 * <li>r - Record metadata row</li>
 * <li>d - Document metadata row</li>
 * <li>e - Encoding row</li>
 * <li>s - Simple Record row</li>
 * </ul>
 * <p>
 * The second column of a file or record row contains the name of the VEO to
 * construct (e.g. 'image-10-0101.veo').
 * <p>
 * The second column of an encoding row contains the name (e.g. 'Report.pdf') of
 * the file to be included in the encoding. The remaining pieces of metadata for
 * an encoding are generated automatically from encoding template.
 * <p>
 * A simple record row contains all the information necessary to build a record
 * with one document of one encoding. The second column of a simple record row
 * contains the name of the VEO to construct. (e.g. 'image-10-0101.veo'), and
 * the third column contains the name of the file to be be included in the
 * encoding (e.g. 'Report.pdf'). The remaining columns are used as data for both
 * the record and document metadata.
 * <p>
 * Typical rows are:
 * <pre>
 *    f File-image-10-0101.veo metadata1 metadata2...
 *    r Image-10-0101.veo metadata1 metadata2...
 *    d metadata1 metadata2...
 *    e Image-10-0101.jpg
 *    s Image-10-0102.veo Image-10-0101.jpg metadata1 metadata2...
 * </pre>
 */
public class VEOCreator {

    VEOGenerator vg;// the representation of the VEO
    boolean verbose;// true if verbose output
    File templateDir;// directory in which the templates are found
    boolean noDataFile; // true data file is to be passed when building VEOs
    File dataFile;	// the data file which is to control production
    String hashAlg; // hash algorithm to use
    File pfxFile;	// PFX file containing infor about the signer
    PFXUser signer;	// signer information
    String passwd;	// password for the PFX file
    File outputDir;	// directory in which to place the VEOs
    Fragment rData;	// template for record metadata
    Fragment fData;	// template for file metadata
    Fragment dData;	// template for document metadata
    boolean help;           // true if printing a cheat list of command line options

    private static final String USAGE = "veoCreator [-help] [-v] [-h <hashAlg>] -t <templateDir> [-d <dataFile>| -nd] -s <pfxFile> <password> [-o <outputDir>]";

    /**
     * Report on version...
     *
     * <pre>
     * 2006     1.0 Initial release
     * 20090516 1.1 Generalised BuildVEOs() to take a variety of DataSources
     * 20100604 1.2 Altered buildNewVEO() to call vg.cleanUpAfterError() whenever an error occurs
     * 20180618 1.3 Checked for XML special characters & added (commented out code) to output byte stream
     * 20180909 1.4 Added support for other hash algorithms than SHA-1
     * 20210412 2.0 Added version, and standardised reporting in run. Integrated with VERSCommon (PFXUser, VEOError, VEOFatal)
     * </pre>
     */
    static String version() {
        return ("2.00");
    }

    /**
     * Default constructor. This constructor processes the command line
     * arguments, obtains the location of the templates and parses them, and
     * reads the PFX file to obtain the signers details. If any errors occur, an
     * error message will be printed and the program will terminate.
     *
     * @param args command line arguments
     * @throws VERSCommon.VEOFatal if something went wrong
     */
    public VEOCreator(String args[]) throws VEOFatal {
        SimpleDateFormat sdf;
        TimeZone tz;
        StringBuffer sb;
        int c;
        char ch;

        verbose = false;
        templateDir = null;
        noDataFile = false;
        dataFile = null;
        hashAlg = "SHA-256";
        signer = null;
        passwd = null;
        outputDir = null;
        help = false;

        // process command line arguments
        configure(args);

        // tell what is happening
        System.out.println("******************************************************************************");
        System.out.println("*                                                                            *");
        System.out.println("*                 V E O ( V 2 )   C R E A T I O N   T O O L                  *");
        System.out.println("*                                                                            *");
        System.out.println("*                                Version " + version() + "                                *");
        System.out.println("*               Copyright 2015 Public Record Office Victoria                 *");
        System.out.println("*                                                                            *");
        System.out.println("******************************************************************************");
        System.out.println("");
        System.out.print("Run at ");
        tz = TimeZone.getTimeZone("GMT+10:00");
        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss+10:00");
        sdf.setTimeZone(tz);
        System.out.println(sdf.format(new Date()));
        System.out.println("");
        if (help) {
            // veoCreator [-v] [-h <hashAlg>] -t <templateDir> [-d <dataFile>| -nd] -s <pfxFile> [-p <password>] [-o <outputDir>]
            System.out.println("Command line arguments:");
            System.out.println(" Mandatory:");
            System.out.println("  -t <directory>: file path to where the templates are located");
            System.out.println("  -s <pfxFile> <password>: path to a PFX file and its password for signing a VEO (can be repeated)");
            System.out.println("");
            System.out.println(" Optional:");
            System.out.println("  -d <file>: file path to the data (control) file (must be present unless -nd is set)");
            System.out.println("  -nd: no data file is required to generate the VEOs");
            System.out.println("  -h <hashAlgorithm>: specifies the hash algorithm (default SHA-256)");
            System.out.println("  -o <directory>: the directory in which the VEOs are created (default is current working directory)");
            System.out.println("");
            System.out.println("  -v: verbose mode: give more details about processing");
            System.out.println("  -help: print this listing");
            System.out.println("");
        }

        // check to see that user specified a template directory, data file,
        // and PFXfile
        if (templateDir == null) {
            throw new VEOFatal("VEOCreator", 1, "No template directory specified. Usage: " + USAGE);
        }
        if (dataFile == null && !noDataFile) {
            throw new VEOFatal("VEOCreator", 2, "No data file specified. Usage: " + USAGE);
        }
        if (pfxFile == null) {
            throw new VEOFatal("VEOCreator", 3, "No PFX file specified. Usage: " + USAGE);
        }

        System.out.println("Configuration:");
        if (dataFile != null) {
            System.out.println(" Data file: '" + dataFile.toString() + "'");
        } else {
            System.out.println(" No data file is specified");
        }
        System.out.println(" Template directory: '" + templateDir.toString() + "'");
        System.out.println(" PFX file: '" + pfxFile.toString() + "'");
        if (outputDir != null) {
            System.out.println(" Output directory: '" + outputDir.toString() + "'");
        }
        System.out.println(" Hash algorithm (specified on command line or the default): " + hashAlg);
        if (verbose) {
            System.out.println(" Verbose output is selected");
        }

        // read the templates
        try {
            vg = new VEOGenerator(new File(templateDir, "encodingTemplates"), args);
            rData = Fragment.parseTemplate(new File(templateDir, "record.txt"), args);
            fData = Fragment.parseTemplate(new File(templateDir, "file.txt"), args);
            dData = Fragment.parseTemplate(new File(templateDir, "document.txt"), args);
        } catch (VEOError e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }

        // if a password for the pfx file has not been supplied, ask for it...
        if (passwd == null) {
            sb = new StringBuffer();
            System.out.print("Password: ");
            try {
                while ((c = System.in.read()) != -1) {
                    System.out.print("\b*");
                    ch = (char) c;
                    if (ch == '\r' || ch == '\n') {
                        break;
                    }
                    sb.append(ch);
                }
            } catch (IOException e) {
                throw new VEOFatal("VEOCreator", 4, "Failed when trying to get password from user: " + e.getMessage());
            }
            passwd = sb.toString();
        }

        // open pfx file
        try {
            signer = new PFXUser(pfxFile.getPath(), passwd);
        } catch (VEOError e) {
            throw new VEOFatal("VEOCreator", 5, "Failed opening PFX file: " + e.getMessage());
        }
    }

    /**
     * Configure
     *
     * This method configures the VEOCreator from the arguments on the command
     * line. See the comment at the start of this file for the command line
     * arguments.
     *
     * @param args[] the command line arguments
     */
    private void configure(String args[]) throws VEOFatal {
        int i;

        // process command line arguments
        i = 0;
        try {
            while (i < args.length) {

                // get hash algorithm
                if (args[i].toLowerCase().equals("-help")) {
                    i++;
                    help = true;
                    continue;
                }

                // get hash algorithm
                if (args[i].toLowerCase().equals("-h")) {
                    i++;
                    hashAlg = args[i];
                    i++;
                    continue;
                }

                // get template directory
                if (args[i].toLowerCase().equals("-t")) {
                    i++;
                    templateDir = openFile("template directory", args[i], true);
                    i++;
                    continue;
                }

                // get data file
                if (args[i].toLowerCase().equals("-d")) {
                    i++;
                    dataFile = openFile("data file", args[i], false);
                    i++;
                    continue;
                }

                // data file will be passed to buildVEOs()
                if (args[i].toLowerCase().equals("-nd")) {
                    noDataFile = true;
                    i++;
                    continue;
                }

                // get pfx file
                if (args[i].toLowerCase().equals("-s")) {
                    i++;
                    pfxFile = openFile("PFX file", args[i], false);
                    i++;
                    passwd = args[i];
                    i++;
                    continue;
                }

                // get output directory
                if (args[i].toLowerCase().equals("-o")) {
                    i++;
                    outputDir = openFile("output directory", args[i], true);
                    i++;
                    continue;
                }

                // get password
                if (args[i].toLowerCase().equals("-p")) {
                    i++;
                    passwd = args[i];
                    i++;
                    continue;
                }

                // if verbose...
                if (args[i].toLowerCase().equals("-v")) {
                    verbose = true;
                    i++;
                    System.err.println("Verbose output");
                    continue;
                }

                // if unrecognised arguement, print help string and exit
                throw new VEOFatal("VEOCreator", 6, "Unrecognised argument '" + args[i] + "'");
            }
        } catch (ArrayIndexOutOfBoundsException ae) {
            throw new VEOFatal("VEOCreator", 7, "Missing argument. Usage: " + USAGE);
        }
    }

    /**
     * Open file.
     *
     * This method opens a file, checking to see that it exists and is the
     * correct type. The program terminates if an error is encountered.
     *
     * @param type a String describing the file to be opened
     * @param name the file name to be opened
     * @param isDirectory true if the file is supposed to be a directory
     * @return the File opened
     */
    private File openFile(String type, String name, boolean isDirectory) throws VEOFatal {
        String s;
        File f;

        s = null;
        f = null;
        try {
            f = new File(name);
            s = f.getCanonicalPath();
        } catch (NullPointerException npe) {
            throw new VEOFatal("VEOCreator.openFile()", 8, npe.toString());
        } catch (IOException ioe) {
            throw new VEOFatal("VEOCreator.openFile()", 9, "IO Error when accessing " + type + ": " + ioe.getMessage());
        }
        if (s == null) {
            throw new VEOFatal("VEOCreator.openFile()", 10, type + ", " + name + ", " + isDirectory + "): f.getCanonicalPath() returned null");
        }
        if (!f.exists()) {
            throw new VEOFatal("VEOCreator.openFile()", 11, type + " '" + s + "' does not exist");
        }
        if (isDirectory && !f.isDirectory()) {
            throw new VEOFatal("VEOCreator.openFile()", 12, type + " '" + s + "' is a file not a directory");
        }
        if (!isDirectory && f.isDirectory()) {
            throw new VEOFatal("VEOCreator.openFile()", 12, type + " '" + s + "' is a directory not a file");
        }
        if (verbose) {
            System.err.println(type + " is '" + s + "'");
        }
        return f;
    }

    /**
     * Parse a template file. If the file does not exist, return null.
     *
     * @param filename the name of the template file to read
     * @param args an array of Strings to use for substitutions
     * @return Fragment parsed template
     */
    private Fragment parseTemplate(String filename, String[] args) {
        File f;
        Fragment template;
        String name = "VEOCreator.parseTemplate(): ";

        try {
            f = new File(templateDir, filename);
        } catch (NullPointerException e) {
            System.err.println(name + "template file name is null");
            return null;
        }
        if (!f.exists()) {
            return null;
        }
        if (f.isDirectory()) {
            System.err.println(name + "Error in parsing template: '" + filename + "' is a directory");
            return null;
        }
        try {
            template = Fragment.parseTemplate(f, args);
        } catch (VEOError e) {
            System.err.println(name + "Error in parsing template: " + e.getMessage());
            return null;
        }
        return template;
    }

    /**
     * Build the VEOs. This method processes the data file passed as a command
     * line argument in the constructor, building VEOs from the data and the
     * templates.
     *
     * @throws VEOError when anything goes wrong...
     */
    public void buildVEOs() throws VEOError {
        buildVEOs(dataFile);
    }

    /**
     * Build the VEOs. This method processes the data file contained in File
     * argument, building VEOs from the data and the templates.
     *
     * @param data file containing the data file
     * @throws VEOError when anything goes wrong...
     */
    public void buildVEOs(File data) throws VEOError {
        TableDataSource tds;
        String name = "VEOCreator.buildVEOs(): ";

        if (data == null) {
            throw new VEOError(name + "Null datafile!");
        }

        tds = new TableDataSource(data);
        buildVEOs(tds);
        tds.close();
    }

    /**
     * Build the VEOs. This method processes the data contained in an array of
     * linked lists. Each element in the linked list is equivalent to the line
     * of a data file, and the elements in the linked list are strings.
     *
     * @param data array of LinkedList containing Strings forming the data
     * @throws VEOError when anything goes wrong...
     */
    public void buildVEOs(LinkedList[] data) throws VEOError {
        ListDataSource lds;
        String name = "VEOCreator.buildVEOs(): ";

        if (data == null) {
            throw new VEOError(name + "Null data file!");
        }

        lds = new ListDataSource(data);
        // System.err.println(lds.toString());
        buildVEOs(lds);
    }

    /**
     * Build the VEOs. This method processes a generic data source building VEOs
     * from the data and the templates.
     *
     * @param data the data to use when building the VEOs
     * @throws VEOError when anything goes wrong...
     */
    public void buildVEOs(DataSource data) throws VEOError {
        int seqNo;
        String name = "VEOCreator.buildVEOs(): ";

        if (data == null) {
            throw new VEOError(name + "Null data file!");
        }

        // build VEOs from information in data file
        seqNo = 1;
        while (!data.isAtEnd()) {
            if (verbose) {
                System.err.print(System.currentTimeMillis() / 1000 + " ");
                System.err.println("Building " + data.getColumn(2) + " (" + seqNo + ")");
            }
            buildNewVEO(seqNo, data);
            seqNo++;
        }
    }

    /**
     * Build an individual VEO according to the templates and the data...
     */
    private void buildNewVEO(int seqNo, DataSource tds) throws VEOError {
        File veo;
        String name = "VEOCreator.buildNewVEO(): ";

        // VEO file name is in column 2...
        if (outputDir == null) {
            veo = new File(tds.getColumn(2));
        } else {
            veo = new File(outputDir, tds.getColumn(2));
        }

        // start VEO
        vg.startVEO(veo, seqNo, 1);
        try {
            vg.addSignatureBlock(signer, hashAlg);
            vg.addLockSignatureBlock(1, signer, hashAlg);
        } catch (VEOError ve) {
            vg.cleanUpAfterError();
            throw new VEOError(ve.getMessage());
        }

        // are we building a record VEO?
        if (tds.getRowType() == DataSource.DS_Record) {

            // check we have the templates
            if (rData == null) {
                System.err.println(name + "No record template file found ('record.txt'), but attempting to make a record");
                vg.cleanUpAfterError();
                return;
            }
            if (dData == null) {
                System.err.println(name + "No document template file found ('document.txt'), but attempting to make a document");
                tds.getNextRow();
                vg.cleanUpAfterError();
                return;
            }

            try {
                // start record
                vg.startRecord(rData, tds);
                tds.getNextRow();

                // go on to add the documents
                while (tds.getRowType() == DataSource.DS_Document) {
                    vg.startDocument(dData, tds);
                    tds.getNextRow();

                    // add encodings
                    while (tds.getRowType() == DataSource.DS_Encoding) {
                        vg.addEncoding(new File(tds.getColumn(2)));
                        tds.getNextRow();
                    }
                    vg.endDocument();
                }
                vg.endRecord();
            } catch (VEOError ve) {
                vg.cleanUpAfterError();
                throw new VEOError(ve.getMessage());
            }

            // are we building a file VEO?
        } else if (tds.getRowType() == DataSource.DS_File) {

            // check we have a file template
            if (fData == null) {
                System.err.println(name + "No file template file found ('file.txt'), but attempting to make a file");
                tds.getNextRow();
                vg.cleanUpAfterError();
                return;
            }

            // generate File VEO
            try {
                vg.addFile(fData, tds);
                tds.getNextRow();
            } catch (VEOError ve) {
                vg.cleanUpAfterError();
                throw new VEOError(ve.getMessage());
            }

            // are we building a simple Record VEO?
        } else if (tds.getRowType() == DataSource.DS_SimpleRecord) {

            // check we have a record and document template
            if (rData == null) {
                System.err.println(name + "No record template file found ('record.txt'), but attempting to make a record");
                tds.getNextRow();
                vg.cleanUpAfterError();
                return;
            }
            if (dData == null) {
                System.err.println(name + "No document template file found ('document.txt'), but attempting to make a document");
                tds.getNextRow();
                vg.cleanUpAfterError();
                return;
            }

            try {
                vg.addSimpleRecord(rData, dData, tds);
                tds.getNextRow();
            } catch (VEOError ve) {
                vg.cleanUpAfterError();
                throw new VEOError(ve.getMessage());
            }

            // otherwise it's wrong!
        } else {
            System.err.println(name + "Out of sequence table data row. Expecting DS_File or DS_Record, found " + tds.getRowType());
            vg.cleanUpAfterError();
            return;
        }

        // end VEO
        vg.endVEO();
    }

    /**
     * Main program. This program is given a set of command line arguments and
     * builds a collection of VEOs from the information in the arguments.
     *
     * @param args command line arguments
     */
    public static void main(String args[]) {
        VEOCreator vc;

        // process datafile
        try {
            vc = new VEOCreator(args);
            vc.buildVEOs();
        } catch (VEOError e) {
            System.err.println("Error in constructing VEO (" + e.getMessage() + ")");
            System.exit(-1);
        }
    }
}
