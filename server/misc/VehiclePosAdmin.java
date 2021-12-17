import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

public class VehiclePosAdmin {
    // commands: delete <vehicle-id>, delete-all

    private static void log(Object o) {
        System.out.println(o.toString());
    }

    public static void main(String[] arg) throws IOException {
        String email = System.getenv("LATLONGUID");
        String password = System.getenv("LATLONGPW");

        log("- email: " + email);
        log("- password: " + password);

        if (email == null || password == null) {
            System.err.println("env variables $LATLONGUID and $LATLONGPW must be set");
            System.exit(0);
        }

        String url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=AIzaSyCV8Gmn5kLPp3CIuP5FeZs3iFhF2TMz8Lg";
        String command = String.format("{\"email\":\"%s\",\"password\":\"%s\",\"returnSecureToken\":true}", email, password);

        String reply = HTTPUtil.post(url, command);
        //log("- reply: " + reply);

        JSONObject obj = (JSONObject)JSONValue.parse(reply);
        String token = (String)obj.get("idToken");

        log("- token: " + token);

        Map<String, String> map = new HashMap<String, String>();
        map.put("Cookie", "token=" + token);

        url = "http://localhost:8080";
        reply = HTTPUtil.get(url, map, "Set-Cookie");
        //log("- reply: " + reply);

        String[] tokens = reply.split("; ");
        String session = null;

        for (String t : tokens) {
            if (t.startsWith("session=")) {
                session = t;
            }
        }

        log("- session: " + session);

        map = new HashMap<String, String>();
        map.put("Cookie", session);

        long now = System.currentTimeMillis();

        url = "http://localhost:8080/new-pos";
        command = "{\"uuid\": \"foo\", \"agent\": \"bar\", \"timestamp\": \"" + now + "\", \"lat\": \"0\", \"long\": \"0\", \"speed\": \"0\", \"heading\": \"0\", \"trip-id\": \"0\", \"agency-id\": \"foo\", \"vehicle-id\": \"0\", \"pos-timestamp\": \"" + now + "\", \"accuracy\": \"1\"}";

        //log("- command: " + command);

        reply = HTTPUtil.post(url, command, map);
        log("- reply: " + reply);
    }
}