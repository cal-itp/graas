package gtfu.test;

import gtfu.Debug;
// import static org.junit.jupiter.api.Assertions.assertEquals;

// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.CsvSource;
// import gtfu.tools.AgencyYML;

class TestLoadBadGTFS {

    // @Test
    // @DisplayName("TestLoadBadGTFS")
    private static void TestLoadBadGTFS() {
        String cacheDir = "dirname";
        String agencyID = "id";
        try {
            new LoadAgencyDataTest(cacheDir, agencyID);
        } catch (Exception e) {
            Debug.error("* test failed: " + e);
        }
    }
    public static void main(String[] arg) throws Exception {
        TestLoadBadGTFS();
    }
}