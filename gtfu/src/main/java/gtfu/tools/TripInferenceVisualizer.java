package gtfu.tools;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Panel;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import gtfu.*;

public class TripInferenceVisualizer extends Panel implements KeyListener, MouseListener, MouseMotionListener {
    private static final String GRID_HEADER = "grid: ";
    private static final String SEGMENT_HEADER = "segment: ";
    private static final String CURRENT_HEADER = "current location: ";
    private static final String UPDATE_HEADER = "segment update: ";
    private static final String CANDIDATE_HEADER = "candidate update: ";
    private static final String ZOOM_HEADER = "zoom: ";
    private static final int RADIUS = 2;

    private static final Color[] ACCENT_COLORS = {
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

    private static final int PANEL_SIZE = 700;
    private static final int PADDING = 30;
    private static final Color GRID_COLOR = new Color(16, 16, 64);
    //private static final Color INACTIVE_SEGMENT_COLOR = new Color(10, 40, 10);
    private static final Color INACTIVE_SEGMENT_COLOR = new Color(15, 60, 15);
    private static final Color ACTIVE_SEGMENT_COLOR = ACCENT_COLORS[10];
    private static final Color BORDER_COLOR = new Color(32, 32, 32);
    private static final Color ACTIVE_LOCATION = new Color(255, 0, 255);
    private static final Color INACTIVE_LOCATION = new Color(64, 0, 64);

    private static final int MAX_SLEEP_MILLIS = 500;
    private static final int MAX_ACC = 4;
    private static final int ZOOMED_OUT = 0;
    private static final int ZOOMED_IN = 1;
    private static final int PLAYING = 0;
    private static final int PAUSED = 1;

    private Image buf;
    private int subdivisions;
    private Map<Integer, Segment> segmentTable;
    private List<TripEvent> eventList;
    private List<ShapePoint> tripPoints;
    private List<String> commands;
    private Thread runner;
    private int eventIndex;
    private Deque<Area> areaStack;
    private String frameString;
    private String dataDir;
    private String declaredTripID;
    private String segmentFilterKey;
    private String segmentFilterValue;
    private Font font;
    private Button playButton;
    private Button fwdButton;
    private Button rewButton;
    private int playState;
    private int fwdState;
    private int rewState;
    private int sleepMillis;
    private int mouseDownX;
    private int mouseDownY;
    private int mouseX;
    private int mouseY;
    private int hslice;
    private int vslice;
    private int gridWidth;
    private int gridHeight;
    private int hoff;
    private int voff;
    private float zoomFactor;

    private static String getProperty(String line, String name, char separator) {
        int i1 = line.indexOf(name + "=");
        if (i1 < 0) return null;

        i1 += name.length() + 1;
        int i2 = i1;

        while (i2 < line.length()) {
            if (line.charAt(i2) == separator) break;
            i2++;
        }

        if (separator != ' ' && i2 == line.length()) {
            throw new RuntimeException("malformed input: " + line);
        }

        return line.substring(i1, i2);
    }

    private static String getProperty(String line, String name) {
        return getProperty(line, name, ' ');
    }

    private boolean hasProperty(String line, String name) {
        return line.indexOf(name + "=") >= 0;
    }

    private int getIntProperty(String line, String name) {
        return Integer.parseInt(getProperty(line, name, ' '));
    }

    private float getFloatProperty(String line, String name) {
        return Float.parseFloat(getProperty(line, name, ' '));
    }

    private float[] getFloatTupleProperty(String line, String name) {
        String s = getProperty(line, name, ')');
        if (s == null) return null;

        if (s.charAt(0) != '(') {
            throw new RuntimeException("not a tuple: " + s);
        }

        s = s.substring(1);

        String[] arr = s.split(", ");
        float[] f = new float[arr.length];

        for (int i=0; i<f.length; i++) {
            f[i] = Float.parseFloat(arr[i]);
        }

        return f;
    }

    public TripInferenceVisualizer(String dataDir, String cmd) throws Exception {
        this.dataDir = dataDir;
        tripPoints = new ArrayList<ShapePoint>();

        List<String> list = Util.getFileContentsAsStrings(dataDir + "/trip-outline.txt");

        for (String line : list) {
            if (Util.isEmpty(line)) continue;

            String[] arg = line.split(",");
            float lat = Float.parseFloat(arg[0]);
            float lon = Float.parseFloat(arg[1]);

            tripPoints.add(new ShapePoint(lat, lon));
        }

        sleepMillis = MAX_SLEEP_MILLIS;
        eventList = new ArrayList<>();
        eventIndex = 0;
        frameString = "";
        zoomFactor = 1;

        commands = new ArrayList();
        segmentTable = new HashMap();
        font = new Font("Consolas", Font.PLAIN, 11);
        segmentFilterValue = "*";

        if (cmd != null) {
            commands.add(cmd);
        }

        playState = PLAYING;
        rewState = 0;
        fwdState = 1;

        areaStack = new ArrayDeque();

        addMouseListener(this);
        addMouseMotionListener(this);
        addKeyListener(TripInferenceVisualizer.this);

        declaredTripID = getPropertyFromFile(dataDir + "/metadata.txt", "trip-id");
        Debug.log(declaredTripID);

        String line = getPropertyFromFile(dataDir + "/metadata.txt", "grid");

        float[] topLeft = getFloatTupleProperty(line, "top_left");
        //Debug.log("- topLeft: (" + topLeft[0] + ", " + topLeft[1] + ")");
        float[] bottomRight = getFloatTupleProperty(line, "bottom_right");
        //Debug.log("- bottomRight: (" + bottomRight[0] + ", " + bottomRight[1] + ")");

        Area area = new Area(
            new ShapePoint(topLeft[0], topLeft[1]),
            new ShapePoint(bottomRight[0], bottomRight[1])
        );

        areaStack.clear();
        areaStack.push(area);

        subdivisions = getIntProperty(line, "subdivisions");
        //Debug.log("- subdivisions: " + subdivisions);

        Frame f = new Frame("Trip Inference");

        int width = 0;
        int height = 0;
        float aspectRatio = area.getAspectRatio();

        if (aspectRatio > 1) {
            width = PANEL_SIZE;
            height = (int)(PANEL_SIZE / aspectRatio);
        } else {
            width = (int)(PANEL_SIZE / aspectRatio);
            height = PANEL_SIZE;
        }

        setPreferredSize(new Dimension(width + 3 * PADDING + PANEL_SIZE / 2, height + 2 * PADDING));

        f.add(this);
        f.pack();
        f.setVisible(true);

        repaint();
    }

    private void handleCommand(String line) {
        if (line.startsWith(ZOOM_HEADER)) {
            ShapePoint tl = new ShapePoint();
            tl.screenX = getIntProperty(line, "x1");
            tl.screenY = getIntProperty(line, "y1");

            screenXYToLatLong(gridWidth, gridHeight, tl);

            ShapePoint br = new ShapePoint();
            br.screenX = getIntProperty(line, "x2");
            br.screenY = getIntProperty(line, "y2");

            screenXYToLatLong(gridWidth, gridHeight, br);

            areaStack.push(new Area(tl, br));
            return;
        }

        Debug.log("* ignoring unknown command: " + line);
    }

    private Thread getRunner() {
        return new Thread(new Runnable() {
            public void run() {
                try {
                    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
                    Pattern p = Pattern.compile("^[0-9]+:[0-9]+:[0-9]+ .*");

                    for (;;) {
                        String line = in.readLine();
                        if (line == null) break;

                        Debug.log("- line: " + line);

                        Matcher matcher = p.matcher(line);

                        if (matcher.matches()) {
                            line = line.substring(9);
                        }
                        if (line.startsWith(SEGMENT_HEADER)) {
                            //Debug.log(">> got segment");

                            int id = getIntProperty(line, "id");
                            //Debug.log("- id: " + id);
                            String tripID = getProperty(line, "trip_id");
                            //Debug.log("- trip_id: " + trip_id);
                            float[] topLeft = getFloatTupleProperty(line, "top_left");
                            //Debug.log("- topLeft: (" + topLeft[0] + ", " + topLeft[1] + ")");
                            float[] bottomRight = getFloatTupleProperty(line, "bottom_right");
                            //Debug.log("- bottomRight: (" + bottomRight[0] + ", " + bottomRight[1] + ")");
                            String startTime = getProperty(line, "start_time");
                            //Debug.log("- startTime: " + startTime);
                            String endTime = getProperty(line, "end_time");
                            //Debug.log("- endTime: " + endTime);

                            Segment segment = new Segment(
                                id,
                                tripID,
                                new Area(
                                    new ShapePoint(topLeft[0], topLeft[1]),
                                    new ShapePoint(bottomRight[0], bottomRight[1])
                                ),
                                startTime,
                                endTime
                            );

                            synchronized (TripInferenceVisualizer.this) {
                                segmentTable.put(id, segment);
                            }

                            //repaint();
                        }

                        if (line.startsWith(CURRENT_HEADER)) {
                            //Debug.log(">> got location");
                            float lat = getFloatProperty(line, "lat");
                            //Debug.log("- lat: " + lat);
                            float lon = getFloatProperty(line, "long");
                            //Debug.log("- lon: " + lon);

                            int seconds = -1;

                            if (hasProperty(line, "seconds"))  {
                                seconds = getIntProperty(line, "seconds");
                                //Debug.log("- seconds: " + seconds);
                            }

                            eventList.add(new TripEvent(new ShapePoint(lat, lon), seconds));
                        }

                        if (line.startsWith(UPDATE_HEADER)) {
                            int id = getIntProperty(line, "id");
                            //Debug.log("- id: " + id);
                            String name = Util.base64Decode(getProperty(line, "trip-name"));
                            //Debug.log("- name: " + name);
                            float score = getFloatProperty(line, "score");
                            //Debug.log("- score: " + score);
                            float closestLat = getFloatProperty(line, "closest-lat");
                            //Debug.log("- closestLat: " + closestLat);
                            float closestLon = getFloatProperty(line, "closest-lon");
                            //Debug.log("- closestLon: " + closestLon);

                            Score so = new Score(score, name, "" + id, new ShapePoint(closestLat, closestLon));

                            synchronized (TripInferenceVisualizer.this) {
                                Segment segment = segmentTable.get(id);
                                segment.setClosestPoint(new ShapePoint(closestLat, closestLon));

                                TripEvent e = eventList.get(eventList.size() - 1);
                                e.addSegmentScore(so);
                            }
                        }

                        // util.debug(f'candidate update: id={trip_id} trip-name={util.to_b64(name)} score={score}')
                        if (line.startsWith(CANDIDATE_HEADER)) {
                            String id = getProperty(line, "id");
                            //Debug.log("- id: " + id);
                            String name = Util.base64Decode(getProperty(line, "trip-name"));
                            //Debug.log("- name: " + name);
                            float score = getFloatProperty(line, "score");
                            //Debug.log("- score: " + score);

                            Score so = new Score(score, name, id);

                            synchronized (TripInferenceVisualizer.this) {
                                TripEvent e = eventList.get(eventList.size() - 1);
                                e.addCandidateScore(so);
                            }
                        }
                    }

                    repaint();

                    Debug.log("- segmentTable: " + segmentTable);

                    for (;;) {
                        if (commands.size() > 0) {
                            String line = commands.remove(0);
                            handleCommand(line);
                        }

                        repaint();
                        Util.sleep(sleepMillis);

                        if (playState == PAUSED) continue;

                        int lastEventIndex = eventIndex;

                        if (fwdState > 0) eventIndex++;
                        else if (rewState > 0) eventIndex--;

                        if (eventIndex < 0) eventIndex = eventList.size() - 1;
                        if (eventIndex >= eventList.size()) eventIndex = 0;

                        frameString = "frame: " + eventIndex + "/" + eventList.size();

                        if (eventIndex != lastEventIndex) {
                            synchronized (TripInferenceVisualizer.this) {
                                for (Segment segment : segmentTable.values()) {
                                    segment.active = false;
                                    segment.setClosestPoint(null);
                                }

                                TripEvent e = eventList.get(eventIndex);
                                List<Score> scoreList = e.getSegmentScoreList();

                                for (Score score : scoreList) {
                                    Segment s = segmentTable.get(Integer.parseInt(score.id));

                                    if (s != null) {
                                        s.active = true;
                                        s.setClosestPoint(score.p);
                                    }
                                }
                            }
                        }

                        //Debug.log("- eventIndex: (" + eventIndex + "/" + eventList.size() + ")");
                    }

                    //Debug.log("bye...");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private String getPropertyFromFile(String path, String name) throws Exception {
        List<String> strings = Util.getFileContentsAsStrings(path);
        String key = name + ": ";

        for (String line : strings) {
            if (line.startsWith(key)) {
                return line.substring(key.length());
            }
        }

        return null;
    }

    public void keyPressed(KeyEvent e) {
        int c = e.getKeyChar();

        if (c == 'x') {
            Debug.log("bye...");
            System.exit(0);
        }

        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && areaStack.size() > 1) {
            synchronized (this) {
                areaStack.pop();
                zoomFactor = 1;
                repaint();
            }
        }

        if (c == ' ') {
            playState = 1 - playState;
            playButton.setText(playState == PLAYING ? "PAUSE" : "RESUME");
            repaint();
        }

        if (c == ',') {
            if (fwdState > 0) {
                rewState = 1;
                sleepMillis = MAX_SLEEP_MILLIS;
                fwdState = 0;
                fwdButton.setText("");
                rewButton.setText("<");
                repaint();
            } else {
                rewState++;
                if (rewState > MAX_ACC) rewState = 1;

                sleepMillis = (int)(MAX_SLEEP_MILLIS / Math.pow(2, rewState - 1));

                String s = Util.repeat('<', rewState);
                rewButton.setText(s);
                repaint();
            }
        }

        if (c == '.') {
            if (rewState > 0) {
                fwdState = 1;
                sleepMillis = MAX_SLEEP_MILLIS;
                rewState = 0;
                rewButton.setText("");
                fwdButton.setText(">");
                repaint();
            } else {
                fwdState++;
                if (fwdState > MAX_ACC) fwdState = 1;

                sleepMillis = (int)(MAX_SLEEP_MILLIS / Math.pow(2, fwdState - 1));

                String s = Util.repeat('>', fwdState);
                fwdButton.setText(s);
                repaint();
            }
        }

        if (c == 't') {
            if (segmentFilterValue.equals("*")) {
                segmentFilterKey = "trip_id";
                segmentFilterValue = declaredTripID;
            } else {
                segmentFilterKey = null;
                segmentFilterValue = "*";
            }
            repaint();
        }
    }

    public void keyReleased(KeyEvent e) {
    }

    public void keyTyped(KeyEvent e) {
    }

    public void latLongToScreenXY(int displayWidth, int displayHeight, ShapePoint p) {
        //Debug.log("latLongToScreenXY()");
        //Debug.log("- displayWidth: " + displayWidth);
        //Debug.log("- displayHeight: " + displayHeight);
        //Debug.log("- p: " + p);

        Area area = areaStack.peek();
        float fractionLat = area.getLatFraction(p.lat, false);
        //Debug.log("- fractionLat: " + fractionLat);
        float fractionLong = area.getLongFraction(p.lon, false);
        //Debug.log("- fractionLong: " + fractionLong);

        p.screenX = (int)(displayWidth * fractionLong);
        p.screenY = (int)(displayHeight * fractionLat);

        //Debug.log("- p.screenX: " + p.screenX);
        //Debug.log("- p.screenY: " + p.screenY);
    }

    public void screenXYToLatLong(int displayWidth, int displayHeight, ShapePoint p) {
        float fractionX = (float)p.screenX / displayWidth;
        float fractionY = (float)p.screenY / displayHeight;
        Area area = areaStack.peek();

        p.lat = area.topLeft.lat - area.getLatDelta() * fractionY;
        p.lon = area.topLeft.lon + area.getLongDelta() * fractionX;
    }

    public void mouseClicked(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    public synchronized void mousePressed(MouseEvent e) {
        //Debug.log("mousePressed()");
        //Debug.log("- e.getX(): " + e.getX());
        //Debug.log("- e.getY(): " + e.getY());

        mouseDownX = mouseX = e.getX();
        mouseDownY = mouseY = e.getY();

        repaint();
    }

    public synchronized void mouseReleased(MouseEvent e) {
        //Debug.log("mouseReleased()");

        if (Math.abs(mouseDownX - mouseX) < 10 || Math.abs(mouseDownY - mouseY) < 10) {
            Debug.log("zoom area too small, ignoring...");
            return;
        }

        if (areaStack.size() < 2) {
            int x = Math.min(mouseDownX, mouseX);
            int y = Math.min(mouseDownY, mouseY);
            int w = Math.abs(mouseDownX - mouseX);
            int h = Math.abs(mouseDownY - mouseY);

            float wFactor = (float)gridWidth / w;
            float hFactor = (float)gridHeight / h;
            float zoomFactor = (wFactor + hFactor) / 2;

            // preserve aspect ratio
            w = (int)(gridWidth / zoomFactor);
            h = (int)(gridHeight / zoomFactor);

            ShapePoint topLeft = new ShapePoint();
            topLeft.screenX = x - hoff;
            topLeft.screenY = y - voff;

            screenXYToLatLong(gridWidth, gridHeight, topLeft);

            ShapePoint bottomRight = new ShapePoint();
            bottomRight.screenX = x - hoff + w;
            bottomRight.screenY = y - voff + h;

            screenXYToLatLong(gridWidth, gridHeight, bottomRight);

            Debug.log("- topLeft.screenX    : " + topLeft.screenX);
            Debug.log("- topLeft.screenY    : " + topLeft.screenY);
            Debug.log("- bottomRight.screenX: " + bottomRight.screenX);
            Debug.log("- bottomRight.screenY: " + bottomRight.screenY);

            Area area = new Area(topLeft, bottomRight);
            areaStack.push(area);
        }

        mouseDownX = -1;
        mouseDownY = -1;

        repaint();
    }

    public synchronized void mouseDragged(MouseEvent e) {
        //Debug.log("mouseDragged()");
        //Debug.log("- e.getX(): " + e.getX());
        //Debug.log("- e.getY(): " + e.getY());

        mouseX = e.getX();
        mouseY = e.getY();

        repaint();
    }

    public void mouseMoved(MouseEvent e) {
    }

    public void update(Graphics g) {
        Debug.log("update()");

        if (gridWidth == 0) {
            hslice = (int)((getWidth() - PANEL_SIZE / 2 - 3 * PADDING) / subdivisions);
            Debug.log("- hslice: " + hslice);
            vslice = (int)((getHeight() - 2 * PADDING) / subdivisions);
            Debug.log("- vslice: " + vslice);
            gridWidth = hslice * subdivisions;
            Debug.log("- gridWidth: " + gridWidth);
            gridHeight = vslice * subdivisions;
            Debug.log("- gridHeight: " + gridHeight);
            hoff = PADDING;
            Debug.log("- hoff: " + hoff);
            voff = (getHeight() - gridHeight) / 2;
            Debug.log("- voff: " + voff);
        }

        if (runner == null) {
            runner = getRunner();
            runner.start();
        }

        if (buf == null) {
            buf = createImage(getWidth(), getHeight());
        }

        requestFocus();
        paint(buf.getGraphics());
        g.drawImage(buf, 0, 0, null);
    }

    private void drawPoint(Graphics g, int x, int y, int radius) {
        g.fillOval(
            x - radius,
            y - radius,
            2 * radius,
            2 * radius
        );
    }

    public synchronized void paint(Graphics g) {
        int y;

        if (gridWidth == 0) return;

        Debug.log("paint()");

        g.setColor(Color.black);
        g.fillRect(0, 0, getWidth(), getHeight());

        ((Graphics2D)g).setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        );

        ((Graphics2D)g).setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );

        g.setColor(GRID_COLOR);

        for (int i=0; i<(subdivisions+1); i++) {
            g.drawLine(hoff, voff + i * vslice, hoff + gridWidth, voff + i * vslice);
            g.drawLine(hoff + i * hslice, voff, hoff + i * hslice, voff + gridHeight);
        }

        if (areaStack.size() > 0) {
            Area a = areaStack.peek();
            g.setColor(Color.gray);

            int x = hoff;
            int ll = 5;
            y = voff + gridHeight + 15;

            g.drawLine(hoff, y, hoff + gridWidth / 2 - 50, y);
            g.drawLine(hoff, y, hoff + ll, y - ll);
            g.drawLine(hoff, y, hoff + ll, y + ll);


            g.drawLine(hoff + gridWidth / 2 + 50, y, hoff + gridWidth, y);
            g.drawLine(hoff + gridWidth, y, hoff + gridWidth - ll, y - ll);
            g.drawLine(hoff + gridWidth, y, hoff + gridWidth - ll, y +ll);

            String s = Util.getDisplayDistance(a.getWidthInFeet());

            FontMetrics fm = g.getFontMetrics();
            int sw = fm.stringWidth(s);
            int hh = fm.getHeight();
            GraphicsUtil.drawString(g, s, hoff + gridWidth / 2 - sw / 2, y - hh / 2);
        }

        g.setColor(INACTIVE_SEGMENT_COLOR);

        for (Segment segment : segmentTable.values()) {
            if (segment.active || !segment.matches(segmentFilterKey, segmentFilterValue)) continue;

            Area a = segment.area;

            latLongToScreenXY(gridWidth, gridHeight, a.topLeft);
            latLongToScreenXY(gridWidth, gridHeight, a.bottomRight);

            g.drawRect(
                hoff + a.topLeft.screenX,
                voff + a.topLeft.screenY,
                a.bottomRight.screenX - a.topLeft.screenX,
                a.bottomRight.screenY - a.topLeft.screenY
            );
        }

        g.setColor(ACTIVE_SEGMENT_COLOR);

        for (Segment segment : segmentTable.values()) {
            if (!segment.active || !segment.matches(segmentFilterKey, segmentFilterValue)) continue;

            Area a = segment.area;

            latLongToScreenXY(gridWidth, gridHeight, a.topLeft);
            latLongToScreenXY(gridWidth, gridHeight, a.bottomRight);

            /*if (segmentFilterKey != null) {
                Debug.log("-- a.topLeft.screenX: " + a.topLeft.screenX);
                Debug.log("-- a.topLeft.screenY: " + a.topLeft.screenY);
            }*/

            g.drawRect(
                hoff + a.topLeft.screenX,
                voff + a.topLeft.screenY,
                a.bottomRight.screenX - a.topLeft.screenX,
                a.bottomRight.screenY - a.topLeft.screenY
            );

            latLongToScreenXY(gridWidth, gridHeight, segment.p);
            int radius = 7;

            g.drawOval(
                hoff + segment.p.screenX - radius,
                voff + segment.p.screenY - radius,
                2 * radius,
                2 * radius
            );
        }

        int buttonHeight = 20;
        int hroff = (int)(2.2 * PADDING) + gridWidth;
        int panelHeight = gridHeight - buttonHeight - 10;
        g.setColor(BORDER_COLOR);
        g.drawRect(hroff, voff, PANEL_SIZE / 2, panelHeight);

        g.setFont(font);

        if (rewButton == null) {
            y = voff + gridHeight - buttonHeight;
            Color c = ACCENT_COLORS[0].darker().darker();
            g.setColor(c);

            int xx = hroff;
            int w = PANEL_SIZE / 2 / 3;

            rewButton = new Button("", c, xx, y, w - 10, buttonHeight);
            xx += w;
            playButton = new Button("PAUSE", c, xx, y, w - 10, buttonHeight);
            xx += w;
            fwdButton = new Button(">", c, xx, y, w - 10, buttonHeight);
        }

        rewButton.paint(g);
        playButton.paint(g);
        fwdButton.paint(g);

        if (mouseDownX > 0 && mouseDownY > 0) {
            int x = Math.min(mouseDownX, mouseX);
            y = Math.min(mouseDownY, mouseY);
            int w = Math.abs(mouseDownX - mouseX);
            int h = Math.abs(mouseDownY - mouseY);

            g.setColor(Color.green);
            g.drawRect(x, y, w, h);
        }

        g.setColor(Color.gray);

        for (ShapePoint p : tripPoints) {
            latLongToScreenXY(gridWidth, gridHeight, p);
            drawPoint(g, PADDING + p.screenX, voff + p.screenY, (int)(RADIUS * 2));
        }

        ShapePoint p;

        g.setColor(INACTIVE_LOCATION);

        for (TripEvent e : eventList) {
            p = e.position;
            latLongToScreenXY(gridWidth, gridHeight, p);

            drawPoint(g, PADDING + p.screenX, voff + p.screenY, RADIUS);
        }

        if (eventList.size() == 0) return;

        g.setColor(ACTIVE_LOCATION);

        TripEvent e = eventList.get(eventIndex);
        p = e.position;
        latLongToScreenXY(gridWidth, gridHeight, p);

        drawPoint(g, PADDING + p.screenX, voff + p.screenY, RADIUS);

        y = voff + 6;
        int step = g.getFontMetrics().getHeight() + 1;
        List<Score> scoreList = e.getSegmentScoreList();
        g.setColor(Color.lightGray);
        GraphicsUtil.drawString(g, "segment scores:", hroff + 8, y);
        y += step;

        for (int i=0; i<scoreList.size(); i++) {
            g.setColor(ACCENT_COLORS[i % ACCENT_COLORS.length]);
            Score so = scoreList.get(i);
            String s = "- " + so.name + ": " + so.score;
            GraphicsUtil.drawString(g, s, hroff + 8, y);

            y += step;
        }

        y = voff + gridHeight / 2;
        scoreList = e.getCandidateScoreList();
        g.setColor(Color.lightGray);
        GraphicsUtil.drawString(g, "candidate scores:", hroff + 8, y);
        y += step;

        for (int i=0; i<scoreList.size(); i++) {
            g.setColor(ACCENT_COLORS[i % ACCENT_COLORS.length]);
            Score so = scoreList.get(i);
            String s = "- " + so.name + ": " + so.score;
            GraphicsUtil.drawString(g, s, hroff + 8, y);

            y += step;
        }

        g.setColor(ACCENT_COLORS[0]);
        int seconds = e.getDaySeconds();
        String hhmmss = "";

        if (seconds >= 0) {
            hhmmss = Time.getHMSForMillis(seconds * 1000);
        }

        GraphicsUtil.drawString(g, hhmmss, 5, 5);
        GraphicsUtil.drawString(g, frameString, 80, 5);
        GraphicsUtil.drawString(g, "declared trip ID: " + declaredTripID, 5, 20);
    }

    private static void usage() {
        System.err.println("usage: TripInferenceVisualizer -D|--data-dir <data-dir> -c|--command <command>");
        System.exit(1);
    }

    public static void main(String[] arg) throws Exception {
        String dataDir = null;
        String cmd = null;

        for (int i=0; i<arg.length; i++) {
            if ((arg[i].equals("-D") || arg[i].equals("--data-dir")) && i < arg.length - 1) {
                dataDir = arg[++i];
                continue;
            }

            if ((arg[i].equals("-c") || arg[i].equals("--command")) && i < arg.length - 1) {
                cmd = arg[++i];
                continue;
            }
        }

        if (dataDir == null) usage();

        new TripInferenceVisualizer(dataDir, cmd);
    }
}

class Score implements Comparable<Score> {
    float score;
    String name;
    String id;
    ShapePoint p;

    Score(float score, String name, String id) {
        this(score, name, id, null);
    }

    Score(float score, String name, String id, ShapePoint p) {
        this.score = score;
        this.name = name;
        this.id = id;
        this.p = p;
    }

    public int compareTo(Score s) {
        return (int)Math.signum(s.score - score);
    }
}

class TripEvent {
    ShapePoint position;
    private int daySeconds;
    private List<Score> segmentScoreList;
    private List<Score> candidateScoreList;
    boolean sorted;

    TripEvent(ShapePoint position, int daySeconds) {
        this.position = position;
        this.daySeconds = daySeconds;
        segmentScoreList = new ArrayList();
        candidateScoreList = new ArrayList();
    }

    public int getDaySeconds() {
        return daySeconds;
    }

    void addSegmentScore(Score score) {
        segmentScoreList.add(score);
    }

    void addCandidateScore(Score score) {
        candidateScoreList.add(score);
    }

    List<Score> getSegmentScoreList() {
        if (!sorted) {
            Collections.sort(segmentScoreList);
            Collections.sort(candidateScoreList);
            sorted = true;
        }

        return segmentScoreList;
    }

    List<Score> getCandidateScoreList() {
        if (!sorted) {
            Collections.sort(segmentScoreList);
            Collections.sort(candidateScoreList);
            sorted = true;
        }

        return candidateScoreList;
    }
}

class Segment {
    int id;
    String tripID;
    Area area;
    String startTime;
    String endTime;
    ShapePoint p;
    boolean active;

    Segment(int id, String tripID, Area area, String startTime, String endTime) {
        this.id = id;
        this.tripID = tripID;
        this.area = area;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public boolean matches(String name, String value) {
        if (name == null || value.equals("*")) return true;

        if (name.equals("trip_id") && tripID.equals(value)) {
            return tripID.equals(value);
        }

        return false;
    }

    void setClosestPoint(ShapePoint p) {
        this.p = p;
    }

    public String toString() {
        return "segment: id=" + id + ". tripID=" + tripID;
    }
}
