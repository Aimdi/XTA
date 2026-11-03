package com.aimdi.xtanative.services.links

/** An app that said it opens https links; [label] is what the system calls it. */
data class InstalledBrowser(val packageName: String, val label: String)

/** Where links go, as settings offers it: in the app, the system's choice, or one browser. */
sealed interface LinkDestination {
    data object InApp : LinkDestination

    data object SystemDefault : LinkDestination

    data class Browser(val packageName: String) : LinkDestination
}

/**
 * The browser picker's rules (_browser_picker.dart, browsers.dart): one
 * choice among in-app, the system default and each installed browser. An
 * empty list of browsers means the platform could not say, not that there
 * are none: then only the first two are offered.
 */
object BrowserPicker {
    /** The stored browser that stands for "whatever the system picks". */
    const val SYSTEM_DEFAULT = ""

    fun options(browsers: List<InstalledBrowser>): List<LinkDestination> =
        listOf(LinkDestination.InApp, LinkDestination.SystemDefault) + browsers.map { LinkDestination.Browser(it.packageName) }

    /**
     * What the picker shows as chosen: in-app when that switch is on, else
     * the stored browser while it is still installed, else the system's.
     */
    fun selected(settings: LinkSettings, browsers: List<InstalledBrowser>): LinkDestination {
        if (settings.inApp == true) return LinkDestination.InApp
        val stored = settings.browser.orEmpty()
        return if (stored.isNotEmpty() && browsers.any { it.packageName == stored }) LinkDestination.Browser(stored) else LinkDestination.SystemDefault
    }

    /**
     * [settings] once [destination] is picked. Picking a browser turns the
     * in-app view off; picking in-app keeps the last browser stored, so going
     * back to it is one tap.
     */
    fun choose(settings: LinkSettings, destination: LinkDestination): LinkSettings = when (destination) {
        LinkDestination.InApp -> settings.copy(inApp = true)
        LinkDestination.SystemDefault -> settings.copy(inApp = false, browser = SYSTEM_DEFAULT)
        is LinkDestination.Browser -> settings.copy(inApp = false, browser = destination.packageName)
    }

    /** The name a browser goes by: its label, or its package when it is no longer installed. */
    fun label(packageName: String, browsers: List<InstalledBrowser>): String =
        browsers.firstOrNull { it.packageName == packageName }?.label ?: packageName
}
