package gtfu.tuff;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.Timer;
import java.util.TimerTask;

import gtfu.*;

public class GTFSChecker implements Runnable {
    private AgencyData ad;
    private String rootFolder;

    public GTFSChecker(AgencyData ad, String rootFolder) {
        this.ad = ad;
        this.rootFolder = rootFolder;

        Thread t = new Thread(this);
        t.start();
    }

    private GTFSChecker() {
    }

    private void serializeData(String path, long then) {
        Debug.log(String.format("+ updated cache for %s in %d ms", path, (Util.now() - then)));
        then = Util.now();

        ProgressObserver po = new ConsoleProgressObserver(40);
        Agency agency = new Agency(path);
        ShapeCollection shapeCollection = new ShapeCollection(path, po);
        Debug.log("- shapeCollection.getSize(): " + shapeCollection.getSize());

        /*for (Shape s : shapeCollection) {
            Debug.log("-- s.getSize(): " + s.getSize());
        }*/

        StopCollection stopCollection = new StopCollection(path);
        Debug.log("- stopCollection.getSize(): " + stopCollection.getSize());
        TripCollection tripCollection = new TripCollection(path, stopCollection, shapeCollection, po);
        Debug.log("- tripCollection.getSize(): " + tripCollection.getSize());
        TripScheduleCollection scheduleCollection = new TripScheduleCollection(path, tripCollection, stopCollection, po);
        Debug.log("- scheduleCollection.getSize(): " + scheduleCollection.getSize());
        RouteCollection routeCollection = new RouteCollection(path, tripCollection);
        Debug.log("- routeCollection.getSize(): " + routeCollection.getSize());

        for (Route route : routeCollection) {
            route.computeArea();
        }

        Debug.log(String.format("+ instantiated static data for %s in %d ms", path, (Util.now() - then)));
        then = Util.now();

        try (FileOutputStream fos = new FileOutputStream(path + "/static-data.ser");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            ObjectOutputStream oos = new ObjectOutputStream(bos)) {
        //try {
        //    ByteArrayOutputStream bos = new ByteArrayOutputStream();
        //    ObjectOutputStream oos = new ObjectOutputStream(bos);

            oos.writeObject(agency);
            //oos.writeObject(shapeCollection);
            shapeCollection.write(dos);
            //Debug.log(String.format("++ wrote shape data for %s in %d ms", path, (Util.now() - then)));
            //then = Util.now();
            oos.writeObject(stopCollection);
            //Debug.log(String.format("++ serialized stop data for %s in %d ms", path, (Util.now() - then)));
            //then = Util.now();
            oos.writeObject(tripCollection);
            //Debug.log(String.format("++ serialized trip data for %s in %d ms", path, (Util.now() - then)));
            //then = Util.now();
            oos.writeObject(scheduleCollection);
            //Debug.log(String.format("++ serialized schedule data for %s in %d ms", path, (Util.now() - then)));
            //then = Util.now();
            oos.writeObject(routeCollection);
            //Debug.log(String.format("++ serialized route data for %s in %d ms", path, (Util.now() - then)));
            //then = Util.now();

            //FileOutputStream fos = new FileOutputStream(path + "/static-data.ser");
            fos.write(bos.toByteArray());
            //fos.close();
            //Debug.log(String.format("++ wrote serialized data for %s in %d ms", path, (Util.now() - then)));
            //then = Util.now();
        } catch (IOException e) {
            Debug.error("could not serialize static data for '" + path + "': " + e);
        }

        Debug.log(String.format("+ serialized static data for %s in %d ms", path, (Util.now() - then)));
        then = Util.now();

        /*try (FileOutputStream fos = new FileOutputStream(path + "/shape-data.ser")) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            shapeCollection.write(out);
            Debug.log(String.format("+ serialized shape data for %s in %d ms", path, (Util.now() - then)));
            then = Util.now();
            fos.write(bos.toByteArray());
        } catch (IOException e) {
            Debug.error("could not custom-write static data for '" + path + "': " + e);
        }

        Debug.log(String.format("+ persisted shape data for %s in %d ms", path, (Util.now() - then)));
        then = Util.now();

        try (FileOutputStream fos = new FileOutputStream(path + "/stop-data.ser")) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            stopCollection.write(out);
            fos.write(bos.toByteArray());
        } catch (IOException e) {
            Debug.error("could not custom-write static data for '" + path + "': " + e);
        }

        Debug.log(String.format("+ serialized stop data for %s in %d ms", path, (Util.now() - then)));
        then = Util.now();

        try (FileOutputStream fos = new FileOutputStream(path + "/trip-data.ser")) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            tripCollection.write(out);
            fos.write(bos.toByteArray());
        } catch (IOException e) {
            Debug.error("could not custom-write static data for '" + path + "': " + e);
        }
        Debug.log(String.format("+ serialized trip data for %s in %d ms", path, (Util.now() - then)));
        then = Util.now();

        try (FileOutputStream fos = new FileOutputStream(path + "/schedule-data.ser")) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            scheduleCollection.write(out);
            fos.write(bos.toByteArray());
        } catch (IOException e) {
            Debug.error("could not custom-write static data for '" + path + "': " + e);
        }

        Debug.log(String.format("+ serialized schedule data for %s in %d ms", path, (Util.now() - then)));
        then = Util.now();

        try (FileOutputStream fos = new FileOutputStream(path + "/route-data.ser")) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            routeCollection.write(out);
            fos.write(bos.toByteArray());
        } catch (IOException e) {
            Debug.error("could not custom-write static data for '" + path + "': " + e);
        }

        Debug.log(String.format("+ serialized route data for %s in %d ms", path, (Util.now() - then)));*/
    }

    public void run() {
        String path = rootFolder +  "/" + ad.agencyId;
        Debug.log("- path: " + path);

        long then = Util.now();
        boolean updated = Util.updateCacheIfNeeded(rootFolder, ad.agencyId, ad.staticGtfsUrl, null);

        if (updated) {
            serializeData(path, then);
        }
    }

    public static void main(String[] arg) {
        GTFSChecker checker = new GTFSChecker();
        checker.serializeData(arg[0] + "/" + arg[1], Util.now());
    }
}