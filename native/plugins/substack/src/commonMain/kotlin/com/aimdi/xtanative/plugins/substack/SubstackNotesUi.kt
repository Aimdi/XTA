package com.aimdi.xtanative.plugins.substack

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.substack.SubstackLinks
import com.aimdi.xtanative.networks.substack.SubstackNote
import com.aimdi.xtanative.networks.substack.SubstackNotesFeed
import com.aimdi.xtanative.networks.substack.SubstackPublication
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.NetworkCount
import com.aimdi.xtanative.ui.plugins.NetworkHeaderButton
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.NetworkPostCard
import com.aimdi.xtanative.ui.plugins.NetworkPostExtras
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.TextRun
import com.aimdi.xtanative.ui.plugins.addToGroupText
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.plugins.rememberShareText
import com.aimdi.xtanative.ui.post.PostIcons

/** Notes: public Notes from around Substack, not a Following feed, which would need a Substack account. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotesPane(app: SubstackApp, notes: SubstackNotesFeed, frame: PluginFrame) {
    val state by notes.state.collectAsState()
    val list = rememberLazyListState()
    ScrollsToTop(list, frame.toTop)
    val actions = rememberNoteActions(app)
    val error = state.refreshError
    PullToRefreshBox(isRefreshing = state.refreshing && state.loaded, onRefresh = { notes.refresh(force = true) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
            when {
                state.notes.isEmpty() && error != null -> item(key = "problem") { FailurePane(error, onRetry = { notes.refresh(force = true) }) }
                state.notes.isEmpty() && !state.loaded -> item(key = "loading") { LoadingRow() }
                state.notes.isEmpty() -> item(key = "empty") { EmptyState(pluginString(Strings.notes_empty), description = pluginString(Strings.notes_intro)) }
                else -> {
                    item(key = "intro") {
                        Text(pluginString(Strings.notes_intro), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp))
                    }
                    if (error != null) item(key = "refresh-failed") { FailureRow(error, { notes.refresh(force = true) }) }
                    items(state.notes, key = { "note-" + it.id }, contentType = { "note" }) { note -> NoteRow(app, note, actions) }
                    item(key = "end") { LoadMoreRow(state.loadingMore, state.loadMoreError, state.canLoadMore, notes::loadMore, enabled = !state.refreshing) }
                }
            }
        }
    }
}

/** A note in the list, its ⋯ and a long press opening the shared post sheet (substack_note_card.dart), its hearts under it, then its publication. */
@Composable
private fun NoteRow(app: SubstackApp, note: SubstackNote, actions: NoteActions) {
    val more = pluginString(Strings.more_options)
    val extras = NetworkPostExtras(
        trailing = if (actions.hasMenu(note)) { { NetworkHeaderButton(PostIcons.MoreHoriz, more) { app.openMenu(note) } } } else null,
        footer = { NoteReactions(app, note) },
    )
    NetworkPostCard(actions.post(note), actions.of(note), extras)
    note.publication?.let { NotePublication(app, it, Modifier.padding(start = 68.dp, end = 16.dp, bottom = 8.dp)) }
    Hairline()
}

/**
 * A note as the shared post card draws it: its author, its text with web
 * links (a Substack post link opens the reader), its picture and its hearts.
 * The author's name opens their profile on substack.com in the browser.
 */
internal class NoteActions(private val app: SubstackApp, private val unknown: String, private val outside: (String) -> Unit) {
    fun post(note: SubstackNote): NetworkPost = noteAsPost(note, unknown)

    /** Where [note]'s parts lead; [opened] on its own screen, where a tap on it opens nothing more. */
    fun of(note: SubstackNote, opened: Boolean = false) = NetworkPostActions(
        open = { if (!opened) app.openNote(note) },
        openAuthor = { post -> if (post.authorHandle.isNotEmpty()) outside("https://substack.com/@" + post.authorHandle) },
        openMention = {},
        openTag = {},
        openMedia = { post, index -> app.host.openMedia(post.media, index) },
        more = if (hasMenu(note)) { { app.openMenu(note) } } else null,
    )

    /** Whether [note] has a page of its own, which its sheet files and shares. */
    fun hasMenu(note: SubstackNote): Boolean = SubstackShared.archive(note) != null
}

