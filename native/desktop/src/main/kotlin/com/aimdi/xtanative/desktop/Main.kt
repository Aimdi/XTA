package com.aimdi.xtanative.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.SingletonImageLoader
import com.aimdi.xtanative.store.settings.DesktopPrefs
import com.aimdi.xtanative.ui.DesktopWindowTitle
import com.aimdi.xtanative.ui.DesktopWindows
import com.aimdi.xtanative.ui.LocalShowMainWindow
import com.aimdi.xtanative.ui.OpenedWindow
import com.aimdi.xtanative.ui.SecondaryWindow
import com.aimdi.xtanative.ui.WindowTitles
import com.aimdi.xtanative.ui.XtaNativeContent
import com.aimdi.xtanative.ui.appImageLoader
import com.aimdi.xtanative.ui.appName
import com.aimdi.xtanative.ui.chrome.AppQuit
import com.aimdi.xtanative.ui.chrome.CommandLine
import com.aimdi.xtanative.ui.chrome.InstanceMessage
import com.aimdi.xtanative.ui.chrome.SavedWindow
import com.aimdi.xtanative.ui.chrome.SavedWindowFile
import com.aimdi.xtanative.ui.chrome.ScreenArea
import com.aimdi.xtanative.ui.chrome.SingleInstance
import com.aimdi.xtanative.ui.chrome.WindowCrash
import com.aimdi.xtanative.ui.chrome.WindowFullscreen
import com.aimdi.xtanative.ui.chrome.tray.StatusNotifier
import com.aimdi.xtanative.ui.chrome.tray.TrayAction
import com.aimdi.xtanative.ui.chrome.tray.TrayService
import com.aimdi.xtanative.ui.chrome.tray.trayWords
import com.aimdi.xtanative.ui.home.ReorderDrags
import com.aimdi.xtanative.ui.links.DesktopIncomingLinks
import com.aimdi.xtanative.ui.media.VideoCheck
import com.aimdi.xtanative.ui.media.mpris.MprisSessions
import com.aimdi.xtanative.ui.saved.DesktopNotices
import com.aimdi.xtanative.ui.saved.DesktopNotifications
import com.aimdi.xtanative.ui.theme.DesktopColorScheme
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import kotlin.math.roundToInt
import kotlin.system.exitProcess
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * XTA Native on the Linux desktop: the Android app's screens in one window.
 * A second start raises the window already open and ends (SingleInstance),
 * handing it the link it was started with (`xta-native <url>`);
 * the window opens as large and where it was left, as XTA's does. The app's icon
 * sits in the desktop's tray (TrayService); with Settings › General › Close
 * to tray on, closing the window hides it there, and `--minimized` starts
 * it hidden there, while a tray shows the icon.
 */
