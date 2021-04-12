package VEOGenerator;

import VERSCommon.VEOError;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;

/**
 * *************************************************************
 *
 * F R A G M E N T
 *
 * This class represents a fragment of a VEO.
 *
 * Version 1.1 20100809 Added new type of substitution - column-xml
 *
 * Andrew Waugh (andrew.waugh@dvc.vic.gov.au) Copyright 2006 PROV
 *
 *************************************************************
 */

/**
 * This class represents a fragment of a VEO. This might be a piece of static
 * text that is identical for all constructed VEOs, or it might be a piece of
 * dynamic content that will change, such as the current date and time.
 * <p>
 * A sequence of such fragments is called a template. Templates represent
 * complete sections of the VEO (e.g. the contents of a vers:RecordMetdata
 * element).
 * <p>
 * There are two steps in generating a VEO using fragments.
 * <p>
 * The first step is to construct a template (a list of fragments). This
 * contains holes into which dynamic content will be added and externally
 * referenced content (e.g. files) are inserted. The second step is to
 * 'finalise' the template. This resolves all the dynamic content (e.g. the
 * fragment saying 'insert current date/time' is replaced by the actual
 * date/time). This is then written to the actual VEO.
 * <p>
 * Templates may be constructed in two ways. Instances of the Fragment classes
 * may be constructed and manually linked together to form a template.
 * Alternatively, a text file containing a representation of the template can be
 * parsed to produce the appropriate list of Fragments.
 */
abstract public class Fragment {

    /**
     * Link to next fragment in the list
     */
    public Fragment next;	// link to the next fragment
    Charset cs;		// character set for converting strings to UTF-8
    /**
     * Location where this fragment (file/line) where this substitution was
     * generated.
     */
    protected String location;// location where this substitution was found

    /**
     * Constructed an uninstantiated fragment.
     *
     * @param location
     */
    public Fragment(String location) {
        next = (Fragment) null;
        try {
            cs = Charset.forName("UTF-8");
        } catch (IllegalCharsetNameException icne) {
            System.err.println("Fragment(): 'UTF-8' is an illegal charset name!");
        } catch (UnsupportedCharsetException icne) {
            System.err.println("Fragment(): 'UTF-8' is not a supported charset!");
        }
        this.location = location;
    }

