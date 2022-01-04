package gtfu.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import gtfu.Recipients;

class CheckReportRecipients {

    @Test
    @DisplayName("CheckReportRecipients")
    void CheckReportRecipients() {
        Recipients r = new Recipients();
        assertNotNull(r.get("graas_report"));
    }
}