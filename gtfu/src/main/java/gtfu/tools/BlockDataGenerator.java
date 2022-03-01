package gtfu.tools;

import java.util.Map;

import gtfu.*;

// A tool to extract the relationship between GTFS blocks and trips,
// more specifically to list all trips associated with a specific block.
// Output is formatted as JSON
public class BlockDataGenerator {
    public BlockDataGenerator(String cacheFolder, String agencyID) throws Exception {
        Debug.log("BlockDataGenerator.BlockDataGenerator()");
        Debug.log("- cacheFolder: " + cacheFolder);
        Debug.log("- agencyID: " + agencyID);

        Map<String, Object> collections = Util.loadCollections(cacheFolder, agencyID, new ConsoleProgressObserver(40));
        BlockCollection blocks = (BlockCollection)collections.get("blocks");
        System.out.println(Util.objectToJSON(blocks.list, true));
    }

    private static void usage() {
        System.err.println("usage: BlockDataGenerator -c|--cache-folder <cache-folder> -a|--agency-id <agency-id>");
        System.err.println("    <cache-folder> a temp folder for unpacking and caching static GTFS data by agency");
        System.err.println("    <agency-id> a transit agency identifier constructed from the alphabet of [a-z\\-]");
        System.exit(1);
    }

    public static void main(String[] arg) throws Exception {
        String cacheFolder = null;
        String agencyID = null;

        for (int i=0; i<arg.length; i++) {
            Debug.log("-- i: " + i);
            Debug.log("-- arg[i]: " + arg[i]);

            if ((arg[i].equals("-c") || arg[i].equals("--cache-folder")) && i < arg.length - 1) {
                cacheFolder = arg[++i];
                continue;
            }

            if ((arg[i].equals("-a") || arg[i].equals("--agency-id")) && i < arg.length - 1) {
                agencyID = arg[++i];
                continue;
            }

            break;
        }

        if (agencyID == null || cacheFolder == null) usage();

        new BlockDataGenerator(cacheFolder, agencyID);
    }
}