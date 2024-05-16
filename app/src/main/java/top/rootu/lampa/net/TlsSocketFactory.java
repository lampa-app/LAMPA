package top.rootu.lampa.net;

import android.os.Build;

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
    private static final String[] TLS_COMPAT = {"TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3"};
    private static final String[] TLS_ONLY = {"TLSv1.2", "TLSv1.3"};
    private static final String[] TLS_V12_ONLY = {"TLSv1.2"};
    final SSLSocketFactory delegate;
    public static final X509TrustManager trustAllCerts = new IgnoreSSLTrustManager();

    public TlsSocketFactory() throws KeyManagementException, NoSuchAlgorithmException {
        if (TlsSocketFactory.conscrypt == null) {
            TlsSocketFactory.conscrypt = Conscrypt.newProvider();
            // Add as provider
            Security.insertProviderAt(conscrypt, 1);
        }
        SSLContext context = SSLContext.getInstance("TLS", TlsSocketFactory.conscrypt);
        context.init(null, new TrustManager[] {trustAllCerts}, null);
        this.delegate = context.getSocketFactory();
    }

    public TlsSocketFactory(SSLSocketFactory base) {
        this.delegate = base;
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
            ((SSLSocket) s).setEnabledProtocols(TLS_COMPAT);
        }
        return s;
    }
}