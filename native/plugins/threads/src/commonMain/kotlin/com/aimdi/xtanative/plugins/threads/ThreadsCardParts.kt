package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.core.x.model.XCard
import com.aimdi.xtanative.networks.threads.ThreadsCaption
import com.aimdi.xtanative.networks.threads.ThreadsPoll
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsTextFragment
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.rememberCopyText
import com.aimdi.xtanative.ui.plugins.PluginCounts
import com.aimdi.xtanative.ui.plugins.PluginMenuRow
import com.aimdi.xtanative.ui.plugins.PluginPostMenuRows
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.CardView
import com.aimdi.xtanative.ui.post.PostIcons
import java.time.Instant

/**
 * A post's caption on its card: covered where its author marked a spoiler
 * until a tap shows it (Threads' `is_spoiler`), and a long one cut short in
 * a list with Show more and Show less under it.
 */
@Composable
internal fun ThreadsCaptionText(app: ThreadsApp, post: ThreadsPost, modifier: Modifier, style: TextStyle, selectable: Boolean, maxLines: Int = Int.MAX_VALUE) {
    if (post.text.isEmpty()) return
    var shown by rememberSaveable(post.id) { mutableStateOf(false) }
    var expanded by rememberSaveable(post.id) { mutableStateOf(false) }
    val long = maxLines == Int.MAX_VALUE && !selectable && ThreadsCardRules.isLong(post.text)
    val lines = if (long && !expanded) ThreadsCardRules.SHORT_LINES else maxLines
    Column(modifier) {
        if (post.hasSpoiler && !shown) {
            CoveredText(post, style, lines) { shown = true }
        } else {
            ThreadsRichText(app, post.text, post.fragments, style = style, maxLines = lines, selectable = selectable)
        }
        if (long) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(pluginString(if (expanded) Strings.show_less else Strings.show_more))
            }
        }
    }
}

/** The caption with its spoilers covered: their words drawn in the colour of the bar over them, a tap on one showing them all. */
@Composable
private fun CoveredText(post: ThreadsPost, style: TextStyle, maxLines: Int, show: () -> Unit) {
    val cover = MaterialTheme.colorScheme.onSurfaceVariant
    val link = MaterialTheme.colorScheme.primary
    val said = pluginString(Strings.tap_to_show_spoiler)
    val text = remember(post.text, post.fragments, cover, link) { ThreadsCardRules.covered(ThreadsCaption.runs(post.text, post.fragments), cover, link, show) }
    Text(
        text,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics { contentDescription = ThreadsCardRules.spoken(post, said) },
    )
}

/** "Pinned", over a post its author pinned to their profile or under their own post. */
@Composable
internal fun PinnedLine(post: ThreadsPost) {
    val words = when {
        post.facts.pinnedToProfile -> Strings.pinned_post
        post.facts.pinnedToParent -> Strings.pinned_reply
        else -> return
    }
    FactLine(XIcons.Pin, pluginString(words), Modifier.padding(top = 12.dp, start = 24.dp), bold = true)
}

/** What Meta says about the post under its words: its AI label and its request for a check before it is shared. */
@Composable
internal fun FactLines(post: ThreadsPost) {
    if (post.facts.aiLabel) FactLine(PostIcons.Sparkle, pluginString(Strings.ai_info) + " · " + pluginString(Strings.ai_info_detail), Modifier.padding(top = 8.dp))
    if (post.facts.sharingFriction) FactLine(PostIcons.Article, pluginString(Strings.sharing_friction), Modifier.padding(top = 8.dp))
}

@Composable
private fun FactLine(icon: ImageVector, text: String, modifier: Modifier, bold: Boolean = false) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = muted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = if (bold) FontWeight.Bold else null, color = muted)
    }
}

/**
 * The topic or community the post was posted in, as threads.com shows it
 * beside the author: "› Books", or a community's emoji or picture and its
 * name in the accent colour. A topic opens its posts, a community its page.
 */
