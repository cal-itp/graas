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
    public BlockDataGenerator(String cacheFolder, String outputFile, String agencyID, Date date) throws Exception {
        Debug.log("BlockDataGenerator.BlockDataGenerator()");
        Debug.log("- cacheFolder: " + cacheFolder);
        Debug.log("- outputFile: " + outputFile);
        Debug.log("- agencyID: " + agencyID);
        Debug.log("- date: " + date);

        Map<String, Object> collections = Util.loadCollections(cacheFolder, agencyID, new ConsoleProgressObserver(40));
        BlockCollection blocks = (BlockCollection)collections.get("blocks");
        TripCollection trips = (TripCollection)collections.get("trips");

        List<BlockRecord> list = new ArrayList<BlockRecord>();

        for (Block b : blocks) {
            BlockRecord br = new BlockRecord();

            br.id = b.id;
            br.vehicleId = "";

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

        try (FileOutputStream fos = new FileOutputStream(outputFile);
            PrintStream out = new PrintStream(fos)) {
            out.println(Util.objectToJSON(list, true));
        }
    }

    private static void usage() {
        System.err.println("usage: BlockDataGenerator -c|--cache-folder <cache-folder> -a|--agency-id <agency-id> [-d|--date <mm/dd/yy>]");
        System.err.println("    <cache-folder> a temp folder for unpacking and caching static GTFS data by agency");
        System.err.println("    <agency-id> a transit agency identifier constructed from the alphabet of [a-z\\-]");
        System.err.println("    <mm/dd/yy> valid 24 hour period for block data, defaults to current day if omitted");
        System.exit(1);
    }

    public static void main(String[] arg) throws Exception {
        String cacheFolder = null;
        String agencyID = null;
        String outputFile = System.getenv("HOME") + "/tmp/blocks.json";
        Date date = new Date();

        for (int i=0; i<arg.length; i++) {
            if ((arg[i].equals("-c") || arg[i].equals("--cache-folder")) && i < arg.length - 1) {
                cacheFolder = arg[++i];
                continue;
            }

            if ((arg[i].equals("-o") || arg[i].equals("--output-file")) && i < arg.length - 1) {
                outputFile = arg[++i];
                continue;
            }

            if ((arg[i].equals("-a") || arg[i].equals("--agency-id")) && i < arg.length - 1) {
                agencyID = arg[++i];
                continue;
            }

            if ((arg[i].equals("-d") || arg[i].equals("--date")) && i < arg.length - 1) {
                date = Time.parseDate("MM/dd/yy", arg[++i]);
                continue;
            }

            break;
        }

        if (agencyID == null || cacheFolder == null) usage();

        new BlockDataGenerator(cacheFolder, outputFile, agencyID, date);
    }

    class TripRecord {
        public String id;
        public int startTime;
        public int endTime;
    }

    class BlockRecord {
        public String id;
        public long lastModified;
        public long validFrom;
        public long validTo;
        public String agencyId;
        public String vehicleId;
        public List<TripRecord> trips;

        BlockRecord() {
            trips = new ArrayList<TripRecord>();
        }

        void add(TripRecord tr) {
            trips.add(tr);
        }
    }
}