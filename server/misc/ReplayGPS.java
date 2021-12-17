import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class ReplayGPS {
    private static final String SERVER_URL = "http://localhost:8080/new-pos";

    private static void httpPost(String cmd) throws IOException {
        HttpURLConnection con = null;

        try {
            var myurl = new URL(SERVER_URL);
            con = (HttpURLConnection) myurl.openConnection();

            con.setDoOutput(true);
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");

            try (var wr = new DataOutputStream(con.getOutputStream())) {
                wr.write(cmd.getBytes());
            }

            StringBuilder content;

            try (var br = new BufferedReader(
                    new InputStreamReader(con.getInputStream()))) {

                String line;
                content = new StringBuilder();

                while ((line = br.readLine()) != null) {
                    content.append(line);
                    content.append(System.lineSeparator());
                }
            }

            System.out.println("> " + content);
        } finally {

            con.disconnect();
        }
    }

    public static void main(String[] s) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(s[0])));
        long lastTimestamp = -1;

        for (;;) {
            String line = in.readLine();
            if (line == null) break;

            String[] arg = line.split(",");

            long timestamp = Long.parseLong(arg[0]);

            /*
            data = `{
            "uuid": "${uuid}",
            "agent": "${agent}",
            "timestamp": "${timestamp}",
            "lat": "${lat}",
            "long": "${long}",
            "speed": "${speed}",
            "heading": "${heading}",
            "trip-id": "${tripID}",
            "agency-id": "${agencyID}",
            "vehicle-id": "${vehicleID}",
            "pos-timestamp": "${posTimestamp}",
            "accuracy": "${accuracy}"}`
            */
            String cmd  = String.format("{\"uuid\": \"replay\", \"agent\": \"replay\", \"timestamp\": \"%d\", \"lat\": \"%f\", \"long\": \"%f\", \"speed\": \"1\", \"heading\": \"0\", \"trip-id\": \"%s\", \"agency-id\": \"santa-barbara-clean-air-express\", \"vehicle-id\": \"replay\", \"pos-timestamp\": \"%d\", \"accuracy\": \"30\"}", timestamp, Float.parseFloat(arg[1]), Float.parseFloat(arg[2]), arg[3], timestamp);

            System.out.println("- cmd: " + cmd);

            if (lastTimestamp > 0) {
                int delta = (int)(timestamp - lastTimestamp);
                System.out.println("- delta: ");

                try {Thread.sleep(delta * 1000);}
                catch (InterruptedException e) {System.out.println("insomnia");}
            }

            httpPost(cmd);

            lastTimestamp = timestamp;
        }
    }
}