    /**
     * This method is a factory that parses a file containing the template and
     * builds a list of Fragments.
     * <p>
     * A template is a text file that contains static text (typically XML) and
     * substitutions. The static text is copied explicitly into each VEO
     * generated. The substitutions represent dynamic content (e.g. the current
     * date and time). Each substitution is replaced by actual text when the VEO
     * is generated.
     * <p>
     * Valid substitutions are:<br>
     * <ul>
     * <li>
     * $$ date $$ - substitute the current date and time in VERS format</li>
     * <li>
     * $$ encoding|e [column] &lt;x&gt; $$ - create an encoding from the file
     * named in column &lt;x&gt;</li>
     * <li>
     * $$ argument &lt;&lt;x&gt; $$ - substitute the contents of command line
     * argument &lt;x&gt;</li>
     * <li>
     * $$ sequenceno $$ - substitute the current sequence number</li>
     * <li>
     * $$ [column] &lt;&gt;x&gt; $$ - substitute the contents of column
     * &lt;x&gt; encoding XML characters</li>
     * <li>
     * $$ column-xml &lt;&gt;x&gt; $$ - substitute the contents of column
     * &lt;x&gt; without encoding XML characters</li>
     * <li>
     * $$ file binary|utf8|xml [column] &lt;x&gt; $$ - substitute the contents
     * of column &lt;x&gt;</li>
     * </ul>
     * <p>
     * The parse will not stop if an error is encountered in parsing the
     * template. If a syntax error is encountered, a message is printed on
     * standard out and the parse continues.
     * <p>
     * This method will generate a VEOError in the following situations:
     * <ul>
     * <li>the template file cannot be opened, is a directory, or the cannonical
     * path cannot be generated.</li>
     * <li>an IO error occurred when reading a template file.</li>
     * </ul>
     *
     * @param template file containing the template
     * @param args	An array of strings which is used to populate command line
     * argument substitutions in the encoding templates.
     * @return Fragment the head of the internal representation
     * @throws VEOError if a fatal error occurs
     *
     */
    static public Fragment parseTemplate(File template, String[] args) throws VEOError {
        FileReader fr;
        LineNumberReader lnr;
        Fragment fs, fe, fn;
        int c, j;
        StringBuffer sb;
        String s, filename, location, tag = "$$";
        boolean stringFrag;
        String name = "Fragment.parseTemplate(): ";

        // check template file is not a directory
        if (template.isDirectory()) {
            try {
                s = template.getCanonicalPath();
            } catch (IOException ioe) {
                throw new VEOError(name
                        + "cannot get the canonical path of template file: "
                        + ioe.getMessage());
            }
            throw new VEOError(
                    "Template file '" + s + "' is a directory not a .txt file"
                    + "(" + name + ")");
        }
        // open file & instantiate a line number reader with it
        try {
            fr = new FileReader(template);
        } catch (FileNotFoundException fnfe) {
            try {
                s = template.getCanonicalPath();
            } catch (IOException ioe) {
                throw new VEOError(name
                        + "cannot get the canonical path of template file: "
                        + ioe.getMessage());
            }
            throw new VEOError(
                    "Template file '" + s + "' does not exist"
                    + "(" + name + ")");
        }
        lnr = new LineNumberReader(fr);
        filename = template.getName();

        // go through each character in file. Put the characters in a string
        // buffer until a substitution flag is found ('$$'). If this is the
        // first substitution flag, the found characters are literal text.
        // If it is the second, the found characters are a substitution command
        // which is parsed. This continues (odd flags indicate literal
        // text, even substitutions.
        fs = null;
        fe = null;
        j = 0;
        stringFrag = true;
        sb = new StringBuffer();
        c = 0;
        while (c != -1) {
            try {
                c = lnr.read();
            } catch (IOException ioe) {
                throw new VEOError(name
                        + "IO Error reading template file " + filename
                        + " Line " + lnr.getLineNumber() + ". Error: "
                        + ioe.getMessage());
            }

            // if not at end of file, append char to buffer and check if
            // part of substitution tag
            if (c != -1) {
                sb.append((char) c);
                if (c == tag.charAt(j)) {
                    j++;
                } else {
                    j = 0;
                }
            }

            // found a start or end substitution tag or at end of file...
            if (j == tag.length() || c == -1) {

                // delete tag from end of string buffer
                if (j == tag.length()) {
                    sb.setLength(sb.length() - tag.length());
                }
                j = 0;

                location = "template " + filename + " (around line " + (lnr.getLineNumber() + 1) + ") ";

                // if found a start tag put characters out as
                // string fragment and next fragment is a substitution
                if (stringFrag) {
                    fn = new StringFragment(location, sb.toString());
                    sb.setLength(0);
                    stringFrag = false;

                    // found an end tag so work out what
                    // substitution we got, and next fragment is a string
                } else {
                    fn = parseSubstitution(location, sb, args);
                    sb.setLength(0);
                    stringFrag = true;
                }

                // append new fragement (if any) to list...
                if (fn != null) {
                    if (fs == null) {
                        fe = fn;
                        fs = fe;
                    } else {
                        fe.next = fn;
                        fe = fe.next;
                    }
                }
            }
        }

        // close template
        try {
            fr.close();
        } catch (IOException ioe) {
            /* ignore */ }

        // return list of fragments from template
        return fs;
    }

