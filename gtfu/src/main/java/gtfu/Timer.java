package gtfu;

public class Timer {
    private String name;
    private long lastMillis;

    public Timer(String name) {
        this.name = name;
        lastMillis = Util.now();
    }

    public int lap() {
        int d = (int)(Util.now() - lastMillis);
        lastMillis = Util.now();
        return d;
    }

    public void dumpLap() {
        Debug.log(String.format("+ lap: %d ms (%s)", lap(), name));
    }
}