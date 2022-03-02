package gtfu.tools;

import gtfu.ProgressObserver;
import gtfu.ConsoleProgressObserver;
import gtfu.Util;
import gtfu.Debug;

import java.util.Date;
import java.util.Arrays;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class UpdateTripNames {

    public static void UpdateTripNames(String agencyID) throws Exception {
        String[] agencyIDList = {agencyID};
        UpdateTripNames(agencyIDList);
    }

    public static void UpdateTripNames(String[] agencyIDList) throws Exception {
        GH gh = new GH();

        for (String agencyID : agencyIDList) {
            Debug.log(agencyID);
            String filePath = "server/agency-config/gtfs/gtfs-aux/" + agencyID +"/trip-names.json";
            long lastUpdatedTripList = gh.getLatestCommitMillis(filePath);
            Debug.log("- lastUpdatedTripList: " + lastUpdatedTripList);

            AgencyYML yml = new AgencyYML();
            String gtfsURL = yml.getURL(agencyID);
            long lastModifiedLRemote = Util.getLastModifiedRemote(gtfsURL);
            Debug.log("- lastModifiedLRemote: " + lastModifiedLRemote);

            if(lastModifiedLRemote > lastUpdatedTripList){
                Debug.log("Static GTFS has been updated since trip-names.json was last updated. Re-running TripListGenerator now to check whether this impacts trip-names.json");

                String fileURL = "https://raw.githubusercontent.com/cal-itp/graas/main/" + filePath;
                ProgressObserver po = new ConsoleProgressObserver(40);
                byte[] currentFile = Util.getURLContentBytes(fileURL, po);

                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final String utf8 = StandardCharsets.UTF_8.name();
                try (PrintStream ps = new PrintStream(baos, true, utf8)) {
                    TripListGenerator.generateTripList(agencyID, null, ps);
                }
                byte[] newFile = baos.toByteArray();

                if(!Arrays.equals(currentFile, newFile)){
                    Debug.log("Relevant changes detected. Creating a PR to update the file");

                    String title = ":robot: updates to " + agencyID + " triplist";
                    String description = ":robot: Automatically-generated PR :robot: Our daily check detected that changes were made to " + agencyID + "'s static GTFS. This PR was automatically generated, so please review and make updates if necessary before merging";
                    String message = "Update trip-names.json to reflect static GTFS updates";
                    String branchName = agencyID + "-triplist-update-" + Util.now();
                    gh.createPR(title, description, filePath, newFile, message, branchName);
                }
                else{
                    Debug.log("No relevant changes detected.");
                }
            }
            else{
                Debug.log("trip-names.json has been updated since the last static GTFS update. Continuing.");
            }
        }
    }

    private static void usage() {
        System.err.println("usage: UpdateTripNames [-u|--url <live-agencies-url>] [-a|--agency-id <agency-id>]");
        System.err.println("    <url> is assumed to point to a plain text document that has an agency ID per line");
        System.exit(1);
    }

    public static void main(String[] arg) throws Exception {
        String url = null;
        String agencyID = null;;

        for (int i=0; i<arg.length; i++) {

            if ((arg[i].equals("-u") || arg[i].equals("--url")) && i < arg.length - 1) {
                url = arg[i + 1];
            }

            if ((arg[i].equals("-a") || arg[i].equals("--agency-id")) && i < arg.length - 1) {
                agencyID = arg[i + 1];
            }
        }

        if (agencyID == null && url == null) usage();

        if(url != null){
            ProgressObserver po = new ConsoleProgressObserver(40);
            String context = Util.getURLContent(url, po);
            String[] agencyIDList = context.split("\n");
            UpdateTripNames(agencyIDList);
        }
        else{
            UpdateTripNames(agencyID);
        }
    }

}