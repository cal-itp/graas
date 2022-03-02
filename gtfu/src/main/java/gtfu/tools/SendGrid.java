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
    private String body;
    private Map<String, byte[]> imageMap;

    public SendGrid(String[] recipients, String emailSubject, String body, Map<String, byte[]> imageMap) {
        this.tos = recipients;
        this.subject = emailSubject;
        this.body = escape(body);
        this.imageMap = imageMap;
    }

    private String escape(String s) {
        StringBuilder sb = new StringBuilder();

        for (char c : s.toCharArray()) {
            if (c == '\n') {
                sb.append("\\n");
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
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

        for (String key : imageMap.keySet()) {
            byte[] buf = imageMap.get(key);

            if (sb.length() > 0) {
                sb.append(',');
            }

            String b64 = Base64.getEncoder().encodeToString(buf);

            sb.append("{\"type\": \"image/png\", \"filename\": \"");
            sb.append(key);
            sb.append(".png\", \"content\": \"");
            sb.append(b64);
            sb.append("\"}");
        }
        return sb.toString();
    }

    public int send() {
        String attach = "";
        if(imageMap != null){
            if (imageMap.size() > 0) {
                attach = String.format(",\"attachments\": [%s]", makeAttachments());
            } else{
                body = "There are no images attached";
            }
        }

        String from = "calitp.gtfsrt@gmail.com";
        String f = System.getenv("GRAAS_REPORT_FROM");

        if (f != null) {
            from = f;
        }
        // TODO update content type to HTML, to enable links in emails
        String urlData = String.format("{\"personalizations\": [{\"to\": [%s]}],\"from\": {\"email\": \"%s\"},\"subject\": \"%s\",\"content\": [{\"type\": \"text/plain\", \"value\": \"%s\"}]%s}", makeTos(), from, subject, body, attach);

        //Debug.log("- urlData: " + urlData);

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

