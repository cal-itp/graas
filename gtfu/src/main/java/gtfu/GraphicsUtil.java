package gtfu;

import java.awt.FontMetrics;
import java.awt.Graphics;

public class GraphicsUtil {
    private static void drawPixel(int[] buf, int width, int x, int y, float fraction, int argb) {
        //Debug.log("- fraction: " + fraction);
        //Debug.log("- argb: " + Util.toHex(argb));

        float rf = 1 - fraction;
        int index = y * width + x;
        int c = buf[index];
        //Debug.log("-    c: " + Util.toHex(c));

        int a1 = (c & 0xff000000) >>> 24;
        int r1 = (c & 0x00ff0000) >>> 16;
        int g1 = (c & 0x0000ff00) >>>  8;
        int b1 = (c & 0x000000ff) >>>  0;

        int a2 = (argb & 0xff000000) >>> 24;
        int r2 = (argb & 0x00ff0000) >>> 16;
        int g2 = (argb & 0x0000ff00) >>>  8;
        int b2 = (argb & 0x000000ff) >>>  0;

        int a3 = (int)Math.rint(rf * a1 + fraction * a2);
        int r3 = (int)Math.rint(rf * r1 + fraction * r2);
        int g3 = (int)Math.rint(rf * g1 + fraction * g2);
        int b3 = (int)Math.rint(rf * b1 + fraction * b2);

        int nc = (a3 << 24) | (r3 << 16) | (g3 << 8) | b3;
        //Debug.log("-   nc: " + Util.toHex(nc));

        buf[index] = nc;
    }

    private static int intPart(float f) {
        return (int)Math.floor(f);
    }

    private static float fractionalPart(float f) {
        return f - (float)Math.floor(f);
    }

    public static void drawString(Graphics g, String s, int x, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, x, y + fm.getAscent());
    }

    public static void drawLine(int[] buf, int width, int x0, int y0, int x1, int y1, int argb) {
        boolean steep = Math.abs(y1 - y0) > Math.abs(x1 - x0);

        if (steep) {
            int t = x0;
            x0 = y0;
            y0 = t;

            t = x1;
            x1 = y1;
            y1 = t;
        }

        if (x0 > x1) {
            int t = x0;
            x0 = x1;
            x1 = t;

            t = y0;
            y0 = y1;
            y1 = t;
        }

        float dx = x1 - x0;
        float dy = y1 - y0;
        float gradient = dy / dx;
        if (dx == 0) gradient = 1;

        int xpxl1 = x0;
        int xpxl2 = x1;
        float intersectY = y0;

        if (steep) {
            int x;
            for (x = xpxl1 ; x <=xpxl2 ; x++) {
                drawPixel(buf, width, intPart(intersectY), x,
                    1 - fractionalPart(intersectY), argb);
                drawPixel(buf, width, intPart(intersectY) + 1, x,
                    fractionalPart(intersectY), argb);
                intersectY += gradient;
            }
        }
        else {
            int x;
            for (x = xpxl1 ; x <=xpxl2 ; x++) {
                drawPixel(buf, width, x, intPart(intersectY),
                    1 - fractionalPart(intersectY), argb);
                drawPixel(buf, width, x, intPart(intersectY) + 1,
                    fractionalPart(intersectY), argb);
                intersectY += gradient;
            }
        }
    }
}