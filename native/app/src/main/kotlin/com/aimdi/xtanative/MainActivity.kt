package com.aimdi.xtanative

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.aimdi.xtanative.ui.XtaNativeRoot
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.theme.XtaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as XtaNativeApp).graph
        setContent {
            CompositionLocalProvider(LocalGraph provides graph) {
                XtaTheme { XtaNativeRoot() }
            }
        }
    }
}
