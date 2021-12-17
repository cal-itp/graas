package gtfu;

import java.io.Serializable;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;

public class TripScheduleElement implements Serializable {
    ShapePoint closest;
    Stop stop;
    float distance; // distance in feet from last stop
    int time; // time in seconds from last stop
    int daySeconds; // arrival time as millisecond offset into day

    public TripScheduleElement(ShapePoint closest, Stop stop, float distance, int time, int daySeconds) {
        this.closest = closest;
        this.stop = stop;
        this.distance = distance;
        this.time = time;
        this.daySeconds = daySeconds;
    }

    private TripScheduleElement() {
    }

    public void write(DataOutputStream out) {
        //Debug.log("ShapeCollection.write()");
        //Debug.log("- list.size(): " + list.size());

        try {
            closest.write(out);
            stop.write(out);
            out.writeFloat(distance);
            out.writeInt(time);
            out.writeInt(daySeconds);
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public static TripScheduleElement fromStream(DataInputStream in) {
        try {
            TripScheduleElement e = new TripScheduleElement();

            e.closest = ShapePoint.fromStream(in);
            e.stop = Stop.fromStream(in);
            e.distance = in.readFloat();
            e.time = in.readInt();
            e.daySeconds = in.readInt();

            return e;
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public String toString() {
        return String.format("closest: (%f, %f), stop: %s, distance: %d, time: %d", closest.lat, closest.lon, stop.id, (int)distance, time);
    }
}