import java.io.*;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class Util {
    public static PrivateKey importPrivateKey(String base64) {
        try {
            byte[] buf = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(buf);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static PublicKey importPublicKey(String base64) {
        try {
            byte[] buf = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(buf);
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String sign(String msg, PrivateKey key) {
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(key);
            signer.update(msg.getBytes());
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean verify(String msg, String signature, PublicKey key) {
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initVerify(key);
            signer.update(msg.getBytes());
            return signer.verify(Base64.getDecoder().decode(signature));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getFileAsString(String filename) {
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(filename)));

            for (;;) {
                String line = in.readLine();
                if (line == null) break;

                sb.append(line);
            }

            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}