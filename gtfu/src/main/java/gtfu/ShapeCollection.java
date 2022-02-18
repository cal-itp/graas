package gtfu;

import java.io.Serializable;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;

public class ShapeCollection implements Iterable<Shape>, Serializable {
    private Map<String, Shape> map;
    private List<Shape> list;
    private Area area;

    // shape_id,shape_pt_lat,shape_pt_lon,shape_pt_sequence
    public ShapeCollection(String path, ProgressObserver observer) {
        this();
        area = new Area();

        TextFile f = new TextFile(path + "/shapes.txt");
        CSVHeader header = new CSVHeader(f.getNextLine());
        int count = 1;

        for (;;) {
            String line = f.getNextLine();
            if (line == null) break;

            if (observer != null) observer.tick();
            CSVRecord r = new CSVRecord(header, line);
            String id = r.get("shape_id");
            Shape shape = map.get(id);

            if (shape == null) {
                shape = new Shape(id);
                map.put(id, shape);
                list.add(shape);
            }

            ShapePoint p = new ShapePoint(
                Float.parseFloat(r.get("shape_pt_lat")),
                Float.parseFloat(r.get("shape_pt_lon"))
            );

            area.update(p);
            shape.add(p);
        }

        f.dispose();

        List<String> keys = new ArrayList<String>(map.keySet());
        Collections.sort(keys);

        for (String id : keys) {
            Shape s = map.get(id);
            s.check();
            //Debug.log(String.format("  - shape '%9s': %d points", id, s.getSize()));
        }
    }

    public Set<String> getKeys() {
        return map.keySet();
    }

    private ShapeCollection() {
        map = new HashMap<String, Shape>();
        list = new ArrayList<Shape>();
    }

    public void write(DataOutputStream out) {
        //Debug.log("ShapeCollection.write()");
        //Debug.log("- list.size(): " + list.size());

        try {
            out.writeInt(list.size());
            int count = 0;

            for (Shape s : list) {
                s.write(out);
                //Debug.log(count++);
            }

            area.write(out);
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public static ShapeCollection fromStream(DataInputStream in) {
        try {
            ShapeCollection c = new ShapeCollection();

            int count = in.readInt();

            for (int i=0; i<count; i++) {
                Shape s = Shape.fromStream(in);

                c.list.add(s);
                c.map.put(s.getID(), s);
            }

            c.area = Area.fromStream(in);

            return c;
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public Shape get(String shapeID) {
        return map.get(shapeID);
    }

    public int getSize() {
        return list.size();
    }

    public Area getArea() {
        return area;
    }

    public void forEach(Consumer<? super Shape> action) {
        list.forEach(action);
    }

    public Iterator<Shape> iterator() {
        return list.iterator();
    }

    public Spliterator<Shape> spliterator() {
        return list.spliterator();
    }
}