/** [note] as the shared post card draws it; a note without a name or handle is by [unknown]. */
internal fun noteAsPost(note: SubstackNote, unknown: String): NetworkPost {
    val handle = note.authorHandle?.trim()?.removePrefix("@").orEmpty()
    return NetworkPost(
        id = note.id,
        authorId = handle,
        authorName = listOfNotNull(note.authorName, note.authorHandle).firstOrNull { it.isNotBlank() } ?: unknown,
        authorHandle = handle,
        avatarUrl = SubstackLinks.webUrl(note.authorPhotoUrl),
        createdAt = note.at,
        text = SubstackLinks.textParts(note.body).map { part -> part.url?.let { TextRun.Link(part.text, it) } ?: TextRun.Plain(part.text) },
        media = listOfNotNull(SubstackLinks.webUrl(note.imageUrl)?.let { MediaItem(it) }),
        likes = note.reactionCount ?: 0,
        url = SubstackLinks.webUrl(note.url),
    )
}

/** How many hearts a note's row counts: none when it has none, or in zen or calm mode. */
internal fun noteLikes(note: SubstackNote, settings: Settings?): Long? {
    val count = note.reactionCount ?: 0
    val quiet = settings?.let { it[PostsPrefs.zenMode] || it[PostsPrefs.calmMode] } == true
    return count.takeIf { it > 0 && !quiet }
}

@Composable
internal fun rememberNoteActions(app: SubstackApp): NoteActions {
    val unknown = pluginString(Strings.unknown)
    val outside = rememberOpenOutside()
    return remember(app, unknown, outside) { NoteActions(app, unknown, outside) }
}

/** A note's hearts, as XTA counts them under it (no reply or repost to count): none when it has none, or in zen or calm mode. */
@Composable
private fun NoteReactions(app: SubstackApp, note: SubstackNote) {
    val settings by app.host.settings.state.collectAsState()
    val count = noteLikes(note, settings) ?: return
    Row(Modifier.padding(top = 12.dp)) { NetworkCount(XIcons.Like, count, label = pluginString(Strings.favorites) + ": " + count) }
}

/** The publication a note came from: open it, follow it here (saying so, as XTA's chip does), or put it in groups. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotePublication(app: SubstackApp, publication: SubstackPublication, modifier: Modifier = Modifier) {
    val follows by app.publications.collectAsState()
    val followed = follows != null && app.followOf(publication) != null
    val followedText = pluginString(Strings.followed, publication.displayName)
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = { app.openPublication(publication) },
            label = { Text(publication.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(SubstackIcons.Newspaper, null, Modifier.size(16.dp)) },
        )
        AssistChip(
            onClick = {
                app.follow(publication)
                app.host.tell(followedText)
            },
            enabled = !followed,
            label = { Text(pluginString(if (followed) Strings.following else Strings.follow)) },
            leadingIcon = { Icon(if (followed) Icons.Default.Check else Icons.Default.Add, null, Modifier.size(16.dp)) },
        )
        AssistChip(
            onClick = { app.addToGroup(publication) },
            label = { Text(addToGroupText()) },
            leadingIcon = { Icon(PostIcons.GroupAdd, null, Modifier.size(16.dp)) },
        )
    }
}

/**
 * One note on its own (substack_note_screen.dart): the note as its row draws
 * it (XTA's SubstackNoteContent in both), its text to select, its hearts
 * under it as a heart and a count, hidden in zen and calm mode; its link to
 * share or open in the browser, and the shared post sheet, in the bar.
 */
@Composable
internal fun SubstackNoteScreen(app: SubstackApp, id: String) {
    val note = remember(id) { app.noteFor(id) }
    val share = rememberShareText()
    val outside = rememberOpenOutside()
    val actions = rememberNoteActions(app)
    val url = note?.url?.let(SubstackLinks::webUrl)
    PluginScaffold(
        title = pluginString(Strings.tab_notes),
        onBack = app.host::back,
        actions = {
            if (url != null && note != null) {
                IconButton(onClick = { share(url) }) { Icon(Icons.Default.Share, pluginString(Strings.share_link)) }
                IconButton(onClick = { outside(url) }) { Icon(SubstackIcons.OpenInNew, pluginString(Strings.open_in_browser)) }
                IconButton(onClick = { app.openMenu(note) }) { Icon(PostIcons.MoreHoriz, pluginString(Strings.more_options)) }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (note == null) {
                item(key = "gone") { EmptyState(pluginString(Strings.notes_empty)) }
            } else {
                item(key = "note") {
                    SelectionContainer { NetworkPostCard(actions.post(note), actions.of(note, opened = true), NetworkPostExtras(footer = { NoteReactions(app, note) })) }
                }
                note.publication?.let { publication -> item(key = "publication") { NotePublication(app, publication, Modifier.padding(16.dp)) } }
            }
        }
    }
}
