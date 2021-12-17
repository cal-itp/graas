package gtfu;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Stroke;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.geom.Rectangle2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.awt.image.BufferStrategy;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class Display extends Frame implements KeyListener, MouseListener, MouseMotionListener, Runnable {
    private static final float ROTATION_DELTA = .03f;
    private static final int SLEEP_MILLIS = 33;
    private static final Color labelBG = new Color(0xc0000000, true);

    private List<DisplayList> list;
    private UIEventListener listener;
    private BufferStrategy strategy;
    private Image pathCache;
    private Drawable overlay;
    private BasicStroke stroke;
    private Font font;
    private float rotation;
    private float rotationStep;
    private float scaleFactor;
    private boolean usePathCache;

    public Display(UIEventListener listener) {
        this(listener, true);
    }

    public Display(UIEventListener listener, boolean usePathCache) {
        super("Display");

        this.usePathCache = usePathCache;
        this.listener = listener;
        list = new ArrayList<DisplayList>();

        setScaleFactor(2);
        font = new Font("Arial", Font.PLAIN, 11);
        setSize(400 * 3, 200 * 3);
        setVisible(true);
        createBufferStrategy(2);
        strategy = getBufferStrategy();

        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);

        Thread t = new Thread(this);
        t.start();
    }

    public void setOverlay(Drawable overlay) {
        this.overlay = overlay;
    }

    public synchronized void addList(DisplayList l) {
        list.add(l);
    }

    public synchronized void removeList(DisplayList l) {
        list.remove(l);
    }

    public synchronized void removeAllLists() {
        list.clear();
    }

    public synchronized void resetPathCache() {
        pathCache = null;
    }

    public synchronized void setScaleFactor(float scaleFactor) {
        this.scaleFactor = scaleFactor;
        stroke = new BasicStroke(scaleFactor, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        resetPathCache();
    }

    public void run() {
        for (;;) {
            long then = Util.now();
            repaint();
            int delta = (int)(Util.now() - then);
            //Debug.log("- delta: " + delta);

            if (delta < SLEEP_MILLIS) {
                Util.sleep(SLEEP_MILLIS - delta);
            } else {
                Thread.yield();
            }
        }
    }

    public void repaint() {
        do {
            do {
                Graphics graphics = strategy.getDrawGraphics();
                paint(graphics);
                graphics.dispose();
            } while (strategy.contentsRestored());

            strategy.show();
        } while (strategy.contentsLost());
    }

    public void paintLabel(Graphics g, String[] s, int x, int y) {
        g.setFont(font);

        int cx = x + 50;
        int cy = y - 30;

        FontMetrics fm = g.getFontMetrics();
        int sh = s.length * fm.getAscent();
        int r = 2;

        int sw = Integer.MIN_VALUE;

        for (int i=0; i<s.length; i++) {
            sw = Math.max(sw, fm.stringWidth(s[i]));
        }

        //g.setColor(Color.red);
        //g.fillOval(cx - 1, cy - r, 2 * r, 2 * r);

        //int bx = cx - sw / 2 - 2;
        //int by = cy - sh / 2 - 2;
        int bx = cx;
        int by = cy;
        int bw = sw + 4;
        int bh = sh + 4 + (s.length - 1);

        g.setColor(labelBG);
        g.fillRect(bx - 1, by - 1, bw + 3, bh + 3);

        g.setColor(Color.lightGray);

        //g.drawString(s[0], cx + 2, cy + sh + 2);
        for (int i=0; i<s.length; i++) {
            g.drawString(s[i], bx + 2, by + (i + 1) * font.getSize());
        }

        g.drawRect(bx, by, bw, bh);
        g.drawLine(x, y, bx, by + bh / 2);
    }

    public void paint(Graphics g) {
        Insets insets = getInsets();

        Graphics2D g2d = (Graphics2D)g;

        g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        );

        synchronized(this) {
            if (pathCache == null || !usePathCache) {
                pathCache = createImage(getWidth(), getHeight());
                Graphics cacheG = pathCache.getGraphics();
                Graphics2D cacheG2d = (Graphics2D)cacheG;

                cacheG.setColor(Color.black);
                cacheG.fillRect(0, 0, getWidth(), getHeight());

                //Debug.log("- list.size(): " + list.size());

                Stroke saved = cacheG2d.getStroke();
                cacheG2d.setStroke(stroke);

                cacheG2d.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
                );

                for (DisplayList l : list) {
                    l.paint(cacheG, scaleFactor);
                }

                //g.setColor(Color.red);
                //g.drawLine(0, insets.top, 0, insets.top + 2);

                cacheG2d.setStroke(saved);
            }
        }

        int tx = (int)Math.round(getWidth() / 2);
        int ty = (int)Math.round(getHeight() / 2);

        rotation += rotationStep;

        //g2d.translate(tx, ty);
        g2d.rotate(rotation);

        if (pathCache != null) {
            //g.drawImage(pathCache, insets.left, insets.top, null);
            g.drawImage(pathCache, 0, 0, null);
        }

        int offset = 3;
        g.setColor(Color.lightGray);
        g.drawRect(
            insets.left + offset,
            insets.top + offset,
            getWidth() - insets.left - insets.right - 2 * offset,
            getHeight() - insets.top - insets.bottom - 2 * offset
        );

        /*Color c = new Color((Color.red.getRGB() & 0xffffff) | 0x80000000, true);
        g.setColor(c);
        g.fillRect(200, 200, 200, 200);*/

        if (overlay != null) {
            overlay.paint(g);
        }
    }

    public void keyPressed(KeyEvent e) {
        //Debug.log("Display.keyPressed()");
        //Debug.log("- e.getKeyChar(): " + e.getKeyChar());

        int code = e.getKeyCode();

        if (code == KeyEvent.VK_X) {
            System.out.println("bye...");
            System.exit(1);
        } else if (code == KeyEvent.VK_Q) {
            //rotationStep = -ROTATION_DELTA;
        } else if (code == KeyEvent.VK_W) {
            //rotationStep = ROTATION_DELTA;
        }

        listener.keyPressed(e);
    }

    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();

        if (code == KeyEvent.VK_Q) {
            if (rotationStep == -ROTATION_DELTA) {
                rotationStep = 0;
            }
        } else if (code == KeyEvent.VK_W) {
            if (rotationStep == ROTATION_DELTA) {
                rotationStep = 0;
            }
        }

        listener.keyReleased(e);
    }

    public void keyTyped(KeyEvent e) {
    }

    public void mouseClicked(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    public void mousePressed(MouseEvent e) {
        listener.mousePressed(e);
    }

    public void mouseReleased(MouseEvent e) {
        listener.mouseReleased(e);
    }

    public void mouseMoved(MouseEvent e) {
    }

    public void mouseDragged(MouseEvent e) {
        listener.mouseDragged(e);
    }
}