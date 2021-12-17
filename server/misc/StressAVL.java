import java.io.*;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.Map;

public class StressAVL {
    static PrivateKey key;
    private static final String PEM_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_FOOTER = "-----END PRIVATE KEY-----";

    private static void usage() {
        System.err.println("StressAVL -url <avl-server-url> -count <n> [-lat <n.m>] [-long <n.m>]");
        System.exit(0);
    }

    private static PrivateKey getKey() {
        String pem = Util.getFileAsString("keys/sbc/id_rsa");
        Debug.log("- pem: " + pem);

        int i1 = pem.indexOf(PEM_HEADER) + PEM_HEADER.length();
        int i2 = pem.indexOf(PEM_FOOTER);
        String b64 = pem.substring(i1, i2);
        Debug.log("- b64: " + b64);

        return Util.importPrivateKey(b64);
    }

    public static void main(String[] arg) throws IOException {
        float lat = 40.7831f;
        float lon = 73.9712f;
        String url = null;
        int count = 0;

        key = getKey();

        Debug.log("- arg.length: " + arg.length);

        for (int i=0; i<arg.length; i++) {
            Debug.log("- arg[i]: " + arg[i]);

            if (arg[i].equals("-url") && i < arg.length - 1) {
                url = arg[++i];
                Debug.log("- url: " + url);
                continue;
            }

            if (arg[i].equals("-count") && i < arg.length - 1) {
                try {count = Integer.parseInt(arg[++i]);}
                catch (IllegalArgumentException e) {usage();}
                Debug.log("- count: " + count);

                if (count <= 0) usage();
                continue;
            }

            if (arg[i].equals("-lat") && i < arg.length - 1) {
                try {lat = Float.parseFloat(arg[++i]);}
                catch (IllegalArgumentException e) {usage();}
                continue;
            }

            if (arg[i].equals("-long") && i < arg.length - 1) {
                try {lon = Float.parseFloat(arg[++i]);}
                catch (IllegalArgumentException e) {usage();}
                continue;
            }

            usage();
        }

        Debug.log("- lat: " + lat);
        Debug.log("- lon: " + lon);

        if (url == null || count == 0) {
            usage();
        }

        int vehicleID = 1;
        int tripID = 60000;

        for (int i=0; i<count; i++) {
            new SimVehicle(new VehicleConfig(url, "avl-stress", "" + vehicleID, "" + tripID, lat, lon));

            vehicleID++;
            tripID++;
        }
    }
}

class VehicleConfig {
    String url;
    String agencyID;
    String id;
    String tripID;
    float lat;
    float lon;

    VehicleConfig(String url, String agencyID, String id, String tripID, float lat, float lon) {
        this.url = url;
        this.agencyID = agencyID;
        this.id = id;
        this.tripID = tripID;
        this.lat = lat;
        this.lon = lon;
    }
}

class SimVehicle implements Runnable {
    static final String MSG_FORMAT;
    static final Map<String, String> REQUEST_MAP;

    static {
        MSG_FORMAT = "{\"uuid\": \"avl-stress\", \"agent\": \"none\", \""
        + "timestamp\": \"%d\", \"lat\": \"%f\", \"long\": \"%f\", \"speed\": \"%d\", \""
        + "heading\": \"%d\", \"trip-id\": \"%s\", \"agency-id\": \"%s\", \"vehicle-id\""
        + ": \"%s\", \"pos-timestamp\": \"%d\", \"accuracy\": \"15\"}";

        REQUEST_MAP = new HashMap<String, String>();
        REQUEST_MAP.put("Content-Type", "application/json");
    }

    VehicleConfig config;

    SimVehicle(VehicleConfig config) {
        this.config = config;

        sleep(getRandom(3000));

        Thread t = new Thread(this);
        t.start();
    }

    private void sleep(int millis) {
        try {Thread.sleep(millis);}
        catch (InterruptedException e) {e.printStackTrace();}
    }

    private long now() {
        return System.currentTimeMillis() / 1000;
    }

    private int getRandom(int upper) {
        return (int)(Math.random() * upper);
    }

    private int getRandom(int lower, int upper) {
        return lower + getRandom(upper - lower);
    }

    private void sendGPSUPdate() {
        int speed = getRandom(30);
        int heading = getRandom(360);

        String data = String.format(MSG_FORMAT, now(), config.lat, config.lon, speed, heading,
            config.tripID, config.agencyID, config.id, now());
        String msg = String.format("{\"data\": %s, \"sig\": \"%s\"}", data, Util.sign(data, StressAVL.key));

        //Debug.log("- msg: " + msg);

        HTTPUtil.post(config.url, msg, REQUEST_MAP);
    }

    public void run() {
        for (;;) {
            sendGPSUPdate();

            int millis = 3000 + getRandom(-500, 500);
            sleep(millis);
        }
    }
}
