package com.aimdi.xtanative.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.SingletonImageLoader
import com.aimdi.xtanative.ui.XtaNativeContent
import com.aimdi.xtanative.ui.appImageLoader
import com.aimdi.xtanative.ui.appName
import com.aimdi.xtanative.ui.media.VideoCheck
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** XTA Native on the Linux desktop: the Android app's screens in one window. */
fun main() {
    val app = desktopApp()
    val graph = app.graph
    app.start()
    SingletonImageLoader.setSafe { context -> appImageLoader(context) }
    // The process waits for the writes the closing window asked for (the reading place) before it ends.
    application(exitProcessOnExit = false) {
        Window(
            onCloseRequest = ::exitApplication,
            title = appName(),
            icon = appIcon(),
            state = rememberWindowState(width = 520.dp, height = 860.dp),
        ) {
            CompositionLocalProvider(LocalUriHandler provides SystemBrowser) {
                Box(Modifier.fillMaxSize()) {
                    XtaNativeContent(graph, app.plugins)
                    VideoCheck.Shown()
                }
            }
        }
    }
    runBlocking { withTimeoutOrNull(2_000) { graph.awaitWrites() } }
    exitProcess(0)
}
