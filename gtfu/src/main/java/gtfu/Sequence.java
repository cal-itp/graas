package gtfu;

import java.util.List;

public class Sequence<T> {
    List<T> list;
    int start;
    int stop;

    public Sequence() {
    }

    public Sequence(List<T> list, int start, int stop) {
        this.list = list;
        this.start = start;
        this.stop = stop;
    }
}