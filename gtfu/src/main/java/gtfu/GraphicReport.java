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
import java.awt.Rectangle;
import java.awt.geom.Path2D;
import java.awt.AlphaComposite;
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
import java.util.stream.Collectors;

import gtfu.tools.DayLogSlicer;
import gtfu.tools.DB;
import gtfu.tools.GPSLogSlicer;
import gtfu.tools.SendGrid;
import gtfu.tools.AgencyYML;
import gtfu.tools.GCloudStorage;
import gtfu.tools.AgencyListGenerator;

import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.Clock;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;

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
    private static final double OPACITY_MULTIPLIER = 0.98;
    private static final double ALPHA_MIN = 0.4;
    private static final int CANVAS_WIDTH = 1200 * SCALE;
    private static final int TILE_SIZE = 300 * SCALE;
    private static final int MIN_HEIGHT = 40 * SCALE;
    private static final int ROW_HEIGHT = 30 * SCALE;
    private static final int INSET = TILE_SIZE / 10;
    private static final Font FONT = new Font("Arial", Font.PLAIN, 10 * SCALE);
    private static final Font SMALL_FONT = new Font("Arial", Font.PLAIN, 9 * SCALE);
    private static final int LINE_HEIGHT = (int)(FONT.getSize() * 1.33);
    private static final int LENGTH = TILE_SIZE - LINE_HEIGHT * 4 - INSET;

    private TripCollection tripCollection;
    private RouteCollection routeCollection;
    private ShapeCollection shapeCollection;
    private Map<String, Map<String, GPSData>> gpsMap;
    private List<TripReportData> tdList;
    private Map<String, TripReportData> tdMap;
    private Map<String, Rectangle> timelineCoords;
    private Map<String, Rectangle> mapCoords;
    private Map<String, List<Point>> pointsMap;
    private BufferedImage img;
    private Ellipse2D.Float dot = new Ellipse2D.Float(0, 0, DOT_SIZE, DOT_SIZE);
    private Rectangle2D clipRect = new Rectangle2D.Float();
    private int timeRowCount;
    private int tileRowCount;
    private int tilesPerRow;
    private int headerHeight;
    private int bodyHeight;
    private GCloudStorage gcs = new GCloudStorage();
    private AgencyListGenerator alg = new AgencyListGenerator();

    public GraphicReport(String cacheDir, String selectedDate, String savePath, boolean sendEmail, String gCloudPath) throws Exception {
        Debug.log("GraphicReport.GraphicReport()");
        Debug.log("- cacheDir: " + cacheDir);
        Debug.log("- selectedDate: " + selectedDate);
        Debug.log("- savePath: " + savePath);
        Debug.log("- sendEmail: " + sendEmail);
        Debug.log("- gCloudPath: " + gCloudPath);

        DB db = new DB();
        long queryStartTime = 0;
        long queryEndTime = 0;
        if (selectedDate == null) {
            queryStartTime = Time.getMidnightTimestamp() / 1000;
        } else {
            queryStartTime = Time.parseDateAsLong("MM/dd/yy", selectedDate) / 1000;
        }
        queryEndTime = Time.getNextDaySeconds(queryStartTime);

        if (gCloudPath == null){
            gCloudPath = "graas-report-archive/";
        }

        // Debug.log("- queryStartTime: " + queryStartTime);
        // Debug.log("- queryEndTime: " + queryEndTime);
        List<String> results = db.fetch(queryStartTime, queryEndTime, "position", PROPERTY_NAMES);
        GPSLogSlicer slicer = new GPSLogSlicer(results, "");
        Map<String, List<String>> logs = slicer.getLogs();
        Map<String, byte[]> blobMap = new HashMap();

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
            if (tdList.size() == 0) {
                Debug.log("No results for agency " + key + ", moving on...");
                continue;
            }

            tdMap = dls.getTripReportDataMap();
            mapCoords = new HashMap();
            timelineCoords = new HashMap();
            pointsMap = new HashMap();

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
            String date = sdf.format(new Date(queryStartTime * 1000l));

            // Debug.log("- name: " + name);
            // Debug.log("- date: " + date);

            // addRandomTestData(1);
            // Debug.log("- tdList.size(): " + tdList.size());

            // converts <agency-id>-yyyy-mm-dd.txt to <agency-id>-yyyy-mm-dd
            String agencyDate = key.substring(0, key.length() - 4);

            // Create the canvas once without position updates, and then again with them
            Graphics2D noVehiclePos = createCanvas(name, date);
            reportTimeCoverage(noVehiclePos);
            reportGPSCoverage(noVehiclePos, false);
            byte[] buf = imageToBlob(img);
            uploadToGCloud(agencyDate, buf, "png", gCloudPath);
            uploadToGCloud(agencyDate, generateJsonFile().toString().getBytes("utf-8"), "json", gCloudPath);

            Graphics2D withVehiclePos = createCanvas(name, date);
            reportTimeCoverage(withVehiclePos);
            reportGPSCoverage(withVehiclePos, true);
            blobMap.put(agencyDate, imageToBlob(img));
        }

        for (String key : blobMap.keySet()) {

            byte[] buf = blobMap.get(key);

            if (savePath != null) {
                String fn = savePath + "/" + key + ".png";
                Debug.log("writing " + fn + "...");
                try (FileOutputStream fos = new FileOutputStream(fn)) {
                    fos.write(buf, 0, buf.length);
                }
            }
        }
        if (sendEmail) {
            sendEmail(blobMap);
        }
        alg.generateAgencyList(gCloudPath);
    }

    private byte[] imageToBlob(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private void sendEmail(Map<String, byte[]> blobMap) throws IOException {
        Debug.log("GraphicReport.sendEmail()");
        // Debug.log("- blobs: " + blobs);

        Recipients r = new Recipients();
        String[] recipients = r.get("graas_report");

        SendGrid grid = new SendGrid(recipients, "Automated GRaaS Report", "Attached", blobMap);
        int responseCode = grid.send();
    }

    private void uploadToGCloud(String agencyDate, byte[] file, String fileType, String gCloudPath) throws IOException {
        // converts <agency-id>-yyyy-mm-dd to <agency-id>
        String agencyID = agencyDate.substring(0, agencyDate.length() - 11);
        String path = gCloudPath + agencyID + "/";
        String fileSuffix = "";
        String fileTypeName = "";
        if(fileType.equals("png")){
            fileSuffix = ".png";
            fileTypeName = "image/png";
        }
        else if (fileType.equals("json")){
            fileSuffix = ".json";
            fileTypeName = "text/json";
        }
        String fileName = agencyDate + fileSuffix;
        gcs.uploadObject("graas-resources", path, fileName, file, fileTypeName);
    }

    private JSONObject generateJsonFile() throws IOException {
        JSONArray tripsInfo = new JSONArray();
        for (TripReportData td : tdList) {
            Rectangle timelineRect = timelineCoords.get(td.id);
            Rectangle mapRect = mapCoords.get(td.id);
            JSONObject boundaries = new JSONObject();
            boundaries.put("timelineX", timelineRect.x);
            boundaries.put("timelineY", timelineRect.y);
            boundaries.put("timelineWidth", timelineRect.width);
            boundaries.put("timelineHeight", timelineRect.height);
            boundaries.put("mapX", mapRect.x);
            boundaries.put("mapY", mapRect.y);
            boundaries.put("mapWidth", mapRect.width);
            boundaries.put("mapHeight", mapRect.height);

            JSONObject trip = new JSONObject();
            trip.put("tripID", td.id);
            trip.put("boundaries", boundaries);
            trip.put("tripName", td.getTripName());
            trip.put("agent", td.getAgent());
            trip.put("os", td.getOs());
            trip.put("device", td.getDevice());
            trip.put("vehicleID", td.getVehicleId());
            trip.put("uuidTail", td.getUuidTail());
            trip.put("avgUpdateInterval", td.getAvgUpdateInterval());
            trip.put("minUpdateInterval", td.getMinUpdateInterval());
            trip.put("maxUpdateInterval", td.getMaxUpdateInterval());

            JSONArray tripPoints = new JSONArray();
            List<Point> pl = pointsMap.get(td.id);

            for (Point p : pl) {
                JSONObject tripPoint = new JSONObject();
                tripPoint.put("x", p.x + INSET);
                tripPoint.put("y", p.y + INSET + LINE_HEIGHT);
                tripPoint.put("millis", p.millis);
                tripPoint.put("count", p.count);
                tripPoints.add(tripPoint);
            }
            trip.put("tripPoints", tripPoints);
            tripsInfo.add(trip);
        }

        JSONObject agencyReport = new JSONObject();
        agencyReport.put("trips", tripsInfo);
        agencyReport.put("headerHeight", headerHeight);
        return agencyReport;
    }

    // Creates one report per agency per day
    private Graphics2D createCanvas(String name, String date) {
        timeRowCount = getTimeRowCount();
        //Debug.log("- timeRowCount: " + timeRowCount);

        int width = CANVAS_WIDTH;
        tilesPerRow = width / TILE_SIZE;
        //Debug.log("- tilesPerRow: " + tilesPerRow);
        tileRowCount = (int)Math.ceil(tdList.size() / (float)tilesPerRow);
        //Debug.log("- tileRowCount: " + tileRowCount);
        headerHeight = MIN_HEIGHT + Math.max(2, timeRowCount) * ROW_HEIGHT;
        bodyHeight = tileRowCount * TILE_SIZE;
        img = new BufferedImage(
            width,
            headerHeight + bodyHeight,
            BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g = (Graphics2D)img.getGraphics();
        g.setFont(FONT);
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

                    Map<String, GPSData> latLonMap = new HashMap();
                    long midnight = Time.getMidnightTimestamp();
                    int step = 5 * 60 * 1000;
                    int offset = 0;

                    while (offset < duration) {
                        ShapePoint p = t.getLocation(offset);
                        if (p == null) continue;
                        String latLon = String.valueOf(p.lat) + String.valueOf(p.lon);
                        latLonMap.put(latLon, new GPSData(midnight + start + offset, 3, p.lat, p.lon));
                        offset += step;
                    }

                    gpsMap.put(id,latLonMap);
                    // Create lists as input to Stats
                    List<Double> updateFreqList = new ArrayList<>();
                    List<Double> updateMillisList = new ArrayList<>();

                    for (GPSData gpsData : gpsMap.get(id).values()) {
                        Double secsSinceLastUpdateDouble = gpsData.getSecsSinceLastUpdateAsDouble();

                        if(secsSinceLastUpdateDouble > 0) {
                            updateFreqList.add(secsSinceLastUpdateDouble);
                        }
                        updateMillisList.add(gpsData.getMillisAsDouble());
                    }

                    TripReportData td = new TripReportData(id, t.getHeadsign(), start, duration, "test", "test", "test", new Stats(updateFreqList), new Stats(updateMillisList));
                    tdList.add(td);
                    tdMap.put(id, td);
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

        g.setFont(SMALL_FONT);
        FontMetrics fm = g.getFontMetrics();

        g.setColor(DARK);

        g.drawLine(start, y, end, y);

        int offset = FONT.getSize() / 2;
        g.drawLine(start, y - offset, start, y + offset);
        g.drawLine(end, y - offset, end, y + offset);

        g.setColor(FONT_COLOR);

        String s = "12 am";
        int sw = fm.stringWidth(s);
        int yoff = 2 * SMALL_FONT.getSize();
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
                    timelineCoords.put(t.id, new Rectangle(t.x, t.y + 1 + MIN_HEIGHT, t.width, t.height - 1));
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

    private void reportGPSCoverage(Graphics2D g, boolean drawPosUpdates) throws Exception {
        int x, y;

        g.setColor(DARK);

        for (y=0; y<tileRowCount; y++) {
            g.drawLine(0, y * TILE_SIZE, img.getWidth(), y * TILE_SIZE);
        }

        for (x=1; x<tilesPerRow; x++) {
            g.drawLine(x * TILE_SIZE, 0, x * TILE_SIZE, TILE_SIZE * tileRowCount);
        }

        for (int i=0; i<tdList.size(); i++) {

            TripReportData td = tdList.get(i);
            x = i % tilesPerRow * TILE_SIZE;
            y = i / tilesPerRow * TILE_SIZE;
            mapCoords.put(td.id, new Rectangle(x, y, TILE_SIZE, TILE_SIZE));

            String s  = td.getTripName();
            FontMetrics fm = g.getFontMetrics();
            int sw = fm.stringWidth(s);
            g.setColor(TITLE_COLOR);
            // TODO: dynamic text formatting/resizing to prevent overflow. For now we just clip:
            clipRect.setRect(x, y,TILE_SIZE,TILE_SIZE);
            g.setClip(clipRect);
            y = y + LINE_HEIGHT;
            g.drawString(s, x + (TILE_SIZE - sw) / 2 , y);

            AffineTransform t = g.getTransform();
            g.translate(x + INSET, y + INSET);
            drawMap(g, td, drawPosUpdates);
            g.setTransform(t);
        }

        g.setClip(null);
    }

    private void drawMap(Graphics2D g, TripReportData td, boolean drawPosUpdates) {
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

        //g.setClip(0, 0, LENGTH, LENGTH);

        Stroke savedStroke = g.getStroke();
        g.setStroke(new BasicStroke(SCALE));
        g.setColor(DARK);

        // Draw static GTFS
        Path2D.Float path = new Path2D.Float();

        for (int i=0; i<tripShape.getSize(); i++) {

            ShapePoint sp = tripShape.get(i);
            Point p = latLongToScreenXY(area, sp, LENGTH, LENGTH);
            /*ShapePoint sp2 = shape.get(i + 1);
            Point p2 = latLongToScreenXY(area, sp2, LENGTH, LENGTH);*/

            if (i == 0) path.moveTo(p.x, p.y);
            else path.lineTo(p.x, p.y);
        }

        g.draw(path);
        g.setStroke(savedStroke);
        g.setColor(ACCENT);

        List<Point> pointList = new ArrayList<Point>();

        // Process vehicle location data and optionally draw it
        for (String latLon : latLonMap.keySet()) {
            Point p = latLongToScreenXY(area, latLonMap.get(latLon).lat, latLonMap.get(latLon).lon, LENGTH, LENGTH);
            p.millis = latLonMap.get(latLon).millis;
            p.count = latLonMap.get(latLon).count;
            pointList.add(p);

            if(drawPosUpdates){
                Integer count = latLonMap.get(latLon).count;
                float scaledDotSize = DOT_SIZE * (1 + (count - 1) / DOT_SIZE_MULTIPLIER);
                double alpha = Math.pow(OPACITY_MULTIPLIER, (double) (count - 1) );
                float alphaFinal = (float) Math.max(alpha, ALPHA_MIN);
                dot.width = scaledDotSize;
                dot.height = scaledDotSize;

                dot.x = p.x - dot.width / 2;
                dot.y = p.y - dot.width / 2;
                AlphaComposite ac = AlphaComposite.getInstance(AlphaComposite.SRC_OVER,alphaFinal);
                g.setComposite(ac);
                g.fill(dot);
            }
        }
        pointsMap.put(td.id, pointList);

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
        System.err.println("usage: GraphicReport -c|--cache-dir <cache-dir> [-s|--save-path <save-path>] [-d|--date <mm/dd/yy>] [-ne|--no-email] [-gp|--gcloud-path]");
        System.err.println("    <mm/dd/yy> is a data spefified as numeric month/day/year, e.g. 6/29/21 for June 29 2021");
        System.err.println("    <save-path> (if given) is the path to a folder where to save position logs & reports");
        System.err.println("    Using -ne prevents an email report from being sent");
        System.err.println("    Using -t runs as a test and doesn't save results to production GCloudStorage");
        System.exit(1);
    }

    public static void main(String[] arg) throws Exception {
        String cacheDir = null;
        String date = null;
        String savePath = null;
        String gCloudPath = null;
        boolean sendEmail = true;

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

            if (arg[i].equals("-ne") || arg[i].equals("--no-email")) {
                sendEmail = false;
                continue;
            }

            if ((arg[i].equals("-gp") || arg[i].equals("--gcloud-path")) && i < arg.length - 1){
                gCloudPath = arg[++i];
                continue;
            }

            usage();
        }

        if (cacheDir == null) usage();

        new GraphicReport(cacheDir, date, savePath, sendEmail, gCloudPath);
    }
}
