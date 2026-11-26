package com.aimdi.xtanative.ui.links

import com.aimdi.xtanative.ui.common.ChildProcess
import java.awt.Desktop
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Web links handed to the desktop's default browser on a thread of their
 * own: java.awt.Desktop's browse goes through GTK (gtk_show_uri), which with
 * a cold browser or a slow portal could stall the window it was clicked in.
 * Only http(s) links leave, a post's link never starts a program of its
 * own, and nothing here throws.
 */
class BrowserHandoff(private val open: (String) -> Unit = ::browseOrXdgOpen) {
    private val thread: ExecutorService = Executors.newSingleThreadExecutor { task -> Thread(task, "xta-native-browse").apply { isDaemon = true } }

    /** Sends [url] to the browser, without waiting for it; false (and nothing sent) for anything but a web link. */
    fun send(url: String): Boolean {
        if (!isWeb(url)) return false
        runCatching { thread.execute { runCatching { open(url) } } }
        return true
    }

    /** Waits (a few seconds at most) for the links sent so far; for tests. */
    fun settle() {
        thread.submit {}.get(5, TimeUnit.SECONDS)
    }

    companion object {
        /** The desktop's own browser. */
        val desktop: BrowserHandoff by lazy { BrowserHandoff() }

        fun isWeb(url: String): Boolean = url.startsWith("https://") || url.startsWith("http://")

        /** java.awt.Desktop, or xdg-open where that cannot (desktops without GNOME's libraries). */
        private fun browseOrXdgOpen(url: String) {
            val browsed = runCatching {
                val desktop = Desktop.getDesktop()
                desktop.isSupported(Desktop.Action.BROWSE).also { supported -> if (supported) desktop.browse(URI(url)) }
            }.getOrDefault(false)
            if (!browsed) runCatching { ChildProcess.start("xdg-open", url) }
        }
    }
}
