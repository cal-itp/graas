package gtfu;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

public class GroupGPSUpdates {
    private static final String FORMAT = "EEE, MMM dd @ hh:mm a";
    private static final Map <String, Long> msTable = new HashMap<String, Long>();
    private static final Map <String, String> sdTable = new HashMap<String, String>();
    private static final Map <String, List<String>> outputTable = new HashMap<String, List<String>>();

    private static String getStartDate(String id) {
        return sdTable.get(id);
    }

    private static void setStartDate(String id, String startDate) {
        sdTable.put(id, startDate);
    }

    private static long getLastMillis(String id) {
        Long l = msTable.get(id);
        return l == null ? 0 : l.longValue();
    }

    private static void setLastMillis(String id, long lastMillis) {
        msTable.put(id, lastMillis);
    }

    private static void addRun(String id, String to) {
        //System.out.println(id + ": " + getStartDate(id) + " - " + to);

        List<String> list = outputTable.get(id);

        if (list == null) {
            list = new ArrayList<String>();
            outputTable.put(id, list);
        }

        list.add(id + ": " + getStartDate(id) + " - " + to);
    }

    private static void displayRuns() {
        for (String id : outputTable.keySet()) {
            List<String> list = outputTable.get(id);

            for (String s : list) {
                System.out.println(s);
            }

            System.out.println();
        }
    }

    public static void main(String[] arg) {
        //Debug.log("- arg[0]: " + arg[0]);

        if (arg.length == 0) {
            System.err.println("GroupGPSUpdates <path-to-file>");
            System.err.println("# file is assumed to be in CSV format with a");
            System.err.println("# vehicle ID as first field");
            System.err.println("# timestamp of seconds since epoch as second field");
            System.exit(-1);
        }

        TextFile tf = new TextFile(arg[0]);
        long lastMillis = 0;
        String startDate = null;
        String id = null;
        String hms1 = null;
        String hms2 = null;
        long millis = 0;

        for (;;) {
            String line = tf.getNextLine();
            if (line == null) break;

            String[] token = line.split(",");
            id = token[0];
            millis = Long.parseLong(token[1]) * 1000;

            lastMillis =getLastMillis(id);

            if (millis - lastMillis > 5 * Time.MILLIS_PER_MINUTE) {
                hms1 = Time.formatDate(FORMAT, lastMillis);
                hms2 = Time.formatDate(FORMAT, millis);

                if (getStartDate(id) == null) {
                    setStartDate(id, hms2);
                } else {
                    addRun(id, hms1);
                    startDate = hms2;
                    setStartDate(id, hms2);
                }
            }

            setLastMillis(id, millis);
        }

        hms2 = Time.formatDate(FORMAT, millis);
        addRun(id, hms2);

        displayRuns();
    }
}
