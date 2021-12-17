package gtfu;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class TextFile {
    public static final String UTF8_BOM = "\uFEFF";

    private BufferedReader in;

    public TextFile(File file) {
        this(file.getAbsolutePath());
    }

    public TextFile(String path) {
        try {
            in = new BufferedReader(new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public TextFile(InputStream is) {
        in = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
    }

    public void append(String line) {
        String s = getAllLinesAsString();

        s += line;
        s += '\n';

        in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(s.getBytes()), StandardCharsets.UTF_8));
    }

    public String getNextLine() {
        String s = null;

        try {
            do {
                s = in.readLine();
            } while (s != null && s.trim().length() == 0);

            if (s != null && s.startsWith(UTF8_BOM)) {
                s = s.substring(1);
            }

            return s;
        } catch (IOException e) {
            throw new Fail(e);
        }
    }

    public String getAllLinesAsString() {
        StringBuilder sb = new StringBuilder();

        for (;;) {
            String line = getNextLine();
            if (line == null) break;

            sb.append(line);
            sb.append('\n');
        }

        return sb.toString();
    }

    public List<String> getAllLinesAsList() {
        List<String> list = new ArrayList<String>();

        for (;;) {
            String line = getNextLine();
            if (line == null) break;

            list.add(line);
        }

        return list;
    }

    public void dispose() {
        try {
            in.close();
        } catch (IOException e) {
            throw new Fail(e);
        }
    }
}