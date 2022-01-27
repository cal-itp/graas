package gtfu;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;

public class Util {
    public static final String[] GTFS_FILE_LIST = {
        "agency.txt",
        "routes.txt",
        "shapes.txt",
        "stop_times.txt",
        "stops.txt",
        "trips.txt"
    };

    public static final String CACHE_ROOT = System.getProperty("java.io.tmpdir") + "/gtfu-cache";
    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss 'GMT'";

    private static final int FEET_PER_MILE = 5280;
    private final static int EARTH_RADIUS_IN_FEET = 20902231;
    private static final String WHITESPACE = " \t\r\n";

    private static FailureReporter NULL_REPORTER = new NullFailureReporter();

    private static FailureReporter reporter = NULL_REPORTER;

    public static String repeat(char c, int count) {
        StringBuffer sb = new StringBuffer();

        for (int i=0; i<count; i++) {
            sb.append(c);
        }

        return sb.toString();
    }

    public static boolean isAlphaNumeric(char c) {
        return c >= 'a' && c <= 'z'
            || c >= 'A' && c <= 'Z'
            || c >= '0' && c <= '9';
    }

    public static void implementMe() {
        throw new Fail("Implement me!");
    }

    public static String getDisplayDistance(int feet) {
        if (feet < FEET_PER_MILE) {
            return String.format("%d FEET", feet);
        } else if (feet < 10 * FEET_PER_MILE) {
            return String.format("%3.1f MILES", (float)feet / FEET_PER_MILE);
        } else {
            return String.format("%d MILES", feet / FEET_PER_MILE);
        }
    }

    public static void setReporter(FailureReporter reporter) {
        Util.reporter = reporter;
    }

    public static void fail(String s) {
        fail(s, true);
    }

    public static void fail(String s, boolean except) {
        fail(s, reporter, except);
    }

    public static void fail(String s, FailureReporter reporter, boolean except) {
        Debug.error(s);
        reporter.addLine("* " + s);

        if (except) throw new Fail(s);
    }

