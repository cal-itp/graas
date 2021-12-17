package gtfu.tools;

import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import gtfu.*;

// slice a log file by agency and days. Input is a log file that may have logs for multiple agencies and days.
// The tool will generate a separate log file for each agency and day. Output files will be named <agency-id>-yyyy-mm-dd.txt
public class GPSLogSlicer {
    private static final String[] WEEKDAYS = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};

    private Map<String, List<String>> logs;

    public GPSLogSlicer(List<String> input, String path) {
        logs = new HashMap<String, List<String>>();

        input.add("vid,2000000000,0,0,tid,aid"); // sentinel
        CSVHeader header = new CSVHeader(input.remove(0));
        Map<String, List<String>> map = new HashMap<String, List<String>>();
        int lastMidnight = -1;
        int count = 0;

        while (input.size() > 0) {
            String line = input.remove(0);
            if (line == null) break;

            CSVRecord r = new CSVRecord(header, line);
            String id = r.get("agency-id");
            long ts = r.getLong("timestamp");
            //Debug.log("- ts      : " + ts);

            if (ts > 2000000000l) continue;

            int midnight = (int)(Time.getMidnightTimestamp(ts * 1000l) / 1000);
            //Debug.log("- midnight: " + midnight);
            //System.exit(0);

            if (lastMidnight > 0 &&  midnight != lastMidnight) {
                for (String key : map.keySet()) {
                    //writeLog(path, key, lastMidnight, map.get(key));

                    String name = makeLogName(path, key, lastMidnight * 1000l);
                    logs.put(name, map.get(key));
                }

                map.clear();

                /*if (count++ == 10) {
                    Debug.log("x exiting loop early...");
                    break;
                }*/
            }

            List<String> lines = map.get(id);

            if (lines == null) {
                lines = new ArrayList<String>();
                map.put(id, lines);
            }

            lines.add(line);
            lastMidnight = midnight;
        }
    }

    public Map<String, List<String>> getLogs() {
        return logs;
    }

    private static String makeLogName(String path, String agencyID, long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyy-MM-dd");
        return path + agencyID + "-" + sdf.format(new Date(millis)) + ".txt";
    }

    //private static void writeLog(String path, String agencyID, int timestamp, List<String> lines) throws Exception {
    private static void writeLog(String filename, List<String> lines) throws Exception {
        if (lines.size() < 100) {
            //System.out.println("* day has very few entries for agency " + agencyID + ", ignoring...");
            return;
        }

        try (FileOutputStream fos = new FileOutputStream(filename)) {
            PrintWriter out = new PrintWriter(fos);

            for (String l : lines) {
                out.println(l);
            }

            out.flush();
        }
    }

    private static void usage() {
        System.err.println("usage: GPSLogSlicer <path-to-log>");
        System.exit(0);
    }

    public static void main(String[] arg) throws Exception {
        if (arg.length < 1) usage();

        String s = arg[0];
        String path = "./";
        int i = s.lastIndexOf('/');
        if (i > 0) {
            path = s.substring(0, i + 1);
        }

        TextFile tf = new TextFile(arg[0]);
        List<String> input = tf.getAllLinesAsList();
        GPSLogSlicer slicer = new GPSLogSlicer(input, path);
        Map<String, List<String>> logs = slicer.getLogs();

        for (String name : logs.keySet()) {
            writeLog(name, logs.get(name));
        }

        tf.dispose();
    }
}
