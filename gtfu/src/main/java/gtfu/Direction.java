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

        this.routeDirectionID = routeDirectionID;
        this.routeID = routeID;
        this.directionID = directionID;
        this.name = name;
    }

    public String getRouteDirectionID() {
        return routeDirectionID;
    }

    public String getName() {
        return name;
    }
}