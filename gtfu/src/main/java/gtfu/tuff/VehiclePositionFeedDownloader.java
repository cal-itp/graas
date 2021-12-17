package gtfu.tuff;

import java.io.IOException;
import java.io.FileOutputStream;
import java.util.Timer;
import java.util.TimerTask;

import gtfu.AgencyData;
import gtfu.Debug;
import gtfu.Util;

public class VehiclePositionFeedDownloader implements Runnable {
    private static final int DOWNLOAD_TIMEOUT = 10000;

    private Timer timer;
    private AgencyData ad;
    private String rootFolder;

    public VehiclePositionFeedDownloader(AgencyData ad, String rootFolder) {
        this.ad = ad;
        this.rootFolder = rootFolder;

        timer = new Timer();

        Thread t = new Thread(this);
        t.start();

        timer.schedule(
            new TimerTask() {
                public void run() {
                    TripUpdatesFeedFinagler.instance.postEvent(makeEvent());
                }
            },
            DOWNLOAD_TIMEOUT
        );
    }

    private Event makeEvent() {
        return new Event(
            this,
            null,
            Event.VEHICLE_POSITION_DOWNLOADED,
            ad.agencyId
        );
    }

    public void run() {
        String path = rootFolder +  "/" + ad.agencyId + "/vp.pb";
        Debug.log("- path: " + path);

        try (FileOutputStream fos = new FileOutputStream(path)) {
            Util.downloadURLContent(ad.vehiclePositionUrl, fos, null);
            timer.cancel();
            TripUpdatesFeedFinagler.instance.postEvent(makeEvent());
        } catch (IOException e) {
            Debug.error("could not download vehicle position feed for '" + ad.agencyId + "'");
        }
    }
}