package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivNovel
import com.aimdi.xtanative.networks.pixiv.PixivUser
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.Settings
import java.time.Instant

/** What a bookmark left: whether the work is bookmarked, whether its creator was followed, and whether its pages are to be saved. */
internal data class PixivBookmarkOutcome(val bookmarked: Boolean, val followedAuthor: Boolean = false, val saveAll: Boolean = false)

/**
 * Bookmarking on this device, as XTA's bookmark actions do it on Pixiv: the
 * heart, the bookmark editor and bookmark-after-save all come here, under the
 * reader's Bookmarking settings (auto-tags, following the creator, saving the
 * work). The creator is followed on this device; nothing is sent to Pixiv.
 */
internal class PixivBookmarking(
    private val bookmarks: PixivDeviceBookmarks,
    private val settings: () -> Settings,
    private val follow: (PixivUser) -> Unit,
    private val ownId: () -> Long?,
    private val now: () -> Instant = Instant::now,
) {
    fun isBookmarked(kind: PixivKind, id: Long): Boolean = bookmarks.find(kind, id) != null

    /** The heart: bookmarks with the defaults, or takes the bookmark away. */
    suspend fun toggle(illust: PixivIllust): PixivBookmarkOutcome =
        if (isBookmarked(PixivKind.WORK, illust.id)) remove(PixivKind.WORK, illust.id) else bookmark(illust)

    suspend fun toggle(novel: PixivNovel): PixivBookmarkOutcome =
        if (isBookmarked(PixivKind.NOVEL, novel.id)) remove(PixivKind.NOVEL, novel.id) else bookmark(novel)

    /**
     * Bookmarks [illust], or files its bookmark again under [tags]. Without
     * [tags] the work's own go along when auto-tag is on. A new bookmark may
     * also save the work (never when [fromSave]) and follow its creator.
     */
    suspend fun bookmark(illust: PixivIllust, tags: List<String>? = null, fromSave: Boolean = false): PixivBookmarkOutcome {
        val isNew = !isBookmarked(PixivKind.WORK, illust.id)
        bookmarks.put(PixivKind.WORK, PixivKept.of(illust), tags ?: autoTags(illust.tags.map { it.name }), now())
        if (!isNew) return PixivBookmarkOutcome(bookmarked = true)
        val saveAll = !fromSave && settings()[PixivPrefs.downloadAfterBookmark]
        return PixivBookmarkOutcome(bookmarked = true, followedAuthor = followAuthor(authorOf(illust)), saveAll = saveAll)
    }

    suspend fun bookmark(novel: PixivNovel, tags: List<String>? = null): PixivBookmarkOutcome {
        val isNew = !isBookmarked(PixivKind.NOVEL, novel.id)
        bookmarks.put(PixivKind.NOVEL, PixivKept.of(novel), tags ?: autoTags(novel.tags.map { it.name }), now())
        return PixivBookmarkOutcome(bookmarked = true, followedAuthor = isNew && followAuthor(novel.user))
    }

    suspend fun remove(kind: PixivKind, id: Long): PixivBookmarkOutcome {
        bookmarks.remove(kind, id)
        return PixivBookmarkOutcome(bookmarked = false)
    }

    /** Bookmark-after-save: a work just saved is bookmarked when the reader asked for that and it is not yet; null when nothing changed. */
    suspend fun afterSave(illust: PixivIllust): PixivBookmarkOutcome? {
        if (!settings()[PixivPrefs.bookmarkAfterDownload] || isBookmarked(PixivKind.WORK, illust.id)) return null
        return bookmark(illust, fromSave = true)
    }

    /** The tags a bookmark made without the editor carries: the work's own, popularity tags left out, while auto-tag is on. */
    fun autoTags(tags: List<String>): List<String> = if (settings()[PixivPrefs.autoTagBookmarks]) ownTags(tags) else emptyList()

    /** A creator followed after a bookmark, never the reader themself nor one already followed here. */
    private fun followAuthor(author: PixivUser): Boolean {
        val settings = settings()
        if (!settings[PixivPrefs.followAfterBookmark] || author.id == ownId() || PixivFollows.isFollowed(settings, author.id)) return false
        follow(author)
        return true
    }

    companion object {
        /** Pixiv's popularity tags such as `1000users入り` or `東方100users入り`, which say how many bookmarked a work, not what it is. */
        private val popularity = Regex("""\d+users入り""")

        fun ownTags(tags: List<String>): List<String> = tags.filterNot { popularity.containsMatchIn(it) }

        fun authorOf(illust: PixivIllust): PixivUser =
            PixivUser(illust.userId, illust.userName, illust.userAccount, avatarUrl = illust.userAvatarUrl, isFollowed = illust.userIsFollowed)
    }
}

/** What the bookmark editor holds while the reader edits: the tags offered, which are checked, and what is typed. */
internal data class PixivBookmarkDraft(val tags: List<String>, val checked: Set<String>, val typed: String = "") {
    val allChecked: Boolean get() = tags.isNotEmpty() && checked.containsAll(tags)

    fun toggled(tag: String): PixivBookmarkDraft = copy(checked = if (tag in checked) checked - tag else checked + tag)

    /** Every tag checked, or none when every tag already is. */
    fun allToggled(): PixivBookmarkDraft = copy(checked = if (allChecked) emptySet() else tags.toSet())

    /** The typed tags go on top, checked; Pixiv tags hold no spaces, so `blue sky` is two. */
    fun withTyped(): PixivBookmarkDraft {
        val added = PixivTyping.words(typed).filterNot { it in tags }
        val typedTags = PixivTyping.words(typed)
        return copy(tags = added + tags, checked = checked + typedTags, typed = "")
    }

    /** The checked tags, in the order shown, a tag still in the field taken as Done would take it. */
    fun saved(): List<String> = withTyped().let { draft -> draft.tags.filter { it in draft.checked } }

    companion object {
        /** How many of the reader's tags a tag field suggests at once. */
        const val SUGGESTED = 8

        /**
         * The editor's start for a work with [workTags]: the tags it is filed
         * under now, then the work's own, then the reader's other tags; a work
         * not bookmarked yet starts with the auto-tags checked.
         */
        fun of(workTags: List<String>, filed: List<String>?, known: List<String>, autoTags: List<String>): PixivBookmarkDraft {
            val tags = (filed.orEmpty() + workTags + known).distinct()
            return PixivBookmarkDraft(tags, (filed ?: autoTags).toSet())
        }

        /** Up to [limit] of the reader's [known] tags holding [query], whatever its case: the suggestions under the tag field. */
        fun matches(known: List<String>, query: String, limit: Int = SUGGESTED): List<String> {
            val needle = query.trim().lowercase()
            if (needle.isEmpty()) return emptyList()
            return known.filter { needle in it.lowercase() }.take(limit)
        }
    }
}
