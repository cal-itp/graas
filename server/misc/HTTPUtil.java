import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.List;

class HTTPUtil {
    private static void log(String s) {
        System.out.println(s);
    }

    public static String post(String url, String body) throws IOException {
        return post(url, body, null);
    }

    public static String post(String url, String body, Map<String, String> requestMap) {
        HttpURLConnection con = null;
        StringBuilder content = new StringBuilder("");

        try {
            var myurl = new URL(url);
            con = (HttpURLConnection) myurl.openConnection();

            con.setDoOutput(true);
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");

            if (requestMap == null) {
                requestMap = Collections.EMPTY_MAP;
            }

            for (String key : requestMap.keySet()) {
                con.setRequestProperty(key, requestMap.get(key));
            }

            try (var wr = new DataOutputStream(con.getOutputStream())) {
                wr.write(body.getBytes());
            }

            System.out.println("response code: " + con.getResponseCode());

            try (var br = new BufferedReader(
                new InputStreamReader(con.getInputStream()))) {

                String line;
                content = new StringBuilder();

                while ((line = br.readLine()) != null) {
                    content.append(line);
                    content.append(System.lineSeparator());
                }
            }
        } finally {
            con.disconnect();
            return content.toString();
        }
    }

    public static String get(String url, Map<String, String> requestMap, String responseHeaderName) throws IOException {
        HttpURLConnection con = null;
        StringBuilder content = new StringBuilder("");

        try {
            var myurl = new URL(url);
            con = (HttpURLConnection) myurl.openConnection();

            con.setRequestMethod("GET");
            con.setRequestProperty("Content-Type", "text/html");

            if (requestMap == null) {
                requestMap = Collections.EMPTY_MAP;
            }

            for (String key : requestMap.keySet()) {
                con.setRequestProperty(key, requestMap.get(key));
            }

            System.out.println("- response code: " + con.getResponseCode());

            try (var br = new BufferedReader(
                new InputStreamReader(con.getInputStream()))) {

                String line;
                content = new StringBuilder();

                while ((line = br.readLine()) != null) {
                    content.append(line);
                    content.append(System.lineSeparator());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            String s = con.getHeaderField(responseHeaderName);
            con.disconnect();
            return s;
        }
    }
}
