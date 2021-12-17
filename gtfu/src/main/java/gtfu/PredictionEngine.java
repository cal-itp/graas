package gtfu;

import java.util.ConcurrentModificationException;
import java.util.Map;

public class PredictionEngine {
    static PredictionUpdate updateInstance;
    static Object lock = new Object();

    boolean runAsynchronously;

    public PredictionEngine(boolean runAsynchronously) {
        this.runAsynchronously = runAsynchronously;
    }

    public void update(Agency agency, TripCollection tripCollection, StopCollection stopCollection, Map<String, Vehicle> vehicles) {
        synchronized (lock) {
            if (updateInstance != null) {
                Debug.error("already running prediction update");
                return;
            }

            updateInstance = new PredictionUpdate(agency, tripCollection, stopCollection, vehicles, runAsynchronously);
        }
    }
}

class PredictionUpdate implements Runnable {
    private Agency agency;
    private TripCollection tripCollection;
    private StopCollection stopCollection;
    private Map<String, Vehicle> vehicles;
    private boolean running;

    public PredictionUpdate(
        Agency agency,
        TripCollection tripCollection,
        StopCollection stopCollection,
        Map<String, Vehicle> vehicles,
        boolean runAsynchronously)
    {
        this.agency = agency;
        this.tripCollection = tripCollection;
        this.stopCollection = stopCollection;
        this.vehicles = vehicles;

        running = true;

        if (runAsynchronously) {
            Thread t = new Thread(this);
            t.start();
        } else {
            run();
        }
    }

    private void setArrivalPredictions(Vehicle v) {
        //Debug.log("- v.id: " + v.id);

        Sequence<TripScheduleElement> sequence = new Sequence<TripScheduleElement>();
        v.trip.getStopsPastPoint(sequence, v.closest);
        //Debug.log("- sequence.start: " + sequence.start);

        for (int i=sequence.start; i<sequence.stop; i++) {
            TripScheduleElement e = sequence.list.get(i);
            //Debug.log("----------------------------");
            //Debug.log("-- e.stop.name: " + e.stop.name);
            //Debug.log("-- e.daySeconds: " + e.daySeconds);
            //Debug.log("-- v.scheduleOffset: " + v.scheduleOffset);

            int predictedArrivalSeconds = e.daySeconds + v.scheduleOffset;

            String scheduledArrival = Time.getHMSForMillis(e.daySeconds * 1000);
            //Debug.log("-- scheduledArrival: " + scheduledArrival);
            String predictedArrival = Time.getHMSForMillis((e.daySeconds + v.scheduleOffset) * 1000);
            //Debug.log("-- predictedArrival: " + predictedArrival);

            int dayOffsetSeconds = Time.getDayOffsetMillis(agency.getTimeZoneString()) / 1000;
            //Debug.log("-- dayOffsetSeconds: " + dayOffsetSeconds);
            String timeOfDay = Time.getHMSForMillis(dayOffsetSeconds * 1000);
            //Debug.log("-- timeOfDay: " + timeOfDay);
            int minutesToArrival = (predictedArrivalSeconds - dayOffsetSeconds) / 60;
            //Debug.log("-- minutesToArrival: " + minutesToArrival);

            if (minutesToArrival >= 0) {
                ArrivalPrediction ap = new ArrivalPrediction(v, e.stop, minutesToArrival);
                e.stop.add(ap);
            }
        }
    }

    private void setScheduleOffset(Vehicle v) {
        Trip trip = tripCollection.get(v.getTripID());
        //Debug.log("- trip: " + trip);
        //Debug.log("- trip.getShape(): " + trip.getShape());
        // ### TODO: getClosestNeighbor() is expensive. Better approach is
        // to cache the shape point returned in 'v' and then pass as an
        // arg to getCloseNeighbor(p, maxDistance)
        ShapePoint p1 = trip.getShape().getClosestNeighbor(v.lat, v.lon);
        //Debug.log("- p1: " + p1);
        v.closest = new ShapePoint(p1);
        int offset = Time.getDayOffsetMillis(agency.getTimeZoneString());
        //Debug.log("+ offset: " + Time.getHMSForMillis(offset));
        int tripStart = trip.getStartTime() * 1000;
        //Debug.log("+ tripStart: " + Time.getHMSForMillis(tripStart));
        ShapePoint p2 = trip.getLocation(offset - tripStart);

        if (p2 == null) {
            v.scheduleOffset = Integer.MIN_VALUE;
            return;
        }

        v.projected = new ShapePoint(p2);
        //Debug.log("- p2: " + p2);
        int t1 = trip.getTimeForLocation(p1);
        //Debug.log("- t1: " + t1 + ", " + Time.getHMSForMillis(t1));
        int t2 = trip.getTimeForLocation(p2);
        //Debug.log("- t2: " + t2 + ", " + Time.getHMSForMillis(t2));
        v.scheduleOffset = t2 - t1;

        setArrivalPredictions(v);
    }

    public void terminate() {
        running = false;

        synchronized (PredictionEngine.lock) {
            PredictionEngine.updateInstance = null;
        }
    }

    public void run() {
        Debug.log("PredictionUpdate.run()");
        try {
            long then = Util.now();

            stopCollection.clearArrivalPredictions();

            for (Vehicle v : vehicles.values()) {
                //Debug.log("-- v: " + v);
                if (!running) break;
                setScheduleOffset(v);
            }

            stopCollection.sortArrivalPredictions();

            synchronized (PredictionEngine.lock) {
                PredictionEngine.updateInstance = null;
            }

            int millis = (int)(Util.now() - then);
            Debug.log("- prediction millis: " + millis);
        } catch (ConcurrentModificationException e) {
            Debug.log("+ concurrent modification, aborting prediction update");
        }
    }
}