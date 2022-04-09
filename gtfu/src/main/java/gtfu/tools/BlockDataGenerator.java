package gtfu.tools;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

import gtfu.*;

// A tool to extract the relationship between GTFS blocks and trips,
// more specifically to list all trips associated with a specific block.
// Output is formatted as JSON
public class BlockDataGenerator {
    /**
    * Generates block data for an agency
    * @param agencyID   agency to produce block data for
    * @param offset     how many days in advance to generate data for.
    */
    public BlockDataGenerator(String agencyID, Integer offset) throws Exception {
        new BlockDataGenerator("src/main/resources/conf/cache", null, agencyID, null, getDateFromOffset(offset), true);
    }

    /**
    * Generates block data for an agency
    * @param cacheFolder    Folder to use for caching static GTFS data
    * @param outputFolder   Folder for saving output files
    * @param agencyID       Agency to produce block data for
    * @param date           Date to generate block data for
    * @param uploadToGcloud Whether or not to upload results to gcloud rather than downloading
    */
    public BlockDataGenerator(String cacheFolder, String outputFolder, String agencyID, String gtfsURL, Date date, Boolean uploadToGcloud) throws Exception {

        if(outputFolder == null){
            outputFolder = System.getenv("HOME") + "/tmp";
        }

        if(cacheFolder == null){
            cacheFolder = System.getenv("HOME") + "/tmp/tuff";
        }

        Debug.log("BlockDataGenerator.BlockDataGenerator()");
        Debug.log("- cacheFolder: " + cacheFolder);
        Debug.log("- outputFolder: " + outputFolder);
        Debug.log("- agencyID: " + agencyID);
        Debug.log("- date: " + date);
        Debug.log("- uploadToGcloud: " + uploadToGcloud);

        if (gtfsURL == null) {
            AgencyYML a = new AgencyYML();
            gtfsURL = a.getURL(agencyID);
        }

        if (gtfsURL == null) {
            System.out.println(agencyID + " does not appear in agencies.yml, exiting");
            System.exit(1);
        }

        ConsoleProgressObserver progressObserver = new ConsoleProgressObserver(40);
        Util.updateCacheIfNeeded(cacheFolder, agencyID, gtfsURL, progressObserver);
        Map<String, Object> collections = Util.loadCollections(cacheFolder, agencyID, progressObserver);
        CalendarCollection calendars = (CalendarCollection)collections.get("calendars");
        TripCollection trips = (TripCollection)collections.get("trips");
        BlockCollection blocks = new BlockCollection(calendars, trips, date);

        List<BlockRecord> list = new ArrayList<BlockRecord>();

        for (Block b : blocks) {
            BlockRecord br = new BlockRecord();

            br.id = b.id;

            for (String tripID : b.tripIDs) {
                TripRecord tr = new TripRecord();
                Trip trip = trips.get(tripID);

                tr.id = tripID;
                tr.headsign = trip.getHeadsign();
                tr.startTime = trip.getStartTime();
                tr.endTime = tr.startTime + trip.getDurationInSeconds();

                br.add(tr);
            }

            list.add(br);
        }

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(date);
        String dateString = cal.get(cal.YEAR)
            + "-"
            + Util.pad("" + (cal.get(cal.MONTH) + 1), '0', 2)
            + "-"
            + Util.pad("" + cal.get(cal.DAY_OF_MONTH), '0', 2);

        String fileName = "blocks-" + dateString + ".json";
        Debug.log("- fileName: " + fileName);

        if(uploadToGcloud){
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            String utf8 = StandardCharsets.UTF_8.name();
            try (PrintStream ps = new PrintStream(baos, true, utf8)) {
                ps.println(Util.objectToJSON(list, true));
            }
            byte[] file = baos.toByteArray();

            GCloudStorage gcs = new GCloudStorage();
            gcs.uploadObject("graas-resources", "gtfs-aux/" + agencyID, fileName, file);
        }
        else{
            String outputFile = outputFolder + "/" + fileName;

            Debug.log("- outputFile: " + outputFile);

            try (FileOutputStream fos = new FileOutputStream(outputFile);
                PrintStream out = new PrintStream(fos)) {
                out.println(Util.objectToJSON(list, true));
            }
        }
        Util.getReporter().addLine("   - " + dateString);
    }

    private static void usage() {
        System.err.println("usage: BlockDataGenerator -a|--agency-id <agency-id> [-U|--url <static-gtfs-url>] -u|--upload [-c|--cache-folder <cache-folder>] [-o|--output-folder <output-folder>] [-d|--date <mm/dd/yy>|<n>]");
        System.err.println("    <agency-id> a transit agency identifier constructed from the alphabet of [a-z\\-]");
        System.err.println("    <static-gtfs-url> optional link to agency's static static GTFS data. For agencies present in agencies.yml, the URL will be loaded automatically");
        System.err.println("    use -u or --upload flag to upload files directly to Google Cloud");
        System.err.println("    <cache-folder> a temp folder for unpacking and caching static GTFS data by agency");
        System.err.println("    <output-folder> folder to place output file in (file name will be 'blocks-<mm>-<dd>.json'");
        System.err.println("    <mm/dd/yy> valid 24 hour period for block data, defaults to current day if omitted");
        System.err.println("    <n> offset from today's date (must be between 0 and 30): 0 is today, 1 is tomorrow, etc.");
        System.exit(1);
    }

    private static Date getDateFromOffset(int offset) {
        if (offset < 0 || offset > 30) usage();

        long millis = Util.now() + (long) offset * Time.MILLIS_PER_DAY;
        return new Date(millis);
    }

    public static void main(String[] arg) throws Exception {
        String cacheFolder = null;
        String agencyID = null;
        String outputFolder = null;
        String gtfsURL = null;
        Date date = new Date();
        Boolean uploadToGcloud = false;

        for (int i=0; i<arg.length; i++) {
            if ((arg[i].equals("-c") || arg[i].equals("--cache-folder")) && i < arg.length - 1) {
                cacheFolder = arg[++i];
                continue;
            }

            if ((arg[i].equals("-o") || arg[i].equals("--output-folder")) && i < arg.length - 1) {
                outputFolder = arg[++i];
                continue;
            }

            if ((arg[i].equals("-a") || arg[i].equals("--agency-id")) && i < arg.length - 1) {
                agencyID = arg[++i];
                continue;
            }

            if ((arg[i].equals("-U") || arg[i].equals("--url")) && i < arg.length - 1) {
                gtfsURL = arg[++i];
                continue;
            }

            if ((arg[i].equals("-d") || arg[i].equals("--date")) && i < arg.length - 1) {
                String s = arg[++i];

                if (s.indexOf('/') > 0) {
                    date = Time.parseDate("MM/dd/yy", s);
                } else {
                    int offset = -1;
                    try {
                        offset = Integer.parseInt(s);
                    } catch (NumberFormatException e) {
                        usage();
                    }
                    date = getDateFromOffset(offset);
                }
                continue;
            }

            if (arg[i].equals("-u") || arg[i].equals("--upload")) {
                uploadToGcloud = true;
                continue;
            }

            break;
        }

        if (agencyID == null) usage();

        new BlockDataGenerator(cacheFolder, outputFolder, agencyID, gtfsURL, date, uploadToGcloud);
    }

    class TripRecord {
        public String id;
        public String headsign;
        public int startTime;
        public int endTime;
    }

    class BlockRecord {
        public String id;
        public List<TripRecord> trips;

        BlockRecord() {
            trips = new ArrayList<TripRecord>();
        }

        void add(TripRecord tr) {
            trips.add(tr);
        }
    }
}
