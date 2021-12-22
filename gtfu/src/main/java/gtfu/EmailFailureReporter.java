package gtfu;

import gtfu.tools.SendGrid;

public class EmailFailureReporter extends FailureReporter {
    private String[]recipients;
    private String subject;

    public EmailFailureReporter(String[] recipients, String subject) {
        this.recipients = recipients;
        this.subject = subject;
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

        SendGrid grid = new SendGrid(recipients, subject, sb.toString(), null);
        grid.send();
    }
}
