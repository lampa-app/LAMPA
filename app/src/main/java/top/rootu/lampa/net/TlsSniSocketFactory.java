package top.rootu.lampa.net;

import android.annotation.SuppressLint;
import android.net.SSLCertificateSocketFactory;
import android.os.Build;
import android.util.Log;

import org.apache.http.conn.scheme.LayeredSocketFactory;
import org.apache.http.conn.ssl.StrictHostnameVerifier;
import org.apache.http.params.HttpParams;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

/**
 * Implements an HttpClient socket factory with extensive support for SSL. Many thanks to
 * http://blog.dev001.net/post/67082904181/android-using-sni-and-tlsv1-2-with-apache-httpclient for the base
 * implementation.
 * <p/>
 * Firstly, all SSL protocols that a particular Android version support will be enabled (according to
 * http://developer.android.com/reference/javax/net/ssl/SSLSocket.html). This currently includes SSL v3 and TLSv1.0,
 * v1.1 and v1.2.
 * <p/>
 * Second, SNI is supported for host name verification. For Android 4.2+, which supports it natively, the default
 * (strict) hostname verifier is used. For Android 4.1 and earlier it is possibly supported through reflexion on the
 * same methods.
 * <p/>
 * Third, self-signed certificates are supported through the checking of the received certificate key with a given SHA-1
 * encoded hex of the self-signed certificate key. When a key is given but not a correct match, the thumbprint of the
 * server certificate is given, such that the correct SHA-1 hash to use can be foudn in the log.
 * <p/>
 * Finally, the ignoring of all SSL certificates (and hostname) is possible (which is obviously very insecure!).
 */
public class TlsSniSocketFactory implements LayeredSocketFactory {
    private static final String[] TLS_V12_ONLY = {"TLSv1.2"};

    private final static HostnameVerifier hostnameVerifier = new StrictHostnameVerifier();
    public static int DEFAULT_HANDSHAKE_TIMEOUT = 15000;

    private final boolean acceptAllCertificates;
    private final String selfSignedCertificateKey;
    private final int handshakeTimeoutMillis;

    public TlsSniSocketFactory() {
        this.acceptAllCertificates = false;
        this.selfSignedCertificateKey = null;
        this.handshakeTimeoutMillis = DEFAULT_HANDSHAKE_TIMEOUT;
    }

    public TlsSniSocketFactory(int handshakeTimeoutMillis) {
        this.acceptAllCertificates = false;
        this.selfSignedCertificateKey = null;
        this.handshakeTimeoutMillis = handshakeTimeoutMillis;
    }

    public TlsSniSocketFactory(String certKey) {
        this.acceptAllCertificates = false;
        this.selfSignedCertificateKey = certKey;
        this.handshakeTimeoutMillis = DEFAULT_HANDSHAKE_TIMEOUT;
    }

    public TlsSniSocketFactory(String certKey, int handshakeTimeoutMillis) {
        this.acceptAllCertificates = false;
        this.selfSignedCertificateKey = certKey;
        this.handshakeTimeoutMillis = handshakeTimeoutMillis;
    }

    public TlsSniSocketFactory(boolean acceptAllCertificates) {
        this.acceptAllCertificates = acceptAllCertificates;
        this.selfSignedCertificateKey = null;
        this.handshakeTimeoutMillis = DEFAULT_HANDSHAKE_TIMEOUT;
    }

    public TlsSniSocketFactory(boolean acceptAllCertificates, int handshakeTimeoutMillis) {
        this.acceptAllCertificates = acceptAllCertificates;
        this.selfSignedCertificateKey = null;
        this.handshakeTimeoutMillis = handshakeTimeoutMillis;
    }

    // Plain TCP/IP (layer below TLS)

    @Override
    public Socket connectSocket(Socket s, String host, int port, InetAddress localAddress, int localPort,
                                HttpParams params) {
        return null;
    }

    @Override
    public Socket createSocket() {
        return null;
    }

    @Override
    public boolean isSecure(Socket s) throws IllegalArgumentException {
        if (s instanceof SSLSocket) {
            return s.isConnected();
        }
        return false;
    }

    // TLS layer

    @Override
    public Socket createSocket(Socket plainSocket, String host, int port, boolean autoClose) throws IOException {
        if (autoClose) {
            // we don't need the plainSocket
            plainSocket.close();
        }

        SSLCertificateSocketFactory sslSocketFactory =
                (SSLCertificateSocketFactory) SSLCertificateSocketFactory.getDefault(this.handshakeTimeoutMillis);

        // For self-signed certificates use a custom trust manager
        if (acceptAllCertificates) {
            sslSocketFactory.setTrustManagers(new TrustManager[]{new IgnoreSSLTrustManager()});
        } else if (selfSignedCertificateKey != null) {
            sslSocketFactory.setTrustManagers(new TrustManager[]{new SelfSignedTrustManager(selfSignedCertificateKey)});
        }

        // create and connect SSL socket, but don't do hostname/certificate verification yet
        @SuppressLint("SSLCertificateSocketFactoryCreateSocket") SSLSocket ssl = (SSLSocket) sslSocketFactory.createSocket(InetAddress.getByName(host), port);

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            // enable TLSv1.2 only
            ssl.setEnabledProtocols(TLS_V12_ONLY);
        } else {
            ssl.setEnabledProtocols(ssl.getSupportedProtocols());
        }

        // set up SNI before the handshake
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            sslSocketFactory.setHostname(ssl, host);
        } else {
            try {
                java.lang.reflect.Method setHostnameMethod = ssl.getClass().getMethod("setHostname", String.class);
                setHostnameMethod.invoke(ssl, host);
            } catch (Exception e) {
                Log.d(TlsSniSocketFactory.class.getSimpleName(), "SNI not usable: " + e);
            }
        }

        // verify hostname and certificate
        SSLSession session = ssl.getSession();
        if (!(acceptAllCertificates || selfSignedCertificateKey != null) && !hostnameVerifier.verify(host, session)) {
            throw new SSLPeerUnverifiedException("Cannot verify hostname: " + host);
        }

//        Log.d(TlsSniSocketFactory.class.getSimpleName(),
//                "Established " + session.getProtocol() + " connection with " + session.getPeerHost() +
//                        " using " + session.getCipherSuite());

        return ssl;
    }

}