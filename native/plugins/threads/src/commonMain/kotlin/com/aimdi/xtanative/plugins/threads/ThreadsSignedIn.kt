package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsActivityItem
import com.aimdi.xtanative.networks.threads.ThreadsLink
import com.aimdi.xtanative.networks.threads.ThreadsOwnFeed
import com.aimdi.xtanative.networks.threads.ThreadsSession
import com.aimdi.xtanative.ui.plugins.PluginText

/** What a signed-in reader is offered of their own Threads, from the Threads tab's menu. */
internal enum class ThreadsSignedInPlace {
    /** Threads' Following feed: the accounts the reader follows on Threads, newest first. */
    FOLLOWING,

    /** Their activity: replies, mentions, quotes, reposts, likes and new followers. */
    ACTIVITY,

    /** The posts they saved on Threads. */
    SAVED,

    /** The posts they liked on Threads. */
    LIKED,

    /** Their own profile, with whom they follow and who follows them. */
    PROFILE,
}

/**
 * When a signed-in reader is offered each [ThreadsSignedInPlace], and where
 * it leads, decided apart from its drawing. A list whose path no probe has
 * seen answer is not offered, rather than a menu row that always fails: CI's
 * live probe (ThreadsSignedInLiveTest) logs how each candidate answers a
 * guest, and its constant here is switched on once one is seen to be real.
 */
internal object ThreadsSignedInRules {
    /**
     * Whether the Following feed's read is known to work. Its first path is
     * the timeline a Bearer's For you is read from, but whether Meta takes
     * `feed_type=following` there no guest can see (a guest is sent to sign
     * in whatever is asked), so it waits for a session's word.
     */
    const val FOLLOWING_PATH_KNOWN = false

    /** Whether one of the activity's paths is known to answer. */
    const val ACTIVITY_PATH_KNOWN = false

    /** Whether one of the saved posts' paths is known to answer. */
    const val SAVED_PATH_KNOWN = false

    /** Whether one of the liked posts' paths is known to answer. */
    const val LIKED_PATH_KNOWN = false

    /** Whether [place]'s read is known to answer; the reader's profile is a public page, and whom the cookies sign in as a read the sign-in itself makes. */
    fun pathKnown(place: ThreadsSignedInPlace): Boolean = when (place) {
        ThreadsSignedInPlace.FOLLOWING -> FOLLOWING_PATH_KNOWN
        ThreadsSignedInPlace.ACTIVITY -> ACTIVITY_PATH_KNOWN
        ThreadsSignedInPlace.SAVED -> SAVED_PATH_KNOWN
        ThreadsSignedInPlace.LIKED -> LIKED_PATH_KNOWN
        ThreadsSignedInPlace.PROFILE -> true
    }

    /**
     * What the menu offers a reader with [session], in order: nothing without
     * one; their profile only with cookies (whom a Bearer signs in as is read
     * nowhere); each list once its path is [known].
     */
    fun offered(session: ThreadsSession, known: (ThreadsSignedInPlace) -> Boolean = ::pathKnown): List<ThreadsSignedInPlace> =
        ThreadsSignedInPlace.entries.filter { place ->
            known(place) && if (place == ThreadsSignedInPlace.PROFILE) session.hasCookies else session.hasDirectAuth
        }

    /** The list of posts [place] opens; null for one that is no list of posts. */
    fun feedOf(place: ThreadsSignedInPlace): ThreadsOwnFeed? = when (place) {
        ThreadsSignedInPlace.FOLLOWING -> ThreadsOwnFeed.FOLLOWING
        ThreadsSignedInPlace.SAVED -> ThreadsOwnFeed.SAVED
        ThreadsSignedInPlace.LIKED -> ThreadsOwnFeed.LIKED
        else -> null
    }

    /** The route [place] opens; null for the reader's profile, whose handle is known only once asked. */
    fun route(place: ThreadsSignedInPlace): String? = when (place) {
        ThreadsSignedInPlace.ACTIVITY -> ThreadsRoutes.ACTIVITY
        ThreadsSignedInPlace.PROFILE -> null
        else -> feedOf(place)?.let(ThreadsRoutes::ownFeed)
    }

    /** The menu row's and the screen's title. */
    fun label(place: ThreadsSignedInPlace): PluginText = when (place) {
        ThreadsSignedInPlace.FOLLOWING -> Strings.following_on_threads
        ThreadsSignedInPlace.ACTIVITY -> Strings.activity
        ThreadsSignedInPlace.SAVED -> Strings.saved_on_threads
        ThreadsSignedInPlace.LIKED -> Strings.liked_on_threads
        ThreadsSignedInPlace.PROFILE -> Strings.your_profile
    }

    /** A list of posts' title. */
    fun title(feed: ThreadsOwnFeed): PluginText = when (feed) {
        ThreadsOwnFeed.FOLLOWING -> Strings.following_on_threads
        ThreadsOwnFeed.SAVED -> Strings.saved_on_threads
        ThreadsOwnFeed.LIKED -> Strings.liked_on_threads
    }

    /** What an empty list of posts says. */
    fun empty(feed: ThreadsOwnFeed): PluginText = when (feed) {
        ThreadsOwnFeed.FOLLOWING -> Strings.following_on_threads_empty
        ThreadsOwnFeed.SAVED -> Strings.saved_on_threads_empty
        ThreadsOwnFeed.LIKED -> Strings.liked_on_threads_empty
    }

    /**
     * The route an activity line opens: the post it is about (its short link,
     * whose author its page names), else the profile of whoever did it, else
     * none.
     */
    fun opens(item: ThreadsActivityItem): String? =
        item.postUrl?.let { ThreadsRoutes.post(ThreadsLink.Post(it, "")) } ?: item.handle?.let(ThreadsRoutes::profile)
}
