package gtfu;

public class Calendar {
    public static final int MONDAY    = java.util.Calendar.MONDAY;
    public static final int TUESDAY   = java.util.Calendar.TUESDAY;
    public static final int WEDNESDAY = java.util.Calendar.WEDNESDAY;
    public static final int THURSDAY  = java.util.Calendar.THURSDAY;
    public static final int FRIDAY    = java.util.Calendar.FRIDAY;
    public static final int SATURDAY  = java.util.Calendar.SATURDAY;
    public static final int SUNDAY    = java.util.Calendar.SUNDAY;

    private static final String LETTERS = "MTWTFSS";

    private String serviceID;
    private int days[];

    public Calendar(String serviceID, int monday, int tuesday, int wednesday, int thursday, int friday, int saturday, int sunday) {
        this.serviceID = serviceID;
        days = new int[8];

        days[MONDAY] = monday;
        days[TUESDAY] = tuesday;
        days[WEDNESDAY] = wednesday;
        days[THURSDAY] = thursday;
        days[FRIDAY] = friday;
        days[SATURDAY] = saturday;
        days[SUNDAY] = sunday;
    }

    boolean isActiveForDay(int dow) {
        return days[dow] != 0;
    }

    public String toShortList() {
        StringBuffer sb = new StringBuffer();

        /*
        0 -> 2
        1 -> 3
        2 -> 4
        3 -> 5
        4 -> 6
        5 -> 7
        6 -> 1
        */

        for (int i=0; i<7; i++) {
            int n = ((i + 1) % 7 + 1);
            sb.append(days[n] == 0 ? '-' : LETTERS.charAt(i));
        }

        return sb.toString();
    }
}