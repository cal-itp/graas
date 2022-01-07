package gtfu;

import java.io.Serializable;

public class ArrivalPrediction implements Comparable<ArrivalPrediction>, Serializable {
    Vehicle vehicle;
    Stop stop;
    int minutes;

    public ArrivalPrediction(Vehicle vehicle, Stop stop, int minutes) {
        this.vehicle = vehicle;
        this.stop = stop;
        this.minutes = minutes;
    }

    public int compareTo(ArrivalPrediction a) {
        return minutes - a.minutes;
    }

    public String getArrivalString() {
        return vehicle.trip.getName() + " in " + minutes + " minutes";
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("{vehicle: ");
        sb.append(vehicle.id);
        sb.append(", stop: ");
        sb.append(stop.name);
        sb.append(", minutes: ");
        sb.append(minutes);
        sb.append("}");

        return sb.toString();
    }
}