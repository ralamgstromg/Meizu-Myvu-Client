package com.myvu.client.ui.common

import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.myvu.client.core.LogBus
import com.myvu.client.core.MindMapVisualizerHelper

/**
 * Reusable, decoupled controller for interactive Mind Map visualization:
 * - Safe WebView initialization and lifecycle management
 * - Mermaid.js HTML rendering with zoom/pan
 * - Toggle button between visual graph and raw outline text
 */
class MindMapController(
    private val activity: AppCompatActivity,
    private val webView: WebView,
    private val scrollText: ScrollView,
    private val tvMindmapText: TextView,
    private val btnToggleMode: MaterialButton?
) {

    private var isGraphMode = true
    private var currentTitle: String = "Mapa Mental"
    private var currentMindmapData: String = ""

    init {
        setupWebView()
        setupToggle()
    }

    private fun setupWebView() {
        try {
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            webView.setBackgroundColor(0) // Transparent background
        } catch (e: Throwable) {
            LogBus.error("MindMapController: WebView setup error (System WebView may be unavailable)", e)
        }
    }

    private fun setupToggle() {
        btnToggleMode?.setOnClickListener {
            isGraphMode = !isGraphMode
            updateViewMode()
        }
    }

    fun loadMindMap(title: String, mindmapData: String) {
        currentTitle = title
        currentMindmapData = mindmapData

        if (mindmapData.isBlank()) {
            tvMindmapText.text = "Mapa mental no disponible. Toca ✨ para generarlo con IA."
            val html = MindMapVisualizerHelper.buildMindMapHtml(title, "mindmap\n  root(($title))\n    Sin datos\n      Genera con IA")
            try {
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            } catch (e: Throwable) {
                LogBus.error("MindMapController: Error loading empty mindmap in webview", e)
            }
            updateViewMode()
            return
        }

        tvMindmapText.text = mindmapData

        try {
            val html = MindMapVisualizerHelper.buildMindMapHtml(title, mindmapData)
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        } catch (e: Throwable) {
            LogBus.error("MindMapController: Error loading mindmap data into webview", e)
            // Fallback automatically to text mode
            isGraphMode = false
        }

        updateViewMode()
    }

    private fun updateViewMode() {
        if (isGraphMode) {
            webView.visibility = View.VISIBLE
            scrollText.visibility = View.GONE
            btnToggleMode?.text = "📝 Ver Texto"
        } else {
            webView.visibility = View.GONE
            scrollText.visibility = View.VISIBLE
            btnToggleMode?.text = "📊 Ver Gráfico"
        }
    }

    fun destroy() {
        try {
            webView.stopLoading()
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        } catch (e: Throwable) {
            LogBus.error("MindMapController: Error destroying webview", e)
        }
    }
}
