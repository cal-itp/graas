package gtfu;

import java.io.Serializable;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;

public class Shape implements Serializable {
    String id;
    List<ShapePoint> list;
    List<Integer> milliList;
    Area area;
    float distance;
    int closeIndex;

    public Shape(String id) {
        this.id = id;
        list = new ArrayList<ShapePoint>();
        area = new Area();
    }

    public Shape(Shape s) {
        this(s.id);

        for (ShapePoint p : s.list) {
            list.add(new ShapePoint(p));
        }

        if (s.milliList != null) {
            milliList = new ArrayList<Integer>();

            for (Integer i : s.milliList) {
                milliList.add(Integer.valueOf(i));
            }
        }

        distance = s.distance;
        area = new Area(s.area);
        closeIndex = s.closeIndex;
    }

    public void write(DataOutputStream out) {
        //Debug.log("Shape.write()");
        //Debug.log("- list.size(): " + list.size());

        try {
            out.writeUTF(id);

            out.writeInt(list.size());
            int count = 0;

            for (ShapePoint p : list) {
                p.write(out);
                //Debug.log(count++);
            }

            if (milliList == null) {
                out.writeInt(0);
            } else {
                out.writeInt(milliList.size());
                count = 0;

                for (Integer i : milliList) {
                    out.writeInt(i);
                    //Debug.log(count++);
                }
            }

            area.write(out);

            out.writeFloat(distance);
            out.writeInt(closeIndex);
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public static Shape fromStream(DataInputStream in) {
        try {
            Shape s = new Shape(in.readUTF());

            int count = in.readInt();

            for (int i=0; i<count; i++) {
                ShapePoint p = ShapePoint.fromStream(in);
                s.list.add(p);
            }

            count = in.readInt();

            for (int i=0; i<count; i++) {
                int v = in.readInt();
                s.milliList.add(v);
            }

            s.area = Area.fromStream(in);
            s.distance = in.readFloat();
            s.closeIndex = in.readInt();

            return s;
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public String getID() {
        return id;
    }

    public ShapePoint get(int index) {
        return list.get(index);
    }

    public Area getArea() {
        return area;
    }

    public void resetCloseIndex() {
        closeIndex = 0;
    }

    public ShapePoint getCloseNeighbor(ShapePoint p) {
        return getCloseNeighbor(p.lat, p.lon);
    }

    public ShapePoint getClosestNeighbor(ShapePoint p) {
        return getClosestNeighbor(p.lat, p.lon);
    }

    public ShapePoint getCloseNeighbor(float lat, float lon) {
        for (int i=closeIndex; i<list.size(); i++) {
            ShapePoint p = list.get(i);
            float d = p.getDistance(lat, lon);

            if (d < 300) {
                closeIndex = i;
                //Debug.log("- closeIndex: " + closeIndex);
                return p;
            }
        }

        return getClosestNeighbor(lat, lon);
    }

    public ShapePoint getClosestNeighbor(float lat, float lon) {
        float minDistance = Float.MAX_VALUE;
        ShapePoint minPoint = null;

        for (ShapePoint neighbor : list) {
            float d = neighbor.getDistance(lat, lon);

            if (d < minDistance) {
                minDistance = d;
                minPoint = neighbor;
            }
        }

        //Debug.log("- minDistance: " + minDistance);

        return minPoint;
    }

    // 'p' is assumed to be a shape point in this shape, tracking
    // a vehicle's progress on a trip so far. 'lat' and 'lon'
    // are the vehicle's current coordinates.
    // There are three possible outcomes to calling this method:
    // - 'p' remains the closest point to vehicle's actual position
    // - list[p.index + 1] is returned as the new closest shape point
    // - the vehicle is off course, and a different shape point is
    //   returned
    public ShapePoint crawl(ShapePoint p, float lat, float lon) {
        /*
        - if p is last shape point and lat/lon are within max allowed distance, return p
        - determine distance of lat/lon to p and to p'. If one at least one of them is within max allowed distance, return closer one
        - return getClosestNeighbor()
        */
        Util.implementMe();
        return null;
    }

    public void add(ShapePoint p) {
        area.update(p);
        list.add(p);

        p.setIndex(list.size() - 1);

        if (list.size() > 1) {
            ShapePoint r = list.get(list.size() - 2);
            double hd = Util.getHaversineDistance(p.lat, p.lon, r.lat, r.lon);
            p.setDistance((float)hd);
            distance += hd;
        }
    }

    public List<ShapePoint> getList() {
        return list;
    }

    public void check() {
        for (ShapePoint p : list) {
            if (!area.contains(p)) throw new Fail("foo");
        }
    }

    public int getSize() {
        return list.size();
    }

    public int getMillis(int i) {
        return milliList.get(i);
    }

    public ShapePoint getPoint(int i) {
        //Debug.log("getPoint()");
        //Debug.log("- id: " + id);
        return list.get(i);
    }

    // return distance in feet
    public float getDistance() {
        return distance;
    }

    public float getDistance(ShapePoint from, ShapePoint to) {
        //Debug.log("Shape.getDistance()");
        //Debug.log("- from: " + from);
        //Debug.log("- to: " + to);

        if (from.index < 0 || from.index >= list.size() || to.index < 0 || to.index >= list.size()) {
            throw new Fail("invalid indices");
        }

        if (from == null && to != null) return to.distance;

        ShapePoint p1 = list.get(from.index);
        //Debug.log("- p1: " + p1);
        if (!p1.equals(from)) throw new Fail("'from' not in shape");

        ShapePoint p2 = list.get(to.index);
        if (!p2.equals(to)) throw new Fail("'to' not in shape");

        float d = 0;

        for (int i=p1.index+1; i<=p2.index; i++) {
            d += list.get(i).distance;
        }

        return d;
    }

    public void setTimesForPoints(int totalMillis) {
        //Debug.log("Shape.setTimesForPoints()");
        //Debug.log("- totalMillis: " + totalMillis);

        if (milliList != null) return;

        milliList = new ArrayList<Integer>();
        float distanceTravelled = 0;

        milliList.add(0);

        for (int i=1; i<list.size(); i++) {
            ShapePoint a = list.get(i - 1);
            ShapePoint b = list.get(i);

            double d = Util.getHaversineDistance(a.lat, a.lon, b.lat, b.lon);
            distanceTravelled += d;
            double fraction = distanceTravelled / distance;
            int millis = (int)Math.round(totalMillis * fraction);
            //Debug.log("-- millis: " + millis);

            milliList.add(millis);
        }
    }
}
