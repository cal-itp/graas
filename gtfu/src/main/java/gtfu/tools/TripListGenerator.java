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

import java.nio.charset.StandardCharsets;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * Create a list of trip names from an agency's static GTFS feed.
 */
public class TripListGenerator {

    private static final String[] WEEKDAYS = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};
    private static final String reportPath = "src/main/resources/conf/output";

    /**
     * Generate the triplist and print it to the provided PrintStream. If you pass only these 3 args, useValidator
     * @param agencyID      AgencyID
     * @param cacheDir      Directory where agency GTFS files will be cached. You can pass a null value and a default directory will be used.
     * @param out           PrintStream where output will be printed
     * @param useValidator  Whether or not to run the GTFS valdiator prior to generating list. If true, the validator will present a brief a summary in case of errors and give the optiont to bail.
     */
    public static void generateTripList(String agencyID, String cacheDir, PrintStream out, boolean useValidator) throws Exception {
        Map<String, RouteData> rmap = new HashMap();
        Map<String, CalendarData> cmap = new HashMap();
        Map<String, TripData> tmap = new HashMap();
        Map<String, String> dmap = new HashMap();
        Map<String, String> smap = new HashMap();
        boolean hasDirectionsTxt = false;

        if (cacheDir == null) {
            String home = System.getProperty("user.home");
            String path = home + "/tmp/tuff";
            File f = new File(path);

            if (f.exists() && f.isDirectory()) {
                cacheDir = path;
            } else {
                cacheDir = System.getProperty("java.io.tmpdir");
            }
        }

        AgencyYML yml = new AgencyYML();
        String gtfsURL = yml.getURL(agencyID);

        if (gtfsURL == null) {
            System.out.println("entry for '" + agencyID + "' has no static GTFS URL listed in agencies.yml, bailing...");
            System.exit(1);
        }

        Debug.log("- cacheDir: " + cacheDir);
        Debug.log("- agencyID: " + agencyID);
        Debug.log("- gtfsURL: " + gtfsURL);

        byte[] bytes = GCloudStorage.getObject("graas-resources","gtfs-aux/" + agencyID + "/agency-params.json");
        JSONParser parser = new JSONParser();
        JSONObject agencyParams = (JSONObject) parser.parse(new String(bytes, StandardCharsets.UTF_8));

        String nameField = (String) agencyParams.get("triplist-generator-namefield");
        Boolean useDirection = (Boolean) agencyParams.get("triplist-generator-use-direction");
        if(useDirection == null){
            useDirection = false;
        }

        if (nameField == null) {
            System.err.println("Update agency-params for your agency to include a valid value for triplist-generator-namefield. Valid options include:");
            System.err.println("headsign, route_short_name, route_long_name, shape_id");
            System.exit(0);
        }

        if (useValidator) {
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
        }

        Util.updateCacheIfNeeded(cacheDir, agencyID, gtfsURL, new ConsoleProgressObserver(40));

        TextFile tf = new TextFile(cacheDir + "/" + agencyID + "/routes.txt");
        CSVHeader header = new CSVHeader(tf.getNextLine());

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

        tf = new TextFile(cacheDir + "/" + agencyID + "/calendar.txt");
        header = new CSVHeader(tf.getNextLine());

        for (;;) {
            String line = tf.getNextLine();
            if (line == null) break;

            CSVRecord r = new CSVRecord(header, line);
            String id = r.get("service_id");
            List<Integer> il = new ArrayList<Integer>();

            for (int i=0; i<WEEKDAYS.length; i++) {
                il.add(r.getInt(WEEKDAYS[i]));
            }

            String startDate = r.get("start_date");
            String endDate = r.get("end_date");
            cmap.put(id, new CalendarData(il, startDate, endDate));
        }

        tf.dispose();
        // Debug.log("- cmap: " + cmap);

        tf = new TextFile(cacheDir + "/" + agencyID + "/trips.txt");
        header = new CSVHeader(tf.getNextLine());

        for (;;) {
            String line = tf.getNextLine();
            if (line == null) break;
            CSVRecord r = new CSVRecord(header, line);
            String id = r.get("trip_id");
            String headsign = r.get("trip_headsign");
            String serviceID = r.get("service_id");
            String routeID = r.get("route_id");
            String shapeID = r.get("shape_id");
            String directionID = r.get("direction_id");

            tmap.put(id, new TripData(headsign, serviceID, routeID, shapeID, directionID));
        }

        tf.dispose();
        // Debug.log("- tmap: " + tmap);

        try {
            tf = new TextFile(cacheDir + "/" + agencyID + "/directions.txt");
            // If directions.txt is not present, we'll exit the try{} and none of the below code will run.
            // hasDirectionsTxt will remain false.
            hasDirectionsTxt = true;
            header = new CSVHeader(tf.getNextLine());

            for (;;) {
                String line = tf.getNextLine();
                if (line == null) break;
                CSVRecord r = new CSVRecord(header, line);
                String id = r.get("direction_id");
                String routeID = r.get("route_id");
                String direction = r.get("direction");
                dmap.put(routeID + id, direction);
            }
            // Debug.log("dmap: " + dmap);

            tf.dispose();

        } catch (Exception e) {}

        tf = new TextFile(cacheDir + "/" + agencyID + "/stop_times.txt");
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
                String routeID = tdata.routeID;
                String directionID = tdata.directionID;
                RouteData rdata = rmap.get(routeID);
                String direction = null;
                if (hasDirectionsTxt){
                    direction = dmap.get(routeID + directionID);
                } else {
                    direction = createDirectionMap().get(directionID);
                }
                list.add(new TripListEntry(tdata, rdata, stopID, id, departureTime, nameField, direction, useDirection));
            }

            lastID = id;
        }

        tf.dispose();
        Collections.sort(list);
        //Debug.log("- list: " + list);

        tf = new TextFile(cacheDir + "/" + agencyID + "/stops.txt");
        header = new CSVHeader(tf.getNextLine());

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

        // It's temping to replace the below manual JSON generation with JSONObject and JSONArray classes,
        // but because they offer little control over formatting, this method is preferable for something
        // needing to be read by humans.
        out.println("[");

        for (int i=0; i<list.size(); i++) {
            TripListEntry e = list.get(i);

            out.print("    ");
            out.print(e.toString(cmap, smap));

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

    // For agencies who don't include the optional directions.txt file, assume the following mapping.
    // This is designed specifically around Unitrans
    private static Map<String, String> createDirectionMap() {
        Map<String,String> directionMap = new HashMap<String,String>();
        directionMap.put("0", "Outbound");
        directionMap.put("1", "Inbound");
        return directionMap;
    }

    private static void usage() {
        System.err.println("usage: TripListGenerator [-t|--temp-folder <tmp-folder>] -a|--agency-id <agency-id> [-o|--output-file <output-path>] [-a|--gtfs-agency <gtfs-agency-id>] [-r|-R|-h]");
        System.err.println("  <tmp-folder>: a folder where to cache static GTFS data for agencies (defaults to system tmp folder if not specified)");
        System.err.println("  <agency-id>: a descriptive name for an agency (lower-case latters and hyphens only)");
        System.err.println("  <gtfs-agency-id>: an agency ID as given in agency.txt");
        System.err.println("  <output-path>: where to write the generated data (defaults to stdout)");
        System.exit(0);
    }

    /**
     * Generate TripList from the command line
     */
    public static void main(String[] arg) throws Exception {
        String cacheDir = null;
        String agencyID = null;
        PrintStream out = System.out;
        boolean useValidator = false;

        for (int i=0; i<arg.length; i++) {

            if ((arg[i].equals("-a") || arg[i].equals("--agency-id")) && i < arg.length - 1) {
                agencyID = arg[i + 1];
            }

            if ((arg[i].equals("-c") || arg[i].equals("--cache-dir")) && i < arg.length - 1) {
                cacheDir = arg[i + 1];
            }

            if ((arg[i].equals("-o") || arg[i].equals("--output-file")) && i < arg.length - 1) {
                out = new PrintStream(new FileOutputStream(arg[i + 1]));
            }

            if (arg[i].equals("-l") || arg[i].equals("--update-local")) {
                if(agencyID == null) {
                    System.err.println("** -l flag must come after agency-id");
                    System.exit(0);
                }
                String path = "../server/agency-config/gtfs/gtfs-aux/" + agencyID + "/trip-names.json";
                out = new PrintStream(new FileOutputStream(path));
                System.out.println("output will be saved to " + path );
            }

            if (arg[i].equals("-v") || arg[i].equals("--use-validator")) {
                useValidator = true;
            }
        }

        if (agencyID == null) {
            usage();
        }

        generateTripList(agencyID, cacheDir, out, useValidator);
    }
}

