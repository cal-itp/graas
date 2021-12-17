package gtfu;

import java.io.*;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.Map;

public class StressAVL {
    static PrivateKey key;

    private static void usage() {
        System.err.println("StressAVL -tripid <trip-id> -path <path-to-gtfs-files> -url <avl-server-url> -count <n>");
        System.exit(0);
    }

    public static void main(String[] arg) throws IOException {
        String url = null;
        String path = null;
        String tripID = null;
        int count = 0;

        key = CryptoUtil.importPrivateKeyFromFile("keys/sbc/id_rsa");

        for (int i=0; i<arg.length; i++) {
            if (arg[i].equals("-tripid") && i < arg.length - 1) {
                tripID = arg[++i];
                continue;
            }

            if (arg[i].equals("-path") && i < arg.length - 1) {
                path = arg[++i];
                continue;
            }

            if (arg[i].equals("-url") && i < arg.length - 1) {
                url = arg[++i];
                continue;
            }

            if (arg[i].equals("-count") && i < arg.length - 1) {
                try {count = Integer.parseInt(arg[++i]);}
                catch (IllegalArgumentException e) {usage();}

                if (count <= 0) usage();
                continue;
            }

            usage();
        }

        if (url == null || count == 0 || path == null || tripID == null) {
            usage();
        }

        Debug.log("- count: " + count);
        Debug.log("- path: " + path);
        Debug.log("- url: " + url);

        int vehicleID = 1;

        ShapeCollection shapeCollection = new ShapeCollection(path, null);
        StopCollection stopCollection = new StopCollection(path);
        TripCollection tripCollection = new TripCollection(path, stopCollection, shapeCollection, null);
        Trip trip = tripCollection.get(tripID);

        for (int i=0; i<count; i++) {
            new SimVehicle(trip, new VehicleConfig(url, "avl-stress", "" + vehicleID, tripID, key));

            vehicleID++;
        }
    }
}
