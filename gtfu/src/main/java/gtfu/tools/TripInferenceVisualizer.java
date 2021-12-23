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
    private static final String UPDATE_HEADER = "segment update:";

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
    private static final Color INACTIVE_SEGMENT_COLOR = new Color(10, 40, 10);
    private static final Color ACTIVE_SEGMENT_COLOR = ACCENT_COLORS[10];
    private static final Color BORDER_COLOR = new Color(32, 32, 32);

    private static final int MAX_SLEEP_MILLIS = 500;
    private static final int MAX_ACC = 4;
    private static final int ZOOMED_OUT = 0;
    private static final int ZOOMED_IN = 1;
    private static final int PLAYING = 0;
    private static final int PAUSED = 1;


    private Image buf;
    private int subdivisions;
    //private ShapePoint p;
    private Map<Integer, Segment> segmentTable;
    //private List<Score> scoreList;
    private List<TripEvent> eventList;
    private int eventIndex;
    private Deque<Area> areaStack;
    private Font font;
    private String hhmmss = "";
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

    public TripInferenceVisualizer() {
        sleepMillis = MAX_SLEEP_MILLIS;
        eventList = new ArrayList<>();
        eventIndex = 0;

        segmentTable = new HashMap();
        font = new Font("Consolas", Font.PLAIN, 11);

        playState = PLAYING;
        rewState = 0;
        fwdState = 1;

        areaStack = new ArrayDeque();

        addMouseListener(this);
        addMouseMotionListener(this);
        addKeyListener(TripInferenceVisualizer.this);

        Thread t = new Thread(new Runnable() {
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

                        if (line.startsWith(GRID_HEADER)) {
                            //Debug.log(">> got grid");

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

                            TripInferenceVisualizer that = TripInferenceVisualizer.this;
                            that.setPreferredSize(new Dimension(width + 3 * PADDING + PANEL_SIZE / 2, height + 2 * PADDING));

                            f.add(that);
                            f.pack();
                            f.setVisible(true);
                        }

                        if (line.startsWith(SEGMENT_HEADER)) {
                            //Debug.log(">> got segment");

                            int id = getIntProperty(line, "id");
                            //Debug.log("- id: " + id);
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

                            if (hasProperty(line, "seconds"))  {
                                int seconds = getIntProperty(line, "seconds");
                                //Debug.log("- seconds: " + seconds);

                                hhmmss = Time.getHMSForMillis(seconds * 1000);
                            }

                            eventList.add(new TripEvent(new ShapePoint(lat, lon)));
                        }

                        if (line.startsWith(UPDATE_HEADER)) {
                            //Debug.log(">> got score");

                            int id = getIntProperty(line, "id");
                            //Debug.log("- id: " + id);
                            String name = Util.base64Decode(getProperty(line, "trip-name"));
                            //Debug.log("- name: " + name);
                            float score = getFloatProperty(line, "score");
                            //Debug.log("- score: " + score);

                            Score so = new Score(score, name, id);

                            synchronized (TripInferenceVisualizer.this) {
                                TripEvent e = eventList.get(eventList.size() - 1);
                                e.add(so);
                            }
                        }
                    }

                    repaint();

                    for (;;) {
                        repaint();
                        Util.sleep(sleepMillis);

                        if (playState == PAUSED) continue;

                        int lastEventIndex = eventIndex;

                        if (fwdState > 0) eventIndex++;
                        else if (rewState > 0) eventIndex--;

                        if (eventIndex < 0) eventIndex = 0;
                        if (eventIndex >= eventList.size()) eventIndex = eventList.size() - 1;

                        if (eventIndex != lastEventIndex) {
                            synchronized (TripInferenceVisualizer.this) {
                                for (Segment segment : segmentTable.values()) {
                                    segment.active = false;
                                }

                                TripEvent e = eventList.get(eventIndex);
                                List<Score> scoreList = e.getScoreList();

                                for (Score score : scoreList) {
                                    Segment s = segmentTable.get(score.id);

                                    if (s != null) {
                                        s.active = true;
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

        t.start();
    }

    public void keyPressed(KeyEvent e) {
        int c = e.getKeyChar();

        if (c == 'x') {
            Debug.log("bye...");
            System.exit(0);
        }

        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            synchronized (this) {
                areaStack.pop();
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

        if (areaStack.size() < 2) {
            int hslice = (int)((getWidth() - PANEL_SIZE / 2 - 3 * PADDING) / subdivisions);
            int vslice = (int)((getHeight() - 2 * PADDING) / subdivisions);
            int gridWidth = hslice * subdivisions;
            int gridHeight = vslice * subdivisions;
            int hoff = PADDING;
            int voff = (getHeight() - gridHeight) / 2;

            int x = Math.min(mouseDownX, mouseX);
            int y = Math.min(mouseDownY, mouseY);
            int w = Math.abs(mouseDownX - mouseX);
            int h = Math.abs(mouseDownY - mouseY);

            ShapePoint topLeft = new ShapePoint();
            topLeft.screenX = x - hoff;
            topLeft.screenY = y - voff;

            screenXYToLatLong(gridWidth, gridHeight, topLeft);

            ShapePoint bottomRight = new ShapePoint();
            bottomRight.screenX = x - hoff + w;
            bottomRight.screenY = y - voff + h;

            screenXYToLatLong(gridWidth, gridHeight, bottomRight);

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

        int hslice = (int)((getWidth() - PANEL_SIZE / 2 - 3 * PADDING) / subdivisions);
        int vslice = (int)((getHeight() - 2 * PADDING) / subdivisions);
        int gridWidth = hslice * subdivisions;
        int gridHeight = vslice * subdivisions;
        int hoff = PADDING;
        int voff = (getHeight() - gridHeight) / 2;

        for (int i=0; i<(subdivisions+1); i++) {
            g.drawLine(hoff, voff + i * vslice, hoff + gridWidth, voff + i * vslice);
            g.drawLine(hoff + i * hslice, voff, hoff + i * hslice, voff + gridHeight);
        }

        g.setColor(INACTIVE_SEGMENT_COLOR);

        for (Segment segment : segmentTable.values()) {
            if (segment.active) continue;

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
            if (!segment.active) continue;

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

        int buttonHeight = 20;
        hoff = (int)(2.2 * PADDING) + gridWidth;
        int panelHeight = gridHeight - buttonHeight - 10;
        g.setColor(BORDER_COLOR);
        g.drawRect(hoff, voff, PANEL_SIZE / 2, panelHeight);

        g.setFont(font);

        if (rewButton == null) {
            y = voff + gridHeight - buttonHeight;
            Color c = ACCENT_COLORS[0].darker().darker();
            g.setColor(c);

            int xx = hoff;
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

        if (eventList.size() == 0) return;

        TripEvent e = eventList.get(eventIndex);
        ShapePoint p = e.position;

        g.setColor(Color.magenta);
        latLongToScreenXY(gridWidth, gridHeight, p);
        Debug.log("- eventIndex: " + eventIndex);
        Debug.log("- p: (" + p.screenX + ", " + p.screenY + ")");

        //Debug.log("- area: " + area);

        int radius = 2;

        drawPoint(g, PADDING + p.screenX, voff + p.screenY, radius);

        y = voff + 6;
        int step = g.getFontMetrics().getHeight() + 1;
        List<Score> scoreList = e.getScoreList();

        for (int i=0; i<scoreList.size(); i++) {
            g.setColor(ACCENT_COLORS[i]);
            Score so = scoreList.get(i);
            String s = so.name + ": " + so.score;
            GraphicsUtil.drawString(g, s, hoff + 8, y);

            y += step;
        }

        g.setColor(ACCENT_COLORS[0]);
        GraphicsUtil.drawString(g, hhmmss, 5, 5);

        if (mouseDownX > 0 && mouseDownY > 0) {
            int x = Math.min(mouseDownX, mouseX);
            y = Math.min(mouseDownY, mouseY);
            int w = Math.abs(mouseDownX - mouseX);
            int h = Math.abs(mouseDownY - mouseY);

            g.setColor(Color.green);
            g.drawRect(x, y, w, h);
        }
    }

    public static void main(String[] arg) throws Exception {
        new TripInferenceVisualizer();
    }
}

class Score implements Comparable<Score> {
    float score;
    String name;
    int id;

    Score(float score, String name, int id) {
        this.score = score;
        this.name = name;
        this.id = id;
    }

    public int compareTo(Score s) {
        return (int)Math.signum(s.score - score);
    }
}

class TripEvent {
    ShapePoint position;
    private List<Score> scoreList;
    boolean sorted;

    TripEvent(ShapePoint position) {
        this.position = position;
        scoreList = new ArrayList();
    }

    void add(Score score) {
        scoreList.add(score);
    }

    List<Score> getScoreList() {
        if (!sorted) {
            Collections.sort(scoreList);
            sorted = true;
        }

        return scoreList;
    }
}

class Segment {
    int id;
    Area area;
    String startTime;
    String endTime;
    boolean active;

    Segment(int id, Area area, String startTime, String endTime) {
        this.id = id;
        this.area = area;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
