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
    private static URL url;

    private static void dumpFeed(FeedMessage msg) throws Exception {
        for (FeedEntity entity : msg.getEntityList()) {
            if (entity.hasAlert()) {
                System.out.println(new ServiceAlert(entity.getAlert()));
            }
        }
    }

    public static void main(String[] arg) throws Exception {
        if (arg.length == 0) {
            System.err.println("usage: DumpServiceAlerts <service-alert-url>");
            System.exit(-1);
        }

        url = new URL(arg[0]);

        try (InputStream is = url.openStream()) {
            dumpFeed(FeedMessage.parseFrom(is));
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

