package com.example.gpstest;

import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class CryptoUtil {
    private static final String PEM_PRIV_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_PRIV_FOOTER = "-----END PRIVATE KEY-----";
    private static final String PEM_PUB_HEADER = "-----BEGIN RSA PUBLIC KEY-----";
    private static final String PEM_PUB_FOOTER = "-----END RSA PUBLIC KEY-----";

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static PrivateKey importPrivateKeyFromPEM(String pem) {
        int i1 = pem.indexOf(PEM_PRIV_HEADER) + PEM_PRIV_HEADER.length();
        int i2 = pem.indexOf(PEM_PRIV_FOOTER);
        String b64 = pem.substring(i1, i2);
        //Debug.log("- b64: " + b64);
        //Debug.log("- b64.length(): " + b64.length());

        return importPrivateKey(b64);
    }

    /*@RequiresApi(api = Build.VERSION_CODES.O)
    public static PublicKey importPublicKeyFromFile(String path) {
        String pem = Util.getFileAsString(path);
        //Debug.log("- pem: " + pem);

        int i1 = pem.indexOf(PEM_PUB_HEADER) + PEM_PUB_HEADER.length();
        int i2 = pem.indexOf(PEM_PUB_FOOTER);
        String b64 = pem.substring(i1, i2);
        //Debug.log("- b64: " + b64);
        Debug.log("- b64.length(): " + b64.length());

        return importPublicKey(b64);
    }*/

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static PrivateKey importPrivateKey(String base64) {
        try {
            byte[] buf = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(buf);
            return KeyFactory.getInstance(base64.length() < 250 ? "EC" : "RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static PublicKey importPublicKey(String base64) {
        try {
            byte[] buf = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(buf);
            return KeyFactory.getInstance(base64.length() < 250 ? "EC" : "RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String hash(String s) {
        Digest digest = new Digest();
        digest.update(s);
        return digest.getHash();
    }

    private static byte[] derToRaw(byte[] buf) {
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

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static String sign(String msg, PrivateKey key) {
        return sign(msg, key, false);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static String sign(String msg, PrivateKey key, boolean rawOutput) {
        try {
            Signature signer = Signature.getInstance("SHA256with" + (key.getAlgorithm().equals("EC") ? "ECDSA" : "RSA"));
            signer.initSign(key);
            byte[] encoded = msg.getBytes(StandardCharsets.UTF_8);
            signer.update(encoded);
            byte[] buf = signer.sign();

            if (rawOutput) {
                buf = derToRaw(buf);
            }

            Debug.log("- buf.length: " + buf.length);

            return Base64.getEncoder().encodeToString(buf);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static boolean verify(String msg, String signature, PublicKey key) {
        try {
            Signature signer = Signature.getInstance("SHA256with" + (key.getAlgorithm().equals("EC") ? "ECDSA" : "RSA"));
            signer.initVerify(key);
            signer.update(msg.getBytes());
            return signer.verify(Base64.getDecoder().decode(signature));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}