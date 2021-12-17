package gtfu;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.SystemColor;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

public class AlertUI extends JFrame implements ActionListener {
    private static final String PRIVATE_KEY = "private-key";
    private static final String STATIC_GTFS_URL = "static-gtfs-url";
    private static final String POST_URL = "post-url";

    private JComboBox causeSelector;
    private JComboBox effectSelector;
    private JComboBox timeSelector;
    private JComboBox entitySelector;
    private JTextField entityChoice;
    private JTextField header;
    private JTextField urlTF;
    private JTextArea description;
    private JButton cancel;
    private JButton post;
    private Map<String, String> lookupMap;
    private Agency agency;
    private String hash;
    private PostInfo info;
    private String agencyID;
    private String routeID;
    private String tripID;
    private String stopID;

    private static final String NOT_SELECTED = "Not Selected";

    private static final String[] CAUSE_CHOICES = {
        "Unknown Cause", "Other Cause", "Technical Problem", "Strike",
        "Demonstration", "Accident", "Holiday", "Weather", "Maintenance",
        "Construction", "Police Activity", "Medical Emergency"
    };

    private static final String[] EFFECT_CHOICES = {
        "Unknown Effect",
        "No Service", "Reduced Service", "Significant Delays", "Detour",
        "Additional Service", "Modified Service", "Other Effect",
        "Stop Moved"
    };

    private static final String[] TIME_CHOICES = {
        "15 minutes", "30 minutes", "1 hour", "2 hours", "4 hours", "8 hours", "1 day", "1 week"
    };

    private static final String[] ENTITY_CHOICES = {
        NOT_SELECTED, "Agency", "Route", "Trip", "Stop"
    };

    public AlertUI(PostInfo info) {
        super("Post Alert");

        this.info = info;

        JPanel panel;
        String staticGTFSURL = info.staticGtfsUrl;

        hash = CryptoUtil.hash(staticGTFSURL);

        Util.updateCacheIfNeeded(Util.CACHE_ROOT, hash, staticGTFSURL, new ConsoleProgressObserver(40));
        agency = new Agency(Util.CACHE_ROOT + "/" + hash);

        Debug.log("AlertUI.AlertUI()");
        Debug.log("- hash: " + hash);
        Debug.log("- agency.getSanitizedName(): " + agency.getSanitizedName());

        setSize(400, 509);
        setResizable(false);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        add(makeLabel("Headline:"));

        header = new JTextField(30);
        header.addActionListener(this);
        add(header);

        add(new JSeparator(SwingConstants.HORIZONTAL));

        add(makeLabel("Description:"));

        description = new JTextArea(5, 30);
        //description.addActionListener(this);
        add(description);

        add(makeLabel("URL:"));

        urlTF = new JTextField(30);
        urlTF.addActionListener(this);
        add(urlTF);

        add(makeLabel("Valid For:"));

        panel = new JPanel();
        panel.setLayout(new BorderLayout());
        size(panel, getWidth() - 30, 35);

        timeSelector = new JComboBox(TIME_CHOICES);
        timeSelector.addActionListener(this);
        panel.add(timeSelector);
        add(panel);

        add(makeLabel("Affected Entity:"));

        entitySelector = new JComboBox(ENTITY_CHOICES);
        entitySelector.addActionListener(this);
        add(entitySelector);

        entityChoice = new JTextField(18);
        entityChoice.setEditable(false);
        add(entityChoice);

        add(makeLabel("Cause:"));

        panel = new JPanel();
        panel.setLayout(new BorderLayout());
        size(panel, getWidth() - 30, 35);

        causeSelector = new JComboBox(CAUSE_CHOICES);
        causeSelector.addActionListener(this);
        panel.add(causeSelector);
        add(panel);

        add(makeLabel("Effect:"));

        panel = new JPanel();
        panel.setLayout(new BorderLayout());
        size(panel, getWidth() - 30, 35);

        effectSelector = new JComboBox(EFFECT_CHOICES);
        effectSelector.addActionListener(this);
        panel.add(effectSelector);
        add(panel);

        panel = new JPanel();
        panel.setLayout(new GridLayout(1, 2));
        size(panel, getWidth() - 30, 35);

        cancel = new JButton("Cancel", new ImageIcon("img/cancel.png"));
        cancel.addActionListener(this);
        panel.add(cancel);

        post = new JButton("Post", new ImageIcon("img/confirm.png"));
        post.addActionListener(this);
        panel.add(post);

        add(panel);

        setLayout(new FlowLayout());
        setVisible(true);
    }

    private long getTimestamp(String s) {
        String[] arg = s.split(" ");
        int count = 0;
        String unit = arg[1];
        long base = Util.now() / 1000;

        try {
            count = Integer.parseInt(arg[0]);
        } catch (Exception e) {
            Debug.error("can't parse count '" + arg[0] + "'");
            return -1l;
        }

        if (unit.startsWith("minute")) {
            return base + count * Time.SECONDS_PER_MINUTE;
        } else if (unit.startsWith("hour")) {
            return base + count * Time.SECONDS_PER_HOUR;
        } else if (unit.startsWith("day")) {
            return base + count * Time.SECONDS_PER_DAY;
        } else if (unit.startsWith("week")) {
            return base + count * Time.SECONDS_PER_WEEK;
        } else {
            Debug.error("unknown unit '" + unit + "'");
            return -1;
        }
    }

    private void size(Component c, int width, int height) {
        c.setPreferredSize(new Dimension(width, height));
    }

    private JLabel makeLabel(String text) {
        return makeLabel(text, true);
    }

