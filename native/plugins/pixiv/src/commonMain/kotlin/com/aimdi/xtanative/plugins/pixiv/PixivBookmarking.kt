package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivBookmarkApi
import com.aimdi.xtanative.networks.pixiv.PixivBookmarkDetail
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivNovelApi
import com.aimdi.xtanative.networks.pixiv.PixivNovel
import com.aimdi.xtanative.networks.pixiv.PixivUser
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlinx.coroutines.CancellationException

/** What a bookmark left: whether the work is bookmarked, whether its creator was followed, and whether its pages are to be saved. */
internal data class PixivBookmarkOutcome(val bookmarked: Boolean, val followedAuthor: Boolean = false, val saveAll: Boolean = false)

/**
 * Every bookmark write, on the signed-in Pixiv account as XTA's bookmark
 * actions make them: the heart, the bookmark editor and bookmark-after-save
 * all come here, under the reader's Bookmarking settings (default
 * visibility, auto-tags, following the creator, saving the work). Whether a
 * work is bookmarked is what Pixiv sent with it until this session changed
 * it ([works], [novels]). Without an account a write fails as any read does.
 * Removing a bookmark also takes away a copy this device kept from before.
 */
internal class PixivBookmarking(
    private val api: PixivBookmarkApi,
    private val novelApi: PixivNovelApi,
    private val follows: PixivFollowing,
    private val settings: () -> Settings,
    private val ownId: () -> Long?,
    private val kept: PixivDeviceBookmarks? = null,
) {
    val works = PixivMarkStore()
    val novels = PixivMarkStore()

    /** The visibility a bookmark made without the editor gets, works and novels alike. */
    val defaultRestrict: String get() = if (settings()[PixivPrefs.defaultPrivateBookmark]) PixivRestrict.PRIVATE else PixivRestrict.PUBLIC

    fun isBookmarked(illust: PixivIllust): Boolean = works.now.on(illust.id, illust.isBookmarked)

    fun isBookmarked(novel: PixivNovel): Boolean = novels.now.on(novel.id, novel.isBookmarked)

    /**
     * The work's bookmark as Pixiv has it now. A card loaded before it was
     * bookmarked or removed elsewhere is brought in line, so the heart and
     * whether the next write is a new bookmark follow Pixiv.
     */
    suspend fun detail(illust: PixivIllust): PixivBookmarkDetail {
        val detail = api.detail(illust.id)
        if (isBookmarked(illust) != detail.isBookmarked && !works.now.isBusy(illust.id)) works.mark(illust.id, detail.isBookmarked)
        return detail
    }

    /** The heart: bookmarks with the defaults, or takes the bookmark away; null while a write for the work is on its way. */
    suspend fun toggle(illust: PixivIllust): PixivBookmarkOutcome? =
        works.exclusive(illust.id) { if (isBookmarked(illust)) unbookmark(illust) else bookmark(illust) }

    /** The editor's Save: files the bookmark with [restrict] under [tags]; null while another write for the work is on its way. */
    suspend fun save(illust: PixivIllust, restrict: String, tags: List<String>): PixivBookmarkOutcome? =
        works.exclusive(illust.id) { bookmark(illust, restrict, tags) }

    /** The editor's Remove. */
    suspend fun remove(illust: PixivIllust): PixivBookmarkOutcome? = works.exclusive(illust.id) { unbookmark(illust) }

    /** Bookmark-after-save: a work just saved is bookmarked when the reader asked for that and it is not yet; null when nothing was written. */
    suspend fun afterSave(illust: PixivIllust): PixivBookmarkOutcome? {
        if (!settings()[PixivPrefs.bookmarkAfterDownload] || isBookmarked(illust)) return null
        return works.exclusive(illust.id) { bookmark(illust, fromSave = true) }
    }

    /**
     * Bookmarks [illust], or files its bookmark again. Tags from the editor
     * win; without them the work's own go along when auto-tag is on. A new
     * bookmark may also save the work (never when [fromSave]) and follow its creator.
     */
    private suspend fun bookmark(illust: PixivIllust, restrict: String? = null, tags: List<String>? = null, fromSave: Boolean = false): PixivBookmarkOutcome {
        val isNew = !isBookmarked(illust)
        api.add(illust.id, restrict ?: defaultRestrict, tags ?: autoTags(illust.tags.map { it.name }))
        works.mark(illust.id, true)
        if (!isNew) return PixivBookmarkOutcome(bookmarked = true)
        val saveAll = !fromSave && settings()[PixivPrefs.downloadAfterBookmark]
        return PixivBookmarkOutcome(bookmarked = true, followedAuthor = followAuthor(authorOf(illust)), saveAll = saveAll)
    }

    private suspend fun unbookmark(illust: PixivIllust): PixivBookmarkOutcome {
        api.delete(illust.id)
        works.mark(illust.id, false)
        kept?.remove(PixivKind.WORK, illust.id)
        return PixivBookmarkOutcome(bookmarked = false)
    }

    /** A novel's heart: bookmarks with the default visibility, or takes the bookmark away; where it now stands, null while a write is on its way. */
    suspend fun toggle(novel: PixivNovel): Boolean? =
        novels.exclusive(novel.id) { if (isBookmarked(novel)) unbookmark(novel) else bookmark(novel, defaultRestrict) }

    /** A novel's long press: bookmarks it privately, or turns a public bookmark private. */
    suspend fun bookmarkPrivately(novel: PixivNovel): Boolean? = novels.exclusive(novel.id) { bookmark(novel, PixivRestrict.PRIVATE) }

    private suspend fun bookmark(novel: PixivNovel, restrict: String): Boolean {
        novelApi.addBookmark(novel.id, restrict)
        novels.mark(novel.id, true)
        return true
    }

    private suspend fun unbookmark(novel: PixivNovel): Boolean {
        novelApi.deleteBookmark(novel.id)
        novels.mark(novel.id, false)
        kept?.remove(PixivKind.NOVEL, novel.id)
        return false
    }

    /** The tags a bookmark made without the editor carries: the work's own, popularity tags left out, while auto-tag is on. */
    fun autoTags(tags: List<String>): List<String> = if (settings()[PixivPrefs.autoTagBookmarks]) ownTags(tags) else emptyList()

    /** A creator followed publicly after a bookmark, never the reader themself nor one already followed; a follow that fails leaves the bookmark standing. */
    private suspend fun followAuthor(author: PixivUser): Boolean {
        if (!settings()[PixivPrefs.followAfterBookmark] || author.id == ownId() || follows.isFollowed(author)) return false
        return try {
            follows.toggle(author) == true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /** Forgets this session's changes, for another account. */
    fun clear() {
        works.clear()
        novels.clear()
    }

    companion object {
        /** Pixiv's popularity tags such as `1000users入り` or `東方100users入り`, which say how many bookmarked a work, not what it is. */
        private val popularity = Regex("""\d+users入り""")

        fun ownTags(tags: List<String>): List<String> = tags.filterNot { popularity.containsMatchIn(it) }

        fun authorOf(illust: PixivIllust): PixivUser =
            PixivUser(illust.userId, illust.userName, illust.userAccount, avatarUrl = illust.userAvatarUrl, isFollowed = illust.userIsFollowed)
    }
}

/** What the bookmark editor holds while the reader edits: the tags offered, which are checked, what is typed, and whether the bookmark is private. */
internal data class PixivBookmarkDraft(val tags: List<String>, val checked: Set<String>, val typed: String = "", val private: Boolean = false) {
    val restrict: String get() = if (private) PixivRestrict.PRIVATE else PixivRestrict.PUBLIC

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
         * The editor's start from a work's bookmark [detail]: its tags as
         * Pixiv lists them, those it is filed under checked. A work not
         * bookmarked yet takes [defaultRestrict], its [autoTags] checked.
         */
        fun fromDetail(detail: PixivBookmarkDetail, defaultRestrict: String, autoTags: List<String>): PixivBookmarkDraft {
            val tags = detail.tags.map { it.name }.distinct()
            val checked = detail.tags.filter { it.checked || (!detail.isBookmarked && it.name in autoTags) }.map { it.name }
            val restrict = if (detail.isBookmarked) detail.restrict else defaultRestrict
            return PixivBookmarkDraft(tags, checked.toSet(), private = restrict == PixivRestrict.PRIVATE)
        }

        /** Up to [limit] of the reader's [known] tags holding [query], whatever its case: the suggestions under the tag field. */
        fun matches(known: List<String>, query: String, limit: Int = SUGGESTED): List<String> {
            val needle = query.trim().lowercase()
            if (needle.isEmpty()) return emptyList()
            return known.filter { needle in it.lowercase() }.take(limit)
        }
    }
}
