package gtfu;

import java.io.Serializable;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Trip implements Serializable {
    String id;
    String routeID;
    String serviceID;
    String headsign;
    List<Stop> stopList;
    List<Integer> milliList;
    Map<String, Integer> stopMap;
    TripSchedule schedule;
    transient Shape shape;
    String shapeID;
    int accelerator;
    int startTime;

    public Trip(String id, String routeID, String serviceID, String headsign, Shape shape) {
        this();

        this.id = id;
        this.routeID = routeID;
        this.serviceID = serviceID;
        this.headsign = headsign;
        this.shape = shape;
        shapeID = shape.getID();
        //Debug.log("- shapeID: " + shapeID);

        accelerator = 1;
    }

    private Trip() {
        stopList = new ArrayList<Stop>();
        milliList = new ArrayList<Integer>();
        stopMap = new HashMap<String, Integer>();
    }

    public void setShapeFromID(ShapeCollection shapeCollection) {
        //Debug.log("Trip.setShapeFromID()");
        //Debug.log("- shapeID: " + shapeID);
        shape = shapeCollection.get(shapeID);
        //Debug.log("- shape: " + shape);
    }

    public void write(DataOutputStream out) {
        //Debug.log("Trip.write()");

        try {
            out.writeUTF(id);
            out.writeUTF(routeID);
            out.writeUTF(headsign);

            out.writeInt(stopList.size());

            for (Stop s : stopList) {
                s.write(out);
            }

            out.writeInt(milliList.size());

            for (Integer i : milliList) {
                out.writeInt(i);
            }

            schedule.write(out);
            shape.write(out);
            out.writeInt(accelerator);
            out.writeInt(startTime);
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public static Trip fromStream(DataInputStream in) {
        try {
            Trip t = new Trip();

            t.id = in.readUTF();
            t.routeID = in.readUTF();
            t.headsign = in.readUTF();

            int count = in.readInt();

            for (int i=0; i<count; i++) {
                Stop s = Stop.fromStream(in);

                t.stopList.add(s);
            }

            count = in.readInt();

            for (int i=0; i<count; i++) {
                t.milliList.add(in.readInt());
            }

            for (int i=0; i<t.milliList.size(); i++) {
                t.stopMap.put(t.stopList.get(i).getID(), t.milliList.get(i));
            }

            t.schedule = TripSchedule.fromStream(in);
            t.shape = Shape.fromStream(in);
            t.accelerator = in.readInt();
            t.startTime = in.readInt();

            return t;
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public void setStartTime(int startTime) {
        this.startTime = startTime;
    }

    public int getStartTime() {
        return startTime;
    }

    public void setSchedule(TripSchedule schedule) {
        this.schedule = schedule;
    }

    public String getDepartureTime() {
        if (schedule == null) return "???";
        TripScheduleElement e = schedule.get(0);
        return Time.getHMForMillis(e.daySeconds * 1000);
    }

    public String getHeadsign() {
        return headsign;
    }

    public TripSchedule getSchedule() {
        return schedule;
    }

    public void setAccelerator(int accelerator) {
        this.accelerator = accelerator;
    }

    public int getAccelerator() {
        return accelerator;
    }

    public String getID() {
        return id;
    }

    public String getServiceID() {
        return serviceID;
    }

    public String getRouteID() {
        return routeID;
    }

    public Shape getShape() {
        return shape;
    }

    public String getShapeID() {
        return shape.getID();
    }

    public void addStop(Stop stop, int millis) {
        stopList.add(stop);
        milliList.add(millis);

        stopMap.put(stop.id, millis);
        stop.addRouteID(routeID);
    }

    public ShapePoint getFirstPoint() {
        return shape.get(0);
    }

    public Stop getStop(int index) {
        return stopList.get(index);
    }

    public int getStopSize() {
        return stopList.size();
    }

    public int getTimeForStop(String stopID) {
        Integer v = stopMap.get(stopID);
        if (v == null) return -1;
        else return v.intValue();
    }

    public int getTimeAt(int index) {
        return milliList.get(index);
    }

    // return duration in seconds
    public int getDuration() {
        return (milliList.get(milliList.size() - 1).intValue() - milliList.get(0).intValue()) / 1000 / accelerator;
    }

    public int getAverageSpeed() {
        return (int)Math.round(Conversions.feetToMiles((int)shape.getDistance()) / Conversions.secondsToHours(getDuration()));
    }

    public void computeTimings() {
        shape.setTimesForPoints(getDuration() * 1000);
    }

    public void getStopsPastPoint(Sequence<TripScheduleElement> seq, ShapePoint p) {
        if (p.index < 0) {
            throw new Fail("p has no index");
        }

        seq.list = schedule.getList();

        for (int i=0; i<schedule.getSize(); i++) {
            TripScheduleElement e = schedule.get(i);
            if (e.closest.index >= p.index) {
                seq.start = i;
                seq.stop = schedule.getSize();
                return;
            }
        }

        seq.start = schedule.getSize();
        seq.stop = schedule.getSize();
    }

    // input: offset time since trip start in milliseconds
    // getLocation(0) sometimes returns null in GraphicReport (addRandomTestData).
    // TODO: Look into underlying issue, possibly related to agency static GTFS gaps
    public ShapePoint getLocation(int millis) {
        if (millis < 0) millis = 0;

        //Debug.log("Trip.getLocation()");
        //Debug.log("+ millis: " + Time.getHMSForMillis(millis));

        int time = millis / 1000;
        int totalTime = 0;
        TripScheduleElement e = null;
        TripScheduleElement pe = null;

        for (int i=0; i<schedule.getSize(); i++) {
            e = schedule.get(i);
            totalTime += e.time;

            if (totalTime >= time) {
                break;
            }

            pe = e;
        }

        if (e == null) return null;
        if (totalTime == time || pe == null) return e.closest;

        int timeSinceStop = time - (totalTime - e.time);
        float fraction = timeSinceStop / (float)e.time;

        int distanceBetweenStops = (int)shape.getDistance(pe.closest, e.closest);
        int coveredDistance = (int)(distanceBetweenStops * fraction);

        float d = 0;
        ShapePoint p = null;

        for (int i = pe.closest.index + 1; i<shape.getSize(); i++) {
            ShapePoint q = shape.getPoint(i);
            d += q.distance;

            if (d >= coveredDistance) {
                p = q;
                break;
            }
        }

        return p;
    }

    public boolean isLocationBeforeStop(ShapePoint p, Stop stop) {
        ShapePoint sp = stop.getClosestShapePoint(this);

        // ### assumption: p.index is index into trip shape list
        return p.index <= sp.index;
    }

    public int getTimeForLocation(ShapePoint p) {
        if (p == null) throw new Fail("'p' not in shape");
        if (p.index < 0) throw new Fail("'p' does not have valid index");

        TripScheduleElement e = null;
        TripScheduleElement pe = null;
        int totalTime = 0;

        for (int i=0; i<schedule.getSize(); i++) {
            e = schedule.get(i);
            totalTime += e.time;
            //Debug.log("-- e.time: " + e.time);

            if (e.closest.index >= p.index) {
                break;
            }

            pe = e;
        }

        if (e.closest.index == p.index || pe == null) return totalTime;

        totalTime -= e.time;
        //Debug.log("- totalTime: " + totalTime);

        ShapePoint p1 = null;
        float coveredDistance = 0;

        for (int i = pe.closest.index + 1; i<shape.getSize(); i++) {
            ShapePoint q = shape.getPoint(i);
            coveredDistance += q.distance;

            if (q == p) {
                p1 = q;
                break;
            }
        }

        if (p1 == null) throw new Fail("'p' not in shape");

        //Debug.log("- coveredDistance: " + coveredDistance);
        int distanceBetweenStops = (int)shape.getDistance(pe.closest, e.closest);
        //Debug.log("- distanceBetweenStops: " + distanceBetweenStops);
        float fraction = coveredDistance / distanceBetweenStops;
        //Debug.log("- fraction: " + fraction);

        return totalTime + (int)(fraction * e.time);
    }
}