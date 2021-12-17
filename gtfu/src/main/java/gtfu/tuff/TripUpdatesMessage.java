package gtfu.tuff;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.Map;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.Position;

import gtfu.*;


public class TripUpdatesMessage {
    Agency agency;
    ShapeCollection shapeCollection;
    StopCollection stopCollection;
    TripCollection tripCollection;
    TripScheduleCollection scheduleCollection;
    RouteCollection routeCollection;

    public TripUpdatesMessage(String agencyId, String rootFolder) {
        String path = rootFolder + "/" + agencyId;

        readSerialized(path);
        generatePredictions(path);
    }

    private void readSerialized(String path) {
        String filename = path + "/static-data.ser";
        Debug.log("- filename: " + filename);
        long then = Util.now();

        try (FileInputStream fis = new FileInputStream(filename)) {
            File f = new File(filename);
            byte[] buf = new byte[(int)f.length()];
            fis.read(buf);

            //Debug.log("+ read serialization file:  " + (Util.now() - then) + " millis");
            //then = Util.now();

            ByteArrayInputStream bis = new ByteArrayInputStream(buf);

            //Debug.log("+ byte stream:  " + (Util.now() - then) + " millis");
            //then = Util.now();

            DataInputStream dis = new DataInputStream(bis);

            //Debug.log("+ data stream:  " + (Util.now() - then) + " millis");
            //then = Util.now();

            ObjectInputStream ois = new ObjectInputStream(bis);

            //Debug.log("+ object stream:  " + (Util.now() - then) + " millis");
            //then = Util.now();

            agency = (Agency)ois.readObject();
            //Debug.log("+ agency:  " + (Util.now() - then) + " millis");
            //then = Util.now();
            shapeCollection = ShapeCollection.fromStream(dis);
            //Debug.log("+ shapeCollection:  " + (Util.now() - then) + " millis");
            //Debug.log("- shapeCollection.getSize(): " + shapeCollection.getSize());*/
            //then = Util.now();
            stopCollection = (StopCollection)ois.readObject();
            //Debug.log("+ stopCollection:  " + (Util.now() - then) + " millis");
            //Debug.log("- stopCollection.getSize(): " + stopCollection.getSize());
            //then = Util.now();
            tripCollection = (TripCollection)ois.readObject();

            for (Trip t : tripCollection) {
                t.setShapeFromID(shapeCollection);
            }

            //Debug.log("+ tripCollection:  " + (Util.now() - then) + " millis");
            //Debug.log("- tripCollection.getSize(): " + tripCollection.getSize());
            //then = Util.now();
            scheduleCollection = (TripScheduleCollection)ois.readObject();
            //Debug.log("+ scheduleCollection:  " + (Util.now() - then) + " millis");
            //Debug.log("- scheduleCollection.getSize(): " + scheduleCollection.getSize());
            //then = Util.now();
            routeCollection = (RouteCollection)ois.readObject();
            //Debug.log("+ routeCollection:  " + (Util.now() - then) + " millis");
            //Debug.log("- routeCollection.getSize(): " + routeCollection.getSize());
            //then = Util.now();
        } catch (Exception e) {
            Debug.error("could not deserialize static data for '" + path + "': " + e);
        }

        Debug.log(String.format("+ deserialized static data for %s in %d ms", path, (int)(Util.now() - then)));
        then = Util.now();

        Debug.log("- shapeCollection.getSize(): " + shapeCollection.getSize());
        Debug.log("- stopCollection.getSize(): " + stopCollection.getSize());
        Debug.log("- tripCollection.getSize(): " + tripCollection.getSize());
        Debug.log("- scheduleCollection.getSize(): " + scheduleCollection.getSize());
        Debug.log("- routeCollection.getSize(): " + routeCollection.getSize());
    }

    private Map<String, Vehicle> generateVehicleList(String path) {
        Map<String, Vehicle> vehicles = new HashMap<String, Vehicle>();
        FeedMessage msg = null;

        /*try (FileInputStream fis = new FileInputStream(path + "/vp.pb")) {
            msg = FeedMessage.parseFrom(fis);
        } catch (Exception e) {
            Debug.error("unable to parse feed message for " + path + ": " + e);
            return vehicles;
        }*/

        try {
            byte[] buf = Util.getFileContentsAsByteArray(path + "/vp.pb");
            ByteArrayInputStream bis = new ByteArrayInputStream(buf);
            long then = Util.now();
            msg = FeedMessage.parseFrom(bis);
            Debug.log(String.format("+ parsed protobuf in %d millis", (int)(Util.now() - then)));
        } catch (Exception e) {
            Debug.error("unable to parse feed message for " + path + ": " + e);
            return vehicles;
        }

        for (FeedEntity entity : msg.getEntityList()) {
            if (entity.hasVehicle()) {
                VehiclePosition vp = entity.getVehicle();

                if (vp.hasPosition() && vp.hasTrip() && vp.hasVehicle()) {
                    TripDescriptor tripDesc = vp.getTrip();
                    VehicleDescriptor vd = vp.getVehicle();
                    Position pos = vp.getPosition();

                    String id = entity.getId();
                    //Debug.log("-- id: " + id);
                    String tripID = tripDesc.getTripId();
                    float lat = (float)pos.getLatitude();
                    float lon = (float)pos.getLongitude();
                    float bearing = (float)pos.getBearing();
                    float speed = (float)pos.getSpeed();

                    Trip trip = tripCollection.get(tripID);
                    if (trip == null) continue;

                    Vehicle v = new Vehicle(trip, id, lat, lon, bearing, speed, trip.getFirstPoint());
                    vehicles.put(id, v);
                }
            }
        }

        return vehicles;
    }

    private void generatePredictions(String path) {
        PredictionEngine engine = new PredictionEngine(false);
        long then = Util.now();

        Map<String, Vehicle> vehicles = generateVehicleList(path);

        Debug.log(String.format("+ generated vehicle list in %d millis", (int)(Util.now() - then)));
        Debug.log("- vehicles.size(): " + vehicles.size());
        then = Util.now();

        engine.update(agency, tripCollection, stopCollection, vehicles);

        Debug.log(String.format("+ generated predictions in %d millis", (int)(Util.now() - then)));
        then = Util.now();

        for (Stop s : stopCollection) {
            if (s.getArrivalPredictionCount() > 0) {
                Debug.log("++ " + s.getArrivalPredictionsString());
            }
        }
    }

    public static void main(String[] arg) {
        new TripUpdatesMessage(arg[1], arg[0]);
    }
}
