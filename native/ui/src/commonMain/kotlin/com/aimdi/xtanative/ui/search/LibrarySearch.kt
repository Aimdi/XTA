package com.aimdi.xtanative.ui.search

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.store.offline.OfflineEntry
import com.aimdi.xtanative.store.saved.ArchiveAnnotation
import com.aimdi.xtanative.store.saved.LocalPost
import com.aimdi.xtanative.store.saved.LocalPosts
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.store.saved.SavedLibrary
import com.aimdi.xtanative.store.saved.SavedPost
import com.aimdi.xtanative.store.saved.archivePlainText
import com.aimdi.xtanative.ui.offline.OfflineOpen
import com.aimdi.xtanative.ui.offline.OfflineRules
import java.text.Normalizer

/** What the library search finds, in XTA's order (reader_search_store.dart): its chips after All, and its results. */
enum class LibraryKind { SAVED, NOTE, ACCOUNT, GROUP, ARTICLE }

/** Where a result opens, each in its own screen. */
sealed interface LibraryTarget {
    /** A saved X post, in the post screen. */
    data class XPost(val id: String) : LibraryTarget

    /** A post saved from another network, which Saved shows. */
    data object Saved : LibraryTarget

    /** A note, in its thread from the first note of it. */
    data class NoteThread(val rootId: String) : LibraryTarget

    data class Profile(val screenName: String) : LibraryTarget

    /** A search followed here, searched on X again. */
    data class Search(val query: String) : LibraryTarget

    data class Group(val id: String) : LibraryTarget

    /** An article kept offline, in the archive with the reader's notes on it. */
    data class Article(val id: String) : LibraryTarget

    /** A post's pictures and videos kept offline. */
    data class OfflineMedia(val id: String) : LibraryTarget
}

/** One of the reader's things: its [title], the rest it is found by ([text]), and where it opens. */
data class LibraryDocument(val id: String, val title: String, val text: String, val kind: LibraryKind, val target: LibraryTarget) {
    /** What a search reads, folded once rather than at every key. */
    val folded: String = LibrarySearch.fold("$title $text")
}

/**
 * XTA's search of the reader's library (reader_search_screen.dart): one
 * search over the saved posts, the notes, the accounts and searches
 * followed, the groups and the offline collection, of which nothing leaves
 * the device. A result holds every word typed, whatever its case; unlike
 * XTA, accents and other marks count for nothing either, so "cafe" finds
 * "Café" and "lodz" finds "Łódź".
 */
object LibrarySearch {
    private val spaces = Regex("""\s+""")

    /** The marks Latin, Greek and Cyrillic letters carry once decomposed; a script's own signs stay. */
    private val marks = Regex("""[̀-ͯ]""")

    /** Letters whose stroke is part of them rather than a mark. */
    private val strokes = mapOf('ł' to "l", 'ø' to "o", 'đ' to "d", 'ħ' to "h", 'ı' to "i", 'ß' to "ss", 'æ' to "ae", 'œ' to "oe")

    /**
     * [text] as a search compares it: its compatibility forms decomposed (ﬁ
     * is fi), in lower case, without accents, then composed again, so a
     * script's own signs stay with their letters (が is no か).
     */
    fun fold(text: String): String {
        val stripped = Normalizer.normalize(text, Normalizer.Form.NFKD).lowercase().replace(marks, "")
        val plain = Normalizer.normalize(stripped, Normalizer.Form.NFC)
        return if (plain.none { it in strokes }) plain else buildString { plain.forEach { append(strokes[it] ?: it) } }
    }

    /** The words of [query], folded; none for a blank query, which finds everything. */
    fun words(query: String): List<String> = fold(query).split(spaces).filter { it.isNotEmpty() }

    /** [documents] holding every word of [query], of [kind] unless that is null (All), in their order. */
    fun results(documents: List<LibraryDocument>, query: String, kind: LibraryKind?): List<LibraryDocument> {
        val words = words(query)
        return documents.filter { document -> (kind == null || document.kind == kind) && words.all(document.folded::contains) }
    }

    /** What a result says under its title: its text on a few lines, as XTA cuts it. */
    fun preview(text: String): String = text.replace(spaces, " ").trim().take(PREVIEW)

    private const val PREVIEW = 240

