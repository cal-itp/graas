package gtfu;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Enumeration;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.Position;

public class RealtimeVisualization implements CollectionOwner, Drawable, UIEventListener, Runnable {
    private static final String LOADING = "LOADING...";
    private static final String PATH_ROOT = "/tmp/playground/static-gtfs/";

    private static final String CONFIG_KEY = "--conf";
    private static final String READ_INPUT_KEY = "--read-input";
    private static final String NAME_KEY = "--agency-name";

    private PredictionEngine engine;
    private AgencyData[] agencyData;
    private DisplayProgressObserver loadPO;
    private DisplayProgressObserver parsePO;
    private Ellipse2D.Double ellipse;
    private Agency agency;
    private ShapeCollection shapeCollection;
    private StopCollection stopCollection;
    private TripCollection tripCollection;
    private RouteCollection routeCollection;
    private Map<String, Vehicle> vehicles;
    private Map<String, CustomItem> customItemCollection;
    private Deque<Area> areaStack;
    private ShapePoint closest;
    private ShapePoint projected;
    private String vehiclePositionURL;
    private String agencyName;
    private int dataIndex;
    private String activeRouteID;
    private boolean isLoading;
    private boolean ctrlPressed;
    private Display display;
    private GeoLabel geoLabel;
    private Font smallFont;
    private Font largeFont;
    private Area paddedArea;
    private long lastVehicleChange;
    private int mouseDownX;
    private int mouseDownY;
    private int mouseDragX;
    private int mouseDragY;
    private float zoomFactor;
    private boolean readInput;

    public RealtimeVisualization(AgencyData[] agencyData, boolean readInput, int dataIndex) {
        this.agencyData = agencyData;
        this.readInput = readInput;
        this.dataIndex = dataIndex;

        engine = new PredictionEngine(true);
        vehicles = new HashMap<String, Vehicle>();
        areaStack = new ArrayDeque<Area>();
        ellipse = new Ellipse2D.Double();
        smallFont = new Font("Arial", Font.PLAIN, 12);
        largeFont = new Font("Arial", Font.PLAIN, 14);
        isLoading = true;
        display = new Display(this, false);
        display.setOverlay(this);
        display.setLocation(200, 200);

        int bh = 30;
        int bw = 200;
        int voff = 10;

        loadPO = new DisplayProgressObserver(
            display.getWidth()/ 2 - bw / 2,
            display.getHeight() / 2 - bh - voff / 2,
            bw,
            bh,
            Color.lightGray
        );

        parsePO = new DisplayProgressObserver(
            display.getWidth()/ 2 - bw / 2,
            display.getHeight() / 2 + voff / 2,
            bw,
            bh,
            Color.lightGray
        );

        load();

        Thread t = new Thread(this);
        t.start();
    }

    public TripCollection getTripCollection() {
        return tripCollection;
    }

    private void loadInternal() {
        Debug.log("RealtimeVisualization.loadInternal()");

        Map<String, Object> collections = Util.loadCollections(Util.CACHE_ROOT, agencyData[dataIndex].agencyId, parsePO);

        String path = Util.CACHE_ROOT + "/"  + agencyData[dataIndex].agencyId;
        Debug.log("- path: " + path);
        vehiclePositionURL = agencyData[dataIndex].vehiclePositionUrl;
        Debug.log("- vehiclePositionURL: " + vehiclePositionURL);
        lastVehicleChange = Util.now();
        activeRouteID = null;
        mouseDownX = -1;
        mouseDownY = -1;
        mouseDragX = -1;
        mouseDragY = -1;
        zoomFactor = 1;

        agency = new Agency(path);

        shapeCollection = (ShapeCollection)collections.get("shapes");
        stopCollection = (StopCollection)collections.get("stops");
        tripCollection = (TripCollection)collections.get("trips");
        routeCollection = (RouteCollection)collections.get("routes");
        customItemCollection = Collections.synchronizedMap(new HashMap<String, CustomItem>());

        populateDisplay(null);
    }

