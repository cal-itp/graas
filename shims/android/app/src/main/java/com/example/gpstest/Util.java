package com.example.gpstest;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class Util {
    public static Map<String, String> EMPTY_MAP = Collections.EMPTY_MAP;

    public static String bytesToHexString(byte[] buf) {
        StringBuilder sb = new StringBuilder();

        for (byte b : buf) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }

    public static void post(final String url, final String body, final Map<String, String> headers) {
        Thread t = new Thread(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            public void run() {
                HttpURLConnection con = null;
                StringBuilder content = new StringBuilder("");

                try {
                    URL myurl = new URL(url);
                    con = (HttpsURLConnection) myurl.openConnection();

                    //con.setDoOutput(true);
                    con.setRequestMethod("POST");

                    for (String key : headers.keySet()) {
                        con.setRequestProperty(key, headers.get(key));
                    }

                    try (DataOutputStream wr = new DataOutputStream(con.getOutputStream())) {
                        wr.write(body.getBytes());
                    }

                    Log.d("gpstest", "response code: " + con.getResponseCode());

                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(con.getInputStream()))) {

                        String line;
                        content = new StringBuilder();

                        while ((line = br.readLine()) != null) {
                            content.append(line);
                            content.append(System.lineSeparator());
                        }
                    }

                    Log.d("gpstest", "response body: " + content);
                } catch (Exception e) {
                    e.printStackTrace();
                    Debug.error("post failed: " + e);
                } finally {
                    con.disconnect();
                }
            }
        });

        t.start();
    }
}