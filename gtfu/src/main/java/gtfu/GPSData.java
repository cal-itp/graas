package gtfu;

public class GPSData implements StatValue {
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

    // For use as a StatValue:
    public Double getValue() {
        if(secsSinceLastUpdate > 0) {
            Double secsSinceLastUpdateDouble = Double.valueOf(secsSinceLastUpdate);
            return secsSinceLastUpdateDouble;
        } else return null;
    }
}