package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.semantics.Role
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
import com.aimdi.xtanative.networks.threads.ThreadsPeople
import com.aimdi.xtanative.networks.threads.ThreadsReplyAudience
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

/** What Meta says about the post under its words: its AI label, its request for a check before it is shared, and that the author of the post it answers liked it. */
@Composable
internal fun FactLines(post: ThreadsPost) {
    if (post.facts.aiLabel) FactLine(PostIcons.Sparkle, pluginString(Strings.ai_info) + " · " + pluginString(Strings.ai_info_detail), Modifier.padding(top = 8.dp))
    if (post.facts.sharingFriction) FactLine(PostIcons.Article, pluginString(Strings.sharing_friction), Modifier.padding(top = 8.dp))
    if (post.facts.likedByAuthor) FactLine(ThreadsHeart.Filled, pluginString(Strings.liked_by_author), Modifier.padding(top = 8.dp))
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

/**
 * The opened post's views, its video's plays, quotes and shares, where Meta counted them,
 * which its buttons do not show; for a signed-in reader its quotes open the
 * posts that quote it ([ThreadsQuotesScreen]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FocalCounts(app: ThreadsApp, post: ThreadsPost, hideCounts: Boolean) {
    if (hideCounts) return
    val settings by app.prefs.collectAsState()
    val quotes = post.quoteCount?.takeIf { it > 0 }?.let { it to pluginString(Strings.quotes) }
    val shares = post.facts.shareCount?.takeIf { it > 0 }?.let { it to pluginString(Strings.shares) }
    val views = post.facts.viewCount?.takeIf { it > 0 }?.let { it to pluginString(Strings.views) }
    val plays = post.facts.playCount?.takeIf { it > 0 && post.hasVideo }?.let { it to pluginString(Strings.plays) }
    if (quotes == null && shares == null && views == null && plays == null) return
    val signedIn = ThreadsPeopleView.signedIn(ThreadsSessions.of(settings))
    val quotesOpen = ThreadsPeople.mediaId(post).takeIf { ThreadsPeopleView.opensQuotes(post, signedIn) }
    val showQuotes = pluginString(Strings.show_quotes)
    // One line while it fits; four counts in a long language wrap whole, not inside their words.
    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        views?.let { PluginCounts(listOf(it)) }
        plays?.let { PluginCounts(listOf(it)) }
        quotes?.let { counted ->
            val opens = quotesOpen?.let { id ->
                Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = showQuotes) { app.host.open(ThreadsRoutes.quotes(id, post.url)) }
            }
            Box(opens ?: Modifier, contentAlignment = Alignment.CenterStart) { PluginCounts(listOf(counted)) }
        }
        shares?.let { PluginCounts(listOf(it)) }
    }
}

/** Who the author lets answer the opened post, when that is not everyone. */
@Composable
internal fun ReplyAudienceLine(post: ThreadsPost) {
    val words = when (post.facts.replyAudience ?: return) {
        ThreadsReplyAudience.FOLLOWED -> Strings.replies_limited_followed
        ThreadsReplyAudience.FOLLOWERS -> Strings.replies_limited_followers
        ThreadsReplyAudience.MENTIONED -> Strings.replies_limited_mentioned
        ThreadsReplyAudience.AUTHOR -> Strings.replies_limited_author
    }
    FactLine(XIcons.Reply, pluginString(words), Modifier.padding(top = 8.dp))
}

/** The song the post plays, named as Meta names it: nothing is played here. */
@Composable
internal fun MusicLine(post: ThreadsPost, modifier: Modifier) {
    val music = post.facts.music ?: return
    val said = pluginString(Strings.music) + ": " + music.line
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = said }, verticalAlignment = Alignment.CenterVertically) {
        if (music.imageUrl != null) {
            AsyncImage(music.imageUrl, contentDescription = null, modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)))
        } else {
            Icon(ThreadsIcons.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            music.line,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A card's ⋯ and long-press sheet, in three groups between dividers
 * ([ThreadsSheetRows]): what is done with the post (translating its words
 * where Threads translates, or showing them again; copying its words and
 * its link; the post as a picture, [onPicture], as the app shares an X
 * post) and, for a signed-in reader, its lists on Threads; then the app's
 * shared post sheet (Saved and its folders, the note, Add to group, Share
 * link, Open in browser); and last what puts it away: hiding it or muting
 * its author on this device. None of it writes to Threads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadsPostSheet(app: ThreadsApp, post: ThreadsPost, onPicture: (() -> Unit)? = null, onDone: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDone) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
            ThreadsPostSheetRows(app, post, onPicture, onDone)
        }
    }
}

