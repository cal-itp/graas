package gtfu.tools;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import gtfu.ConsoleProgressObserver;
import gtfu.Debug;
import gtfu.EmailFailureReporter;
import gtfu.SlackFailureReporter;
import gtfu.FailureReporter;
import gtfu.FeedInfo;
import gtfu.ProgressObserver;
import gtfu.Recipients;
import gtfu.Util;
import gtfu.Time;
import java.util.Date;

/**
 * Daily checks and updates on each active agency's feed.
 */
public class UpdateFeedData {
    private static final double MAX_TRIP_NAMES_LENGTH_CHANGE = 0.05;
    private static final long MAX_FEED_EXPIRATION_WINDOW = 30;

     /**
     * Runs UpdateFeedData for a single agency
     * @param agencyID The agencyID
     * @param regenerateAll A true value will run the trip comparison even if there is no recent update detected
     */
    public static void UpdateFeedData(String agencyID, boolean regenerateAll) throws Exception {
        String[] agencyIDList = {agencyID};
        UpdateFeedData(agencyIDList, regenerateAll);
    }
    
    /**
     * Runs a series of checks for each agency in the list.
     * @param agencyIDList A list of agencyIDs
     * @param regenerateAll A true value will run the comparison even if there is no recent update detected
     */
    public static void UpdateFeedData(String[] agencyIDList, boolean regenerateAll) throws Exception {

        Map <String,String> agencyURLMap = new HashMap<>();
        AgencyYML yml = new AgencyYML();
        Recipients r = new Recipients();
        String[] recipients = r.get("error_report");

        for (String agencyID : agencyIDList){
            String gtfsURL = yml.getURL(agencyID);
            agencyURLMap.put(agencyID, gtfsURL);
        }
        UpdateTripNames(agencyURLMap, regenerateAll, recipients);    
        CheckFeedExpiration(agencyURLMap, recipients);
    }

    /**
     * Checks whether each agency has updated their static GTFS feed since the latest update to trip-names.json. If they have, it runs TripListGenerator, compares the new trip with the old one, and creates a PR if the new one differs.
     * @param agencyURLMap A map of agencyIDs to their gtfs URL
     * @param regenerateAll     A true value will run the comparison even if there is no recent update detected
     */
    public static void UpdateTripNames(Map <String,String> agencyURLMap, boolean regenerateAll, String[] recipients) throws Exception {
        GitHubUtil gh = new GitHubUtil();
        FailureReporter reporter = new EmailFailureReporter(recipients, "Update Trip Names Report");
        Util.setReporter(reporter);

        reporter.addLine("New PRs created for:");
        int prCount = 0;
        for (String agencyID : agencyURLMap.keySet()) {
            Debug.log(agencyID);
            String gtfsURL = agencyURLMap.get(agencyID);
            String filePath = "server/agency-config/gtfs/gtfs-aux/" + agencyID +"/trip-names.json";
            long lastUpdatedTripList = gh.getLatestCommitMillis(filePath);
            Debug.log("- lastUpdatedTripList: " + lastUpdatedTripList);

            long lastModifiedLRemote = Util.getLastModifiedRemote(gtfsURL);
            Debug.log("- lastModifiedLRemote: " + lastModifiedLRemote);

            if(lastModifiedLRemote > lastUpdatedTripList || regenerateAll ){
                Debug.log("Static GTFS has been updated since trip-names.json was last updated. Re-running TripListGenerator now to check whether this impacts trip-names.json");

                String fileURL = "https://raw.githubusercontent.com/cal-itp/graas/main/" + filePath;
                ProgressObserver po = new ConsoleProgressObserver(40);
                byte[] currentFile = Util.getURLContentBytes(fileURL, po);
                int currentFileLength = currentFile.length;

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                String utf8 = StandardCharsets.UTF_8.name();
                try (PrintStream ps = new PrintStream(baos, true, utf8)) {
                    TripListGenerator.generateTripList(agencyID, null, ps, false);
                } catch (Exception e) {
                    reporter.addLine("   * exception in generating triplist for " + agencyID + ": " + e);
                    continue;
                }
                byte[] newFile = baos.toByteArray();
                String json = baos.toString(utf8);
                int newFileLength = newFile.length;
                if(!Util.isValidJSON(json)){
                    reporter.addLine("   * failed to generate tripList for agency " + agencyID + " because JSON was not valid");
                    continue;
                }

                if(!Arrays.equals(currentFile, newFile)){
                    Debug.log("Relevant changes detected. Creating a PR to update the file");

                    // Consider linking the URL for the PR. This requires some updates to Sendgrid.java, to send HTML.
                    String reportLine = agencyID + ": ";
                    double lengthChange  = Math.abs((double) (currentFileLength - newFileLength) / currentFileLength);
                    Debug.log("lengthChange: " + lengthChange);

                    boolean autoMerge = lengthChange < MAX_TRIP_NAMES_LENGTH_CHANGE;

                    String title = ":robot: updates to " + agencyID + " triplist";
                    String message = "Update trip-names.json to reflect static GTFS updates";
                    String branchName = agencyID + "-triplist-update-" + Util.now();
                    String description = "Our automated daily check detected that changes were made to " + agencyID + "'s static GTFS.";

                    if(autoMerge) {
                        description += "This PR merged automatically because the file's length was changed by less than " + (MAX_TRIP_NAMES_LENGTH_CHANGE * 100) + "%";
                        reportLine += "Automerged PR";
                        Debug.log("Automerging PR");
                    } else {
                        description += "This PR was automatically generated, so please review and make updates if necessary before merging";
                        reportLine += "Please review PR";
                    }

                    gh.createCommitAndPR(title, description, filePath, newFile, message, branchName, autoMerge);
                    reporter.addLine(reportLine);
                    prCount++;
                }
                else{
                    Debug.log("No relevant changes detected.");
                }
            }
            else{
                Debug.log("trip-names.json has been updated since the last static GTFS update. Continuing.");
            }
        }
        // Only send email if at least one PR was created.
        if (prCount > 0) {
            reporter.send();
        }
    }
    
