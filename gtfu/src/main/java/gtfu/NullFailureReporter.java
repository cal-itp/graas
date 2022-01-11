package gtfu;

public class NullFailureReporter extends FailureReporter {
    public void send() {
        Debug.log("NullFailureReporter.send()");
        Debug.log("+ doing exactly squat");
    }
}