@OptIn(FlowPreview::class, ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    // `xta-native --help` and `--version`, as command-line programs answer them, and an unknown option refused, before anything starts.
    when (CommandLine.answer(args)) {
        CommandLine.Answer.HELP -> print(CommandLine.usage).also { exitProcess(0) }
        CommandLine.Answer.VERSION -> println(CommandLine.versionLine(System.getProperty("xtanative.version").orEmpty())).also { exitProcess(0) }
        CommandLine.Answer.UNKNOWN -> {
            System.err.println(CommandLine.unknownLine(CommandLine.unknownOption(args).orEmpty()))
            System.err.print(CommandLine.usage)
            exitProcess(CommandLine.USAGE_ERROR)
        }
        CommandLine.Answer.RUN -> Unit
    }
    val files = dataFolder()
    val link = InstanceMessage.linkIn(args)
    val instance = SingleInstance.claim(files, link?.let(InstanceMessage::Open) ?: InstanceMessage.Raise) ?: exitProcess(0)
    link?.let(DesktopIncomingLinks::hand)
    val raises = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val trayClicks = MutableSharedFlow<TrayAction>(extraBufferCapacity = 4)
    val tray = TrayService.desktop
    tray.onAction = { action -> if (action == TrayAction.SHOW) raises.tryEmit(Unit) else trayClicks.tryEmit(action) }
    val startHidden = "--minimized" in args
    // The desktop's dark or light is asked while the window starts, so "System" opens in it.
    DesktopColorScheme.desktop.start()
    MprisSessions.service.onRaise = { raises.tryEmit(Unit) }
    DesktopNotifications.desktop.onActivate = { raises.tryEmit(Unit) }
    instance.listen { message ->
        if (message is InstanceMessage.Open) DesktopIncomingLinks.hand(message.url)
        raises.tryEmit(Unit)
    }
    val windowFile = SavedWindowFile(File(files, "window.ini"))
    // Back where it was left, while a screen still shows enough of it there; else where the desktop puts a new window.
    val saved = windowFile.read().placedOn(screenAreas())
    var kept = saved
    // The app is made and started on a thread of its own while the window's toolkit starts on this one: neither waits for the other.
    val starting = FutureTask { desktopApp(files).also { it.start() } }
    Thread(starting, "app-start").start()
    SingletonImageLoader.setSafe { context -> appImageLoader(context) }
    // The process waits for the writes the closing window asked for (the reading place) before it ends.
    application(exitProcessOnExit = false) {
        val app = remember { starting.started() }
        var beforeFullscreen by remember { mutableStateOf<WindowPlacement?>(null) }
        val graph = app.graph
        val name = appName()
        LaunchedEffect(name) { MprisSessions.service.identity = name }
        val words = trayWords(name)
        LaunchedEffect(words) {
            tray.words(words)
            tray.start()
        }
        val toTray by remember(graph) { graph.settings.observe(DesktopPrefs.closeToTray) }.collectAsState(initial = false)
        val uiScale by remember(graph) { graph.settings.observe(DesktopPrefs.uiScale) }.collectAsState(initial = 1.0)
        val inTray by tray.shown.collectAsState()
        // `--minimized` starts hidden in the tray, once the tray shows the icon and Close to tray is on; else the window opens.
        var visible by remember { mutableStateOf(!startHidden) }
        var deciding by remember { mutableStateOf(startHidden) }
        // Each raise counted, so the window comes in front once it is on screen.
        var fronts by remember { mutableStateOf(0) }
        val quit = {
            windowFile.write(kept)
            exitApplication()
        }
        // A window that breaks is written down as a crash and quits the app ([WindowCrash]), not hidden to the tray behind Swing's dialog.
        val crashes = remember(graph) { WindowCrash.handlers({ graph.crashReports.recordCaught(it, Thread.currentThread().name) }, quit) }
        // Here, outside the window, as a hidden window's content may not run.
        LaunchedEffect(Unit) {
            raises.collect {
                visible = true
                fronts++
            }
        }
        LaunchedEffect(visible) { tray.windowShown(visible) }
        LaunchedEffect(Unit) {
            trayClicks.collect { action ->
                when {
                    action == TrayAction.QUIT -> quit()
                    // As the launcher's New search action: X's search, with the field ready to type in.
                    action == TrayAction.NEW_SEARCH -> {
                        DesktopIncomingLinks.hand(NEW_SEARCH_LINK, byReader = true)
                        raises.tryEmit(Unit)
                    }
                    action == TrayAction.PLAY_PAUSE -> MprisSessions.service.playPause()
                    visible -> visible = false
                    else -> raises.tryEmit(Unit)
                }
            }
        }
        // The tray's Play/Pause, while something plays or is paused in the app.
        LaunchedEffect(Unit) { MprisSessions.service.session.collect { tray.media(StatusNotifier.media(it)) } }
        if (startHidden) {
            LaunchedEffect(Unit) {
                val hides = withTimeoutOrNull(TRAY_WAIT_MS) { tray.shown.first { it } } == true &&
                    graph.settings.observe(DesktopPrefs.closeToTray).first()
                if (!hides) visible = true
                deciding = false
            }
        }
        // A tray that goes away while the window is hidden in it gives the window back.
        LaunchedEffect(inTray, deciding) { if (!inTray && !visible && !deciding) visible = true }
        val titlePart by DesktopWindowTitle.part.collectAsState()
        val state = rememberWindowState(
            placement = if (saved.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            size = DpSize(saved.width.dp, saved.height.dp),
            position = savedPosition(saved),
        )
        CompositionLocalProvider(LocalWindowExceptionHandlerFactory provides crashes) {
            Window(
                // Closed to the tray only while a tray shows the icon, so the window is never lost; a quit asked (Ctrl+Q) quits.
                onCloseRequest = { if (!AppQuit.taken() && toTray && tray.shown.value) visible = false else quit() },
                visible = visible,
                // Names the screen shown, as a desktop app's title does ("flutter – Search – XTA Native").
                title = WindowTitles.title(titlePart, name),
                icon = appIcon(),
                state = state,
                // F11 fills the screen and gives it back, over every screen and viewer.
                onPreviewKeyEvent = { event ->
                    // Esc while a list's item is dragged puts it back, before anything else hears it ([ReorderDrags]).
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && ReorderDrags.escape()) return@Window true
                    val f11 = event.type == KeyEventType.KeyDown && event.key == Key.F11 && !event.isAltPressed && !event.isCtrlPressed
                    if (f11) {
                        val (placement, back) = WindowFullscreen.toggled(state.placement, beforeFullscreen)
                        beforeFullscreen = back
                        state.placement = placement
                    }
                    f11
                },
            ) {
                // The smallest window grows with the interface size, as the layouts it keeps from overflowing do.
                LaunchedEffect(uiScale) { window.minimumSize = minimumSize(uiScale) }
                // Each change once it has rested half a second, and the last one when the window closes, as XTA's window_state.cc.
                LaunchedEffect(state) {
                    snapshotFlow { Triple(state.placement, state.size, state.position) }
                        .map { (placement, size, position) ->
                            val (x, y) = if (position.isSpecified) position.x.value.roundToInt() to position.y.value.roundToInt() else null to null
                            kept.after(placement, size.width.value.roundToInt(), size.height.value.roundToInt(), x, y).also { kept = it }
                        }
                        .distinctUntilChanged()
                        .debounce(500)
                        .collect(windowFile::write)
                }
                LaunchedEffect(fronts) {
                    if (fronts == 0) return@LaunchedEffect
                    state.isMinimized = false
                    window.toFront()
                    window.requestFocus()
                }
                // What a post window asked of the plugins or the voice settings shows here, so the window comes up for it.
                val showMain = remember(window) {
                    {
                        val appActive = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow != null
                        if (DesktopWindows.showsMain(visible && !state.isMinimized, window.isActive, appActive)) raises.tryEmit(Unit)
                    }
                }
                CompositionLocalProvider(LocalUriHandler provides SystemBrowser, LocalShowMainWindow provides showMain) {
                    Box(Modifier.fillMaxSize()) {
                        XtaNativeContent(graph, app.plugins)
                        // A download or offline save that ends while another window is in use is told by the desktop too.
                        val notifies by remember(graph) { graph.settings.observe(DesktopPrefs.notifications) }.collectAsState(initial = true)
                        DesktopNotices(name, notifies) { window.isActive }
                        VideoCheck.Shown()
                        WindowCrash.TestCrash("main")
                    }
                }
            }
            // The posts opened in windows of their own (Ctrl+click, a middle click, the post menu's Open in new window).
            val others by DesktopWindows.opened.collectAsState()
            for (opened in others) {
                key(opened.serial) { PostWindow(opened, app, name, uiScale, quit, onQuitKey = { raises.tryEmit(Unit) }) }
            }
        }
    }
    runBlocking { withTimeoutOrNull(2_000) { starting.started().graph.awaitWrites() } }
    instance.close()
    exitProcess(0)
}

