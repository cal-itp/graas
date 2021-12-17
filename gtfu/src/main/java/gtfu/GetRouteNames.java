package gtfu;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class GetRouteNames {
    public GetRouteNames(TextFile stops, TextFile stopTimes, TextFile trips) {
        Map<String, String> stopMap = new HashMap<String, String>();

        TextFile file = stops;
        CSVHeader header = new CSVHeader(file.getNextLine());
        CSVRecord record = null;
        String line = null;

        for (;;) {
            line = file.getNextLine();
            if (line == null) break;

            record = new CSVRecord(header, line);
            String stopID = record.get("stop_id");
            String stopName = record.get("stop_name");

            stopMap.put(stopID, stopName);
        }

        //Debug.log("- stopMap: " + stopMap);

        Map<String, TripData> tripMap = new HashMap<String, TripData>();

        file = stopTimes;
        header = new CSVHeader(file.getNextLine());
        TripData tripData = new TripData();
        String lastStopID = null;
        String tripID = null;

        for (;;) {
            line = file.getNextLine();
            if (line == null) break;

            record = new CSVRecord(header, line);
            tripID = record.get("trip_id");
            String stopID = record.get("stop_id");

            if (!tripID.equals(tripData.getID())) {
                if (tripData.getID() != null) {
                    tripData.setToStop(stopMap.get(lastStopID));
                    tripMap.put(tripData.getID(), tripData);
                }

                String fromStop = stopMap.get(record.get("stop_id"));
                String fromTime = record.get("departure_time");
                tripData = new TripData(tripID, fromStop, fromTime);
            }

            lastStopID = stopID;
        }

        if (tripID != null) {
            tripData.setToStop(stopMap.get(lastStopID));
            tripMap.put(tripID, tripData);
        }

        //Debug.log("- tripMap: " + tripMap);

        file = trips;
        header = new CSVHeader(file.getNextLine());
        List<TripData> list = new ArrayList<TripData>();
        int maxStrLength = 0;

        for (;;) {
            line = file.getNextLine();
            if (line == null) break;

            record = new CSVRecord(header, line);
            tripID = record.get("trip_id");
            TripData data = tripMap.get(tripID);

            int len = data.toString().length();

            if (len > maxStrLength) {
                maxStrLength = len;
            }

            list.add(data);
        }

        System.out.println("{");

        for (int i=0; i<list.size(); i++) {
            System.out.println(String.format("    %s%s", list.get(i), i == list.size() - 1 ? "" : ","));
        }

        System.out.println("}");
    }

    private static void getURLContents(String u) {
        try {
            URL url = new URL(u);
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod("GET");
            int contentLength = conn.getContentLength();
            Debug.log("- contentLength: " + contentLength);
            int responseCode = conn.getResponseCode();
            Debug.log("- responseCode: " + responseCode);

            byte[] buf = new byte[16 * 1024];
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            int totalRead = 0;
            long lastSeconds = 0;

            for (;;) {
                int read = in.read(buf, 0, buf.length);
                if (read < 0) break;

                bos.write(buf, 0, read);

                totalRead += read;

                long seconds = Util.now() / 100;

                if (seconds != lastSeconds) {
                    System.out.print(String.format("\r  %d%%", (int)((float)totalRead / contentLength * 100)));
                    System.out.flush();
                }

                lastSeconds = seconds;
            }

            System.out.println("\r  100%");

            in.close();

            FileOutputStream fos = new FileOutputStream("google_transit.zip");
            fos.write(bos.toByteArray());
            fos.close();
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public static void main(String[] arg) throws IOException {
        if (arg.length == 0) {
            System.err.println("usage: GetRouteNames <gtfs-folder>|<gtfs-url>");
        }

        String s = arg[0];

        if (s.toLowerCase().endsWith(".zip")) {
            getURLContents(s);
            ZipFile zip = new ZipFile("google_transit.zip");
            Enumeration<? extends ZipEntry> e = zip.entries();

            while (e.hasMoreElements()) {
                Debug.log("-- " + e.nextElement());
            }

            new GetRouteNames(
                new TextFile(zip.getInputStream(zip.getEntry("stops.txt"))),
                new TextFile(zip.getInputStream(zip.getEntry("stop_times.txt"))),
                new TextFile(zip.getInputStream(zip.getEntry("trips.txt")))
            );
        } else {
            new GetRouteNames(
                new TextFile(s + "/stops.txt"),
                new TextFile(s + "/stop_times.txt"),
                new TextFile(s + "/trips.txt")
            );
        }
    }
}

class TripData {
    private static final String[] TRUNCATORS = {
        "(", " at ", "/"
    };

    private String id;
    private String fromStop;
    private String fromTime;
    private String toStop;

    TripData() {
    }

    TripData(String id, String fromStop, String fromTime) {
        this.id = id;
        this.fromStop = truncate(fromStop, TRUNCATORS);
        this.fromTime = formatTime(fromTime);
    }

    public String getID() {
        return id;
    }

    public void setToStop(String toStop) {
        this.toStop = truncate(toStop, TRUNCATORS);
    }

    private String truncate(String s, String[] truncators) {
        int minIndex = Integer.MAX_VALUE;
        String sl = s.toLowerCase();

        for (String t : truncators) {
            int index = sl.indexOf(t);

            if (index >=0 && index < minIndex) {
                minIndex = index;
            }
        }

        if (minIndex < s.length()) {
            s = s.substring(0, minIndex);
        }

        return s;
    }

    private String formatTime(String time) {
        String[] arg = time.split(":");
        int hour = Integer.parseInt(arg[0]);
        int min = Integer.parseInt(arg[1]);
        String ampm = "am";

        if (hour > 12) {
            hour -= 12;
            ampm = "pm";
        } else if (hour == 0) {
            hour = 12;
        }

        return String.format("%d:%02d %s", hour, min, ampm);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("\"");
        sb.append(fromStop.trim());
        sb.append(" -> ");
        sb.append(toStop.trim());
        sb.append(" @ ");
        sb.append(fromTime);
        sb.append("\": \"");
        sb.append(id);
        sb.append("\"");

        return sb.toString();
    }
}
