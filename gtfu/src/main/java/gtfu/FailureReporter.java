package gtfu;

import java.util.ArrayList;
import java.util.List;

public abstract class FailureReporter {
    protected List<String> lines;

    public FailureReporter() {
        lines = new ArrayList();
    }

    public void addLine(String s) {
        lines.add(s);
    }

    public int getFailCount() {
        return lines.size();
    }

    public abstract void send();
}
