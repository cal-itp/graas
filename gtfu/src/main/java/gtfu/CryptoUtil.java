package gtfu;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Signature;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class CryptoUtil {
    private static final String PEM_PRIV_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_PRIV_FOOTER = "-----END PRIVATE KEY-----";
    private static final String PEM_GRAAS_HEADER = "-----BEGIN TOKEN-----";
    private static final String PEM_GRAAS_FOOTER = "-----END TOKEN-----";
    private static final String PEM_PUB_HEADER = "-----BEGIN RSA PUBLIC KEY-----";
    private static final String PEM_PUB_FOOTER = "-----END RSA PUBLIC KEY-----";

    static SecureRandom rnd = new SecureRandom();

    public static PrivateKey importPrivateKeyFromFile(String path) {
        return importPrivateKeyFromFile(path, false);
    }

    public static PrivateKey importPrivateKeyFromFile(String path, boolean graasWrapper) {
        String pem = Util.getFileAsString(path);
        //Debug.log("- pem: " + pem);

        String header = graasWrapper ? PEM_GRAAS_HEADER : PEM_PRIV_HEADER;
        String footer = graasWrapper ? PEM_GRAAS_FOOTER : PEM_PRIV_FOOTER;

        int i1 = pem.indexOf(header) + header.length();
        int i2 = pem.indexOf(footer);
        String b64 = pem.substring(i1, i2);
        //Debug.log("- b64: " + b64);
        //Debug.log("- b64.length(): " + b64.length());

        return importPrivateKey(b64);
    }

    public static PublicKey importPublicKeyFromFile(String path) {
        String pem = Util.getFileAsString(path);
        //Debug.log("- pem: " + pem);

        int i1 = pem.indexOf(PEM_PUB_HEADER) + PEM_PUB_HEADER.length();
        int i2 = pem.indexOf(PEM_PUB_FOOTER);
        String b64 = pem.substring(i1, i2);
        //Debug.log("- b64: " + b64);
        Debug.log("- b64.length(): " + b64.length());

        return importPublicKey(b64);
    }

    public static PrivateKey importPrivateKey(String base64) {
        try {
            byte[] buf = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(buf);
            ECPrivateKey key = (ECPrivateKey)KeyFactory.getInstance(base64.length() < 250 ? "EC" : "RSA").generatePrivate(spec);
            return key;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static PublicKey importPublicKey(String base64) {
        try {
            Debug.log("- base64: " + base64);
            byte[] buf = Base64.getDecoder().decode(base64);
            //PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(buf);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(buf);
            return KeyFactory.getInstance(base64.length() < 250 ? "EC" : "RSA").generatePublic(spec);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static String hash(String s) {
        Digest digest = new Digest();
        digest.update(s);
        return digest.getHash();
    }

    private static byte[] derToRawSig(byte[] buf) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        // byte 0: 0x30
        // byte 1: overall length of sig data
        // byte 2: 0x02
        // byte 3: length of r (0x20 if high byte <= 0x7f, 0x21 otherwise)
        // byte 4..n: r
        // byte n + 1: 0x02
        // byte n + 2: length of s (0x20 if high byte <= 0x7f, 0x21 otherwise)
        // byte n + 3..m: s

        int offset = buf[3] == 32 ? 4 : 5;
        bos.write(buf, offset, 32);

        int offset2 = offset + 32 + (buf[offset + 32 + 1] == 32 ? 2 : 3);
        bos.write(buf, offset2, 32);

        return bos.toByteArray();
    }

    private static byte[] rawToDerSig(byte[] buf) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        bos.write((byte)0x30);

        int rlen = 32;
        if (buf[ 0] > 0x7f) rlen++;

        int slen = 32;
        if (buf[32] > 0x7f) slen++;

        int len = rlen + slen + 4;

        bos.write((byte)len);
        bos.write((byte)0x02);
        bos.write((byte)rlen);
        bos.write(buf, 0, 32);
        bos.write((byte)0x02);
        bos.write((byte)slen);
        bos.write(buf, 32, 32);

        return bos.toByteArray();
    }

    public static String sign(String msg, PrivateKey key) {
        return sign(msg, key, false);
    }

    public static String sign(String msg, PrivateKey key, boolean rawOutput) {
        try {
            Signature signer = Signature.getInstance("SHA256with" + (key.getAlgorithm().equals("EC") ? "ECDSA" : "RSA"));
            signer.initSign(key);
            signer.update(msg.getBytes(StandardCharsets.UTF_8));
            byte[] buf = signer.sign();
            //Debug.log("- buf.length: " + buf.length);
            //Debug.log("+ buf: " + Util.bytesToHexString(buf));

            if (rawOutput) {
                buf = derToRawSig(buf);
                //Debug.log("- buf.length: " + buf.length);
                //Debug.log("+ buf: " + Util.bytesToHexString(buf));
            }

            return Base64.getEncoder().encodeToString(buf);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean verify(String msg, String signature, PublicKey key) {
        try {
            Signature signer = Signature.getInstance("SHA256with" + (key.getAlgorithm().equals("EC") ? "ECDSA" : "RSA"));
            signer.initVerify(key);
            signer.update(msg.getBytes(StandardCharsets.UTF_8));
            byte[] buf = Base64.getDecoder().decode(signature);
            Debug.log("- buf.length: " + buf.length);

            if (buf.length == 64) {
                buf = rawToDerSig(buf);
                Debug.log("+ sig: " + Base64.getEncoder().encodeToString(buf));
            }

            return signer.verify(buf);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] arg) {
        String cmd = arg[0];

        if (cmd.equals("-sign")) {
            // ### TODO add cmd line arg to switch between standard and graas wrapper
            System.out.println("message: " + arg[1]);
            System.out.println("sig: " + sign(arg[1], importPrivateKeyFromFile(arg[2], true), true));
        } else if (cmd.equals("-verify")) {
            System.out.println("message: " + arg[1]);
            System.out.println(verify(arg[1], arg[2], importPublicKey(arg[3])));
        } else if (cmd.equals("-test")) {
            System.out.println("message: " + arg[1]);
            String sig = sign(arg[1], importPrivateKeyFromFile(arg[2], false));
            System.out.println("sig: " + sig);
            System.out.println(verify(arg[1], sig, importPublicKey(arg[3])));
        }
    }
}