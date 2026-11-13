package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.store.downloads.DownloadDestination
import com.aimdi.xtanative.store.downloads.DownloadRequest
import com.aimdi.xtanative.store.downloads.originalDownloadUrl
import java.net.URI

/**
 * The rules of the sheet a long press on the bookmark opens: folders as chips
 * that file the post with a tap, a search that can make a folder, and where
 * a plain tap on the bookmark files a post.
 */
object FolderPicker {
    /**
     * The order the chips open in: the folders of the latest saved posts
     * first, each once, then the rest in their own order. [savedNewestFirst]
     * is the saved list as the store holds it.
     */
    fun openOrder(savedNewestFirst: List<SavedPost>, folders: List<SavedFolder>): List<SavedFolder> {
        val byId = folders.associateBy { it.id }
        val ids = LinkedHashSet<String>()
        savedNewestFirst.mapNotNullTo(ids) { post -> post.folderId?.takeIf { it in byId } }
        ids.addAll(byId.keys)
        return ids.map { byId.getValue(it) }
    }

    /** Where a tap on [tapped] files a post now in [current]: a tap on its own folder takes it out, still saved. */
    fun afterTap(current: String?, tapped: String): String? = if (current == tapped) null else tapped

    /**
     * Where a plain tap on the bookmark files a post: the folder last picked
     * in the sheet, while Remember this folder is on; else unfiled, as before
     * the reader asked for anything else.
     */
    fun rememberedFolder(rememberOn: Boolean, storedId: String?): String? = storedId?.takeIf { rememberOn && it.isNotEmpty() }

    /** What the sheet remembers after a pick, or null to leave the setting alone while remembering is off. */
    fun toRemember(rememberOn: Boolean, picked: String?): String? = if (rememberOn) picked.orEmpty() else null

    /**
     * The downloads a folder set to auto-download starts for a post filed in
     * it: its pictures at their original size, named after the author; null
     * when the download setting asks for a folder each time, which a filing
     * cannot answer.
     */
    fun autoDownloads(content: SavedContent, destination: DownloadDestination): List<DownloadRequest>? {
        if (destination.asks) return null
        val author = when (content.kind) {
            SavedContent.Kind.MASTODON -> content.post["acct"].string?.substringBefore('@')
            SavedContent.Kind.X -> content.post["user"]["screen_name"].string
            else -> null
        } ?: "xta"
        return content.photos.map { photo ->
            val url = originalDownloadUrl(photo.url)
            val name = runCatching { URI(url).path }.getOrNull().orEmpty().substringAfterLast('/').removeSuffix(":orig")
            DownloadRequest(url, "$author-$name", destination.treeUri, destination.background)
        }
    }
}

/**
 * A quick-pick sheet's chips (the folder picker is one): the first [SHOWN]
 * until All, or the matches for a search, which may offer to make the name
 * typed into a new choice.
 */
object QuickPick {
    const val SHOWN = 6

    enum class Section { RECENT, ALL, MATCHES, NO_MATCH }

    /** What the chip area holds: the choices, under which heading, whether the search can be made a choice, whether All has more. */
    data class Chips<T>(val items: List<T>, val section: Section, val canCreate: Boolean, val hasMore: Boolean)

    /** The choices whose names hold [query], whatever its case. */
    fun <T> matches(items: List<T>, query: String, nameOf: (T) -> String): List<T> {
        val needle = query.trim().lowercase()
        return items.filter { nameOf(it).lowercase().contains(needle) }
    }

    /** Whether a choice is already called [query], whatever its case. */
    fun <T> nameTaken(items: List<T>, query: String, nameOf: (T) -> String): Boolean {
        val name = query.trim().lowercase()
        return items.any { nameOf(it).trim().lowercase() == name }
    }

    /** The chips for [query] and All, from [ordered]: every choice in the order the sheet opened with. */
    fun <T> chips(ordered: List<T>, query: String, showAll: Boolean, nameOf: (T) -> String, shown: Int = SHOWN): Chips<T> {
        if (query.isNotBlank()) {
            val found = matches(ordered, query, nameOf)
            return Chips(found, if (found.isEmpty()) Section.NO_MATCH else Section.MATCHES, !nameTaken(ordered, query, nameOf), false)
        }
        val hasMore = !showAll && ordered.size > shown
        return Chips(if (hasMore) ordered.take(shown) else ordered, if (showAll) Section.ALL else Section.RECENT, false, hasMore)
    }

    /** The choices made in the sheet lead, newest first, then [openOrder], then any that came since; each once, while it exists. */
    fun <T> ordered(items: List<T>, openOrder: List<String>, createdIds: List<String>, idOf: (T) -> String): List<T> {
        val byId = items.associateBy(idOf)
        return LinkedHashSet(createdIds + openOrder + byId.keys).mapNotNull { byId[it] }
    }
}
