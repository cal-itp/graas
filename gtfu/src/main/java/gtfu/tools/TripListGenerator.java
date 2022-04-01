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
    public static void generateTripList(String agencyID, String cacheFolder, PrintStream out, boolean useValidator) throws Exception {

        if(cacheFolder == null){
            cacheFolder = System.getenv("HOME") + "/tmp/tuff";
        }

        AgencyYML a = new AgencyYML();
        String gtfsURL = a.getURL(agencyID);

        if (gtfsURL == null) {
            System.out.println(agencyID + " does not appear in agencies.yml, exiting");
            System.exit(1);
        }

        byte[] bytes = GCloudStorage.getObject("graas-resources","gtfs-aux/" + agencyID + "/agency-params.json");
        JSONParser parser = new JSONParser();
        JSONObject agencyParams = (JSONObject) parser.parse(new String(bytes, StandardCharsets.UTF_8));


        Boolean useDirection = (Boolean) agencyParams.get("triplist-generator-use-direction");

        if(useDirection == null){
            useDirection = false;
        }

        String nameField = (String) agencyParams.get("triplist-generator-namefield");
        Debug.log("nameField: " + nameField);

        if (nameField == null) {
            System.err.println("Update agency-params for your agency to include a valid value for triplist-generator-namefield. Valid options include:");
            System.err.println("headsign, route_short_name, route_long_name");
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

        ConsoleProgressObserver progressObserver = new ConsoleProgressObserver(40);
        Util.updateCacheIfNeeded(cacheFolder, agencyID, gtfsURL, progressObserver);

        Map<String, Object> collections = Util.loadCollections(cacheFolder, agencyID, progressObserver);
        CalendarCollection calendars = (CalendarCollection)collections.get("calendars");
        TripCollection trips = (TripCollection)collections.get("trips");
        RouteCollection routes = (RouteCollection)collections.get("routes");
        DirectionCollection directions = (DirectionCollection)collections.get("directions");

        out.println("[");

        for(int i = 0; i < trips.getSize(); i++){

            Trip trip = trips.get(i);
            Route route = routes.get(trip.getRouteID());
            Calendar calendar = calendars.get(trip.getServiceID());
            Stop firstStop = trip.getStop(0);

            String name = null;
            if(nameField.equals("headsign")){
                name = trip.getHeadsign();
            } else if (nameField.equals("route_long_name")){
                name = route.getLongName();
            } else if (nameField.equals("route_short_name")){
                name = route.getShortName();
            }
            TODO:
            if(useDirection){
                Direction direction = directions.get(trip.getRouteID() + "-" + trip.getDirectionID());
                String directionName = direction.getName();
                name += " - " + directionName;
            }

            if(calendar != null){

                String tripInfo = String.format("{\"trip_name\": \"%s @ %s\", \"trip_id\": \"%s\", \"calendar\": %s, \"departure_pos\": {\"lat\": %s, \"long\": %s}, \"start_date\": %s, \"end_date\": %s}",
                                    name,
                                    Time.getHMForSeconds(trip.getStartTime(), true),
                                    trip.getID(),
                                    calendar.toArrayString(),
                                    firstStop.getLat(),
                                    firstStop.getLong(),
                                    calendar.getStartDate(),
                                    calendar.getEndDate()
                                );

                out.print("    ");
                out.print(tripInfo);

                if (i < trips.getSize() - 1) {
                    out.print(",");
                }
                out.println();
            }
        }

        out.println("]");

        if (out != System.out) {
            out.close();
        }
    }

        // try {
        //     tf = new TextFile(cacheDir + "/" + agencyID + "/directions.txt");
        //     // If directions.txt is not present, we'll exit the try{} and none of the below code will run.
        //     // hasDirectionsTxt will remain false.
        //     hasDirectionsTxt = true;
        //     header = new CSVHeader(tf.getNextLine());

        //     for (;;) {
        //         String line = tf.getNextLine();
        //         if (line == null) break;
        //         CSVRecord r = new CSVRecord(header, line);
        //         String id = r.get("direction_id");
        //         String routeID = r.get("route_id");
        //         String direction = r.get("direction");
        //         dmap.put(routeID + id, direction);
        //     }
        //     // Debug.log("dmap: " + dmap);

        //     tf.dispose();

        // } catch (Exception e) {}


        // for (;;) {
        //     String line = tf.getNextLine();
        //     if (line == null) break;

        //     CSVRecord r = new CSVRecord(header, line);
        //     String id = r.get("trip_id");
        //     String stopID = r.get("stop_id");
        //     String hms = r.get("departure_time");

        //     if (!id.equals(lastID)) {
        //         int departureTime = Time.getMillisForTime(hms);
        //         TripData tdata = tmap.get(id);
        //         String routeID = tdata.routeID;
        //         String directionID = tdata.directionID;
        //         RouteData rdata = rmap.get(routeID);
        //         String direction = null;
        //         if (hasDirectionsTxt){
        //             direction = dmap.get(routeID + directionID);
        //         } else {
        //             direction = createDirectionMap().get(directionID);
        //         }
        //         list.add(new TripListEntry(tdata, rdata, stopID, id, departureTime, nameField, direction, useDirection));
        //     }

        //     lastID = id;
        // }

        // tf.dispose();


    // For agencies who don't include the optional directions.txt file, assume the following mapping.
    // This is designed specifically around Unitrans
    // private static Map<String, String> createDirectionMap() {
    //     Map<String,String> directionMap = new HashMap<String,String>();
    //     directionMap.put("0", "Outbound");
    //     directionMap.put("1", "Inbound");
    //     return directionMap;
    // }

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
