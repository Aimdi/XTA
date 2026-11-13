package com.aimdi.xtanative

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aimdi.xtanative.store.settings.GeneralPrefs
import com.aimdi.xtanative.ui.XtaNativeContent
import com.aimdi.xtanative.ui.speech.VoiceCheck

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
            XtaNativeContent(graph, app.plugins)
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

    override fun onResume() {
        super.onResume()
        preferFastestRefreshRate()
    }
}

/** The extra CI starts the activity with to run the voice check. */
private const val VOICE_CHECK = "xta.voice_check"
