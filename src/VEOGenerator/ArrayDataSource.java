package VEOGenerator;

/***************************************************************
 *
 * A R R A Y   D A T A   S O U R C E
 *
 * This class represents a data source with the data taken from
 * an array of strings.
 *
 * Andrew Waugh (andrew.waugh@dvc.vic.gov.au)
 * Copyright 2006 PROV
 *
 **************************************************************/

/**
 * This class is a DataSource in which the data is taken from
 * an array of Strings. Only one row can be present in the data
 * source.
 */
public class ArrayDataSource extends DataSource {

/**
 * Default constructor
 * @param data
 */
public ArrayDataSource(String[] data) {
	super(data);
}

@Override
public int getNextRow() {
	return DS_AtEnd;
}
}
