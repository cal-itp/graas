package gtfu;

import java.awt.Graphics;

public class GeoLabel {
    private static final int UPDATE_MILLIS = 1000;

    private String[] text;
    private long lastTextUpdate;
    private Trip trip;
    private GeoObject target;
    private ShapePoint p;

    public GeoLabel(GeoObject target) {
        this.target = target;

        p = new ShapePoint();
    }

    public String[] getText() {
        return text;
    }

    public GeoObject getTarget() {
        return target;
    }

    public ShapePoint getShapePoint() {
        return p;
    }

    private String[] updateText() {
        if (Util.now() - UPDATE_MILLIS > lastTextUpdate) {
            text = target.getLabelText();
            lastTextUpdate = Util.now();
        }

        return text;
    }

    public void paint(Graphics g, Display display, Area area) {
        p.lat = target.getLat();
        p.lon = target.getLong();
        Util.latLongToScreenXY(display, area, p);
        display.paintLabel(g, updateText(), (int)p.screenX, (int)p.screenY);
    }
}