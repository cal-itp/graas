package gtfu.tools;
import gtfu.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.FileReader;
import org.json.simple.*;
import org.json.simple.parser.*;
import java.nio.file.*;

// Leverage MobilityData's gtfs-validator tool to detect issues in agency GTFS feeds
public class GTFSValidator {

    private static void usage() {
        System.err.println("usage: java -cp build/libs/gtfu.jar gtfu.tools.GTFSValidator <static-gtfs-url>");
        System.exit(0);
    }

    public static void main(String[] arg) throws Exception {
        if (arg.length != 1) {
            usage();
        }
        String gtfsURL = arg[0];
        int errors = countErrors(gtfsURL, "src/main/resources/conf/output");
    }

    public static int countErrors(String gtfsURL, String reportDir) throws Exception {

        String reportPath = reportDir + "/report.json";
        Path path = Paths.get(reportPath);
        try{
            boolean result = Files.deleteIfExists(path);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        Runtime rt = Runtime.getRuntime();
        //Todo: run validator directly rather than creating a new process
        String[] commands = {"java", "-jar", "libs/gtfs-validator-current.jar", "-u", gtfsURL,"-o",reportDir};
        System.out.println("Running validator...");
        Process proc = rt.exec(commands);
        proc.waitFor();
        BufferedReader stdInput = new BufferedReader(new
             InputStreamReader(proc.getInputStream()));
        BufferedReader stdError = new BufferedReader(new
             InputStreamReader(proc.getErrorStream()));

        // // Read the output from the command
        // System.out.println("Here is the standard output of the command:\n");
        // String s = null;
        // while ((s = stdInput.readLine()) != null) {
        //     System.out.println(s);
        // }

        // // Read any errors from the attempted command
        // System.out.println("Here is the standard error of the command (if any):\n");
        // // String s = null;
        // while ((s = stdError.readLine()) != null) {
        //     System.out.println(s);
        // }

        int errorCount = 0;
        JSONParser parser = new JSONParser();
        JSONObject report = (JSONObject) parser.parse(new FileReader(reportPath));
        JSONArray notices = (JSONArray) report.get("notices");

        for (Object o : notices) {
            JSONObject notice = (JSONObject) o;
            String severity = (String) notice.get("severity");
            if(severity.equals("ERROR")){
                errorCount++;
                if (errorCount == 1){
                    System.out.println("** GTFS error(s) detected:");
                }
                String code = (String) notice.get("code");
                Long instances = (Long) notice.get("totalNotices");
                System.out.println("**   error #" + errorCount + ":");
                System.out.println("**     error code: " + code);
                System.out.println("**     instances: " + instances);
            }
        }
        if (errorCount > 0) System.out.println(errorCount + " errors detected. Output saved at " + reportPath);
        return errorCount;
    }
}
