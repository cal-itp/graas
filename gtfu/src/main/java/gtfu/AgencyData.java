package gtfu;

public class AgencyData {
    public String agencyId;
    public String shortName;
    public String staticGtfsUrl;
    public String gtfsAgencyId;
    public String vehiclePositionUrl;

    public AgencyData() {
    }

    public AgencyData(String agencyId, String shortName, String staticGtfsUrl, String vehiclePositionUrl) {
        this.agencyId = agencyId;
        this.shortName = shortName;
        this.staticGtfsUrl = staticGtfsUrl;
        this.vehiclePositionUrl = vehiclePositionUrl;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("{");

        sb.append("agencyId: ");
        sb.append(agencyId);
        sb.append(", ");

        sb.append("shortName: ");
        sb.append(shortName);
        sb.append(", ");

        sb.append("staticGtfsUrl: ");
        sb.append(staticGtfsUrl);
        sb.append(", ");

        sb.append("vehiclePositionUrl: ");
        sb.append(vehiclePositionUrl);

        return sb.toString();
    }
}