    /**
     * Checks whether any feed has expired, and sends warning to graas_internal Slack channel if so.
     * @param agencyURLMap A map of agencyIDs to their gtfs URL
     */
    public static void CheckFeedExpiration(Map <String,String> agencyURLMap, String[] recipients) throws Exception {
        ConsoleProgressObserver progressObserver = new ConsoleProgressObserver(40);
        String cacheFolder = "src/main/resources/conf/cache";
        
        for (String agencyID : agencyURLMap.keySet()) {
            String gtfsURL = agencyURLMap.get(agencyID);
            Util.updateCacheIfNeeded(cacheFolder, agencyID, gtfsURL, progressObserver);
            FeedInfo feedInfo = Util.loadFeedInfo(cacheFolder,agencyID);
            String endDate = feedInfo.getEndDate();
            if (endDate == null){
                Debug.log("No end date is listed, assume the feed doesn't expire");
            } else{
                // int daysUntilEnd = Time.getDaysUntilDateLong(endDate);
                int daysUntilEnd = -10;
                Debug.log("endDate: " + endDate);
                Debug.log("daysUntilEnd: " + daysUntilEnd);

                if(daysUntilEnd <= MAX_FEED_EXPIRATION_WINDOW) {
                    String subject;
                    String body;
                    
                    if(daysUntilEnd <= 0){
                        subject = "GRaaS Error: feed expired";
                        Date endDateDate = Time.getDateFromYYYYMMDD(endDate);
                        String endDateFormatted = Time.formatDate("MMM dd yyyy", endDateDate);
                        body = "Error: the " + agencyID + " GTFS Schedule feed has expired, as of " + endDateFormatted;
                    } else {
                        subject = "GRaaS Warning: feed will expire soon";
                        body = "Warning: " + agencyID + " GTFS Schedule feed will expire in " + daysUntilEnd + " days. We send automated warnings when feeds expire within " + MAX_FEED_EXPIRATION_WINDOW + " days.";
                    }

                    FailureReporter reporter = new EmailFailureReporter(recipients,  subject);
                    Util.setReporter(reporter);
                    reporter.addLine(body);
                    reporter.send(); 
    
                    reporter = new SlackFailureReporter();
                    Util.setReporter(reporter);
                    reporter.addLine(body);
                    reporter.send();  
                }
            }
            break;
        }
    }

    private static void usage() {
        System.err.println("usage: UpdateFeedData -u|--url <live-agencies-url> -a|--agency-id <agency-id> [-r|--regenerate-all]");
        System.err.println("    <live-agencies-url> is assumed to point to a plain text document that has an agency ID per line");
        System.err.println("    <agency-id> is the id of the single agency you'd like to update");
        System.err.println("    You must supply <live-agencies-url> or <agency-id>. If you supply both, <agency-id> will be ignored");
        System.err.println("    Use the '-r' flag to regenerate all files, rather than just the ones that were modified recently");
        System.exit(1);
    }

    /**
     * Runs UpdateFeedData from the command line
     */
    public static void main(String[] arg) throws Exception {
        String url = null;
        String agencyID = null;
        boolean regenerateAll = false;

        for (int i=0; i<arg.length; i++) {

            if ((arg[i].equals("-u") || arg[i].equals("--url")) && i < arg.length - 1) {
                url = arg[i + 1];
            }

            if ((arg[i].equals("-a") || arg[i].equals("--agency-id")) && i < arg.length - 1) {
                agencyID = arg[i + 1];
            }

            if (arg[i].equals("-r") || arg[i].equals("--regenerate-all")) {
                regenerateAll = true;
            }
        }

        if (agencyID == null && url == null) usage();

        if(url != null){
            ProgressObserver po = new ConsoleProgressObserver(40);
            String context = Util.getURLContent(url, po);
            String[] agencyIDList = context.split("\n");
            UpdateFeedData(agencyIDList, regenerateAll);
        }
        else{
            UpdateFeedData(agencyID, regenerateAll);
        }
    }
}
