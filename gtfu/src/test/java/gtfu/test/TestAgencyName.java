package gtfu.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import gtfu.tools.AgencyYML;

class TestAgencyName {

    @Test
    @DisplayName("TestAgencyName")
    void testName() {
        AgencyYML agencies = new AgencyYML();
        // Not ideal to hardcode an agency name into unit test, but it also
        // wouldn't be a good idea to add test agencies into agencies.yml.
        // Open to better solutions here.
        assertEquals("Tulare County Regional Transit Agency", agencies.getName("tcrta"));
    }
}