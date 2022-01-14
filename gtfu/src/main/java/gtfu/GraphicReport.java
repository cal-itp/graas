package gtfu;

import java.awt.image.BufferedImage;
import java.awt.BasicStroke;
import java.awt.Stroke;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Path2D;
import javax.imageio.ImageIO;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import gtfu.tools.DayLogSlicer;
import gtfu.tools.DB;
import gtfu.tools.GPSLogSlicer;
import gtfu.tools.SendGrid;
import gtfu.tools.AgencyYML;

import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.Clock;

public class GraphicReport {
    private static final Color BACKGROUND = new Color(0xffffff);
    private static final Color DARK       = new Color(0xe0e0e0);
    private static final Color FONT_COLOR = Color.gray;
    private static final Color TITLE_COLOR = Color.black;
    private static final Color ACCENT     = new Color(0x00b000);
    /*private static final Color BACKGROUND = new Color(0x1c3a08);
    private static final Color DARK       = new Color(0x324e34);
    private static final Color FONT_COLOR = new Color(0xd9bf77);
    private static final Color ACCENT     = new Color(0xd8ebb5);*/

    private static final String[] PROPERTY_NAMES = {
        "vehicle-id", "timestamp", "lat", "long", "trip-id", "agency-id", "uuid", "agent"
    };
    private static final int SCALE = 2;
    private static final float DOT_SIZE = 1.75f * SCALE;
    private static final int DOT_SIZE_MULTIPLIER = 8;
    private static final int CANVAS_WIDTH = 1200 * SCALE;
    private static final int TILE_SIZE = 300 * SCALE;
    private static final int MIN_HEIGHT = 40 * SCALE;
    private static final int ROW_HEIGHT = 30 * SCALE;

    private TripCollection tripCollection;
    private RouteCollection routeCollection;
    private ShapeCollection shapeCollection;
    private Map<String, Map<String, GPSData>> gpsMap;
    private List<TripReportData> tdList;
    private Map<String, TripReportData> tdMap;
    private BufferedImage img;
    private Ellipse2D.Float dot = new Ellipse2D.Float(0, 0, DOT_SIZE, DOT_SIZE);
    private Rectangle2D clipRect = new Rectangle2D.Float();
    private Font font;
    private Font smallFont;
    private int timeRowCount;

