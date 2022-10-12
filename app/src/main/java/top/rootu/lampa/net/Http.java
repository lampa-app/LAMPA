package top.rootu.lampa.net;

import android.util.Log;

import com.btr.proxy.selector.pac.PacProxySelector;
import com.btr.proxy.selector.pac.PacScriptSource;
import com.btr.proxy.selector.pac.Proxy;
import com.btr.proxy.selector.pac.UrlPacScriptSource;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.entity.StringEntity;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;

public class Http {
    private int lastErrorCode = 0;
    private static PacProxySelector ps;

    public Http() {
        if (Http.ps == null) {
            PacScriptSource src = new UrlPacScriptSource("https://antizapret.prostovpn.org/proxy.pac");
            Http.ps = new PacProxySelector(src);
        }
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

        Proxy az = Http.getProxy(url);
        if (az != null) {
            HttpHost proxy = new HttpHost(az.host, az.port, az.type);
            client.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
        }
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

    public static Proxy getProxy(String url) {
        try {
            URI uri = new URI(url);
            List<Proxy> list = ps.select(uri);
            if (list != null && list.size() != 0) {

                Proxy p = list.get(0);

                Log.v("ProxyDroid.PAC", url);
                Log.v("ProxyDroid.PAC", p.host);
                Log.v("ProxyDroid.PAC", "" + p.port);
                Log.v("ProxyDroid.PAC", p.type);

                // No proxy means error
                if (p.equals(Proxy.NO_PROXY)
                        || p.host == null || p.port == 0 || p.type == null
                        || (!p.type.equals(Proxy.TYPE_HTTP) && !p.type.equals(Proxy.TYPE_HTTPS))
                ) {
                    return null;
                }
                return p;
            } else {
                // No proxy means error
                return null;
            }
        } catch (URISyntaxException ignore) {
        }
        return null;
    }
}
