// This is a visualizer for an outdated version of trip inference.
// Please see tools/TripInferenceVisualizer for the current version

package gtfu;

import java.awt.*;
import java.awt.event.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class TripInferenceTracker extends Frame implements KeyListener, LatLongConverter {
    public static final String FAKE_INPUT_KEY = "--fake-input";
    public static final String LIVE_DATA_KEY  = "--live-data";

    public static final String UPDATE_KEY = "pos-update: ";
    public static final String CANDIDATE_KEY = "candidate: ";
    public static String COMPLETE_KEY = "update-complete";

    public static final int WIDTH = 600;
    public static final int PANEL_XSTEP = 300;
    public static final int PANEL_YSTEP = 26;
    public static final int PANEL_HEIGHT = 10 * PANEL_YSTEP;
    public static final int MAP_HEIGHT = 450;
    public static final int HEIGHT = PANEL_HEIGHT + MAP_HEIGHT;
    public static final int FRAME_WIDTH = 7;
    public static final int FRAME_OFFSET = FRAME_WIDTH / 2;
    public static final int TITLE_BAR_HEIGHT = 22;

    public static final Color BACKGROUND = Color.black;
    public static final Color LIGHT = Color.lightGray;

    private static final Color[] COLORS = {
        new Color(0xf7f305),
        new Color(0xf7c902),
        new Color(0xf7a103),
        new Color(0xf77a03),
        new Color(0xf72001),
        new Color(0xee228f),
        new Color(0x8820b5),
        new Color(0x0325b4),
        new Color(0x0176c2),
        new Color(0x01add1),
        new Color(0x02b103),
        new Color(0x80c903)
    };

    private static final int DEFAULT_PLAYBACK_SLEEP = 2048;

    private Area area;
    private Font font;
    private CalendarCollection calendarCollection;
    private ShapeCollection shapeCollection;
    private StopCollection stopCollection;
    private TripCollection tripCollection;
    private RouteCollection routeCollection;
    private Map<String, DisplayList> backgroundList;
    private List<InferenceUpdate> updateList;
    private List<String> activeTrips;
    private List<String> activeShapes;
    private int colorIndex;
    private int updateIndex;
    private Map<String, Color> colorMap;
    private Image offscreenBuf;
    private boolean zoomedIn;
    private int playbackSleep;

    public TripInferenceTracker(String cacheRoot, String gtfsConfigFile, String graasID, String agencyID, boolean generateFakeInput, boolean dataIsLive) {
        super("Trip Inference Tracker");

        backgroundList = new HashMap<String, DisplayList>();
        updateList = new ArrayList<InferenceUpdate>();
        colorMap = new HashMap<String, Color>();
        font = new Font("Courier New", Font.PLAIN, 11);

        int width = WIDTH + 2 * FRAME_WIDTH;
        int height = HEIGHT + 2 * FRAME_WIDTH + TITLE_BAR_HEIGHT;
        playbackSleep = DEFAULT_PLAYBACK_SLEEP;

        setSize(width, height);
        setLocation(300, 75);
        setVisible(true);
        addKeyListener(this);

        AgencyData[] agencyList = (AgencyData[])Util.readJSONObjectFromFile(gtfsConfigFile, AgencyData[].class);
        String gtfsURL = Util.getGTFSURL(agencyList, graasID);
        ConsoleProgressObserver po = new ConsoleProgressObserver(40);
        Util.updateCacheIfNeeded(cacheRoot, graasID, gtfsURL, po);
        Map<String, Object> collections = Util.loadCollections(cacheRoot, graasID, po);

        calendarCollection = (CalendarCollection)collections.get("calendars");
        shapeCollection = (ShapeCollection)collections.get("shapes");
        stopCollection = (StopCollection)collections.get("stops");
        tripCollection = (TripCollection)collections.get("trips");
        routeCollection = (RouteCollection)collections.get("routes");

        Debug.log("- shapeCollection.getSize(): " + shapeCollection.getSize());
        Debug.log("- stopCollection.getSize(): " + stopCollection.getSize());
        Debug.log("- tripCollection.getSize(): " + tripCollection.getSize());
        Debug.log("- routeCollection.getSize(): " + routeCollection.getSize());

        activeTrips = new ArrayList<String>();
        activeShapes = new ArrayList<String>();

        for (Trip t : tripCollection) {
            String routeID = t.getRouteID();
            Route route = routeCollection.get(routeID);

            if (route.getAgencyID().equals(agencyID)) {
                activeTrips.add(t.getID());

                String shapeID = t.getShapeID();

                // ### use a map for active shapes to avoid O(n ^ 2) here
                if (!activeShapes.contains(shapeID)) {
                    activeShapes.add(shapeID);
                }
            }
        }

        Debug.log("- activeTrips.size(): " + activeTrips.size());
        Debug.log("- activeShapes.size(): " + activeShapes.size());

        area = new Area();
        computeScreenCoordinates(true);

        repaint();
        if (generateFakeInput) generateFakeInput();

        Object lock = this;

        Thread t = new Thread(new Runnable() {
            private InferenceUpdate update;

            private String readLine(InputStream is) throws IOException {
                    StringBuffer sb = new StringBuffer();
                    int c = -1;

                    for (;;) {
                        c = is.read();

                        if (c < 0) return null;
                        if (c == '\n') break;

                        sb.append((char)c);
                    }

                    return sb.toString();
            }

            public void run() {
                String line = null;
                int index = 0;

                for (;;) {
                    try {line = readLine(System.in);}
                    catch (IOException e) {throw new Fail(e);}

                    if (line == null) break;
                    line = line.trim();
                    Debug.log(". line: >" + line + "<");

                    if ((index = line.indexOf(UPDATE_KEY)) >= 0) {
                        line = line.substring(index);
                        Debug.log("- line: " + line);
                        String[] tok = line.substring(UPDATE_KEY.length()).split(" ");
                        Debug.log("- tok: " + Util.arrayToString(tok));
                        ShapePoint p = new ShapePoint(
                            Float.parseFloat(tok[0]),
                            Float.parseFloat(tok[1])
                        );
                        latLongToScreenXY(WIDTH, MAP_HEIGHT, area, p);
                        Debug.log("- tok[2]: " + tok[2]);
                        int timeOffset = Integer.parseInt(tok[2]);
                        update = new InferenceUpdate(p, timeOffset);
                    } else if ((index = line.indexOf(CANDIDATE_KEY)) >= 0) {
                        line = line.substring(index);
                        Debug.log("- line: " + line);
                        CandidateData cd = CandidateData.fromString(
                            line.substring(CANDIDATE_KEY.length())
                        );
                        latLongToScreenXY(WIDTH, MAP_HEIGHT, area, cd.closest);
                        if (update != null) update.addCandidate(cd);
                    } else if ((index = line.indexOf(COMPLETE_KEY)) >= 0) {
                        line = line.substring(index);
                        Debug.log("- line: " + line);

                        if (update != null) {
                            Collections.sort(update.candidateList);
                            addUpdate(update);
                        }

                        repaint();

                        if (!dataIsLive) {
                            Util.sleep(playbackSleep);
                        }
                    }
                }
            }
        });

        t.start();
    }

    private void computeScreenCoordinates(boolean updateArea) {
        backgroundList.clear();

        if (updateArea) {
            for (String shapeID : activeShapes) {
                Shape s = shapeCollection.get(shapeID);

                for (int i=0; i<s.getSize(); i++) {
                    area.update(s.get(i));
                }
            }
        }

        area = Util.padArea(area, 1.33f);

        for (String shapeID : activeShapes) {
            Shape s = shapeCollection.get(shapeID);

            DisplayList dl = Util.toDisplayList(
                s.getList(),
                area,
                this,
                WIDTH,
                MAP_HEIGHT,
                Color.darkGray,
                DisplayList.STYLE_CONNECTED_POINTS,
                1
            );

            backgroundList.put(shapeID, dl);
        }

        for (InferenceUpdate update : updateList) {
            latLongToScreenXY(WIDTH, MAP_HEIGHT, area, update.location);

            for (CandidateData cd : update.candidateList) {
                latLongToScreenXY(WIDTH, MAP_HEIGHT, area, cd.closest);
            }
        }

        repaint();
    }

    private Color getColor(String shapeID) {
        Color c = colorMap.get(shapeID);

        if (c == null) {
            c = COLORS[colorIndex];
            colorMap.put(shapeID, c);

            colorIndex++;
            if (colorIndex >= COLORS.length) {
                colorIndex = 0;
            }
        }

        return c;
    }

    public synchronized void addUpdate(InferenceUpdate update) {
        updateList.add(update);
        updateIndex = updateList.size() - 1;

        /*if (zoomedIn) {
            handleZoom();
        }*/
    }

    public void latLongToScreenXY(int displayWidth, int displayHeight, Area area, ShapePoint p) {
        float fractionLat = area.getLatFraction(p.lat, false);
        float fractionLong = area.getLongFraction(p.lon, false);

        float ratio = area.getAspectRatio();

        if (ratio > 1) {
            p.screenX = (int)(displayHeight * fractionLong);
            p.screenY = (int)(displayHeight / 2 + displayHeight / ratio * fractionLat - displayHeight / ratio / 2);
        } else {
            p.screenX = (int)(displayHeight * ratio * fractionLong);
            p.screenY = (int)(displayHeight * fractionLat);
        }
    }

    String getDays(Trip trip) {
        Calendar cal = calendarCollection.get(trip.getServiceID());
        return cal.toShortList();
    }

    private void generateFakeInput() {
        Debug.log("TripInferenceTracker.generateFakeInput()");

        WritableInputStream wis = new WritableInputStream();
        System.setIn(wis);

        Thread t = new Thread(new Runnable() {
            private ShapePoint getRandomPoint(Area area) {
                //Debug.log("- area: " + area);
                ShapePoint p = new ShapePoint(
                    Util.getRandomFloat(area.topLeft.lat, area.bottomRight.lat),
                    Util.getRandomFloat(area.bottomRight.lon, area.topLeft.lon)
                );

                return p;
            }

            public void run() {
                for (;;) {
                    ShapePoint p = getRandomPoint(area);
                    String s = String.format("%s%f %f %d", UPDATE_KEY, p.lat, p.lon, Util.getRandom(86400));
                    wis.writeLine(s.getBytes());

                    int count = Util.getRandom(20);

                    for (int i=0; i<count; i++) {
                        int n = Util.getRandom(activeTrips.size());
                        String tripID = activeTrips.get(n);
                        Trip trip = tripCollection.get(tripID);
                        String days = getDays(trip);
                        float score = Util.getRandomFloat(0, 10000);
                        int start = Util.getRandom(86400);
                        int end = Util.getRandom(86400);
                        p = getRandomPoint(area);
                        int offset = Util.getRandom(86400);

                        s = String.format(
                            "%s%s %s %f %s %d %d %f %f %d",
                            CANDIDATE_KEY,
                            tripID,
                            trip.getShapeID(),
                            score,
                            days,
                            start,
                            end,
                            p.lat,
                            p.lon,
                            offset
                        );

                        wis.writeLine(s.getBytes());
                    }

                    s = String.format(COMPLETE_KEY);
                    wis.writeLine(s.getBytes());

                    Util.sleep(3000);
                }
            }
        });

        t.start();
    }

    public void update(Graphics g) {
        if (offscreenBuf == null) {
            offscreenBuf = createImage(getWidth(), getHeight());
        }

        paint(offscreenBuf.getGraphics());
        g.drawImage(offscreenBuf, 0, 0, null);
    }

    public synchronized void paint(Graphics gg) {
        /*Debug.log("TripInferenceTracker.paint()");
        Debug.log("- getWidth(): " + getWidth());
        Debug.log("- getHeight(): " + getHeight());*/

        Graphics2D g = (Graphics2D)gg;
        g.setFont(font);

        g.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        );

        g.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );

        g.setColor(BACKGROUND);
        g.fillRect(0, 0, getWidth(), getHeight());

        g.setColor(LIGHT);
        g.drawRect(FRAME_OFFSET, FRAME_OFFSET + TITLE_BAR_HEIGHT, getWidth() - 2 * FRAME_OFFSET, getHeight() - 2 * FRAME_OFFSET- TITLE_BAR_HEIGHT);

        g.translate(FRAME_WIDTH, TITLE_BAR_HEIGHT + FRAME_WIDTH);
        if (updateList.size() == 0) return;

        drawPanel(g);
        g.translate(0, PANEL_HEIGHT);
        drawMap(g);
    }

    private void drawPanel(Graphics2D g) {
        g.setColor(Color.darkGray);

        for (int x=0; x<WIDTH; x+=PANEL_XSTEP) {
            g.drawLine(x, 0, x, PANEL_HEIGHT);
        }

        for (int y=0; y<PANEL_HEIGHT; y+=PANEL_YSTEP) {
            g.drawLine(0, y, WIDTH, y);
        }

        int rows = PANEL_HEIGHT / PANEL_YSTEP;
        List<CandidateData> candidateList = updateList.get(updateIndex).candidateList;

        for (int i=0; i<candidateList.size(); i++) {
            CandidateData cd = candidateList.get(i);
            Color c = getColor(cd.shapeID);

            int x = i / rows * PANEL_XSTEP;
            int y = i % rows * PANEL_YSTEP;

            int offset = 10;
            g.setColor(c);
            g.fillRect(x + offset / 2, y + (PANEL_YSTEP - offset) / 2, offset, offset);

            g.setColor(Color.lightGray);
            //Debug.log("- cd.days: " + cd.days);
            g.drawString(cd.days, x + 20, y + PANEL_YSTEP / 2 + font.getSize() / 2);

            String start = Time.getHMForSeconds(cd.start, false);
            String end = Time.getHMForSeconds(cd.end, false);
            g.drawString(start + "-" + end, x + 75, y + PANEL_YSTEP / 2 + font.getSize() / 2);

            g.drawString(String.format("%4.3f", cd.score), x + 160, y + PANEL_YSTEP / 2 + font.getSize() / 2);
        }
    }

    private void drawMap(Graphics2D g) {
        int d = 7;

        g.setColor(Color.darkGray);
        g.drawLine(0, 0, WIDTH, 0);

        for (DisplayList dl : backgroundList.values()) {
            dl.paint((Graphics)g, 1);
        }

        InferenceUpdate update = updateList.get(updateIndex);

        for (CandidateData cd : update.candidateList) {
            DisplayList dl = backgroundList.get(cd.shapeID);
            Color c = getColor(cd.shapeID);
            g.setColor(c);
            dl.paint((Graphics)g, c, 1);

            drawCircle(
                g,
                (int)(cd.closest.screenX - d / 2),
                (int)(cd.closest.screenY - d / 2),
                d
            );
        }

        g.setColor(Color.white);

        drawCircle(
            g,
            (int)(update.location.screenX - d / 2),
            (int)(update.location.screenY - d / 2),
            2 * d
        );

        g.drawString(Time.getHMForSeconds(update.timeOffset, false), WIDTH - 40, 3 + font.getSize());

        String s = "playback sleep: " + playbackSleep;
        g.drawString(s, 5, 3 + font.getSize());
    }

    private void drawCircle(Graphics2D g, int x, int y, int d) {
        g.drawOval(x - d / 2, y - d / 2, d, d);
    }

    private void handleZoom() {
        if (!zoomedIn) {
            if (updateList.size() == 0) return;

            InferenceUpdate update = updateList.get(updateIndex);

            synchronized (this) {
                area = new Area(update.location.lat, update.location.lon, 20 * Area.HALF_MILE_DECIMAL);
                computeScreenCoordinates(false);
            }

            zoomedIn = true;
        } else {
            synchronized (this) {
                area = new Area();
                computeScreenCoordinates(true);
            }

            zoomedIn = false;
        }
    }

    public void keyReleased(KeyEvent e) {
    }

    public void keyTyped(KeyEvent e) {
    }

    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        int c = e.getKeyChar();

        if (c == 'x') {
            System.exit(1);
        }

        if (c == 'z') {
            handleZoom();
        }

        if (code == KeyEvent.VK_UP && playbackSleep > 1) {
            playbackSleep /= 2;
        }

        if (code == KeyEvent.VK_DOWN) {
            playbackSleep *= 2;
        }

        if (c == '1') {
            playbackSleep = DEFAULT_PLAYBACK_SLEEP;
        }
    }

    private static void usage() {
        System.err.println("TripInferenceTracker [--fake-input <false|true>]");
        System.err.println("--fake-input: if true, generate fake input for testing purposes (default is false)");
        System.exit(0);
    }

    public static void main(String[] arg) {
        String cacheRoot = System.getProperty("user.home") + "/tmp/tuff";
        String gtfsConfigFile = System.getProperty("user.home") + "/tmp/gtfs-visualization.json";
        String graasID = "humboldt-transit-authority";
        String agencyID = "1";
        boolean generateFakeInput = false;
        boolean dataIsLive = false;

        for (int i=0; i<arg.length; i++) {
            if (arg[i].equals(FAKE_INPUT_KEY) && i + 1 < arg.length) {
                try {generateFakeInput = Boolean.parseBoolean(arg[++i]);}
                catch (Exception e) {usage();}
                continue;
            }

            if (arg[i].equals(LIVE_DATA_KEY) && i + 1 < arg.length) {
                try {dataIsLive = Boolean.parseBoolean(arg[++i]);}
                catch (Exception e) {usage();}
                continue;
            }

            usage();
        }

        new TripInferenceTracker(cacheRoot, gtfsConfigFile, graasID, agencyID, generateFakeInput, dataIsLive);
    }
}

