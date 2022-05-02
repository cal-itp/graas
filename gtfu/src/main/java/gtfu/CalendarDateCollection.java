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

public class CalendarDateCollection implements Iterable<CalendarDate>, Serializable {
    private static final String DATE_FORMAT = "yyyyMMdd";
    private Map<String, CalendarDate> map;
    private List<CalendarDate> list;

    //service_id,service_name,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date
    public CalendarDateCollection(String path) {
        this();

        TextFile tf = new TextFile(path + "/calendar_dates.txt");
        CSVHeader header = new CSVHeader(tf.getNextLine());

        for (;;) {
            String line = tf.getNextLine();
            if (line == null) break;

            CSVRecord r = new CSVRecord(header, line);
            String id = r.get("service_id");

            CalendarDate cal = new CalendarDate(
                id,
                r.get("date"),
                r.getInt("exception_type")
            );

            map.put(id, cal);
            list.add(cal);
        }

        tf.dispose();
    }

    private CalendarDateCollection() {
        list = new ArrayList<CalendarDate>();
        map = new HashMap<String, CalendarDate>();
    }

    public CalendarDate get(int index) {
        return list.get(index);
    }

    public CalendarDate get(String id) {
        return map.get(id);
    }

    public int getSize() {
        return list.size();
    }

    public void forEach(Consumer<? super CalendarDate> action) {
        list.forEach(action);
    }

    public Iterator<CalendarDate> iterator() {
        return list.iterator();
    }

    public Spliterator<CalendarDate> spliterator() {
        return list.spliterator();
    }

    public List<CalendarDate> getList(){
        return list;
    }
}