/**
 * A window of the app's own beside the main one, [opened] on a post, about
 * as wide as the reading column at the interface size [uiScale]: closing it closes it alone, unless the app
 * was asked to quit from it (Ctrl+Q, [onQuitKey] bringing the main window up
 * to ask), and only the main window keeps its size, the tray and the title
 * the bar shows for the app.
 */
@Composable
private fun ApplicationScope.PostWindow(opened: OpenedWindow, app: DesktopApp, name: String, uiScale: Double, quit: () -> Unit, onQuitKey: () -> Unit) {
    val secondary = remember(opened) { SecondaryWindow(opened.route) { DesktopWindows.close(opened) } }
    val part by secondary.titlePart.collectAsState()
    val state = rememberWindowState(size = remember { postWindowSize(uiScale) })
    Window(
        onCloseRequest = { if (AppQuit.taken()) quit() else secondary.close() },
        title = WindowTitles.title(part, name),
        icon = appIcon(),
        state = state,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Q && event.isCtrlPressed && !event.isAltPressed) onQuitKey()
            false
        },
    ) {
        LaunchedEffect(uiScale) { window.minimumSize = minimumSize(uiScale) }
        CompositionLocalProvider(LocalUriHandler provides SystemBrowser) {
            XtaNativeContent(app.graph, app.plugins, secondary)
            WindowCrash.TestCrash("post")
        }
    }
}

