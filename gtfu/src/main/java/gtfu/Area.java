package gtfu;

import java.io.Serializable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

// ### lat/long calculations assume North American location
public class Area implements Serializable {
    public static final float HALF_MILE_DECIMAL = .00924f;

    public ShapePoint topLeft;
    public ShapePoint bottomRight;

    public Area() {
        topLeft = new ShapePoint();
        bottomRight = new ShapePoint();
    }

    public Area(Area area) {
        topLeft = new ShapePoint(area.topLeft);
        bottomRight = new ShapePoint(area.bottomRight);
    }

    public Area(ShapePoint tl, ShapePoint br) {
        topLeft = new ShapePoint(tl);
        bottomRight = new ShapePoint(br);
    }

    public Area(float clat, float clon, float skirt) {
        float hdelta = skirt;
        float vdelta = hdelta * .76f;

        topLeft = new ShapePoint(clat + vdelta, clon - hdelta);
        bottomRight = new ShapePoint(clat - vdelta, clon + hdelta);
    }

    public void write(DataOutputStream out) {
        topLeft.write(out);
        bottomRight.write(out);
    }

    public static Area fromStream(DataInputStream in) {
        Area a = new Area();

        a.topLeft = ShapePoint.fromStream(in);
        a.bottomRight = ShapePoint.fromStream(in);

        return a;
    }

    public void extend(Area area) {
        //Debug.log("Area.extend()");
        //Debug.log("- area: " + area);
        update(area.topLeft);
        update(area.bottomRight);
        //Debug.log("- this: " + this);
    }

    public void update(ShapePoint p) {
        update(p.lat, p.lon);
    }

    public void update(float lat, float lon) {
        //Debug.log("Area.update()");
        //Debug.log("- lat: " + lat);
        //Debug.log("- lon: " + lon);
        //Debug.log("- topLeft    : " + topLeft);
        //Debug.log("- bottomRight: " + bottomRight);

        topLeft.setLongIfLess(lon);
        topLeft.setLatIfGreater(lat);

        bottomRight.setLongIfGreater(lon);
        bottomRight.setLatIfLess(lat);

        //Debug.log("- topLeft    : " + topLeft);
        //Debug.log("- bottomRight: " + bottomRight);
    }

    public Area copyIfExtending(ShapePoint p) {
        if (contains(p)) {
            return this;
        } else {
            Area area = new Area(this);
            area.update(p);

            return area;
        }
    }

    public float getLatDelta() {
        return Math.abs(Math.abs(topLeft.lat) - Math.abs(bottomRight.lat));
    }

    public float getLongDelta() {
        return Math.abs(Math.abs(topLeft.lon) - Math.abs(bottomRight.lon));
    }

    public int getWidthInFeet() {
        return (int)Util.getHaversineDistance(0, topLeft.lon, 0, bottomRight.lon);
    }

    public int getHeightInFeet() {
        return (int)Util.getHaversineDistance(topLeft.lat, 0, bottomRight.lat, 0);
    }

    public float getAspectRatio() {
        return getLongDelta() / getLatDelta();
    }

    public int getDiagonalInFeet() {
        int a = getWidthInFeet();
        int b = getHeightInFeet();

        return (int)Math.sqrt((long)a * a + b * b);
    }

    public float getLatFraction(float lat) {
        return getLatFraction(lat, true);
    }

    public float getLatFraction(float lat, boolean flagErrors) {
        if (flagErrors && (lat < bottomRight.lat || lat > topLeft.lat)) {
            throw new Fail(String.format("bottomRight.lat: %f, lat: %f, topLeft.lat: %f", bottomRight.lat, lat, topLeft.lat));
        }

        float delta = getLatDelta();

        return 1 - ((lat - bottomRight.lat) / delta);
    }

    public float getLongFraction(float lon) {
        return getLongFraction(lon, true);
    }

    public float getLongFraction(float lon, boolean flagErrors) {
        if (flagErrors && (lon > bottomRight.lon || lon < topLeft.lon)) {
            throw new Fail(String.format("bottomRight.lon: %f, lon: %f, topLeft.lon: %f", bottomRight.lon, lon, topLeft.lon));
        }

        float delta = getLongDelta();

        return 1 - ((Math.abs(lon) - Math.abs(bottomRight.lon)) / delta);
    }


    public boolean contains(ShapePoint p) {
        return contains(p.lat, p.lon);
    }

    public boolean contains(float lat, float lon) {
        return lat >= bottomRight.lat && lat <= topLeft.lat
            && lon <= bottomRight.lon && lon >= topLeft.lon;
    }

    public String toString() {
        return String.format("topLeft: %s, bottomRight: %s", topLeft.toString(), bottomRight.toString());
    }
}