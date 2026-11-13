package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.store.backup.ProfileSubscriptionRow

/** What the follow button on a profile says: the account is not followed on this device yet, or it is. */
internal enum class ThreadsFollowFace { FOLLOW, FOLLOWING }

/** Why a profile's list shows nothing: nothing was read, or what was read is hidden by the filter or the search. */
internal enum class ThreadsProfileEmpty { NOTHING, FILTERED, NO_MATCH }

/**
 * The profile screen's choices that need no Compose: its bar's title, the
 * follow button's face, the posts a tab shows under the reader's filter and
 * search, and why a list is empty. The filter and the search only narrow
 * what is already loaded; Meta is never asked anything else for them.
 */
internal object ThreadsProfileView {
    /** The bar's title: the profile's name once read (its handle when it gave none), else its @handle. */
    fun title(profile: ThreadsProfile?, handle: String): String = profile?.displayName ?: "@$handle"

    /** The banner's picture: Threads has no banner, so the profile's own picture, blurred; none without one. */
    fun bannerUrl(profile: ThreadsProfile): String? = profile.pictureUrl.trim().ifEmpty { null }

    /** The follow button's face, or null while the accounts followed here are still being read. */
    fun followFace(rows: List<ProfileSubscriptionRow>?, handle: String): ThreadsFollowFace? = when {
        rows == null -> null
        ThreadsAccounts.follows(rows, handle) -> ThreadsFollowFace.FOLLOWING
        else -> ThreadsFollowFace.FOLLOW
    }

    /** [posts] as the round toggles under the tabs ([content]) and the bar's search ([query]) leave them. */
    fun shown(posts: List<ThreadsPost>, content: ThreadsFeedContent, query: String): List<ThreadsPost> {
        val words = words(query)
        val kept = ThreadsFeedOptions(content = content).shown(posts)
        return if (words.isEmpty()) kept else kept.filter { matches(it, words) }
    }

    /** Why [shown] is empty when [read] posts were there to show. */
    fun empty(read: List<ThreadsPost>, content: ThreadsFeedContent, query: String): ThreadsProfileEmpty = when {
        read.isEmpty() -> ThreadsProfileEmpty.NOTHING
        words(query).isNotEmpty() -> ThreadsProfileEmpty.NO_MATCH
        content != ThreadsFeedContent.ALL -> ThreadsProfileEmpty.FILTERED
        else -> ThreadsProfileEmpty.NOTHING
    }

    /** Whether [post] says every one of [words], in its text, its quote, its link card, its topic or its author's name. */
    fun matches(post: ThreadsPost, words: List<String>): Boolean {
        val said = searchable(post)
        return words.all { said.contains(it) }
    }

    /** What the search looks in, lower case. */
    private fun searchable(post: ThreadsPost): String = listOfNotNull(
        post.text,
        post.quoted?.text,
        post.linkCard?.title,
        post.linkCard?.description,
        post.topicTag,
        post.authorName,
        post.handle,
    ).joinToString("\n").lowercase()

    /** The round toggles under a tab: every post, those with media, those with a link; Media drops its own toggle. */
    fun filters(mediaTab: Boolean): List<ThreadsFeedContent> =
        ThreadsFeedContent.entries.filterNot { mediaTab && it == ThreadsFeedContent.MEDIA }

    /** The toggle in force on a tab: [content], unless the tab does not offer it, then every post. */
    fun chosen(content: ThreadsFeedContent, mediaTab: Boolean): ThreadsFeedContent =
        content.takeIf { it in filters(mediaTab) } ?: ThreadsFeedContent.ALL

    /**
     * Each tab's width in a strip [width] wide, from the width each needs
     * ([naturals]): all equal when every one fits that share, else each its
     * own width and an equal part of the room left, else (wider than the
     * strip) its own width, the strip then scrolling sideways.
     */
    fun tabWidths(naturals: List<Int>, width: Int): List<Int> {
        if (naturals.isEmpty()) return naturals
        val share = width / naturals.size
        val sum = naturals.sum()
        val widths = when {
            naturals.all { it <= share } -> naturals.map { share }
            sum <= width -> naturals.map { it + (width - sum) / naturals.size }
            else -> return naturals
        }
        // The pixels division leaves go to the last tab, so the strip ends where the screen does.
        return widths.dropLast(1) + (widths.last() + width - widths.sum())
    }

    /** The words a search is made of, lower case, its # and @ kept since they are typed in the text too. */
    fun words(query: String): List<String> = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
}
