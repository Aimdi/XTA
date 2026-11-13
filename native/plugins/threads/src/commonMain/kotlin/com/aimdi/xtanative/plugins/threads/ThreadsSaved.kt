package com.aimdi.xtanative.plugins.threads

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.ui.plugins.PluginSavedScreen

/** What was saved from Threads on this device (XTA's Saved for Threads, from the tab's menu): each post as its card, with its note. */
@Composable
internal fun ThreadsSavedScreen(app: ThreadsApp) {
    PluginSavedScreen(app.host, read = { ThreadsArchive.post(it.content) }) { post -> ThreadsPostCard(app, post, divider = false) }
}
