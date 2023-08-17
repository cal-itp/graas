package gtfu;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Objects;
import java.util.List;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.google.transit.realtime.GtfsRealtime.Position;

public class MonitorPositionUpdates {
    private static final String API_KEY_ARG = "-api-key";
    private static final String API_KEY_NAME_ARG = "-api-key-name";
    private static final String RAW_ARG = "-raw";
    private static final String CSV_ARG = "-csvoutput";
    private static final String ID_ARG = "-id";
    private static final String LOOKUP_ARG = "-lookup-file";
    private static final String SLEEP_MILLIS_ARG = "-sleep-millis";
    private static final String URL_ARG = "-url";
    private static final String HELP_ARG = "-help";
    private static final String ARROW_LIST = "\u2191\u2197\u2192\u2198\u2193\u2199\u2190\u2196";
    private static final String AGENCY_QUERY = "agency=";

    static Map<String, LatLong> map = new HashMap<String, LatLong>();
    static long lastTimestamp;
    static List<VehicleStats> lastList;
    static URL url;

    static char getArrowForBearing(int bearing) {
        return ARROW_LIST.charAt((int)((bearing % 360 + 22.5) / 45) % ARROW_LIST.length());
    }

    private static void dumpRawFeed(FeedMessage msg, String id) throws Exception {
        System.out.println("raw feed for: " + id);
        System.out.println();

        for (FeedEntity entity : msg.getEntityList()) {
            System.out.println(entity);
        }
    }

    private static void dumpCSV(FeedMessage msg) throws Exception {
        System.out.println("vehicleid,timestamp,lat,long,heading,speed");

        for (FeedEntity entity : msg.getEntityList()) {
            VehiclePosition vp = entity.getVehicle();

            String vid = "";
            String lat = "";
            String lon = "";
            String heading = "";
            String speed = "";
            String timestamp = "";

            if (vp.hasVehicle()) {
                VehicleDescriptor vd = vp.getVehicle();

                if (vd.hasLabel()) {
                    vid = vd.getLabel();
                } else if (vd.hasId()) {
                    vid = vd.getId();
                }
            }

            if (vp.hasPosition()) {
                Position pos = vp.getPosition();

                lat = "" + pos.getLatitude();
                lon = "" + pos.getLongitude();

                if (pos.hasBearing()) {
                    heading = "" + (int)pos.getBearing();
                }

                if (pos.hasSpeed()) {
                    speed = "" + (int)pos.getSpeed();
                }
            }

            if (vp.hasTimestamp()) {
                timestamp = "" + vp.getTimestamp();
            }

            System.out.println(String.format("%s,%s,%s,%s,%s,%s", vid, timestamp, lat, lon, heading, speed));
        }
    }

    private static void dumpFeed(FeedMessage msg, String agency) throws Exception {
        System.out.print("\033[H\033[2J");
        System.out.flush();

        if (agency == null) {
            agency = "unknown";
        }

        String ipAddress = getIP(url);

        FeedHeader header = msg.getHeader();
        long ts = header.getTimestamp();
        //System.out.println("-  ts: " + ts);
        long now =  System.currentTimeMillis() / 1000;
        //System.out.println("- now: " + now);

        int lastRefresh = (int)(now - ts);
        System.out.println(String.format("last update from '%s' for '%s': %d seconds ago", ipAddress, agency, lastRefresh));

        boolean refresh = (ts != lastTimestamp);
        lastTimestamp = ts;

        if (lastList != null && refresh) {
            for (VehicleStats v : lastList) {
                if (v.lat != 0 && v.lon != 0) {
                    map.put(v.id, new LatLong(v.lat, v.lon));
                }
            }
        }

        int count = 0;

        List<VehicleStats> list = new ArrayList<VehicleStats>();

        for (FeedEntity entity : msg.getEntityList()) {
            if (entity.hasVehicle()) {
                list.add(new VehicleStats(entity.getVehicle()));
            }
        }

        System.out.println(String.format("vehicle count: %d", list.size()));

        Collections.sort(list);

	int maxDisplayCount = 100;

        for (int i=0; i<Math.min(maxDisplayCount, list.size()); i++) {
            System.out.print(i % 4 == 0 ? '\n' : ", ");
            System.out.print(list.get(i));
        }

        System.out.println();
	if (list.size() > maxDisplayCount) System.out.println("...");

        lastList = list;
    }

