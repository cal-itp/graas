package gtfu.tools;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

import gtfu.*;

public class TrainingDataUtil {
    private static final int TILE_SIZE   = 400;
    private static final int MAP_YOFF    = 260;
    private static final int CROP_XOFF   =   8;
    private static final int CROP_YOFF   =  31;
    private static final int CROP_WIDTH  = 386;
    private static final int CROP_HEIGHT = 363;

    private static void processGraphicReportOutput(String dataFile, String mapFile, String cacheDir, String outputDir) throws Exception {
        List<String> lines = Util.getFileContentsAsStrings(dataFile);
        TripCollection tripCollection;
        RouteCollection routeCollection;
        ShapeCollection shapeCollection;
        List<BufferedImage> tileList = getCroppedTiles(mapFile);

        Debug.log("- lines.get(0): " + lines.get(0));
        String[] fields = lines.get(0).split(",");
        AgencyYML yml = new AgencyYML();
        String agencyID = fields[5];
        long tripStart = Long.parseLong(fields[1]);
        String gtfsUrl = yml.getURL(agencyID);
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
            return;
        }

        DayLogSlicer dls = new DayLogSlicer(tripCollection, routeCollection, lines);
        List<TripReportData> tdList = dls.getTripReportDataList();
        Map<String, Map<String, GPSData>> gpsMap = dls.getMap();
        int tileIndex = 0;

