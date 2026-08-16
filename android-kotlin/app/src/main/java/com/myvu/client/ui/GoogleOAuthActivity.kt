package com.myvu.client.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.myvu.client.R
import com.myvu.client.core.GoogleDriveSyncHelper
import com.myvu.client.core.LogBus
import kotlinx.coroutines.launch

/**
 * In-App Web OAuth flow for Google Drive:
 * Handles sign-in without requiring pre-registered Play Services SHA-1 certificates.
 */
class GoogleOAuthActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var txtStatus: TextView

    companion object {
        const val EXTRA_CLIENT_ID = "extra_client_id"
        const val EXTRA_CLIENT_SECRET = "extra_client_secret"
        const val REDIRECT_URI = "http://localhost/oauth2callback"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_google_oauth)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        progressBar = findViewById(R.id.progressBar)
        txtStatus = findViewById(R.id.txtStatus)
        webView = findViewById(R.id.webView)

        val clientId = intent.getStringExtra(EXTRA_CLIENT_ID) ?: GoogleDriveSyncHelper.getClientId(this)
        val clientSecret = intent.getStringExtra(EXTRA_CLIENT_SECRET) ?: GoogleDriveSyncHelper.getClientSecret(this)

        if (clientId.isBlank()) {
            Toast.makeText(this, "Ingresa primero tu Client ID de Google Cloud o Token en Ajustes", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val authUrl = GoogleDriveSyncHelper.buildAuthUrl(clientId, REDIRECT_URI)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                if (url != null && url.startsWith(REDIRECT_URI)) {
                    view?.stopLoading()
                    handleRedirect(url, clientId, clientSecret)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith(REDIRECT_URI)) {
                    handleRedirect(url, clientId, clientSecret)
                    return true
                }
                return false
            }
        }

        txtStatus.text = "Cargando acceso seguro de Google..."
        webView.loadUrl(authUrl)
    }

    private fun handleRedirect(url: String, clientId: String, clientSecret: String) {
        val uri = Uri.parse(url)
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")

        if (error != null) {
            Toast.makeText(this, "Autorización cancelada o fallida: $error", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (code != null) {
            webView.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
            txtStatus.visibility = View.VISIBLE
            txtStatus.text = "Conectando con Google Drive y obteniendo credenciales..."

            lifecycleScope.launch {
                val success = GoogleDriveSyncHelper.exchangeAuthCodeForTokens(
                    context = this@GoogleOAuthActivity,
                    authCode = code,
                    clientId = clientId,
                    clientSecret = clientSecret,
                    redirectUri = REDIRECT_URI
                )

                if (success) {
                    Toast.makeText(this@GoogleOAuthActivity, "¡Google Drive vinculado exitosamente!", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    txtStatus.text = "Error al intercambiar credenciales con Google."
                    Toast.makeText(this@GoogleOAuthActivity, "Error al autenticar con Google", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
