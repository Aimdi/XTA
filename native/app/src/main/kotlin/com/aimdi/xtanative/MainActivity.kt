package com.aimdi.xtanative

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aimdi.xtanative.ui.XtaNativeContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as XtaNativeApp).graph
        setContent { XtaNativeContent(graph) }
    }
}
