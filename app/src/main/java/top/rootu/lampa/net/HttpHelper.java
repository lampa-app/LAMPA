package top.rootu.lampa.net;

import android.net.Uri;
import android.os.Build;

import com.btr.proxy.selector.pac.PacProxySelector;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
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
import org.brotli.dec.BrotliInputStream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import top.rootu.lampa.helpers.Helpers;

/**
 * Provides a set of general helper methods that can be used in web-based communication.
 *
 * @author erickok
 */
public class HttpHelper {

    public static final int DEFAULT_CONNECTION_TIMEOUT = 15000;

    /**
     * The 'User-Agent' name to send to the server
     */
    public static String userAgent = "Mozilla/5.0";

    private static InetAddress getByIp(String host) {
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            // unlikely
            throw new RuntimeException(e);
        }
    }

    public static OkHttpClient getOkHttpClient(int timeout) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        if (Http.isDisableH2()) {
            Http.disableH2(false);
            builder.protocols(Collections.singletonList(Protocol.HTTP_1_1));
        }
        try {
            // use Conscrypt for TLS on Android < 10 and trust all certs
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                    && (!Helpers.isBrokenTCL() && !Helpers.isWisdomShare())
            ) {
                builder.sslSocketFactory(
                        new TlsSocketFactory(TlsSocketFactory.TLS_MODERN),
                        TlsSocketFactory.trustAllCerts
                );
                builder.hostnameVerifier((hostname, session) -> true);
            }
            // https://github.com/square/okhttp/issues/3894
            // The default OkHttp configuration does not support older versions of TLS,
            // or all cipher suites.  Make our support as reasonably broad as possible.
            builder.connectionSpecs(Arrays.asList(ConnectionSpec.CLEARTEXT,
                    new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                            .allEnabledTlsVersions()
                            .allEnabledCipherSuites()
                            .build()));
            // builder.connectionSpecs(Collections.singletonList(ConnectionSpec.MODERN_TLS));
            // https://gist.github.com/Karewan/4b0270755e7053b471fdca4419467216
            // For OkHttp 3.12.x
            // ConnectionSpec.COMPATIBLE_TLS = TLS1.0
            // ConnectionSpec.MODERN_TLS = TLS1.0 + TLS1.1 + TLS1.2 + TLS 1.3
            // ConnectionSpec.RESTRICTED_TLS = TLS 1.2 + TLS 1.3
        } catch (NoSuchAlgorithmException | KeyManagementException ignore) {
        }
        if (timeout > 0) {
            builder.connectTimeout(timeout / 2L, TimeUnit.MILLISECONDS);
            builder.readTimeout(timeout, TimeUnit.MILLISECONDS);
            builder.writeTimeout(timeout, TimeUnit.MILLISECONDS);
            builder.callTimeout(timeout, TimeUnit.MILLISECONDS);
        } else {
            builder.connectTimeout(DEFAULT_CONNECTION_TIMEOUT / 2L, TimeUnit.MILLISECONDS);
            builder.readTimeout(DEFAULT_CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);
            builder.writeTimeout(DEFAULT_CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);
            builder.callTimeout(DEFAULT_CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);
        }
        PacProxySelector ps = Http.getProxySelector();
        if (ps != null) {
            builder.proxySelector(ps);
        }
        return builder.build();
    }

    /**
     * HTTP request interceptor to allow for gzip, deflate, br-encoded data transfer
     */
    public static HttpRequestInterceptor encodingRequestInterceptor = (request, context) -> {
        if (!request.containsHeader("Accept-Encoding")) {
            request.addHeader("Accept-Encoding", "gzip, deflate, br");
        }
        if (!request.containsHeader("Accept")) {
            request.addHeader(
                    "Accept",
                    "*/*"
            );
        }
    };
    /**
     * HTTP response interceptor that decodes gzip, deflate, br data
     */
    public static HttpResponseInterceptor encodingResponseInterceptor = (response, context) -> {
        HttpEntity entity = response.getEntity();
        Header ceheader = entity.getContentEncoding();
        if (ceheader != null) {
            HeaderElement[] codecs = ceheader.getElements();
            for (HeaderElement codec : codecs) {
                if (codec.getName().equalsIgnoreCase("gzip")) {
                    response.setEntity(new GzipDecompressingEntity(response.getEntity()));
                    return;
                }
                if (codec.getName().equalsIgnoreCase("deflate")) {
                    response.setEntity(new DeflateDecompressingEntity(response.getEntity()));
                    return;
                }
                if (codec.getName().equalsIgnoreCase("br")) {
                    response.setEntity(new BrotliDecompressingEntity(response.getEntity()));
                    return;
                }
            }
        }
    };

    /**
     * Creates a standard Apache HttpClient that is thread safe, supports different SSL auth methods and basic
     * authentication
     *
     * @param sslTrustAll Whether to trust all SSL certificates
     * @param sslTrustKey A specific SSL key to accept exclusively
     * @param timeout     The connection timeout for all requests
     * @param authAddress The authentication domain address
     * @param authPort    The authentication domain port number
     * @return An HttpClient that should be stored locally and reused for every new request
     * @throws Exception Thrown when information (such as username/password) is missing
     */
    public static DefaultHttpClient createStandardHttpClient(boolean userBasicAuth, String username, String password, String authToken,
                                                             boolean sslTrustAll, String sslTrustKey, int timeout,
                                                             String authAddress, int authPort) throws Exception {

        // Register http and https sockets
        SchemeRegistry registry = new SchemeRegistry();
        SocketFactory httpsSocketFactory;
        if (sslTrustKey != null && sslTrustKey.length() != 0) {
            httpsSocketFactory = new TlsSniSocketFactory(sslTrustKey);
        } else if (sslTrustAll) {
            httpsSocketFactory = new TlsSniSocketFactory(true);
        } else {
            httpsSocketFactory = new TlsSniSocketFactory();
        }
        registry.register(new Scheme("http", new PlainSocketFactory(), 80));
        registry.register(new Scheme("https", httpsSocketFactory, 443));

        // Standard parameters
        HttpParams httpparams = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpparams, timeout);
        HttpConnectionParams.setSoTimeout(httpparams, timeout);
        if (userAgent != null) {
            HttpProtocolParams.setUserAgent(httpparams, userAgent);
        }

        DefaultHttpClient httpclient =
                new DefaultHttpClient(new ThreadSafeClientConnManager(httpparams, registry), httpparams);

        // Authentication credentials
        if (userBasicAuth) {
            if (username == null || password == null) {
                throw new Exception("No username or password was provided while we had authentication enabled");
            }
            httpclient.getCredentialsProvider()
                    .setCredentials(new AuthScope(authAddress, authPort, AuthScope.ANY_REALM),
                            new UsernamePasswordCredentials(username, password));
        }

        // Auth token header
        if (authToken != null) {
            httpclient.addRequestInterceptor((httpRequest, httpContext) ->
                    httpRequest.addHeader("Authorization", "Bearer " + authToken));
        }

        return httpclient;

    }

    public static DefaultHttpClient createStandardHttpClient(boolean sslTrustAll) {
        return createStandardHttpClient(sslTrustAll, null);
    }

    public static DefaultHttpClient createStandardHttpClient(boolean sslTrustAll, String ua) {
        return createStandardHttpClient(sslTrustAll, null, DEFAULT_CONNECTION_TIMEOUT);
    }

    public static DefaultHttpClient createStandardHttpClient(boolean sslTrustAll, String ua, int timeout) {

        // Register http and https sockets
        SchemeRegistry registry = new SchemeRegistry();
        SocketFactory httpsSocketFactory;
        if (sslTrustAll) {
            httpsSocketFactory = new TlsSniSocketFactory(true, timeout);
        } else {
            httpsSocketFactory = new TlsSniSocketFactory(timeout);
        }
        registry.register(new Scheme("http", new PlainSocketFactory(), 80));
        registry.register(new Scheme("https", httpsSocketFactory, 443));

        // Standard parameters
        HttpParams httpparams = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpparams, timeout);
        HttpConnectionParams.setSoTimeout(httpparams, timeout);
        if (ua != null) {
            HttpProtocolParams.setUserAgent(httpparams, ua);
        } else if (userAgent != null) {
            HttpProtocolParams.setUserAgent(httpparams, userAgent);
        }

        DefaultHttpClient httpclient =
                new DefaultHttpClient(new ThreadSafeClientConnManager(httpparams, registry), httpparams);

        // add gzip, deflate, br support
        httpclient.addRequestInterceptor(encodingRequestInterceptor);
        httpclient.addResponseInterceptor(encodingResponseInterceptor);

        return httpclient;

    }

    /*
     * To convert the InputStream to String we use the BufferedReader.readLine() method. We iterate until the
     * BufferedReader return null which means there's no more data to read. Each line will appended to a StringBuilder
     * and returned as String.
     *
     * Taken from http://senior.ceng.metu.edu.tr/2009/praeda/2009/01/11/a-simple-restful-client-at-android/
     */
    public static String convertStreamToString(InputStream is, String encoding) throws UnsupportedEncodingException {
        InputStreamReader isr;
        if (encoding != null) {
            isr = new InputStreamReader(is, encoding);
        } else {
            isr = new InputStreamReader(is);
        }
        BufferedReader reader = new BufferedReader(isr);
        StringBuilder sb = new StringBuilder();

        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }

    public static String convertStreamToString(InputStream is) {
        try {
            return convertStreamToString(is, null);
        } catch (UnsupportedEncodingException e) {
            // Since this is going to use the default encoding, it is never going to crash on an
            // UnsupportedEncodingException
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Parses the individual parameters from a textual cookie representation-like string and returns them in an unsorted
     * map. Inspired by Android's (API level 11+) getQueryParameterNames(Uri).
     *
     * @param raw A string of the form key1=value1;key2=value
     * @return An unsorted, unmodifiable map of pairs of string keys and string values
     */
    public static Map<String, String> parseCookiePairs(String raw) {

        Map<String, String> pairs = new HashMap<>();
        int start = 0;
        do {
            int next = raw.indexOf(';', start);
            int end = (next == -1) ? raw.length() : next;
            int separator = raw.indexOf('=', start);
            if (separator > end || separator == -1) {
                separator = end;
            }

            String name = raw.substring(start, separator);
            String value = raw.substring(separator + 1, end);
            pairs.put(Uri.decode(name), Uri.decode(value));

            start = end + 1;
        } while (start < raw.length());

        return Collections.unmodifiableMap(pairs);

    }

    /**
     * HTTP entity wrapper to decompress GZipped HTTP responses
     */
    private static class GzipDecompressingEntity extends HttpEntityWrapper {

        public GzipDecompressingEntity(final HttpEntity entity) {
            super(entity);
        }

        @Override
        public InputStream getContent() throws IOException, IllegalStateException {

            // the wrapped entity's getContent() decides about repeatability
            InputStream wrappedin = wrappedEntity.getContent();
            return new GZIPInputStream(wrappedin);
        }

        @Override
        public long getContentLength() {
            // length of ungzipped content is not known
            return -1;
        }

    }

    /**
     * HTTP entity wrapper to decompress deflate HTTP responses
     */
    private static class DeflateDecompressingEntity extends HttpEntityWrapper {

        public DeflateDecompressingEntity(final HttpEntity entity) {
            super(entity);
        }

        @Override
        public InputStream getContent() throws IOException, IllegalStateException {

            // the wrapped entity's getContent() decides about repeatability
            InputStream wrappedin = wrappedEntity.getContent();
            return new InflaterInputStream(wrappedin);
        }

        @Override
        public long getContentLength() {
            return -1;
        }
    }

    /**
     * HTTP entity wrapper to decompress br HTTP responses
     */
    private static class BrotliDecompressingEntity extends HttpEntityWrapper {

        public BrotliDecompressingEntity(final HttpEntity entity) {
            super(entity);
        }

        @Override
        public InputStream getContent() throws IOException, IllegalStateException {

            // the wrapped entity's getContent() decides about repeatability
            InputStream wrappedin = wrappedEntity.getContent();
            return new BrotliInputStream(wrappedin);
        }

        @Override
        public long getContentLength() {
            return -1;
        }
    }
}