/** The sheet's entries, each closing it ([onDone]) before it acts. */
@Composable
internal fun ThreadsPostSheetRows(app: ThreadsApp, post: ThreadsPost, onPicture: (() -> Unit)?, onDone: () -> Unit) {
    val copy = rememberCopyText()
    val textCopied = pluginString(Strings.text_copied)
    val linkCopied = pluginString(Strings.link_copied)
    val options by app.options.collectAsState()
    val settings by app.prefs.collectAsState()
    val translation = rememberThreadsTranslation(app, post)
    val signedIn = remember(settings) { ThreadsPeopleView.signedIn(ThreadsSessions.of(settings)) }
    val above = ThreadsSheetRows.above(post, onPicture != null, ThreadsSheetRows.translation(translation.shown != null, translation.offered), ThreadsSheetRows.lists(post, signedIn))
    val below = ThreadsSheetRows.below(post, options)
    val handle = "@" + post.handle
    val id = ThreadsPeople.mediaId(post).orEmpty()
    val then = { action: () -> Unit -> { onDone(); action() } }
    val row: @Composable (ThreadsSheetRow) -> Unit = { entry ->
        when (entry) {
            ThreadsSheetRow.TRANSLATE -> PluginMenuRow(PostIcons.Translate, pluginString(Strings.translate), then { translation.translate() })
            ThreadsSheetRow.SHOW_ORIGINAL -> PluginMenuRow(PostIcons.Translate, pluginString(Strings.show_original), then(translation.showOriginal))
            ThreadsSheetRow.COPY_TEXT -> PluginMenuRow(PostIcons.TextSnippet, pluginString(Strings.copy_text), then { copy(post.text); app.host.tell(textCopied) })
            ThreadsSheetRow.COPY_LINK -> PluginMenuRow(XIcons.Link, pluginString(Strings.copy_link), then { post.url?.let { copy(it); app.host.tell(linkCopied) } })
            ThreadsSheetRow.SHARE_IMAGE -> PluginMenuRow(PostIcons.Screenshot, pluginString(Strings.share_image), then { onPicture?.invoke() })
            ThreadsSheetRow.LIKED_BY -> PluginMenuRow(ThreadsHeart.Outlined, pluginString(Strings.show_liked_by), then { app.host.open(ThreadsRoutes.likers(id, post.url)) })
            ThreadsSheetRow.REPOSTED_BY -> PluginMenuRow(XIcons.Repost, pluginString(Strings.show_reposted_by), then { app.host.open(ThreadsRoutes.reposters(id, post.url)) })
            ThreadsSheetRow.QUOTES -> PluginMenuRow(PostIcons.Quote, pluginString(Strings.show_quotes), then { app.host.open(ThreadsRoutes.quotes(id, post.url)) })
            ThreadsSheetRow.HIDE -> PluginMenuRow(PostIcons.VisibilityOff, pluginString(Strings.hide_post), then { app.hidePost(post) })
            ThreadsSheetRow.MUTE -> PluginMenuRow(ThreadsIcons.PersonOff, pluginString(Strings.mute_account, handle), then { app.muteAccount(post.handle, muted = true) })
            ThreadsSheetRow.UNMUTE -> PluginMenuRow(ThreadsIcons.PersonOff, pluginString(Strings.unmute_account, handle), then { app.muteAccount(post.handle, muted = false) })
        }
    }
    above.forEach { group ->
        group.forEach { row(it) }
        HorizontalDivider()
    }
    PluginPostMenuRows(app.host, ThreadsShared.menu(app, post), onDone)
    if (below.isNotEmpty()) HorizontalDivider()
    below.forEach { row(it) }
}

/** An entry of the card's sheet that the plugin itself adds around the app's. */
internal enum class ThreadsSheetRow { TRANSLATE, SHOW_ORIGINAL, COPY_TEXT, COPY_LINK, SHARE_IMAGE, LIKED_BY, REPOSTED_BY, QUOTES, HIDE, MUTE, UNMUTE }

