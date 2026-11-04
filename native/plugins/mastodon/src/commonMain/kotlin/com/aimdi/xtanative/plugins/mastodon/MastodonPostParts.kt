package com.aimdi.xtanative.plugins.mastodon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.mastodon.MastodonPoll
import com.aimdi.xtanative.networks.mastodon.MastodonPost
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.fullTimeText
import com.aimdi.xtanative.ui.plugins.PluginMenuRow
import com.aimdi.xtanative.ui.plugins.PluginNoteEditor
import com.aimdi.xtanative.ui.plugins.PluginNoteMenuRow
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.plugins.rememberShareText
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.saved.Keeping
import com.aimdi.xtanative.ui.saved.SaveSheet
import com.aimdi.xtanative.ui.saved.SavedIcons
import java.text.NumberFormat
import java.time.Instant
import java.util.Locale

/**
 * A content warning, a label and a decision rather than a headline, as XTA
 * draws it: "Content warning" over the author's [text], and Show or Hide for
 * what it holds back.
 */
@Composable
internal fun ContentWarning(text: String, open: Boolean, onToggle: () -> Unit) {
    val accent = MaterialTheme.colorScheme.tertiary
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(accent.copy(alpha = 0.07f))
            .border(1.dp, accent.copy(alpha = 0.35f), shape)
            .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(pluginString(Strings.content_warning), style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
        }
        if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp, end = 4.dp))
        TextButton(onClick = onToggle, modifier = Modifier.align(Alignment.End)) { Text(pluginString(if (open) Strings.hide else Strings.show)) }
    }
}

/** A poll's results as they were read, never a vote: each option's share, the votes, whether it is open, when it ends. */
@Composable
internal fun PollResults(poll: MastodonPoll) {
    val locale = Locale.forLanguageTag(currentLanguage())
    val closed = poll.closed(Instant.now())
    val whole = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val share = remember(locale) { NumberFormat.getPercentInstance(locale) }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        poll.options.forEach { option ->
            val fraction = poll.fraction(option)
            Column(Modifier.padding(bottom = 8.dp)) {
                Row {
                    Text(option.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (poll.resultsAvailable) {
                        Text(if (fraction == null) whole.format(option.votes) else share.format(fraction), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (fraction != null) {
                    LinearProgressIndicator(progress = { fraction.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(RoundedCornerShape(4.dp)))
                }
            }
        }
        val votes = poll.votesCount.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        val parts = listOfNotNull(
            if (poll.resultsAvailable) pluginPlural(Strings.poll_votes, votes, whole.format(votes)) else pluginString(Strings.poll_unavailable),
            pluginString(if (closed) Strings.poll_closed else Strings.poll_open),
            poll.expiresAt?.let { pluginString(if (closed) Strings.poll_ended else Strings.poll_ends, fullTimeText(it)) },
        )
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * What a long press on a post offers, in XTA's order (plugin_post_actions.dart):
 * save it on this device or remove it, a note on it in Saved, share its link,
 * file it in a folder, who boosted it, what quotes it, and the browser.
 * Nothing reaches the instance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostActionsSheet(app: MastodonApp, post: MastodonPost, onDismiss: () -> Unit) {
    val graph = LocalGraph.current
    val openOutside = rememberOpenOutside()
    val share = rememberShareText()
    val saved = savedHere(post)
    val item = remember(post) { keepable(post) }
    var folders by remember { mutableStateOf(false) }
    var noting by remember { mutableStateOf(false) }
    if (folders) {
        SaveSheet(item, onDismiss)
        return
    }
    if (noting) {
        PluginNoteEditor(item, onDismiss)
        return
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            val then = { action: () -> Unit -> { onDismiss(); action() } }
            PluginMenuRow(if (saved) SavedIcons.Bookmark else SavedIcons.BookmarkBorder, pluginString(if (saved) Strings.unsave else Strings.save), then { Keeping.toggleSave(graph, item) })
            PluginNoteMenuRow({ noting = true })
            PluginMenuRow(Icons.Filled.Share, pluginString(Strings.share_link), then { share(post.url) })
            PluginMenuRow(SavedIcons.Folder, pluginString(Strings.save_to_folder), { folders = true })
            PluginMenuRow(XIcons.Repost, pluginString(Strings.reposted_by), then { app.openReposts(post) })
            PluginMenuRow(PostIcons.Quote, pluginString(Strings.quotes), then { app.openQuotes(post) })
            PluginMenuRow(PostIcons.OpenInNew, pluginString(Strings.open_in_browser), then { openOutside(post.url) })
        }
    }
}