    private void populateDisplay(Area area) {
        if (area == null) {
            area = new Area(shapeCollection.getArea());
            area.extend(stopCollection.getArea());

            paddedArea = Util.padArea(area, 1.2f);
        }

        synchronized (this) {
            geoLabel = null;
            vehicles.clear();
        }

        display.setScaleFactor(Math.max(2, zoomFactor / 2));
        display.removeAllLists();

        for (Route route : routeCollection) {
            if (!route.matches(Filterable.ROUTE, activeRouteID)) continue;

            //Debug.log("-- " + route.getID());
            List<Trip> tripList = route.getTripList();
            Set<String> idSet = new HashSet<String>();

            for (Trip trip : tripList) {
                //Debug.log("--- " + trip.getID());
                Shape shape = trip.getShape();
                String id = shape.getID();

                if (!idSet.contains(id)) {
                    DisplayList sdl = Util.toDisplayList(
                        shape.getList(),
                        paddedArea,
                        display.getWidth(),
                        display.getHeight(),
                        route.getColor(),
                        DisplayList.STYLE_CONNECTED_POINTS,
                        1
                    );

                    display.addList(sdl);
                    idSet.add(id);
                }
            }
        }

        List<ShapePoint> stopList = new ArrayList<ShapePoint>();

        for (Stop stop : stopCollection) {
            if (!stop.matches(Filterable.ROUTE, activeRouteID)) continue;

            ShapePoint p = new ShapePoint(stop.lat, stop.lon);
            stopList.add(p);
        }

        DisplayList sdl = Util.toDisplayList(
            stopList,
            paddedArea,
            display.getWidth(),
            display.getHeight(),
            Color.gray,
            DisplayList.STYLE_POINTS_ONLY,
            1
        );

        display.addList(sdl);

        for (CustomItem item : customItemCollection.values()) {
            List<String> tripIDs = tripCollection.getTripIDsForRoute(activeRouteID);

            for (String tripID : tripIDs) {
                if (!item.matches(Filterable.TRIP, tripID)) continue;

                List<ShapePoint> customList = new ArrayList<ShapePoint>();

                for (ShapePoint p : item.getList()) {
                    customList.add(p);
                }

                DisplayList cdl = Util.toDisplayList(
                    customList,
                    paddedArea,
                    display.getWidth(),
                    display.getHeight(),
                    item.getColor(),
                    DisplayList.STYLE_POINTS_ONLY,
                    1
                );

                display.addList(sdl);
            }
        }

        display.resetPathCache();

        //display.repaint();
        isLoading = false;
        Util.notify(this);
    }

    private void load() {
        Debug.log("RealtimeVisualization.load() >");

        isLoading = true;
        loadPO.update(0);
        parsePO.update(0);
        display.removeAllLists();

        synchronized (this) {
            vehicles.clear();
            areaStack.clear();
        }

        Thread t = new Thread(new Runnable() {
            public void run() {
                Debug.log("load() runnable started");

                String agencyID = agencyData[dataIndex].agencyId;
                String gtfsURL = agencyData[dataIndex ].staticGtfsUrl;

                Util.updateCacheIfNeeded(Util.CACHE_ROOT, agencyID, gtfsURL, loadPO);
                loadInternal();
            }
        });

        t.start();

        if (readInput) {
            Thread t2 = new Thread(new Runnable() {
                public void run() {
                    try {
                        Debug.log("read input runnable started");
                        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

                        for (;;) {
                            String line = in.readLine();

                            if (line.startsWith(CustomItem.KEY)) {
                                CustomItem ci = new CustomItem(line);

                                List<String> tripIDs = tripCollection.getTripIDsForRoute(activeRouteID);

                                for (String tripID : tripIDs) {
                                    if (!ci.matches(Filterable.TRIP, tripID)) continue;

                                    DisplayList cdl = Util.toDisplayList(
                                        ci.getList(),
                                        paddedArea,
                                        display.getWidth(),
                                        display.getHeight(),
                                        ci.getColor(),
                                        DisplayList.STYLE_POINTS_ONLY,
                                        1
                                    );

                                    display.addList(cdl);
                                }

                                customItemCollection.put(ci.getName(), ci);
                            }
                        }
                    } catch (IOException e) {
                        throw new Fail(e);
                    }
                }
            });

            t2.start();
        }

        Debug.log("RealtimeVisualization.load() <");
    }

