package com.myvu.client.core;

import android.content.Context;

import java.net.URLConnection;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Utility to bypass SSL certificate validation for self-signed HTTPS endpoints across all services.
 */
public final class SslUtils {
    private SslUtils() {}

    /**
     * Bypasses SSL certificate and hostname verification if the global ignoreSsl setting is enabled.
     */
    public static void applySslBypassIfNeeded(URLConnection connection, Context context) {
        if (context != null && Prefs.ignoreSsl(context)) {
            applySslBypass(connection);
        }
    }

    /**
     * Unconditionally disables SSL certificate and hostname verification for the connection.
     */
    public static void applySslBypass(URLConnection connection) {
        if (connection instanceof HttpsURLConnection) {
            try {
                HttpsURLConnection httpsConn = (HttpsURLConnection) connection;
                TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                        @Override
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        @Override
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
                };
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, trustAllCerts, new SecureRandom());
                httpsConn.setSSLSocketFactory(sc.getSocketFactory());
                httpsConn.setHostnameVerifier((hostname, session) -> true);
            } catch (Exception e) {
                LogBus.error("failed to disable SSL verification", e);
            }
        }
    }
}
