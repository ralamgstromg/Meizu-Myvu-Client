package com.myvu.client.core

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.myvu.client.R

object EdgeToEdgeHelper {

    /**
     * Configures the activity window for edge-to-edge rendering and attaches
     * dynamic insets listeners to ensure toolbars and bottom bars never collide
     * with system status bars, notches, or gesture navigation bars.
     */
    fun setupEdgeToEdge(
        activity: Activity,
        topBar: View? = null,
        bottomBar: View? = null,
        scrollContent: View? = null
    ) {
        try {
            val window = activity.window

            // Make system status bar and navigation bar transparent/themed
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT

            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false

            val rootView = activity.findViewById<View>(android.R.id.content) ?: return

            ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, windowInsets ->
                try {
                    val insets = windowInsets.getInsets(
                        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                    )

                    // Adjust top bar with safe status bar + notch height
                    topBar?.let {
                        val initialTopPadding = it.getTag(R.id.tag_initial_top_padding) as? Int
                            ?: it.paddingTop.also { p -> it.setTag(R.id.tag_initial_top_padding, p) }
                        it.updatePadding(top = initialTopPadding + insets.top)
                    }

                    // Adjust bottom bar with gesture navigation height
                    bottomBar?.let {
                        val initialBottomMargin = (it.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
                        val cachedInitial = it.getTag(R.id.tag_initial_bottom_margin) as? Int
                            ?: initialBottomMargin.also { m -> it.setTag(R.id.tag_initial_bottom_margin, m) }
                        it.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                            bottomMargin = cachedInitial + insets.bottom
                        }
                    }

                    // Adjust scroll content bottom padding
                    scrollContent?.let {
                        val initialBottomPadding = it.getTag(R.id.tag_initial_bottom_padding) as? Int
                            ?: it.paddingBottom.also { p -> it.setTag(R.id.tag_initial_bottom_padding, p) }
                        it.updatePadding(bottom = initialBottomPadding + insets.bottom)
                    }
                } catch (e: Throwable) {
                    LogBus.error("EdgeToEdgeHelper: Error in applyWindowInsets listener", e)
                }

                WindowInsetsCompat.CONSUMED
            }

            ViewCompat.requestApplyInsets(rootView)
        } catch (e: Throwable) {
            LogBus.error("EdgeToEdgeHelper: Error setting up edge to edge", e)
        }
    }
}
