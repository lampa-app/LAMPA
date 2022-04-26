package top.rootu.lampa.net;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class Http {
    public static int lastErrorCode = 1000;

    public static String getContent(HttpResponse response) throws Exception {
        int code = response.getStatusLine().getStatusCode();
        if (code != 200) {
            lastErrorCode = code;
            throw new Exception("Invalid response from server: " + response.getStatusLine().toString());
        }

        // Pull content stream from response
        HttpEntity entity = response.getEntity();
        InputStream inputStream = entity.getContent();

        ByteArrayOutputStream content = new ByteArrayOutputStream();

        // Read response into a buffered stream
        int readBytes;
        byte[] sBuffer = new byte[512];
        while ((readBytes = inputStream.read(sBuffer)) != -1) {
            content.write(sBuffer, 0, readBytes);
        }
        inputStream.close();

        // Return result from buffered stream
        return content.toString();
    }

    public static String Get(String url) throws Exception {
        HttpClient client = HttpHelper.createStandardHttpClient(false);
        HttpGet request = new HttpGet(url);
        HttpResponse response = client.execute(request);
        return getContent(response);
    }

    public static String Post(String url, String data, String contentType) throws Exception {
        HttpClient client = HttpHelper.createStandardHttpClient(false);
        HttpPost request = new HttpPost(url);
        StringEntity se = new StringEntity(data);
        request.setEntity(se);
        request.setHeader("Content-type", contentType);
        HttpResponse response = client.execute(request);
        return getContent(response);
    }
}
