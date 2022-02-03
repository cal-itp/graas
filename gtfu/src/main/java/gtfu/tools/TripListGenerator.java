package gtfu.tools;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import gtfu.*;

// extract a list of trip names from an agency's static gtfs URL.
// Names are generated by concatenating a trip head sign and
// departure time from first stop, e.g. "Elm @ 12:05"
// If '-c' or '--calendar' are specified, a calendar array is included per trip
public class TripListGenerator {
    private static final String[] WEEKDAYS = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};
    private static final String reportPath = "src/main/resources/conf/output";

    private static void usage() {
        System.err.println("usage: TripListGenerator [-nf|--not-filterable] [-t|--temp-folder <tmp-folder>] -i|--agency-id <agency-id> [-u|--gtfs-url <gtfs-url>] [-o|--output-file <output-path>] [-a|--gtfs-agency <gtfs-agency-id>] [-r|-R|-h]");
        System.err.println("  <tmp-folder>: a folder where to cache static GTFS data for agencies (defaults to system tmp folder if not specified)");
        System.err.println("  <agency-id>: a descriptive name for an agency (lower-case latters and hyphens only)");
        System.err.println("  <gtfs-url>: Optional static GTFS URL for the agency - will default to agencies.yml without this value provided");
        System.err.println("  <gtfs-agency-id>: an agency ID as given in agency.txt");
        System.err.println("  <output-path>: where to write the generated data (defaults to stdout)");
        System.err.println("  Use one of the following flags to determine name field:");
        System.err.println("    -r route_short_name from routes.txt");
        System.err.println("    -R route_long_name from routes.txt");
        System.err.println("    -h trip_headsign from trips.txt");
        System.err.println("    -s shape_id from trips.txt");
        System.exit(0);
    }

    private static boolean hasName(String tripID, Map<String, TripData> tmap) {
        TripData data = tmap.get(tripID);
        return !Util.isEmpty(data.headSign);
    }

    public static void main(String[] arg) throws Exception {
        String rootFolder = null;
        String agencyID = null;
        String gtfsURL = null;
        String gtfsAgency = null;
        String nameField = null;
        boolean filterable = true;
        PrintStream out = System.out;

        for (int i=0; i<arg.length; i++) {
            if (arg[i].equals("-nf") || arg[i].equals("--not-filterable")) {
                filterable = false;
            }

            if ((arg[i].equals("-t") || arg[i].equals("--temp-folder")) && i < arg.length - 1) {
                rootFolder = arg[i + 1];
            }

            if ((arg[i].equals("-i") || arg[i].equals("--agency-id")) && i < arg.length - 1) {
                agencyID = arg[i + 1];
            }

            if ((arg[i].equals("-u") || arg[i].equals("--gtfs-url")) && i < arg.length - 1) {
                gtfsURL = arg[i + 1];
            }

            if ((arg[i].equals("-a") || arg[i].equals("--gtfs-agency")) && i < arg.length - 1) {
                gtfsAgency = arg[i + 1];
            }

            if ((arg[i].equals("-o") || arg[i].equals("--output-file")) && i < arg.length - 1) {
                out = new PrintStream(new FileOutputStream(arg[i + 1]));
            }

            if (arg[i].equals("-r") || arg[i].equals("--route-short-name")) {
                nameField = "route_short_name";
            }

            if (arg[i].equals("-R") || arg[i].equals("--route-long-name")) {
                nameField = "route_long_name";
            }

            if (arg[i].equals("-h") || arg[i].equals("--headsign")) {
                nameField = "headsign";
            }

            if (arg[i].equals("-s") || arg[i].equals("--shape-id")) {
                nameField = "shape_id";
            }


            if (arg[i].equals("-oa") || arg[i].equals("--output-agency-dir")) {
                if(agencyID == null) {
                    System.err.println("** -oa flag must come after agency-id");
                    System.exit(0);
                }
                String path = "../server/agency-config/gtfs/gtfs-aux/" + agencyID + "/route-names.json";
                out = new PrintStream(new FileOutputStream(path));
                System.out.println("output will be saved to " + path );
            }

        }
        if (nameField == null) {
            System.err.println("Use one of the following flags to determine name field:");
            System.err.println("   -r route_short_name from routes.txt");
            System.err.println("   -R route_long_name from routes.txt");
            System.err.println("   -h trip_headsign from trips.txt");
            System.exit(0);
        }

        if (agencyID == null) {
            usage();
        }

        if (rootFolder == null) {
            String home = System.getProperty("user.home");
            String path = home + "/tmp/tuff";
            File f = new File(path);

            if (f.exists() && f.isDirectory()) {
                rootFolder = path;
            } else {
                rootFolder = System.getProperty("java.io.tmpdir");
            }
        }

        if (gtfsURL == null) {
            AgencyYML yml = new AgencyYML();

            String s = agencyID + ':';
            gtfsURL = yml.getURL(agencyID);

            if (gtfsURL == null) {
                System.out.println("entry for '" + agencyID + "' has no static GTFS URL, bailing...");
                System.exit(1);
            }
        }

        Debug.log("- rootFolder: " + rootFolder);
        Debug.log("- agencyID: " + agencyID);
        Debug.log("- gtfsURL: " + gtfsURL);
        Debug.log("- gtfsAgency: " + gtfsAgency);
        Debug.log("- filterable: " + filterable);

        int errorCount = GTFSValidator.countErrors(gtfsURL, reportPath);
        if (errorCount > 0) {
            System.out.println("Do you wish to continue with TripListGenerator? (y or n)");
            Scanner in = new Scanner(System.in);

            String yn = in.nextLine();
            if (yn.equalsIgnoreCase("N")){
                System.out.println("Good choice - now go work on resolving those errors!");
                System.exit(0);
            }
            else {
                System.out.println("Continuing despite errors");
            }
            in.close();
        }

        Util.updateCacheIfNeeded(rootFolder, agencyID, gtfsURL, new ConsoleProgressObserver(40));

        TextFile tf = new TextFile(rootFolder + "/" + agencyID + "/routes.txt");
        CSVHeader header = new CSVHeader(tf.getNextLine());
        Map<String, RouteData> rmap = new HashMap<String, RouteData>();
        for (;;) {
            String line = tf.getNextLine();
            if (line == null) break;

            CSVRecord r = new CSVRecord(header, line);
            String id = r.get("route_id");
            String aid = r.get("agency_id");
            String rsn = r.get("route_short_name");
            String rln = r.get("route_long_name");
            rmap.put(id, new RouteData(aid, rsn, rln));
        }

        // Debug.log("- rmap: " + rmap);

        tf = new TextFile(rootFolder + "/" + agencyID + "/calendar.txt");
        header = new CSVHeader(tf.getNextLine());
        Map<String, List<Integer>> cmap = new HashMap<String, List<Integer>>();

        for (;;) {
            String line = tf.getNextLine();
            if (line == null) break;

            CSVRecord r = new CSVRecord(header, line);
            String id = r.get("service_id");
            List<Integer> il = new ArrayList<Integer>();

            for (int i=0; i<WEEKDAYS.length; i++) {
                il.add(r.getInt(WEEKDAYS[i]));
            }

            cmap.put(id, il);
        }

        tf.dispose();
        // Debug.log("- cmap: " + cmap);

        tf = new TextFile(rootFolder + "/" + agencyID + "/trips.txt");
        header = new CSVHeader(tf.getNextLine());
        Map<String, TripData> tmap = new HashMap<String, TripData>();

        for (;;) {
            String line = tf.getNextLine();
            if (line == null) break;
            CSVRecord r = new CSVRecord(header, line);
            String id = r.get("trip_id");
            String headsign = r.get("trip_headsign");
            String serviceID = r.get("service_id");
            String routeID = r.get("route_id");
            String shapeID = r.get("shape_id");

            tmap.put(id, new TripData(headsign, serviceID, routeID, shapeID));
        }

        tf.dispose();
        // Debug.log("- tmap: " + tmap);

        tf = new TextFile(rootFolder + "/" + agencyID + "/stop_times.txt");
        header = new CSVHeader(tf.getNextLine());
        List<TripListEntry> list = new ArrayList<TripListEntry>();
        String lastID = null;

        for (;;) {
            String line = tf.getNextLine();
            if (line == null) break;

            CSVRecord r = new CSVRecord(header, line);
            String id = r.get("trip_id");
            String stopID = r.get("stop_id");
            String hms = r.get("departure_time");

            if (!id.equals(lastID)) {
                int departureTime = Time.getMillisForTime(hms);
                TripData tdata = tmap.get(id);
                RouteData rdata = rmap.get(tdata.routeID);
                list.add(new TripListEntry(tdata, rdata, stopID, id, departureTime, nameField));
            }

            lastID = id;
        }

        tf.dispose();
        Collections.sort(list);
        //Debug.log("- list: " + list);

        tf = new TextFile(rootFolder + "/" + agencyID + "/stops.txt");
        header = new CSVHeader(tf.getNextLine());
        Map<String, String> smap = new HashMap<String, String>();

        for (;;) {
            String line = tf.getNextLine();
            if (line == null) break;

            CSVRecord r = new CSVRecord(header, line);
            String id = r.get("stop_id");
            float lat = r.getFloat("stop_lat");
            float lon = r.getFloat("stop_lon");

            smap.put(id, String.format("{\"lat\": %f, \"long\": %f}", lat, lon));
        }

        tf.dispose();
        //Debug.log("- smap: " + smap);

        for (int i=0; i<list.size(); i++) {
            TripListEntry e = list.get(i);
            String routeID = e.routeID;
            String aid = rmap.get(e.routeID).agencyID;

            if (gtfsAgency != null && !aid.equals(gtfsAgency)) {
               list.remove(i--);
            }
        }

        out.println();
        out.println("[");

        for (int i=0; i<list.size(); i++) {
            TripListEntry e = list.get(i);

            out.print("    ");
            out.print(filterable ? e.toFilterableString(cmap, smap) : e.toSimpleString());

            if (i < list.size() - 1) {
                out.print(",");
            }

            out.println();
        }

        out.println("]");

        if (out != System.out) {
            out.close();
        }
    }
}

