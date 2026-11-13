package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsPeople
import com.aimdi.xtanative.networks.threads.ThreadsPeopleList
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.networks.threads.ThreadsSession

/** What a people list (followers, following, both, likers, reposters) shows, decided apart from its drawing. */
internal object ThreadsPeopleView {
    /** What fills a list's place. */
    enum class Body {
        /** No session: Threads shows these lists only to signed-in readers. */
        SIGNED_OUT,
        LOADING,

        /** The first page failed and nothing is shown. */
        PROBLEM,

        /** Meta's list is empty. */
        EMPTY,

        /** People were read, but none matches the words typed. */
        NO_MATCH,
        PEOPLE,
    }

    /** Typed words shorter than this are only filtered where shown: one letter is no search worth a session read. */
    const val MIN_QUERY = 2

    /** Whether [session] can read these lists: cookies or a Bearer. */
    fun signedIn(session: ThreadsSession): Boolean = session.hasDirectAuth

    /** The tabs of [handle]'s lists for a reader signed in as [me], if anyone: "both" is not asked of the reader's own account. */
    fun tabsFor(signedIn: Boolean, me: String?, handle: String): List<ThreadsPeopleList> =
        ThreadsPeople.tabs(signedIn, ownProfile = me != null && me.trim().removePrefix("@").equals(handle.trim().removePrefix("@"), ignoreCase = true))

    /** The words Meta is asked to search [list] for, once typing settles; null for none, or a list Meta does not search. */
    fun serverQuery(list: ThreadsPeopleList, typed: String): String? =
        typed.trim().removePrefix("@").trim().takeIf { list.searchesOnServer && it.length >= MIN_QUERY }

    /** The people shown: the first page and those after it, each once, filtered by the words [typed]. */
    fun shown(first: List<ThreadsProfile>, more: List<ThreadsProfile>, typed: String): List<ThreadsProfile> =
        ThreadsPeople.filter((first + more).distinctBy { it.username.lowercase() }, typed)

    /** What the list's place shows, given whether the first page [read] and failed with a [problem]. */
    fun body(signedIn: Boolean, read: Boolean, problem: Boolean, people: Int, typed: String): Body = when {
        !signedIn -> Body.SIGNED_OUT
        people > 0 -> Body.PEOPLE
        !read && problem -> Body.PROBLEM
        !read -> Body.LOADING
        typed.isNotBlank() -> Body.NO_MATCH
        else -> Body.EMPTY
    }

    /** The count a tab names, when the profile told it. */
    fun count(profile: ThreadsProfile?, list: ThreadsPeopleList): Long? = when (list) {
        ThreadsPeopleList.FOLLOWERS -> profile?.followerCount
        ThreadsPeopleList.FOLLOWING -> profile?.followingCount
        else -> null
    }

    /**
     * Whether a post's like count opens who liked it: for a signed-in reader,
     * a post Meta counted likes on, whose media id is known.
     */
    fun opensLikers(post: ThreadsPost, signedIn: Boolean): Boolean = opens(post.likeCount, post, signedIn)

    /** Whether a post's repost count opens who reposted it: as [opensLikers], for reposts. */
    fun opensReposters(post: ThreadsPost, signedIn: Boolean): Boolean = opens(post.repostCount, post, signedIn)

    /** Whether a post's quote count opens its quotes: as [opensLikers], for quotes. */
    fun opensQuotes(post: ThreadsPost, signedIn: Boolean): Boolean = opens(post.quoteCount, post, signedIn)

    private fun opens(count: Long?, post: ThreadsPost, signedIn: Boolean): Boolean =
        signedIn && (count ?: 0) > 0 && ThreadsPeople.mediaId(post) != null
}

/** What a post's list of posts (its quotes) shows, decided apart from its drawing. */
internal object ThreadsPostLists {
    /** The first page and those after it, each post once by its short code (else its id). */
    fun shown(first: List<ThreadsPost>, more: List<ThreadsPost>): List<ThreadsPost> = (first + more).distinctBy { it.shortcode ?: it.id }
}
