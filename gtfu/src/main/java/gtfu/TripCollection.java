package gtfu;

import java.io.Serializable;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;

public class TripCollection implements Iterable<Trip>, Serializable {
    private Map<String, Trip> map;
    private List<Trip> list;

    // trip_id,route_id,service_id,trip_headsign,trip_short_name,direction_id,shape_id,wheelchair_accessible,bikes_allowed,block_id,block_name
    public TripCollection(String path, StopCollection stopCollection, ShapeCollection shapeCollection, ProgressObserver observer) {
        this(path, stopCollection, shapeCollection, observer, false);
    }

    // trip_id,route_id,service_id,trip_headsign,trip_short_name,direction_id,shape_id,wheelchair_accessible,bikes_allowed,block_id,block_name
    public TripCollection(String path, StopCollection stopCollection, ShapeCollection shapeCollection, ProgressObserver observer, boolean skipErrors) {
        this();

        TextFile tf = new TextFile(path + "/trips.txt");
        CSVHeader header = new CSVHeader(tf.getNextLine());
        int count = 1;

        for (;;) {
            String line = tf.getNextLine();
            if (line == null) break;

            if (observer != null) observer.tick();
            CSVRecord r = new CSVRecord(header, line);
            String id = r.get("trip_id");
            String routeID = r.get("route_id");
            String serviceID = r.get("service_id");
            String headSign = r.get("trip_headsign");
            String shapeID = r.get("shape_id");
            String blockID = r.get("block_id");
            Shape shape = shapeCollection.get(shapeID);
            if (shape == null) {
                Util.fail(
                    String.format(
                        "fatal error, trip '%s' references non-existing shape ID '%s'",
                        id,
                        shapeID
                    ),
                    !skipErrors
                );
            } else {
                Trip trip = new Trip(id, routeID, serviceID, headSign, shape);
                trip.setBlockID(blockID);

                map.put(id, trip);
                list.add(trip);
            }
        }

        tf.dispose();

        // trip_id,arrival_time,departure_time,stop_id,stop_sequence,timepoint
        TextFile stf = new TextFile(path + "/stop_times.txt");
        String lastTripID = null;
        header = new CSVHeader(stf.getNextLine());

        for (;;) {
            Trip trip = null;
            String tripID = null;
            String line = stf.getNextLine();
            if (line == null) break;

            if (observer != null) observer.tick();
            try {
                CSVRecord r = new CSVRecord(header, line);
                tripID = r.get("trip_id");
                String stopID = r.get("stop_id");
                String time = r.get("arrival_time");

                if (Util.isEmpty(time)) continue;

                trip = map.get(tripID);

                if (trip == null) continue;

                Stop stop = stopCollection.get(stopID);

                if (!tripID.equals(lastTripID)) {
                    trip.getShape().resetCloseIndex();
                    trip.setStartTime(Time.getMillisForTime(time) / 1000);
                }

                stop.getClosestShapePoint(trip);
                trip.addStop(stop, Time.getMillisForTime(time));

                lastTripID = tripID;
            } catch (Exception e) {
                Debug.error("* can't parse line: " + line);
                Debug.error("- tripID: " + tripID);
                Debug.error("- trip: " + trip);
                Debug.error("- trip.getShape(): " + trip.getShape());
                throw new Fail(e);
            }
        }

        for (String id : map.keySet()) {
            Trip trip = map.get(id);
            trip.setAccelerator(50);
        }
    }

    private TripCollection() {
        map = new HashMap<String, Trip>();
        list = new ArrayList<Trip>();
    }

    public void write(DataOutputStream out) {
        //Debug.log("ShapeCollection.write()");
        //Debug.log("- list.size(): " + list.size());

        try {
            out.writeInt(list.size());
            int count = 0;

            for (Trip t : list) {
                t.write(out);
                //Debug.log(count++);
            }
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public static TripCollection fromStream(DataInputStream in) {
        try {
            TripCollection c = new TripCollection();

            int count = in.readInt();

            for (int i=0; i<count; i++) {
                Trip t = Trip.fromStream(in);

                c.list.add(t);
                c.map.put(t.getID(), t);
            }

            return c;
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public List<String> getTripIDsForRoute(String routeID) {
        List<String> l = new ArrayList<String>();

        for (Trip t : list) {
            if (routeID == null || t.getRouteID().equals(routeID)) {
                l.add(t.getID());
            }
        }

        return l;
    }

    public Trip get(int index) {
        return list.get(index);
    }

    public Trip get(String id) {
        return map.get(id);
    }

    public int getSize() {
        return list.size();
    }

    public void forEach(Consumer<? super Trip> action) {
        list.forEach(action);
    }

    public Iterator<Trip> iterator() {
        return list.iterator();
    }

    public Spliterator<Trip> spliterator() {
        return list.spliterator();
    }
}