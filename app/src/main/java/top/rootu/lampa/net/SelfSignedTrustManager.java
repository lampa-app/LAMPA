package top.rootu.lampa.net;

import android.annotation.SuppressLint;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

@SuppressLint("CustomX509TrustManager")
public class SelfSignedTrustManager implements X509TrustManager {

    private static final X509Certificate[] acceptedIssuers = new X509Certificate[]{};

    private final String certKey;

    public SelfSignedTrustManager(String certKey) {
        super();
        this.certKey = certKey;
    }

    // Thank you: http://stackoverflow.com/questions/1270703/how-to-retrieve-compute-an-x509-certificates-thumbprint-in-java
    private static String getThumbPrint(X509Certificate cert)
            throws NoSuchAlgorithmException, CertificateEncodingException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] der = cert.getEncoded();
        md.update(der);
        byte[] digest = md.digest();
        return hexify(digest);
    }

    private static String hexify(byte[] bytes) {

        char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        StringBuilder buf = new StringBuilder(bytes.length * 2);
        for (byte aByte : bytes) {
            buf.append(hexDigits[(aByte & 0xf0) >> 4]);
            buf.append(hexDigits[aByte & 0x0f]);
        }
        return buf.toString();

    }

    @SuppressLint("TrustAllX509TrustManager")
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        if (this.certKey == null) {
            throw new CertificateException("Requires a non-null certificate key in SHA-1 format to match.");
        }

        // Qe have a certKey defined. We should now examine the one we got from the server.
        // They match? All is good. They don't, throw an exception.
        String ourKey = this.certKey.replaceAll("[^a-fA-F0-9]+", "");
        try {
            // Assume self-signed root is okay?
            X509Certificate sslCert = chain[0];
            String thumbprint = SelfSignedTrustManager.getThumbPrint(sslCert);
            if (ourKey.equalsIgnoreCase(thumbprint)) {
                return;
            }

            //Log.e(SelfSignedTrustManager.class.getSimpleName(), certificateException.toString());
            throw new CertificateException("Certificate key [" + thumbprint + "] doesn't match expected value.");

        } catch (NoSuchAlgorithmException e) {
            throw new CertificateException("Unable to check self-signed cert, unknown algorithm. " + e);
        }

    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return acceptedIssuers;
    }

}