@Composable
internal fun TopicTag(app: ThreadsApp, post: ThreadsPost, tag: String) {
    val facts = post.facts
    val color = if (facts.community) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val open = { ThreadsTopics.of(post)?.let(app::openTopic) ?: app.openTag(tag) }
    Row(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = tag) { open() }, verticalAlignment = Alignment.CenterVertically) {
        when {
            facts.topicEmoji != null -> Text(facts.topicEmoji, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 4.dp))
            facts.topicIconUrl != null -> AsyncImage(facts.topicIconUrl, contentDescription = null, modifier = Modifier.padding(end = 4.dp).size(16.dp).clip(RoundedCornerShape(4.dp)))
            else -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        }
        Text(tag, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The post's poll with its results, as the app draws X's: read only, nothing is voted from here. */
@Composable
internal fun ThreadsPollView(poll: ThreadsPoll, modifier: Modifier) {
    val card = remember(poll) { ThreadsCardRules.card(poll, Instant.now()) }
    CardView(card, modifier)
}

/** The opened post's quotes and shares, where Meta counted them, which its buttons do not show. */
@Composable
internal fun FocalCounts(post: ThreadsPost, hideCounts: Boolean) {
    if (hideCounts) return
    val counts = listOfNotNull(
        post.quoteCount?.takeIf { it > 0 }?.let { it to pluginString(Strings.quotes) },
        post.facts.shareCount?.takeIf { it > 0 }?.let { it to pluginString(Strings.shares) },
    )
    if (counts.isNotEmpty()) PluginCounts(counts, Modifier.padding(top = 8.dp))
}

/**
 * A card's ⋯ and long-press sheet: copying its words and its link, then the
 * app's shared post sheet (Saved and its folders, the note, Add to group,
 * Share link, Open in browser).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadsPostSheet(app: ThreadsApp, post: ThreadsPost, onDone: () -> Unit) {
    val copy = rememberCopyText()
    val textCopied = pluginString(Strings.text_copied)
    val linkCopied = pluginString(Strings.link_copied)
    val then = { action: () -> Unit -> { onDone(); action() } }
    ModalBottomSheet(onDismissRequest = onDone) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
            if (post.text.isNotBlank()) PluginMenuRow(PostIcons.TextSnippet, pluginString(Strings.copy_text), then { copy(post.text); app.host.tell(textCopied) })
            post.url?.let { url -> PluginMenuRow(XIcons.Link, pluginString(Strings.copy_link), then { copy(url); app.host.tell(linkCopied) }) }
            HorizontalDivider()
            PluginPostMenuRows(app.host, ThreadsShared.menu(app, post), onDone)
        }
    }
}

/** The card's rules that need no screen: when a caption is cut short, how a spoiler is covered and read, a poll as the app draws one. */
internal object ThreadsCardRules {
    /** The lines a long caption shows in a list. */
    const val SHORT_LINES = 10

    private const val LONG_CHARS = 600
    private const val LONG_LINES = 12

    /** A caption a list cuts short: more than [LONG_CHARS] characters, or more than [LONG_LINES] lines. */
    fun isLong(text: String): Boolean = text.length > LONG_CHARS || text.count { it == '\n' } >= LONG_LINES

    /**
     * [runs] with each spoiler drawn in [cover] on [cover], a tap on it
     * calling [show]; mentions, links and tags only coloured with [link]
     * until it is shown, so a tap meant for the cover opens nothing.
     */
    fun covered(runs: List<ThreadsTextFragment>, cover: Color, link: Color, show: () -> Unit): AnnotatedString = buildAnnotatedString {
        for (run in runs) {
            when {
                run.spoiler -> withLink(LinkAnnotation.Clickable("spoiler") { show() }) {
                    withStyle(SpanStyle(color = cover, background = cover)) { append(run.text) }
                }
                run.kind != ThreadsTextFragment.Kind.TEXT -> withStyle(SpanStyle(color = link)) { append(run.text) }
                else -> append(run.text)
            }
        }
    }

    /** The caption as a screen reader hears it while covered: each spoiler as [said]. */
    fun spoken(post: ThreadsPost, said: String): String =
        ThreadsCaption.runs(post.text, post.fragments).joinToString("") { if (it.spoiler) " $said " else it.text }.replace(Regex(" {2,}"), " ").trim()

    /** [poll] as the app's poll card takes it, ended when its time is past at [now]. */
    fun card(poll: ThreadsPoll, now: Instant): XCard.Poll =
        XCard.Poll(poll.options.map { XCard.Poll.Choice(it.text, it.votes) }, poll.endsAt, poll.closed(now))

    /**
     * The whole card as a screen reader hears it first: who reposted it,
     * who wrote it and when, its topic, its words (each spoiler as
     * [spoiler]), and Meta's labels.
     */
    fun summary(post: ThreadsPost, time: String, reposted: String?, spoiler: String, labels: List<String>): String = listOfNotNull(
        reposted,
        listOf(post.authorName, "@" + post.handle, time).filter { it.isNotBlank() }.joinToString(", "),
        post.topicTag,
        post.text.takeIf { it.isNotBlank() }?.let { if (post.hasSpoiler) spoken(post, spoiler) else it },
    ).plus(labels).filter { it.isNotBlank() }.joinToString(". ")
}
