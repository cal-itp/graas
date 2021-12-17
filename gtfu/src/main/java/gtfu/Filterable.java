package gtfu;

public interface Filterable {
    public static final String ROUTE = "routeID";
    public static final String TRIP = "tripID";

    public boolean matches(String key, String value);
}