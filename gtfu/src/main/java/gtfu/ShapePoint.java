package gtfu;

import java.io.Serializable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

// ### lat/long calculations assume North American location
// western-most point: -124.78431510761095
// eastern-most point: -67.04017764518407  (long increases from west to east
// northern-most point: 49.09625260808487
// southern-most point: 24.553231902487354 (lat increases from south to north)
public class ShapePoint implements Serializable {
    private final static float UNSET = Float.MIN_VALUE;

    public float lat;
    public float lon;
    public float distance;
    public int screenX;
    public int screenY;
    public int index;

    public ShapePoint() {
        this(UNSET, UNSET);
    }

    public ShapePoint(ShapePoint p) {
        this(p.lat, p.lon);
        index = p.index;
        distance = p.distance;
    }

    public ShapePoint(float lat, float lon) {
        this.lat = lat;
        this.lon = lon;

        distance = -1;
        index = -1;
    }

    public void write(DataOutputStream out) {
        try {
            out.writeFloat(lat);
            out.writeFloat(lon);
            out.writeFloat(distance);
            out.writeInt(index);
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public static ShapePoint fromStream(DataInputStream in) {
        try {
            ShapePoint p = new ShapePoint();

            p.lat = in.readFloat();
            p.lon = in.readFloat();
            p.distance = in.readFloat();
            p.index = in.readInt();

            return p;
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public float getDistance() {
        return distance;
    }

    public void setDistance(float distance) {
        this.distance = distance;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public void setLatIfLess(float lat) {
        if (this.lat == UNSET || lat < this.lat) {
            this.lat = lat;
        }
    }

    public void setLatIfGreater(float lat) {
        if (this.lat == UNSET || lat > this.lat) {
            this.lat = lat;
        }
    }

    public void setLongIfLess(float lon) {
        if (this.lon == UNSET || lon < this.lon) {
            this.lon = lon;
        }
    }

    public void setLongIfGreater(float lon) {
        if (this.lon == UNSET || lon > this.lon) {
            this.lon = lon;
        }
    }

    public float getDistance(ShapePoint p) {
        return (float)Util.getHaversineDistance(lat, lon, p.lat, p.lon);
    }

    public float getDistance(float plat, float plon) {
        return (float)Util.getHaversineDistance(lat, lon, plat, plon);
    }

    public String toString() {
        return String.format("(%f, %f), index = %d, distance = %f", lat, lon, index, distance);
    }

    public String toCSVLine() {
        return String.format("%f,%f", lat, lon);
    }

    public boolean equals(Object o) {
        if (o instanceof ShapePoint) {
            ShapePoint p = (ShapePoint)o;

            return lat == p.lat
                && lon == p.lon
                && index == p.index
                && distance == p.distance;
        }

        return false;
    }

    public int hashCode() {
        return (int)(lat + lon + index + distance) * 1000;
    }
}