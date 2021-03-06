package gtfu.tools;

import java.util.Collections;
import java.util.stream.Collectors;
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
import gtfu.Util;
import gtfu.Stats;

/**
* Processes GPS updates from one agency by comparing with their trips and routes, to create several assets valuable to reporting.
*/
public class DayLogSlicer {
    public static final int MIN_REPORT_DURATION_MINS = 1;
    private Map<String, Map<String, GPSData>> gpsMap;
    private Map<String, GPSData> latLonMap;
    private Map<String, Integer> previousUpdateMap;
    private Map<String, String> uuidMap;
    private Map<String, String> agentMap;
    private Map<String, String> vehicleIdMap;
    private List<TripReportData> tdList;
    private Map<String, TripReportData> tdMap;

    /**
    * Uses GPS updates, along with trip and route info, to create the following assets:
    *  - a TripReportData instance for each trip
    *  - gpsMap, a map containing GPS updates for each trip
    * @param tripCollection     A collection of this agency's trips
    * @param routeCollection    A collection of this agency's routes
    * @param lines              One line per GPS update database entries for this agency
    */
    public DayLogSlicer(TripCollection tripCollection, RouteCollection routeCollection, List<String> lines) {
        gpsMap = new HashMap();
        uuidMap = new HashMap();
        agentMap = new HashMap();
        vehicleIdMap = new HashMap();
        tdList = new ArrayList();
        tdMap = new HashMap();
        previousUpdateMap = new HashMap();

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

            vehicleIdMap.put(tripID,vehicleId);
            uuidMap.put(tripID,uuid);
            agentMap.put(tripID,agent);

            String latLon = String.valueOf(lat) + String.valueOf(lon);
            latLonMap = gpsMap.get(tripID);
            // If there is no LatLonMap for this trip, add it.
            if(latLonMap == null){
                latLonMap = new HashMap();
                gpsMap.put(tripID, latLonMap);
            }

            // If there is no existing GPSData for this latLon value, add it and update previousUpdate for that trip
            if (latLonMap.get(latLon) == null) {
                // previousUpdateMap stores the most recent GPS update timestamp (in seconds) for each trip_id.
                // it relies on the list being sorted by timestmap, which it is.
                int secsSinceLastUpdate = -1;
                if (previousUpdateMap.get(tripID) != null) {
                    secsSinceLastUpdate = seconds - previousUpdateMap.get(tripID);
                }
                latLonMap.put(latLon, new GPSData(seconds * 1000l, secsSinceLastUpdate, lat, lon));
                previousUpdateMap.put(tripID, seconds);
            }

            // If there is already a GPSData for this latLon value, increment the count
            else{
                latLonMap.get(latLon).increment();
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
            if (Util.isEmpty(name)) {
                String route_id = trip.getRouteID();
                Route route = routeCollection.get(route_id);
                name = route.getName();
                // Use tripID if routeName is null
                if (Util.isEmpty(name)) {
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

            // Create lists as input to Stats
            List<Double> updateFreqList = new ArrayList<>();
            List<Double> updateMillisList = new ArrayList<>();

            for (GPSData gpsData : gpsMap.get(id).values()) {
                Double secsSinceLastUpdateDouble = gpsData.getSecsSinceLastUpdateAsDouble();

                if(secsSinceLastUpdateDouble > 0) {
                    updateFreqList.add(secsSinceLastUpdateDouble);
                }
                updateMillisList.add(gpsData.getMillisAsDouble());
            }

            TripReportData td = new TripReportData(id, name, start, duration, uuidMap.get(id), agentMap.get(id), vehicleIdMap.get(id), new Stats(updateFreqList), new Stats(updateMillisList));
            int tripDuration = td.getDurationMins();

            // Filter out trips shorter than minimum duration
            if (tripDuration >= MIN_REPORT_DURATION_MINS) {
                tdList.add(td);
                tdMap.put(id, td);
            } else Debug.log(" - skipping trip " + id + " because duration was less than " + MIN_REPORT_DURATION_MINS + " mins");
        }

        Collections.sort(tdList);
    }

    /**
    * Return the map of GPS updates
    * @return gpsMap     A map containing GPS updates for each trip.
    */
    public Map<String, Map<String, GPSData>> getMap() {
        return gpsMap;
    }

    /**
    * Return a list of trip report info
    * @return tdList     A list containing one TripReportData instance per trip representated in the GPS updates
    */
    public List<TripReportData> getTripReportDataList() {
        return tdList;
    }

    /**
    * Return a map of trip report info
    * @return tdMap     A map containing one TripReportData instance per trip representated in the GPS updates
    */
    public Map<String, TripReportData> getTripReportDataMap() {
        return tdMap;
    }
}
