package gtfu.tools;

import gtfu.ProgressObserver;
import gtfu.ConsoleProgressObserver;
import gtfu.Util;
import gtfu.Debug;
import gtfu.EmailFailureReporter;
import gtfu.FailureReporter;
import gtfu.Recipients;
import gtfu.Time;

import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Enumeration;
import java.util.Date;

/**
 * Uploads agency static GTFS files to GCloud Bucket
 */
public class StaticGTFSToBucket {

    private static GCloudStorage gcs = new GCloudStorage();

    /**
     * Uploads static GTFS for a single agency to the specified bucket
     * @param agencyID   Agency
     * @param bucketName GCloud bucket name
     * @param cacheDir   Cache location for storing zip files
     */
    public static void StaticGTFSToBucket(String agencyID, String bucketName, String cacheDir) throws IOException {
        String[] agencyIDList = {agencyID};
        StaticGTFSToBucket(agencyIDList, bucketName, cacheDir);
    }

    /**
     * Uploads static GTFS for each agency to the specified bucket
     * @param agencyIDList A list of agencyIDs
     * @param bucketName   GCloud bucket name
     * @param cacheDir     Cache location for storing zip files
     */
    public static void StaticGTFSToBucket(String[] agencyIDList, String bucketName, String cacheDir) throws IOException {
        AgencyYML yml = new AgencyYML();

        Recipients r = new Recipients();
        String[] recipients = r.get("error_report");

        FailureReporter reporter = new EmailFailureReporter(recipients, "Static GTFS File Update");
        Util.setReporter(reporter);

        File file = new File(cacheDir);

        if (!file.exists()) {
            file.mkdir();
        }

        reporter.addLine("Updated Gcloud static GTFS files:");

        for (String agencyID : agencyIDList) {
            String name = cacheDir + "/" + agencyID;
            String gtfsURL = yml.getURL(agencyID);
            file = new File(name);

            if (!file.exists()) {
                file.mkdir();
            }

            long lastModifiedGcloud = getLastModifiedGcloud(bucketName, agencyID);
            long lastModifiedRemote = Util.getLastModifiedRemote(gtfsURL);
            ConsoleProgressObserver progressObserver = new ConsoleProgressObserver(40);

            if (lastModifiedRemote <= lastModifiedGcloud) {
                if (progressObserver != null) {
                    progressObserver.setMax(1);
                    progressObserver.update(1);
                }
                continue;
            }

            Debug.log("+ remote GTFS zip is newer than the version on gcloud, updating...");
            setLastModifiedGcloud(bucketName, agencyID, lastModifiedRemote);

            try {
                String cl = Util.getResponseHeader(gtfsURL, "Content-Length");

                if (cl == null) {
                    cl = "0";
                }

                int contentLength = Integer.parseInt(cl);
                Debug.log("- contentLength: " + contentLength);
                progressObserver.setMax(contentLength);

                File zf = new File(name + "/gtfs.zip");
                FileOutputStream fos = new FileOutputStream(zf);

                Util.downloadURLContent(gtfsURL, fos, progressObserver);
                fos.close();

                ZipFile zip = new ZipFile(zf);
                Enumeration<? extends ZipEntry> entries = zip.entries();

                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    Debug.log("-- " + e.getName());
                    byte[] bytes = Util.readInput(zip.getInputStream(e), null);
                    gcs.uploadObject(bucketName, "gtfs-archive/" + agencyID + "/", e.getName(), bytes);
                }
                reporter.addLine("- " + agencyID);

            } catch (IOException e) {
                Debug.error("couldn't upload agency zip from URL: " + e);
            }
        }
        reporter.send();
    }

    private static long getLastModifiedGcloud(String bucket, String agency){
        try{
            byte[] bytes = gcs.getObject(bucket, "gtfs-archive/" + agency + "/last-modified.txt");
            String gcloudModified = new String(bytes);
            Debug.log("- gcloudModified: " + gcloudModified);
            return Time.parseDateAsLong(Util.HTTP_DATE_FORMAT, gcloudModified);
        } catch(Exception e) {
            Debug.log("File does not exist. Returning last modified time of 0");
            return 0;
        }
    }

    private static void setLastModifiedGcloud(String bucket, String agency, long lastModified) throws IOException {
        String s = Time.formatDate(Util.HTTP_DATE_FORMAT, new Date(lastModified)) + '\n';
        byte[] bytes = s.getBytes();
        gcs.uploadObject(bucket, "gtfs-archive/" + agency + "/", "last-modified.txt", bytes);
    }

    private static void usage() {
        System.err.println("usage: StaticGTFSToBucket -u|--url <live-agencies-url> -a|--agency-id <agency-id> -b|--bucket-name <gcloud-bucket-name>");
        System.err.println("    <live-agencies-url> is assumed to point to a plain text document that has an agency ID per line");
        System.err.println("    <agency-id> is the id of the single agency you'd like to update");
        System.err.println("    You must supply <live-agencies-url> or <agency-id>. If you supply both, <agency-id> will be ignored");
        System.exit(1);
    }

    /**
     * Runs StaticGTFSToBucket from the command line
     */
    public static void main(String[] arg) throws Exception {
        String url = null;
        String agencyID = null;
        String bucketName = null;
        String cacheDir = null;

        for (int i=0; i<arg.length; i++) {

            if ((arg[i].equals("-u") || arg[i].equals("--url")) && i < arg.length - 1) {
                url = arg[i + 1];
            }
            if ((arg[i].equals("-a") || arg[i].equals("--agency-id")) && i < arg.length - 1) {
                agencyID = arg[i + 1];
            }
            if ((arg[i].equals("-b") || arg[i].equals("--bucket-name")) && i < arg.length - 1) {
                bucketName = arg[i + 1];
            }
            if ((arg[i].equals("-c") || arg[i].equals("--cache-dir")) && i < arg.length - 1) {
                cacheDir = arg[++i];
                continue;
            }
        }

        if ((agencyID == null && url == null) || bucketName == null || cacheDir == null) usage();

        if(url != null){
            ProgressObserver po = new ConsoleProgressObserver(40);
            String context = Util.getURLContent(url, po);
            String[] agencyIDList = context.split("\n");
            StaticGTFSToBucket(agencyIDList, bucketName, cacheDir);
        }
        else{
            StaticGTFSToBucket(agencyID, bucketName, cacheDir);
        }
    }
}
