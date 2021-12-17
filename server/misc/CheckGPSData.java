import java.io.*;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

public class CheckGPSData {
    private final static int EARTH_RADIUS_IN_FEET = 20902000;

    private static float getDistance(float lat1, float lon1, float lat2, float lon2) {
        double x1 = EARTH_RADIUS_IN_FEET * Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat1));
        double y1 = EARTH_RADIUS_IN_FEET * Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lon1));
        double z1 = EARTH_RADIUS_IN_FEET * Math.sin(Math.toRadians(lon1));

        double x2 = EARTH_RADIUS_IN_FEET * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(lat2));
        double y2 = EARTH_RADIUS_IN_FEET * Math.cos(Math.toRadians(lat2)) * Math.sin(Math.toRadians(lon2));
        double z2 = EARTH_RADIUS_IN_FEET * Math.sin(Math.toRadians(lon2));

        double xd = x1 - x2;
        double yd = y1 - y2;
        double zd = z1 - z2;

        return (float)Math.sqrt(xd * xd + yd * yd + zd * zd);
    }

    public static void main(String[] arg) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(arg[0])));
        List<Data> list = new ArrayList<Data>();

        for (int i=0;;i++) {
            String line = in.readLine();
            if (line == null) break;

            Data data = new Data(line);

            if (list.size() > 0) {
                Data prev = list.get(list.size() - 1);
                int deltaT = (int)(data.ts - prev.ts);
                int deltaP = (int)getDistance(data.lat, data.lon, prev.lat, prev.lon);

                if (deltaT > 5) {
                    System.out.println(String.format("%d: %d seconds", i, deltaT));
                }

                if (deltaP > 500) {
                    System.out.println(String.format("%d: %d feet", i, deltaP));
                }
            }

            list.add(data);
        }
    }
}

class Data {
    long ts;
    float lat;
    float lon;

    public Data(String s) {
        String[] arg = s.split(",");

        ts = Long.parseLong(arg[0]);
        lat = Float.parseFloat(arg[1]);
        lon = Float.parseFloat(arg[2]);
    }
}