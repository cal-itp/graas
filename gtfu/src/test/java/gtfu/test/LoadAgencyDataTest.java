package gtfu.test;

import java.util.Map;

import gtfu.ConsoleProgressObserver;
import gtfu.Debug;
import gtfu.EmailFailureReporter;
import gtfu.FailureReporter;
import gtfu.ProgressObserver;
import gtfu.Util;
import gtfu.TripCollection;
import gtfu.RouteCollection;
import gtfu.ShapeCollection;
import gtfu.Recipients;

import gtfu.tools.AgencyYML;


public class LoadAgencyDataTest {

    public LoadAgencyDataTest(String cacheDir, String agencyID) throws Exception {
        new LoadAgencyDataTest(cacheDir, agencyID, false);
    }

    public LoadAgencyDataTest(String cacheDir, String agencyID, boolean skipCacheUpdate) throws Exception {
        Debug.log("LoadAgencyDataTest.LoadAgencyDataTest()");
        Debug.log("- cacheDir: " + cacheDir);
        Debug.log("- agencyID: " + agencyID);

        AgencyYML yml = new AgencyYML();
        String gtfsUrl = yml.getURL(agencyID);
        Debug.log("- gtfsUrl: " + gtfsUrl);

        ConsoleProgressObserver progressObserver = new ConsoleProgressObserver(40);

        if (!skipCacheUpdate){
            Util.updateCacheIfNeeded(cacheDir, agencyID, gtfsUrl, progressObserver);
        }

        Map<String, Object> collections = Util.loadCollections(cacheDir, agencyID, progressObserver, true);

        TripCollection tripCollection = (TripCollection)collections.get("trips");
        Debug.log("- tripCollection.getSize(): " + tripCollection.getSize());

        RouteCollection routeCollection = (RouteCollection)collections.get("routes");
        Debug.log("- routeCollection.getSize(): " + routeCollection.getSize());

        ShapeCollection shapeCollection = (ShapeCollection)collections.get("shapes");
        Debug.log("- shapeCollection.getSize(): " + shapeCollection.getSize());
    }

    private static void usage() {
        System.err.println("usage: LoadAgencyDataTest -c|--cache-dir <cache-dir> -a|--agency-id <agency-id>");
        System.err.println("usage: LoadAgencyDataTest -c|--cache-dir <cache-dir> -u|--url <url>");
        System.err.println("    <url> is assumed to point to a plain text document that has an agency ID per line");
        System.exit(1);
    }

    public static void main(String[] arg) throws Exception {
        String cacheDir = null;
        String agencyID = null;
        String url = null;

        Recipients r = new Recipients();
        String[] recipients = r.get("error_report");

        FailureReporter reporter = new EmailFailureReporter(recipients, "LoadAgencyDataTest Report");
        Util.setReporter(reporter);

        for (int i=0; i<arg.length; i++) {
            if ((arg[i].equals("-c") || arg[i].equals("--cache-dir")) && i < arg.length - 1) {
                cacheDir = arg[++i];
                continue;
            }

            if ((arg[i].equals("-a") || arg[i].equals("--agency-id")) && i < arg.length - 1) {
                agencyID = arg[++i];
                continue;
            }

            if ((arg[i].equals("-u") || arg[i].equals("--url")) && i < arg.length - 1) {
                url = arg[++i];
                continue;
            }

            usage();
        }

        if (cacheDir == null || (agencyID == null && url == null)) usage();

        if (url == null) {
            reporter.addLine("agency: " + agencyID);

            try {
                new LoadAgencyDataTest(cacheDir, agencyID);
            } catch (Exception e) {
                Debug.error("* test failed: " + e);
            }
        } else {
            ProgressObserver po = new ConsoleProgressObserver(40);
            String context = Util.getURLContent(url, po);
            String[] agencyIDList = context.split("\n");

            for (String id : agencyIDList) {
                Debug.log("-- id: " + id);

                reporter.addLine("agency: " + id);

                try {
                    new LoadAgencyDataTest(cacheDir, id);
                } catch (Exception e) {
                    Debug.error("* test failed: " + e);
                }
            }
        }

        reporter.send();
    }
}
