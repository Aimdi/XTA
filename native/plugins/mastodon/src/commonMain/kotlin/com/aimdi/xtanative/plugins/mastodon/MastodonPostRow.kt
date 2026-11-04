package com.aimdi.xtanative.plugins.mastodon

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.aimdi.xtanative.networks.mastodon.MastodonSnapshot
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.plugins.NetworkPostCard
import com.aimdi.xtanative.ui.plugins.NetworkPostExtras
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.saved.Keepable
import com.aimdi.xtanative.ui.saved.Keeping
import com.aimdi.xtanative.ui.saved.PostKeep
import com.aimdi.xtanative.ui.saved.SaveSheet
import com.aimdi.xtanative.ui.saved.SavedIcons
import com.aimdi.xtanative.ui.saved.isSaved

/**
 * A Mastodon post in a list, as XTA's Ink card draws it: who boosted it or
 * that it is pinned, the author, when (and Edited), whom it answers, its
 * content warning holding the rest back, text, quote, media, poll and link
 * card, and XTA's row of replies, boosts, likes, the bookmark kept on this
 * device and the browser; a long press opens the post's actions. The thread's
 * [selected] post does not open itself again.
 */
@Composable
internal fun MastodonPostRow(app: MastodonApp, post: MastodonPost, modifier: Modifier = Modifier, pinned: Boolean = false, selected: Boolean = false) {
    val language = currentLanguage()
    var open by rememberSaveable(post.url, post.spoilerText) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
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
        onLongPress = { menu = true },
    )
    NetworkPostCard(shown, actions, extras, modifier)
    Hairline()
    if (menu) PostActionsSheet(app, post) { menu = false }
}

/** XTA's read-only replies, boosts and likes (each a count that leads somewhere), the bookmark, and the browser. */
@Composable
private fun EngagementRow(app: MastodonApp, post: MastodonPost, hideCounts: Boolean, onOpen: () -> Unit) {
    val openOutside = rememberOpenOutside()
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        CountButton(XIcons.Reply, post.repliesCount, Strings.replies, hideCounts, onClick = onOpen)
        CountButton(XIcons.Repost, post.reblogsCount, Strings.reposted_by, hideCounts, onClick = { app.openReposts(post) }, onLongClick = { app.openQuotes(post) })
        CountButton(XIcons.Like, post.favouritesCount, Strings.likes, hideCounts, onClick = onOpen)
        SaveButton(post)
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

/** The post kept in [Keepable] form, as XTA's Saved keeps a Mastodon post: under its address, by its author. */
internal fun keepable(post: MastodonPost): Keepable = Keepable(MastodonSnapshot.archiveId(post), post.acct, MastodonSnapshot.archive(post))

/** Whether [post] is saved on this device, as that changes. */
@Composable
internal fun savedHere(post: MastodonPost): Boolean = PostKeep().isSaved(MastodonSnapshot.archiveId(post))

/** The bookmark: a tap saves the post on this device, or removes it; a long press opens the save sheet with its folders. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SaveButton(post: MastodonPost) {
    val graph = LocalGraph.current
    val saved = savedHere(post)
    var sheet by remember { mutableStateOf(false) }
    val item = remember(post) { keepable(post) }
    val label = pluginString(if (saved) Strings.unsave else Strings.save)
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .combinedClickable(onClickLabel = label, role = Role.Button, onClick = { Keeping.toggleSave(graph, item) }, onLongClick = { sheet = true }),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (saved) SavedIcons.Bookmark else SavedIcons.BookmarkBorder,
            contentDescription = label,
            tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
    }
    if (sheet) SaveSheet(item) { sheet = false }
}
