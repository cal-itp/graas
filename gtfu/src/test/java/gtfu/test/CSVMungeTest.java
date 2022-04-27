package gtfu.test;
import gtfu.tools.AppEngineLogMunger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/*
 * Things tested:
 * - AppEngineLogMunger basic functionality
 * - CSV parsing throught CSVHEader and CSVRecord, particularly CSV records with embedded commas and escaped quotes
 */
public class CSVMungeTest {
    @Test
    @DisplayName("CSVMungeTest")
    void mungeCSV() throws Exception {
        AppEngineLogMunger m = new AppEngineLogMunger("src/test/resources/csv-munge/test.csv");

        String expected = "05:45:06.598 - data_str: {\"uuid\":\"stresstest\",\"agent\":[\"(Macintosh; Intel Mac OS X 10_15_7)\"],\"timestamp\":1650951906490,\"lat\":37.83915227205035,\"long\":-122.28377128957112,\"speed\":0,\"heading\":0,\"accuracy\":65,\"version\":\"0.14 (11/09/21)\",\"trip-id\":\"stresstest\",\"agency-id\":\"stress-test-1650951885510-agency-3\",\"vehicle-id\":\"stress-test-1650951885510-agency-3-4\",\"pos-timestamp\":1650951906490}";

        assertEquals(expected, m.getLines().get(0));
    }
}