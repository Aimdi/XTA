package com.aimdi.xtanative.plugins.mastodon

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.networks.mastodon.MastodonSnapshot
import com.aimdi.xtanative.ui.plugins.PluginSavedScreen

/** What was saved from Mastodon on this device (XTA's Saved for Mastodon, from the reader's menu): each post as its card, with its note. */
@Composable
internal fun MastodonSavedScreen(app: MastodonApp) {
    PluginSavedScreen(app.host, read = { MastodonSnapshot.fromArchive(it.content) }) { post -> MastodonPostRow(app, post, divider = false) }
}
