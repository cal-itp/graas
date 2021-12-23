package gtfu;
import java.io.*;
import java.util.*;

public class Recipients {

	private static final String RECIPIENTS_FILE_PATH = "src/main/resources/conf/recipients.json";
	private Map<String, Object> recipientsMap;

	public Recipients() {
        recipientsMap = Util.parseJSONasMap(RECIPIENTS_FILE_PATH);
	}

    public String[] get(String reportType) {

        String[] recipientsList = null;

		// Default to local recipient list if env variable is present
        String recipientsString = System.getenv("LOCAL_REPORT_RECIPIENTS");

        if (recipientsString != null){
	        recipientsList = recipientsString.split(",");
        }
    	else {
			recipientsList = (String[]) recipientsMap.get(reportType);

			if (recipientsList == null) {
				throw new Fail("** error: no report type " + reportType + " listed in " + RECIPIENTS_FILE_PATH);
			}
        }
        return recipientsList;
    }
}
