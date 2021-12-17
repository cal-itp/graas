package gtfu;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class GTFSNow {
    private static final String URL_ARG = "-url";
    private static final String ZIP_ARG = "-zip-name";
    private static final String OFFSET_ARG = "-offset";

    private static final int MILLIS_PER_SECOND = 1000;
    private static final int MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND;

    private static final int SECONDS_PER_MINUTE = 60;
    private static final int SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE;

    private static final String STOP_TIMES = "stop_times.txt";
    private static final String STOP_TIMES_TMP = "stop_times.txt.tmp";

    public GTFSNow(String url, String zipName, int offset) {
        Debug.log("GTFSNow.GTFSNow()");
        Debug.log("- url: " + url);
        Debug.log("- zipName: " + zipName);

        try {
            Path path = Files.createTempDirectory(null);
            String folder = path.toFile().getAbsolutePath();
            File fromZip = new File(folder + "/gtfs.zip");
            FileOutputStream fos = new FileOutputStream(fromZip);
            Util.downloadURLContent(url, fos, null);
            fos.close();
            Debug.log("- fromZip.getAbsolutePath()(): " + fromZip.getAbsolutePath());
            Debug.log("- fromZip.length(): " + fromZip.length());

            ZipFile zip = new ZipFile(fromZip);
            Enumeration<? extends ZipEntry> entries = zip.entries();

            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                Debug.log("-- e.getName(): " + e.getName());

                File ff = new File(path.toFile().getAbsolutePath() + "/" + e.getName());
                FileOutputStream foe = new FileOutputStream(ff);
                foe.write(Util.readInput(zip.getInputStream(e), null));
                foe.close();

            }

            updateStopTimes(folder, offset);

            String zipOutName = folder + "/" + zipName + ".zip";
            Debug.log("- zipOutName: " + zipOutName);
            ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(zipOutName));

            entries = zip.entries();

            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                Debug.log("-- e.getName(): " + e.getName());

                zout.putNextEntry(new ZipEntry(e.getName()));
                byte[] buf = Util.getFileContentsAsByteArray(folder + "/" + e.getName());
                zout.write(buf, 0, buf.length);
            }

            zout.close();

            String to = "gs://transitclock-resources/gtfs-zips/" + zipName + ".zip";
            Debug.log("- to: " + to);

            ProcessData pd = Util.runProcess(
                System.getenv("PATH"),
                new String[] {
                    "gsutil",
                    "cp",
                    zipOutName,
                    to
                },
                true
            );

            pd = Util.runProcess(
                System.getenv("PATH"),
                new String[] {
                    "gsutil",
                    "ls",
                    "-l",
                    to
                },
                true
            );

            Debug.log("- pd.output: " + pd.output);
            System.out.println("public URL: https://storage.googleapis.com/transitclock-resources/gtfs-zips/" + zipName + ".zip");

            path.toFile().delete();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ### TODO: check for date wraparound: create calendar instance from current time,
    // set hour/min/sec from 't' time, compare to calendar with hour/min/sec set from
    // calculated 'hour', 'min', 'sec'. If day of month between calendar instances is
    // different, we have wraparound. In that case, set hour/min/sec to 23:59:59
    private String makeTime(String t, String firstT, int offset) {
        //Debug.log("GTFSNow.makeTime()");
        //Debug.log("-      t: " + t);
        //Debug.log("- firstT: " + firstT);
        //Debug.log("- offset: " + offset);

        String[] arg = firstT.split(":");
        int h1 = Integer.parseInt(arg[0]);
        int m1 = Integer.parseInt(arg[1]);
        int s1 = Integer.parseInt(arg[2]);

        arg = t.split(":");
        int h2 = Integer.parseInt(arg[0]);
        int m2 = Integer.parseInt(arg[1]);
        int s2 = Integer.parseInt(arg[2]);

        int deltaT = h2 * SECONDS_PER_HOUR + m2 * SECONDS_PER_MINUTE + s2 - (h1 * SECONDS_PER_HOUR + m1 * SECONDS_PER_MINUTE + s1);
        //Debug.log("- deltaT: " + deltaT);

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(Util.now() + deltaT * MILLIS_PER_SECOND + offset * MILLIS_PER_MINUTE);

        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int min = cal.get(Calendar.MINUTE);
        int sec = cal.get(Calendar.SECOND);

        return String.format("%02d:%02d:%02d", hour, min, sec);
    }

    private void updateStopTimes(String folder, int offset) throws IOException {
        Debug.log("GTFSNow.updateStopTimes()");
        Debug.log("- folder: " + folder);
        Debug.log("- offset: " + offset);

        File f = new File(folder + "/" + STOP_TIMES);
        FileOutputStream fos = new FileOutputStream(folder + "/" + STOP_TIMES_TMP);
        PrintStream out = new PrintStream(fos);

        if (!f.exists()) {
            System.err.println("* 'stop_times.txt not found, nothing to process");
            return;
        }

        TextFile tf = new TextFile(f);
        String line = tf.getNextLine();
        CSVHeader header = new CSVHeader(line);
        String[] keys = header.getKeys();
        String firstArrival = null;
        String firstDeparture = null;
        String lastTripID = null;

        out.println(line);

        for (;;) {
            line = tf.getNextLine();
            if (line == null) break;

            //Debug.log("-- " + line);
            CSVRecord record = new CSVRecord(header, line);
            String tripID = record.get("trip_id");
            String arrival = record.get("arrival_time");
            String departure = record.get("departure_time");

            if (!tripID.equals(lastTripID)) {
                firstArrival = arrival;
                firstDeparture = departure;
            }

            lastTripID = tripID;

            StringBuilder sb = new StringBuilder();

            for (int i=0; i<keys.length; i++) {
                if (keys[i].equals("arrival_time")) {
                    sb.append(makeTime(arrival, firstArrival, offset));
                } else if (keys[i].equals("departure_time")) {
                    sb.append(makeTime(departure, firstDeparture, offset));
                } else {
                    sb.append(record.get(i));
                }

                if (i + 1 < keys.length) {
                    sb.append('.');
                }
            }

            //Debug.log("++ " + sb.toString());
            out.println(sb.toString());
        }

        fos.close();

        Path from = FileSystems.getDefault().getPath(folder, STOP_TIMES_TMP);
        Path to = FileSystems.getDefault().getPath(folder, STOP_TIMES);
        Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean getGCloudProjectName(String name) {
        Debug.log("GTFSNow.getGCloudProjectName()");

        ProcessData pd = Util.runProcess(System.getenv("PATH"), new String[] {"gcloud", "config", "get-value", "project"}, true);
        String output = Util.stripCommonWhitespace(pd.output);
        Debug.log("- output: " + output);

        return output.equals(name);
    }

    private static void usage() {
        System.err.println("usage: GTFSNow -url <static-gtfs-url> [-zip-name <zip-name>] [-offset <offset-in-minutes>]");
        System.exit(0);
    }

    // ### TODO check that gcloud sdk is installed first (e.g. by checking return status of 'which gcloud' with subprocess
    // path set to user path, $? will be 0 if found)
    // ### TODO checking if gs://transitclock-resources is accessible (e.g. by running gsutil ls gs://transitclock-resources)
    // instead of checking for a hardcoded project name would be less brittle
    public static void main(String[] arg) {
        String url = null;
        String zipName = "gtfs-static";
        int offset = 0;

        if (!getGCloudProjectName("transitclock-282522")) {
            System.err.println("* gcloud project must be 'transitclock-282522'");
            System.exit(0);
        }

        for (int i=0; i<arg.length; i++) {
            if (arg[i].equals(URL_ARG) && i + 1 < arg.length) {
                url = arg[++i];
                continue;
            }

            if (arg[i].equals(ZIP_ARG) && i + 1 < arg.length) {
                zipName = arg[++i];
                continue;
            }

            if (arg[i].equals(OFFSET_ARG) && i + 1 < arg.length) {
                offset = Integer.parseInt(arg[++i]);
                continue;
            }

            usage();
        }

        if (url == null) usage();

        if (zipName.toLowerCase().endsWith(".zip")) {
            zipName = zipName.substring(0, zipName.length() - 4);
        }

        new GTFSNow(url, zipName, offset);
    }
}