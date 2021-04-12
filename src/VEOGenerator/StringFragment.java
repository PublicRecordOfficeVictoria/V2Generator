package VEOGenerator;

import VERSCommon.VEOError;

/**
 * *************************************************************
 *
 * S T R I N G F R A G M E N T
 *
 * This class represents a string fragment, that is a sequence of text that will
 * be included in the VEO verbatim.
 *
 * Andrew Waugh (andrew.waugh@dvc.vic.gov.au) Copyright 2006 PROV
 *
 *************************************************************
 */
/**
 * This fragment represents static content which will simply be output to the
 * VEO with no further processing.
 */
public class StringFragment extends Fragment {

    String string;

    /**
     * Constructor
     *
     * @param location location of the fragment that generated this substitution
     * @param string the static content to be written to the VEO
     */
    public StringFragment(String location, String string) {
        super(location);
        this.string = string;
    }

    /**
     * Output the static string to the VEO.
     *
     * @throws VEOError
     */
    @Override
    public void finalise(DataSource data, VEOGenerator veo)
            throws VEOError {
        Fragment f;

        // output to VEO
        veo.outputDataToVeo(cs.encode(string));

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
        s = "String Fragment: '" + string + "'\n";
        if (next != null) {
            s += next.toString();
        }
        return s;
    }
}
