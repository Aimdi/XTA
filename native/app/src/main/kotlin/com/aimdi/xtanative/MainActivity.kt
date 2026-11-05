package com.aimdi.xtanative

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    override fun onResume() {
        super.onResume()
        preferFastestRefreshRate()
    }
}

/** The extra CI starts the activity with to run the voice check. */
private const val VOICE_CHECK = "xta.voice_check"