class InferenceUpdate {
    ShapePoint location;
    int timeOffset;
    List<CandidateData> candidateList;

    public InferenceUpdate(ShapePoint location, int timeOffset) {
        this.location = location;
        this.timeOffset = timeOffset;
        this.candidateList = new ArrayList<CandidateData>();
    }

    public void addCandidate(CandidateData cd) {
        candidateList.add(cd);
    }
}

class CandidateData implements Comparable<CandidateData> {
    String id;
    String shapeID;
    float score;
    String days; // e.g. MT--F--
    int start;
    int end;
    ShapePoint closest;
    int closestOffset;

    static CandidateData fromString(String s) {
        String[] tok = s.split(" ");

        return new CandidateData(
            tok[0],
            tok[1],
            Float.parseFloat(tok[2]),
            tok[3],
            Integer.parseInt(tok[4]),
            Integer.parseInt(tok[5]),
            new ShapePoint(
                Float.parseFloat(tok[6]),
                Float.parseFloat(tok[7])
            ),
            (int)Math.rint(Float.parseFloat(tok[8]))
        );
    }

    CandidateData(String id, String shapeID, float score, String days, int start, int end, ShapePoint closest, int closestOffset) {
        this.id = id;
        this.shapeID = shapeID;
        this.score = score;
        this.days = days;
        this.start = start;
        this.end = end;
        this.closest = closest;
        this.closestOffset = closestOffset;
    }

    public int  compareTo(CandidateData cd) {
        return (int)Math.signum(cd.score - score);
    }
}

class WritableInputStream extends InputStream {
    private List<Byte> buf;

    public WritableInputStream() {
        buf = new ArrayList<Byte>();
    }

    public synchronized void writeLine(byte[] data) {
        //Debug.log("WritableInputStream.write()");
        //Debug.log("- data.length: " + data.length);

        for (byte b : data) {
            buf.add(b);
        }

        buf.add((byte)'\n');
        Util.notify(this);
    }

    public synchronized int read() {
        //Debug.log("WritableInputStream.read()");
        //Debug.log("- buf.size(): " + buf.size());

        while (buf.size() == 0) {
            Util.wait(this, 10000);
        }

        return buf.remove(0);
    }
}
