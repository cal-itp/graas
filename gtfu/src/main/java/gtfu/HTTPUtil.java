package gtfu;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.List;

public class HTTPUtil {
    @SuppressWarnings("unchecked")
    private static Map<String, String> EMPTY_MAP = Collections.EMPTY_MAP;

    private static void log(String s) {
        System.out.println(s);
    }

    public static Integer post(String url, String body) {
        return post(url, body, null);
    }

    public static Integer post(String url, String body, Map<String, String> requestMap) {
        //Debug.log("post()");
        //Debug.log("url: " + url);
        //Debug.log("body: " + body);
        HttpURLConnection con = null;

        StringBuilder content = new StringBuilder("");
        int responseCode = 0;
        try {
            URL myurl = new URL(url);
            con = (HttpURLConnection) myurl.openConnection();
            con.setDoOutput(true);
            con.setRequestMethod("POST");

            if (requestMap == null) {
                requestMap = EMPTY_MAP;
            }

            for (String key : requestMap.keySet()) {
                con.setRequestProperty(key, requestMap.get(key));
            }

            try (OutputStream wr = con.getOutputStream()) {
                byte[] bodyBytes = body.getBytes("utf-8");
                wr.write(bodyBytes, 0, bodyBytes.length);
                wr.flush();
                wr.close();
                responseCode = con.getResponseCode();
                System.out.println("responseCode: " + responseCode);
            }

            // Previously this line had "getInputStream" which returned null - getErrorStream
            // at least returns some text, though it's redundant with "resposne message"
            try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"))) {
                String line;
                content = new StringBuilder();
                while ((line = br.readLine()) != null) {
                    content.append(line.trim());
                    // content.append(System.lineSeparator());
                }
                System.out.println("response content: "+ content.toString());
                br.close();
                }
        } catch (Exception e) {
          e.printStackTrace();
        } finally{
            con.disconnect();
            return responseCode;
        }
    }

    public static String get(String url, Map<String, String> requestMap, String responseHeaderName) throws IOException {
        HttpURLConnection con = null;
        StringBuilder content = new StringBuilder("");

        try {
            URL myurl = new URL(url);
            con = (HttpURLConnection) myurl.openConnection();

            con.setRequestMethod("GET");
            con.setRequestProperty("Content-Type", "text/html");

            if (requestMap == null) {
                requestMap = EMPTY_MAP;
            }

            for (String key : requestMap.keySet()) {
                con.setRequestProperty(key, requestMap.get(key));
            }

            System.out.println("- response code: " + con.getResponseCode());

            try (BufferedReader br = new BufferedReader(
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
