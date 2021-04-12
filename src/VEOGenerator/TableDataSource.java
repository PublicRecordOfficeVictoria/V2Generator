package VEOGenerator;

import VERSCommon.VEOError;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

/**
 * *************************************************************
 *
 * T A B L E D A T A S O U R C E
 *
 * This class represents data obtained from a tab separated file.
 *
 * Version 1.1 20090516 Added close to allow template file to be deleted 1.2
 * 20100809 Removed encoding of XML characters - now done when outputting data
 *
 * Andrew Waugh (andrew.waugh@dvc.vic.gov.au) Copyright 2006 PROV
 *
 *************************************************************
 */

/**
 * This class represents a DataSource that is a file containing tab seperated
 * rows. Each row may represent the data for record metadata, file metadata,
 * document metadata or an encoding. These are indicated by the value of the
 * first column: 'r' for record metadata, 'f' for file metadata, 'd' for
 * document metadata, 'e' for an encoding, and 's' for simple record.
 */
public class TableDataSource extends DataSource {

    BufferedReader input;
    String charset = "UTF-8";
    FileInputStream fis;
    InputStreamReader isr;

    /**
     * Construct a new TableDataSource from the specified file.
     *
     * @param table the file containing the data.
     * @throws VEOError
     */
    public TableDataSource(File table) throws VEOError {
        super();

        String s;
        String name = "TableDataSource(): ";

        atEnd = false;

        // open table
        try {
            s = table.getCanonicalPath();
        } catch (IOException ioe) {
            throw new VEOError("could not get filepath of table");
        }
        try {
            fis = new FileInputStream(table);
            isr = new InputStreamReader(fis, charset);
            input = new BufferedReader(isr);
        } catch (FileNotFoundException fnfe) {
            throw new VEOError(name + "input file '" + s + "' not found");
        } catch (UnsupportedEncodingException uee) {
            atEnd = true;
            return;
        }

        // read first line
        readLine();
    }

    /**
     * Close the Data Source
     *
     * @throws VEOGenerator.VEOError
     */
    public void close() throws VEOError {
        String name = "TableDataSource.close(): ";

        try {
            input.close();
            isr.close();
            fis.close();
        } catch (IOException ioe) {
            throw new VEOError(name + " input file failed to close");
        }
    }

    @Override
    public int getNextRow() {
        return readLine();
    }

    /**
     * Read Line from input file, splitting it up into columns Columns are
     * separated by tabs, and lines by new line or carriage return character.
     *
     * @return the type of line found.
     */
    private int readLine() {
        String s;
        char c;

        // return immediately if at end
        if (atEnd) {
            return DS_AtEnd;
        }

        // read one line of table, separating at tabs and end of line
        // ignore lines in which the first character is a '!'
        do {
            try {
                s = input.readLine();
            } catch (IOException ioe) {
                s = null;
            }

            // if at end of file
            if (s == null) {
                atEnd = true;
                rowType = DS_AtEnd;
                return rowType;
            }
        } while (s.length() == 0 && s.charAt(0) != '!');

        // translate &, <, and > into xml equivalents
        // s = translateSpecialCharacters(s);
        // split into columns
        column = s.split("\t");

        // look at 1st column to see if this is a record (value 'r'), file ('f')
        // document ('d'),  encoding ('e'), or simple record (value 's').
        c = (column[0].trim().toLowerCase()).charAt(0);
        switch (c) {
            case 'f':
                rowType = DS_File;
                break;
            case 'r':
                rowType = DS_Record;
                break;
            case 'd':
                rowType = DS_Document;
                break;
            case 'e':
                rowType = DS_Encoding;
                break;
            case 's':
                rowType = DS_SimpleRecord;
                break;
            default:
                rowType = DS_AtEnd;
                break;
        }
//int m;
//for (m=0;m<column.length;m++)
//	System.out.println("Col "+m+" = '"+column[m]+"'");
        return rowType;
    }
}
