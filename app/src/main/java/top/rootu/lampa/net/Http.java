package top.rootu.lampa.net;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Iterator;

public class Http {
    private int lastErrorCode = 0;

    public Http() {
    }

    public String getContent(HttpResponse response) throws Exception {
        int code = response.getStatusLine().getStatusCode();
        if (code != 200) {
            this.lastErrorCode = code;
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

    public String Get(String url, JSONObject headers, int timeout) throws Exception {
        String ua = HttpHelper.userAgent;
        HttpGet request = new HttpGet(url);
        if (headers != null) {
            Iterator<String> keys = headers.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String val = headers.optString(key);
                switch (key.toLowerCase()) {
                    case "user-agent":
                        ua = val;
                    case "content-length":
                        break;
                    default:
                        request.setHeader(key, val);
                }
            }
        }
        HttpClient client = HttpHelper.createStandardHttpClient(true, ua, timeout);
        HttpResponse response = client.execute(request);
        return getContent(response);
    }

    public String Post(String url, String data, String contentType, JSONObject headers, int timeout) throws Exception {
        String ua = HttpHelper.userAgent;
        HttpPost request = new HttpPost(url);
        StringEntity se = new StringEntity(data);
        request.setEntity(se);
        boolean setContentType = false;
        if (headers != null) {
            Iterator<String> keys = headers.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String val = headers.optString(key);
                request.setHeader(key, val);
                switch (key.toLowerCase()) {
                    case "user-agent":
                        ua = val;
                    case "content-length":
                        break;
                    case "content-type":
                        setContentType = true;
                    default:
                        request.setHeader(key, val);
                }
            }
        }
        if (!setContentType && !contentType.isEmpty()) {
            request.setHeader("Content-Type", contentType);
        }
        HttpClient client = HttpHelper.createStandardHttpClient(true, ua, timeout);
        HttpResponse response = client.execute(request);
        return getContent(response);
    }

    public int getLastErrorCode() {
        return lastErrorCode;
    }
}
