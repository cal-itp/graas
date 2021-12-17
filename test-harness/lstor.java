import java.io.File;
import java.sql.DriverManager;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;

public class lstor {
    private static String dumpBytes(byte[] buf) {
        StringBuffer sb = new StringBuffer();

        for (byte b : buf) {
            if (sb.length() > 0) {
                sb.append(' ');
            }

            sb.append(String.format("%02x", b & 0xff));
        }

        return sb.toString();
    }

    private static void log(String s) {
        System.out.println(s);
    }

    private Connection connect(String url) {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return conn;
    }

    public void read(String url, String key) throws Exception {
        String sql = "SELECT key, value FROM itemtable WHERE key='" + key + "'";
        //log("- sql: " + sql);

        try (Connection conn = connect(url);
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                System.out.println(
                    rs.getString("key") + ": " +
                    new String(rs.getBytes("value"), "UTF-16LE")
                );
            } else {
                System.out.println("* no results found for '" + key + "'");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void write(String url, String key, String value) throws Exception {
        String sql = "SELECT key FROM itemtable WHERE key='" + key + "'";
        //log("- sql: " + sql);

        try (Connection conn = connect(url);
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                sql = "UPDATE itemtable SET key = ? , "
                    + "value = ? "
                    + "WHERE key = ?";
                //log("- sql: " + sql);

                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, key);
                    pstmt.setBytes(2, value.getBytes("UTF-16LE"));
                    pstmt.setString(3, key);

                    pstmt.executeUpdate();
                }
            } else {
                sql = "INSERT INTO itemtable(key, value) VALUES(?,?)";
                //log("- sql: " + sql);

                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, key);
                    pstmt.setBytes(2, value.getBytes("UTF-16LE"));
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.";

    private static String sanitize(String input) {
        StringBuffer sb = new StringBuffer();

        for (int i=0; i<input.length(); i++) {
            char c = input.charAt(i);

            if (ALPHABET.indexOf(c) >= 0) {
                sb.append(c);
            } else if (sb.charAt(sb.length() - 1) != '_') {
                sb.append('_');
            }
        }

        return sb.toString();
    }

    private static final void usage() {
        System.err.println("usage: lstor -read -url <url> -key <key>");
        System.err.println("usage: lstor -write -url <url> -key <key> -value <value>");
        System.exit(1);
    }

    public static void main(String[] args) throws Exception {
        String cmd = null;
        String url = null;
        String key = null;
        String value = null;

        // currently, only safari is supported
        String path = System.getProperty("user.home") + "/Library/Safari/LocalStorage/";

        for (int i=0; i<args.length; i++) {
            if (args[i].equals("-read")) {
                cmd = "read";
                continue;
            }

            if (args[i].equals("-write")) {
                cmd = "write";
                continue;
            }

            if (args[i].equals("-url") && i < args.length - 1) {
                url = args[++i];
                continue;
            }

            if (args[i].equals("-key") && i < args.length - 1) {
                key = args[++i];
                continue;
            }

            if (args[i].equals("-value") && i < args.length - 1) {
                value = args[++i];
                continue;
            }

            usage();
        }

        if (cmd == null || url == null || key == null || (cmd.equals("write") && value == null) || (cmd.equals("read") && value != null)) {
            usage();
        }

        //log("- cmd: " + cmd);
        //log("- url: " + url);
        //log("- key: " + key);
        //log("- value: " + value);

        String filename = sanitize(url);
        //log("- filename: " + filename);

        String dbName = path + filename + ".localstorage";
        //log("- dbName: " + dbName);

        File file = new File(dbName);

        String dbURL = "jdbc:sqlite:" + file.toURI().toURL().getPath();
        //log("- dbURL: " + dbURL);

        lstor lstor = new lstor();

        if (cmd.equals("write")) {
            lstor.write(dbURL, key, value);
        } else {
            lstor.read(dbURL, key);
        }
    }

}