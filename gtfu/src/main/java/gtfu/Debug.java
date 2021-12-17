package gtfu;

public class Debug {
    public static final boolean DEBUG = true;

    public static void log(Object msg) {
        if (DEBUG) System.out.println(msg);
    }

    public static void error(Object msg) {
        System.out.println(msg);
    }

    public static void bail() {
        System.err.println("Debug.bail()");
        System.exit(0);
    }
}