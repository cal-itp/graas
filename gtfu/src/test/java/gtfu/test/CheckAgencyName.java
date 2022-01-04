package gtfu.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import gtfu.tools.AgencyYML;

class CheckAgencyName {

    @Test
    @DisplayName("CheckAgencyName")
    void checkName() {
        AgencyYML agencies = new AgencyYML();
        assertEquals("Tulare County Regional Transit Agency", agencies.getName("tcrta"));
    }
}