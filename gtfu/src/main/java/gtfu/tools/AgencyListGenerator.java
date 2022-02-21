package gtfu.tools;

import gtfu.*;
import java.util.ArrayList;
import java.util.List;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import java.io.IOException;

public class AgencyListGenerator {

    public static void generateAgencyList() throws IOException {

        GCloudStorage gcs = new GCloudStorage();
        String dirName = "graas-report-archive/";

        List <String> objectList = gcs.getObjectList("graas-resources", dirName);

        JSONObject agencies = new JSONObject();

        for (int i = 0; i < objectList.size(); i++){

            String dirPath = objectList.get(i);
            // Turns "graas-report-archive/agencyname/" into "agencyname"
            String agencyName = dirPath.substring(dirName.length(), dirPath.length() - 1);
            List <String> fileNames = gcs.getObjectList("graas-resources", dirPath);

            JSONArray dates = new JSONArray();

            for (int j = 0; j < fileNames.size(); j++)   {
                String filePath = fileNames.get(j);
                // Turns "graas-report-archive/agencyname/agencyname-2022-02-07.png" into "agencyname-2022-02-07.png"
                String fileName = filePath.substring(dirPath.length(), filePath.length());

                if(fileName.contains(".png")){
                    // turns "agencyname-2022-02-10.png" into "2022-02-10"
                    String date = fileName.substring(agencyName.length() + 1, fileName.length() - 4);
                    dates.add(date);
                }
            }
            if(dates.size() > 0){
                agencies.put(agencyName, dates);
            }
        }
        gcs.uploadObject("graas-resources", "web", "graas-report-agency-dates.json", agencies.toString().getBytes("utf-8"), "text/json");
    }

    private static void usage() {
        System.err.println("usage: AgencyListGenerator");
    }

    public static void main(String[] arg) throws Exception {
        generateAgencyList();
    }
}