    public GraphicReport(String cacheDir, String selectedDate, String savePath, boolean downloadReport) throws Exception {
        Debug.log("GraphicReport.GraphicReport()");
        Debug.log("- cacheDir: " + cacheDir);
        Debug.log("- selectedDate: " + selectedDate);

        DB db = new DB();
        long queryStartTime = 0;
        long queryEndTime = 0;
        if (selectedDate == null) {
            queryStartTime = Time.getMidnightTimestamp() / 1000;
        } else {
            queryStartTime = Time.parseDateAsLong("MM/dd/yy", selectedDate) / 1000;
        }
        queryEndTime = Time.getNextDaySeconds(queryStartTime);
        Debug.log("- queryStartTime: " + queryStartTime);
        Debug.log("- queryEndTime: " + queryEndTime);
        List<String> results = db.fetch(queryStartTime, queryEndTime, "position", PROPERTY_NAMES);
        GPSLogSlicer slicer = new GPSLogSlicer(results, "");
        Map<String, List<String>> logs = slicer.getLogs();
        List<byte[]> blobs = new ArrayList<byte[]>();

        font = new Font("Arial", Font.PLAIN, 10 * SCALE);
        smallFont = new Font("Arial", Font.PLAIN, 9 * SCALE);

        AgencyYML a = new AgencyYML();

        for (String key : logs.keySet()) {
            // converts <agency-id>-yyyy-mm-dd.txt to <agency-id>
            String agencyID = key.substring(0, key.length() - 15);

            String gtfsUrl = a.getURL(agencyID);
            String name = a.getName(agencyID);

            if (name == null) {
                System.out.println(agencyID + " has no name in agencies.yml, will appear as Null");
            }
            if (gtfsUrl == null) {
                System.out.println(agencyID + " does not appear in agencies.yml, excluding from report");
                continue;
            }

            ConsoleProgressObserver progressObserver = new ConsoleProgressObserver(40);
            Util.updateCacheIfNeeded(cacheDir, agencyID, gtfsUrl, progressObserver);

            try {
                Map<String, Object> collections = Util.loadCollections(cacheDir, agencyID, progressObserver);
                tripCollection = (TripCollection)collections.get("trips");
                routeCollection = (RouteCollection)collections.get("routes");
                shapeCollection = (ShapeCollection)collections.get("shapes");
            } catch(Exception e) {
                Debug.error("* can't load agency data for: " + agencyID);
                e.printStackTrace();
                continue;
            }

            List<String> lines = logs.get(key);
            DayLogSlicer dls = new DayLogSlicer(tripCollection, routeCollection, lines);
            gpsMap = dls.getMap();
            tdList = dls.getTripReportDataList();
            tdMap = dls.getTripReportDataMap();
            int startSecond = dls.getStartSecond();

            if (savePath != null) {
                String path = savePath + "/" + key;
                Debug.log("- path: " + path);

                try (
                    FileOutputStream fos = new FileOutputStream(path);
                    PrintStream out = new PrintStream(fos)
                ) {
                    for (String line : lines) {
                        out.println(line);
                    }
                }
            }

            SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM d yyyy");
            String date = sdf.format(new Date(startSecond * 1000l));

            // Debug.log("- startSecond: " + startSecond);
            // Debug.log("- name: " + name);
            // Debug.log("- date: " + date);

            //addRandomTestData(1);
            // Debug.log("- tdList.size(): " + tdList.size());

            Graphics2D g = createCanvas(name, date);

            reportTimeCoverage(g);
            reportGPSCoverage(g);

            // Only create report for agencies with trip report data
            if (tdList.size() > 0) {
                blobs.add(imageToBlob(img));
            }
        }
        if (downloadReport) {
            for (int i=0; i<blobs.size(); i++) {
                byte[] buf = blobs.get(i);
                String fn = "/tmp/report-" + i + ".png";
                Debug.log("writing " + fn + "...");

                try (FileOutputStream fos = new FileOutputStream(fn)) {
                    fos.write(buf, 0, buf.length);
                }
            }
        } else {
            sendEmail(blobs);
        }
    }

    private byte[] imageToBlob(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private void sendEmail(List<byte[]> blobs) throws IOException {
        Debug.log("GraphicReport.sendEmail()");
        // Debug.log("- blobs: " + blobs);

        Recipients r = new Recipients();
        String[] recipients = r.get("graas_report");

        SendGrid grid = new SendGrid(recipients, "Automated GRaaS Report", "Attached", blobs);
        int responseCode = grid.send();
    }

    // Creates one report per agency per day
    private Graphics2D createCanvas(String name, String date) {
        timeRowCount = getTimeRowCount();
        //Debug.log("- timeRowCount: " + timeRowCount);

        int width = CANVAS_WIDTH;
        int tilesPerRow = width / TILE_SIZE;
        //Debug.log("- tilesPerRow: " + tilesPerRow);
        int tileRowCount = (int)Math.ceil(tdList.size() / (float)tilesPerRow);
        //Debug.log("- tileRowCount: " + tileRowCount);

        img = new BufferedImage(
            width,
            MIN_HEIGHT + Math.max(2, timeRowCount) * ROW_HEIGHT + tileRowCount * TILE_SIZE,
            BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g = (Graphics2D)img.getGraphics();
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();

        g.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        );

        g.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );

        g.setColor(BACKGROUND);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());

        g.setColor(FONT_COLOR);

        String desc = (name + ", " + date).toUpperCase();
        int sw = fm.stringWidth(desc);
        g.drawString(desc, (img.getWidth() - sw) / 2, 23 * SCALE);
        g.translate(0, MIN_HEIGHT);

