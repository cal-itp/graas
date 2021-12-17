package gtfu;

public interface LatLongConverter {
    public void latLongToScreenXY(int displayWidth, int displayHeight, Area area, ShapePoint p);
}