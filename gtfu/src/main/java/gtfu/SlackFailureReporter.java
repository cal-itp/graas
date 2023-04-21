package gtfu;

import gtfu.tools.Slack;

public class SlackFailureReporter extends FailureReporter {

    public SlackFailureReporter() {
    }

    public void send() {
        StringBuilder sb = new StringBuilder();

        for (String l : lines) {
            sb.append(l);
            sb.append('\n');
        }

        if (sb.length() == 0) {
            sb.append("0 lines\n");
        }
        
        Slack s = new Slack();
        s.send(sb.toString());
    }
}
