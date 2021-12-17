package gtfu.tuff;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import gtfu.AgencyData;
import gtfu.Debug;
import gtfu.Util;

public class TripUpdatesFeedFinagler implements Runnable {
    public static TripUpdatesFeedFinagler instance;

    private BlockingQueue<Event> queue;
    private List<AgencyData> agencies;
    private String rootFolder;

    public TripUpdatesFeedFinagler(String rootFolder) {
        this.rootFolder = rootFolder;

        agencies = new ArrayList<AgencyData>();
        queue = new LinkedBlockingQueue<Event>();
        instance = this;

        readAgencies();
        ensureFolderStructure();

        Thread t = new Thread(this);
        t.start();

        Timer timer = new Timer();

        timer.scheduleAtFixedRate(
            new TimerTask() {
                public void run() {
                    Debug.log("+ post TRIGGER_VEHICLE_POSITION_UPDATES 0");
                    Event e = new Event(
                        this,
                        null,
                        Event.TRIGGER_VEHICLE_POSITION_UPDATES,
                        new Integer(0)
                    );

                    TripUpdatesFeedFinagler.instance.postEvent(e);
                }
            },
            1000,
            30000
        );
    }

    private void ensureFolderStructure() {
        File root = new File(rootFolder);

        if (!root.exists()) {
            Debug.error("root folder '" + rootFolder + "' does not exist, please create");
            System.exit(0);
        }

        for (AgencyData ad : agencies) {
            File f = new File(rootFolder + "/" + ad.agencyId);

            if (!f.exists()) {
                f.mkdir();
            }
        }
    }

    private void readAgencies() {
        String path = rootFolder + "/agencies.json";
        AgencyData[] list = (AgencyData[])Util.readJSONObjectFromFile(path, AgencyData[].class);

        for (AgencyData ad : list) {
            agencies.add(ad);
        }

        Debug.log("- agencies: " + agencies);
    }

    public void postEvent(Event e) {
        //Debug.log("TripUpdatesFeedFinagler.postEvent()");
        //Debug.log("- e: " + e);

        try {
            queue.put(e);
        } catch (InterruptedException ie) {
            Debug.error("could not add event to queue");
        }
    }

    private void handleTriggerVehiclePositionUpdates() {
        Debug.log("TripUpdatesFeedFinagler.handleTriggerVehiclePositionUpdates()");

        Thread t = new Thread(new Runnable() {
            public void run() {
                for (int i=0; i<agencies.size(); i++) {
                    AgencyData ad = agencies.get(i);
                    new VehiclePositionFeedDownloader(ad, rootFolder);

                    Util.sleep(50);
                }

                Debug.log("+ ---> vehicle positions feed download triggers complete");

                Event e = new Event(
                    this,
                    null,
                    Event.TRIGGER_CACHE_CHECKS
                );

                postEvent(e);
            }
        });

        t.start();
    }

    private void handleCacheChecks() {
        Debug.log("TripUpdatesFeedFinagler.handleGTFSCheck()");

        Thread t = new Thread(new Runnable() {
            public void run() {
                for (int i=0; i<agencies.size(); i++) {
                    AgencyData ad = agencies.get(i);
                    new GTFSChecker(ad, rootFolder);

                    Util.sleep(50);
                }

                Debug.log("+ ---> gtfs check trigger complete");
            }
        });

        t.start();
    }

    private void handleVehiclePositionUpdate(String agencyId) {
        Debug.log("handleVehiclePositionUpdate()");
        Debug.log("- agencyId: " + agencyId);

        Thread t = new Thread(new Runnable() {
            public void run() {
                new TripUpdatesMessage(agencyId, rootFolder);
            }
        });

        t.start();
    }

    public void run() {
        for (;;) {
            Event e = null;

            try {
                e = queue.take();
            } catch (InterruptedException ie) {
                Debug.error("could not dequeue event");
                continue; // ### this will lead to a busy loop if take() continues to throw InterruptedExceptions
            }

            Debug.log("- e: " + e);

            switch (e.getType()) {
                case Event.TRIGGER_VEHICLE_POSITION_UPDATES:
                    handleTriggerVehiclePositionUpdates();
                    break;

                case Event.TRIGGER_CACHE_CHECKS:
                    handleCacheChecks();
                    break;

                case Event.VEHICLE_POSITION_DOWNLOADED:
                    handleVehiclePositionUpdate(e.getArgAsString());
                    break;
            }
        }
    }

    public static void main(String[] arg) {
        if (arg.length < 1) {
            System.err.println("usage TripUpdatesFeedFinagler <path-to-tuff-folder>");
            System.exit(0);
        }

        new TripUpdatesFeedFinagler(arg[0]);
    }
}
