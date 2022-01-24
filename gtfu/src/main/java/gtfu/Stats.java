package gtfu;

import java.util.HashMap;
import java.util.Map;
import java.util.Collection;

public class Stats {

    private double avg;
    private double min = Double.MAX_VALUE;
    private double max = Double.MIN_VALUE;


    public Stats(Collection<? extends StatValue> c) {
        double total = 0;
        int count = 0;

        for (StatValue v : c) {
            Double value = v.getValue();
            if (value == null) continue;
            count++;
            total += value;

            if (value < min) {
                min = value;
            }

            if (value > max) {
                max = value;
            }
        }

        avg = total / count;
    }

    public double getAvg() {
        return avg;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }
}

interface StatValue {
    public Double getValue();
}