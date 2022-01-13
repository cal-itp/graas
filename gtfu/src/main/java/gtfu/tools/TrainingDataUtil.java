package gtfu.tools;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
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
        } catch(Exception e) {
            Debug.error("* can't load agency data for: " + agencyID);
            e.printStackTrace();
            return;
        }

        DayLogSlicer dls = new DayLogSlicer(tripCollection, routeCollection, lines);
        List<TripReportData> tdList = dls.getTripReportDataList();
        Map<String, List<GPSData>> map = dls.getMap();
        int tileIndex = 0;

        for (TripReportData td : tdList) {
            List<GPSData> list = map.get(td.id);

            String folderName = makeFolder(outputDir, list.get(0).millis, agencyID);
            Debug.log("- folderName: " + folderName);

            writePositionsToFile(folderName + "/updates.txt", list);
            writeStringToFile(folderName + "/metadata.txt", td.id);

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

    private static void writePositionsToFile(String path, List<GPSData> list) throws Exception {
        try (
            FileOutputStream fos = new FileOutputStream(path);
            PrintWriter out = new PrintWriter(fos)
        ) {
            for (GPSData gps : list) {
                out.println(gps.toCSVLine());
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

    private static void usage() {
        System.err.println("usage: TrainingDataUtil -C|--cache-dir -o|--output-folder <output-folder> <cache-dir> -c|--cmd <cmd> <options>");
        System.err.println("    cmds:");
        System.err.println("        graphicreportout|gro: -d <data-file> -m <map-file>");
        System.err.println("    <output-folder>: root folder for output (must already exist)");

        System.exit(1);
    }

    public static void main(String[] arg) throws Exception {
        String cmd = null;
        String dataFile = null;
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

        if (cmd.equals("gro") || cmd.equals("graphicreportout") && cacheDir != null && dataFile != null && mapFile != null && outputDir != null) {
            processGraphicReportOutput(dataFile, mapFile, cacheDir, outputDir);
            System.exit(0);
        }

        usage();
    }
}