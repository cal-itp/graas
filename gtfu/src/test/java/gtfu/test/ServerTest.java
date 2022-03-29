package gtfu.test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.net.URL;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Objects;
import java.util.List;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.google.transit.realtime.GtfsRealtime.Position;

import gtfu.Debug;
import gtfu.GTFSRTUtil;
import gtfu.Util;
import gtfu.tools.DB;

public class ServerTest {
    private static final String DOMAIN = "https://lat-long-prototype.wl.r.appspot.com";
    private static final String POST_ALERT_ENDPOINT = "/post-alert";
    private static final String VEHICLE_POSITION_ENDPOINT = "/vehicle-positions.pb?agency=";
    private static final String SERVICE_ALERTS_ENDPOINT = "/service-alerts.pb?agency=";
    private static final String PRIVATE_KEY = System.getenv("PR_TEST_ID_ECDSA");

    // The purpose of this function is to submit a test service alert
    private static boolean postTestAlert(String agencyID) {
        Debug.log("postTestAlert()");

        String url = DOMAIN + POST_ALERT_ENDPOINT;
        Map<String, Object> attributes = new HashMap();
        attributes.put("agency_key",agencyID);
        attributes.put("effect","Unknown Effect");
        long now = Util.now() / 1000;
        long later = now + 1000;
        attributes.put("time_start",now);
        attributes.put("time_stop",later);
        attributes.put("route_id","1234");

        int serverResponse = GTFSRTUtil.postAlertFromPrivateKey(url, attributes, PRIVATE_KEY);
        if (serverResponse == 200){
            return true;
        } else{
            System.out.println("* Post failed. Service response: " + serverResponse);
            return false;
        }
    }

    // This function was built to check for an update in the last minute.
    // For now, it checks for the existance of a realtime position update feed
    // Once we prevent the server from creating multiple instances, un-comment the extra text
    // ...and rename to checkForRecentPosUpdate()
    private static boolean checkForPosUpdateFeed(String agency) throws Exception {
        Debug.log("checkForPosUpdateFeed()");

        URL url = new URL(DOMAIN + VEHICLE_POSITION_ENDPOINT + agency);
        String query = url.getQuery();
        // long mostRecentUpdate = 0;
        // long now =  System.currentTimeMillis() / 1000;
        // int successInterval = 60;

        // Since there can be multiple instances of the server, there's no guarantee that a posted update will be returned here.
        // Until this is resolved, successfully creating and parsing an InputStream is enough to mark success
        try (InputStream is = url.openStream()) {
            FeedMessage msg = FeedMessage.parseFrom(is);

            // Delete below return statement when updating later
            return true;

            // for (FeedEntity entity : msg.getEntityList()) {
            //     if (entity.getVehicle().hasTimestamp()) {
            //         long timestamp = entity.getVehicle().getTimestamp();
            //         if (timestamp > mostRecentUpdate){
            //             mostRecentUpdate = timestamp;
            //         }
            //     }
            // }
        }
        // Fail if the feed does not exist
        catch (Exception e){
            e.printStackTrace();
            return false;
        }

        // long timeSinceUpdate = now - mostRecentUpdate;
        // System.out.println("- Last position update was " + timeSinceUpdate + " seconds ago");

        // // If most recent update is recent enough, success.
        // if (timeSinceUpdate <= successInterval){
        //     return true;
        // } else return false;
    }

    // See notes about checkForPosUpdateFeed(). When updating that function, build out this one as well.
    private static boolean checkForServiceAlertFeed(String agency) throws Exception {
        Debug.log("checkForServiceAlertFeed()");

        URL url = new URL(DOMAIN + SERVICE_ALERTS_ENDPOINT + agency);
        String query = url.getQuery();

        // Since there can be multiple instances of the server, there's no guarantee that a posted alert will be returned here.
        // Until this is resolved, successfully creating an InputStream is enough to mark success
        try (InputStream is = url.openStream()) {
            FeedMessage msg = FeedMessage.parseFrom(is);
            return true;
        }
        // Fail if the feed does not exist
        catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }

    // Checks whether the provided agency has a position updated logged to the DB within the last 30 seconds.
    // Note that the update is posted by running `NODE_PATH=../node/node_modules node post-position-update.js` from server/tests
    private static boolean checkDBForPosUpdate(String agency) throws Exception {
        String[] propertyNames = {"agency-id"};
        int searchWindowSecs = 30;

        DB db = new DB();
        long nowSecs = Util.now() / 1000;
        long queryStartTime = nowSecs - searchWindowSecs;
        long queryEndTime = nowSecs;
        List<String> results = db.fetch(queryStartTime, queryEndTime, "position", propertyNames);

        for (int i=0; i < results.size(); i++){
            if(results.get(i).equals(agency)){
                return true;
            }
        }

        return false;
    }

    // Run all tests and report results. Fail if any tests fail.
    public static void runTests(String agencyID) throws Exception {
        boolean posUpdateFeedSuccess = false;
        boolean serAlertFeedSuccess = false;
        boolean postAlertSuccess = false;
        boolean updateInDBSuccess = false;

        posUpdateFeedSuccess = checkForPosUpdateFeed(agencyID);
        postAlertSuccess = postTestAlert(agencyID);
        serAlertFeedSuccess = checkForServiceAlertFeed(agencyID);
        updateInDBSuccess = checkDBForPosUpdate(agencyID);

        System.out.println("----- Test Results -----");
        System.out.println("- Position update feed success: " + posUpdateFeedSuccess);
        System.out.println("- Service alert feed success: " + serAlertFeedSuccess);
        System.out.println("- Post alert success: " + postAlertSuccess);
        System.out.println("- Check DB for positiion update success: " + updateInDBSuccess);

        if(posUpdateFeedSuccess && serAlertFeedSuccess && postAlertSuccess && updateInDBSuccess) System.exit(0);
        else System.exit(1);
    }

    private static void usage() {
        System.err.println("usage: ServerTest <agency-id>");
    }

    // Take agencyID and run tests
    public static void main(String[] arg) throws Exception {
        String agencyID = null;
        if (arg.length != 1) {
            usage();
        }
        agencyID = arg[0];

        if ( agencyID == null) {
            usage();
        }

        runTests(agencyID);
    }
}