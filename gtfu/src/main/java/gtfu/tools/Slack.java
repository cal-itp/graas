package gtfu.tools;
import gtfu.HTTPClient;
import gtfu.Debug;

/**
 * Interact with Slack
 * @see <a href="https://slack.dev/java-slack-sdk/guides/incoming-webhooks</a>
 */
public class Slack {
    private String webHookURL;

    /**
     * Initialize Slack connection
     * @throws Exception
     */
    public Slack() throws Exception {
        this.webHookURL = System.getenv("GRAAS_BOT_WEBHOOK");
        if(this.webHookURL == null) {
            throw new Exception("No env variable found with name GRAAS_BOT_WEBHOOK");
        }
    }

    /**
     * Send the message
     * @return Slack API response
     */
    public int send(String message) {
        int responseCode = 0;
        String payload = "{\"text\":\"" + message + "\"}";
        responseCode = HTTPClient.post(webHookURL, payload);
        return responseCode;
    }
}

