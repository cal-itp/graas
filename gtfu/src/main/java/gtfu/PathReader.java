package gtfu;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class PathReader {
    private static final int MOVE_TO = 0;
    private static final int LINE_TO = 1;
    private static final int CLOSE = 2;

    private List<List<Point>> list;

    public PathReader(String filename) {
        if (!filename.toLowerCase().endsWith(".svg")) {
            throw new Fail("only .svg files are currently supported");
        }

        list = new ArrayList<List<Point>>();
        Pattern pattern = Pattern.compile(".*[ \t]+d=\"(.*?)\".*");
        TextFile tf = new TextFile(filename);

        for (;;) {
            String line = tf.getNextLine();
            if (line == null) break;

            //Debug.log("-- line: " + line);
            Matcher matcher = pattern.matcher(line);
            if (!matcher.matches()) continue;

            String m = matcher.group(1);
            //Debug.log("- m: " + m);

            list.add(parsePath(new TokenStream(m)));
        }
    }

    private List<Point> parsePath(TokenStream stream) {
        List<Point> l = new ArrayList<Point>();
        String token = null;
        int cmd = -1;
        int x = 0;
        int y = 0;

        for (;;) {
            token = stream.get();

            if (token.equals("M")) {
                cmd = MOVE_TO;
            } else if (token.equals("l")) {
                cmd = LINE_TO;
            } else if (token.equals("z")) {
                cmd = CLOSE;
                break;
            } else {
                String[] arg = token.split(",");
                int xx = Integer.parseInt(arg[0]);
                int yy = Integer.parseInt(arg[1]);

                if (cmd == MOVE_TO) {
                    x = xx;
                    y = yy;

                    l.add(new Point(x, y));
                } else if (cmd == LINE_TO) {
                    x += xx;
                    y += yy;

                    l.add(new Point(x, y));
                }
            }
        }

        Debug.log("- l.size(): " + l.size());

        return l;
    }

    public List<List<Point>> getPaths() {
        return list;
    }
}