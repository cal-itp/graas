package gtfu.test;
import gtfu.Debug;
import gtfu.FailureReporter;
import gtfu.NullFailureReporter;
import gtfu.Util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class TestBadStaticGTFS {

    @Test
    @DisplayName("TestBadStaticGTFS")

    void TestBadStaticGTFS() {
        FailureReporter reporter = new NullFailureReporter();
        Util.setReporter(reporter);

        int expectedFailures = 31;
        String cachePath = "src/test/resources/test-gtfs/";
        String agencyID = "bad-static-gtfs";

        try {
            new LoadAgencyDataTest(cachePath, agencyID, false);
        } catch (Exception e) {
            Debug.error("* test failed: " + e);
        }

        assertEquals(expectedFailures, reporter.getFailCount());
    }
}