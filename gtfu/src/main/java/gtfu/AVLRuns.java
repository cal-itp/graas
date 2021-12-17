package gtfu;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * a utility to extract AVL runs from transitclock logs. For each distinct vehicle ID found in the logs,
 * a run record is created and displayed at the end.
 */
public class AVLRuns {
    private static final String TC_LOG_FORMAT = "MM-dd-yyyy HH:mm:ss.SSS z";

    private static final String[] WEEK_DAY = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private static final String[] MONTH    = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private static final String VEHICLE_ID_ARG = "vehicleId=";
    private static final String TIME_ARG = "time=";

    private static final String SERVER_ARG = "-server";
    private static final String AGENCY_ARG = "-agency";
    private static final String DAY_OFFSET_ARG = "-dayoffset";

    private static final int RUN_MAX_GAP = 5 * 60 * 1000;
    private static final int MILLISECONDS_PER_DAY = 24 * 60 * 60 * 1000;

    public AVLRuns(String server, String agencyID, int dayOffset) {
        Debug.log("AVLRuns.AVLRuns()");
        Debug.log("- server: " + server);
        Debug.log("- agencyID: " + agencyID);
        Debug.log("- dayOffset: " + dayOffset);

        String date = getDate(dayOffset);
        Debug.log("- date: " + date);

        String[] arg;
        String innerCmd;

        if (1 == 1) {
            String cmd = "gcloud compute ssh " + server + " --command=\"docker ps\" | grep -v COMMAND | grep -v entrypoint.sh | rev | awk '{print $1}' | rev";
            ProcessData pd = Util.runProcess(System.getenv("PATH"), new String[] {"sh", "-c" ,cmd}, true);
            String container = Util.stripCommonWhitespace(pd.output);
            Debug.log("- container: " + container);

            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR_OF_DAY);

            if (dayOffset == 0 && hour < 17) {
                innerCmd = "cat logs/" + agencyID + "/core/" + date + "/core.log | grep AvlProcessor";
            } else {
                innerCmd = "zcat logs/" + agencyID + "/core/" + date + "/core.log.gz | grep AvlProcessor";
            }

            Debug.log("- innerCmd: " + innerCmd);
            cmd = "gcloud compute ssh " + server + " --container=" + container + " --command=\"" + innerCmd + "\"";
            Debug.log("- cmd: " + cmd);
            pd = Util.runProcess(System.getenv("PATH"), new String[] {"sh", "-c" ,cmd}, true);
            //Debug.log("- output: " + pd.output);

            arg = pd.output.split("\n");
        } else {
            TextFile tf = new TextFile("core.log");
            arg = tf.getAllLinesAsString().split("\n");
            tf.dispose();
        }

        Debug.log("- arg.length: " + arg.length);

        Map<String, List<AVLRunData>> map = new HashMap<String, List<AVLRunData>>();

        for (String line : arg) {
            AVLReportData data = getData(line);

            if (data != null) {
                updateMap(map, data.id, data.lastMillis);
            }
        }

        dumpMap(map, dayOffset);
    }

    private void updateMap(Map<String, List<AVLRunData>> map, String id, long millis) {
        List<AVLRunData> list = map.get(id);

        if (list == null) {
            list = new ArrayList<>();
            list.add(new AVLRunData(millis));
            map.put(id, list);
        } else {
            AVLRunData ard = list.get(list.size() - 1);

            if (millis - ard.end < RUN_MAX_GAP) {
                ard.end = millis;
            } else {
                list.add(new AVLRunData(millis));
            }
        }
    }

    private void dumpMap(Map<String, List<AVLRunData>> map, int dayOffset) {
        System.out.println();
        System.out.println(getDisplayDate(dayOffset));
        System.out.println();

        for (String id : map.keySet()) {
            System.out.println(id + ":");

            List<AVLRunData> list = map.get(id);

            for (AVLRunData d : list) {
                System.out.println("  " + getTime(d.start) + " - " + getTime(d.end));
            }
        }
    }

    private String getTime(long millis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);

        return String.format("%d:%02d %s", cal.get(Calendar.HOUR), cal.get(Calendar.MINUTE), cal.get(Calendar.AM_PM) == Calendar.AM ? "am" : "pm");
    }

    private AVLReportData getData(String s) {
        int i1 = s.indexOf('[');
        int i2 = s.lastIndexOf(']');

        if (i1 < 0 || i2 < 0) return null;

        String s2 = s.substring(i1 + 1, i2);
        //Debug.log("- s2: " + s2);
        String[] arg = s2.split(", ");
        String id = null;
        long millis = -1l;

        for (String a : arg) {
            //Debug.log("- a: " + a);
            if (a.startsWith(VEHICLE_ID_ARG)) {
                id = a.substring(VEHICLE_ID_ARG.length());
                //Debug.log("- id: " + id);
            }

            if (a.startsWith(TIME_ARG)) {
                Date date = null;

                try {
                    // 09-28-2020 17:00:01.000 PDT
                    date = Time.parseDate(TC_LOG_FORMAT, a.substring(TIME_ARG.length()));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                millis = date.getTime();
                //Debug.log("- millis: " + millis);
            }
        }

        if (id == null || millis < 0) return null;

        return new AVLReportData(id, millis);
    }

    private static String getDisplayDate(int dayOffset) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(Util.now() + dayOffset * MILLISECONDS_PER_DAY);

        return String.format("%s, %s %d", WEEK_DAY[cal.get(Calendar.DAY_OF_WEEK) - 1], MONTH[cal.get(Calendar.MONTH)], cal.get(Calendar.DAY_OF_MONTH));
    }

    private static String getDate(int dayOffset) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(Util.now() + dayOffset * MILLISECONDS_PER_DAY);

        return String.format("%04d/%02d/%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
    }

    private static void usage() {
        System.err.println("AVLRuns [-server <server-name>] [-agency <agency-id>] [-dayoffset <-n>]");
        System.exit(0);
    }

    public static void main(String[] arg) {
        String agencyID = "santa-barbara-clean-air-express";
        String server = "transitclock-cluster-1";
        int dayOffset = 0;

        for (int i=0; i<arg.length; i++) {
            if (arg[i].equals(SERVER_ARG) && i + 1 < arg.length) {
                server = arg[++i];
                continue;
            }

            if (arg[i].equals(AGENCY_ARG) && i + 1 < arg.length) {
                agencyID = arg[++i];
                continue;
            }

            if (arg[i].equals(DAY_OFFSET_ARG) && i + 1 < arg.length) {
                try {
                    dayOffset = Integer.parseInt(arg[++i]);
                } catch (NumberFormatException e) {
                    usage();
                }
                continue;
            }

            usage();
        }

        if (dayOffset > 0) usage();

        new AVLRuns(server, agencyID, dayOffset);
    }
}

class AVLReportData {
    String id;
    long lastMillis;

    AVLReportData(String id, long lastMillis) {
        this.id = id;
        this.lastMillis = lastMillis;
    }
}

class AVLRunData {
    long start;
    long end;

    AVLRunData(long start) {
        this.start = start;
        this.end = start;
    }
}