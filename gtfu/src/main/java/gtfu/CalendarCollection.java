package gtfu;

import java.io.Serializable;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;

public class CalendarCollection implements Iterable<Calendar>, Serializable {
    private static final String DATE_FORMAT = "yyyyMMdd";
    private Map<String, Calendar> map;
    private List<Calendar> list;

    //service_id,service_name,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date
    public CalendarCollection(String path) {
        this();

        TextFile tf = new TextFile(path + "/calendar.txt");
        CSVHeader header = new CSVHeader(tf.getNextLine());

        for (;;) {
            String line = tf.getNextLine();
            if (line == null) break;

            CSVRecord r = new CSVRecord(header, line);
            String id = r.get("service_id");

            Calendar cal = new Calendar(
                id,
                r.getInt("monday"),
                r.getInt("tuesday"),
                r.getInt("wednesday"),
                r.getInt("thursday"),
                r.getInt("friday"),
                r.getInt("saturday"),
                r.getInt("sunday"),
                Time.parseDateAsLong(DATE_FORMAT, r.get("start_date")),
                Time.parseDateAsLong(DATE_FORMAT, r.get("end_date"))
            );

            map.put(id, cal);
            list.add(cal);
        }

        tf.dispose();
    }

    private CalendarCollection() {
        list = new ArrayList<Calendar>();
        map = new HashMap<String, Calendar>();
    }

    public Calendar get(int index) {
        return list.get(index);
    }

    public Calendar get(String id) {
        return map.get(id);
    }

    public int getSize() {
        return list.size();
    }

    public void forEach(Consumer<? super Calendar> action) {
        list.forEach(action);
    }

    public Iterator<Calendar> iterator() {
        return list.iterator();
    }

    public Spliterator<Calendar> spliterator() {
        return list.spliterator();
    }
}