package gtfu;

public class GPSData {
    public long millis;
    public float lat;
    public float lon;
    public int count;

    public GPSData(long millis, float lat, float lon, int count) {
        this.millis = millis;
        this.lat = lat;
        this.lon = lon;
        this.count = count;
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
}