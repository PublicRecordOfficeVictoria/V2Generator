package VEOGenerator;

/***************************************************************
 *
 * V E O   E R R O R
 *
 * This class represents an error produced when constructing a VEO
 *
 * Andrew Waugh (andrew.waugh@dvc.vic.gov.au)
 * Copyright 2006 PROV
 *
 **************************************************************/

/**
 * This class represents an error produced when constructing a VEO.
 * It is simply a wrapper around Exception and adds no additional
 * functionality.
 */
public class VEOError extends Exception {

/**
 * Construct a new VEOError with appropriate error message
 *
 * @param s the error message to return
 */
public VEOError(String s) {
	super(s);
}
}
