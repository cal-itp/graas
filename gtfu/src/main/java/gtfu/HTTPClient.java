package gtfu;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import java.io.*;
import java.util.Collections;
import java.util.Map;

public class HTTPClient {

private static Map<String, String> EMPTY_MAP = Collections.EMPTY_MAP;

  static public Integer post(String url, String body) {
    return post(url, body, null);
  }

  static public Integer post(String url, String body, Map<String, String> headerMap) {
    //Debug.log("- url: " + url);
    //Debug.log("- body: " + body);

    int responseCode = 0;
    try{
      CloseableHttpClient client = HttpClients.createDefault();
      HttpPost post = new HttpPost(url);
      post.setEntity(EntityBuilder.create().setText(body).build());
      if (headerMap == null) {
        headerMap = EMPTY_MAP;
      }

      for (String key : headerMap.keySet()) {
          post.setHeader(key, headerMap.get(key));
      }

      CloseableHttpResponse response = client.execute(post);

      BufferedReader rd = new BufferedReader(new InputStreamReader( response.getEntity().getContent()));
      String line = "";
      while ((line = rd.readLine()) != null) {
        System.out.println(line);
      }
      responseCode = response.getCode();
      Debug.log("responseCode: " + responseCode);

    } catch (Exception e) {
      e.printStackTrace();
    } finally{
      return responseCode;
    }
  }
}