    public void run() {
        try {
            for (;;) {
                //Debug.log("+ tick");

                if (isLoading) {
                    Util.sleep(300);
                } else {
                    URL url = new URL(vehiclePositionURL);

                    try (InputStream is = url.openStream()) {
                        FeedMessage msg = FeedMessage.parseFrom(is);
                        //Debug.log("- msg: " + msg);
                        int updateCount = 0;

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

                                    if (paddedArea.contains(lat, lon)) {
                                        Vehicle v = vehicles.get(id);

                                        Trip trip = tripCollection.get(tripID);

                                        if (trip == null) {
                                            Debug.log(String.format("* no trip found for vehicle trip ID '%s'", tripID));
                                            continue;
                                        }

                                        synchronized (this) {
                                            if (v == null) {
                                                v = new Vehicle(trip, id, lat, lon, bearing, speed, trip.getFirstPoint());
                                                if (!v.matches(Filterable.ROUTE, activeRouteID)) {
                                                    //Debug.log("+ filtering " + id);
                                                    continue;
                                                }

                                                vehicles.put(id, v);
                                                updateCount++;
                                            } else {
                                                v.clearDirty();
                                                v.setPos(lat, lon);

                                                if (v.isDirty()) {
                                                    updateCount++;
                                                    ShapePoint closest = v.getClosestShapePoint();
                                                    // ### UNCOMMENT ME v.setClosestShapePoint(trip.getShape().crawl(closest, lat, lon));

                                                    //Debug.log(String.format("-- (%f, %f) -> (%f, %f)", plat, plon, v.lat, v.lon));
                                                }
                                            }

                                            v.setPos(lat, lon);
                                        }
                                    }
                                }
                            }
                        }

                        Debug.log("- vehicles.size(): " + vehicles.size());

                        if (updateCount > 0) {
                            Debug.log("- updateCount: " + updateCount);

                            int delta = (int)(Util.now() - lastVehicleChange);
                            lastVehicleChange = Util.now();
                            Debug.log("- delta: " + delta + " ms");
                        }

                        engine.update(agency, tripCollection, stopCollection, vehicles);
                    }

                    Util.wait(this, 10000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void keyPressed(KeyEvent e) {
        if (isLoading) return;

        //Debug.log("RealtimeVisualization.keyPressed()");

        int code = e.getKeyCode();

        if (code == KeyEvent.VK_N) {
            dataIndex++;

            if (dataIndex >= agencyData.length) {
                dataIndex = 0;
            }

            load();
        } else if (code == KeyEvent.VK_P) {
            dataIndex--;

            if (dataIndex < 0) {
                dataIndex = agencyData.length - 1;
            }

            load();
        } else if (code == KeyEvent.VK_R && ctrlPressed) {
            activeRouteID = null;
            populateDisplay(null);
            Debug.log("+ cleared route ID");
        } else if (code == KeyEvent.VK_Z) {
            synchronized (this) {
                if (areaStack.size() > 0) {
                    int od = paddedArea.getDiagonalInFeet();
                    paddedArea = areaStack.removeFirst();
                    int nd = paddedArea.getDiagonalInFeet();
                    float scale = 1f / ((float)nd / od);
                    Debug.log("- scale: " + scale);
                    zoomFactor *= scale;
                    Debug.log("- zoomFactor: " + zoomFactor);
                    populateDisplay(paddedArea);
                }
            }
        } else if (code == KeyEvent.VK_CONTROL) {
            ctrlPressed = true;
        }
    }

    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();

        if (code == KeyEvent.VK_CONTROL) {
            ctrlPressed = false;
        }
    }

    private void handleClick(int x, int y) {
        Debug.log("handleClick()");
        Debug.log("- x: " + x);
        Debug.log("- y: " + y);

        ShapePoint p = new ShapePoint();

        if (ctrlPressed) {
            Debug.log("- paddedArea: " + paddedArea);
            int minDistance = paddedArea.getDiagonalInFeet() / 100;
            Debug.log("- minDistance: " + minDistance);

            p.screenY = y;
            p.screenX = x;
            Util.screenXYToLatLong(display, paddedArea, p);
            Debug.log("- p: " + p);
            projected = new ShapePoint(p);

            Timer timer = new Timer("find close route");
            for (Route route : routeCollection) {
                if (route.contains(p, minDistance)) {
                    activeRouteID = route.id;
                    break;
                }
            }
            timer.dumpLap();

            Debug.log("- activeRouteID: " + activeRouteID);
            if (activeRouteID != null) {
                populateDisplay(null);
                return;
            }
        }

        for (Vehicle v : vehicles.values()) {
            if (!paddedArea.contains(v.lat, v.lon)) continue;

            p.lat = v.lat;
            p.lon = v.lon;

            Util.latLongToScreenXY(display, paddedArea, p);
            //Debug.log("- p.screenX: " + p.screenX);
            //Debug.log("- p.screenY: " + p.screenY);

            if (Math.abs(x - p.screenX) < 5 && Math.abs(y - p.screenY) < 5) {
                Debug.log("- v.id: " + v.id);
                Debug.log("- v.getTripID(): " + v.getTripID());
                Debug.log("- v.heading: " + v.heading);
                Debug.log("- v.speed: " + v.speed);

                int scheduleOffset = v.scheduleOffset;
                Debug.log("- scheduleOffset: " + scheduleOffset);
                String status = "no data";

                if (Math.abs(scheduleOffset) < Time.SECONDS_PER_DAY) {
                    status = Time.getTimeDeltaString(scheduleOffset, true);
                }

                closest = v.closest;
                projected = v.projected;

                Debug.log("- status: " + status);

                Trip trip = tripCollection.get(v.getTripID());

                synchronized (this) {
                    geoLabel = new GeoLabel(v);
                }

                return;
            }
        }

        for (Stop stop : stopCollection) {
            if (!paddedArea.contains(stop.lat, stop.lon)) continue;

            p.lat = stop.lat;
            p.lon = stop.lon;

            Util.latLongToScreenXY(display, paddedArea, p);

            //Debug.log("- p.screenX: " + p.screenX);
            //Debug.log("- p.screenY: " + p.screenY);

            if (Math.abs(x - p.screenX) < 5 && Math.abs(y - p.screenY) < 5) {
                Debug.log("- stop.id: " + stop.id);
                Debug.log("- stop.name: " + stop.name);

                synchronized (this) {
                    geoLabel = new GeoLabel(stop);
                }

                return;
            }
        }
    }

    private void handleDrag(int x, int y) {
        Debug.log("handleDrag()");

        ShapePoint tl = new ShapePoint();
        tl.screenY = Math.min(mouseDownY, mouseDragY);
        tl.screenX = Math.min(mouseDownX, mouseDragX);

        Util.screenXYToLatLong(display, paddedArea, tl);

        ShapePoint br = new ShapePoint();
        br.screenY = Math.max(mouseDownY, mouseDragY);
        br.screenX = Math.max(mouseDownX, mouseDragX);

        Util.screenXYToLatLong(display, paddedArea, br);

        Area area = new Area(tl, br);
        Debug.log("- area: " + area);
        Debug.log("+ width: " + area.getWidthInFeet() + " ft");
        Debug.log("+ height: " + area.getHeightInFeet() + " ft");

        synchronized (this) {
            int od = paddedArea.getDiagonalInFeet();
            areaStack.addFirst(paddedArea);
            paddedArea = Util.padArea(area, 1.2f);
            int nd = paddedArea.getDiagonalInFeet();
            float scale = 1f / ((float)nd / od);
            Debug.log("- scale: " + scale);
            zoomFactor *= scale;
            Debug.log("- zoomFactor: " + zoomFactor);
        }

        populateDisplay(paddedArea);
    }

    public void mousePressed(MouseEvent e) {
        mouseDownX = e.getX();
        mouseDownY = e.getY();
    }

    public void mouseDragged(MouseEvent e) {
        mouseDragX = e.getX();
        mouseDragY = e.getY();
    }

    public void mouseReleased(MouseEvent e) {
        if (isLoading) return;

        Debug.log("RealtimeVisualization.mouseReleased()");

        int x = e.getX();
        Debug.log("- x: " + x);

        int y = e.getY();
        Debug.log("- y: " + y);

        synchronized (this) {
            closest = null;
            projected = null;
        }

        if (mouseDragX >= 0) {
            handleDrag(x, y);
        } else {
            handleClick(x, y);
        }

        mouseDownX = -1;
        mouseDownY = -1;
        mouseDragX = -1;
        mouseDragY = -1;
    }

    private void paintDot(Graphics g, float lat, float lon, int size, Color color) {
        if (!paddedArea.contains(lat, lon)) return;

        float fractionLat = paddedArea.getLatFraction(lat);
        float fractionLong = paddedArea.getLongFraction(lon);

        int dsize = Math.min(display.getWidth(), display.getHeight());
        float x = dsize * fractionLong;
        float y = dsize * fractionLat;
        float radius = size / 2f;

        g.setColor(color);

        ellipse.x = x - radius;
        ellipse.y = y - radius;
        ellipse.width = size;
        ellipse.height = size;

        ((Graphics2D)g).fill(ellipse);
    }

    public synchronized void paint(Graphics g) {
        //Debug.log("RealtimeVisualization.paint()");

        Graphics2D g2d = (Graphics2D)g;

        g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        );

        if (isLoading) {
            g.setColor(Color.lightGray);
            g.setFont(smallFont);

            FontMetrics fm = g.getFontMetrics();
            int xx = (display.getWidth() - fm.stringWidth(LOADING)) / 2;
            int yy = (display.getHeight() - smallFont.getSize()) / 2;

            //g.drawString(LOADING, xx, yy);

            loadPO.paint(g);
            parsePO.paint(g);
        } else {
            int size = (int)Math.round(Math.max(4, 1.5f * zoomFactor / 2));

            for (Vehicle v : vehicles.values()) {
                paintDot(g, v.lat, v.lon, size, Color.magenta);
            }

            if (projected != null) {
                paintDot(g, projected.lat, projected.lon, size, Color.green);
            }

            if (closest != null) {
                paintDot(g, closest.lat, closest.lon, size, Color.lightGray);
            }

            g.setColor(Color.lightGray);
            g.setFont(smallFont);

            String s = String.format("%s: %d vehicles, %d stops, %d trips, %d routes", agency.getName(), vehicles.size(), stopCollection.getSize(), tripCollection.getSize(), routeCollection.getSize());
            g.drawString(s, 8, 27 + smallFont.getSize());

            if (geoLabel != null) {
                geoLabel.paint(g, display, paddedArea);
            }

            if (mouseDownX >= 0 && mouseDownY >= 0 && mouseDragX >= 0 && mouseDragY >= 0) {
                g.setColor(Color.green);

                int x = Math.min(mouseDownX, mouseDragX);
                int y = Math.min(mouseDownY, mouseDragY);
                int w = Math.abs(mouseDownX - mouseDragX);
                int h = Math.abs(mouseDownY - mouseDragY);
                g.drawRect(x, y, w, h);
            }
        }
    }

