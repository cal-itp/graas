package gtfu;

public class Fail extends RuntimeException {
    public Fail(Throwable t) {
        super(t);
    }

    public Fail(String msg) {
        super(msg);
    }

    public Fail(String msg, Throwable t) {
        super(msg, t);
    }
}