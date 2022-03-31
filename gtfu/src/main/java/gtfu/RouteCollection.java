package gtfu;

import java.io.Serializable;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;

public class RouteCollection implements Iterable<Route>, Serializable {
    private Map<String, Route> map;
    private List<Route> list;

    // route_id,route_short_name,route_long_name,route_desc,route_url,route_color,route_text_color,route_type
    public RouteCollection(String path, TripCollection tripCollection) {
        this(path, tripCollection, false);
    }

    public RouteCollection(String path, TripCollection tripCollection, boolean skipErrors) {
        this();

        TextFile tf = new TextFile(path + "/routes.txt");
        CSVHeader header = new CSVHeader(tf.getNextLine());

        for (;;) {
            String line = tf.getNextLine();
            if (line == null) break;

            CSVRecord r = new CSVRecord(header, line);
            String id = r.get("route_id");
            String agencyID = r.get("agency_id");
            String name = r.get("route_long_name");

            if (name == null) {
                name = r.get("route_short_name");
            }

            String longName = r.get("route_long_name");
            String shortName = r.get("route_short_name");
            String hexRGB = r.get("route_color");
            //Debug.log("- hexRGB: " + hexRGB);

            if (hexRGB.length() == 0) {
                hexRGB = "ffffff";
            }

            Color color = Util.getColorFromHexString(hexRGB);

            Route route = new Route(id, agencyID, name, longName, shortName, color);
            //Debug.log("  - " + id + ": " + name);

            map.put(id, route);
            list.add(route);
        }

        tf.dispose();

        for (Trip trip : tripCollection) {
            Route route = map.get(trip.getRouteID());

            if (route == null) {
                Util.fail(
                    String.format(
                        "fatal error, trip '%s' references non-existing route ID '%s'",
                        trip.getID(),
                        trip.getRouteID()
                    ),
                    !skipErrors
                );
            } else {
                route.addTrip(trip);
            }
        }
    }

    private RouteCollection() {
        map = new HashMap<String, Route>();
        list = new ArrayList<Route>();
    }

    public void write(DataOutputStream out) {
        try {
            out.writeInt(list.size());
            int count = 0;

            for (Route r : list) {
                r.write(out);
            }
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public static RouteCollection fromStream(DataInputStream in) {
        try {
            RouteCollection c = new RouteCollection();

            int count = in.readInt();

            for (int i=0; i<count; i++) {
                Route r = Route.fromStream(in);

                c.list.add(r);
                c.map.put(r.getID(), r);
            }

            return c;
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public Route get(int index) {
        return list.get(index);
    }

    public Route get(String id) {
        return map.get(id);
    }

    public int getSize() {
        return list.size();
    }

    public void forEach(Consumer<? super Route> action) {
        list.forEach(action);
    }

    public Iterator<Route> iterator() {
        return list.iterator();
    }

    public Spliterator<Route> spliterator() {
        return list.spliterator();
    }
}