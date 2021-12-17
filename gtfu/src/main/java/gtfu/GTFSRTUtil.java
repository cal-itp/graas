package gtfu;

import java.security.PrivateKey;
import java.util.HashMap;
import java.util.Map;

public class GTFSRTUtil {
    private static Map<String, String> HEADER_MAP;

    static {
        HEADER_MAP = new HashMap();
        HEADER_MAP.put("Content-Type", "application/json");
    }
    public static int postAlertFromPrivateKeyFile(String url, Map<String, Object> attributes, String privateKeyFile) {
        PrivateKey pk = CryptoUtil.importPrivateKeyFromFile(privateKeyFile, true);
        return postAlert(url, attributes, pk);
    }

    public static int postAlertFromPrivateKey(String url, Map<String, Object> attributes, String privateKey) {
        PrivateKey pk = CryptoUtil.importPrivateKey(privateKey);
        return postAlert(url, attributes, pk);
    }

    private static int postAlert(String url, Map<String, Object> attributes, PrivateKey pk) {

        Debug.log("- pk.getAlgorithm(): " + pk.getAlgorithm());
        Debug.log("- pk.getFormat(): " + pk.getFormat());

        StringBuilder sb = new StringBuilder("{");
        boolean first = true;

        for (String key : attributes.keySet()) {
            Object value = attributes.get(key);

            if (!first) {
                sb.append(",");
            }

            sb.append("\"");
            sb.append(key);
            sb.append("\":");

            if (value instanceof String) {
                sb.append("\"");
            }

            sb.append(value);

            if (value instanceof String) {
                sb.append("\"");
            }

            first = false;
        }

        sb.append("}");

        String data = sb.toString();
        Debug.log("- data: " + data);

        String sig = CryptoUtil.sign(data, pk, true);
        String msg = String.format("{\"data\":%s,\"sig\":\"%s\"}", data, sig);
        Debug.log("- msg: " + msg);

        return HTTPClient.post(url, msg, HEADER_MAP);
    }
}
