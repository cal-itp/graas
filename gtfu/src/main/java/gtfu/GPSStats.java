package gtfu;

import java.util.HashMap;
import java.util.Map;

import java.math.RoundingMode;
import java.text.DecimalFormat;

public class GPSStats {

    private static final DecimalFormat df = new DecimalFormat("0.00");
    public int maxUpdateTime = 0;
    public int minUpdateTime = 999999;
    public float avgUpdateTime = 0;

    public GPSStats(Map<String, GPSData> latLonMap) {
        int runningTotalUpdates = 0;
        int runningTotalTimeSinceUpdate = 0;
        int secsSinceLastUpdate = 0;

        for (String latLon : latLonMap.keySet()) {
            secsSinceLastUpdate = latLonMap.get(latLon).secsSinceLastUpdate;
            if(secsSinceLastUpdate >= 0) {
                runningTotalUpdates += 1;
                runningTotalTimeSinceUpdate += latLonMap.get(latLon).secsSinceLastUpdate;
                if (secsSinceLastUpdate > maxUpdateTime) {
                        maxUpdateTime = secsSinceLastUpdate;
                    }
                if (secsSinceLastUpdate < minUpdateTime) {
                    minUpdateTime = secsSinceLastUpdate;
                }
           }
        }
        avgUpdateTime = (float) runningTotalTimeSinceUpdate / (float) runningTotalUpdates;
    }

    public String getAverageUpdateTimeStr() {
        return String.valueOf(df.format(avgUpdateTime));
    }

    public String getMinUpdateTimeStr() {
        return String.valueOf(minUpdateTime);
    }

    public String getMaxUpdateTimeStr() {
        return String.valueOf(maxUpdateTime);
    }
}






