package com.aimdi.xtanative.plugins.mastodon

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.mastodon.MastodonPost
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.plugins.NetworkBookmark
import com.aimdi.xtanative.ui.plugins.NetworkPostCard
import com.aimdi.xtanative.ui.plugins.NetworkPostExtras
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.post.PostIcons

/**
 * A Mastodon post in a list, as XTA's Ink card draws it: who boosted it or
 * that it is pinned, the author, when (and Edited), whom it answers, its
 * content warning holding the rest back, text, quote, media, poll and link
 * card, and XTA's row of replies, boosts, likes, the bookmark that files it
 * in the app's Saved and the browser; a long press opens the app's post
 * sheet. The thread's [selected] post does not open itself again; without
 * [divider] the line under it is left to the list.
 */
@Composable
internal fun MastodonPostRow(
    app: MastodonApp,
    post: MastodonPost,
    modifier: Modifier = Modifier,
    pinned: Boolean = false,
    selected: Boolean = false,
    divider: Boolean = true,
) {
    val language = currentLanguage()
    var open by rememberSaveable(post.url, post.spoilerText) { mutableStateOf(false) }
    val prefs by app.prefs.collectAsState()
    val shown = remember(post, language, pinned, open) { post.toNetworkPost(language, pinned, open) }
    val actions = remember(post, selected) { app.actionsFor(post, opens = !selected) }
    val onOpen = { if (!selected) app.openThread(post) }
    val poll = post.poll?.takeIf { open || !post.hasSpoiler }
    val warning: (@Composable () -> Unit)? = if (post.hasSpoiler) { { ContentWarning(post.spoilerText, open) { open = !open } } } else null
    val results: (@Composable () -> Unit)? = if (poll != null) { { PollResults(poll) } } else null
    val extras = NetworkPostExtras(
        repostLine = if (post.boosted) pluginString(Strings.boosted, post.boostedBy ?: post.authorName) else null,
        onRepostLine = post.boostedByAcct?.takeIf { it.isNotEmpty() }?.let { acct -> { app.openProfile(acct) } },
        timeNote = if (post.edited) pluginString(Strings.edited) else null,
        above = warning,
        below = results,
        footer = { EngagementRow(app, post, prefs.hideCounts, onOpen) },
        onLongPress = { app.openMenu(post) },
    )
    NetworkPostCard(shown, actions, extras, modifier)
    if (divider) Hairline()
}

/**
 * What the app's shared row adds to [post] among other networks' posts
 * (XtaPlugin.postExtras), so that Home's timelines and the groups draw it as
 * XTA draws its Mastodon card there (mastodon_interleaved.dart): "… boosted"
 * leading to who boosted it, Edited after its time, its poll's results
 * behind its content warning with the rest of it, XTA's row of replies,
 * boosts (who boosted it; a long press, its quotes), likes, the bookmark and
 * the browser, and the post sheet on a long press. [onOpen] opens its thread.
 */
internal fun sharedExtras(app: MastodonApp, post: MastodonPost, language: String, onOpen: () -> Unit): NetworkPostExtras = NetworkPostExtras(
    repostLine = SharedLines.boosted(post, language),
    onRepostLine = post.boostedByAcct?.takeIf { it.isNotEmpty() }?.let { acct -> { app.openProfile(acct) } },
    timeNote = SharedLines.edited(post, language),
    below = post.poll?.let { poll -> { PollResults(poll) } },
    footer = { SharedFooter(app, post, onOpen) },
    onLongPress = { app.openMenu(post) },
)

/** The lines XTA's card writes over and beside a post, in [language]: who boosted it, and that it was edited. */
internal object SharedLines {
    fun boosted(post: MastodonPost, language: String): String? = if (post.boosted) Strings.boosted.get(language, post.boostedBy ?: post.authorName) else null

    fun edited(post: MastodonPost, language: String): String? = if (post.edited) Strings.edited.get(language) else null
}

/** XTA's engagement row under a post the app draws, its numbers gone in zen or calm mode as on the plugin's own screens. */
@Composable
private fun SharedFooter(app: MastodonApp, post: MastodonPost, onOpen: () -> Unit) {
    val prefs by app.prefs.collectAsState()
    EngagementRow(app, post, prefs.hideCounts, onOpen)
}

/** XTA's read-only replies, boosts and likes (each a count that leads somewhere), the bookmark, and the browser. */
@Composable
private fun EngagementRow(app: MastodonApp, post: MastodonPost, hideCounts: Boolean, onOpen: () -> Unit) {
    val openOutside = rememberOpenOutside()
    val archive = remember(post) { MastodonShared.archive(post) }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        CountButton(XIcons.Reply, post.repliesCount, Strings.replies, hideCounts, onClick = onOpen)
        CountButton(XIcons.Repost, post.reblogsCount, Strings.reposted_by, hideCounts, onClick = { app.openReposts(post) }, onLongClick = { app.openQuotes(post) })
        CountButton(XIcons.Like, post.favouritesCount, Strings.likes, hideCounts, onClick = onOpen)
        NetworkBookmark(archive)
        IconButton(onClick = { openOutside(post.url) }, modifier = Modifier.size(40.dp)) {
            Icon(PostIcons.OpenInNew, pluginString(Strings.open_in_browser), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CountButton(icon: ImageVector, count: Long, label: PluginText, hideCounts: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val name = pluginString(label)
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClickLabel = name, role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = name, tint = muted, modifier = Modifier.size(17.dp))
        if (!hideCounts && count > 0) {
            Text(countText(count), style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.padding(start = 4.dp))
        }
    }
}