    private JLabel makeLabel(String text, boolean wide) {
        JLabel label = new JLabel(text);
        Font f = label.getFont();
        label.setFont(f.deriveFont(f.getStyle() | Font.BOLD));
        if (wide) size(label, getWidth(), 15);
        label.setBorder(new EmptyBorder(0, 18, 0, 0));
        return label;
    }

    private boolean hasEntitySelector() {
        return agencyID != null || routeID != null || tripID != null || stopID != null;
    }

    private void addEntitySelector(Map<String, Object> attributes) {
        if (agencyID != null) {
            attributes.put("agency_id", agencyID);
        } else if (routeID != null) {
            attributes.put("route_id", routeID);
        } else if (tripID != null) {
            attributes.put("trip_id", tripID);
        } else if (stopID != null) {
            attributes.put("stop_id", stopID);
        }
    }

    String getInputFromDialog(String title, String[] choices) {
        return (String)JOptionPane.showInputDialog(
            null,
            null,
            title,
            JOptionPane.QUESTION_MESSAGE,
            null,
            choices,
            choices[0]
        );
    }

    private String sanitize(String s) {
        //s = s.replace("'", "\\'");
        s = s.replace("\"", "\\\"");

        return s;
    }

    public void actionPerformed(ActionEvent e) {
        Debug.log("AlertUI.actionPerformed()");

        if (e.getSource() == entitySelector) {
            String entity = ((String)entitySelector.getSelectedItem()).toLowerCase();

            if (entity.equals("agency")) {
                entityChoice.setText(agency.getID());
                agencyID = agency.getID();
                routeID = null;
                tripID = null;
                stopID = null;
                Debug.log("- agencyID: " + agencyID);
            } else if (entity.equals("route")) {
                lookupMap = Util.getRouteMappings(Util.CACHE_ROOT + "/" + hash);
                String[] list = Util.getSortedKeys(lookupMap);
                String route = getInputFromDialog("Select Route", list);
                Debug.log("- route: " + route);
                entityChoice.setText(route);
                routeID = lookupMap.get(route);
                Debug.log("- routeID: " + routeID);
                agencyID = null;
                tripID = null;
                stopID = null;
            } else if (entity.equals("trip")) {
                lookupMap = Util.getTripMappings(Util.CACHE_ROOT + "/" + hash);
                String[] list = Util.getSortedKeys(lookupMap);
                String trip = getInputFromDialog("Select Trip", list);
                Debug.log("- trip: " + trip);
                entityChoice.setText(trip);
                tripID = lookupMap.get(trip);
                Debug.log("- tripID: " + tripID);
                agencyID = null;
                routeID = null;
                stopID = null;
            } else if (entity.equals("stop")) {
                lookupMap = Util.getStopMappings(Util.CACHE_ROOT + "/" + hash);
                String[] list = Util.getSortedKeys(lookupMap);
                String stop = getInputFromDialog("Select Stop", list);
                Debug.log("- stop: " + stop);
                entityChoice.setText(stop);
                stopID = lookupMap.get(stop);
                Debug.log("- stopID: " + stopID);
                agencyID = null;
                routeID = null;
                tripID = null;
            } else {
                entityChoice.setText("");
                agencyID = null;
                routeID = null;
                tripID = null;
                stopID = null;
            }
        }

        if (e.getSource() == cancel) {
            Debug.log("+ cancel");
            System.exit(0);
        }

        if (e.getSource() == post) {
            if (!checkInput()) return;

            Map<String, Object> attributes = new HashMap();
            attributes.put("agency_key", agency.getSanitizedName());
            addEntitySelector(attributes);

            if (!Util.isEmpty(header.getText())) {
                attributes.put("header", sanitize(header.getText()));
            }

            if (!Util.isEmpty(description.getText())) {
                attributes.put("description", sanitize(description.getText()));
            }

            if (!Util.isEmpty(urlTF.getText())) {
                attributes.put("url", sanitize(urlTF.getText()));
            }

            String cause = (String)causeSelector.getSelectedItem();

            if (!cause.equals(NOT_SELECTED)) {
                attributes.put("cause", cause);
            }

            String effect = (String)effectSelector.getSelectedItem();

            if (!effect.equals(NOT_SELECTED)) {
                attributes.put("effect", effect);
            }

            attributes.put("time_start", Util.now() / 1000);
            attributes.put("time_stop", getTimestamp((String)timeSelector.getSelectedItem()));

            GTFSRTUtil.postAlertFromPrivateKeyFile(info.postUrl, attributes, info.privateKeyFile);
        }
    }

    private boolean checkInput() {
        if (!hasEntitySelector()) {
            JOptionPane.showMessageDialog(null, "Must select an entity");
            return false;
        }

        return true;
    }

    private static void usage() {
        System.err.println("usage: AlertUI <path-to-config-file>");
        System.err.println("vvvv example config file:");
        System.err.println("{");
        System.err.println("    \"private_key_file\": \"/Users/foo/id_rsa\",");
        System.err.println("    \"static_gtfs_url\": \"http://bar.com/gtfs.zip\",");
        System.err.println("    \"post_url\": \"https://gtfs-rt-server.com/post-alert\"");
        System.err.println("}");
        System.err.println("^^^^");

        System.exit(-1);
    }

    public static void main(String[] arg) {
        if (arg.length == 0) {
            usage();
        }

        PostInfo info = (PostInfo)Util.readJSONObjectFromFile(arg[0], PostInfo.class);
        new AlertUI(info);
    }
}

class PostInfo {
    public String privateKeyFile;
    public String staticGtfsUrl;
    public String postUrl;
}