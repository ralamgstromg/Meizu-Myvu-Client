package com.myvu.client.core

import android.content.Context
import java.net.URLConnection
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Utility to bypass SSL certificate validation for self-signed HTTPS endpoints across all services.
 */
object SslUtils {

    /**
     * Bypasses SSL certificate and hostname verification if the global ignoreSsl setting is enabled.
     */
    @JvmStatic
    fun applySslBypassIfNeeded(connection: URLConnection?, context: Context?) {
        if (context != null && Prefs.ignoreSsl(context)) {
            applySslBypass(connection)
        }
    }

    /**
     * Unconditionally disables SSL certificate and hostname verification for the connection.
     */
    @JvmStatic
    fun applySslBypass(connection: URLConnection?) {
        if (connection is HttpsURLConnection) {
            try {
                val trustAllCerts = arrayOf<TrustManager>(
                    object : X509TrustManager {
                        override fun getAcceptedIssuers(): Array<X509Certificate> {
                            return arrayOf()
                        }

                        override fun checkClientTrusted(certs: Array<X509Certificate>?, authType: String?) {}
                        override fun checkServerTrusted(certs: Array<X509Certificate>?, authType: String?) {}
                    }
                )
                val sc = SSLContext.getInstance("TLS")
                sc.init(null, trustAllCerts, SecureRandom())
                connection.sslSocketFactory = sc.socketFactory
                connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
            } catch (e: Exception) {
                LogBus.error("failed to disable SSL verification", e)
            }
        }
    }
}
