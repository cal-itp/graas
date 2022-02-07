package gtfu.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import gtfu.Debug;
import gtfu.TripReportData;

class BBSAgentTest {
    private static final String[] AGENT_LIST = {
        "raspberry almanor-15 graas 0.1 (gulper)",
        "raspberry friendo",
        "raspberry",
        "(Macintosh; Intel Mac OS X 10_15_6)",
        "Linux; Android 11; SM-T727U)"
    };

    private static final String[] EXPECTED = {
        "graas 0.1 (gulper)",
        "raspberry almanor-15",
        "Raspberry Pi OS",
        "",
        "raspberry friendo",
        "Raspberry Pi OS",
        "Other",
        "Other",
        "Other",
        "Other",
        "Mac",
        "Mac OS X.10.15",
        "Android.11",
        "Samsung SM-T727U",
        "Android.11"
    };

    @Test
    @DisplayName("BBSAgentTest")
    void testAgent() {
        for (int i=0; i<AGENT_LIST.length; i++) {
            String agent = AGENT_LIST[i];

            TripReportData td = new TripReportData(
                "",
                "",
                0,
                0,
                "",
                agent,
                "",
                null,
                null
            );

            assertEquals(td.getAgent(), EXPECTED[3 * i + 0]);
            assertEquals(td.getDevice(), EXPECTED[3 * i + 1]);
            assertEquals(td.getOs(), EXPECTED[3 * i + 2]);
        }
    }
}
