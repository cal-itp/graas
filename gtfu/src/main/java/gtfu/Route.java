package gtfu;

import java.io.Serializable;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class Route implements Filterable, Serializable {
    String id;
    String agencyID;
    String name;
    String longName;
    String shortName;
    transient Color color;
    List<Trip> tripList;
    Area area;

    public Route(String id, String agencyID, String name, String longName, String shortName, Color color) {
        this();

        this.id = id;
        this.agencyID = agencyID;
        this.name = name;
        this.longName = longName;
        this.shortName = shortName;
        this.color = color;

        area = new Area();
    }

    private Route() {
        tripList = new ArrayList<Trip>();
    }

    public void write(DataOutputStream out) {
        try {
            out.writeUTF(id);
            out.writeUTF(name);

            out.writeInt(color.getRed());
            out.writeInt(color.getGreen());
            out.writeInt(color.getBlue());
            out.writeInt(color.getAlpha());

            out.writeInt(tripList.size());

            for (Trip t : tripList) {
                t.write(out);
            }

            area.write(out);
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public static Route fromStream(DataInputStream in) {
        try {
            Route r = new Route();

            r.id = in.readUTF();
            r.name = in.readUTF();

            r.color = new Color(
                in.readInt(),
                in.readInt(),
                in.readInt(),
                in.readInt()
            );

            int count = in.readInt();

            for (int i=0; i<count; i++) {
                Trip t = Trip.fromStream(in);

                r.tripList.add(t);
            }

            r.area = Area.fromStream(in);

            return r;
        } catch (IOException e) {
            throw new Fail(e);
        }
    }


    public boolean matches(String key, String value) {
        if (key.equals(Filterable.ROUTE)) {
            return value == null || value.equals(id);
        }

        return false;
    }

    public Color getColor() {
        return color;
    }

    public Area getArea() {
        return area;
    }

    public String getID() {
        return id;
    }

    public String getAgencyID() {
        return agencyID;
    }

    public String getName() {
        return name;
    }

    public String getLongName() {
        return longName;
    }

    public String getShortName() {
        return shortName;
    }

    public void addTrip(Trip trip) {
        tripList.add(trip);
    }

    public int getTripSize() {
        return tripList.size();
    }

    public Trip getTrip(int index) {
        return tripList.get(index);
    }

    public List<Trip> getTripList() {
        return tripList;
    }

    public void computeArea() {
        //Debug.log("Route.computeArea()");

        for (Trip trip : tripList) {
            //Debug.log("-- " + trip.getID());
            area.extend(trip.getShape().getArea());
        }
    }

    public boolean contains(ShapePoint p, int minDistance) {
        Set<String> checked = new HashSet<String>();

        for (Trip trip : tripList) {
            Shape shape = trip.getShape();
            if (checked.contains(shape.getID())) continue;

            checked.add(shape.getID());
            ShapePoint closest = shape.getClosestNeighbor(p);
            int distance = (int)closest.getDistance(p);
            //Debug.log("- distance: " + distance);

            if (closest.getDistance(p) < minDistance) return true;
        }

        return false;
    }
}