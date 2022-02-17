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


// Access data from agencies.yml, the Cal-ITP source of truth for agency attributes
public class AgencyYML {
    private static final String url = "https://raw.githubusercontent.com/cal-itp/data-infra/main/airflow/data/agencies.yml";

    private String[] ymlLines;
    private Map<String,String> urlMap;
    private Map<String,String> nameMap;

    public AgencyYML(){
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Util.downloadURLContent(url, bos, null);
        this.ymlLines = (new String(bos.toByteArray())).split("\n");
        urlMap = new HashMap<String,String>();
        nameMap = new HashMap<String,String>();
        processYML();
    }

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

    public String getURL(String agencyID){
        return urlMap.get(agencyID);
    }

    public String getName(String agencyID){
        return nameMap.get(agencyID);
    }

    private String getStrippedLine(int index) {
        // Debug.log("getStrippedLine()");
        return ymlLines[index].replace("\r", "");
    }
}