        for (TripReportData td : tdList) {
            Map<String, GPSData> latLonMap = gpsMap.get(td.id);

            String folderName = makeFolder(outputDir, latLonMap.get(latLonMap.keySet().stream().findFirst()).millis, agencyID);
            Debug.log("- folderName: " + folderName);

            writePositionsToFile(folderName + "/updates.txt", latLonMap);

            StringBuilder metadata = new StringBuilder();

            metadata.append("trip-id: ");
            metadata.append(td.id);
            metadata.append('\n');

            writeStringToFile(folderName + "/metadata.txt", metadata.toString());

            Trip trip = tripCollection.get(td.id);
            Shape shape = shapeCollection.get(trip.getShapeID());
            writeShapeToFile(folderName + "/trip-outline.txt", shape);

            ImageIO.write(tileList.get(tileIndex++), "PNG", new File(folderName + "/tile.png"));
        }
    }

    private static void writeStringToFile(String path, String s) throws Exception {
        try (
            FileOutputStream fos = new FileOutputStream(path);
            PrintWriter out = new PrintWriter(fos)
        ) {
            out.println(s);
        }
    }

    private static void writePositionsToFile(String path, Map<String,GPSData> latLonMap) throws Exception {
        try (
            FileOutputStream fos = new FileOutputStream(path);
            PrintWriter out = new PrintWriter(fos)
        ) {
            for (String latLon : latLonMap.keySet()) {
                out.println(latLonMap.get(latLon).toCSVLine());
            }
        }
    }

    private static void writeShapeToFile(String path, Shape shape) throws Exception {
        try (
            FileOutputStream fos = new FileOutputStream(path);
            PrintWriter out = new PrintWriter(fos)
        ) {
            for (ShapePoint p : shape.getList()) {
                out.println(p.toCSVLine());
            }
        }
    }

    private static String makeDateFields(long millis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        //Debug.log("- cal: " + cal);

        return String.format(
            "%4d-%02d-%02d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE)
        );
    }

    private static String makeFolder(String outputDir, long millis, String agencyID) throws Exception {
        String baseName = outputDir + "/" + makeDateFields(millis) + "-" + agencyID + "-";
        int counter = 0;
        String name = null;

        for (;;) {
            name = baseName + counter++;
            if (!(new File(name)).exists()) break;
        }

        File f = new File(name);
        f.mkdir();

        return name;
    }

    private static List<BufferedImage> getCroppedTiles(String mapFile) throws Exception {
        List<BufferedImage> list = new ArrayList();
        BufferedImage report = ImageIO.read(new File(mapFile));
        int x = 0;
        int y = MAP_YOFF;

        while (y < report.getHeight()) {
            BufferedImage img = new BufferedImage(CROP_WIDTH, CROP_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics g = img.getGraphics();
            g.drawImage(report, -(x + CROP_XOFF), -(y + CROP_YOFF), null);

            list.add(img);

            x += TILE_SIZE;

            if (x >= report.getWidth()) {
                x = 0;
                y += TILE_SIZE;
            }
        }

        return list;
    }

    private static Map<String, String> getDataFiles(File folder) throws Exception {
        Map<String, String> map = new HashMap();
        File[] list = folder.listFiles();

        if (list == null) return null;

        for (File f : list) {
            String name = f.getName();

            if (name.endsWith("tile.png")) {
                map.put("map", f.getAbsolutePath());
            }

            if (name.endsWith("updates.txt")) {
                map.put("updates", f.getAbsolutePath());
            }
        }

        if (map.containsKey("map") && map.containsKey("updates")) {
            return map;
        } else {
            return null;
        }
    }

    private static boolean checkMap(String path) throws Exception {
        BufferedImage img = ImageIO.read(new File(path));

        int[] pixels = img.getRGB(
            0,
            0,
            img.getWidth(),
            img.getHeight(),
            null,
            0,
            img.getWidth()
        );

        int shapeCount = 0;
        int updateCount = 0;

        for (int i=0; i<pixels.length; i++) {
            int rgb = pixels[i] & 0xffffff;

            if (rgb == (GraphicReport.DARK.getRGB() & 0xffffff)) {
                shapeCount++;
            }

            if (rgb == (GraphicReport.ACCENT.getRGB() & 0xffffff)) {
                updateCount++;
            }
        }

        float ratio = (float)updateCount / shapeCount;

        System.out.println("--- map:");
        System.out.println("---- shape pixels : " + shapeCount);
        System.out.println("---- update pixels: " + updateCount);
        System.out.println("---- ratio: " + String.format("%1.1f", ratio));

        return ratio >= 3;
    }

    private static boolean checkUpdates(String path) throws Exception {
        List<String> lines = Util.getFileContentsAsStrings(path);

        if (lines.size() == 0) {
            Debug.error("no updates for " + path);
            return false;
        }

        String[] a = lines.get(0).split(",");
        long lastSeconds = Long.parseLong(a[0]);
        float lastLat = Float.parseFloat(a[1]);
        float lastLon = Float.parseFloat(a[2]);

        int totalSeconds = 0;
        int totalDistance = 0;

        for (String l : lines) {
            a = l.split(",");
            long seconds = Long.parseLong(a[0]);
            float lat = Float.parseFloat(a[1]);
            float lon = Float.parseFloat(a[2]);

            float distance = (float)Util.getHaversineDistance(lat, lon, lastLat, lastLon);
            float deltaSeconds = seconds - lastSeconds;

            totalSeconds += deltaSeconds;
            totalDistance += distance;

            lastSeconds = seconds;
            lastLat = lat;
            lastLon = lon;
        }

        float avgDeltaSeconds = (float)totalSeconds / lines.size();
        float avgDeltaFeet = (float)totalDistance / lines.size();

        System.out.println("--- updates:");
        System.out.println("---- avgDeltaSeconds: " + avgDeltaSeconds);
        System.out.println("---- avgDeltaFeet   : " + avgDeltaFeet);

        return avgDeltaSeconds < 5 && avgDeltaFeet < 100;
    }

    private static void findPromisingCandidates(String dataDir) throws Exception {
        File folder = new File(dataDir);

        if (!folder.isDirectory()) {
            Debug.error("not a directory: " + dataDir);
            System.exit(1);
        }

        List<File> candidates = new ArrayList();
        List<File> list = Arrays.asList(folder.listFiles());
        Collections.sort(list);

        for (File f : list) {
            Map<String, String> dataFiles = getDataFiles(f);

            if (dataFiles == null) continue;

            Debug.log("-- f: " + f.getName());

            if (!checkMap(dataFiles.get("map"))) continue;
            if (!checkUpdates(dataFiles.get("updates"))) continue;

            candidates.add(f);
        }

        Collections.sort(candidates);

        System.out.println();
        System.out.println();
        System.out.println("- candidates.size(): " + candidates.size());
        System.out.println("candidates:");

        for (File f : candidates) {
            System.out.println(f.getAbsolutePath());
        }
    }

    private static void usage() {
        System.err.println("usage: TrainingDataUtil -c|--cmd <cmd> <options>");
        System.err.println("    cmds:");
        System.err.println("        graphicreportdata|grd: -C|--cache-dir <cache-dir> -o|--output-folder <output-folder> -d|--data-file <data-file> -m|--map-file <map-file>");
        System.err.println("            <cache-dir>:     path to static GTFS cache folder");
        System.err.println("            <output-folder>: root folder for output (must already exist)");
        System.err.println("            <data-file>:     path to GraphicReport data file");
        System.err.println("            <map-file>:     path to GraphicReport map PNG");
        System.err.println("        findpromisingcandidates|fpc: -D|--data-dir <data-dir>");
        System.err.println("            <data-dir>:     path to data dir (i.e. --output-folder from previous)");

        System.exit(1);
    }

    public static void main(String[] arg) throws Exception {
        String cmd = null;
        String dataFile = null;
        String dataDir = null;
        String mapFile = null;
        String cacheDir = null;
        String outputDir = null;

        for (int i=0; i<arg.length; i++) {
            if ((arg[i].equals("-c") || arg[i].equals("--cmd")) && i < arg.length - 1) {
                cmd = arg[++i];
                continue;
            }

            if ((arg[i].equals("-d") || arg[i].equals("--data-file")) && i < arg.length - 1) {
                dataFile = arg[++i];
                continue;
            }

            if ((arg[i].equals("-D") || arg[i].equals("--data-dir")) && i < arg.length - 1) {
                dataDir = arg[++i];
                continue;
            }

            if ((arg[i].equals("-m") || arg[i].equals("--map-file")) && i < arg.length - 1) {
                mapFile = arg[++i];
                continue;
            }

            if ((arg[i].equals("-C") || arg[i].equals("--cache-dir")) && i < arg.length - 1) {
                cacheDir = arg[++i];
                continue;
            }

            if ((arg[i].equals("-o") || arg[i].equals("--output-folder")) && i < arg.length - 1) {
                outputDir = arg[++i];
                continue;
            }

            usage();
        }

        if (cmd == null) usage();

        if (cmd.equals("grd") || cmd.equals("graphicreportdata") && cacheDir != null && dataFile != null && mapFile != null && outputDir != null) {
            processGraphicReportOutput(dataFile, mapFile, cacheDir, outputDir);
            System.exit(0);
        }

        if (cmd.equals("fpc") || cmd.equals("findpromisingcandidates") && dataDir != null) {
            findPromisingCandidates(dataDir);
            System.exit(0);
        }

        usage();
    }
}