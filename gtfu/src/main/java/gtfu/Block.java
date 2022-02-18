package gtfu;

import java.util.ArrayList;
import java.util.List;

public class Block {
    public String id;
    public List<String> tripIDs;

    public Block(String id) {
        this.id = id;
        this.tripIDs = new ArrayList<String>();
    }

    public void addTrip(String tripID) {
        tripIDs.add(tripID);
    }
}