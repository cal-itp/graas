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

		// Default to local recipient list if env variable is present
		String localRecipientsString = System.getenv("LOCAL_REPORT_RECIPIENTS");

		if (localRecipientsString != null){
			return localRecipientsString.split(",");
		}
		else {
			ArrayList recipients = (ArrayList) recipientsMap.get(reportType);

			if (recipients == null) {
				throw new Fail("** error: no report type " + reportType + " listed in " + RECIPIENTS_FILE_PATH);
			}

			// Convert arraylist of objects to array of strings
			try{
				String[] recipientsList = new String[recipients.size()];

				for (int i = 0; i < recipients.size(); i++) {
					recipientsList[i] = (String) recipients.get(i);
				}

				return recipientsList;

			} catch (Exception e) {
	            throw new Fail(e);
	        }
		}
	}
}
