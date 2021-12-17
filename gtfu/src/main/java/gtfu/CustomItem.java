package gtfu;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class CustomItem implements Filterable {
    public static final String KEY = "custom_item: ";
    private static final String NAME_KEY = "name=";
    private static final String ID_KEY = "trip_id=";
    private static final String COLOR_KEY = "color=";
    private static final String POINTS_KEY = "points=";

    private String tripID;
    private String name;
    private Color color;
    private List<ShapePoint> list;

    // s must be of the form custom_item: name=<name> trip_id=<trip_id> points=(<lat>,<long>)|(<lat>,<long>)|...
    public CustomItem(String s) {
        //Debug.log("CustomItem.CustomItem()");
        //Debug.log("- s: " + s);

        if (!s.startsWith(KEY)) {
            throw new Fail("invalid custom item string: " + s);
        }

        s = s.substring(KEY.length());

        String[] tok = s.split(" ");

        for (String t : tok) {
            if (t.startsWith(NAME_KEY)) {
                name = t.substring(NAME_KEY.length());
            }

            if (t.startsWith(ID_KEY)) {
                tripID = t.substring(ID_KEY.length());
            }

            if (t.startsWith(COLOR_KEY)) {
                color = new Color(Integer.parseInt(t.substring(COLOR_KEY.length()), 16));
            }

            if (t.startsWith(POINTS_KEY)) {
                list = Util.parsePoints(t.substring(POINTS_KEY.length()));
            }
        }

        Debug.log("- name: " + name);
        Debug.log("- color: " + Integer.toHexString(color.getRGB()));
        Debug.log("- tripID: " + tripID);
        Debug.log("- list: " + list);
    }

    public Color getColor() {
        return color;
    }

    public String getName() {
        return name;
    }

    public List<ShapePoint> getList() {
        return list;
    }

    public boolean matches(String key, String value) {
        if (key.equals(Filterable.TRIP)) {
            return value == null || tripID == null || tripID.equals(value);
        }

        return false;
    }
}
