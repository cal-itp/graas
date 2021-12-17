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
import java.awt.geom.Path2D;

import javax.imageio.ImageIO;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import gtfu.tools.DB;
import gtfu.tools.GPSLogSlicer;


public class ServerActivitySummary {
    
    private static final String[] PROPERTY_NAMES = {
        "vehicle-id", "timestamp", "lat", "long", "trip-id", "agency-id"
    };


    public static void main(String[] arg) throws Exception {

    ServerActivitySummary sas = new ServerActivitySummary(1633935600, 1633936600);
    }

    public ServerActivitySummary(long startTime, long endTime) throws Exception {
        Debug.log("ServerActivitySummary.ServerActivitySummary()");
        Debug.log("- startTime: " + startTime);
        Debug.log("- endTime: " + endTime);

        DB db = new DB();
        
        List<String> results = db.fetch(startTime, endTime, "position", PROPERTY_NAMES);
        GPSLogSlicer slicer = new GPSLogSlicer(results, "");
        Map<String, List<String>> logs = slicer.getLogs();
    }
}