class TripData {
    String headSign;
    String serviceID;
    String routeID;
    String shapeID;
    String directionID;

    public TripData(String headSign, String serviceID, String routeID, String shapeID, String directionID) {
        this.headSign = headSign;
        this.serviceID = serviceID;
        this.routeID = routeID;
        this.shapeID = shapeID;
        this.directionID = directionID;
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

class CalendarData {
    List<Integer> dayList;
    String startDate;
    String endDate;

    public CalendarData(List<Integer> dayList, String startDate, String endDate) {
        this.dayList = dayList;
        this.startDate = startDate;
        this.endDate = endDate;
    }
}

class TripListEntry implements Comparable<TripListEntry> {
    String serviceID;
    String routeID;
    String firstStopID;
    String tripID;
    String name;
    int departureTime;

    public TripListEntry(TripData data, RouteData rdata, String firstStopID, String tripID, int departureTime, String nameField, String direction, boolean useDirection) {
        serviceID = data.serviceID;
        routeID = data.routeID;

        if (nameField.equals("headsign")){
            name = data.headSign;
        } else if (nameField.equals("route_short_name")) {
            name = rdata.routeShortName;
        } else if (nameField.equals("route_long_name")) {
            name = rdata.routeLongName;
        } else if (nameField.equals("shape_id")) {
            name = data.shapeID;
        }
        if (useDirection){
            name += " - " + direction;
        }
        if (Util.isEmpty(name)) {
            System.err.println("** selected name field " + nameField + " is null/blank for trip "+ tripID);
        }

        this.firstStopID = firstStopID;
        this.tripID = tripID;
        this.departureTime = departureTime;
    }

    public int compareTo(TripListEntry o) {
        int res = name.compareTo(o.name);
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

    public String toString(Map<String, CalendarData> cmap, Map<String, String>smap) {
        return String.format("{\"trip_name\": \"%s @ %s\", \"trip_id\": \"%s\", \"calendar\": %s, \"departure_pos\": %s, \"start_date\": %s, \"end_date\": %s}",
            name,
            Time.getHMForMillis(departureTime),
            tripID,
            cmap.get(serviceID).dayList,
            smap.get(firstStopID),
            cmap.get(serviceID).startDate,
            cmap.get(serviceID).endDate
        );
    }
}