    private static String getGTFSRTURLForAgencyID(String id, String filename) throws Exception {
        FileInputStream fis = new FileInputStream(filename);
        BufferedReader in = new BufferedReader(new InputStreamReader(fis));
        String url = null;
        String prefix = id + " ";

        for (;;) {
            String line = in.readLine();
            if (line == null) break;

            if (line.startsWith(prefix)) {
                url = line.split(" ")[2];
                break;
            }
        }

        return url;
    }

    private static void help() {
        System.out.println("attributes shown per vehicle:");
        System.out.println("- vehicle label or ID (drawn in reverse color, yellow if tardy on updates, red if very tardy)");
        System.out.println("- distance traveled since last update, in feet or miles ('no pos' if lat/long missing, 'no hist' if no previous lat/long)");
        System.out.println("- vehicle bearing drawn as directional arrow (if available)");
        System.out.println("- vehicle speed (if available)");

        System.exit(1);
    }

    private static void usage() {
        System.err.println("usage: MonitorPositionUpdates [-raw [-csvoutput]] [-help] {-url <url> | -id <id> -lookup-file <lookup-file>}");
        System.exit(0);
    }

    private static String getIP(URL url) throws Exception {
        String host = url.getHost();
        InetAddress address = InetAddress.getByName(host);
        return address.getHostAddress();
    }

    public static void main(String[] arg) throws Exception {
        String id = null;
        String lookupFile = null;
        String apiKeyName = "x-api-key";
        String apiKey = null;
        boolean raw = false;
        boolean csvoutput = false;
        boolean help = false;
    	int sleepMillis = 1000;

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                System.out.println();
            }
        });

        for (int i=0; i<arg.length; i++) {
            if (arg[i].equals(API_KEY_ARG) && i + 1 < arg.length) {
                apiKey = arg[++i];
            }

            if (arg[i].equals(API_KEY_NAME_ARG) && i + 1 < arg.length) {
                apiKeyName = arg[++i];
            }

            if (arg[i].equals(RAW_ARG)) {
                raw = true;
            }

            if (arg[i].equals(CSV_ARG)) {
                csvoutput = true;
            }

            if (arg[i].equals(HELP_ARG)) {
                help = true;
            }

            if (arg[i].equals(ID_ARG) && i + 1 < arg.length) {
                id = arg[++i];
            }

            if (arg[i].equals(URL_ARG) && i + 1 < arg.length) {
                url = new URL(arg[++i]);
            }

            if (arg[i].equals(LOOKUP_ARG) && i + 1 < arg.length) {
                lookupFile = arg[++i];
            }

            if (arg[i].equals(SLEEP_MILLIS_ARG) && i + 1 < arg.length) {
                sleepMillis = Integer.parseInt(arg[++i]);
            }
        }

        if (help) help();

        if (url != null && id == null && lookupFile == null) {
            // url given directly, nothing else to do
        } else if (url == null && id != null && lookupFile != null) {
            url = new URL(getGTFSRTURLForAgencyID(id, lookupFile));
        } else {
            usage();
        }

        String query = url.getQuery();

        if (id == null && query != null && query.indexOf(AGENCY_QUERY) >= 0) {
            int i1 = query.indexOf(AGENCY_QUERY);
            int i2 = query.indexOf('&', i1);

            if (i2 < 0) id = query.substring(i1 + AGENCY_QUERY.length());
            else id = query.substring(i1 + AGENCY_QUERY.length(), i2);
        }

        if (raw) {
            String host = url.getHost();

            if (host.equals("127.0.0.1") || host.equals("localhost")) {
                // if we're connecting to localhost, chances
                // are the cert will be self-signed, which won't
                // go over well with any checks

                Util.disableSSLChecking();
            }

            URLConnection conn = url.openConnection();

            try (AutoCloseable ac = () -> ((HttpURLConnection)conn).disconnect()) {
                if (apiKey != null ) {
                    conn.setRequestProperty(apiKeyName, apiKey);
                }

                try (InputStream is = conn.getInputStream()) {
                    FeedMessage msg = FeedMessage.parseFrom(is);

                    if (!csvoutput) {
                        dumpRawFeed(msg, id != null ? id : url.toString());
                    } else {
                        dumpCSV(msg);
                    }
                }
            }
        } else {
            for (;;) {
                URLConnection conn = url.openConnection();

                try (AutoCloseable ac = () -> ((HttpURLConnection)conn).disconnect()) {
                    if (apiKey != null ) {
                        conn.setRequestProperty(apiKeyName, apiKey);
                    }

                    try (InputStream is = conn.getInputStream()) {
                        dumpFeed(FeedMessage.parseFrom(is), id);
                    }
                }

                Util.sleep(sleepMillis);
            }
        }
    }
}

