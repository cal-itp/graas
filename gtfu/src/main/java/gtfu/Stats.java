package gtfu;

import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

public class Stats {

    private double avg;
    private double min = Double.MAX_VALUE;
    private double max = Double.MIN_VALUE;


    public Stats(List<Double> l) {
        double total = 0;
        int count = 0;

        for (Double d : l) {
            count++;
            total += d;

            if (d < min) {
                min = d;
            }

            if (d > max) {
                max = d;
            }
        }
        if (count > 0) avg = total / count;
    }

    public double getAvg() {
        return avg;
    }

    public double getMin() {
        Debug.log("min: " + min);
        return min;
    }

    public double getMax() {
        Debug.log("max: " + max);
        return max;
    }
}