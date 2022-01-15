package gtfu;

import java.io.Serializable;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.HashMap;
import java.util.Map;

public class TripScheduleCollection implements Serializable {
    private Map<String, TripSchedule> map;

    public TripScheduleCollection(String path, TripCollection tripCollection, StopCollection stopCollection, ProgressObserver observer) {
        this(path, tripCollection, stopCollection, observer, false);
    }

    public TripScheduleCollection(String path, TripCollection tripCollection, StopCollection stopCollection, ProgressObserver observer, boolean skipErrors) {
        this();

        TextFile stf = new TextFile(path + "/stop_times.txt");
        CSVHeader header = new CSVHeader(stf.getNextLine());
        String lastTripID = null;
        String lastArrivalTime = null;
        Stop lastStop = null;
        Stop stop = null;
        TripSchedule schedule = null;
        Trip trip = null;
        int count = 1;

        for (;;) {
            String line = stf.getNextLine();
            if (line == null) break;

            if (observer != null) observer.tick();
            CSVRecord r = new CSVRecord(header, line);
            String tripID = r.get("trip_id");
            String stopID = r.get("stop_id");
            String arrivalTime = r.get("arrival_time");


            if (Util.isEmpty(arrivalTime)) continue;
            if (trip == null) {
                // This error message is so common noisy that we'll need better formatting in order launch it
                // TODO: launch stop_time failure messages
                // Util.fail(
                //     String.format(
                //         "fatal error, stop_times.txt references trip ID '%s', which is either absent from trips.txt or omitted due to upstream issue",
                //         tripID
                //     ),
                //     !skipErrors
                // );
                continue;
            }
            int daySeconds = Time.getMillisForTime(arrivalTime) / 1000;

            stop = stopCollection.get(stopID);

            if (tripID.equals(lastTripID)) {
                float distance  = getDistance(lastStop, stop, trip);
                int time = getElapsedTime(lastArrivalTime, arrivalTime);
                //Debug.log("- time: " + time);

                schedule.add(stop, distance, time, daySeconds);
            } else {
                if (schedule != null) {
                    String hash = schedule.getHash();
                    //Debug.log("- hash: " + hash);
                    TripSchedule s = map.get(hash);

                    if (s == null) {
                        map.put(hash, schedule);
                        //Debug.log("+ new hash " + hash);
                        //Debug.log(schedule);
                    } else {
                        //Debug.log("+ cached hash " + hash);
                        schedule = s;
                    }
                }

                trip = tripCollection.get(tripID);
                schedule = new TripSchedule(trip);
                schedule.add(stop, 0, 0, daySeconds);
                trip.setSchedule(schedule);

            }

            lastTripID = tripID;
            lastArrivalTime = arrivalTime;
            lastStop = stop;
        }

        //Debug.log("- map.size(): " + map.size());
        //Debug.log("- tripCollection.getSize(): " + tripCollection.getSize());
        //Debug.log("+ trip schedule share ratio: " + (tripCollection.getSize() / (float)map.size()));
    }

    private TripScheduleCollection() {
        map = new HashMap<String, TripSchedule>();
    }

    public void write(DataOutputStream out) {
        try {
            out.writeInt(map.size());

            for (String s : map.keySet()) {
                out.writeUTF(s);
                map.get(s).write(out);
            }
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public static TripScheduleCollection fromStream(DataInputStream in) {
        TripScheduleCollection c = new TripScheduleCollection();

        try {
            int count = in.readInt();

            for (int i=0; i<count; i++) {
                String hash = in.readUTF();
                c.map.put(hash, TripSchedule.fromStream(in));
            }

            return c;
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    // return difference between two HMS time strings in seconds
    private int getElapsedTime(String from, String to) {
        //Debug.log("TripScheduleCollection.getElapsedTime()");
        //Debug.log("- from: " + from);
        //Debug.log("- to:   " + to);
        int m1 = Time.getMillisForTime(from);
        //Debug.log("- m1: " + m1);
        int m2 = Time.getMillisForTime(to);
        //Debug.log("- m2: " + m2);
        return (m2 - m1) / 1000;
    }

    // return distance in feet between two stops
    private int getDistance(Stop from, Stop to, Trip trip) {
        //Debug.log("TripScheduleCollection.getDistance()");
        ShapePoint p1 = from.getClosestShapePoint(trip);
        //Debug.log("- p1.getIndex(): " + p1.getIndex());
        ShapePoint p2 = to.getClosestShapePoint(trip);
        //Debug.log("- p2.getIndex(): " + p2.getIndex());
        Shape shape = trip.getShape();
        float distance = 0;

        for (int i=p1.getIndex(); i<=p2.getIndex(); i++) {
            distance += shape.getPoint(i).getDistance();
        }

        return (int)Math.round(distance);
    }

    public int getSize() {
        return map.size();
    }
}