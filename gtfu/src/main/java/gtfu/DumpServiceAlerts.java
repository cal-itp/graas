package gtfu;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.net.URL;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Objects;
import java.util.List;

import com.google.transit.realtime.GtfsRealtime.*;

public class DumpServiceAlerts {
    private static final String CSV_ARG = "-csvoutput";
    private static final String URL_ARG = "-url";

    private static void dumpFeed(FeedMessage msg, boolean csv) throws Exception {
        for (FeedEntity entity : msg.getEntityList()) {
            if (entity.hasAlert()) {
                ServiceAlert alert = new ServiceAlert(entity.getAlert());
                System.out.println(csv ? alert.toCSVString() : alert);
            }
        }
    }

    public static void main(String[] arg) throws Exception {
        boolean csv = false;
        URL url = null;

        for (int i=0; i<arg.length; i++) {
            if (arg[i].equals(CSV_ARG)) {
                csv = true;
            }

            if (arg[i].equals(URL_ARG) && i + 1 < arg.length) {
                url = new URL(arg[++i]);
            }
        }

        if (url == null) {
            System.err.println("usage: DumpServiceAlerts -url <service-alert-url> [-csvoutput]");
            System.exit(-1);
        }

        String host = url.getHost();

        if (host.equals("127.0.0.1") || host.equals("localhost")) {
            // if we're connecting to localhost, chances
            // are the cert will be self-signed, which won't
            // go over well with any checks

            Util.disableSSLChecking();
        }

        try (InputStream is = url.openStream()) {
            dumpFeed(FeedMessage.parseFrom(is), csv);
        }
    }
}

class ServiceAlert {
    private Alert src;
    private static Map<String, String> causeMap;
    private static Map<String, String> effectMap;
    private static SimpleDateFormat sdf;

    static {
        causeMap = new HashMap<String, String>();

        causeMap.put("UNKNOWN_CAUSE", "unknown");
        causeMap.put("OTHER_CAUSE", "other");
        causeMap.put("TECHNICAL_PROBLEM", "technical problem");
        causeMap.put("STRIKE", "strike");
        causeMap.put("DEMONSTRATION", "demonstration");
        causeMap.put("ACCIDENT", "accident");
        causeMap.put("HOLIDAY", "holiday");
        causeMap.put("WEATHER", "weather");
        causeMap.put("MAINTENANCE", "maintenance");
        causeMap.put("CONSTRUCTION", "construction");
        causeMap.put("POLICE_ACTIVITY", "police activity");
        causeMap.put("MEDICAL_EMERGENCY", "medical emergency");

        effectMap = new HashMap<String, String>();

        effectMap.put("NO_SERVICE", "no service");
        effectMap.put("REDUCED_SERVICE", "reduced service");
        effectMap.put("SIGNIFICANT_DELAYS", "significant delays");
        effectMap.put("DETOUR", "detour");
        effectMap.put("ADDITIONAL_SERVICE", "additional service");
        effectMap.put("MODIFIED_SERVICE", "modified service");
        effectMap.put("OTHER_EFFECT", "other");
        effectMap.put("UNKNOWN_EFFECT", "unknown");
        effectMap.put("STOP_MOVED", "stop moved");

        sdf = new SimpleDateFormat("EEE, MMM dd @ hh:mm a");
    }

    ServiceAlert(Alert src) {
        this.src = src;
    }

    // Mon, April 5 12:00 pm
    private String timeRangeToString(TimeRange range) {
        String from = "beginning of time";
        String to = "end of time";

        if (range.hasStart()) {
            from = sdf.format(new Date(range.getStart() * 1000));
        }

        if (range.hasEnd()) {
            to = sdf.format(new Date(range.getEnd() * 1000));
        }

        return from + " - " + to;
    }

    public String getCSVHeader() {
        return "agency_id,route_id,trip_id,stop_id,header,description,url,cause,effect";
    }

    public String toCSVString() {
        StringBuilder sb = new StringBuilder();

        String agency_id = "";
        String route_id = "";
        String trip_id = "";
        String stop_id = "";
        String header = "";
        String description = "";
        String url = "";
        String cause = "";
        String effect = "";

        for (EntitySelector sel : src.getInformedEntityList()) {
            if (sel.hasAgencyId()) agency_id = sel.getAgencyId();
            if (sel.hasRouteId()) route_id = sel.getRouteId();

            if (sel.hasTrip()) {
                TripDescriptor trip = sel.getTrip();
                if (trip.hasTripId()) trip_id = trip.getTripId();
            }

            if (sel.hasStopId()) stop_id = sel.getStopId();
        }

        if (src.hasHeaderText()) {
            List<TranslatedString.Translation> list = src.getHeaderText().getTranslationList();
            if (list.size() > 0) header = list.get(0).getText();
        }

        if (src.hasDescriptionText()) {
            List<TranslatedString.Translation> list = src.getDescriptionText().getTranslationList();
            if (list.size() > 0) description = list.get(0).getText();
        }

        if (src.hasUrl()) {
            List<TranslatedString.Translation> list = src.getUrl().getTranslationList();
            if (list.size() > 0) url = list.get(0).getText();
        }

        if (src.hasCause()) cause = causeMap.get(src.getCause().toString());
        if (src.hasEffect()) effect = effectMap.get(src.getEffect().toString());

        return String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s",
            agency_id, route_id, trip_id, stop_id,
            header, description, url, cause, effect
        );
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("alert:\n");
        List<TimeRange> rangeList = src.getActivePeriodList();

        if (rangeList.size() > 0) {
            sb.append("  active periods:\n");
        }

        for (TimeRange range : rangeList) {
            sb.append("    ");
            sb.append(timeRangeToString(range));
            sb.append('\n');
        }

        sb.append("  informed entities:\n");

        for (EntitySelector sel : src.getInformedEntityList()) {
            if (sel.hasAgencyId()) {
                sb.append("    agency ID: ");
                sb.append(sel.getAgencyId());
                sb.append('\n');
            }

            if (sel.hasRouteId()) {
                sb.append("    route ID: ");
                sb.append(sel.getRouteId());
                sb.append('\n');
            }

            if (sel.hasTrip()) {
                TripDescriptor trip = sel.getTrip();

                if (trip.hasTripId()) {
                    sb.append("    trip ID: ");
                    sb.append(trip.getTripId());
                    sb.append('\n');
                }
            }

            if (sel.hasStopId()) {
                sb.append("    stop ID: ");
                sb.append(sel.getStopId());
                sb.append('\n');
            }
        }

        if (src.hasHeaderText()) {
            List<TranslatedString.Translation> list = src.getHeaderText().getTranslationList();

            if (list.size() > 0) {
                sb.append("  header: ");
                sb.append(list.get(0).getText());
                sb.append('\n');
            }
        }

        if (src.hasDescriptionText()) {
            List<TranslatedString.Translation> list = src.getDescriptionText().getTranslationList();

            if (list.size() > 0) {
                sb.append("  description: ");
                sb.append(list.get(0).getText());
                sb.append('\n');
            }
        }

        if (src.hasUrl()) {
            List<TranslatedString.Translation> list = src.getUrl().getTranslationList();

            if (list.size() > 0) {
                sb.append("  url: ");
                sb.append(list.get(0).getText());
                sb.append('\n');
            }
        }

        if (src.hasCause()) {
            sb.append("  cause: ");
            sb.append(causeMap.get(src.getCause().toString()));
            sb.append('\n');
        }

        if (src.hasEffect()) {
            sb.append("  effect: ");
            sb.append(effectMap.get(src.getEffect().toString()));
            sb.append('\n');
        }

        return sb.toString();
    }
}

