package gtfu;

public class FeedInfo {
    private String startDate;
    private String endDate;

    public FeedInfo(String path) {
        TextFile tf = new TextFile(path + "/feed_info.txt");
        CSVHeader header = new CSVHeader(tf.getNextLine());
        String line = tf.getNextLine();
        if (line == null) return;

        CSVRecord r = new CSVRecord(header, line);
        startDate = r.get("feed_start_date");
        endDate = r.get("feed_end_date");
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }
}
