package gtfu;

import java.util.ArrayList;
import java.util.List;

public class CSVRecord {
    private String[] split;
    private CSVHeader header;

    public CSVRecord(CSVHeader header, String s) {
        this.header = header;
        split = split(s);
    }

    public int getLength() {
        return split.length;
    }

    public String get(int index) {
        return Util.stripQuotes(split[index]);
    }

    public String get(String key) {
        int index = header.getIndex(key);
        if (index < 0) return null;
        return Util.stripQuotes(split[index]);
    }

    public int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    public long getLong(String key) {
        return Long.parseLong(get(key));
    }

    public int getHex(String key) {
        return Integer.parseInt(get(key), 16);
    }

    public float getFloat(String key) {
        return Float.parseFloat(get(key));
    }

    private static Token parseToken(String s, int from) {
        Token token = new Token();
        int i = from;
        char c = '\0';

        for (;;) {
            if (i >= s.length()) break;

            c = s.charAt(i++);
            if (c == ',') break;
        }

        if (c != ',' && i < s.length()) throw new Fail("can't parse '" + s + "'");
        token.s = s.substring(from, c == ',' ? i - 1 : i);
        token.to = i;

        return token;
    }

    private static Token parseString(String s, int from) {
        Token token = new Token();
        int i = from;
        char c = '\0';
        StringBuilder sb = new StringBuilder();

        for (;;) {
            if (i >= s.length()) break;

            c = s.charAt(i++);

            if (c == '"') {
                if (i < s.length() && s.charAt(i) == '"') {
                    i++;
                } else break;
            }

            sb.append(c);
        }

        if (c != '"') throw new Fail("can't parse '" + s + "'");
        token.s = sb.toString();
        token.to = i + 1;

        return token;
    }

    public static String[] split(String s) {
        //Debug.log("CSVRecord.split()");
        //Debug.log("- s: " + s);

        List<String> list = new ArrayList<String>();
        int i = 0;
        char c = '\0';
        char lastC = '\0';

        while (i < s.length()) {
            Token token;
            c = s.charAt(i);
            //Debug.log("-- c: " + c);
            //Debug.log("-- i: " + i);

            if (c == '"') {
                token = parseString(s, i + 1);
                list.add(token.s);
                i = token.to;
            } else if (c == ',') {
                if (i - 1 < 0 || s.charAt(i - 1) == ',') {
                    list.add("");
                }

                i++;
            } else {
                token = parseToken(s, i);
                list.add(token.s);
                i = token.to;
            }

            lastC = c;
            //Debug.log("list " + list);
        }

        if (c == ',') list.add("");

        String[] ret = new String[list.size()];
        return list.toArray(ret);
    }
}

class Token {
    String s;
    int to;
}
