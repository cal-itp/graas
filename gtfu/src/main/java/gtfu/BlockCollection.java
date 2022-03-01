package gtfu;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public class BlockCollection implements Iterable<Block> {
    private Map<String, Block> map;
    public List<Block> list;

    public BlockCollection(TripCollection tripCollection) {
        map = new HashMap<String, Block>();
        list = new ArrayList<Block>();

        for (Trip trip : tripCollection) {
            String bid = trip.getBlockID();
            String tid = trip.getID();

            if (Util.isEmpty(bid)) {
                continue;
            }

            Block block = map.get(bid);

            if (block == null) {
                block = new Block(bid);

                map.put(bid, block);
                list.add(block);
            }

            block.addTrip(tid);
        }
    }

    public Block get(int index) {
        return list.get(index);
    }

    public Block get(String id) {
        return map.get(id);
    }

    public int getSize() {
        return list.size();
    }

    public void forEach(Consumer<? super Block> action) {
        list.forEach(action);
    }

    public Iterator<Block> iterator() {
        return list.iterator();
    }

    public Spliterator<Block> spliterator() {
        return list.spliterator();
    }
}