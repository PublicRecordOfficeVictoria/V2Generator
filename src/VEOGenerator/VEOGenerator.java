package VEOGenerator;

/**
 * *************************************************************
 *
 * V E O G E N E R A T O R
 *
 * This class generates VEOs from a template and data
 *
 * Andrew Waugh (andrew.waugh@dvc.vic.gov.au) Copyright 2006 PROV
 *
 * v1.1 20071203 Added includeSignedObject()
 *
 *************************************************************
 */
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class generates a VEO using a set of XML templates and datafiles. VEOs
 * are constructed by:
 * <ul>
 * <li> Constructing an instance of a VEOGenerator.</li>
 * <li> Parsing template files that are used to build the vers:FileMetadata,
 * vers:RecordMetadata, and vers:DocumentMetadata elements.</li>
 * <li> Opening a DataSource that is used to populate the variable elements of
 * the templates.
 * <li> Opening PFX files that contain details about the signer of the VEO
 * (notably the private key and certificates).</li>
 * <li> Calling a sequence of methods to build the VEO.</li>
 * </ul>
 * <p>
 * A typical sequence of calls (to build a VEO with one signer, one Document and
 * one Encoding) would be:
 * <pre>
 * startVEO		// generate to beginning of vers:SignatureBlock
 * addSignatureBlock	// generate vers:SignatureBlock
 * addLockSignatureBlock // generate vers:LockSignatureBlock
 * startRecord		// generate vers:SignedObject to start of vers:Document
 * startDocument	// generate vers:Document to start of vers:Encoding
 * addEncoding		// generate vers:Encoding
 * endDocument		// finish vers:Document
 * endRecord		// finish vers:SignedObject
 * endVEO		// finish VEO, including calculating signatures
 * </pre> The sequence startDocument...endDocument can be repeated to add
 * multiple documents, and the addSignatureBlock and addEncoding can be repeated
 * to add multiple signatures and encodings. The whole sequence
 * startVEO...endVEO can be repeated to generate multiple VEOs.
 * <p>
 * The vers:RecordMetadata, vers:FileMetadata, vers:DocumentMetadata, and
 * vers:Encoding elements are generated from XML templates. These are text files
 * which contain XML text (to be included literally in each VEO created), and
 * substitutions. Typical substitutions are: include the current date/time,
 * include the contents of a file, and include the contents of a column in a
 * data source. For more detail about substitutions see {@link Fragment}.
 * <p>
 * Data for some substitutions is obtained from a {@link DataSource}. The model
 * of a DataSource is of a table. Each row represents the data for a template,
 * and each column the data for a substitution. Conventionally, the first column
 * indicates the type of template to be constructed: 'f' for a File VEO, 'r' for
 * a Record VEO, 'd' for a Document, and 'e' for an Encoding.
 * <p>
 * The following code builds multiple Record VEOs. Note that no error handling
 * is shown.
 * <pre>
 * VEOGenerator vg;
 * Fragment rData;	// template for record metadata
 * Fragment dData;	// template for document metadata
 * TableDataSource tds; // data to fill in templates
 * PFXUser signer;	// pfx file with private key and certificates
 * File veo;
 * int seqNo;
 *
 * vg = new VEOGenerator(new File("encDirectory"), args);
 * signer = new PFXUser(new String("signer.pfx"), new String("password"));
 * rData = Fragment.parseTemplate(new File("rMeta.txt"), args);
 * dData = Fragment.parseTemplate(new File("dMeta.txt"), args);
 * tds = new TableDataSource(new File("dataFile.txt"));
 *
 * seqNo = 1;
 * while (!tds.isAtEnd()) {
 * 	veo = new File("TestData/"+Integer.toString(seqNo)+".veo");
 *
 * 	// start VEO, adding signature and lock signature blocks
 * 	vg.startVEO(veo, seqNo, 1);
 * 	vg.addSignatureBlock(signer);
 * 	vg.addLockSignatureBlock(1, signer);
 *
 * 	// start record, including documents and and encodings
 *	vg.startRecord(rData, tds);
 *	tds.getNextRow();
 *	while (tds.getRowType() == DataSource.DS_Document) {
 *		vg.startDocument(dData, tds);
 *		tds.getNextRow();
 *		while (tds.getRowType() == DataSource.DS_Encoding) {
 *			vg.addEncoding(new File(tds.getColumn(2)));
 *			tds.getNextRow();
 *		}
 *		vg.endDocument();
 * 	vg.endRecord();
 * 	vg.endVEO();
 * 	seqNo++;
 * }
 * </pre>
 */
public class VEOGenerator {

    B64 b64;		// utility to convert to Base64
    Charset cs;		// converter from String to UTF-8
    FileChannel veo;	// veo being written
    FileOutputStream fos;	// underlying file stream for file channel
    boolean signing;	// whether calculating signature or not
    int sigId;		// id of the next signature to be added
    ArrayList<Signature> signatures;	// list of signatures being calculated
    ArrayList<Long> position;	// positions of normal signatures in VEO file
    Signature lockSig;	// lock signature itself
    long locksigPosn;	// position of lock signature in VEO file
    int signsSigBlock;	// which signature the lock signature signs
    HashMap<String, Fragment> encTemplates;	// directory for templates for encodings
    int seqNo;		// sequence number of current VEO
    int revisionId;		// current revision number
    int documentId;		// current document number
    int encodingId;		// current encoding number

    int state;		// which components have been generated
    int NOT_STARTED = 0;	// no VEOs have been started
    int VEO_STARTED = 1;	// VEO has been started
    int SIG_BLOCK_OUT = 2;	// signature block has been outputed
    int LOCK_SIG_OUT = 3;	// lock signature block has been outputed
    int REC_STARTED = 4;	// record started
    int DOC_STARTED = 5;	// document started
    int ENCODING_ADDED = 6;	// encoding added to document
    int DOC_ENDED = 7;	// document ended
    int REC_ENDED = 8;	// record ended
    int FILE_ENDED = 20;	// file started
    int VEO_ENDED = 100;	// veo completed
    
    // FileWriter fw;           // use when outputing the byte stream being signed

    /**
     * Construct a VEOGenerator instance given a directory in which encoding
     * templates are located. Encoding templates are used to construct
     * vers:Encoding elements given a file of a particular format. The format is
     * identified by the file extension of the file name (e.g. the '.pdf' of a
     * the file 'example.pdf').
     * <p>
     * The encoding directory is specified by the encDirectory argument. An
     * error will be thrown if the encoding directory does not exist or is a not
     * a directory. The constructor parses all of the encoding templates found
     * in the encoding directory.
     * <p>
     * The encoding templates must be named '&lt;fileextension&gt;.txt'; for
     * example the template used for a PDF file will be named 'pdf.txt'. The
     * directory must contain a template named 'unknown.txt'. This template is
     * used when there is no template for the file. For example, if the file
     * 'example.p65' was to be included in the VEO, but there was no encoding
     * template 'p65.txt' in the encoding directory, the template 'unknown.txt'
     * would be used.
     * <p>
     * The second argument is an array of strings which is used to populate any
     * command line argument substitutions in the encoding templates.
     * <p>
     * This method will generate a VEOError in the following situations:
     * <ul>
     * <li>the encoding directory does not exists, is a file, or the canonical
     * path cannot be generated.</li>
     * <li>a template file cannot be opened, is a directory, or the cannonical
     * path cannot be generated.</li>
     * <li>an IO error occurred when reading a template file.</li>
     * </ul>
     * <p>
     * It is not a fatal error if a syntax error in encountered in parsing the
     * template. If a syntax error is encountered, a message is printed on the
     * standard out.
     *
     * @param encDirectory	The directory in which the Encoding templates are
     * located
     * @param args	An array of strings which is used to populate command line
     * argument substitutions in the encoding templates.
     * @throws VEOError	If a fatal error occurs
     */
    public VEOGenerator(File encDirectory, String[] args) throws VEOError {
        File[] files;
        String name = "VEOGenerator(): ";
        String s;
        int i, j;
        Fragment f;
        String id;
        
        // Utilities
        b64 = new B64();
        try {
            cs = Charset.forName("UTF-8");
        } catch (IllegalCharsetNameException | UnsupportedCharsetException icne) {
            System.err.println(name + icne.getMessage());
        }
        veo = null;
        fos = null;
        signing = false;
        sigId = 1;
        signatures = new ArrayList<>();
        position = new ArrayList<>();
        signsSigBlock = 0;
        seqNo = 0;
        state = NOT_STARTED;

        // allocate hash table for encoding templates
        encTemplates = new HashMap<>();

        // go through encoding template directory, parsing encoding templates
        if (!encDirectory.exists()) {
            try {
                s = encDirectory.getCanonicalPath();
            } catch (IOException ioe) {
                throw new VEOError(name
                        + "IOException when getting canonical path of encoding template directory: "
                        + ioe.getMessage());
            }
            throw new VEOError(name
                    + "Encoding template directory '" + s + "' does not exist");
        }

        // construct list of '.txt' files
        files = encDirectory.listFiles(new encodingFileFilter());

        // parse each file. Hashtable key is the part of the filename in
        // advance of the '.' (if any)
        for (i = 0; i < files.length; i++) {

            // parse template
            f = Fragment.parseTemplate(files[i], args);
            if (f == null) {
                continue;
            }

            // get name of template (leading part of filename)
            j = files[i].getName().indexOf('.');
            switch (j) {
                case -1:
                    id = files[i].getName();
                    break;
                case 0:
                    continue;
                default:
                    try {
                        id = files[i].getName().substring(0, j);
                    } catch (IndexOutOfBoundsException ie) {
                        continue;
                        /* ignore, cannot happen */
                    }
                    break;
            }

            // put in hashtable
            encTemplates.put(id, f);
        }
    }

    /**
     * File filter to identify files that contain encoding metadata
     *
     * To be considered, the files must not be a directory, and must have the
     * file extension '.txt'
     */
    class encodingFileFilter implements FileFilter {

        @Override
        public boolean accept(File f) {
            String name;

            if (f.isDirectory()) {
                return false;
            }
            name = f.getName();
            return name.contains(".txt");
        }
    }

    /**
     * Construct a VEOGenerator instance without specifying a file encoding
     * directory. Must only be used with includeSignedObject()
     *
     * @throws VEOError	If a fatal error occurs
     */
    public VEOGenerator() throws VEOError {
        String name = "VEOGenerator(): ";

        b64 = new B64();
        try {
            cs = Charset.forName("UTF-8");
        } catch (IllegalCharsetNameException | UnsupportedCharsetException icne) {
            System.err.println(name + icne.getMessage());
        }
        veo = null;
        fos = null;
        signing = false;
        sigId = 1;
        signatures = new ArrayList<>();
        position = new ArrayList<>();
        signsSigBlock = 0;
        seqNo = 0;
        state = NOT_STARTED;
        encTemplates = null;
    }

    /**
     * *************************************************************
     *
     * Start VEO
     *
     *************************************************************
     */
    static String contentsVEO1
            = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>\r\n"
            + "<!DOCTYPE vers:VERSEncapsulatedObject SYSTEM \"vers.dtd\">\r\n"
            + "<vers:VERSEncapsulatedObject\r\n"
            + "  xmlns:vers=\"http://www.prov.vic.gov.au/gservice/standard/pros99007.htm\" \r\n"
            + "  xmlns:naa=\"http://www.naa.gov.au/recordkeeping/control/rkms/contents.html\">\r\n"
            + " <vers:VEOFormatDescription>\r\n"
            + "  <vers:Text>\r\n"
            + "This record conforms to the structure defined in \"Management of\r\n"
            + "Electronic Records, PROS 99/007 (Version 2.0), Public Record Office\r\n"
            + "Victoria, 2003. The structure of this record is represented using\r\n"
            + "Extensible Markup Lanugage (XML) 1.0, W3C, 1998\r\n"
            + "  </vers:Text>\r\n"
            + " </vers:VEOFormatDescription>\r\n"
            + " <vers:Version>2.0</vers:Version>\r\n";

    /**
     * Start generating a new VEO.
     *
     * This method starts a new VEO. It must be called once per VEO as the first
     * step. It is passed the file name of the VEO, sequence number in this run,
     * and revisionId. It generates the preamble that is common to all VEOs
     * (i.e. up to the start of the first signature block).
     * <p>
     * The sequence number is used to populate the sequence number substitution
     * in any of the templates used to construct the VEO. The revisionId is used
     * to construct the vers:id attributes in the VEO.
     * <p>
     * This method may be called multiple times to generate multiple VEOs,
     * however, only one VEO at a time can be generated. Once startVEO is
     * called, a second call to startVEO cannot be made until endVEO is called
     * to finish the first VEO.
     * <p>
     * This method will generate a VEOError in the following situations:
     * <ul>
     * <li>startVEO has already been called for this VEO, without an intervening
     * call to endVEO.</li>
     * <li>addSignatureBlock has already been called for this VEO</li>
     * <li>the revisionId is zero or negative</li>
     * <li>the VEO file cannot be opened for writing</li>
     * <li>an error occurred when writing the VEO to the file</li>
     * </ul>
     *
     * @param veofile filename of newly created VEO
     * @param seqNo unique number of this VEO in this run
     * @param revisionId the revision component of the vers:id attributes
     * @throws VEOError if a fatal error occurs
     */
    public void startVEO(File veofile, int seqNo, int revisionId) throws VEOError {
        String name = "VEOGenerator.startVEO(): ";

        // sanity check
        if (state != NOT_STARTED && state != VEO_ENDED) {
            throw new VEOError(name
                    + "startVEO() has already been called on this VEO");
        }
        if (revisionId < 1) {
            throw new VEOError(name
                    + "revisionId must be a positive integer");
        }

        this.seqNo = seqNo;
        this.revisionId = revisionId;
        signing = false;
        sigId = 1;
        signatures = new ArrayList<>();
        position = new ArrayList<>();
        signsSigBlock = 0;

        // open veoName for writing
        try {
            fos = new FileOutputStream(veofile);
        } catch (FileNotFoundException fnfe) {
            throw new VEOError("Output VEO file '" + veofile.getName() + "' cannot be opened for writing");
        }
        veo = fos.getChannel();

        // generate start of XML file up to vers:Signature
        outputDataToVeo(cs.encode(contentsVEO1));

        state = VEO_STARTED;
        
        // use the following when it is necessary to output the byte stream
        // being signed
        /*
        try {
            fw = new FileWriter("Sig.txt");
        } catch (IOException ioe) {
            System.err.println(ioe.toString());
        }
        */

    }

    /**
     * *************************************************************
     *
     * Clean up after error
     *
     *************************************************************
     */
    /**
     * Clean up after error
     *
     * This method should be called if a VEOError occurs when generating a VEO
     * and it is desired to call startVEO again.
     */
    public void cleanUpAfterError() {
        state = NOT_STARTED;
        try {
            veo.close();
            fos.close();
        } catch (IOException ioe) {
            /* ignore */ }
    }

    /**
     * *************************************************************
     *
     * End VEO
     *
     *************************************************************
     */
    static String contentsVEO2
            = "</vers:VERSEncapsulatedObject>\r\n";

    /**
     * Finalise the VEO.
     *
     * This method finishes a VEO. It writes the final common piece of XML, and
     * finalises all of the signatures. It must be called once per VEO as the
     * last step of constructing a VEO. Once endVEO is called, startVEO can be
     * called to start construction of a new VEO.
     * <p>
     * This method will generate a VEOError in the following situations:
     * <ul>
     * <li>endRecord or endFile has not been called on this VEO</li>
     * <li>endVEO has already been called on this VEO</li>
     * <li>an error occurs when calculating a signature or lock signature</li>
     * <li>an error occurs in writing the signature or lock signature to the
     * VEO</li>
     * <li>an error occurred when writing the VEO to the file</li>
     * </ul>
     *
     * @throws VEOError if a fatal error occurs
     */
    public void endVEO() throws VEOError {
        String name = "VEOGenerator.endVEO(): ";
        int i, j;
        ByteBuffer bb;
        byte b;
        Signature sig;
        byte[] signature;

        // sanity check
        if (veo == null) {
            throw new VEOError(name + "VEO has not been started");
        }
        if (state < REC_ENDED) {
            throw new VEOError(name
                    + "endRecord() or endFile() has not been called on this VEO");
        }
        if (state == VEO_ENDED) {
            throw new VEOError(name
                    + "endVEO() has already been called on this VEO");
        }
        state = VEO_ENDED;
        
        // use the following when it is necessary to output the byte stream being signed
        /*
        try {
            fw.close();
        } catch (IOException ioe) {
            //
        }
        */

        // end calculating signature
        signing = false;

        // finalise end of VEO
        outputDataToVeo(cs.encode(contentsVEO2));

        // finalise signatures and place them in signature blocks
        for (i = 0; i < signatures.size(); i++) {
            sig = signatures.get(i);

            // calculate signature and convert it into a byte buffer
            try {
                signature = sig.sign();
            } catch (SignatureException e) {
                throw new VEOError(name + e.getMessage());
            }
            bb = b64.toBase64(signature);

            // position write position in VEO file & write signature
            try {
                veo.position(position.get(i));
            } catch (IOException ioe) {
                throw new VEOError(name
                        + "Error positioning to write signature: "
                        + ioe.getMessage());
            }
            outputDataToVeo(bb);

            // is this the signature we will calculate lock signature on?
            if (signsSigBlock == i + 1) {
                for (j = 0; j < bb.limit(); j++) {
                    b = bb.get(j);
                    if (b == 0x09 || b == 0x0a
                            || b == 0x0d || b == 0x20) {
                        continue;
                    }
                    try {
                        lockSig.update(b);
                    } catch (SignatureException e) {
                        throw new VEOError(name + e.getMessage());
                    }
                }

                // calculate signature and convert it into a byte buffer
                try {
                    signature = lockSig.sign();
                } catch (SignatureException e) {
                    throw new VEOError(name + e.getMessage());
                }
                bb = b64.toBase64(signature);

                // position write position in VEO file & write signature
                try {
                    veo.position(locksigPosn);
                } catch (IOException ioe) {
                    throw new VEOError(name
                            + "Error positioning to write signature: "
                            + ioe.getMessage());
                }
                outputDataToVeo(bb);
            }
        }

        // close veo
        try {
            veo.close();
            fos.close();
        } catch (IOException ioe) {
            /* ignore */ }
    }

    /**
     * *************************************************************
     *
     * Add Signature Block
     *
     *************************************************************
     */
    static String contentsSig1a = " <vers:SignatureBlock vers:id=\"Revision-";
    static String contentsSig2 = "-Signature-";
    static String contentsSig3 = "\">\r\n";
    static String contentsSig15a = " </vers:SignatureBlock>\r\n";

    /**
     * Add signature block to the VEO.
     * <p>
     * This method adds a new signature block. It must be called immediately
     * after startVEO. It may be called multiple times per VEO to generate
     * multiple signature blocks. The last call to addSignature must be followed
     * by a call to addLockSignatureBlock.
     * <p>
     * The first signature block added is assigned the vers:id
     * 'Revision-&lt;rev&gt;-Signature-1' (where &lt;rev&gt; is the value of the
     * revisionId passed to startVEO). The second signature block added has the
     * vers:id 'Revision-&lt;rev&gt;-Signature-2' and so on.
     * <p>
     * The signature algorithms are automatically set. The hash algorithm is
     * always SHA1. The encryption algorithm is derived from the private key
     * passed in signer
     * <p>
     * This method will generate a VEOError in the following situations:
     * <ul>
     * <li>startVEO has not been called on this VEO</li>
     * <li>addLockSignatureBlock has been called on this VEO</li>
     * <li>the signer is null</li>
     * <li>the private key could not be retrieved from the PFXUser.</li>
     * <li>the private key in the signer used an unsupported algorithm, or was
     * otherwise invalid</li>
     * <li>an error occurred when writing the VEO to the file</li>
     * </ul>
     *
     * @param signer PFXUser representing the signer.
     * @throws VEOError if a fatal error occurs
     */
    public void addSignatureBlock(PFXUser signer)
            throws VEOError {
        String name = "VEOGenerator.addSignatureBlock(): ";
        Long posn;
        Signature sig;
        PrivateKey priKey;
        String algorithmId;

        // sanity checks
        if (veo == null) {
            throw new VEOError(name + "VEO has not been started");
        }
        if (signer == null) {
            throw new VEOError(name + "passed null signer");
        }
        if (state < VEO_STARTED) {
            throw new VEOError(name
                    + "startVEO() has not been called on this VEO");
        }
        if (state > SIG_BLOCK_OUT) {
            throw new VEOError(name
                    + "addLockSignatureBlock() has already been called on this VEO");
        }
        state = SIG_BLOCK_OUT;

        // work out what algorithm to use. This will be based on the algorithm
        // associated with the private key, and will always be SHA1
        priKey = signer.getPrivate();
        if (priKey == null) {
            throw new VEOError(name
                    + "failed to get private key from signer");
        }
        algorithmId = "SHA1with" + priKey.getAlgorithm();

        outputDataToVeo(cs.encode(contentsSig1a));
        outputDataToVeo(cs.encode(Integer.toString(revisionId)));
        outputDataToVeo(cs.encode(contentsSig2));
        outputDataToVeo(cs.encode(Integer.toString(sigId))); // signature id
        sigId++;
        outputDataToVeo(cs.encode(contentsSig3));
        posn = produceSignatureBlock(algorithmId, signer, false, name);
        outputDataToVeo(cs.encode(contentsSig15a));

        // remember position of signature in file
        position.add(posn);

        // initialise signature calculation
        try {
            // md = MessageDigest.getInstance("SHA1");
            // md2 = MessageDigest.getInstance("MD2");
            // md5 = MessageDigest.getInstance("MD5");
            sig = Signature.getInstance(algorithmId);
            sig.initSign(signer.getPrivate());
        } catch (NoSuchAlgorithmException e) {
            throw new VEOError(name + "No Such Algorithm: " + e.getMessage());
        } catch (InvalidKeyException e) {
            throw new VEOError(name + "Invalid Key: " + e.getMessage());
        }
        signatures.add(sig);
    }

    /**
     * *************************************************************
     *
     * Add a Lock Signature Block
     *
     *************************************************************
     */
    static String contentsSig1b
            = " <vers:LockSignatureBlock vers:signsSignatureBlock=\"Revision-";
    static String contentsSig15b = " </vers:LockSignatureBlock>\r\n";

    /**
     * Add a lock signature block to the VEO. It must be called immediately
     * after the last call to addSignatureBlock, and before the first call to
     * startRecord or startFile. It may be called only once per VEO.
     * <p>
     * <code>id</code> identifies which signature to sign. Passing '1' means
     * that the signature from the first signature block is to be signed as the
     * lock signature, '2' means the second signature block, and so on.
     * <p>
     * The <code>signer</code> contains the information necessary to create a
     * digital signature (the private key and the certificate chain). The
     * signature algorithms are automatically set. The hash algorithm is always
     * SHA1. The encryption algorithm is derived from the private key passed in
     * signer
     * <p>
     * This method will generate a VEOError in the following situations:
     * <ul>
     * <li>addSignatureBlock has not been called on this VEO</li>
     * <li>addLockSignatureBlock has been called on this VEO</li>
     * <li>the signer is null</li>
     * <li>the private key could not be retrieved from the PFXUser.</li>
     * <li>the private key in the signer used an unsupported algorithm, or was
     * otherwise invalid</li>
     * <li>the sigId is zero, negative, or greater than the number of signature
     * blocks that have been added to the VEO</li>
     * <li>an error occurred when writing the VEO to the file</li>
     * </ul>
     *
     *
     * @param id	identifier of the signature block containing the signature to
     * be signed
     * @param signer	Instance representing the signer.
     * @throws VEOError if a fatal error occurs
     */
    public void addLockSignatureBlock(int id, PFXUser signer)
            throws VEOError {
        String name = "VEOGenerator.addLockSignatureBlock(): ";
        PrivateKey priKey;
        String algorithmId;

        // sanity checks
        if (veo == null) {
            throw new VEOError(name + "VEO has not been started");
        }
        if (state < SIG_BLOCK_OUT) {
            throw new VEOError(name
                    + "addSignatureBlock() has not been called on this VEO");
        }
        if (state == LOCK_SIG_OUT) {
            throw new VEOError(name
                    + "addLockSignatureBlock() has already been called on this VEO");
        }
        if (state > LOCK_SIG_OUT) {
            throw new VEOError(name
                    + "startRecord() or startFile() has already been called on this VEO");
        }
        if (id < 1) {
            throw new VEOError(name + "signsSignatureId (" + id
                    + ") must be a positive integer");
        }
        if (sigId <= id) {
            throw new VEOError(name + "signsSignatureId (" + id
                    + ") is greater than number of signatures in VEO");
        }
        if (signer == null) {
            throw new VEOError(name + "passed null signer");
        }
        state = LOCK_SIG_OUT;

        // remember which signature to sign
        signsSigBlock = id;

        // work out what algorithm to use. This will be based on the algorithm
        // associated with the private key, and will always be SHA1
        priKey = signer.getPrivate();
        if (priKey == null) {
            throw new VEOError(name
                    + "failed to get private key from signer");
        }
        algorithmId = "SHA1with" + priKey.getAlgorithm();

        // generate lock signature block
        outputDataToVeo(cs.encode(contentsSig1b));
        outputDataToVeo(cs.encode(Integer.toString(revisionId))); // revision
        outputDataToVeo(cs.encode(contentsSig2));
        outputDataToVeo(cs.encode(Integer.toString(id))); // signature id
        outputDataToVeo(cs.encode(contentsSig3));
        locksigPosn = produceSignatureBlock(algorithmId, signer, true, name);
        outputDataToVeo(cs.encode(contentsSig15b));

        // initialise signature calculation
        try {
            // md = MessageDigest.getInstance("SHA1");
            lockSig = Signature.getInstance(algorithmId);
            lockSig.initSign(signer.getPrivate());
        } catch (NoSuchAlgorithmException e) {
            throw new VEOError(name + "No Such Algorithm: " + e.getMessage());
        } catch (InvalidKeyException e) {
            throw new VEOError(name + "Invalid Key: " + e.getMessage());
        }
    }

    /**
     * Produce Signature Bock
     *
     * A utility method that produces a signature block (either a lock signature
     * block or a normal signature block)
     *
     * @param sigAlgId a string describing the algorithms used to calculate
     * signature
     * @param signer an encapsulation of the information known about the signer
     * @returns the position in the output file of the signature itself
     */
    static String contentsSig4a
            = "  <vers:SignatureFormatDescription>\r\n"
            + "The contents of this VEO is signed using SHA-1 hash algorithm and RSA\r\n"
            + "digital signature algorithm. SHA-1 is defined in Secure Hash Standard,\r\n"
            + "FIPS PUB 180-1, National Institute of Standards and Technology, US\r\n"
            + "Department of Commerce, 17 April 1995,\r\n"
            + "(http://csrc.nist.gov/publications/fips/fips180-1/fip180-1.pdf).\r\n"
            + "The RSA algorithm (RSASSA-PKCS-v1_5) is defined in PKCS #1 v2.1: RSA\r\n"
            + "Cryptography Standard, RSA Laboratories, 14 June 2002,\r\n"
            + "(ftp://ftp.rsasecurity.com/pub/pkcs/pkcs-1/pkcs-1v2-1.pdf).\r\n";
    static String contentsSig4b
            = "  <vers:SignatureFormatDescription>\r\n"
            + "The contents of this VEO is signed using SHA-1 hash algorithm and DSA\r\n"
            + "digital signature algorithm. SHA-1 is defined in Secure Hash Standard,\r\n"
            + "FIPS PUB 180-1, National Institute of Standards and Technology, US\r\n"
            + "Department of Commerce, 17 April 1995,\r\n"
            + "(http://csrc.nist.gov/publications/fips/fips180-1/fip180-1.pdf).\r\n"
            + "The DSA algorithm is defined in Digital SIgnature Standard (DSS),\r\n"
            + "FIPS PUB 186-2, National Institute of Standards and Technology\r\n"
            + "US Department of Commerce, 27 January 2000,\r\n"
            + "(http://csrc.nist.gov/publications/fips/fips186-2/fip186-2-change1.pdf).";

    static String contentsSig5
            = "Details of the public keys are encoded as X.509 certificates in the\r\n"
            + "vers:CertificateBlock elements. X.509 certificates are define in\r\n"
            + "\"Information technology - Open Systems Interconnection - The Directory:\r\n"
            + "Public-key and attribute certificate frameworks\", ITU-T Recommendation\r\n"
            + "X.509 (2000). The signature and certificates are encoded using Base64.\r\n"
            + "Base64 is defined in Multipurpose Internet Mail Extensions (MIME) Part\r\n"
            + "One: Format of Internet Message Bodies, Section 6.8, Base64\r\n"
            + "Content-Transfer- Encoding, IETF RFC 2045, N. Freed &amp;\r\n"
            + "N. Borenstein, November 1996,\r\n"
            + "(http://www.ietf.org/rfc/rfc2045.txt?number=2045)\r\n";
    static String contentsSig5a
            = "The signature covers the contents of the vers:SignedObject element\r\n"
            + "starting with the 'less than' symbol of the vers:SignedObject start tag\r\n"
            + "up to and including the 'greater than' symbol of the vers:SignedObject\r\n"
            + "end tag. Before verifying the signature all whitespace (Unicode\r\n"
            + "characters U+0009, U+000A, U+000D, and U+0020) must be removed from the\r\n"
            + "text.\r\n";
    static String contentsSig5b
            = "The signature covers the contents of the vers:SignedObject element\r\n"
            + "starting with the first base64 encoded character and ending with the\r\n"
            + "last character.\r\n";
    static String contentsSig6
            = "  </vers:SignatureFormatDescription>\r\n"
            + "  <vers:SignatureAlgorithm>\r\n"
            + "   <vers:SignatureAlgorithmIdentifier>\r\n";
    static String contentsSig7
            = "\r\n"
            + "   </vers:SignatureAlgorithmIdentifier>\r\n"
            + "  </vers:SignatureAlgorithm>\r\n"
            + "  <vers:SignatureDate>\r\n";
    static String contentsSig8
            = "\r\n"
            + "</vers:SignatureDate>\r\n"
            + "  <vers:Signer>\r\n";
    static String contentsSig9
            = "</vers:Signer>\r\n"
            + "  <vers:Signature>\r\n";
    static String contentsSig10
            = "                                                                        \r\n"
            + "                                                                        \r\n"
            + "                                                                        \r\n"
            + "                                                                        \r\n"
            + "                                                        \r\n";
    static String contentsSig11
            = "  </vers:Signature>\r\n"
            + "  <vers:CertificateBlock>\r\n";
    static String contentsSig12
            = "   <vers:Certificate>\r\n";
    static String contentsSig13
            = "\r\n"
            + "   </vers:Certificate>\r\n";
    static String contentsSig14
            = "  </vers:CertificateBlock>\r\n";

    private long produceSignatureBlock(
            String algorithmId, PFXUser signer, boolean lockSig, String name)
            throws VEOError {
        long posn;
        int i;
        X509Certificate cert;
        byte[] b;
        Signature sig;
        Principal subject;

        // output description of algorithms used
        if (algorithmId.equals("SHA1withRSA")) {
            outputDataToVeo(cs.encode(contentsSig4a));
        }
        if (algorithmId.equals("SHA1withDSA")) {
            outputDataToVeo(cs.encode(contentsSig4b));
        }

        // output description of calculating signature
        outputDataToVeo(cs.encode(contentsSig5));
        if (!lockSig) {
            outputDataToVeo(cs.encode(contentsSig5a));
        } else {
            outputDataToVeo(cs.encode(contentsSig5b));
        }

        // output signature algorithm id
        outputDataToVeo(cs.encode(contentsSig6));
        if (algorithmId.equals("SHA1withDSA")) {
            outputDataToVeo(cs.encode("1.2.840.10040.4.3"));
        }
        if (algorithmId.equals("MD2withRSA")) {
            outputDataToVeo(cs.encode("1.2.840.113549.1.1.2"));
        }
        if (algorithmId.equals("MD5withRSA")) {
            outputDataToVeo(cs.encode("1.2.840.113549.1.1.4"));
        }
        if (algorithmId.equals("SHA1withRSA")) {
            outputDataToVeo(cs.encode("1.2.840.113549.1.1.5"));
        }

        outputDataToVeo(cs.encode(contentsSig7));

        // output date
        outputDataToVeo(cs.encode(new DateFragment(name).versDateTime(0)));

        outputDataToVeo(cs.encode(contentsSig8));

        // output signer
        cert = signer.getX509Certificate(0);
        if (cert != null) {
            subject = cert.getSubjectDN();
            if (subject != null) {
                String s = subject.toString();

                // encode XML characters
                s = s.replaceAll("&", "&amp;");
                s = s.replaceAll("<", "&lt;");
                s = s.replaceAll(">", "&gt;");
                outputDataToVeo(cs.encode(s));
            } else {
                outputDataToVeo(cs.encode("unknown subject"));
            }
        } else {
            outputDataToVeo(cs.encode("Unknown"));
        }

        outputDataToVeo(cs.encode(contentsSig9));

        // output dummy signature and remember position
        try {
            posn = veo.position();
        } catch (IOException ioe) {
            throw new VEOError(name
                    + "Error getting position to write signature: "
                    + ioe.getMessage());
        }
        outputDataToVeo(cs.encode(contentsSig10));

        outputDataToVeo(cs.encode(contentsSig11));

        // output certificates
        for (i = 0; i < signer.getCertificateChainLength(); i++) {
            outputDataToVeo(cs.encode(contentsSig12));
            b = signer.getCertificate(i);
            outputDataToVeo(b64.toBase64(b));
            outputDataToVeo(cs.encode(contentsSig13));
        }
        outputDataToVeo(cs.encode(contentsSig14));
        return posn;
    }

    /**
     * *************************************************************
     *
     * Include Signed Object
     *
     *************************************************************
     */
    /**
     * This method includes a signed object into the VEO verbatim. It must be
     * called after addLockSignatureBlock() and before startRecord() or
     * startFile().
     *
     * @param is the file to include
     * @throws VEOError if a fatal error occurs
     */
    public void includeSignedObject(InputStream is)
            throws VEOError {
        String name = "VEOGenerator.includeContent(): ";
        byte[] bin;

        // sanity checks
        if (veo == null) {
            throw new VEOError(name + "VEO has not been started");
        }
        if (state < LOCK_SIG_OUT) {
            throw new VEOError(name
                    + "addLockSignatureBlock() has not been called on this VEO");
        }
        if (state > LOCK_SIG_OUT) {
            throw new VEOError(name
                    + "startRecord() or startFile() has already been called on this VEO");
        }
        if (is == null) {
            throw new VEOError(name + "file is null");
        }

        signing = true;
        try {
            bin = new byte[1];
            while (is.read(bin) != -1) {
                outputDataToVeo(bin);
            }
        } catch (IOException ioe) {
            throw new VEOError(name + "Error reading input file: " + ioe.getMessage());
        }
        state = REC_ENDED;
        signing = false;
    }

    /**
     * *************************************************************
     *
     * Start Record
     *
     *************************************************************
     */
    static String contentsRecVEO1a
            = " <vers:SignedObject vers:VEOVersion=\"2.0\">\r\n"
            + "  <vers:ObjectMetadata>\r\n"
            + "   <vers:ObjectType>Record</vers:ObjectType>\r\n"
            + "   <vers:ObjectTypeDescription>\r\n"
            + "This object contains a record; that is a collection of information\r\n"
            + "that must be preserved for a period of time.\r\n"
            + "   </vers:ObjectTypeDescription>\r\n"
            + "   <vers:ObjectCreationDate>";
    static String contentsRecVEO1b
            = "</vers:ObjectCreationDate>\r\n"
            + "  </vers:ObjectMetadata>\r\n"
            + "  <vers:ObjectContent>\r\n"
            + "   <vers:Record>\r\n";

    /**
     * This method starts a Record VEO. Apart from completing the preamble for a
     * vers:SignedObject, this method completes the vers:RecordMetadata element
     * from the supplied template and the data.
     * <p>
     * The template contains both the variable and the fixed part of the
     * vers:RecordMetadata element. The fixed part is simply output, but the
     * variable part is filled using the data from the DataSource.
     * <p>
     * Either this method, addSimpleRecord or startFile must be called
     * immediately after the addLockSignatureBlock(). It may be called only once
     * per VEO.
     * <p>
     * This method will generate a VEOError in the following situations:
     * <ul>
     * <li>if addLockSignatureBlock has not been called on this VEO</li>
     * <li>if startRecord, addSimpleRecord or addFile has been called on this
     * VEO</li>
     * <li>if the template or data is null</li>
     * <li>an error occurred when populating the template with data</li>
     * <li>an error occurred when calculating the signature hash value</li>
     * <li>an error occurred when writing the VEO to the file</li>
     * </ul>
     *
     * @param template the Fragment used for the vers:RecordMetadata element
     * @param data	the data used to finalise the template
     * @throws VEOError if a fatal error occurs
     */
    public void startRecord(Fragment template, DataSource data)
            throws VEOError {
        String name = "VEOGenerator.startRecord(): ";

        // sanity checks
        if (veo == null) {
            throw new VEOError(name + "VEO has not been started");
        }
        if (state < LOCK_SIG_OUT) {
            throw new VEOError(name
                    + "addLockSignatureBlock() has not been called on this VEO");
        }
        if (state > LOCK_SIG_OUT) {
            throw new VEOError(name
                    + "startRecord() or startFile() has already been called on this VEO");
        }
        if (template == null) {
            throw new VEOError(name + "template is null");
        }
        if (data == null) {
            throw new VEOError(name + "data is null");
        }
        state = REC_STARTED;

        // First document will be 1...
        documentId = 1;

        signing = true;
        outputDataToVeo(cs.encode(contentsRecVEO1a));
        outputDataToVeo(cs.encode(new DateFragment(name).versDateTime(0)));
        outputDataToVeo(cs.encode(contentsRecVEO1b));

        // build record metadata from template and data
        template.finalise(data, this);
    }

    /**
     * *************************************************************
     *
     * End Record
     *
     *************************************************************
     */
    static String contentsRecVEO2
            = "   </vers:Record>\r\n"
            + "  </vers:ObjectContent>\r\n"
            + " </vers:SignedObject>\r\n";

    /**
     * This method completes a record VEO (that is a vers:SignedObject
     * containing a Record VEO. This must be called immediately after the last
     * call to endDocument() and before the call to endVEO().
     * <p>
     * This method will generate a VEOError in the following situations:
     * <ul>
     * <li>if the last vers:Document has not been ended by a call to
     * endDocument</li>
     * <li>if endRecord has already been called on this VEO</li>
     * <li>an error occurred when calculating the signature hash value</li>
     * <li>an error occurred when writing the VEO to the file</li>
     * </ul>
     *
     * @throws VEOError if a fatal error occurs
     */
    public void endRecord() throws VEOError {
        String name = "VEOGenerator.endRecord(): ";

        // sanity checks
        if (veo == null) {
            throw new VEOError(name + "VEO has not been started");
        }
        if (state < DOC_ENDED) {
            throw new VEOError(name
                    + "endDocument() has not been called on this VEO");
        }
        if (state > DOC_ENDED) {
            throw new VEOError(name
                    + "endRecord() has already been called on this VEO");
        }
        state = REC_ENDED;

        outputDataToVeo(cs.encode(contentsRecVEO2));
        signing = false;
    }

    /**
     * *************************************************************
     *
     * Start Document
     *
     *************************************************************
     */
    String contentsRecDocVEO1a = "    <vers:Document vers:id=\"Revision-";
    String contentsRecDocVEO1b = "-Document-";
    String contentsRecDocVEO1c = "\">\r\n"
            + "     <vers:DocumentMetadata>\r\n";
    String contentsRecDocVEO2 = "     </vers:DocumentMetadata>\r\n";

    /**
     * This method starts a Document (that is, vers:Document element) within a
     * Record VEO. Apart from completing the preamble for a vers:Document
     * element, this method completes the vers:DocumentMetadata element from the
     * supplied template and the data.
     * <p>
     * This method must be called after startRecord. It must be followed by one
     * or more calls to addEncoding (to add the encodings to the document), and
     * then by a call to endDocument (to finish the document). After endDocument
     * has been called, startDocument may be called again to add a second
     * document to the record.
     * <p>
     * The vers:id element is generated automatically using revision supplied in
     * startVEO, and the number of documents already added to the VEO.
     * <p>
     * This method will generate a VEOError in the following situations:
     * <ul>
     * <li>if startRecord has not been called</li>
     * <li>if startDocument has already been called on this VEO and endDocument
     * has not been called</li>
     * <li>if endRecord has been called on this VEO</li>
     * <li>if the template or data is null</li>
     * <li>an error occurred when populating the template with data</li>
     * <li>an error occurred when calculating the signature hash value</li>
     * <li>an error occurred when writing the VEO to the file</li>
     * </ul>
     *
     * @param template the Fragment used for the vers:RecordMetadata element
     * @param data	the data used to finalise the template
     * @throws VEOError if a fatal error occurs
     */
    public void startDocument(Fragment template, DataSource data)
            throws VEOError {
        String name = "VEOGenerator.startDocument(): ";

        // sanity checks
        if (veo == null) {
            throw new VEOError(name + "VEO has not been started");
        }
        if (state < REC_STARTED) {
            throw new VEOError(name
                    + "startRecord() has not been called on this VEO");
        }
        if (state == DOC_STARTED || state == ENCODING_ADDED) {
            throw new VEOError(name
                    + "startDocument() has already been called for this document");
        }
        if (state >= REC_ENDED) {
            throw new VEOError(name
                    + "endRecord() has been called on this VEO");
        }
        if (template == null) {
            throw new VEOError(name + "template is null");
        }
        if (data == null) {
            throw new VEOError(name + "data is null");
        }
        state = DOC_STARTED;

        outputDataToVeo(cs.encode(contentsRecDocVEO1a));
        outputDataToVeo(cs.encode(Integer.toString(revisionId)));
        outputDataToVeo(cs.encode(contentsRecDocVEO1b));
        outputDataToVeo(cs.encode(Integer.toString(documentId)));
        outputDataToVeo(cs.encode(contentsRecDocVEO1c));

        // build record metadata from template and data
        template.finalise(data, this);

        outputDataToVeo(cs.encode(contentsRecDocVEO2));

        encodingId = 1;
    }

    /**
     * *************************************************************
     *
     * End Document
     *
     *************************************************************
     */
    String contentsRecDocVEO3 = "    </vers:Document>\r\n";

    /**
     * This method completes a Document (that is, completes the vers:Document
     * element). This must be called after the encoding has been added to the
     * document. It may be followed by either another call to startDocument (to
     * add another document to the record), or by a call to endRecord.
     * <p>
     * This method will generate a VEOError in the following situations:
     * <ul>
     * <li>if no encodings have been added to the document</li>
     * <li>if endDocument has already been called on this Document</li>
     * <li>an error occurred when calculating the signature hash value</li>
     * <li>an error occurred when writing the VEO to the file</li>
     * </ul>
     *
     * @throws VEOError if a fatal error occurs
     */
    public void endDocument() throws VEOError {
        String name = "VEOGenerator.endDocument(): ";

        // sanity checks
        if (veo == null) {
            throw new VEOError(name + "VEO has not been started");
        }
        if (state < ENCODING_ADDED) {
            throw new VEOError(name
                    + "addEncoding() must be called before endDocument()");
        }
        if (state == DOC_ENDED) {
            throw new VEOError(name
                    + "endDocument() has already been called on this document");
        }
        if (state >= REC_ENDED) {
            throw new VEOError(name
                    + "endDocument() cannot be called after endRecord()");
        }
        state = DOC_ENDED;
        outputDataToVeo(cs.encode(contentsRecDocVEO3));

        documentId++;
    }

    /**
     * *************************************************************
     *
     * Add Encoding
     *
     *************************************************************
     */
    String contentsEncVEO1a = "     <vers:Encoding vers:id=\"Revision-";
    String contentsEncVEO1b = "-Document-";
    String contentsEncVEO1c = "-Encoding-";
    String contentsEncVEO1d = "\">\r\n";
    String contentsEncVEO2 = "     </vers:Encoding>\r\n";

    /**
     * This method includes the contents of a file as an Encoding within a
     * Document (that is, constructs a vers:Encoding element). The argument
     * identifies the file to be included.
     * <p>
     * This method must be called within a Document. That is, it must be called
     * after a call to addDocument, but before a call to endDocument. It may be
     * called multiple times within a document to add multiple encodings.
     * <p>
     * The encoding is generated from a template. The templates are found in the
     * directory passed in the VEOGenerator constructor. The type of the file is
     * indicated by the file extension (e.g. The file 'report.pdf' is assumed to
     * contain a PDF file, and the 'pdf.txt' template would be used). If there
     * is no template for a particular extension, the template 'unknown.txt' is
     * used.
     * <p>
     * The vers:id element is generated automatically using revision supplied in
     * startVEO, the document the encoding is being added within, and the number
     * of encodings already added to the VEO.
     * <p>
     * This method will generate a VEOError in the following situations:
     * <ul>
     * <li>if startDocument has not been called</li>
     * <li>if endDocument has been called on this Document</li>
     * <li>if the file is null</li>
     * <li>if there was no format for the file format, and the 'unknown.txt'
     * template did not exist.
     * <li>an error occurred when populating the template with data</li>
     * <li>an error occurred when calculating the signature hash value</li>
     * <li>an error occurred when writing the VEO to the file</li>
     * </ul>
     *
     * @param file	The file to include within the Encoding
     * @throws VEOError if a fatal error occurs
     */
    public void addEncoding(File file) throws VEOError {
        String name = "VEOGenerator.addEncoding(): ";
        EncodingFragment ef;
        String data[] = new String[4];
        ArrayDataSource ads;

        // sanity checks
        if (veo == null) {
            throw new VEOError(name + "VEO has not been started");
        }
        if (state < DOC_STARTED) {
            throw new VEOError(name
                    + "startDocument() must be called before addEncoding()");
        }
        if (state == DOC_ENDED) {
            throw new VEOError(name
                    + "addEncoding() cannot be called after endDocument() unless another call to startDocument() is made");
        }
        if (state > DOC_ENDED) {
            throw new VEOError(name
                    + "addEncoding() cannot be called after endRecord()");
        }
        if (file == null) {
            throw new VEOError(name + "file is null");
        }
        state = ENCODING_ADDED;

        // output vers:Encoding element
        outputDataToVeo(cs.encode(contentsEncVEO1a));
        outputDataToVeo(cs.encode(Integer.toString(revisionId)));
        outputDataToVeo(cs.encode(contentsEncVEO1b));
        outputDataToVeo(cs.encode(Integer.toString(documentId)));
        outputDataToVeo(cs.encode(contentsEncVEO1c));
        outputDataToVeo(cs.encode(Integer.toString(encodingId)));
        outputDataToVeo(cs.encode(contentsEncVEO1d));

        // output 
        try {
            data[0] = file.getCanonicalPath();
            data[1] = Integer.toString(revisionId);
            data[2] = Integer.toString(documentId);
            data[3] = Integer.toString(encodingId);
        } catch (IOException ioe) {
            throw new VEOError(name
                    + "attempting to get path of included file gave error: "
                    + ioe.getMessage());
        }
        ads = new ArrayDataSource(data);
        ef = new EncodingFragment(name, 1);
        ef.finalise(ads, this);

        // output vers:Encoding end element
        outputDataToVeo(cs.encode(contentsEncVEO2));

        encodingId++;
    }

    /**
     * *************************************************************
     *
     * Add Simple Record
     *
     *************************************************************
     */
    /**
     * This method is a short cut for making a simple VEO that consists of just
     * one document containing one record. The method is equivalent to the
     * following sequence of calls:
     * <pre>
     *	vg.startRecord(rData, tds);
     *	vg.startDocument(dData, tds);
     *	vg.addEncoding(new File(tds.getColumn(3)));
     *	vg.endDocument();
     * 	vg.endRecord();
     * </pre> Note that the one line of DataSource is used to populate both the
     * record and document matadata, and it also contains the file to be
     * included in the encoding as the 3rd column of the DataSource. (If this
     * method is being called from VeoCreator, the first column will be the flag
     * 's', the second column will be the name of the VEO, and the third column
     * will be the file to be included in the VEO. The remaining columns will be
     * used as required to populate the metadata.)
     * <p>
     * Either this method, startRecord or startFile must be called immediately
     * after the addLockSignatureBlock(). It may be called only once per VEO.
     * <p>
     * This method will generate a VEOError in the following situations:
     * <ul>
     * <li>if addLockSignatureBlock has not been called on this VEO</li>
     * <li>if startRecord, addSimpleRecord or addFile has been called on this
     * VEO</li>
     * <li>if either template or data is null</li>
     * <li>an error occurred when populating the template with data</li>
     * <li>an error occurred when calculating the signature hash value</li>
     * <li>an error occurred when writing the VEO to the file</li>
     * </ul>
     *
     * @param recTemplate	the template for the record metadata
     * @param docTemplate	the template for the document metadata
     * @param data
     * @throws VEOError if a fatal error occurs
     */
    public void addSimpleRecord(Fragment recTemplate, Fragment docTemplate, DataSource data)
            throws VEOError {
        ArrayDataSource ads;
        EncodingFragment ef;
        String s[] = new String[4];
        String name = "VEOGenerator.addSimpleRecord(): ";

        // sanity checks
        if (veo == null) {
            throw new VEOError(name + "VEO has not been started");
        }
        if (recTemplate == null) {
            throw new VEOError(name + "record template is null");
        }
        if (docTemplate == null) {
            throw new VEOError(name + "document template is null");
        }
        if (data == null) {
            throw new VEOError(name + "data is null");
        }
        if (state < LOCK_SIG_OUT) {
            throw new VEOError(name
                    + "addLockSignatureBlock() has not been called on this VEO");
        }
        if (state >= REC_STARTED && state <= REC_ENDED) {
            throw new VEOError(name
                    + "addSimpleRecord() cannot be called when already creating a Record VEO");
        }
        if (state == REC_ENDED) {
            throw new VEOError(name
                    + "addSimpleRecord() cannot be called twice");
        }
        if (state == VEO_ENDED) {
            throw new VEOError(name
                    + "endVEO() has already been called");
        }

        signing = true;

        // start record
        outputDataToVeo(cs.encode(contentsRecVEO1a));
        outputDataToVeo(cs.encode(new DateFragment(name).versDateTime(0)));
        outputDataToVeo(cs.encode(contentsRecVEO1b));

        // build record metadata from template and data
        recTemplate.finalise(data, this);

        // start document
        outputDataToVeo(cs.encode(contentsRecDocVEO1a));
        outputDataToVeo(cs.encode("1"));
        outputDataToVeo(cs.encode(contentsRecDocVEO1b));
        outputDataToVeo(cs.encode("1"));
        outputDataToVeo(cs.encode(contentsRecDocVEO1c));

        // build record metadata from template and data
        docTemplate.finalise(data, this);

        // transition from doc to encoding
        outputDataToVeo(cs.encode(contentsRecDocVEO2));

        // start encoding
        outputDataToVeo(cs.encode(contentsEncVEO1a));
        outputDataToVeo(cs.encode("1"));
        outputDataToVeo(cs.encode(contentsEncVEO1b));
        outputDataToVeo(cs.encode("1"));
        outputDataToVeo(cs.encode(contentsEncVEO1c));
        outputDataToVeo(cs.encode("1"));
        outputDataToVeo(cs.encode(contentsEncVEO1d));

        // build encoding
        try {
            s[0] = (new File(data.getColumn(3))).getCanonicalPath();
            s[1] = "1";
            s[2] = "1";
            s[3] = "1";
        } catch (IOException ioe) {
            throw new VEOError(name
                    + "attempting to get path of included file gave error: "
                    + ioe.getMessage());
        }
        ads = new ArrayDataSource(s);
        ef = new EncodingFragment(name, 1);
        ef.finalise(ads, this);

        // output vers:Encoding end element
        outputDataToVeo(cs.encode(contentsEncVEO2));

        // end document
        outputDataToVeo(cs.encode(contentsRecDocVEO3));

        // end record
        outputDataToVeo(cs.encode(contentsRecVEO2));

        signing = false;
        state = REC_ENDED;
    }

    /**
     * *************************************************************
     *
     * Add File
     *
     *************************************************************
     */
    static String contentsFileVEO1a
            = "  <vers:SignedObject vers:VEOVersion=\"2.0\">\r\n"
            + "  <vers:ObjectMetadata>\r\n"
            + "   <vers:ObjectType>File</vers:ObjectType>\r\n"
            + "   <vers:ObjectTypeDescription>\r\n"
            + "This object contains a file; that is a collection of related records.\r\n"
            + "   </vers:ObjectTypeDescription>\r\n"
            + "   <vers:ObjectCreationDate>\r\n";
    static String contentsFileVEO1b
            = "</vers:ObjectCreationDate>\r\n"
            + "  </vers:ObjectMetadata>\r\n"
            + "  <vers:ObjectContent>\r\n"
            + "   <vers:File>\r\n";
    static String contentsFileVEO2
            = "   </vers:File>\r\n"
            + "  </vers:ObjectContent>\r\n"
            + " </vers:SignedObject>\r\n";

    /**
     * This method generates a File VEO. Apart from completing the preamble for
     * a vers:SignedObject, this method completes the vers:FileMetadata element
     * from the supplied template and the data.
     * <p>
     * The template contains both the variable and the fixed part of the
     * vers:RecordMetadata element. The fixed part is simply output, but the
     * variable part is filled using the data from the DataSource.
     * <p>
     * Either this method, startRecord, or addSimpleRecord must be called
     * immediately after the addLockSignatureBlock(). It may be called only once
     * per VEO.
     * <p>
     * This method will generate a VEOError in the following situations:
     * <ul>
     * <li>if addLockSignatureBlock has not been called on this VEO</li>
     * <li>if startRecord, addSimpleRecord or addFile has already been called on
     * this VEO</li>
     * <li>if the template or data is null</li>
     * <li>an error occurred when populating the template with data</li>
     * <li>an error occurred when calculating the signature hash value</li>
     * <li>an error occurred when writing the VEO to the file</li>
     * </ul>
     *
     * @param template the Fragment used for the vers:RecordMetadata element
     * @param data	the data used to finalise the template
     * @throws VEOError if a fatal error occurs
     */
    public void addFile(Fragment template, DataSource data)
            throws VEOError {
        String name = "VEOGenerator.addFile(): ";

        // sanity checks
        if (veo == null) {
            throw new VEOError(name + "VEO has not been started");
        }
        if (template == null) {
            throw new VEOError(name + "template is null");
        }
        if (data == null) {
            throw new VEOError(name + "data is null");
        }
        if (state < LOCK_SIG_OUT) {
            throw new VEOError(name
                    + "addLockSignatureBlock() has not been called on this VEO");
        }
        if (state >= REC_STARTED && state <= REC_ENDED) {
            throw new VEOError(name
                    + "addFile() cannot be called when creating a Record VEO");
        }
        if (state == FILE_ENDED) {
            throw new VEOError(name
                    + "addFile() cannot be called twice");
        }
        if (state == VEO_ENDED) {
            throw new VEOError(name
                    + "endVEO() has already been called");
        }
        state = FILE_ENDED;

        signing = true;
        outputDataToVeo(cs.encode(contentsFileVEO1a));
        outputDataToVeo(cs.encode(new DateFragment(name).versDateTime(0)));
        outputDataToVeo(cs.encode(contentsFileVEO1b));

        // build record metadata from template and data
        template.finalise(data, this);

        outputDataToVeo(cs.encode(contentsFileVEO2));
        signing = false;
    }

    /**
     * Write data to a file channel representing a VEO. The data to be written
     * is contained within a byte array. Note that the encoding to UTF-8 must
     * have already occurred at this point.
     * <p>
     * If a signature is being calculated (i.e. between the calls to startRecord
     * and endRecord, or during the addFile method), the data is also written to
     * the active signatures.
     * <p>
     * <i>This method should not be called by programs constructing VEOs. It is
     * provided for use by the other classes in this package</i>
     * <p>
     * <ul>
     * This method will generate a VEOError in the following situations:
     * <li>an error occurred when calculating the signature hash value</li>
     * <li>an error occurred when writing the VEO to the file</li>
     * </ul>
     *
     * @param b the byte array to be written to the VEO
     * @param offset the offset of the first byte in this array to be written
     * @param length the length of the subarray to be written
     * @throws VEOError if a fatal error occurs
     */
    public void outputDataToVeo(byte[] b, int offset, int length)
            throws VEOError {
        String name = "Fragment.outputDataToVeo(): ";

        outputDataToVeo(ByteBuffer.wrap(b, offset, length));
    }

    /**
     * Write data to a file channel representing a VEO. The data to be written
     * is contained within a byte array. Note that the encoding to UTF-8 must
     * have already occurred at this point.
     * <p>
     * If a signature is being calculated (i.e. between the calls to startRecord
     * and endRecord, or during the addFile method), the data is also written to
     * the active signatures.
     * <p>
     * <i>This method should not be called by programs constructing VEOs. It is
     * provided for use by the other classes in this package</i>
     * <p>
     * <ul>
     * This method will generate a VEOError in the following situations:
     * <li>an error occurred when calculating the signature hash value</li>
     * <li>an error occurred when writing the VEO to the file</li>
     * </ul>
     *
     * @param b the byte array to be written to the VEO
     * @throws VEOError if a fatal error occurs
     */
    public void outputDataToVeo(byte[] b)
            throws VEOError {
        String name = "Fragment.outputDataToVeo(): ";
        int i, j;
        Signature s;

        try {

            // write data to VEO
            veo.write(ByteBuffer.wrap(b));

            // write data to the signature calculations (if calculating signatures)
            // note that tabs, line feeds, carriage returns & spaces must be
            // suppressed
            if (signing) {
                for (i = 0; i < signatures.size(); i++) {
                    s = signatures.get(i);
                    for (j = 0; j < b.length; j++) {
                        if (b[j] == 0x09 || b[j] == 0x0a
                                || b[j] == 0x0d || b[j] == 0x20) {
                            continue;
                        }
                        s.update(b[j]);
                        // use the following when it is necessary to output the
                        // byte stream being signed
                        /*
                        if (i==0) {
                            for (int k=0; k<b.length; k++){
                                fw.write(b[k]);
                            }
                        }
                        */
                    }
                }
            }

            // handle exceptions
        } catch (IOException ioe) {
            throw new VEOError(name + "failed writing to veo " + ioe.getMessage());
        } catch (ArrayIndexOutOfBoundsException aoobe) {
            // ignore
        } catch (SignatureException se) {
            throw new VEOError(name + "failed updating signature" + se.getMessage());
        }
    }

    /**
     * Write data to a file channel representing a VEO. The data to be written
     * is contained within a byte buffer. Note that the encoding to UTF-8 must
     * have already occurred at this point.
     * <p>
     * If a signature is being calculated (i.e. between the calls to startRecord
     * and endRecord, or during the addFile method), the data is also written to
     * the active signatures.
     * <p>
     * <i>This method should not be called by programs constructing VEOs. It is
     * provided for use by the other classes in this package</i>
     * <p>
     * <ul>
     * This method will generate a VEOError in the following situations:
     * <li>an error occurred when calculating the signature hash value</li>
     * <li>an error occurred when writing the VEO to the file</li>
     * </ul>
     *
     * @param bb the byte array to be written to the VEO
     * @throws VEOError if a fatal error occurs
     */
    public void outputDataToVeo(ByteBuffer bb)
            throws VEOError {
        String name = "Fragment.outputDataToVeo(): ";
        byte b;
        int i, j;
        Signature s;

        try {

            // write data to VEO
            veo.write(bb);

            // write data to the signature calculations (if calculating signatures)
            // note that tabs, line feeds, carriage returns & spaces must be
            // suppressed
            if (signing) {
                for (i = 0; i < signatures.size(); i++) {
                    s = signatures.get(i);
                    for (j = 0; j < bb.limit(); j++) {
                        b = bb.get(j);
                        if (b == 0x09 || b == 0x0a
                                || b == 0x0d || b == 0x20) {
                            continue;
                        }
                        s.update(b);
                        if (i==0) {
                        fw.write(b); }
                    }
                }
            }

            // handle exceptions
        } catch (IOException ioe) {
            throw new VEOError(name
                    + "failed writing to veo " + ioe.getMessage());
        } catch (ArrayIndexOutOfBoundsException aoobe) {
            // ignore
        } catch (IndexOutOfBoundsException ioobe) {
            System.err.println(name
                    + "Getting from ByteBuffer: " + ioobe.getMessage());
        } catch (SignatureException se) {
            throw new VEOError(name
                    + "failed updating signature" + se.getMessage());
        }
    }

    /**
     * Gets the File containing the template for the requested file type.
     * <p>
     * <i>This method should not be called by programs constructing VEOs. It is
     * provided for use by the other classes in this package</i>
     * <p>
     * @param filetype the file extension of the file to be put in the encoding
     * @return
     */
    public Fragment getEncodingTemplate(String filetype) {
        if (encTemplates == null) {
            return null;
        }
        return (Fragment) encTemplates.get(filetype);
    }

    /**
     * Gets the current sequence number of the VEO being constructed.
     * <p>
     * <i>This method should not be called by programs constructing VEOs. It is
     * provided for use by the other classes in this package</i>
     *
     * @return
     */
    public int getSeqNo() {
        return seqNo;
    }

    /**
     * Test main program
     *
     * @param args
     */
    public static void main(String args[]) {
        VEOGenerator vg;// the representation of the VEO
        Fragment rData;	// template for record metadata
        Fragment dData;	// template for document metadata
        TableDataSource tds;
        PFXUser signer;
        File veo;
        int seqNo;

        try {
            vg = new VEOGenerator(new File("TestData/encDirectory"), args);
            signer = new PFXUser("TestData/signer.pfx", "Ag0nc1eS");
            rData = Fragment.parseTemplate(new File("TestData/rMeta.txt"), args);
            dData = Fragment.parseTemplate(new File("TestData/dMeta.txt"), args);
            tds = new TableDataSource(new File("TestData/dataFile.txt"));

            seqNo = 1;
            while (!tds.isAtEnd()) {
                veo = new File("TestData/" + Integer.toString(seqNo) + ".veo");

                // start VEO, adding signature and lock signature blocks
                vg.startVEO(veo, seqNo, 1);
                vg.addSignatureBlock(signer);
                vg.addLockSignatureBlock(1, signer);

                // start record, including documents and and encodings
                vg.startRecord(rData, tds);
                tds.getNextRow();
                while (tds.getRowType() == DataSource.DS_Document) {
                    vg.startDocument(dData, tds);
                    tds.getNextRow();
                    while (tds.getRowType() == DataSource.DS_Encoding) {
                        vg.addEncoding(new File(tds.getColumn(2)));
                        tds.getNextRow();
                    }
                    vg.endDocument();
                }
                vg.endRecord();
                vg.endVEO();
                seqNo++;
            }
        } catch (VEOError ve) {
            System.err.println(ve.getMessage());
        }
    }
}
