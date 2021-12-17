package gtfu;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigProperties extends Properties {
    public ConfigProperties(String filename, String[] requiredProps) {
        try (FileInputStream fis = new FileInputStream(filename)) {
            load(fis);

            for (String req : requiredProps) {
                if (get(req) == null) throw new Fail("missing required property: " + req);
            }
        } catch (IOException e) {
            throw new Fail(e);
        }
    }
}