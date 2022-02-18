package gtfu.tools;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Panel;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import gtfu.*;

public class ShapeBrowser extends Panel implements KeyListener, Runnable {
    private List<Shape> shapeList;
    private String allIDs;
    private Image buf;
    private Area area;
    private int shapeIndex;

    private static final Color[] COLORS = {
        new Color(0xf7f305),
        new Color(0xee228f),
        new Color(0x8820b5),
        new Color(0x0325b4),
        new Color(0x0176c2),
        new Color(0x01add1),
        new Color(0x02b103),
        new Color(0x80c903)
    };

    public ShapeBrowser(String agencyID, String cacheFolder, Set<String> shapeIDs) {
        Debug.log("ShapeBrowser.ShapeBrowser()");
        Debug.log("- agencyID: " + agencyID);
        Debug.log("- cacheFolder: " + cacheFolder);
        Debug.log("- shapeIDs: " + shapeIDs);

        allIDs = "";
        shapeList = new ArrayList<Shape>();
        shapeIndex = -1;
        Frame f = new Frame("Shape Browser");

        f.setSize(640, 480);
        f.setLocation(150, 150);
        f.add(this);
        f.setVisible(true);

        addKeyListener(this);

        AgencyYML yml = new AgencyYML();
        String url = yml.getURL(agencyID);
        Debug.log("- url: " + url);

        Area a = new Area();
        Map<String, Object> collections = Util.loadCollections(cacheFolder, agencyID, new ConsoleProgressObserver(40));
        ShapeCollection shapes = (ShapeCollection)collections.get("shapes");
        BlockCollection blocks = (BlockCollection)collections.get("blocks");

        if (shapeIDs.isEmpty()) {
            shapeIDs = shapes.getKeys();
        }

        synchronized (this) {
            allIDs = shapeIDs.toString();

            for (String id : shapeIDs) {
                Shape s = shapes.get(id);
                shapeList.add(s);

                for (ShapePoint p : s.getList()) {
                    a.update(p);
                }
            }
        }

        area = Util.padArea(a, 1.3f);

        Thread t = new Thread(this);
        t.start();
    }

    public void run() {
        for (;;) {
            repaint();
            Util.sleep(100);
        }
    }

    public void update(Graphics g) {
        if (buf == null || buf.getWidth(null) != getWidth() || buf.getHeight(null) != getHeight()) {
            buf = createImage(getWidth(), getHeight());
        }

        requestFocus();
        paint(buf.getGraphics());
        g.drawImage(buf, 0, 0, null);
    }

    public synchronized void paint(Graphics g) {
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

        ((Graphics2D)g).setStroke(
            new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        );

        int xoff = 0;
        int yoff = 0;

        if (getWidth() > getHeight()) {
            xoff = (getWidth() - getHeight()) / 2;
        } else {
            yoff = (getHeight() - getWidth()) / 2;
        }

        for (int i=0; i<shapeList.size(); i++) {
            if (shapeIndex != -1 && shapeIndex != i) {
                continue;
            }

            Shape shape = shapeList.get(i);
            List<ShapePoint> plist = shape.getList();

            Color c = COLORS[i % COLORS.length];

            g.setColor(Color.lightGray);
            g.drawString(shapeIndex == -1 ? allIDs : shape.getID(), 10, 25);

            for (ShapePoint p : plist) {
                Util.latLongToScreenXY(getWidth(), getHeight(), area, p);
            }

            g.setColor(c);

            for (int j=0; j<plist.size()-1; j++) {
                ShapePoint p1 =plist.get(j);
                ShapePoint p2 =plist.get(j + 1);

                g.drawLine(
                    xoff + p1.screenX,
                    yoff + p1.screenY,
                    xoff + p2.screenX,
                    yoff + p2.screenY
                );
            }
        }
    }

    public void keyPressed(KeyEvent e) {
        int c = e.getKeyChar();

        if (c == 'x') {
            Debug.log("bye...");
            System.exit(0);
        }

        if (c == 'n') {
            //Debug.log("+ next");

            shapeIndex++;

            if (shapeIndex >= shapeList.size()) {
                shapeIndex = -1;
            }

            repaint();
        }

        if (c == 'p') {
            //Debug.log("+ prev");

            shapeIndex--;

            if (shapeIndex < -1) {
                shapeIndex = shapeList.size() - 1;
            }

            repaint();
        }
    }

    public void keyReleased(KeyEvent e) {
    }

    public void keyTyped(KeyEvent e) {
    }

    private static void usage() {
        System.err.println("usage: ShapeBrowser -a <agency-id> -c <cache-folder> <shape-id-1> [<shape-id-2>...]");
        System.exit(1);
    }

    public static void main(String[] arg) throws Exception {
        Set<String> shapeIDs = new HashSet<String>();
        String cacheFolder = null;
        String agencyID = null;

        for (int i=0; i<arg.length; i++) {
            if (arg[i].equals("-a") && i < arg.length - 1) {
                agencyID = arg[++i];
                continue;
            }

            if (arg[i].equals("-c") && i < arg.length - 1) {
                cacheFolder = arg[++i];
                continue;
            }

            shapeIDs.add(arg[i]);
        }

        if (agencyID == null || cacheFolder == null) {
            usage();
        }

        new ShapeBrowser(agencyID, cacheFolder, shapeIDs);
    }
}