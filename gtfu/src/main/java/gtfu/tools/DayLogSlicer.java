package gtfu.tools;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import gtfu.GPSData;
import gtfu.Trip;
import gtfu.TripCollection;
import gtfu.RouteCollection;
import gtfu.Route;
import gtfu.TripReportData;
import gtfu.Debug;

public class DayLogSlicer {
    private Map<String, Map<String, GPSData>> gpsMap;
    private Map<String, String> uuidMap;
    private Map<String, String> agentMap;
    private Map<String, String> vehicleIdMap;
    private List<TripReportData> tdList;
    private Map<String, TripReportData> tdMap;
    private int startSecond;

  public DayLogSlicer(TripCollection tripCollection, RouteCollection routeCollection, List<String> lines) {
        gpsMap = new HashMap();
        uuidMap = new HashMap();
        agentMap = new HashMap();
        vehicleIdMap = new HashMap();
        tdList = new ArrayList();
        tdMap = new HashMap();
        startSecond = -1;

        for (String line : lines) {
            String[] arg = line.split(",");
            String vehicleId =  arg[0];
            int seconds = Integer.parseInt(arg[1]);
            float lat = Float.parseFloat(arg[2]);
            float lon = Float.parseFloat(arg[3]);
            String tripID = arg[4];
            String uuid = arg[6];
            String agent = null;
            //If agent is array of strings, extract the string value
            //There may be unexpected behavior if multiple strings in array
            //TODO: consider updating graas.js to send string rather than array of strings
            if(arg[7].contains("StringValue")){
                agent = arg[10];
                agent = agent.substring(8,agent.length() - 3);
            } else agent = arg[7];

            if (startSecond < 0) {
                startSecond = seconds;
            }

            vehicleIdMap.put(tripID,vehicleId);
            uuidMap.put(tripID,uuid);
            agentMap.put(tripID,agent);

            String latLon = String.valueOf(lat) + String.valueOf(lon);
            if(gpsMap.get(tripID) == null){
                Map<String, GPSData> latLongMap = new HashMap();
                latLongMap.put(latLon, new GPSData(seconds * 1000l, lat, lon, 1));
                gpsMap.put(tripID,latLongMap);
            }
            else {
                if (gpsMap.get(tripID).get(latLon) == null) {
                    gpsMap.get(tripID).put(latLon, new GPSData(seconds * 1000l, lat, lon, 1));
                }
                else{
                    gpsMap.get(tripID).get(latLon).increment();
                }
            }
        }

        // Debug.log("- map.size(): " + map.size());

        for (String id : gpsMap.keySet()) {
            Trip trip = tripCollection.get(id);

            if (trip == null) {
                System.err.println("* no trip found for id " + id + ", skipping");
                continue;
            }
            String name = trip.getHeadsign();

            // Use routeName if Headsign is null
            if (name == null || name == "") {
                String route_id = trip.getRouteID();
                Route route = routeCollection.get(route_id);
                name = route.getName();
                // Use tripID if routeName is null
                if (name == null || name == "") {
                    name = id;
                }
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
                TripReportData td = new TripReportData(id, name, start, duration, uuidMap.get(id), agentMap.get(id), vehicleIdMap.get(id));
                tdList.add(td);
                tdMap.put(id, td);
            }
        }

        Collections.sort(tdList);
    }

    public Map<String, Map<String, GPSData>> getMap() {
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
