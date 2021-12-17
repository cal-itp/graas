package gtfu;

public class ProcessData {
    public int exitCode;
    public String output;

    public ProcessData() {
        this(-1, null);
    }

    public ProcessData(int exitCode, String output) {
        this.exitCode = exitCode;
        this.output = output;
    }

    public String toString() {
        return "{exitCode: " + exitCode + ", output: " + output + "}";
    }
}