package gtfu;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

public class DisplayList {
    public static final int STYLE_POINTS_ONLY = 0;
    public static final int STYLE_CONNECTED_POINTS = 1;
    public static final int STYLE_OUTLINE = 2;
    public static final int STYLE_UNSCALED_POINTS = 3;
    public static final int STYLE_OUTLINED_UNSCALED_POINTS = 4;
    public static final int STYLE_ANIMATE_PROGRESS = 5;

    Ellipse2D.Double ellipse;
    Color color;
    int style;
    int radius;
    List<Point> list;
    Path2D.Float path;
    Object arg;

    public DisplayList(Color color, int style, int radius) {
        if (style == STYLE_POINTS_ONLY) {
            this.color = color;
        } else {
            this.color = new Color((color.getRGB() & 0xffffff) | 0x80000000, true);
        }

        this.style = style;
        this.radius = radius;

        list = new ArrayList<Point>();
        path = new Path2D.Float();
        ellipse = new Ellipse2D.Double();
    }

    public void addPoint(float x, float y) {
        if (list.size() == 0) {
            path.moveTo(x, y);
        } else {
            path.lineTo(x, y);
        }

        list.add(new Point((int)x, (int)y));
    }

    public Point get(int i) {
        return list.get(i);
    }

    public int size() {
        return list.size();
    }

    public void paint(Graphics g, float scaleFactor) {
        paint(g, color, scaleFactor);
    }

    public void paint(Graphics g, Color c, float scaleFactor) {
        //Debug.log("- scaleFactor: " + scaleFactor);
        if (style == STYLE_CONNECTED_POINTS) {
            g.setColor(c);
            ((Graphics2D)g).draw(path);
        } else if (style == STYLE_POINTS_ONLY || style == STYLE_UNSCALED_POINTS || style == STYLE_OUTLINED_UNSCALED_POINTS) {
            float r = radius;
            float s = r * 2;

            if (scaleFactor != 1 && style == STYLE_POINTS_ONLY) {
                s = Math.max(4, 1.5f * scaleFactor);
                r = s / 2f;
            }

            g.setColor(c);

            for (Point p : list) {
                ellipse.x = p.x - r;
                ellipse.y = p.y - r;
                ellipse.width = s;
                ellipse.height = s;

                if (style == STYLE_OUTLINED_UNSCALED_POINTS) {
                    ((Graphics2D)g).draw(ellipse);
                } else {
                    ((Graphics2D)g).fill(ellipse);
                }
            }
        }
    }

    public void paint(int[] buf, int width) {
        int rgb = color.getRGB();

        for (int i=1; i<list.size(); i++) {
            Point p1 = list.get(i - 1);
            Point p2 = list.get(i);

            GraphicsUtil.drawLine(buf, width, p1.x, p1.y, p2.x, p2.y, rgb);
        }
    }

    public String toString() {
        StringBuffer sb = new StringBuffer("display list");

        for (Point p : list) {
            sb.append(String.format("(%d, %d) ", p.x, p.y));
        }

        return sb.toString();
    }
}
