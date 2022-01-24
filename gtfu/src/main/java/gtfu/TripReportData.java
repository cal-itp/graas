package gtfu;
import ua_parser.Parser;
import ua_parser.Client;

public class TripReportData implements Comparable<TripReportData> {
    public static final String RASPBERRY_KEY = "raspberry ";

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
        // TODO: Create new file for getting deviceClient info
        this.deviceClient = uaParser.parse(agent);
    }

    public int compareTo(TripReportData o) {
        return start - o.start;
    }

    // Combining name with start time creates a trip name
    public String getTripName() {
        return getCleanName() + " @ " + Time.getHMForMillis(start);
    }

    public String getUuidTail() {
        return uuid.substring(uuid.length() - 4, uuid.length());
    }

    public String getAgent() {
        if (agent.startsWith(RASPBERRY_KEY)) {
            String s = agent.substring(RASPBERRY_KEY.length());
            int index = s.indexOf(' ');

            if (index > 0) {
                return s.substring(index + 1);
            } else {
                return "";
            }
        }

        String userAgentFamily = deviceClient.userAgent.family;
        String userAgentMajor = deviceClient.userAgent.major;
        String userAgentMinor = deviceClient.userAgent.minor;

        return userAgentFamily + ((userAgentMajor == null) ? "" :
                    ((userAgentMinor == null) ? "." + userAgentMajor :  "." + userAgentMajor + "." + userAgentMinor));
    }

    public String getOs() {
        if (agent.startsWith(RASPBERRY_KEY)) {
            return "Raspberry Pi OS";
        }

        String osFamily = deviceClient.os.family;
        String osMajor = deviceClient.os.major;
        String osMinor = deviceClient.os.minor;

        return osFamily + ((osMajor == null) ? "" :
                    ((osMinor == null) ? "." + osMajor :  "." + osMajor + "." + osMinor));
    }

    public String getDevice() {
        if (agent.startsWith(RASPBERRY_KEY)) {
            String s = agent.substring(RASPBERRY_KEY.length());
            int index = s.indexOf(' ');

            if (index > 0) {
                return RASPBERRY_KEY + s.substring(0, index);
            } else {
                return agent;
            }
        }

        return deviceClient.device.family;
    }

    public String getVehicleId() {
        return vehicleId;
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
