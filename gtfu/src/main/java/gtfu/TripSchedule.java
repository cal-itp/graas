package gtfu;

import java.io.Serializable;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;

public class TripSchedule implements Iterable<TripScheduleElement>, Serializable {
    private Digest digest;
    private Trip trip;
    private List<TripScheduleElement> list;

    public TripSchedule(Trip trip) {
        this();
        this.trip = trip;

        try {
            digest = new Digest();
        } catch (Exception e) {
            throw new Fail(e);
        }
    }

    private TripSchedule() {
        list = new ArrayList<TripScheduleElement>();
    }

    public void write(DataOutputStream out) {
        //Debug.log("Trip.write()");

        try {
            digest.write(out);
            trip.write(out);

            out.writeInt(list.size());

            for (TripScheduleElement e : list) {
                e.write(out);
            }
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public static TripSchedule fromStream(DataInputStream in) {
        try {
            TripSchedule t = new TripSchedule();

            t.digest = Digest.fromStream(in);
            t.trip = Trip.fromStream(in);

            int count = in.readInt();

            for (int i=0; i<count; i++) {
                TripScheduleElement e = TripScheduleElement.fromStream(in);

                t.list.add(e);
            }

            return t;
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    // distance: distance to previous stop in feet
    // time: time from previous stop in seconds
    public void add(Stop stop, float distance, int time, int daySeconds) {
        Shape shape = trip.getShape();
        ShapePoint closest = stop.getClosestShapePoint(trip);

        list.add(new TripScheduleElement(
            closest,
            stop,
            distance,
            time,
            daySeconds
        ));

        digest.update(stop.getID());
        digest.update(time);
    }

    public String getHash() {
        return digest.getHash();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(getHash());
        sb.append('\n');

        for (TripScheduleElement e : list) {
            sb.append("  ");
            sb.append(e);
            sb.append('\n');
        }

        return sb.toString();
    }

    public TripScheduleElement get(int index) {
        return list.get(index);
    }

    public int getSize() {
        return list.size();
    }

    public int getDuration() {
        if (getSize() == 0) return -1;

        int from = list.get(0).daySeconds;
        int to = list.get(list.size() - 1).daySeconds;

        return to - from;
    }

    public void forEach(Consumer<? super TripScheduleElement> action) {
        list.forEach(action);
    }

    public List<TripScheduleElement> getList() {
        return list;
    }

    public Iterator<TripScheduleElement> iterator() {
        return list.iterator();
    }

    public Spliterator<TripScheduleElement> spliterator() {
        return list.spliterator();
    }
}
