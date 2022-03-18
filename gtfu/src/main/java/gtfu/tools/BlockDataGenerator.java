package gtfu.tools;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import gtfu.*;

// A tool to extract the relationship between GTFS blocks and trips,
// more specifically to list all trips associated with a specific block.
// Output is formatted as JSON
public class BlockDataGenerator {
    public BlockDataGenerator(String cacheFolder, String outputFolder, String agencyID, Date date) throws Exception {
        Debug.log("BlockDataGenerator.BlockDataGenerator()");
        Debug.log("- cacheFolder: " + cacheFolder);
        Debug.log("- outputFolder: " + outputFolder);
        Debug.log("- agencyID: " + agencyID);
        Debug.log("- date: " + date);

        Map<String, Object> collections = Util.loadCollections(cacheFolder, agencyID, new ConsoleProgressObserver(40));
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
                tr.startTime = trip.getStartTime();
                tr.endTime = tr.startTime + trip.getDurationInSeconds();

                br.add(tr);
            }

            list.add(br);
        }

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(date);

        String outputFile = outputFolder + "/blocks-"
            + cal.get(cal.YEAR)
            + "-"
            + Util.pad("" + (cal.get(cal.MONTH) + 1), '0', 2)
            + "-"
            + Util.pad("" + cal.get(cal.DAY_OF_MONTH), '0', 2)
            + ".json"
        ;
        Debug.log("- outputFile: " + outputFile);

        try (FileOutputStream fos = new FileOutputStream(outputFile);
            PrintStream out = new PrintStream(fos)) {
            out.println(Util.objectToJSON(list, true));
        }
    }

    private static void usage() {
        System.err.println("usage: BlockDataGenerator -c|--cache-folder <cache-folder> -o|--output-folder <output-folder> -a|--agency-id <agency-id> [-d|--date <mm/dd/yy>|<n>]");
        System.err.println("    <cache-folder> a temp folder for unpacking and caching static GTFS data by agency");
        System.err.println("    <output-folder> folder to place output file in (file name will be 'blocks-<mm>-<dd>.json'");
        System.err.println("    <agency-id> a transit agency identifier constructed from the alphabet of [a-z\\-]");
        System.err.println("    <mm/dd/yy> valid 24 hour period for block data, defaults to current day if omitted");
        System.err.println("    <n> offset from today's date (must be between 0 and 30): 0 is today, 1 is tomorrow, etc.");
        System.exit(1);
    }

    private static Date getDateFromOffset(String s) {
        int offset = -1;

        try {
            offset = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            usage();
        }

        if (offset < 0 || offset > 30) usage();

        long millis = Util.now() + offset * Time.MILLIS_PER_DAY;
        return new Date(millis);
    }

    public static void main(String[] arg) throws Exception {
        String cacheFolder = null;
        String agencyID = null;
        String outputFolder = System.getenv("HOME") + "/tmp";
        Date date = new Date();

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

            if ((arg[i].equals("-d") || arg[i].equals("--date")) && i < arg.length - 1) {
                String s = arg[++i];

                if (s.indexOf('/') > 0) {
                    date = Time.parseDate("MM/dd/yy", arg[++i]);
                } else {
                    date = getDateFromOffset(s);
                }

                continue;
            }

            break;
        }

        if (agencyID == null || cacheFolder == null) usage();

        new BlockDataGenerator(cacheFolder, outputFolder, agencyID, date);
    }

    class TripRecord {
        public String id;
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
