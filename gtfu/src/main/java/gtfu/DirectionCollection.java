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

public class DirectionCollection implements Iterable<Direction>, Serializable {
    private Map<String, Direction> map;
    private List<Direction> list;

    // route_direction_id, route_id, direction_id
    public DirectionCollection(String path) {
        this(path, false);
    }

    public DirectionCollection(String path, boolean skipErrors) {
        this();
        try{
            TextFile tf = new TextFile(path + "/directions.txt");
            CSVHeader header = new CSVHeader(tf.getNextLine());

            for (;;) {
                String line = tf.getNextLine();
                if (line == null) break;

                CSVRecord r = new CSVRecord(header, line);
                String routeID = r.get("route_id");
                String directionID = r.get("direction_id");
                String routeDirectionID = routeID + "-" + directionID;
                String name = r.get("direction");
                Direction direction = new Direction(routeDirectionID, routeID, directionID, name);

                map.put(routeDirectionID, direction);
                list.add(direction);
            }

            tf.dispose();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private DirectionCollection() {
        map = new HashMap<String, Direction>();
        list = new ArrayList<Direction>();
    }

    public Direction get(String id) {
        return map.get(id);
    }

    public int getSize() {
        return list.size();
    }

    public void forEach(Consumer<? super Direction> action) {
        list.forEach(action);
    }

    public Iterator<Direction> iterator() {
        return list.iterator();
    }

    public Spliterator<Direction> spliterator() {
        return list.spliterator();
    }
}