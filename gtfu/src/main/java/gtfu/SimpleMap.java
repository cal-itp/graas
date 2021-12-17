package gtfu;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

class SimpleMap implements Drawable, LatLongConverter, UIEventListener {
    private DisplayList vehicle;
    private DisplayList stops;
    private Font font;
    private int index;
    private int step;
    private String filename;

    public SimpleMap(String filename) {
        this.filename = filename;

        font = new Font("Arial", Font.PLAIN, 11);

        TextFile tf = new TextFile(filename);
        CSVHeader header = new CSVHeader(tf.getNextLine());
        Display display = new Display(this);
        List<ListData> lists = new ArrayList<ListData>();
        Area area = new Area();
        ListData ald = null;

        File f = new File(filename);
        String path = System.getProperty("user.dir") + File.separatorChar;

        if (f.getParentFile() != null) {
            path = f.getParentFile().getPath() + File.separatorChar;
        }

        //Debug.log("- path: " + path);

        for (;;) {
            String line = tf.getNextLine();
            if (line == null) break;

            CSVRecord record = new CSVRecord(header, line);

            String file = record.get("file");
            //Debug.log("- file: " + file);

            List<ShapePoint> plist = getPoints(path + file, area);
            //Debug.log("- plist" + plist);

            Color color = new Color(record.getHex("color"));
            //Debug.log("- color: " + color);

            int size = record.getInt("size");
            //Debug.log("- size: " + size);

            int style = getStyle(record.get("style"));
            ListData ld = new ListData(plist, color, size, style);
            ld.filename = file;


            if (style == DisplayList.STYLE_ANIMATE_PROGRESS) {
                ald = ld;
            } else {
                lists.add(ld);
            }

        }

        display.setOverlay(this);
        display.setSize(800, 800);
        display.setLocation(400, 50);
        display.setVisible(true);

        area = Util.padArea(area, 1.33f);

        for (ListData ld : lists) {
            DisplayList dl = Util.toDisplayList(
                ld.list,
                area,
                this,
                display.getWidth(),
                display.getHeight(),
                ld.color,
                ld.style,
                ld.size
            );

            //Debug.log("- dl: " + dl);

            if (ld.filename.startsWith("stops-")) {
                stops = dl;
            }

            display.addList(dl);
        }

        display.resetPathCache();

        if (ald != null) {
            vehicle = Util.toDisplayList(
                ald.list,
                area,
                this,
                display.getWidth(),
                display.getHeight(),
                ald.color,
                ald.style,
                ald.size
            );
        }
    }

    public void latLongToScreenXY(int displayWidth, int displayHeight, Area area, ShapePoint p) {
        float fractionLat = area.getLatFraction(p.lat);
        float fractionLong = area.getLongFraction(p.lon);

        float ratio = area.getAspectRatio();

        if (ratio > 1) {
            p.screenX = (int)(displayHeight * fractionLong);
            p.screenY = (int)(displayHeight / 2 + displayHeight / ratio * fractionLat - displayHeight / ratio / 2);
        } else {
            p.screenX = (int)(displayHeight * ratio * fractionLong);
            p.screenY = (int)(displayHeight * fractionLat);
        }
    }


    public synchronized void paint(Graphics g) {
        //Debug.log("SimpleMap.paint()");

        g.setFont(font);
        g.setColor(Color.lightGray);
        g.drawString(filename, 10, 40);

        if (vehicle != null) {
            Point p = vehicle.get(index);
            g.setColor(Color.green);

            int r = 5;
            g.drawOval(p.x - r, p.y - r, 2 * r, 2 * r);

            index += step;

            if (index >= vehicle.size()) {
                index = 0;
            }
        }

        if (stops != null) {
            for (int i=0; i<stops.size(); i++) {
                Point p = stops.get(i);
                g.drawString("" + (i + 1), p.x + 10, p.y);
            }
        }
    }

    private int getStyle(String s) {
        if (s.equals("line")) return DisplayList.STYLE_CONNECTED_POINTS;
        else if (s.equals("points")) return DisplayList.STYLE_POINTS_ONLY;
        else if (s.equals("unscaled-points")) return DisplayList.STYLE_UNSCALED_POINTS;
        else if (s.equals("outlined-unscaled-points")) return DisplayList.STYLE_OUTLINED_UNSCALED_POINTS;
        else if (s.equals("animate")) return DisplayList.STYLE_ANIMATE_PROGRESS;
        else throw new Fail("unknow line style: " + s);
    }

    public List<ShapePoint> getPoints(String filename, Area area) {
        TextFile tf = new TextFile(filename);
        CSVHeader header = new CSVHeader(tf.getNextLine());
        List<ShapePoint> list = new ArrayList<ShapePoint>();
        boolean show = false; // filename.indexOf("stops-") > 0 || filename.indexOf("proxi-") > 0;

        if (show) {
            Debug.log("");
        }

        for (;;) {
            String line = tf.getNextLine();
            if (line == null) break;


            if (show) {
                Debug.log(line);
            }

            CSVRecord record = new CSVRecord(header, line);
            float lat = record.getFloat("lat");
            float lon = record.getFloat("long");

            area.update(lat, lon);
            list.add(new MapPoint(lat, lon));
        }

        return list;
    }

    public synchronized void keyPressed(KeyEvent e) {
        //Debug.log("SimpleMap.keyPressed()");

        int c = e.getKeyChar();

        if (c == ' ') {
            step = 1 - step;
        }

        if (c == 'n') {
            index++;

            if (index >= vehicle.size()) {
                index = 0;
            }
        }

        if (c == 'p') {
            index--;

            if (index < 0) {
                index = vehicle.size() - 1;
            }
        }

        if (c == 'x') {
            System.out.println("bye...");
            System.exit(1);
        }
    }

    public void keyReleased(KeyEvent e) {
        //Debug.log("SimpleMap.keyReleased()");
    }

    public void mousePressed(MouseEvent e) {
        Debug.log("SimpleMap.mousePressed()");
    }

    public void mouseReleased(MouseEvent e) {
        Debug.log("SimpleMap.mouseReleased()");
    }

    public void mouseDragged(MouseEvent e) {
        Debug.log("SimpleMap.mouseDragged()");
    }

    public static void main(String[] arg) {
        if (arg.length == 0) {
            System.err.println("usage: SimpleMap <path-to-main-csv>");
            System.exit(0);
        }

        new SimpleMap(arg[0]);
    }
}

class MapPoint extends ShapePoint {
    int x;
    int y;

    public MapPoint(float lat, float lon) {
        this.lat = lat;
        this.lon = lon;
    }
}

class ListData {
    List<ShapePoint> list;
    Color color;
    String filename;
    int size;
    int style;

    ListData(List<ShapePoint> list, Color color, int size, int style) {
        this.list = list;
        this.color = color;
        this.size = size;
        this.style = style;
    }
}