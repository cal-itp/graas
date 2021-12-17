package gtfu;

import java.awt.Color;

public class GPSTrack {
    public GPSTrack(String latLongFile) {
        Display display = new Display(null);
        Shape shape = new Shape("trip");
        TextFile file = new TextFile(latLongFile);

        for (;;) {
            String line = file.getNextLine();
            if (line == null) break;

            String[] arg = line.split(",");

            if (arg.length == 2) {
                shape.add(new ShapePoint(Float.parseFloat(arg[0]), Float.parseFloat(arg[1])));
            } else if (arg.length == 3) {
                shape.add(new ShapePoint(Float.parseFloat(arg[1]), Float.parseFloat(arg[2])));
            } else throw new Fail("unknown file format");
        }

        Debug.log("- shape.getSize(): " + shape.getSize());

        Area paddedArea = Util.padArea(shape.getArea(), 1.2f);

        DisplayList dl = Util.toDisplayList(
            shape.getList(),
            paddedArea,
            display.getWidth(),
            display.getHeight(),
            Color.green,
            DisplayList.STYLE_POINTS_ONLY,
            1
        );

        display.addList(dl);
        display.repaint();
    }

    public static void main(String[] arg) {
        new GPSTrack(arg[0]);
    }
}