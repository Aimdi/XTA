package com.aimdi.xtanative.desktop

import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.plugins.AppPlugins
import com.aimdi.xtanative.data.plugins.Destination
import com.aimdi.xtanative.pluginhost.HostPlatform
import com.aimdi.xtanative.ui.common.XdgFolders
import com.aimdi.xtanative.ui.downloads.DesktopFiles
import com.aimdi.xtanative.ui.media.MpvEngine
import com.aimdi.xtanative.ui.media.VideoCheck
import com.aimdi.xtanative.ui.net.DesktopNetworkMonitor
import com.aimdi.xtanative.ui.net.DesktopProxy
import com.aimdi.xtanative.ui.speech.VoiceCheck
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** The desktop app's graph and the plugins beside it. */
internal class DesktopApp(val graph: AppGraph, val plugins: AppPlugins)

/**
 * The app graph on the Linux desktop: its files in ~/.local/share/xta-native,
 * online as the desktop's network portal (or its route table) says, XTA's
 * stand-in for X instead of x.com when XTA_NATIVE_MOCK_X names its address
 * (as CI's desktop check does; always online then, unless
 * XTA_NATIVE_NETWORK_MONITOR asks to follow the desktop's network all the
 * same, as a check of the offline bar does), and
 * downloads in XTA in the XDG Pictures, Videos or Downloads folder by type.
 *
 * Plugins are JARs signed by the certificate the build named, kept in the
 * same folder's plugins/. CI's desktop check, which cannot tap, also names a
 * plugin catalogue of its own (XTA_NATIVE_PLUGIN_CATALOGUE), plugins to
 * install from it at start, put on Home's strip and uninstall
 * (XTA_NATIVE_PLUGIN_INSTALL, _PIN and _UNINSTALL, ids separated by commas)
 * and the screen to open (XTA_NATIVE_OPEN: "plugins" for the store,
 * "plugin:<id>" for a plugin). Its voice check downloads voices from a
 * server of its own (XTA_NATIVE_VOICE_SERVER) and runs at start
 * (XTA_NATIVE_VOICE_CHECK); XTA_NATIVE_VOICE_ENGINE names the voice engine's
 * folder for a build run from Gradle, which the packages carry otherwise.
 * Its video check plays the video XTA_NATIVE_VIDEO_CHECK names at start.
 * libmpv, which plays videos and podcasts in the app, opens on a thread of
 * its own at start, as XTA opens it right after its first frame.
 */
internal fun desktopApp(files: File = dataFolder()): DesktopApp {
    val graph = AppGraph(
        files = files,
        connection = connection(),
        version = System.getProperty("xtanative.version").orEmpty(),
        mockX = env("XTA_NATIVE_MOCK_X"),
        platformFiles = DesktopFiles(),
    )
    val platform = HostPlatform.desktop(PLUGIN_CERTIFICATE_SHA256)
    val plugins = env("XTA_NATIVE_PLUGIN_CATALOGUE")?.let { AppPlugins(files, graph, platform, it) } ?: AppPlugins(files, graph, platform)
    plugins.openAtStart = when (val open = env("XTA_NATIVE_OPEN")) {
        null -> null
        "plugins" -> Destination.PluginStore
        else -> open.removePrefix("plugin:").takeIf { open.startsWith("plugin:") && it.isNotEmpty() }?.let(Destination::PluginClient)
    }
    return DesktopApp(graph, plugins)
}

/** Starts the graph and the plugins, and does what CI's check asks of the store; the proxy the reader set is in place before anything is requested. */
internal fun DesktopApp.start() {
    DesktopProxy.follow(graph.settings)
    graph.start()
    plugins.start()
    plugins.startWith(ids("XTA_NATIVE_PLUGIN_INSTALL"), ids("XTA_NATIVE_PLUGIN_PIN"), ids("XTA_NATIVE_PLUGIN_UNINSTALL"))
    if (env("XTA_NATIVE_VOICE_CHECK") != null) VoiceCheck.ask()
    env("XTA_NATIVE_VIDEO_CHECK")?.let(VideoCheck::ask)
    MpvEngine.warmUp()
}

/** Whether there is a connection: the desktop's word, except against XTA's stand-in for X, which is always there. */
private fun connection(): Flow<Boolean> = when {
    env("XTA_NATIVE_MOCK_X") != null && env("XTA_NATIVE_NETWORK_MONITOR") == null -> flowOf(true)
    else -> DesktopNetworkMonitor.desktop.connection()
}

private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

private fun ids(name: String): List<String> = env(name)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

/**
 * xta-native under $XDG_DATA_HOME, ~/.local/share where that is not set or
 * not absolute ([XdgFolders.base]). Only its user may open it: the session
 * in it holds X cookies.
 */
internal fun dataFolder(): File {
    val base = XdgFolders.base("XDG_DATA_HOME", ".local/share")
    return File(base, "xta-native").apply {
        mkdirs()
        runCatching { Files.setPosixFilePermissions(toPath(), PosixFilePermissions.fromString("rwx------")) }
    }
}
