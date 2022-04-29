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
    private static Map<String, String> customDirectionMap = new HashMap();

    /**
     * Generate the triplist and print it to the provided PrintStream. If you pass only these 3 args, useValidator
     * @param agencyID      AgencyID
     * @param cacheDir      Directory where agency GTFS files will be cached. You can pass a null value and a default directory will be used.
     * @param out           PrintStream where output will be printed
     * @param useValidator  Whether or not to run the GTFS valdiator prior to generating list. If true, the validator will present a brief a summary in case of errors and give the optiont to bail.
     */
    public static void generateTripList(String agencyID, String cacheFolder, PrintStream out, boolean useValidator) throws Exception {

        if(cacheFolder == null){
            cacheFolder = "src/main/resources/conf/cache";
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
        CalendarDateCollection calendarDates = (CalendarDateCollection)collections.get("calendar_dates");
        TripCollection trips = (TripCollection)collections.get("trips");
        RouteCollection routes = (RouteCollection)collections.get("routes");
        DirectionCollection directions = (DirectionCollection)collections.get("directions");

        Map<String, List<String>> dateOnMap = createServiceIDMap(1, calendarDates);
        Map<String, List<String>> dateOffMap = createServiceIDMap(2, calendarDates);

        if(useDirection && directions == null){
            customDirectionMap = createCustomDirectionMap();
        }
        List<TripListEntry> list = new ArrayList<TripListEntry>();
        for(int i = 0; i < trips.getSize(); i++){

            Trip trip = trips.get(i);
            Route route = routes.get(trip.getRouteID());

            String name = null;
            if(nameField.equals("headsign")){
                name = trip.getHeadsign();
            } else if (nameField.equals("route_long_name")){
                name = route.getLongName();
            } else if (nameField.equals("route_short_name")){
                name = route.getShortName();
            }

            if(useDirection){
                if(directions != null){
                    Direction direction = directions.get(trip.getRouteID() + "-" + trip.getDirectionID());
                    String directionName = direction.getName();
                    name += " - " + directionName;
                }
                else{
                    String directionName = customDirectionMap.get(trip.getDirectionID());
                    name += " - " + directionName;
                }
            }
            int startTimeSecs = trip.getStartTime();
            String startTimeString = Time.getHMForSeconds(trip.getStartTime(), true);
            String tripID = trip.getID();

            Calendar calendar = calendars.get(trip.getServiceID());
            String calendarString = null;
            String startDate = null;
            String endDate = null;

            if(calendar != null){
                calendarString = calendar.toArrayString();
                startDate = calendar.getStartDate();
                endDate = calendar.getEndDate();
            }

            String onDates = null;
            if(dateOnMap.get(trip.getServiceID()) != null){
                onDates = dateOnMap.get(trip.getServiceID()).toString();
            }
            String offDates = null;
            if(dateOffMap.get(trip.getServiceID()) != null){
                offDates = dateOffMap.get(trip.getServiceID()).toString();
            }

            Stop firstStop = trip.getStop(0);
            Float lat = firstStop.getLat();
            Float lon = firstStop.getLong();

            list.add(new TripListEntry(name, startTimeSecs, startTimeString, tripID, calendarString, lat, lon, startDate, endDate, onDates, offDates));
        }
        Collections.sort(list);

        // It's temping to replace the below manual JSON generation with JSONObject and JSONArray classes,
        // but because they offer little control over formatting, this method is preferable for something
        // needing to be read by humans.
        out.println("[");

        for (int i=0; i<list.size(); i++) {
            out.print("    ");
            TripListEntry e = list.get(i);
            out.print(e.toString());

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
    private static Map<String, String> createCustomDirectionMap() {
        Map<String,String> directionMap = new HashMap();
        directionMap.put("0", "Outbound");
        directionMap.put("1", "Inbound");
        return directionMap;
    }

    private static Map<String, List<String>> createServiceIDMap(int exception, CalendarDateCollection calendarDates){
        Map<String, List<String>> dateMap = new HashMap();

        CalendarDate currentCalDate = null;
        for(int i = 0; i < calendarDates.getSize(); i++){
            currentCalDate = calendarDates.get(i);
            if(currentCalDate.getExceptionType() == exception){
                if(dateMap.get(currentCalDate.getServiceID()) == null){
                    List<String> dateList = new ArrayList();
                    dateList.add(currentCalDate.getDate());
                    dateMap.put(currentCalDate.getServiceID(),dateList);
                } else{
                    dateMap.get(currentCalDate.getServiceID()).add(currentCalDate.getDate());
                }
            }
        }
        return dateMap;
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

class TripListEntry implements Comparable<TripListEntry> {

    String name;
    int startTimeSecs;
    String startTimeString;
    String tripID;
    String calendarString;
    Float lat;
    Float lon;
    String startDate;
    String endDate;
    String onDates;
    String offDates;

    public TripListEntry(String name, int startTimeSecs, String startTimeString, String tripID, String calendarString, Float lat, Float lon, String startDate, String endDate, String onDates, String offDates) {

        this.name = name;
        this.startTimeSecs = startTimeSecs;
        this.startTimeString = startTimeString;
        this.tripID = tripID;
        this.calendarString = calendarString;
        this.lat = lat;
        this.lon = lon;
        this.startDate = startDate;
        this.endDate = endDate;
        this.onDates = onDates;
        this.offDates = offDates;
    }

    public int compareTo(TripListEntry o) {
        int res = name.compareTo(o.name);
        if (res == 0) res = startTimeSecs - o.startTimeSecs;
        return res;
    }

    public String toString() {
        return String.format("{\"trip_name\": \"%s @ %s\", \"trip_id\": \"%s\", \"calendar\": %s, \"departure_pos\": {\"lat\": %s, \"long\": %s}, \"start_date\": %s, \"end_date\": %s, \"on_dates\": %s, \"off_dates\": %s}",
                                name,
                                startTimeString,
                                tripID,
                                calendarString,
                                lat,
                                lon,
                                startDate,
                                endDate,
                                onDates,
                                offDates
                            );
    }
}