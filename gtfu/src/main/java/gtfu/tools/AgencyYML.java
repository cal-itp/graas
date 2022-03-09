package gtfu.tools;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import gtfu.*;

/**
* Access agency data from agencies.yml, the Cal-ITP source-of-truth for agency attributes
*/
public class AgencyYML {
    private static final String url = "https://raw.githubusercontent.com/cal-itp/data-infra/main/airflow/data/agencies.yml";

    private String[] ymlLines;
    private Map<String,String> urlMap;
    private Map<String,String> nameMap;


    /**
    * Perform initial content download and variable setup, and then run the processYML function
    */
    public AgencyYML(){
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Util.downloadURLContent(url, bos, null);
        this.ymlLines = (new String(bos.toByteArray())).split("\n");
        urlMap = new HashMap<String,String>();
        nameMap = new HashMap<String,String>();
        processYML();
    }

    /**
    * Identify the agencyName and GTFS schedule URL for every agency represented in the file.
    * It is straightforward to add additional fields to pull from the file.
    */
    private void processYML(){

        for (int i=0; i<ymlLines.length; i++) {
            String ymlLine = getStrippedLine(i);
            // This will only be true for the non-indented agencyID's:
            if (ymlLine == ymlLine.trim() && ymlLine != null) {
                String agencyID = ymlLine.replace(":", "");
                String agencyName = null;
                String agencyURL = null;

                for (;;) {
                    String line = getStrippedLine(++i);
                    if (Util.isEmpty(line)) {
                        Debug.log("empty, continuing");
                        continue;
                    }

                    char c = line.charAt(0);
                    if (c != ' ' && c != '\t') break;

                    agencyName = findValue(line,"agency_name");
                    if (agencyName != null){
                        nameMap.put(agencyID,agencyName);
                    }

                    agencyURL = findValue(line,"gtfs_schedule_url");
                    if (agencyURL != null){
                        urlMap.put(agencyID,agencyURL);
                        break;
                    }
                }
            }
        }
    }

    /**
    * Utility function which returns the value of a given fieldname, given that the fieldName is present in the line provided.
    * @param  line      A single line from the file
    * @param  fieldName The field name we are searching for
    * @return           The field value, or null
    */
    private String findValue(String line, String fieldName){
        String key = fieldName + ": ";
        int index = line.indexOf(key);

        if (index > 0){
            String value = line.substring(index + key.length());
            value = value.trim();
            return value;
        }
        return null;
    }

    /**
    * Returns the GTFS schedule URL for the provided agencyID
    * @param  agencyID  The agencyID being searched
    * @return           Static GTFS URL
    */
    public String getURL(String agencyID){
        return urlMap.get(agencyID);
    }

    /**
    * Returns the name for the provided agencyID
    * @param  agencyID  The agencyID being searched
    * @return           Agency name
    */
    public String getName(String agencyID){
        return nameMap.get(agencyID);
    }


    /**
    * Returns the name for the provided agencyID
    * @param  index     The line index from the ymlLines file
    * @return           The string value of the line at that index, with newlines removed
    */
    private String getStrippedLine(int index) {
        // Debug.log("getStrippedLine()");
        return ymlLines[index].replace("\r", "");
    }
}
