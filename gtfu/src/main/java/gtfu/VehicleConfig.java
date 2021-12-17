package gtfu;

import java.security.PrivateKey;

public class VehicleConfig {
    String url;
    String agencyID;
    String id;
    String tripID;
    PrivateKey key; // key for signing messages

    public VehicleConfig(String url, String agencyID, String id, String tripID, PrivateKey key) {
        this.url = url;
        this.agencyID = agencyID;
        this.id = id;
        this.tripID = tripID;
        this.key = key;
    }
}