class LatLong {
    double lat;
    double lon;

    public LatLong(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    public boolean equals(Object o) {
        if (o instanceof LatLong) {
            LatLong ll = (LatLong)o;
            return lat == ll.lat && lon == ll.lon;
        }

        return false;
    }

    public int hashCode() {
        return Objects.hash(new Double(lat), new Double(lon));
    }
}

class VehicleStats implements Comparable<VehicleStats> {
    private static final int REVERSE_ON         =  7;
    private static final int REVERSE_OFF        = 27;
    private static final int RED                = 31;
    private static final int GREEN              = 32;
    private static final int YELLOW             = 33;
    private static final int LIGHT_RED          = 91;
    private static final int LIGHT_GREEN        = 92;
    private static final int LIGHT_YELLOW       = 93;
    private static final int DEFAULT_FOREGROUND = 39;

    private static final int SECONDS_PER_MINUTE = 60;
    private static final int SECONDS_PER_HOUR = SECONDS_PER_MINUTE * 60;
    private static final int SECONDS_PER_DAY = SECONDS_PER_HOUR * 24;

    private static final int FEET_PER_MILE = 5280;

    String id;
    double lat;
    double lon;
    int bearing = -1;
    int speed;
    int seconds = -1;

    public VehicleStats(VehiclePosition vp) {
        id = "???";

        if (vp.hasVehicle()) {
            VehicleDescriptor vd = vp.getVehicle();

            if (vd.hasLabel()) {
                id = vd.getLabel();
            } else if (vd.hasId()) {
                id = vd.getId();
            }
        }

        if (vp.hasPosition()) {
            Position pos = vp.getPosition();

            lat = pos.getLatitude();
            lon = pos.getLongitude();

            if (pos.hasBearing()) {
                bearing = (int)pos.getBearing();
            }

            if (pos.hasSpeed()) {
                speed = (int)pos.getSpeed();
            }
        }

        if (vp.hasTimestamp()) {
            long then = vp.getTimestamp();
            seconds = (int)(System.currentTimeMillis() / 1000 - then);
        }
    }

    public String latLongDeltaToDistance(String id, double lat, double lon) {
        if (lat == 0 || lon == 0) return "no pos";

        LatLong ll = MonitorPositionUpdates.map.get(id);
        if (ll == null) return "no hist";

        int d = (int)Math.round(Util.getHaversineDistance(ll.lat, ll.lon, lat, lon));

        if (d < FEET_PER_MILE) {
            return d + " ft";
        } else {
            return String.format("%3.1f mi", (double)d / FEET_PER_MILE);
        }
    }

    public String getLastContactString() {
        if (seconds < SECONDS_PER_MINUTE) {
            return String.format("%2d s", seconds);
        } else if (seconds < SECONDS_PER_HOUR) {
            return String.format("%2d m", seconds / SECONDS_PER_MINUTE);
        } else if (seconds < SECONDS_PER_DAY) {
            return String.format("%2d h", seconds / SECONDS_PER_HOUR);
        } else {
            return String.format("%2d d", seconds / SECONDS_PER_DAY);
        }
    }

    public int compareTo(VehicleStats vs) {
        return id.compareToIgnoreCase(vs.id);
    }

    public String toString() {
        char bearingChar = ' ';
        if (bearing >= 0) bearingChar = MonitorPositionUpdates.getArrowForBearing(bearing);

        int idColor = GREEN;

        if (seconds >= 2 * 60) {
            idColor = YELLOW;
        }

        if (seconds >= 5 * 60) {
            idColor = RED;
        }

        return String.format("\033[7m\033[%dm%6s\033[39m\033[27m: %7s %c %2d mph %4s",
            idColor,
            id,
            latLongDeltaToDistance(id, lat, lon),
            bearingChar,
            speed,
            getLastContactString()
        );
    }
}
