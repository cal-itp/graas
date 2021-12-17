package gtfu;

import java.io.Serializable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public class Stop implements Filterable, GeoObject, Serializable {
    transient List<ArrivalPrediction> arrivalPredictions;
    Set<String> routeIDs;
    String id;
    String name;
    float lat;
    float lon;
    Map<String, ShapePoint> map;

    public Stop(String id, String name, float lat, float lon) {
        this();

        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lon = lon;
    }

    private Stop() {
        arrivalPredictions = new ArrayList<ArrivalPrediction>();
        routeIDs = new HashSet<String>();
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        arrivalPredictions = new ArrayList<ArrivalPrediction>();
    }

    public void write(DataOutputStream out) {
        //Debug.log("Stop.write()");

        try {
            out.writeInt(routeIDs.size());

            for (String i : routeIDs) {
                out.writeUTF(i);
            }

            out.writeUTF(id);
            out.writeUTF(name);
            out.writeFloat(lat);
            out.writeFloat(lon);

            if (map == null) {
                out.writeInt(0);
            } else {
                out.writeInt(map.size());

                for (String k : map.keySet()) {
                    out.writeUTF(k);
                    map.get(k).write(out);

                    //Debug.log(String.format("%s -> %s", k, map.get(k)));
                }
            }
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public static Stop fromStream(DataInputStream in) {
        try {
            Stop stop = new Stop();
            //Debug.log("------------------------");
            //Debug.log("- stop.routeIDs:");

            int count = in.readInt();

            for (int i=0; i<count; i++) {
                String s = in.readUTF();
                stop.routeIDs.add(s);
                //Debug.log("-- " + s);
            }

            stop.id = in.readUTF();
            //Debug.log("- stop.id: " + stop.id);
            stop.name = in.readUTF();
            //Debug.log("- stop.name: " + stop.name);
            stop.lat = in.readFloat();
            //Debug.log("- stop.lat: " + stop.lat);
            stop.lon = in.readFloat();
            //Debug.log("- stop.lon: " + stop.lon);

            count = in.readInt();

            if (count > 0) {
                stop.map = new HashMap<String, ShapePoint>();
                //Debug.log("- stop.map:");

                for (int i=0; i<count; i++) {
                    String k = in.readUTF();
                    ShapePoint p = ShapePoint.fromStream(in);
                    stop.map.put(k, p);
                    //Debug.log("-- " + k +  " -> " + p);
                }
            }

            return stop;
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public void addRouteID(String routeID) {
        routeIDs.add(routeID);
    }

    public boolean matches(String key, String value) {
        if (key.equals(Filterable.ROUTE)) {
            return value == null || routeIDs.contains(value);
        }

        return false;
    }

    public void add(ArrivalPrediction ap) {
        arrivalPredictions.add(ap);
    }

    public void sortArrivalPredictions() {
        Collections.sort(arrivalPredictions);
    }

    public void clearArrivalPredictions() {
        arrivalPredictions.clear();
    }

    public String getArrivalPredictionsString() {
        StringBuilder sb = new StringBuilder();

        sb.append(name);
        sb.append(": ");

        for (int i=0; i<arrivalPredictions.size(); i++) {
            ArrivalPrediction ap = arrivalPredictions.get(i);

            sb.append(ap.getArrivalString());

            if (i < arrivalPredictions.size() - 1) {
                sb.append(", ");
            }
        }

        return sb.toString();
    }

    public int getArrivalPredictionCount() {
        return arrivalPredictions.size();
    }

    public ShapePoint getClosestShapePoint(Trip trip) {
        if (map == null) {
            map = new HashMap<String, ShapePoint>();
        }

        String tripID = trip.getID();
        ShapePoint closest = map.get(tripID);

        if (closest == null) {
            closest = trip.getShape().getCloseNeighbor(lat, lon);
            map.put(tripID, closest);
            //Debug.log(String.format("+ stop id: %s, trip id: %s", id, tripID));
        }

        return closest;
    }

    public String getID() {
        return id;
    }

    public String getName() {
        return name;
    }

    public float getLat() {
        return lat;
    }

    public float getLong() {
        return lon;
    }

    public String[] getLabelText() {
        int predCount = Math.min(3, arrivalPredictions.size());
        String[] text = new String[1 + (arrivalPredictions.isEmpty() ? 1 : predCount)];
        int index = 0;

        text[index++] = name;

        if (arrivalPredictions.size() == 0) {
            text[index++] = "no arrival predictions";
        } else {
            for (int i=0; i<predCount; i++) {
                ArrivalPrediction ap = arrivalPredictions.get(i);
                text[index++] = ap.getArrivalString();
            }
        }

        return text;
    }
}