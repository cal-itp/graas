package gtfu;
import java.util.HashMap;
import java.util.Map;
import ua_parser.Parser;
import ua_parser.Client;

public class TripReportData implements Comparable<TripReportData> {
    public String id;
    String name;
    int start;
    int duration;
    int x;
    int y;
    int width;
    int height;
    String uuid;
    String agent;
    String vehicleId;
    Client deviceClient;
    Parser uaParser = new Parser();

    public TripReportData(String id, String name, int start, int duration, String uuid, String agent, String vehicleId) {
        this.id = id;
        this.name = name;
        this.start = start;
        this.duration = duration;
        this.uuid = uuid;
        this.agent = agent;
        this.vehicleId = vehicleId;
        this.deviceClient = uaParser.parse(agent);
    }

    public int compareTo(TripReportData o) {
        return start - o.start;
    }

    // Combining route name with start time creates a trip name
    public String getTripName() {
        return getCleanName() + " @ " + Time.getHMForMillis(start);
    }

    public String getUuidTail() {
        return uuid.substring(uuid.length() - 4, uuid.length());
    }

    public String getAgent() {
        return deviceClient.userAgent.family + "."
                + deviceClient.userAgent.major + "."
                + deviceClient.userAgent.minor;
    }

    public String getOS() {
        return deviceClient.os.family + "."
                + deviceClient.os.major + "."
                + deviceClient.os.minor;
    }

    public String getDevice() {
        return deviceClient.device.family;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    // Maybe should be array of strings or just not exist
    public Map<String, String> getAttributeMap(){
        Map<String, String> attributeMap = new HashMap();
        attributeMap.put("v: ",getVehicleId());
        attributeMap.put("a: ",getAgent());
        attributeMap.put("u: ",getUuidTail());

        return attributeMap;
    }

    public boolean overlaps(TripReportData td) {
        return (td.start >= start && td.start < start + duration)
            || (start >= td.start && start < td.start + td.duration);
    }

    // This logic performs cleanup on use cases that may or may not still be present.
    // TODO: Consider removing
    private String getCleanName() {
        String s = new String(name);
        int i = s.length();

        int i1 = s.indexOf('(');
        if (i1 > 0) i = i1 - 1;
        if (i1 < 0) i1 = s.length();

        int i2 = s.indexOf('/');
        if (i2 > 0 && i2 < i1) i = i2;
        if (i2 < 0) i2 = s.length();

        s = s.substring(0, i);

        return s;
    }
}