class TripData {
    String headSign;
    String serviceID;
    String routeID;
    String shapeID;

    public TripData(String headSign, String serviceID, String routeID, String shapeID) {
        this.headSign = headSign;
        this.serviceID = serviceID;
        this.routeID = routeID;
        this.shapeID = shapeID;
    }
}

class RouteData {
    String agencyID;
    String routeShortName;
    String routeLongName;

    public RouteData(String agencyID, String routeShortName, String routeLongName) {
        this.agencyID = agencyID;
        this.routeShortName = routeShortName;
        this.routeLongName = routeLongName;
    }
}

class TripListEntry implements Comparable<TripListEntry> {
    String headSign;
    String serviceID;
    String routeID;
    String routeShortName;
    String routeLongName;
    String firstStopID;
    String tripID;
    String shapeID;
    String name;
    int departureTime;

    public TripListEntry(TripData data, RouteData rdata, String firstStopID, String tripID, int departureTime, String nameField) {
        headSign = data.headSign;
        serviceID = data.serviceID;
        routeID = data.routeID;
        routeShortName = rdata.routeShortName;
        routeLongName = rdata.routeLongName;
        shapeID = data.shapeID;

        // name = shapeID;
        if (nameField == "headsign"){
            name = headSign;
        } else if (nameField == "route_short_name") {
            name = routeShortName;
        } else if (nameField == "route_long_name") {
            name = routeLongName;
        } else if (nameField == "shape_id") {
            name = shapeID;
        }
        if (name == null || name == "") {
            System.err.println("** selected name field " + nameField + " is null/blank for trip "+ tripID);
        }

        this.firstStopID = firstStopID;
        this.tripID = tripID;
        this.departureTime = departureTime;
    }

    public int compareTo(TripListEntry o) {
        int res = headSign.compareTo(o.headSign);
        if (res == 0) res = departureTime - o.departureTime;
        return res;
    }

    private String shortenString(String s, int maxLength) {
        if (s.length() <= maxLength) return s;

        int index = s.indexOf('/');

        if (index >= 0 && index <= maxLength) {
            return s.substring(0, index);
        }

        return s.substring(0, maxLength) + "...";
    }

    public String toSimpleString() {
        return String.format("\"%s @ %s\": \"%s\"", shortenString(name, 25), Time.getHMForMillis(departureTime), tripID);
    }

    public String toFilterableString(Map<String, List<Integer>> cmap, Map<String, String>smap) {
        return String.format("{\"route_name\": \"%s @ %s\", \"trip_id\": \"%s\", \"calendar\": %s, \"departure_pos\": %s}",
            shortenString(name, 25),
            Time.getHMForMillis(departureTime),
            tripID,
            cmap.get(serviceID),
            smap.get(firstStopID)
        );
    }
}