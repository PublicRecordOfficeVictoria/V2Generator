package VEOGenerator;

/**
 * *************************************************************
 *
 * D A T E F R A G M E N T
 *
 * This class represents a dynamic fragment that will be filled in by the
 * current data and time in the standard VERS format.
 *
 * Andrew Waugh (andrew.waugh@dvc.vic.gov.au) Copyright 2006 PROV
 *
 *************************************************************
 */
import java.util.*;
import java.util.TimeZone;
import java.text.SimpleDateFormat;

/**
 * This fragment represent dynamic content that is replaced by the current date
 * and time in the VERS format. The standard VERS format is described in PROS
 * 99/007 (Version 2), Specification 2, p146.
 */
public class DateFragment extends Fragment {

    /**
     * Constructor.
     *
     * @param location source of the substitution (file/line)
     */
    public DateFragment(String location) {
        super(location);
    }

    /**
     * Output the current data and time (in VERS format) to the VEO. The
     * DataSource is not used.
     *
     * @throws VEOGenerator.VEOError
     */
    @Override
    public void finalise(DataSource data, VEOGenerator veo)
            throws VEOError {

        // output current data time to VEO
        veo.outputDataToVeo(cs.encode(versDateTime(0)));

        // finalise any trailing fragments (if any)
        if (next != null) {
            next.finalise(data, veo);
        }
    }

    /**
     * Returns a date and time in the standard VERS format (see PROS 99/007
     * (Version 2), Specification 2, p146.
     *
     * This is a public routine so that other code can get the current
     * date/time.
     *
     * @param ms	milliseconds since the epoch (if zero, return current
     * date/time)
     * @return The date and time as a string
     */
    public String versDateTime(long ms) {
        Date d;
        SimpleDateFormat sdf;
        TimeZone tz;
        Locale l;
        String s;

        tz = TimeZone.getDefault();
        sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        sdf.setTimeZone(tz);
        if (ms == 0) {
            d = new Date();
        } else {
            d = new Date(ms);
        }
        s = sdf.format(d);
        return s.substring(0, 22) + ":" + s.substring(22, 24);
    }

    /**
     * Outputs this fragment as a string.
     */
    @Override
    public String toString() {
        String s;
        s = "Date Fragment:\n";
        if (next != null) {
            s += next.toString();
        }
        return s;
    }
}
