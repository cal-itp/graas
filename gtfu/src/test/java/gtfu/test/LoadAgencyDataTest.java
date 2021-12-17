package gtfu.test;

import java.util.Map;

import gtfu.ConsoleProgressObserver;
import gtfu.Debug;
import gtfu.Util;
import gtfu.TripCollection;
import gtfu.RouteCollection;
import gtfu.ShapeCollection;

import gtfu.tools.AgencyYML;

public class LoadAgencyDataTest {
    public LoadAgencyDataTest(String cacheDir, String agencyID) throws Exception {
        Debug.log("LoadAgencyDataTest.LoadAgencyDataTest()");
        Debug.log("- cacheDir: " + cacheDir);
        Debug.log("- agencyID: " + agencyID);

        AgencyYML yml = new AgencyYML();
        String gtfsUrl = yml.getURL(agencyID);
        Debug.log("- gtfsUrl: " + gtfsUrl);

        ConsoleProgressObserver progressObserver = new ConsoleProgressObserver(40);
        Util.updateCacheIfNeeded(cacheDir, agencyID, gtfsUrl, progressObserver);

        Map<String, Object> collections = Util.loadCollections(cacheDir, agencyID, progressObserver);

        TripCollection tripCollection = (TripCollection)collections.get("trips");
        Debug.log("- tripCollection.getSize(): " + tripCollection.getSize());

        RouteCollection routeCollection = (RouteCollection)collections.get("routes");
        Debug.log("- routeCollection.getSize(): " + routeCollection.getSize());

        ShapeCollection shapeCollection = (ShapeCollection)collections.get("shapes");
        Debug.log("- shapeCollection.getSize(): " + shapeCollection.getSize());
    }

    private static void usage() {
        System.err.println("usage: LoadAgencyDataTest -c|--cache-dir <cache-dir> -a|--agency-id <agency-id>");
        System.exit(1);
    }

    public static void main(String[] arg) throws Exception {
        String cacheDir = null;
        String agencyID = null;

        for (int i=0; i<arg.length; i++) {
            if ((arg[i].equals("-c") || arg[i].equals("--cache-dir")) && i < arg.length - 1) {
                cacheDir = arg[++i];
                continue;
            }

            if ((arg[i].equals("-a") || arg[i].equals("--agency-id")) && i < arg.length - 1) {
                agencyID = arg[++i];
                continue;
            }

            usage();
        }

        if (cacheDir == null || agencyID == null) usage();

        new LoadAgencyDataTest(cacheDir, agencyID);
    }
}