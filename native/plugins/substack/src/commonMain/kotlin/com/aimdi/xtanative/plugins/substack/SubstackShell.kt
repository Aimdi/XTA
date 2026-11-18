package com.aimdi.xtanative.plugins.substack

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.aimdi.xtanative.networks.substack.SubstackHomeRules
import com.aimdi.xtanative.networks.substack.SubstackPublication
import com.aimdi.xtanative.ui.plugins.PluginHomeTab
import com.aimdi.xtanative.ui.plugins.ShellFeeds
import com.aimdi.xtanative.ui.plugins.ShellFilter
import com.aimdi.xtanative.ui.plugins.ShellStatus
import com.aimdi.xtanative.ui.plugins.ShellSubtitle
import com.aimdi.xtanative.ui.plugins.pluginString

/**
 * Substack's Home in the app's shell (the unified plugin shell): its feed
 * row, Subscriptions and each publication followed, which narrows the feed
 * to it; its order and filters behind the shell's filter; under its name,
 * how many posts are unread, or what failed to load.
 */
@Composable
internal fun SubstackShell(app: SubstackApp, state: HomeState, followed: List<SubstackPublication>) {
    var options by rememberSaveable { mutableStateOf(false) }
    val feed by state.feed.state.collectAsState()
    val read by app.read.state.collectAsState()
    val controls = state.controls
    val all = PluginHomeTab(SubstackIcons.Newspaper, pluginString(Strings.feed_subscriptions), controls.publication == null) { controls.publication = null }
    val publications = followed.map { publication ->
        PluginHomeTab(SubstackIcons.Newspaper, publication.name, controls.publication == publication.baseUrl) { controls.publication = publication.baseUrl }
    }
    ShellFeeds(listOf(all) + publications, ShellFilter(SubstackHomeRules.filtered(controls.home, controls.filter)) { options = true })
    val unread = SubstackHomeRules.unread(feed.posts, followed, read)
    when {
        feed.failed -> ShellSubtitle(failureLabel(feed.failedCount, feed.refreshError), ShellStatus.PROBLEM)
        unread > 0 -> ShellSubtitle(pluginString(Strings.unread_count, unread), ShellStatus.UNREAD)
        else -> ShellSubtitle(null)
    }
    if (options) ReadingOptions(state, HomePane.HOME, autofocus = false) { options = false }
}
