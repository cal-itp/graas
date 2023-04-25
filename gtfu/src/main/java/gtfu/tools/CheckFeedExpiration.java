package gtfu.tools;

import java.util.Date;

import gtfu.ConsoleProgressObserver;
import gtfu.Debug;
import gtfu.EmailFailureReporter;
import gtfu.FailureReporter;
import gtfu.FeedInfo;
import gtfu.ProgressObserver;
import gtfu.Recipients;
import gtfu.SlackFailureReporter;
import gtfu.Time;
import gtfu.Util;

/**
 * Daily checks and updates on each active agency's feed.
 */
public class CheckFeedExpiration {
    private static final long MAX_FEED_EXPIRATION_WINDOW_DAYS = 14;

     /**
     * Runs CheckFeedExpiration for a single agency
     * @param agencyID The agencyID
     */
    public static void checkFeedExpiration(String agencyID, boolean sendEmailAlert, boolean sendSlackAlert) throws Exception {
        String[] agencyIDList = {agencyID};
        checkFeedExpiration(agencyIDList, sendEmailAlert, sendSlackAlert);
    }

    /**
     * Checks whether any feed has expired, and sends warning via Email and/or Slack if so.
     * @param agencyIDList A list of agencyIDs
     */
    public static void checkFeedExpiration(String[] agencyIDList, boolean sendEmailAlert, boolean sendSlackAlert) throws Exception {
        AgencyYML yml = new AgencyYML();
        Recipients r = new Recipients();
        String[] recipients = r.get("error_report");

        ConsoleProgressObserver progressObserver = new ConsoleProgressObserver(40);
        String cacheFolder = "src/main/resources/conf/cache";
        
        for (String agencyID : agencyIDList) {
            String gtfsURL = yml.getURL(agencyID);
            Util.updateCacheIfNeeded(cacheFolder, agencyID, gtfsURL, progressObserver);
            FeedInfo feedInfo = Util.loadFeedInfo(cacheFolder, agencyID);
            String endDate = feedInfo.getEndDate();
            if (endDate == null){
                Debug.log("No feed expiration date is listed, assume the feed doesn't expire");
            } else{
                Debug.log("endDate: " + endDate);                
                int daysUntilEnd = Time.getDaysUntilDateInt(endDate);
                Debug.log("daysUntilEnd: " + daysUntilEnd);

                if(daysUntilEnd <= MAX_FEED_EXPIRATION_WINDOW_DAYS) {
                    String messageSubject;
                    String messageBody;
                    
                    if(daysUntilEnd <= 0){
                        messageSubject = "GRaaS Error: feed expired";
                        Date endDateDate = Time.getDateFromYYYYMMDD(endDate);
                        String endDateFormatted = Time.formatDate("MMM dd yyyy", endDateDate);
                        messageBody = "Error: the " + agencyID + " GTFS Schedule feed has expired, as of " + endDateFormatted;
                    } else {
                        messageSubject = "GRaaS Warning: feed will expire soon";
                        messageBody = "Warning: " + agencyID + " GTFS Schedule feed will expire in " + daysUntilEnd + " days. We send automated warnings when feeds expire within " + MAX_FEED_EXPIRATION_WINDOW_DAYS + " days.";
                    }

                    FailureReporter reporter; 

                    if(sendEmailAlert){
                        reporter = new EmailFailureReporter(recipients,  messageSubject);
                        Util.setReporter(reporter);
                        reporter.addLine(messageBody);
                        reporter.send(); 
                    }

                    if(sendSlackAlert) {
                        reporter = new SlackFailureReporter();
                        Util.setReporter(reporter);
                        reporter.addLine(messageBody);
                        reporter.send();  
                    }
                }
            }
        }
    }

    private static void usage() {
        System.err.println("usage: CheckFeedExpiration -u|--url <live-agencies-url> -a|--agency-id <agency-id> [-r|--regenerate-all]");
        System.err.println("    <live-agencies-url> is assumed to point to a plain text document that has an agency ID per line");
        System.err.println("    <agency-id> is the id of the single agency you'd like to update");
        System.err.println("    You must supply <live-agencies-url> or <agency-id>. If you supply both, <agency-id> will be ignored");
        System.exit(1);
    }

    /**
     * Runs CheckFeedExpiration from the command line
     */
    public static void main(String[] arg) throws Exception {
        String url = null;
        String agencyID = null;
        boolean sendEmailAlert = false;
        boolean sendSlackAlert = false;

        for (int i=0; i<arg.length; i++) {

            if ((arg[i].equals("-u") || arg[i].equals("--url")) && i < arg.length - 1) {
                url = arg[i + 1];
            }

            if ((arg[i].equals("-a") || arg[i].equals("--agency-id")) && i < arg.length - 1) {
                agencyID = arg[i + 1];
            }

            if ((arg[i].equals("-e") || arg[i].equals("--email"))) {
                sendEmailAlert = true;
            }

            if ((arg[i].equals("-s") || arg[i].equals("--slack"))) {
                sendSlackAlert = true;
            }
        }

        if (agencyID == null && url == null) usage();

        if(url != null){
            ProgressObserver po = new ConsoleProgressObserver(40);
            String context = Util.getURLContent(url, po);
            String[] agencyIDList = context.split("\n");
            checkFeedExpiration(agencyIDList, sendEmailAlert, sendSlackAlert);
        }
        else{
            checkFeedExpiration(agencyID, sendEmailAlert, sendSlackAlert);
        }
    }
}