    /** The saved posts, newest first: titled by their first line, found by their text, author and the reader's note. */
    fun saved(library: SavedLibrary): List<LibraryDocument> = library.posts.map { post ->
        val haystack = post.about.haystack
        LibraryDocument(
            id = "saved:${post.id}",
            title = haystack.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() } ?: post.id,
            text = "$haystack ${post.note.orEmpty()}",
            kind = LibraryKind.SAVED,
            target = if (opensAsXPost(post)) LibraryTarget.XPost(post.id) else LibraryTarget.Saved,
        )
    }

    /** An X post, or one whose content was too large to keep, which has an X post's id. */
    private fun opensAsXPost(post: SavedPost): Boolean = when (post.about.kind) {
        SavedContent.Kind.X -> true
        SavedContent.Kind.NONE -> post.id.isNotEmpty() && post.id.all(Char::isDigit)
        else -> false
    }

    /** The reader's notes, found by their text; each opens in its thread. */
    fun notes(notes: List<LocalPost>): List<LibraryDocument> = notes.map { note ->
        LibraryDocument("note:${note.id}", note.body, note.body, LibraryKind.NOTE, LibraryTarget.NoteThread(LocalPosts.threadRootId(notes, note.id)))
    }

    /** The X accounts followed here, by name and handle, then the searches followed. */
    fun accounts(session: Session): List<LibraryDocument> =
        session.following.map { LibraryDocument("account:${it.id}", it.name, it.screenName, LibraryKind.ACCOUNT, LibraryTarget.Profile(it.screenName)) } +
            session.searches.map { LibraryDocument("search:${it.query}", it.query, it.query, LibraryKind.ACCOUNT, LibraryTarget.Search(it.query)) }

    /** The groups, in XTA's order. */
    fun groups(session: Session): List<LibraryDocument> = FollowGroup.ordered(session.groups).map { group ->
        LibraryDocument("group:${group.id}", group.name, group.name, LibraryKind.GROUP, LibraryTarget.Group(group.id))
    }

    /** The offline collection a search reads: what is marked sensitive never shows in it, as in XTA. */
    fun searchable(entries: List<OfflineEntry>): List<OfflineEntry> = entries.filterNot { it.sensitive }

    /** [entry] found by its title, source, its article's [text] and the reader's notes on it; a post's media opens in its viewer. */
    fun article(entry: OfflineEntry, text: String, notes: ArchiveAnnotation): LibraryDocument = LibraryDocument(
        id = "article:${entry.id}",
        title = entry.title,
        text = "${entry.source} $text ${notes.searchable}",
        kind = LibraryKind.ARTICLE,
        target = if (OfflineRules.open(entry) == OfflineOpen.MEDIA) LibraryTarget.OfflineMedia(entry.id) else LibraryTarget.Article(entry.id),
    )
}

/** The library search at one moment: what it holds, what was typed and the chip picked (null is All). */
data class LibrarySearchUi(
    val documents: List<LibraryDocument> = emptyList(),
    val query: String = "",
    val kind: LibraryKind? = null,
    /** The offline collection is still being read. */
    val loading: Boolean = true,
    /** Something could not be read; Try again reads everything again. */
    val failed: Boolean = false,
) {
    val results: List<LibraryDocument> get() = LibrarySearch.results(documents, query, kind)

    /** Whether this state's [results] say "No results": not while the first read is still under way. */
    fun saysNoResults(results: List<LibraryDocument>): Boolean = results.isEmpty() && !(loading && documents.isEmpty())
}

/** The library as its search reads it from the app's stores. */
class LibraryIndex(private val graph: AppGraph) {
    /** What reads at once, in XTA's order: the saved posts, the notes, what is followed and the groups. */
    suspend fun local(): List<LibraryDocument> {
        graph.sessions.load()
        val session = graph.sessions.state.value ?: Session()
        val documents = LibrarySearch.saved(graph.saved.library()) + LibrarySearch.notes(graph.localPosts.notes()) +
            LibrarySearch.accounts(session) + LibrarySearch.groups(session)
        return documents.distinctBy { it.id }
    }

    /** The offline collection, read after the rest: each article is opened for its text. */
    suspend fun articles(): List<LibraryDocument> {
        graph.offline.load()
        val notes = graph.archiveNotes.content()
        return LibrarySearch.searchable(graph.offline.state.value.entries).distinctBy { it.id }.map { entry ->
            val article = if (entry.hasArticle) graph.offline.article(entry.id) else null
            LibrarySearch.article(entry, article?.let { archivePlainText(it.bodyHtml) }.orEmpty(), notes[entry.id] ?: ArchiveAnnotation())
        }
    }
}
