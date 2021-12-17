package gtfu.tools;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Base64;
import gtfu.Debug;
import gtfu.HTTPUtil;
import gtfu.HTTPClient;

public class SendGrid {
    private String[] tos;
    private String subject;
    private List<byte[]> blobs;

    public SendGrid(String[] recipients, String emailSubject, List<byte[]> images) {
        this.tos = recipients;
        this.subject = emailSubject;
        this.blobs = images;
    }

    private String makeTos() {
        StringBuffer sb = new StringBuffer();

        for (String s : tos) {
            if (sb.length() > 0) {
                sb.append(',');
            }

            sb.append("{\"email\": \"");
            sb.append(s.trim());
            sb.append("\"}");
        }

        return sb.toString();
    }

    private String makeAttachments() {
        StringBuffer sb = new StringBuffer();

        for (int i=0; i< blobs.size(); i++) {
            byte[] buf = blobs.get(i);

            if (sb.length() > 0) {
                sb.append(',');
            }

            String b64 = Base64.getEncoder().encodeToString(buf);

            sb.append("{\"type\": \"image/png\", \"filename\": \"report-");
            sb.append(i);
            sb.append(".png\", \"content\": \"");
            sb.append(b64);
            sb.append("\"}");
        }
        return sb.toString();
    }

    public Integer setupThenSend() {
        String urlData = String.format("{\"personalizations\": [{\"to\": [%s]}],\"from\": {\"email\": \"calitp.gtfsrt@gmail.com\"},\"subject\": \"%s\",\"content\": [{\"type\": \"text/plain\", \"value\": \"Attached\"}],\"attachments\": [%s]}", makeTos(), subject, makeAttachments());
        return send(urlData);
    }

    private Integer send(String urlData) {
        int responseCode = 0;
        String apiKey = System.getenv("SENDGRID_API_KEY");
        String auth = String.format("Bearer %s", apiKey);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", auth);
        headers.put("Content-Type", "application/json; charset=UTF-8");
        headers.put("Accept", "application/json");
        responseCode = HTTPClient.post("https://api.sendgrid.com/v3/mail/send", urlData, headers);
        return responseCode;
    }
}

