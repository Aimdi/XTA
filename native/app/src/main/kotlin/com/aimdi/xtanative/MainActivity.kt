package com.aimdi.xtanative

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aimdi.xtanative.store.settings.GeneralPrefs
import com.aimdi.xtanative.store.settings.ThemePrefs
import com.aimdi.xtanative.ui.XtaNativeContent
import com.aimdi.xtanative.ui.chrome.StatusBarFrame
import com.aimdi.xtanative.ui.speech.VoiceCheck
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    private var lastNight = Configuration.UI_MODE_NIGHT_UNDEFINED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastNight = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        enableEdgeToEdge()
        // CI's voice check (tool/voice_screens.sh), which only a -PvoiceServer build answers.
        if (BuildConfig.VOICE_SERVER.isNotEmpty() && intent?.getBooleanExtra(VOICE_CHECK, false) == true) VoiceCheck.ask()
        val app = application as XtaNativeApp
        val graph = app.graph
        setContent {
            // Settings › General › Disable screenshots: Android's FLAG_SECURE on the window, as XTA's AndroidSecureWindow.
            val secure by graph.settings.observe(GeneralPrefs.disableScreenshots).collectAsState(initial = false)
            LaunchedEffect(secure) {
                if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            // Settings › Theme › Hide the status bar.
            val statusHidden by graph.settings.observe(ThemePrefs.hideStatusBar).collectAsState(initial = false)
            // The first download asks whether its notification may show (Android 13 and later).
            LaunchedEffect(Unit) {
                graph.downloads.state.first { state -> state.entries.any { it.active } }
                askToNotify()
            }
            StatusBarFrame(window, statusHidden) { XtaNativeContent(graph, app.plugins) }
        }
    }

    /**
     * Dark mode switched while the app is open (the manifest keeps the
     * activity through it): the screens follow as they draw, and the system
     * bars' icons and the window behind the screens follow here, as a new
     * activity would set them from the theme.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val night = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (night == lastNight) return
        lastNight = night
        enableEdgeToEdge()
        window.setBackgroundDrawable(ColorDrawable(if (night == Configuration.UI_MODE_NIGHT_YES) Color.BLACK else Color.WHITE))
    }

    /**
     * Asks once whether notifications may show, which Android 13 keeps back
     * until the reader allows them. A reader who said no is not asked again:
     * the downloads go on either way, and the phone's settings can allow them.
     */
    private fun askToNotify() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val asked = getSharedPreferences(ASKED_PREFS, Context.MODE_PRIVATE)
        if (asked.getBoolean(ASKED_NOTIFICATIONS, false)) return
        asked.edit().putBoolean(ASKED_NOTIFICATIONS, true).apply()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATIONS_REQUEST)
    }

    override fun onResume() {
        super.onResume()
        preferFastestRefreshRate()
    }
}

/** The extra CI starts the activity with to run the voice check. */
private const val VOICE_CHECK = "xta.voice_check"

/** What the activity has asked the reader for already, kept apart from the settings a backup carries. */
private const val ASKED_PREFS = "asked"
private const val ASKED_NOTIFICATIONS = "notifications"
private const val NOTIFICATIONS_REQUEST = 4_202
