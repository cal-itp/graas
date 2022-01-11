package gtfu.tools;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import gtfu.GPSData;
import gtfu.Trip;
import gtfu.TripCollection;
import gtfu.TripReportData;
import gtfu.Debug;

public class DayLogSlicer {
    private Map<String, List<GPSData>> gpsMap;
    private List<TripReportData> tdList;
    private Map<String, TripReportData> tdMap;
    private int startSecond;

    public DayLogSlicer(TripCollection tripCollection, List<String> lines) {
        gpsMap = new HashMap();
        tdList = new ArrayList();
        tdMap = new HashMap();
        startSecond = -1;

        for (String line : lines) {
            String[] arg = line.split(",");
            int seconds = Integer.parseInt(arg[1]);
            float lat = Float.parseFloat(arg[2]);
            float lon = Float.parseFloat(arg[3]);
            String tripID = arg[4];

            if (startSecond < 0) {
                startSecond = seconds;
            }

            List<GPSData> list = gpsMap.get(tripID);

            if (list == null) {
                list = new ArrayList<GPSData>();
                gpsMap.put(tripID, list);
            }

            list.add(new GPSData(seconds * 1000l, lat, lon));
        }

        // Debug.log("- map.size(): " + map.size());

        for (String id : gpsMap.keySet()) {
            Trip trip = tripCollection.get(id);

            if (trip == null) {
                System.err.println("* no trip found for id " + id + ", skipping");
                continue;
            }

            // Debug.log("++ id: " + trip.getName());

            int start = trip.getStartTime() * 1000;
            // Debug.log("++ start: " + Time.getHMForMillis(start));

            int t1 = trip.getTimeAt(0);
            int t2 = trip.getTimeAt(trip.getStopSize() - 1);
            int duration = t2 - t1;
            int durationMins = duration / 1000 / 60;
            // Debug.log("++   end: " + Time.getHMForMillis(start + duration));
            // Debug.log("++   duration: " + Time.getHMForMillis(start + duration));
            // Debug.log("++   durationMins: " + durationMins);

            // Filter out trips shorter than 15 min
            if (durationMins >= 15) {
                TripReportData td = new TripReportData(id, trip.getName(), start, duration);
                tdList.add(td);
                tdMap.put(id, td);
            }
        }

        Collections.sort(tdList);
    }

    public Map<String, List<GPSData>> getMap() {
        return gpsMap;
    }

    public List<TripReportData> getTripReportDataList() {
        return tdList;
    }

    public Map<String, TripReportData> getTripReportDataMap() {
        return tdMap;
    }

    public int getStartSecond() {
        return startSecond;
    }
}