    private static final void usage() {
        System.err.println("usage: RealtimeVisualization [--agency-name <agency-name>] [--read-input] [--conf <config-path>]");
        System.exit(0);
    }

    public static void main(String[] arg) {
        AgencyData[] list = null;
        boolean readInput = false;
        String agencyName = null;

        for (int i=0; i<arg.length; i++) {
            if (CONFIG_KEY.equals(arg[i]) && i < arg.length - 1) {
                list = (AgencyData[])Util.readJSONObjectFromFile(arg[++i], AgencyData[].class);
                continue;
            }

            if (NAME_KEY.equals(arg[i]) && i < arg.length - 1) {
                agencyName = arg[++i];
                continue;
            }

            if (READ_INPUT_KEY.equals(arg[i])) {
                readInput = true;
                continue;
            }

            usage();
        }

        if (list == null) {
            usage();
        }

        int dataIndex = 0;

        if (agencyName != null) {
            for (int i=0; i<list.length; i++) {
                AgencyData d = list[i];

                if (agencyName.equals(d.agencyId)) {
                    dataIndex = i;
                    break;
                }
            }
        }

        Debug.log("- dataIndex: " + dataIndex);
        Debug.log("- readInput: " + readInput);

        new RealtimeVisualization(list, readInput, dataIndex);
    }
}
