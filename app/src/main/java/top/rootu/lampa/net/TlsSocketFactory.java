package top.rootu.lampa.net;

import org.conscrypt.Conscrypt;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class TlsSocketFactory extends SSLSocketFactory {
    private static Provider conscrypt;
    public static final String[] TLS_MODERN = {"TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3"};
    public static final String[] TLS_RESTRICTED = {"TLSv1.2", "TLSv1.3"};
    private final String[] enabledProtocols;
    private final SSLSocketFactory delegate;
    public static final X509TrustManager trustAllCerts = new IgnoreSSLTrustManager();

    public TlsSocketFactory(String[] enabledProtocols) throws KeyManagementException, NoSuchAlgorithmException {
        this.enabledProtocols = enabledProtocols;
        this.delegate = getSocketFactory();
    }

    public TlsSocketFactory() throws KeyManagementException, NoSuchAlgorithmException {
        this.enabledProtocols = TLS_RESTRICTED;
        this.delegate = getSocketFactory();
    }

    public TlsSocketFactory(SSLSocketFactory base) {
        this.enabledProtocols = TLS_RESTRICTED;
        this.delegate = base;
    }

    private static SSLSocketFactory getSocketFactory() throws NoSuchAlgorithmException, KeyManagementException {
        if (TlsSocketFactory.conscrypt == null) {
            TlsSocketFactory.conscrypt = Conscrypt.newProvider();
            // Add as provider
            Security.insertProviderAt(conscrypt, 1);
        }
        SSLContext context = SSLContext.getInstance("TLS", TlsSocketFactory.conscrypt);
        context.init(null, new TrustManager[]{trustAllCerts}, null);
        return context.getSocketFactory();
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException {
        return patch(delegate.createSocket());
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return patch(delegate.createSocket(s, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return patch(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return patch(delegate.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return patch(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return patch(delegate.createSocket(address, port, localAddress, localPort));
    }

    private Socket patch(Socket s) {
        if (s instanceof SSLSocket) {
            ((SSLSocket) s).setEnabledProtocols(enabledProtocols);
        }
        return s;
    }
}