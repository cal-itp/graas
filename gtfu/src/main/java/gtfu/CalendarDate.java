package gtfu;

import java.util.Date;

public class CalendarDate {

    private String serviceID;
    private String date;
    private int exceptionType;

    public CalendarDate(String serviceID, String date, int exceptionType) {
        this.serviceID = serviceID;
        this.date = date;
        this.exceptionType = exceptionType;
    }

    public String getServiceID() {
        return serviceID;
    }

    public String getDate() {
        return date;
    }

    public int getExceptionType() {
        return exceptionType;
    }
}
