package gtfu;

import java.util.HashMap;
import java.util.Map;

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
    Trip trip;
    long startMillis;
    long lastMillis;
    ShapePoint lastPos;

    SimVehicle(Trip trip, VehicleConfig config) {
        this.trip = trip;
        this.config = config;

        Util.sleep(getRandom(3000));

        Thread t = new Thread(this);
        t.start();
    }

    private int getRandom(int upper) {
        return (int)(Math.random() * upper);
    }

    private int getRandom(int lower, int upper) {
        return lower + getRandom(upper - lower);
    }

    private float getSpeed(ShapePoint p) {
        if (lastPos == null) {
            return 0;
        }

        float dlat = p.lat = lastPos.lat;
        float dlon = p.lon = lastPos.lon;
        float deltaDegrees = (float)Math.sqrt(dlat * dlat + dlon * dlon);

        return Conversions.latLongDegreesToMeters(deltaDegrees)
            / Conversions.millisToSeconds((int)(Util.now() - lastMillis));
    }

    private float getHeading(ShapePoint p) {
        if (lastPos == null) {
            return 0;
        }

        return Util.getTrueNorthBearing(p.lon - lastPos.lon, p.lat - lastPos.lat);
    }

    private void sendGPSUPdate() {
        ShapePoint p = trip.getLocation((int)(Util.now() - startMillis));

        String data = String.format(MSG_FORMAT, Util.now(), p.lat, p.lon, getSpeed(p), getHeading(p),
            config.tripID, config.agencyID, config.id, Util.now());
        String msg = String.format("{\"data\": %s, \"sig\": \"%s\"}", data, CryptoUtil.sign(data, config.key));

        lastPos = new ShapePoint(p);
        lastMillis = Util.now();

        //Debug.log("- msg: " + msg);

        HTTPUtil.post(config.url, msg, REQUEST_MAP);
    }

    public void run() {
        startMillis = Util.now();

        for (;;) {
            sendGPSUPdate();

            int millis = 3000 + getRandom(-500, 500);
            Util.sleep(millis);
        }
    }
}
