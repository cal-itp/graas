package gtfu.tools;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.text.SimpleDateFormat;
import gtfu.*;

/*
 * log munging tool for Google App Engine exported CSV logs. How to use:
 * - go to https://console.cloud.google.com/projectselector2/logs/query (select project if necessary)
 * - select time range
 * - press download button in query results panel, choose CSV format
 * - run this tool on downloaded file
 */
public class AppEngineLogMunger {
    private List<String> lines;

    public AppEngineLogMunger(String filename) throws Exception {
        lines = new ArrayList<String>();

        TextFile tf = new TextFile(filename);
        CSVHeader header = new CSVHeader(tf.getNextLine());
        SimpleDateFormat sdfp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        SimpleDateFormat sdff = new SimpleDateFormat("HH:mm:ss.SSS");

        for (;;) {
            String line = tf.getNextLine();
            if (line == null) break;

            CSVRecord r = new CSVRecord(header, line);
            String ts = r.get("timestamp");

            // truncate timestamp so SimpleDateFormat doesn't get confused
            Date date = sdfp.parse(ts.substring(0, 23));
            String text = r.get("textPayload");
            String s = String.format("%s %s", sdff.format(date), text);

            lines.add(0, s);
        }

        for (String s : lines) {
            System.out.println(s);
        }
    }

    public List<String> getLines() {
        return lines;
    }

    private static void usage() {
        System.err.println("usage: AppEngineLogMunger <csv-log-file>");
        System.exit(0);
    }

    public static void main(String[] arg) throws Exception {
        if (arg.length < 1) {
            usage();
        }

        new AppEngineLogMunger(arg[0]);
    }
}
