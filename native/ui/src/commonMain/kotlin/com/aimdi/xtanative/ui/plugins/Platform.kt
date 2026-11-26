package com.aimdi.xtanative.ui.plugins

/**
 * Which platform a plugin is drawn on, for words that name a gesture: a
 * click, a right-click or a drag with the desktop's mouse, where a phone's
 * say a tap, a long press or a swipe (as the app's own settings word them).
 * Plugin API 16.
 */
object PluginPlatform {
    /** The Linux desktop, with a mouse and a keyboard; false on Android. */
    val isDesktop: Boolean get() = pluginsOnDesktop
}

internal expect val pluginsOnDesktop: Boolean