    public static String base64Encode(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    public static String base64Decode(String s) {
        return new String(Base64.getDecoder().decode(s), StandardCharsets.ISO_8859_1);
    }

    public static boolean isEmpty(String s) {
        return s == null || s.trim().length() == 0;
    }

    public static int getRandom(int limit) {
        return (int)(limit * Math.random());
    }

    public static float getRandomFloat(float lower, float upper) {
        if (upper < lower) {
            float tmp = lower;
            lower = upper;
            upper = tmp;
        }

        return lower + (float)((upper - lower) * Math.random());
    }

    public static long now() {
        return System.currentTimeMillis();
    }

    public static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // returns x/y in p lon/lat
    public static void latLongToScreenXY(Display display, Area area, ShapePoint p) {
        latLongToScreenXY(display.getWidth(), display.getHeight(), area, p);
    }

    public static void latLongToScreenXY(int displayWidth, int displayHeight, Area area, ShapePoint p) {
        float fractionLat = area.getLatFraction(p.lat);
        float fractionLong = area.getLongFraction(p.lon);

        float ratio = area.getAspectRatio();
        p.screenX = (int)(displayHeight * ratio * fractionLong);
        p.screenY = (int)(displayHeight * fractionLat);
    }

    // long == x, lat == y
    public static void screenXYToLatLong(Display display, Area area, ShapePoint p) {
        float xFraction = (float)p.screenX / (display.getHeight() * area.getAspectRatio());
        float yFraction = (float)p.screenY / display.getHeight();

        Debug.log("- xFraction: " + xFraction);
        Debug.log("- yFraction: " + yFraction);

        p.lat = area.topLeft.lat - area.getLatDelta() * yFraction;
        p.lon = area.topLeft.lon + area.getLongDelta() * xFraction;
    }

    public static Color getColorFromHexString(String s) {
        return new Color(Integer.parseInt(s, 16));
    }

    public static String stripQuotes(String s) {
        if (s.length() > 0 && s.charAt(0) == '"' && s.charAt(s.length() -1) == '"') {
            return s.substring(1, s.length() - 1);
        }

        return s;
    }

    public static String stripCommonWhitespace(String s) {
        StringBuilder sb = new StringBuilder();

        for (char c : s.toCharArray()) {
            if (WHITESPACE.indexOf(c) < 0) {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    public static String toHex(int n) {
        StringBuilder sb = new StringBuilder(Integer.toHexString(n));

        while (sb.length() < 8) {
            sb.insert(0, '0');
        }

        sb.insert(0, "0x");

        return sb.toString();
    }

    public static Area padArea(Area area, float factor) {
        float latAdjust = (area.getLatDelta() * (factor - 1) / 2);
        //Debug.log("- latAdjust: " + latAdjust);
        float longAdjust = (area.getLongDelta() * (factor - 1) / 2);
        //Debug.log("- longAdjust: " + longAdjust);

        float adjust = Math.min(latAdjust, longAdjust);

        Area newArea = new Area(area);

        newArea.bottomRight.lat -= adjust;
        newArea.topLeft.lat += adjust;

        newArea.topLeft.lon -= adjust;
        newArea.bottomRight.lon += adjust;

        return newArea;
    }

    public static List<ShapePoint> parsePoints(String s) {
        //Debug.log("- s: " + s);
        List<ShapePoint> list = new ArrayList<ShapePoint>();

        String[] tok = s.split(",");

        for (String t : tok) {
            //Debug.log("-- t: " + t);
            String[] itok = t.split("\\|");
            //Debug.log("-- itok[0]: " + itok[0]);
            //Debug.log("-- itok[1]: " + itok[1]);

            ShapePoint p = new ShapePoint(
                Float.parseFloat(itok[0]),
                Float.parseFloat(itok[1])
            );
            list.add(p);
            //Debug.log("-- p: " + p);
        }

        return list;
    }

    public static List<ShapePoint> makeList(ShapePoint p) {
        List<ShapePoint> list = new ArrayList<ShapePoint>();
        list.add(p);
        return list;
    }

    public static DisplayList toDisplayList(List<ShapePoint> list, Area area, int displayWidth, int displayHeight, Color color, int style, int radius) {
        return toDisplayList(list, area, null, displayWidth, displayHeight, color, style, radius);
    }

    public static DisplayList toDisplayList(List<ShapePoint> list, Area area, LatLongConverter converter, int displayWidth, int displayHeight, Color color, int style, int radius) {
        DisplayList displayList = new DisplayList(color, style, radius);
        ShapePoint pt = new ShapePoint();
        int count = 0;

        for (ShapePoint p : list) {
            if (!area.contains(p)) {
                count++;
                continue;
            }

            pt.lat = p.lat;
            pt.lon = p.lon;

            if (converter != null) {
                converter.latLongToScreenXY(displayWidth, displayHeight, area, pt);
            } else {
                latLongToScreenXY(displayWidth, displayHeight, area, pt);
            }

            displayList.addPoint(
                pt.screenX,
                pt.screenY
            );
        }

        return displayList;
    }

    public static byte[] readInput(InputStream is, ProgressObserver observer) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[2048];
        int total = 0;

        for (;;) {
            int n = is.read(buf);
            if (n < 0) break;

            total += n;
            if (observer != null) observer.update(total);
            bos.write(buf, 0, n);
        }

        return bos.toByteArray();
    }

    public static byte[] getFileContentsAsByteArray(String path) throws IOException {
        File f = new File(path);
        int size = (int)f.length();
        byte[] buf = new byte[size];

        try (FileInputStream fis = new FileInputStream(path)) {
           fis.read(buf);
        }

        return buf;
    }

    public static List<String> getFileContentsAsStrings(String path) throws IOException {
        List<String> list = new ArrayList();

        try (
            FileInputStream fis = new FileInputStream(path);
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader in = new BufferedReader(isr)
        ) {
            for (;;) {
                String line = in.readLine();
                if (line == null) break;

                list.add(line);
            }
        }

        return list;
    }

    public static String getResponseHeader(String url, String fieldName) {
        HttpURLConnection con = null;
        String value = null;

        try {
            URL myurl = new URL(url);
            con = (HttpURLConnection) myurl.openConnection();
            con.setRequestMethod("GET");
            //System.out.println("- response code: " + con.getResponseCode());
            value = con.getHeaderField(fieldName);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            con.disconnect();
        }

        return value;
    }

    public static String getURLContent(String url, ProgressObserver observer) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        downloadURLContent(url, bos, observer);
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    public static void downloadURLContent(String url, OutputStream os, ProgressObserver observer) {
        HttpURLConnection con = null;

        try {
            URL myurl = new URL(url);
            con = (HttpURLConnection) myurl.openConnection();
            con.setRequestMethod("GET");

            System.out.println("- response code: " + con.getResponseCode());

            try (InputStream is = con.getInputStream()) {
                os.write(readInput(is, observer));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            con.disconnect();
        }
    }

    private static void writeLastModifiedFile(String path, long millis) {
        try {
            try (FileOutputStream fos = new FileOutputStream(path + "/last-update.txt")) {
                String s = Time.formatDate(HTTP_DATE_FORMAT, new Date(millis)) + '\n';
                fos.write(s.getBytes());
            }
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    private static long getLastModified(String path) {
        String name = path + "/last-update.txt";
        File f = new File(name);

        if (!f.exists()) return 0;

        try {
            try (FileInputStream fis = new FileInputStream(name)) {
                BufferedReader in = new BufferedReader(new InputStreamReader(fis));
                String line = in.readLine();
                Debug.log("- line: " + line);
                return Time.parseDateAsLong(HTTP_DATE_FORMAT, line);
            }
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public static boolean updateCacheIfNeeded(String rootFolder, String agencyID, String gtfsURL, ProgressObserver progressObserver) {
        Debug.log("Util.updateCacheIfNeeded()");
        Debug.log("- agencyID: " + agencyID);
        Debug.log("- gtfsURL: " + gtfsURL);

        File file = new File(rootFolder);

        if (!file.exists()) {
            file.mkdir();
        }

        String name = rootFolder + "/" + agencyID;
        file = new File(name);

        if (!file.exists()) {
            file.mkdir();
            writeLastModifiedFile(name, 0);
        }

        long lastModifiedLocal = getLastModified(name);
        String remoteModified = Util.getResponseHeader(gtfsURL, "Last-Modified");
        Debug.log("- remoteModified: " + remoteModified);

        if (remoteModified == null) {
            Debug.log("* Last-Modified header not present in GTFS zip, re-downloading");
            remoteModified = Time.formatDate(HTTP_DATE_FORMAT, new Date());
        }

        long lastModifiedLRemote = Time.parseDateAsLong(HTTP_DATE_FORMAT, remoteModified);

        if (lastModifiedLRemote <= lastModifiedLocal) {
            if (progressObserver != null) {
                progressObserver.setMax(1);
                progressObserver.update(1);
            }

            return false;
        }

        Debug.log("+ remote GTFS zip is newer than cached version, updating...");
        writeLastModifiedFile(name, lastModifiedLRemote);

        try {
            String cl = Util.getResponseHeader(gtfsURL, "Content-Length");

            if (cl == null) {
                cl = "0";
            }

            int contentLength = Integer.parseInt(cl);
            Debug.log("- contentLength: " + contentLength);
            File zf = new File(name + "/gtfs.zip");
            FileOutputStream fos = new FileOutputStream(zf);

            if (progressObserver != null) {
                progressObserver.setMax(contentLength);
            }

            Util.downloadURLContent(gtfsURL, fos, progressObserver);
            fos.close();

            ZipFile zip = new ZipFile(zf);
            Enumeration<? extends ZipEntry> entries = zip.entries();

            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                Debug.log("-- " + e.getName());

                File ff = new File(name + "/" + e.getName());
                FileOutputStream foe = new FileOutputStream(ff);
                foe.write(Util.readInput(zip.getInputStream(e), null));
                foe.close();
            }
        } catch (IOException e) {
            Debug.error("couldn't update local cache from URL: " + e);
            return false;
        }

        return true;
    }

    public static String getAgencyID(String path) {
        Agency agency = new Agency(path);
        return agency.getID();
    }

    public static Map<String, String> getRouteMappings(String path) {
        Map<String, String> map = new HashMap<String, String>();
        TextFile tf = new TextFile(path + "/routes.txt");
        CSVHeader header = new CSVHeader(tf.getNextLine());

        for (;;) {
            String line = tf.getNextLine();
            if (line == null) break;

            CSVRecord record = new CSVRecord(header, line);
            map.put(record.get("route_long_name"), record.get("route_id"));
        }

        return map;
    }

    public static Map<String, String> getTripDepartures(String path) {
        Map<String, String> map = new HashMap<String, String>();
        TextFile tf = new TextFile(path + "/stop_times.txt");
        CSVHeader header = new CSVHeader(tf.getNextLine());
        String lastTripID = null;

        for (;;) {
            String line = tf.getNextLine();
            if (line == null) break;

            CSVRecord record = new CSVRecord(header, line);
            String tripID = record.get("trip_id");
            String departure = record.get("departure_time");
            int i0 = departure.indexOf(':');
            int i1 = departure.lastIndexOf(':');

            if (i1 > 0 && i0 != i1) {
                departure = departure.substring(0, i1);
            }


            if (!tripID.equals(lastTripID)) {
                map.put(tripID, departure);
            }

            lastTripID = tripID;
        }

        return map;
    }

    public static Map<String, String> getTripMappings(String path) {
        Map<String, String> departures = getTripDepartures(path);
        Map<String, String> map = new HashMap<String, String>();
        TextFile tf = new TextFile(path + "/trips.txt");
        CSVHeader header = new CSVHeader(tf.getNextLine());

        for (;;) {
            String line = tf.getNextLine();
            if (line == null) break;

            CSVRecord record = new CSVRecord(header, line);
            String tripID = record.get("trip_id");
            String headsign = record.get("trip_headsign");
            String name = headsign + " at " + departures.get(tripID);

            map.put(name, tripID);
        }

        return map;
    }

    public static String[] getSortedKeys(Map<String, String> map) {
        List<String> list = new ArrayList<String>(map.keySet());
        Collections.sort(list);
        String[] arr = new String[list.size()];
        return list.toArray(arr);
    }

    public static Map<String, String> getStopMappings(String path) {
        Map<String, String> map = new HashMap<String, String>();
        TextFile tf = new TextFile(path + "/stops.txt");
        CSVHeader header = new CSVHeader(tf.getNextLine());

        for (;;) {
            String line = tf.getNextLine();
            if (line == null) break;

            CSVRecord record = new CSVRecord(header, line);
            map.put(record.get("stop_name"), record.get("stop_id"));
        }

        return map;
    }

    public static String arrayToString(Object[] arg) {
        return arrayToString(arg, true);
    }

    public static String arrayToString(Object[] arg, boolean encloseInBraces) {
        StringBuilder sb = new StringBuilder("");
        if (encloseInBraces) sb.append("[");

        for (int i=0; i<arg.length; i++) {
            sb.append(arg[i]);

            if (i + 1 < arg.length) {
                sb.append(", ");
            }
        }

        if (encloseInBraces) sb.append("]");
        return sb.toString();
    }

    public static ProcessData runProcess(String path, String[] cmd, boolean waitForCompletion) {
        //Debug.log("Util.runProcess()");
        //Debug.log("- cmd: " + stringArrayToString(cmd));
        //Debug.log("- path: " + path);

        ProcessData pd = new ProcessData();
        StringBuilder sb = new StringBuilder();

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(cmd);
            Map<String, String> env = processBuilder.environment();
            env.put("PATH", path);
            //processBuilder.inheritIO();
            Process process = processBuilder.start();
            if (waitForCompletion) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    for (;;) {
                        String line = in.readLine();
                        if (line == null) break;
                        //Debug.log("- line: " + line);

                        sb.append(line);
                        sb.append('\n');
                    }
                }

                process.waitFor();

                pd.exitCode = process.exitValue();
                pd.output = sb.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return pd;
    }

    // 1:22:57
    public static String getDurationString(int seconds) {
        int hour = 0;
        int min = 0;

        StringBuilder sb = new StringBuilder();

        if (seconds > Time.SECONDS_PER_HOUR) {
            hour = seconds / Time.SECONDS_PER_HOUR;
            seconds -= hour * Time.SECONDS_PER_HOUR;

            sb.append(hour);
            sb.append(':');
        }

        if (seconds > Time.SECONDS_PER_MINUTE) {
            min = seconds / Time.SECONDS_PER_MINUTE;
            seconds -= min * Time.SECONDS_PER_MINUTE;

            if (hour > 0) {
                sb.append(String.format("%02d", min));
            } else {
                sb.append(min);
            }

            sb.append(':');
        }

        if (min > 0) {
            sb.append(String.format("%02d", seconds));
        } else {
            sb.append(seconds);
        }

        return sb.toString();
    }

    public static String bytesToHexString(byte[] buf) {
        StringBuilder sb = new StringBuilder();

        for (byte b : buf) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }
    public static int getLineCountForFile(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            int lines = 0;

            while (reader.readLine() != null) {
                lines++;
            }

            return lines;
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public static String getFileAsString(String filename) {
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(filename)));

            for (;;) {
                String line = in.readLine();
                if (line == null) break;

                sb.append(line);
            }

            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // return Haversine distance in feet between lat1/lon1 and lat2/lon2
    public static double getHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLam = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
            + Math.cos(phi1) * Math.cos(phi2)
            * Math.sin(deltaLam / 2) * Math.sin(deltaLam / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_IN_FEET * c;
    }

    // return a floating point value between 0 and 360 where 0 is true North,
    // and then going clockwise around the compass rose
    public static float getTrueNorthBearing(float x, float y) {
        float angle = (float)Math.toDegrees(Math.atan2(y, x)) + 90;
        if (angle < 0) angle += 360;
        return angle;
    }

    public static void notify(Object lock) {
        synchronized (lock) {
            lock.notify();
        }
    }

    public static void wait(Object lock, int millis) {
        synchronized (lock) {
            try {
                lock.wait(millis);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static Object readJSONObjectFromStream(InputStream is, Class _class) {
        TextFile tf = new TextFile(is);
        return readJSONObject(tf.getAllLinesAsString(), _class);
    }

    public static Object readJSONObjectFromFile(String filename, Class _class) {
        TextFile tf = new TextFile(filename);
        return readJSONObject(tf.getAllLinesAsString(), _class);
    }

    public static Object readJSONObject(String json, Class _class) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
            return objectMapper.readValue(json, _class);
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public static int getLineCount(String path) {
        Debug.log("Util.getLineCount()");
        // Debug.log("- path: " + path);

        int total = 0;

        for (String fn : GTFS_FILE_LIST) {
            int count = Util.getLineCountForFile(path + "/" + fn);
            if (fn.equals("stop_times.txt")) count *= 2;

            total += count;
        }

        // Debug.log("- total: " + total);
        return total;
    }

    public static String getGTFSURL(AgencyData[] list, String id) {
        //Debug.log("Util.getGTFSURL()");
        //Debug.log("- id: " + id);

        for (AgencyData d : list) {
            if (id.equals(d.agencyId)) {
                return d.staticGtfsUrl;
            }
        }

        return null;
    }

    public static Map<String, Object> loadCollections(String cacheRoot, String agencyID, ProgressObserver po) {
        return loadCollections(cacheRoot, agencyID, po, false);
    }

    public static Map<String, Object> loadCollections(String cacheRoot, String agencyID, ProgressObserver po, boolean skipErrors) {
        Debug.log("Util.loadCollections()");

        String path = cacheRoot + "/"  + agencyID;
        Debug.log("- path: " + path);
        int count = getLineCount(path);
        po.setMax(count);

        Map<String, Object> collections = new HashMap<String, Object>();

        CalendarCollection calendarCollection = new CalendarCollection(path);
        collections.put("calendars", calendarCollection);

        //Debug.log("shapes:");
        Timer t = new Timer("shapes");
        ShapeCollection shapeCollection = new ShapeCollection(path, po);
        collections.put("shapes", shapeCollection);
        t.dumpLap();
        Debug.log("- shapeCollection.getSize(): " + shapeCollection.getSize());
        //Debug.log("stops:");
        t = new Timer("stops");
        StopCollection stopCollection = new StopCollection(path);
        collections.put("stops", stopCollection);
        t.dumpLap();
        //Debug.log("- stopCollection.getSize(): " + stopCollection.getSize());
        //Debug.log("trips:");
        t = new Timer("trips");
        TripCollection tripCollection = new TripCollection(path, stopCollection, shapeCollection, po, skipErrors);
        collections.put("trips", tripCollection);
        //Debug.log("- tripCollection.getSize(): " + tripCollection.getSize());
        t.dumpLap();

        t = new Timer("schedules");
        TripScheduleCollection scheduleCollection = new TripScheduleCollection(path, tripCollection, stopCollection, po, skipErrors);
        collections.put("schedules", scheduleCollection);
        t.dumpLap();
        //Debug.log("- scheduleCollection.getSize(): " + scheduleCollection.getSize());

        //Debug.log("routes:");
        t = new Timer("routes");
        RouteCollection routeCollection = new RouteCollection(path, tripCollection, skipErrors);
        collections.put("routes", routeCollection);
        t.dumpLap();

        for (Route route : routeCollection) {
            route.computeArea();
        }

        return collections;
    }

    public static Map<String, Object> parseJSONasMap(String path) {
        try {
            return new ObjectMapper().readValue(new File(path), HashMap.class);
        } catch (Exception e) {
            throw new Fail(e);
        }
    }
}
