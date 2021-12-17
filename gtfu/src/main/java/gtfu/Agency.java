package gtfu;

import java.io.Serializable;

public class Agency implements Serializable {
    private String tz;
    private String name;
    private String id;

    public Agency(String path) {
        TextFile f = new TextFile(path + "/agency.txt");
        CSVHeader header = new CSVHeader(f.getNextLine());
        CSVRecord r = new CSVRecord(header, f.getNextLine());

        tz = r.get("agency_timezone");
        name = r.get("agency_name");
        id = r.get("agency_id");

        f.dispose();
    }

    public String getTimeZoneString() {
        return tz;
    }

    public String getName() {
        return name;
    }

    public String getSanitizedName() {
        StringBuilder sb = new StringBuilder();

        for (int i=0; i<name.length(); i++) {
            char c = Character.toLowerCase(name.charAt(i));
            sb.append(Util.isAlphaNumeric(c) ? c : '-');
        }

        return sb.toString();
    }

    public String getID() {
        return id;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("agency: name = ");
        sb.append(getSanitizedName());

        sb.append(", id = ");
        sb.append(id);

        sb.append(", tz = ");
        sb.append(tz);

        return sb.toString();
    }
}