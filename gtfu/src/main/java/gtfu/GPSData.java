package gtfu;

public class GPSData {
    public long millis;
    public int secsSinceLastUpdate;
    public float lat;
    public float lon;
    public int count;

    public GPSData(long millis, int secsSinceLastUpdate, float lat, float lon) {
        this.millis = millis;
        this.secsSinceLastUpdate = secsSinceLastUpdate;
        this.lat = lat;
        this.lon = lon;
        this.count = 1;
    }

    public String toString() {
        return String.format("millis: %d, lat: %f, long: %f", millis, lat, lon);
    }

    public String toCSVLine() {
        return String.format("%d,%f,%f", millis / 1000, lat, lon);
    }

    public void increment() {
        count++;
    }

    public Double getSecsSinceLastUpdateDouble() {
        return Double.valueOf(secsSinceLastUpdate);
    }

    public Double getMillisDouble() {
        return Double.valueOf(millis);
    }
}