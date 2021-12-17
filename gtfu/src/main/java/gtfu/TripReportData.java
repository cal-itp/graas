package gtfu;

public class TripReportData implements Comparable<TripReportData> {
    public String id;
    String headSign;
    int start;
    int duration;
    int x;
    int y;
    int width;
    int height;

    public TripReportData(String id, String headSign, int start, int duration) {
        this.id = id;
        this.headSign = headSign;
        this.start = start;
        this.duration = duration;
    }

    public int compareTo(TripReportData o) {
        return start - o.start;
    }

    public String toString() {
        return headSign + " @ " + Time.getHMForMillis(start) + " - " + Time.getHMForMillis(start + duration);
    }

    public boolean overlaps(TripReportData td) {
        return (td.start >= start && td.start < start + duration)
            || (start >= td.start && start < td.start + td.duration);
    }
}
