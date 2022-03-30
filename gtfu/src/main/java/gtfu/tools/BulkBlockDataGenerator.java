package gtfu.tools;

import gtfu.ProgressObserver;
import gtfu.ConsoleProgressObserver;
import gtfu.Util;
import gtfu.Debug;
import gtfu.EmailFailureReporter;
import gtfu.FailureReporter;
import gtfu.Recipients;

import java.util.Arrays;

/**
 * Creates block data for each agency that uses bulk assignment mode
 */
public class BulkBlockDataGenerator {

    /**
     * Checks whether each agency has bulk assignment mode enabled, and generates block data for them if so.
     * @param agencyIDList A list of agencyIDs
     * @param daysAhead How many days blocks should be generated for. A value of 0 will generate for only today, 1 will generate for today and tomorrow.
     */
    public static void BulkBlockDataGenerator(String[] agencyIDList, Integer daysAhead) throws Exception {

        if (daysAhead < 0 || daysAhead > 30) usage();

        Recipients r = new Recipients();
        String[] recipients = r.get("error_report");

        FailureReporter reporter = new EmailFailureReporter(recipients, "Generate Block Data Report");
        Util.setReporter(reporter);

        reporter.addLine("Block data generated for:");

        for (String agencyID : agencyIDList) {
            reporter.addLine(agencyID);
            for (int i = 0; i < daysAhead + 1; i++){
                new BlockDataGenerator(agencyID, i);
            }
        }
        reporter.send();
    }

    private static void usage() {
        System.err.println("usage: BulkBlockDataGenerator -u|--url <live-agencies-url> -d|--days-ahead <n>");
        System.err.println("    <live-agencies-url> is assumed to point to a plain text document that has an agency ID per line");
        System.err.println("    <n> how many days ahead from todayto generate block data for. 0 is today, and 30 is the maximum");
        System.exit(1);
    }

    /**
     * Runs BulkBlockDataGenerator from the command line. Defaults to generating only for today, unless a number greater than 0 is provided following the -d flag
     */
    public static void main(String[] arg) throws Exception {
        String url = null;
        int daysAhead = -1;

        for (int i=0; i<arg.length; i++) {

            if ((arg[i].equals("-u") || arg[i].equals("--url")) && i < arg.length - 1) {
                url = arg[i + 1];
            }
            if ((arg[i].equals("-d") || arg[i].equals("--days-ahead")) && i < arg.length - 1) {
                String s = arg[i + 1];
                try {
                    daysAhead = Integer.parseInt(s);
                } catch (NumberFormatException e) {
                    usage();
                }
            }
        }

        if (url == null) usage();

        ProgressObserver po = new ConsoleProgressObserver(40);
        String context = Util.getURLContent(url, po);
        String[] agencyIDList = context.split("\n");
        BulkBlockDataGenerator(agencyIDList, daysAhead);
    }
}
