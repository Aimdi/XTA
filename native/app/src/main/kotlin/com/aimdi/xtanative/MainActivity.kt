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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as XtaNativeApp).graph
        setContent {
            // Settings › General › Disable screenshots: Android's FLAG_SECURE on the window, as XTA's AndroidSecureWindow.
            val secure by graph.settings.observe(GeneralPrefs.disableScreenshots).collectAsState(initial = false)
            LaunchedEffect(secure) {
                if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            XtaNativeContent(graph)
        }
    }
}
