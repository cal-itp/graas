package gtfu;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class Time {
    public static final int SECONDS_PER_MINUTE = 60;
    public static final int SECONDS_PER_HOUR   = 60 * SECONDS_PER_MINUTE;
    public static final int SECONDS_PER_DAY    = 24 * SECONDS_PER_HOUR;
    public static final int SECONDS_PER_WEEK   =  7 * SECONDS_PER_DAY;

    public static final int MILLIS_PER_SECOND = 1000;
    public static final int MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND;
    public static final int MILLIS_PER_HOUR   = 60 * MILLIS_PER_MINUTE;
    public static final int MILLIS_PER_DAY    = 24 * MILLIS_PER_HOUR;

    public static final int DAYS_PER_WEEK = 7;

    public static long getMidnightTimestamp() {
        return getMidnightTimestamp(Util.now());
    }

    public static long getMidnightTimestamp(long millis) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/Los_Angeles"), Locale.ENGLISH);

        cal.setTimeInMillis(millis);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        return cal.getTimeInMillis();
    }

    public static int getDayOffsetMillis() {
        return getDayOffsetMillis(Util.now());
    }

    public static int getDayOffsetMillis(long millis) {
        return getDayOffsetMillis("America/Los_Angeles", millis);
    }

    public static int getDayOffsetMillis(String tzName) {
        return getDayOffsetMillis(tzName, Util.now());
    }

    public static int getDayOffsetMillis(String tzName, long millis) {
        //Debug.log("Time.getDayOffsetMillis()");
        //Debug.log("- millis: " + millis);

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(tzName), Locale.ENGLISH);
        //Debug.log("- cal: " + cal);
        cal.setTimeInMillis(millis);
        //Debug.log("- cal: " + cal);

        int hour = cal.get(Calendar.HOUR_OF_DAY);
        //Debug.log("- hour: " + hour);
        int min = cal.get(Calendar.MINUTE);
        //Debug.log("- min: " + min);
        int sec = cal.get(Calendar.SECOND);
        int mil = cal.get(Calendar.MILLISECOND);

        return hour * MILLIS_PER_HOUR + min * MILLIS_PER_MINUTE + sec * MILLIS_PER_SECOND + mil;
    }

    // input format: hh:mm:ss
    public static int getMillisForTime(String hhmmss) {
        String[] arg = hhmmss.split(":");

        int hour = Integer.parseInt(arg[0].trim());
        int min = Integer.parseInt(arg[1]);
        int sec = Integer.parseInt(arg[2]);

        return hour * MILLIS_PER_HOUR
            + min * MILLIS_PER_MINUTE
            + sec * MILLIS_PER_SECOND;
    }


    // seconds are a millisecond resolution offset into a day
    public static String getHMForSeconds(int seconds, boolean includeAMPM) {
        int hour = seconds / SECONDS_PER_HOUR;
        seconds -= hour * SECONDS_PER_HOUR;
        int min = seconds / SECONDS_PER_MINUTE;

        String amPm = hour >= 12 ? " pm" : " am";

        if (hour == 0) hour = 12;
        else if (hour > 12) hour -= 12;

        return String.format("%d:%02d%s", hour, min, includeAMPM ? amPm : "");
    }

    // millis are a millisecond resolution offset into a day
    public static String getHMForMillis(int millis) {
        int hour = millis / MILLIS_PER_HOUR;
        millis -= hour * MILLIS_PER_HOUR;
        int min = millis / MILLIS_PER_MINUTE;

        String amPm = hour >= 12 ? "pm" : "am";

        if (hour == 0) hour = 12;
        else if (hour > 12) hour -= 12;

        return String.format("%d:%02d %s", hour, min, amPm);
    }

    // millis are a millisecond resolution offset into a day
    public static String getHMSForMillis(int millis) {
        int hour = millis / MILLIS_PER_HOUR;
        millis -= hour * MILLIS_PER_HOUR;
        int min = millis / MILLIS_PER_MINUTE;
        millis -= min * MILLIS_PER_MINUTE;
        int sec = millis / MILLIS_PER_SECOND;

        String amPm = hour >= 12 ? "pm" : "am";

        if (hour == 0) hour = 12;
        else if (hour > 12) hour -= 12;

        return String.format("%d:%02d:%02d %s", hour, min, sec, amPm);
    }

    public static String getTimeDeltaString(int seconds, boolean showEarlyLate) {
        //Debug.log("Time.getTimeDeltaString()");
        //Debug.log("- seconds: " + seconds);

        String status = seconds < 0 ? "early" : "late";
        if (!showEarlyLate) status = "";

        seconds = Math.abs(seconds);

        int hour = 0;
        int min = 0;

        if (seconds > Time.SECONDS_PER_HOUR) {
            hour = seconds / Time.SECONDS_PER_HOUR;
            seconds -= hour * Time.SECONDS_PER_HOUR;
        }

        if (seconds > Time.SECONDS_PER_MINUTE) {
            min = seconds / Time.SECONDS_PER_MINUTE;
            seconds -= min * Time.SECONDS_PER_MINUTE;
        }

        //Debug.log("- hour: " + hour);
        //Debug.log("- min: " + min);

        String hourString = hour == 1 ? "hour" : "hours";
        String minString = min == 1 ? "minute" : "minutes";

        if (hour > 0) {
            return String.format("%d %s %d %s %s", hour, hourString, min, minString, status);
        } else if (min > 0) {
            return String.format("%d %s %s", min, minString, status);
        } else {
            return showEarlyLate ? "on time" : "less than one minute";
        }
    }

    public static Date parseDate(String format, String s) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);

        try {
            return sdf.parse(s);
        } catch (ParseException e) {
            Debug.error("parseDate() failed: " + e);
            return null;
        }
    }

    public static long parseDateAsLong(String format, String s) {
        return parseDate(format, s).getTime();
    }

    // returns the day of week for the givem epoch value
    // 1 for Sunday, ... 7 for Saturday
    public static int getDayOfWeek(long millis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        return cal.get(cal.DAY_OF_WEEK);
    }

    public static String formatDate(String format, long millis) {
        return formatDate(format, new Date(millis));
    }

    public static String formatDate(String format, Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(date);
    }

    public static long getNextDaySeconds(long timestamp) {
        return timestamp + SECONDS_PER_DAY;
    }
}