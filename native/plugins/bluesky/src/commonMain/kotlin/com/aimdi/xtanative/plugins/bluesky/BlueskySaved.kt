package com.aimdi.xtanative.plugins.bluesky

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.ui.plugins.PluginSavedScreen

/** What was saved from Bluesky on this device (XTA's Saved for Bluesky, from the tab's menu): each post as its card, with its note. */
@Composable
internal fun BlueskySavedScreen(app: BlueskyApp) {
    PluginSavedScreen(app.host, read = { BlueskyArchive.post(it.content) }) { post -> PostRow(app, post, divider = false) }
}
