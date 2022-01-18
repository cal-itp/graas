package gtfu.test;
import gtfu.Debug;
import gtfu.FailureReporter;
import gtfu.NullFailureReporter;
import gtfu.Util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestBadGTFS {

    @Test
    @DisplayName("TestBadGTFS")

    void TestBadGTFS() {
        FailureReporter reporter = new NullFailureReporter();
        Util.setReporter(reporter);

        int expectedFailures = 30;
        String cachePath = "src/test/resources/test-gtfs/bad-static-gtfs";
        String agencyID = "bad-static-gtfs";

        try {
            new LoadAgencyDataTest(cachePath, agencyID, false);
        } catch (Exception e) {
            Debug.error("* test failed: " + e);
        }

        assertEquals(reporter.getFailCount(), expectedFailures);
    }
}