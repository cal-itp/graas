package gtfu;

import java.io.Serializable;

public class Vehicle implements Filterable, GeoObject, Serializable {
    Trip trip;
    ShapePoint point;
    ShapePoint closest;
    ShapePoint projected;
    String id;
    float lat;
    float lon;
    float heading;
    float speed;
    boolean dirty;
    int scheduleOffset = Integer.MIN_VALUE;

    public Vehicle() {
    }

    public Vehicle(Trip trip, String id, float lat, float lon, float heading, float speed, ShapePoint point) {
        this.trip = trip;
        this.id = id;
        this.heading = heading;
        this.speed = speed;
        this.point = point;

        setPos(lat, lon);
    }

    public String getTripID() {
        return trip.getID();
    }

    public boolean matches(String key, String value) {
        if (key.equals(Filterable.ROUTE)) {
            return value == null || trip.routeID.equals(value);
        }

        return false;
    }

    public String[] getLabelText() {
        String label = trip.getFriendlyID() + ": ";

        if (Math.abs(scheduleOffset) < Time.SECONDS_PER_DAY) {
            label += Time.getTimeDeltaString(scheduleOffset, true);
        } else {
            label += "no data";
        }

        return new String[] {label};
    }

    public void setClosestShapePoint(ShapePoint point) {
        this.point = point;
    }

    public ShapePoint getClosestShapePoint() {
        return point;
    }

    public void clearDirty() {
        dirty = false;
    }

    public boolean isDirty() {
        return dirty;
    }

    public float getLat() {
        return lat;
    }

    public float getLong() {
        return lon;
    }

    public void setPos(float lat, float lon) {
        if (this.lat != lat || this.lon != lon) {
            dirty = true;
        }

        this.lat = lat;
        this.lon = lon;
    }
}