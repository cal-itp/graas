package gtfu;
import java.io.*;
import java.util.*;

public class Recipients {

	private static final String RECIPIENTS_FILE_PATH = "src/main/resources/conf/recipients.txt";
	private static final String DELIMITER = ":";
	private Map<String, String> recipientsMap;

	public Recipients() {

        recipientsMap = Util.hashMapFromTextFile(RECIPIENTS_FILE_PATH,DELIMITER);
	}

    public String[] get(String reportType) {

        String recipientsList = null;
        // Default to local recipient list if env variable is present
        recipientsList = System.getenv("LOCAL_REPORT_RECIPIENTS");

        if (recipientsList == null) {
        	recipientsList = recipientsMap.get(reportType);

			if (recipientsList == null) {
				System.out.println("** error: no report type " + reportType + " listed in " + RECIPIENTS_FILE_PATH);
				System.exit(1);
			}
        }
        return recipientsList.split(",");
    }
}

