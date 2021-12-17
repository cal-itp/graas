package gtfu;

// describe a geographic area in terms of
// bounding lat/long coordinates
public class GeoBounds {
    public static final GeoBounds CALIFORNIA = new GeoBounds(42.000000f, 32.528556f, -124.392122f, -114.152865f);

    public GeoBounds(float north, float south, float west, float east) {
        this.north = north;
        this.south = south;
        this.west = west;
        this.east = east;
    }

    final float north;
    final float south;
    final float west;
    final float east;
}