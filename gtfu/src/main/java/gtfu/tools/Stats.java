package gtfu.tools;
import gtfu.*;

import java.util.HashMap;
import java.util.Map;

public class Stats {

    private int maxUpdateTime = 0;
    private int minUpdateTime = 999999;
    private float avgUpdateTime = 0;

    public void Stats(Map<String, GPSData> latLonMap) {
        int runningTotalUpdates = 0;
        int runningTimeSinceUpdateSum = 0;
        int secsSinceLastUpdate = 0;

        for (String latLon : latLonMap.keySet()) {
            secsSinceLastUpdate = latLonMap.get(latLon).secsSinceLastUpdate;
            if(secsSinceLastUpdate > 0){
                runningTotalUpdates += 1;
                runningTimeSinceUpdateSum += latLonMap.get(latLon).secsSinceLastUpdate;
                if (secsSinceLastUpdate > maxUpdateTime) {
                    secsSinceLastUpdate = maxUpdateTime;
                }
                if (secsSinceLastUpdate < minUpdateTime) {
                    secsSinceLastUpdate = minUpdateTime;
                }
            }
        }

        avgUpdateTime = (float) runningTimeSinceUpdateSum / (float) runningTotalUpdates;
    }

    public float getAverageUpdateTime() {
        return avgUpdateTime;
    }

    public int getMinUpdateTime() {
        return minUpdateTime;
    }

    public int getMaxUpdateTime() {
        return maxUpdateTime;
    }
}