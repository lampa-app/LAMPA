package top.rootu.lampa.net;

import android.util.Log;

import com.btr.proxy.selector.pac.PacProxySelector;
import com.btr.proxy.selector.pac.PacScriptSource;
import com.btr.proxy.selector.pac.UrlPacScriptSource;

import org.json.JSONObject;

import java.util.Iterator;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class Http {
    private int lastErrorCode = 0;
    private static String pacUrl = "";
    private static PacProxySelector ps;
    private static boolean disableH2 = false;

    public Http() {
    }

    private Request.Builder getReqBuilder(String url, JSONObject headers) {
        Log.d("ProxyDroid.PAC", url);
        String ua = HttpHelper.userAgent;
        Request.Builder rb = new Request.Builder().url(url);
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
                        rb.header(key, val);
                }
            }
        }
        rb.header("User-Agent", ua);
        return rb;
    }

    public String Get(String url, JSONObject headers, int timeout) throws Exception {
        Request.Builder rb = getReqBuilder(url, headers);
        OkHttpClient client = HttpHelper.getOkHttpClient(timeout);
        Response response = client.newCall(rb.build()).execute();
        if (!response.isSuccessful()) {
            this.lastErrorCode = response.code();
            throw new Exception("Invalid response from server: " + response.code() + " " + response.message());
        }
        ResponseBody body = response.body();
        assert body != null;
        return body.string();
    }

    public String Post(String url, String data, JSONObject headers, int timeout) throws Exception {
        Request.Builder rb = getReqBuilder(url, headers);
        RequestBody requestBody = RequestBody.create(
                MediaType.parse(headers.optString("Content-Type", "application/x-www-form-urlencoded")),
                data
        );
        rb.post(requestBody);
        OkHttpClient client = HttpHelper.getOkHttpClient(timeout);
        Response response = client.newCall(rb.build()).execute();
        if (!response.isSuccessful()) {
            this.lastErrorCode = response.code();
            throw new Exception("Invalid response from server: " + response.code() + " " + response.message());
        }
        ResponseBody body = response.body();
        assert body != null;
        return body.string();
    }

    public int getLastErrorCode() {
        return lastErrorCode;
    }

    public static PacProxySelector getProxySelector() {
        if (ps == null && !pacUrl.isEmpty()) {
            PacScriptSource src = new UrlPacScriptSource(pacUrl);
            ps = new PacProxySelector(src);
        }
        return ps;
    }

    public static boolean setProxyPAC(String url) {
        if (pacUrl.isEmpty() || !url.equalsIgnoreCase(pacUrl)) {
            pacUrl = url;
            ps = null;
        }
        return true;
    }

    public static String getProxyPAC() {
        return pacUrl;
    }

    public static void disableH2(boolean bool) {
        disableH2 = bool;
    }

    public static boolean isDisableH2() {
        return disableH2;
    }
}
