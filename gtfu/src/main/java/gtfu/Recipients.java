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

			try{
				String[] recipientsList = new String[recipients.size()];
				recipients.toArray(recipientsList);

				return recipientsList;

			} catch (Exception e) {
				throw new Fail(e);
			}
		}
	}
}
