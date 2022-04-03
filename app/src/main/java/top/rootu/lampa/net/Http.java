package top.rootu.lampa.net;

import android.os.AsyncTask;
import android.util.Log;
import android.net.Uri;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.params.HttpParams;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HttpContext;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

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
        int readBytes = 0;
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