/** Which of its own entries a card's sheet lists, in their order and their groups. */
internal object ThreadsSheetRows {
    /** The translate entry: Show original while Meta's translation is [shown], Translate where it is [offered], else none. */
    fun translation(shown: Boolean, offered: Boolean): ThreadsSheetRow? = when {
        shown -> ThreadsSheetRow.SHOW_ORIGINAL
        offered -> ThreadsSheetRow.TRANSLATE
        else -> null
    }

    /**
     * The ways to [post]'s lists on Threads, each where Meta counted some
     * and its read is known to answer: for a signed-in reader only, so a
     * guest is never offered a row that leads to "sign in".
     */
    fun lists(post: ThreadsPost, signedIn: Boolean): List<ThreadsSheetRow> = listOfNotNull(
        ThreadsSheetRow.LIKED_BY.takeIf { ThreadsPeopleView.opensLikers(post, signedIn) },
        ThreadsSheetRow.REPOSTED_BY.takeIf { ThreadsPeopleView.opensReposters(post, signedIn) },
        ThreadsSheetRow.QUOTES.takeIf { ThreadsPeopleView.opensQuotes(post, signedIn) },
    )

    /**
     * The groups above the app's entries, the most used first; a group with
     * nothing in it is left out. First what is done with the post itself:
     * [translate] (see [translation]), its words and its link to copy where
     * it has them, the post as a picture where the card can take one
     * ([picture]). Then its [lists] on Threads.
     */
    fun above(post: ThreadsPost, picture: Boolean, translate: ThreadsSheetRow? = null, lists: List<ThreadsSheetRow> = emptyList()): List<List<ThreadsSheetRow>> = listOf(
        listOfNotNull(
            translate,
            ThreadsSheetRow.COPY_TEXT.takeIf { post.text.isNotBlank() },
            ThreadsSheetRow.COPY_LINK.takeIf { !post.url.isNullOrBlank() },
            ThreadsSheetRow.SHARE_IMAGE.takeIf { picture },
        ),
        lists,
    ).filter { it.isNotEmpty() }

    /**
     * The group under the app's entries, what puts the post away, last as a
     * sheet lists what cannot be taken back in one tap: Hide post unless it
     * is hidden already (a profile and a search still show it), and Mute
     * for its author, or Unmute once muted.
     */
    fun below(post: ThreadsPost, options: ThreadsFeedOptions): List<ThreadsSheetRow> {
        val muted = options.mutes(post.handle)
        return listOfNotNull(
            ThreadsSheetRow.HIDE.takeIf { post.id.isNotBlank() && post.id !in options.hiddenPosts },
            ThreadsSheetRow.MUTE.takeIf { post.handle.isNotBlank() && !muted },
            ThreadsSheetRow.UNMUTE.takeIf { muted },
        )
    }

    /** Every entry of its own the sheet lists for [post], top to bottom. */
    fun of(post: ThreadsPost, options: ThreadsFeedOptions, picture: Boolean): List<ThreadsSheetRow> =
        above(post, picture).flatten() + below(post, options)
}

/** The card's rules that need no screen: when a caption is cut short, how a spoiler is covered and read, a poll as the app draws one. */
internal object ThreadsCardRules {
    /** The lines a long caption shows in a list. */
    const val SHORT_LINES = 10

    private const val LONG_CHARS = 600
    private const val LONG_LINES = 12

    /** A caption a list cuts short: more than [LONG_CHARS] characters, or more than [LONG_LINES] lines. */
    fun isLong(text: String): Boolean = text.length > LONG_CHARS || text.count { it == '\n' } >= LONG_LINES

    /** A post's time with "Edited" after it where its words were changed, as threads.com marks one; either alone when the other is missing. */
    fun timeLine(time: String, edited: String?): String = listOfNotNull(time.ifBlank { null }, edited?.ifBlank { null }).joinToString(" · ")

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

    /** [x] from a row's start, as drawing measures it from the left: counted from the right edge of [width] where the language is written right to left ([rtl]). */
    fun mirrored(x: Float, width: Float, rtl: Boolean): Float = if (rtl) width - x else x

    /**
     * How many of the footer's buttons are placed, given the [widths] each
     * needs and the card's [max]: all of them while they fit; else all but
     * the last when that is the way to the [web], which the sheet has too.
     */
    fun footerShown(widths: List<Int>, max: Int, web: Boolean): Int =
        if (web && widths.size > 1 && widths.sum() > max) widths.size - 1 else widths.size

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
