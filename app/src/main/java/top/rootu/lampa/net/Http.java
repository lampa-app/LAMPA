package top.rootu.lampa.net;

import android.util.Log;

import com.btr.proxy.selector.pac.PacProxySelector;
import com.btr.proxy.selector.pac.PacScriptSource;
import com.btr.proxy.selector.pac.UrlPacScriptSource;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

import okhttp3.Headers;
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
    private static final long MAX_ALLOWED_BODY_SIZE = 2 * 1024 * 1024; // 2MB in bytes

    public Http() {
    }

    private Request.Builder getReqBuilder(String url, JSONObject headers) {
        Log.d("LampaHttp.RB", url);
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

    private JSONObject getJsonFromResponse(Response response) throws Exception {
        if (!response.isSuccessful()) {
            this.lastErrorCode = response.code();
            throw new Exception("Invalid response from server: " + response.code() + " " + response.message());
        }
        Headers rh = response.headers();
        JSONObject jsonHeaders = new JSONObject();
        for (int i = 0, size = rh.size(); i < size; i++) {
            String hName = rh.name(i).toLowerCase();
            if (hName.equals("set-cookie")) {
                if (!jsonHeaders.has(hName)) jsonHeaders.put(hName, new JSONArray());
                JSONArray hValues = jsonHeaders.getJSONArray(hName);
                hValues.put(hValues.length(), rh.value(i));
            } else if (jsonHeaders.has(hName)) {
                String hValue = jsonHeaders.optString(hName, "");
                // see https://datatracker.ietf.org/doc/html/rfc7230#section-3.2.2
                jsonHeaders.put(hName, hValue + ", " + rh.value(i));
            } else {
                jsonHeaders.put(hName, rh.value(i));
            }
        }
        ResponseBody body = response.body();
        assert response.networkResponse() != null;
        assert body != null;
        JSONObject json = new JSONObject();
        try {
            json.put("headers", jsonHeaders);
            json.put("currentUrl", response.request().url().toString());
            // Avoid OOM
            if (body.contentLength() > MAX_ALLOWED_BODY_SIZE) {
                json.put("body", "[Response too large, skipped]");
            } else {
                json.put("body", body.string());
            }
        } catch (JSONException jsonException) {
            jsonException.printStackTrace();
        }
        return json;
    }

    public JSONObject Get(String url, JSONObject headers, int timeout) throws Exception {
        if (!url.toLowerCase().startsWith("http")) {
            this.lastErrorCode = 400;
            throw new Exception("Bad Request (Invalid protocol; use http or https)");
        }
        Request.Builder rb = getReqBuilder(url, headers);
        OkHttpClient client = HttpHelper.getOkHttpClient(timeout);
        Response response = client.newCall(rb.build()).execute();
        return getJsonFromResponse(response);
    }

    public JSONObject Post(String url, String data, JSONObject headers, int timeout) throws Exception {
        if (!url.toLowerCase().startsWith("http")) {
            this.lastErrorCode = 400;
            throw new Exception("Bad Request (Invalid protocol; use http or https)");
        }
        Request.Builder rb = getReqBuilder(url, headers);
        RequestBody requestBody = RequestBody.create(
                MediaType.parse(headers.optString("Content-Type", "application/x-www-form-urlencoded")),
                data
        );
        rb.post(requestBody);
        OkHttpClient client = HttpHelper.getOkHttpClient(timeout);
        Response response = client.newCall(rb.build()).execute();
        return getJsonFromResponse(response);
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
