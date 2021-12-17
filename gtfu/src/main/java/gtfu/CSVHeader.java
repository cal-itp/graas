package gtfu;

import java.util.Map;
import java.util.HashMap;

public class CSVHeader {
    private Map<String, Integer> map;
    private String[] arr;

    public CSVHeader(String s) {
        //Debug.log("CSVHeader.CSVHeader()");
        //Debug.log("- s: " + s);

        arr = s.split(",");
        map = new HashMap<String, Integer>();

        for (int i=0; i<arr.length; i++) {
            String key = Util.stripQuotes(arr[i]);
            //Debug.log("-- key: " + key);
            map.put(key, i);
            arr[i] = key;
        }
    }

    public int getLength() {
        return map.size();
    }

    public String[] getKeys() {
        return arr;
    }

    public int getIndex(String key) {
        //Debug.log("CSVHeader.getIndex()");
        //Debug.log("- map.keySet(): " + map.keySet());
        //Debug.log("- key: " + key);
        if (map.get(key) == null) return -1;
        return map.get(key);
    }
}