package com.aimdi.xtanative.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.SingletonImageLoader
import com.aimdi.xtanative.ui.XtaNativeContent
import com.aimdi.xtanative.ui.appImageLoader
import com.aimdi.xtanative.ui.appName
import com.aimdi.xtanative.ui.chrome.InstanceMessage
import com.aimdi.xtanative.ui.chrome.SavedWindow
import com.aimdi.xtanative.ui.chrome.SavedWindowFile
import com.aimdi.xtanative.ui.chrome.SingleInstance
import com.aimdi.xtanative.ui.media.VideoCheck
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.io.File
import kotlin.math.roundToInt
import kotlin.system.exitProcess
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * XTA Native on the Linux desktop: the Android app's screens in one window.
 * A second start raises the window already open and ends (SingleInstance);
 * the window opens as large as it was left, as XTA's does.
 */
@OptIn(FlowPreview::class)
fun main() {
    val files = dataFolder()
    val instance = SingleInstance.claim(files) ?: exitProcess(0)
    val raises = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    instance.listen { message -> if (message == InstanceMessage.RAISE) raises.tryEmit(Unit) }
    val windowFile = SavedWindowFile(File(files, "window.ini"))
    val saved = windowFile.read().let { kept -> screenArea()?.let { (width, height) -> kept.fitTo(width, height) } ?: kept }
    var kept = saved
    val app = desktopApp(files)
    val graph = app.graph
    app.start()
    SingletonImageLoader.setSafe { context -> appImageLoader(context) }
    // The process waits for the writes the closing window asked for (the reading place) before it ends.
    application(exitProcessOnExit = false) {
        val state = rememberWindowState(
            placement = if (saved.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            size = DpSize(saved.width.dp, saved.height.dp),
        )
        Window(
            onCloseRequest = {
                windowFile.write(kept)
                exitApplication()
            },
            title = appName(),
            icon = appIcon(),
            state = state,
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(SavedWindow.MIN_WIDTH, SavedWindow.MIN_HEIGHT) }
            // Each change once it has rested half a second, and the last one when the window closes, as XTA's window_state.cc.
            LaunchedEffect(state) {
                snapshotFlow { state.placement to state.size }
                    .map { (placement, size) -> kept.after(placement, size.width.value.roundToInt(), size.height.value.roundToInt()).also { kept = it } }
                    .distinctUntilChanged()
                    .debounce(500)
                    .collect(windowFile::write)
            }
            LaunchedEffect(Unit) {
                raises.collect {
                    state.isMinimized = false
                    window.toFront()
                    window.requestFocus()
                }
            }
            CompositionLocalProvider(LocalUriHandler provides SystemBrowser) {
                Box(Modifier.fillMaxSize()) {
                    XtaNativeContent(graph, app.plugins)
                    VideoCheck.Shown()
                }
            }
        }
    }
    runBlocking { withTimeoutOrNull(2_000) { graph.awaitWrites() } }
    instance.close()
    exitProcess(0)
}

/** The primary screen's work area, in the window's units; null where there is no screen to ask. */
private fun screenArea(): Pair<Int, Int>? = runCatching {
    if (GraphicsEnvironment.isHeadless()) return null
    val area = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    area.width to area.height
}.getOrNull()
