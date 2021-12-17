package gtfu;

import java.io.Serializable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;

public class StopCollection implements Iterable<Stop>, Serializable {
    private Map<String, Stop> map;
    private List<Stop> list;
    private Area area;

    // stop_id,stop_code,stop_name,stop_desc,stop_lat,stop_lon,stop_url,location_type,parent_station,stop_timezone,wheelchair_boarding
    public StopCollection(String path) {
        this();
        area = new Area();

        TextFile tf = new TextFile(path + "/stops.txt");
        CSVHeader header = new CSVHeader(tf.getNextLine());
        Stop stop = null;
        int count = 2;
        String lastLat = null;
        String lastLon = null;

        for (;;) {
            String line = tf.getNextLine();
            if (line == null) break;

            CSVRecord r = new CSVRecord(header, line);
            String id = r.get("stop_id");
            String name = r.get("stop_name");

            /*float lat = 0;
            float lon = 0;
            try {
                lat = Float.parseFloat(r.get("stop_lat"));
                lon = Float.parseFloat(r.get("stop_lon"));
            } catch (NumberFormatException e) {
                Debug.error("* unable to parse lat/long on line " + count);
                Debug.error("- line: " + line);
                count++;
                continue;
            }*/

            String lat = r.get("stop_lat");
            String lon = r.get("stop_lon");
            String parentID = r.get("parent_station");

            /*if (Util.isEmpty(lat) && parentID != null) {
                stop = map.get(parentID);

                if (stop != null) {
                    lat = String.valueOf(stop.lat);
                    lon = String.valueOf(stop.lon);
                }
            }*/

            if (Util.isEmpty(lat)) lat = lastLat;
            if (Util.isEmpty(lon)) lon = lastLon;

            try {
                stop = new Stop(id, name, Float.parseFloat(lat), Float.parseFloat(lon));
                area.update(stop.lat, stop.lon);
            } catch (NumberFormatException e) {
                Debug.error("* count " + count);
                Debug.error("- lat: " + lat);
                Debug.error("- lon: " + lon);
                Debug.error("- lastLat: " + lastLat);
                Debug.error("- lastLon: " + lastLon);
                Debug.error("- line: " + line);
            }

            map.put(id, stop);
            list.add(stop);

            if (!Util.isEmpty(lat)) lastLat = lat;
            if (!Util.isEmpty(lon)) lastLon = lon;

            count++;
        }

        tf.dispose();

        for (String id : map.keySet()) {
            stop = map.get(id);
            //Debug.log(String.format("  - stop %s: %s", id, stop.getName()));
        }
    }

    private StopCollection() {
        map = new HashMap<String, Stop>();
        list = new ArrayList<Stop>();
    }

    public void write(DataOutputStream out) {
        //Debug.log("StopCollection.write()");
        //Debug.log("- list.size(): " + list.size());

        try {
            out.writeInt(list.size());
            int count = 0;

            for (Stop s : list) {
                s.write(out);
                //Debug.log(count++);
            }

            area.write(out);
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public static StopCollection fromStream(DataInputStream in) {
        try {
            StopCollection c = new StopCollection();

            int count = in.readInt();

            for (int i=0; i<count; i++) {
                Stop s = Stop.fromStream(in);

                c.list.add(s);
                c.map.put(s.getID(), s);
            }

            c.area = Area.fromStream(in);

            return c;
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public Stop get(int index) {
        return list.get(index);
    }

    public Stop get(String id) {
        return map.get(id);
    }

    public Area getArea() {
        return area;
    }

    public int getSize() {
        return list.size();
    }

    public void clearArrivalPredictions() {
        for (Stop stop : list) {
            stop.clearArrivalPredictions();
        }
    }

    public void sortArrivalPredictions() {
        for (Stop stop : list) {
            stop.sortArrivalPredictions();
        }
    }

    public void forEach(Consumer<? super Stop> action) {
        list.forEach(action);
    }

    public Iterator<Stop> iterator() {
        return list.iterator();
    }

    public Spliterator<Stop> spliterator() {
        return list.spliterator();
    }
}