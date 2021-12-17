package gtfu.tuff;

import gtfu.Debug;
import gtfu.Util;

public class Event {
    public static final int TRIGGER_VEHICLE_POSITION_UPDATES = 0;
    public static final int VEHICLE_POSITION_DOWNLOADED      = 1;
    public static final int OPERATOR_ADD                     = 2;
    public static final int OPERATOR_UPDATE                  = 3;
    public static final int OPERATOR_REMOVE                  = 4;
    public static final int TRIGGER_CACHE_CHECKS             = 5;

    private static final String[] TYPE_STRINGS = {
        "TRIGGER_VEHICLE_POSITION_UPDATES", "VEHICLE_POSITION_DOWNLOADED", "OPERATOR_ADD",
        "OPERATOR_UPDATE", "OPERATOR_REMOVE", "TRIGGER_GTFS_CHECK"
    };

    private long timestamp;
    private Object source;
    private Object arg;
    private String operator;
    private int type;

    public static final String getTypeAsString(int type) {
        if (type < 0 || type > TRIGGER_CACHE_CHECKS) return "???";
        return TYPE_STRINGS[type];
    }

    public Event(Object source, String operator, int type) {
        this(source, operator, type, null);
    }

    public Event(Object source, String operator, int type, Object arg) {
        timestamp = Util.now();

        this.source = source;
        this.operator = operator;
        this.type = type;
        this.arg = arg;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Object getSource() {
        return source;
    }

    public String getOperator() {
        return operator;
    }

    public int getType() {
        return type;
    }

    public Object getArg() {
        return arg;
    }

    public int getArgAsInt() {
        return ((Integer)arg).intValue();
    }

    public String getArgAsString() {
        return (String)arg;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("event: type = ");
        sb.append(getTypeAsString(type));
        sb.append(", arg = ");
        sb.append(arg);
        sb.append(", age = ");
        sb.append((int)(Util.now() - timestamp));
        sb.append(" seconds");

        return sb.toString();
    }
}