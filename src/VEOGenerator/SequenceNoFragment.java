package VEOGenerator;

/**
 * *************************************************************
 *
 * S E Q U E N C E N O F R A G M E N T
 *
 * This abstract class represents a dynamic fragment (i.e. a hole in the
 * template) that will be filled by the sequence number of the VEO.
 *
 * Andrew Waugh (andrew.waugh@dvc.vic.gov.au) Copyright 2006 PROV
 *
 *************************************************************
 */
/**
 * This fragment represents dynamic content that is the current sequence number
 */
public class SequenceNoFragment extends Fragment {

    /**
     * Constructor
     *
     * @param location location of the substitution that generated this fragment
     */
    public SequenceNoFragment(String location) {
        super(location);
    }

    /**
     * Output the current sequence number to the VEO.
     *
     * @throws VEOGenerator.VEOError
     */
    @Override
    public void finalise(DataSource data, VEOGenerator veo)
            throws VEOError {

        // output sequence number
        veo.outputDataToVeo(cs.encode(String.valueOf(veo.getSeqNo())));

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
        s = "Sequence Fragment\n";
        if (next != null) {
            s += next.toString();
        }
        return s;
    }
}