    /**
     * Parse substitution for commands
     *
     * This method parses a substitution to identify what type it is. The method
     * returns a Fragment containing the substitution. If no substitution is
     * found (or an error occurs) a null Fragment is returned.
     *
     * Valid substitutions are described in parseTemplate().
     *
     * @param errorLoc - the location (file/line) in which the substitution is
     * found
     * @param sb - the StringBuffer containing the substitution
     * @param args - an array of Strings used to construct the argument
     * substitution
     * @returns a Fragment, or null if an error occurred
     *
     */
    static private Fragment parseSubstitution(String errorLoc, StringBuffer sb, String[] args) {
        String synerror;
        String[] t, tokens;
        int i, j, col;

        synerror = "Syntax error in " + errorLoc + "\n  ";
        // split substitution into tokens around spaces
        t = sb.toString().split(" ");

        // remove empty tokens
        j = 0;
        for (i = 0; i < t.length; i++) {
            if (t[i] != null && !t[i].equals("")) {
                j++;
            }
        }
        if (j == 0) {
            System.out.println(synerror + "Empty substitution (e.g. '$$ $$') '" + sb.toString() + "'");
            return null;
        }
        tokens = new String[j];
        j = 0;
        for (i = 0; i < t.length; i++) {
            if (t[i] != null && !t[i].equals("")) {
                tokens[j] = t[i];
                j++;
            }
        }

        /*
	for (i=0; i<tokens.length; i++)
		System.out.print("Token '"+tokens[i]+"', ");
	System.out.println("");
         */
        // process tokens
        i = 0;
        col = 0;

        // substitute the current date/time
        if (tokens[i].toLowerCase().equals("date")) {
            return new DateFragment(errorLoc);
        } // generate an encoding from the contents of specified column
        else if (tokens[i].toLowerCase().equals("e")
                || tokens[i].toLowerCase().equals("encoding")) {
            i++;
            if (i == tokens.length) {
                System.out.println(synerror + "column reference is missing from encoding substitution '" + sb.toString() + "'");
                return null;
            }
            if (tokens[i].toLowerCase().equals("column")) {
                i++;
                if (i == tokens.length) {
                    System.out.println(synerror + "column reference is missing in encoding substitution '" + sb.toString() + "'");
                    return null;
                }
            }
            try {
                col = Integer.parseInt(tokens[i]);
            } catch (NumberFormatException nfe) {
                System.out.println(synerror + "column reference in encoding substitution '" + sb.toString() + "' is invalid (must be a positive integer)");
                return null;
            }
            if (col < 1) {
                System.out.println(synerror + "column reference in encoding substitution '" + sb.toString() + "' is invalid (must be a positive integer)");
                return null;
            }
            return new EncodingFragment(errorLoc, col);
        } // include the contents of a file named by a specified column
        else if (tokens[i].toLowerCase().equals("file")) {
            i++;
            if (i == tokens.length) {
                System.out.println(synerror + "file type reference is missing in file substitution '" + sb.toString() + "'");
                return null;
            }
            switch (tokens[i].toLowerCase()) {
                case "binary":
                    j = FileFragment.TYPE_BINARY;
                    break;
                case "utf8":
                    j = FileFragment.TYPE_UTF8;
                    break;
                case "xml":
                    j = FileFragment.TYPE_XML_UTF8;
                    break;
                default:
                    System.out.println(synerror + "invalid file type reference in file substitution '" + sb.toString() + "' (must be 'binary', 'utf8', or 'xml')");
                    return null;
            }
            i++;
            if (i == tokens.length) {
                System.out.println(synerror + "column reference is missing in file substitution '" + sb.toString() + "'");
                return null;
            }
            if (tokens[i].toLowerCase().equals("column")) {
                i++;
                if (i == tokens.length) {
                    System.out.println(synerror + "column reference si missing in file substitution '" + sb.toString() + "'");
                    return null;
                }
            }
            try {
                col = Integer.parseInt(tokens[i]);
            } catch (NumberFormatException nfe) {
                System.out.println(synerror + "column reference in file substitution '" + sb.toString() + "' is invalid (must be a positive integer)");
                return null;
            }
            if (col < 1) {
                System.out.println(synerror + "column reference in file substitution '" + sb.toString() + "' is invalid (must be a positive integer)");
                return null;
            }
            return new FileFragment(errorLoc, col, j);
        } // include a command line argument
        else if (tokens[i].toLowerCase().equals("argument")) {
            i++;
            if (i == tokens.length) {
                System.out.println(synerror + "argument reference is missing in argument substitution '" + sb.toString() + "'");
                return null;
            }
            try {
                col = Integer.parseInt(tokens[i]);
            } catch (NumberFormatException nfe) {
                System.out.println(synerror + "argument reference in argument substitution '" + sb.toString() + "' is invalid (must be a positive integer)");
                return null;
            }
            if (col < 0) {
                System.out.println(synerror + "argument reference in argument substitution '" + sb.toString() + "' is invalid (must be a positive integer)");
                return null;
            }
            if (col >= args.length) {
                System.out.println(synerror + "argument reference in argument substitution '" + sb.toString() + "' is invalid (must be less than the number of command line arguments)");
                return null;
            }
            return new StringFragment(errorLoc, args[col]);
        } // include a sequence number 
        else if (tokens[i].toLowerCase().equals("sequenceno")) {
            return new SequenceNoFragment(errorLoc);
        } // include a column replacement 
        else if (tokens[i].toLowerCase().equals("column")) {
            i++;
            if (i == tokens.length) {
                System.out.println(synerror + "missing column reference in column substitution '" + sb.toString() + "'");
                return null;
            }
            try {
                col = Integer.parseInt(tokens[i]);
            } catch (NumberFormatException nfe) {
                System.out.println(synerror + "column reference in column substitution '" + sb.toString() + "' is invalid (must be a positive integer)");
                return null;
            }
            if (col < 1) {
                System.out.println(synerror + "column reference in column substitution '" + sb.toString() + "' is invalid (must be a positive integer)");
                return null;
            }
            return new ColumnFragment(errorLoc, col);
        } // include a column replacement without XML encoding
        else if (tokens[i].toLowerCase().equals("column-xml")) {
            i++;
            if (i == tokens.length) {
                System.out.println(synerror + "missing column reference in column substitution '" + sb.toString() + "'");
                return null;
            }
            try {
                col = Integer.parseInt(tokens[i]);
            } catch (NumberFormatException nfe) {
                System.out.println(synerror + "column reference in column substitution '" + sb.toString() + "' is invalid (must be a positive integer)");
                return null;
            }
            if (col < 1) {
                System.out.println(synerror + "column reference in column substitution '" + sb.toString() + "' is invalid (must be a positive integer)");
                return null;
            }
            return new ColumnXMLFragment(errorLoc, col);
        }

        // by default assume simple column
        try {
            col = Integer.parseInt(tokens[i]);
        } catch (NumberFormatException nfe) {
            System.out.println(synerror + "column reference in column substitution '" + sb.toString() + "' is invalid (must be a positive integer)");
            return null;
        }
        if (col < 1) {
            System.out.println(synerror + "column reference in column substitution '" + sb.toString() + "' is invalid (must be a positive integer)");
            return null;
        }
        return new ColumnFragment(errorLoc, col);
    }

    /**
     * Append a fragment to the end of this fragment.
     *
     * @param f	fragement to append
     */
    public void appendToEnd(Fragment f) {
        if (next == null) {
            next = f;
        } else {
            next.appendToEnd(f);
        }
    }

    /**
     * Resolve any dynamic content and output the contents to the VEO.
     *
     * @param data the source of data to resolve any dynamic content
     * @param veo the VEO being produced
     * @throws VEOError
     */
    abstract public void finalise(DataSource data, VEOGenerator veo)
            throws VEOError;

    /**
     * Output a list of fragments as a string.
     *
     * @return a string representation of a list of fragments
     */
    @Override
    abstract public String toString();
}
