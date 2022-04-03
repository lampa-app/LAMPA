package top.rootu.lampa.net;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

public class IgnoreSSLTrustManager implements X509TrustManager {

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        // Perform no check whatsoever on the validity of the SSL certificate
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        // Perform no check whatsoever on the validity of the SSL certificate
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return null;
    }

}
