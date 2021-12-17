package gtfu;

public class Conversions {
    public static float feetToMiles(int feet) {
        return feet / 5280f;
    }

    public static float secondsToHours(int seconds) {
        return seconds / (float)Time.SECONDS_PER_HOUR;
    }

    public static float millisToSeconds(int millis) {
        return millis / (float)Time.MILLIS_PER_SECOND;
    }

    public static float millisToHours(int millis) {
        return millis / (float)Time.MILLIS_PER_HOUR;
    }

    public static float latLongDegreesToMeters(float deltaDegrees) {
        return deltaDegrees * 111045;
    }

    public static float latLongDegreesToMiles(float deltaDegrees) {
        return deltaDegrees * 69;
    }
}