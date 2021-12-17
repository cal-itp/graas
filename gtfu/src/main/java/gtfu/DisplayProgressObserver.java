package gtfu;

import java.awt.Color;
import java.awt.Graphics;

public class DisplayProgressObserver implements ProgressObserver {
    private static final int OFFSET = 3;

    private int x;
    private int y;
    private int width;
    private int height;
    private int value;
    private int max;
    private Color color;
    private float fractionalX;

    public DisplayProgressObserver(int x, int y, int width, int height, Color color) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.color = color;
    }

    public void setMax(int max) {
        this.max = max;
        fractionalX = 0;
    }

    public void update(int value) {
        this.value = value;
    }

    public void tick() {
        value++;
    }

    public void paint(Graphics g) {
        g.setColor(color);
        g.drawRect(x - OFFSET, y - OFFSET, width + 2 * OFFSET , height + 2 * OFFSET - 1);


        if (max > 0) {
            float fraction = value / (float)max;
            int ww = (int)(width * fraction);
            g.fillRect(x, y, ww , height);
        } else {
            int ww = width / 5;
            g.fillRect((int)(x + fractionalX), y, ww , height);

            fractionalX += .1;

            if (fractionalX >= width) {
                fractionalX = 0;
            }
        }
    }
}