        return g;
    }

    private void addRandomTestData(float prob) {
        for (Trip t : tripCollection) {
            if (Math.random() < prob) {
                String id = t.getID();

                if (gpsMap.get(id) == null) {
                    int start = t.getStartTime() * 1000;
                    int t1 = t.getTimeAt(0);
                    int t2 = t.getTimeAt(t.getStopSize() - 1);
                    int duration = t2 - t1;

                    TripReportData td = new TripReportData(id, t.getHeadsign(), start, duration, "testUuid", "testAgent", "testVehicleId");
                    tdList.add(td);
                    tdMap.put(id, td);

                    Map<String, GPSData> latLonMap = new HashMap();
                    long midnight = Time.getMidnightTimestamp();
                    int step = 5 * 60 * 1000;
                    int offset = 0;

                    while (offset < duration) {
                        ShapePoint p = t.getLocation(offset);
                        String latLon = String.valueOf(p.lat) + String.valueOf(p.lon);
                        latLonMap.put(latLon, new GPSData(midnight + start + offset, p.lat, p.lon));
                        offset += step;
                    }

                    gpsMap.put(id,latLonMap);
                }
            }
        }
    }

    private int getTimeRowCount() {
        int[] minutes = new int[24 * 60];

        for (TripReportData t : tdList) {
            int start = t.start / (60 * 1000);
            int duration = t.duration / (60 * 1000);

            for (int i=0; i<duration; i++) {
                minutes[start + i]++;
            }
        }

        int max = 0;

        for (int v : minutes) {
            if (v > max) {
                max = v;
            }
        }

        return max | 1;
    }

    private void reportTimeCoverage(Graphics2D g) throws Exception {
        int y = (int)(timeRowCount * ROW_HEIGHT * .4);
        int bw = (int)(img.getWidth() * .97);
        int start = (img.getWidth() - bw) / 2;
        int end = img.getWidth() - start - 2;

        g.setFont(smallFont);
        FontMetrics fm = g.getFontMetrics();

        g.setColor(DARK);

        g.drawLine(start, y, end, y);

        int offset = font.getSize() / 2;
        g.drawLine(start, y - offset, start, y + offset);
        g.drawLine(end, y - offset, end, y + offset);

        g.setColor(FONT_COLOR);

        String s = "12 am";
        int sw = fm.stringWidth(s);
        int yoff = 2 * smallFont.getSize();
        g.drawString(s, start, y + yoff);
        g.drawString(s, end - sw, y + yoff);

        List<List<TripReportData>> tdRows = new ArrayList<List<TripReportData>>();

        for (int i=0; i<timeRowCount; i++) {
            List<TripReportData> l = new ArrayList<TripReportData>();
            tdRows.add(l);
        }

        g.setColor(DARK);

        for (TripReportData t : tdList) {
            t.x = start + getDayFraction(bw, t.start);
            t.width = getDayFraction(bw, t.duration);
            t.height = 2 * offset;

            int index = 0;

            for (;;) {
                List<TripReportData> l = tdRows.get(timeRowCount / 2 + index);

                if (l.size() == 0 || !t.overlaps(l.get(l.size() - 1))) {
                    t.y = y + index * ROW_HEIGHT - offset;
                    g.fillRoundRect(t.x, t.y + 1, t.width, t.height - 1, 5, 5);

                    l.add(t);
                    break;
                }

                if (index > 0) index = -index;
                else index = -index + 1;
            }
        }

        g.setColor(ACCENT);
        yoff = 4 * SCALE;

        for (String id : gpsMap.keySet()) {
            Map<String, GPSData> latLonMap = gpsMap.get(id);
            TripReportData td = tdMap.get(id);
            if (td == null) continue;

            for (String latLon : latLonMap.keySet()) {
                int dayMillis = Time.getDayOffsetMillis(latLonMap.get(latLon).millis);
                int x = getDayFraction(bw, dayMillis);

                g.drawLine(start + x, td.y + yoff, start + x, td.y + td.height - yoff);
            }
        }

        g.translate(0, Math.max(2, timeRowCount) * ROW_HEIGHT);
    }

    private void reportGPSCoverage(Graphics2D g) throws Exception {
        int tilesPerRow = img.getWidth() / TILE_SIZE;
        //Debug.log("- tilesPerRow: " + tilesPerRow);
        int tileRowCount = (int)Math.ceil(tdList.size() / (float)tilesPerRow);
        //Debug.log("- tileRowCount: " + tileRowCount);
        int x, y;

        g.setColor(DARK);

        for (y=0; y<tileRowCount; y++) {
            g.drawLine(0, y * TILE_SIZE, img.getWidth(), y * TILE_SIZE);
        }

        for (x=1; x<tilesPerRow; x++) {
            g.drawLine(x * TILE_SIZE, 0, x * TILE_SIZE, TILE_SIZE * tileRowCount);
        }

        int lineHeight = (int)(font.getSize() * 1.33);
        int inset = TILE_SIZE / 10;
        int length = TILE_SIZE - lineHeight * 4 - inset;

        for (int i=0; i<tdList.size(); i++) {

            TripReportData td = tdList.get(i);
            x = i % tilesPerRow * TILE_SIZE;
            y = i / tilesPerRow * TILE_SIZE;
            String s  = td.getTripName();
            FontMetrics fm = g.getFontMetrics();
            int sw = fm.stringWidth(s);
            g.setColor(TITLE_COLOR);
            // TODO: dynamic text formatting/resizing to prevent overflow. For now we just clip:
            clipRect.setRect(x, y,TILE_SIZE,TILE_SIZE);
            g.setClip(clipRect);
            y = y + lineHeight;
            g.drawString(s, x + (TILE_SIZE - sw) / 2 , y);


            g.setColor(FONT_COLOR);
            s = "a: " + td.getAgent() + " o: " + td.getOs();
            sw = fm.stringWidth(s);
            y = y + lineHeight;
            g.drawString(s, x + (TILE_SIZE - sw) / 2, y);

            s =  "d: " + td.getDevice() + " v: " + td.getVehicleId() + ", u: " + td.getUuidTail();
            sw = fm.stringWidth(s);
            y = y + lineHeight;
            g.drawString(s, x + (TILE_SIZE - sw) / 2, y);

            AffineTransform t = g.getTransform();
            g.translate(x + inset, y + inset);
            drawMap(g, td, length);
            g.setTransform(t);
        }

        g.setClip(null);
    }

    private void drawMap(Graphics2D g, TripReportData td, int length) {
        //Debug.log("GraphicReport.drawMap()");

        Area area = new Area();

        Map<String, GPSData> latLonMap = gpsMap.get(td.id);
        //Debug.log("- gpsl.size(): " + gpsl.size());

        Trip trip = tripCollection.get(td.id);
        //Shape shape = trip.getShape();
        //Debug.log("- shape.getSize(): " + shape.getSize());

        // Update area for static GTFS
        Shape tripShape = trip.getShape();
        for (int i=0; i<tripShape.getSize(); i++) {
            ShapePoint sp = tripShape.get(i);
            area.update(sp);
        }

        for (String latLon : latLonMap.keySet()) {
            area.update(latLonMap.get(latLon).lat, latLonMap.get(latLon).lon);
        }

        //g.setClip(0, 0, length, length);

        Stroke savedStroke = g.getStroke();
        g.setStroke(new BasicStroke(SCALE));
        g.setColor(DARK);


        // Draw static GTFS
        Path2D.Float path = new Path2D.Float();

        for (int i=0; i<tripShape.getSize(); i++) {

            ShapePoint sp = tripShape.get(i);
            Point p = latLongToScreenXY(area, sp, length, length);
            /*ShapePoint sp2 = shape.get(i + 1);
            Point p2 = latLongToScreenXY(area, sp2, length, length);*/

            if (i == 0) path.moveTo(p.x, p.y);
            else path.lineTo(p.x, p.y);
        }

        g.draw(path);
        g.setStroke(savedStroke);
        g.setColor(ACCENT);

        // Draw vehicle location ---
        for (String latLon : latLonMap.keySet()) {
            Point p = latLongToScreenXY(area, latLonMap.get(latLon).lat, latLonMap.get(latLon).lon, length, length);

            Integer count = latLonMap.get(latLon).count;
            float scaledDotSize = DOT_SIZE * (1 + (count - 1) / DOT_SIZE_MULTIPLIER);
            dot.width = scaledDotSize;
            dot.height = scaledDotSize;
            //g.fillOval(p.x - 1, p.y - 1, 2, 2);
            dot.x = p.x - dot.width / 2;
            dot.y = p.y - dot.width / 2;
            g.fill(dot);
        }

        //g.setClip(0, 0, img.getWidth(), img.getHeight());
    }

    private Point latLongToScreenXY(Area area, ShapePoint sp, int width, int height) {
        return latLongToScreenXY(area, sp.lat, sp.lon, width, height);
    }

    private Point latLongToScreenXY(Area area, float lat, float lon, int width, int height) {
        Point p = new Point();

        float fractionLat = area.getLatFraction(lat, false);
        float fractionLong = area.getLongFraction(lon, false);

        float ratio = area.getAspectRatio();

        if (ratio > 1) {
            p.x = (int)(width * fractionLong);
            p.y = (int)(height / 2 + height / ratio * fractionLat - height / ratio / 2);
        } else {
            p.x = (int)(width * ratio * fractionLong);
            p.y = (int)(height * fractionLat);
        }

        return p;
    }

    private int getDayFraction(int fullWidth, int millis) {
        float fraction = millis / (float)Time.MILLIS_PER_DAY;
        return (int)Math.round(fullWidth * fraction);
    }

    private static void usage() {
        System.err.println("usage: GraphicReport -c|--cache-dir <cache-dir> [-s|--save-path <save-path>] [-d|--date <mm/dd/yy>] [-D|--download]");
        System.err.println("    <mm/dd/yy> is a data spefified as numeric month/day/year, e.g. 6/29/21 for June 29 2021");
        System.err.println("    <save-path> (if given) is the path to a folder where to save intermediate position data");
        System.exit(1);
    }

    public static void main(String[] arg) throws Exception {
        String cacheDir = null;
        String date = null;
        String savePath = null;
        boolean downloadReport = false;

        for (int i=0; i<arg.length; i++) {
            if ((arg[i].equals("-c") || arg[i].equals("--cache-dir")) && i < arg.length - 1) {
                cacheDir = arg[++i];
                continue;
            }

            if ((arg[i].equals("-s") || arg[i].equals("--save-path")) && i < arg.length - 1) {
                savePath = arg[++i];
                continue;
            }

            if ((arg[i].equals("-d") || arg[i].equals("--date")) && i < arg.length - 1) {
                date = arg[++i];
                continue;
            }

            if (arg[i].equals("-D") || arg[i].equals("--download")) {
                downloadReport = true;
                continue;
            }

            usage();
        }

        if (cacheDir == null) usage();

        // If date hasn't been manually set, use today's date (PT)
        if (date == null) {
            DateTimeFormatter dtFormat = DateTimeFormatter.ofPattern("MM/dd/yy");
            Clock cl = Clock.systemUTC();
            LocalDateTime now = LocalDateTime.now(cl);
            ZonedDateTime nowUTC = now.atZone(ZoneId.of("UTC"));
            ZonedDateTime nowCal = nowUTC.withZoneSameInstant(ZoneId.of("America/Los_Angeles"));
            date = dtFormat.format(nowCal);
            Debug.log("-- now: " + now);
            Debug.log("-- nowCal: " + nowCal);
            Debug.log("-- date: " + date);
        }

        new GraphicReport(cacheDir, date, savePath, downloadReport);
    }
}
