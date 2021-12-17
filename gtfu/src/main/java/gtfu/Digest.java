package gtfu;

import java.io.Serializable;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.security.MessageDigest;

public class Digest implements Serializable {
    private transient MessageDigest md;
    private String hash;

    public Digest() {
        this(true);
    }

    public Digest(boolean needsMessageDigest) {
        if (needsMessageDigest) {
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (Exception e) {
                throw new Fail(e);
            }
        }
    }

    public void write(DataOutputStream out) {
        //Debug.log("Trip.write()");

        try {
            out.writeUTF(hash);
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public static Digest fromStream(DataInputStream in) {
        try {
            Digest d = new Digest(false);

            d.hash = in.readUTF();

            return d;
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public void update(Object o) {
        if (hash != null) {
            throw new IllegalStateException("can't update after generating hash");
        }

        md.update(o.toString().getBytes());
    }

    public void update(int n) {
        update(String.valueOf(n));
    }

    public String getHash() {
        if (hash == null) {
            hash = Util.bytesToHexString(md.digest());
        }

        return hash;
    }
}