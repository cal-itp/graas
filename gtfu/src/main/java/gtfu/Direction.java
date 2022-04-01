package gtfu;

import java.io.Serializable;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class Direction implements Serializable {
    String routeDirectionID;
    String routeID;
    String directionID;
    String name;

    public Direction(String routeDirectionID, String routeID, String directionID, String name) {
        this();

        this.routeDirectionID = routeDirectionID;
        this.routeID = routeID;
        this.directionID = directionID;
        this.name = name;
    }

    private Direction() {
    }

    public void write(DataOutputStream out) {
        try {
            out.writeUTF(routeDirectionID);
            out.writeUTF(routeID);
            out.writeUTF(directionID);
            out.writeUTF(name);

        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public static Direction fromStream(DataInputStream in) {
        try {
            Direction d = new Direction();

            d.routeDirectionID = in.readUTF();
            d.routeID = in.readUTF();
            d.directionID = in.readUTF();
            d.name = in.readUTF();

            return d;
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public String getRouteDirectionID() {
        return routeDirectionID;
    }

    public String getName() {
        return name;
    }
}