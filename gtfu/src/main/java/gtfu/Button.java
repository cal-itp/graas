package gtfu;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;

public class Button {
    public static final int HCENTER_ALIGN = 0;
    public static final int LEFT_ALIGN    = 1;
    public static final int RIGHT_ALIGN   = 2;

    private static final int MARGIN = 8;

    int x;
    int y;
    int width;
    int height;
    String text;
    Color color;
    int alignment;

    public Button(String text, Color color, int x, int y, int width, int height) {
        this.text = new String(text);
        this.color = new Color(color.getRGB());
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        alignment = HCENTER_ALIGN;
    }

    public void setAlignment(int alignment) {
        this.alignment = alignment;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean contains(int xx, int yy) {
        return xx >= x && xx < x + width && yy >= y && yy < y + height;
    }

    public void paint(Graphics g) {
        g.setColor(color);
        g.drawRect(x, y, width, height);

        FontMetrics fm = g.getFontMetrics();
        int sw = fm.stringWidth(text);
        int yoff = MARGIN / 2;

        if (alignment == LEFT_ALIGN) {
            GraphicsUtil.drawString(g, text, x + MARGIN, y + yoff);
        } else if (alignment == HCENTER_ALIGN) {
            GraphicsUtil.drawString(g, text, x + (width - sw) / 2, y + yoff);
        } else {
            GraphicsUtil.drawString(g, text, x + width - MARGIN - sw, y + yoff);
        }
    }
}