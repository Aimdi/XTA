package com.aimdi.xtanative.ui

import com.aimdi.xtanative.ui.links.LauncherShortcut

/** Where the launcher's shortcuts ([LauncherShortcut]) lead: each to one of the screens the account menu opens. */
internal object LauncherShortcuts {
    /**
     * The route the shortcut of [action] opens over [over], the screen shown
     * now: the search with its field ready, Saved, Subscriptions or
     * (from their notification) the downloads. Null for
     * an action that is no shortcut's, over a screen that must be finished
     * first (the intro, the sign-in, an import), before any screen is shown,
     * and over the very screen asked for, which is not opened twice.
     */
    fun route(action: String?, over: String?): String? {
        val (pattern, route) = when (LauncherShortcut.of(action)) {
            LauncherShortcut.SEARCH -> Routes.SEARCH to Routes.search(null, null, focus = true)
            LauncherShortcut.SAVED -> Routes.SAVED to Routes.SAVED
            LauncherShortcut.SUBSCRIPTIONS -> Routes.SUBSCRIPTIONS to Routes.SUBSCRIPTIONS
            LauncherShortcut.DOWNLOADS -> Routes.DOWNLOADS to Routes.DOWNLOADS
            null -> return null
        }
        return route.takeIf { over != null && over != pattern && over !in KEPT }
    }

    private val KEPT = setOf(Routes.INTRO, Routes.SIGN_IN, Routes.IMPORT)
}