/** A post window's size at the interface size [scale] ([SavedWindow.postWindow]), on the primary screen's work area. */
private fun postWindowSize(scale: Double): DpSize {
    val area = screenArea()
    val (width, height) = SavedWindow.postWindow(scale, area?.first ?: 0, area?.second ?: 0)
    return DpSize(width.dp, height.dp)
}

/** The smallest a window may be at the interface size [scale] ([SavedWindow.minimum]), on the primary screen's work area. */
private fun minimumSize(scale: Double): Dimension {
    val area = screenArea()
    val (width, height) = SavedWindow.minimum(scale, area?.first ?: 0, area?.second ?: 0)
    return Dimension(width, height)
}

/** What the tray's New search opens, as the .desktop file's New search action does. */
private const val NEW_SEARCH_LINK = "https://x.com/search"

/** How long `--minimized` waits for a tray to show the icon before it opens the window after all. */
private const val TRAY_WAIT_MS = 4_000L

/** The app once made and started; what failed doing it fails here, as it did before it moved off this thread. */
private fun FutureTask<DesktopApp>.started(): DesktopApp = try {
    get()
} catch (e: ExecutionException) {
    throw e.cause ?: e
}

/** Where [saved] was left, or the desktop's own place for a window when it has none. */
private fun savedPosition(saved: SavedWindow): WindowPosition {
    val x = saved.x ?: return WindowPosition.PlatformDefault
    val y = saved.y ?: return WindowPosition.PlatformDefault
    return WindowPosition(x.dp, y.dp)
}

/** Every screen's work area (without its panels), the primary first, in the window's units; none where there is no screen to ask. */
private fun screenAreas(): List<ScreenArea> = runCatching {
    if (GraphicsEnvironment.isHeadless()) return emptyList()
    val screens = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val primary = screens.defaultScreenDevice
    val toolkit = Toolkit.getDefaultToolkit()
    (listOf(primary) + screens.screenDevices.filter { it != primary }).map { device ->
        val bounds = device.defaultConfiguration.bounds
        val insets = toolkit.getScreenInsets(device.defaultConfiguration)
        ScreenArea(bounds.x + insets.left, bounds.y + insets.top, bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom)
    }
}.getOrDefault(emptyList())

/** The primary screen's work area, in the window's units; null where there is no screen to ask. */
private fun screenArea(): Pair<Int, Int>? = runCatching {
    if (GraphicsEnvironment.isHeadless()) return null
    val area = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    area.width to area.height
}.getOrNull()
