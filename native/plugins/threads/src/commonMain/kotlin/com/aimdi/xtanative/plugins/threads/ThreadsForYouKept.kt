package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsForYouSource
import com.aimdi.xtanative.networks.threads.ThreadsForYouState
import com.aimdi.xtanative.networks.threads.ThreadsFailure
import com.aimdi.xtanative.networks.threads.ThreadsPerson
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.store.backup.XtaTime
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The sections of the Threads tab: the accounts followed here, Threads' own recommended feed, and the posts liked here. */
internal enum class ThreadsSection { HOME, FOR_YOU, LIKED }

/** Where the reader stopped in the For you feed: the first post in view, and how far it had scrolled off, in pixels. */
internal data class ThreadsPlace(val postId: String? = null, val offset: Int = 0) {
    val atTop: Boolean get() = postId == null
}

/**
 * What the tab keeps of the For you feed between launches, in the plugin's
 * own folder: the posts last shown (never a token or a cookie), whose page
 * they were, when they were read, where the reader stopped, and the section
 * the tab was on. Restored, the feed is painted at once where it was left.
 */
internal data class ThreadsForYouKept(
    val posts: List<ThreadsPost> = emptyList(),
    val source: ThreadsForYouSource? = null,
    val readAt: Instant? = null,
    val place: ThreadsPlace = ThreadsPlace(),
    val section: ThreadsSection = ThreadsSection.HOME,
) {
    fun encode(): String = JsonObject(
        mapOf(
            "v" to JsonPrimitive(VERSION),
            "section" to JsonPrimitive(section.name.lowercase()),
            "source" to (source?.let { JsonPrimitive(it.name.lowercase()) } ?: JsonNull),
            "readAt" to (readAt?.let { JsonPrimitive(XtaTime.iso(it)) } ?: JsonNull),
            "placePost" to (place.postId?.let(::JsonPrimitive) ?: JsonNull),
            "placeOffset" to JsonPrimitive(place.offset),
            "posts" to JsonArray(kept(posts, place).map(ThreadsPostJson::json)),
        ),
    ).toString()

    companion object {
        /** How many posts are kept. */
        const val CAP = 80

        /** How old a restored feed may be before opening it reads a new one. */
        const val STALE_MILLIS = 30 * 60 * 1000L

        private const val VERSION = 1

        /** What [text] keeps; anything unreadable is nothing kept. */
        fun decode(text: String?): ThreadsForYouKept {
            val root = J.parse(text.orEmpty())
            if (root["v"].long != VERSION.toLong()) return ThreadsForYouKept()
            return ThreadsForYouKept(
                posts = root["posts"].list.mapNotNull(ThreadsPostJson::post),
                source = ThreadsForYouSource.entries.firstOrNull { it.name.lowercase() == root["source"].string },
                readAt = XtaTime.parse(root["readAt"].string),
                place = ThreadsPlace(root["placePost"].string?.ifEmpty { null }, root["placeOffset"].long?.toInt() ?: 0),
                section = ThreadsSection.entries.firstOrNull { it.name.lowercase() == root["section"].string } ?: ThreadsSection.HOME,
            )
        }

        /** The [CAP] posts kept: those around where the reader stopped, so the place is never cut away. */
        fun kept(posts: List<ThreadsPost>, place: ThreadsPlace): List<ThreadsPost> {
            if (posts.size <= CAP) return posts
            val at = posts.indexOfFirst { it.id == place.postId }.coerceAtLeast(0)
            val from = (at - CAP / 4).coerceIn(0, posts.size - CAP)
            return posts.subList(from, from + CAP)
        }

        /**
         * Whether opening the section reads Meta: when nothing is shown, or
         * when what is shown is older than [STALE_MILLIS] and the reader is at
         * its top. A reader part way down keeps their place and pulls to refresh.
         */
        fun readsOnOpen(state: ThreadsForYouState, place: ThreadsPlace, now: Long): Boolean {
            if (state.loading || state.problem != null) return false
            if (state.posts.isEmpty()) return !state.loaded
            val age = state.readAt?.let { now - it.toEpochMilli() } ?: Long.MAX_VALUE
            return age > STALE_MILLIS && place.atTop
        }
    }
}

/** One row of the For you list. */
internal sealed interface ThreadsForYouRow {
    val key: String

    /** Why the public page stands in for the session's own. */
    data class SessionNotice(val problem: ThreadsFailure) : ThreadsForYouRow {
        override val key: String get() = "session-notice"
    }

    /** That this is what Threads recommends to everyone. */
    data object PublicNote : ThreadsForYouRow {
        override val key: String get() = "public-note"
    }

    data class Post(val post: ThreadsPost) : ThreadsForYouRow {
        override val key: String get() = KEY + post.id
    }

    /** The feed's authors not followed here yet. */
    data class People(val people: List<ThreadsPerson>) : ThreadsForYouRow {
        override val key: String get() = "people"
    }

    data object LoadingMore : ThreadsForYouRow {
        override val key: String get() = "loading-more"
    }

    data class MoreProblem(val problem: Throwable) : ThreadsForYouRow {
        override val key: String get() = "more-problem"
    }

    data object End : ThreadsForYouRow {
        override val key: String get() = "end"
    }

    companion object {
        private const val KEY = "post:"

        /** After how many posts the people strip sits, as in Home. */
        const val PEOPLE_AFTER = 3

        /**
         * The list for [state]: why a session was not used, or that the page is
         * the public one; the posts, the people to follow after the first
         * few; then that more is coming, why it did not, or that this is all.
         */
        fun of(state: ThreadsForYouState, people: List<ThreadsPerson>): List<ThreadsForYouRow> = buildList {
            val refused = state.sessionProblem
            when {
                refused != null -> add(SessionNotice(refused))
                state.source == ThreadsForYouSource.GUEST && state.posts.isNotEmpty() -> add(PublicNote)
            }
            val at = minOf(PEOPLE_AFTER, state.posts.size)
            state.posts.take(at).forEach { add(Post(it)) }
            if (people.isNotEmpty() && state.posts.isNotEmpty()) add(People(people))
            state.posts.drop(at).forEach { add(Post(it)) }
            val failed = state.moreProblem
            when {
                state.posts.isEmpty() -> Unit
                failed != null -> add(MoreProblem(failed))
                state.loadingMore || state.next != null -> add(LoadingMore)
                state.ended -> add(End)
            }
        }

        /** The post a row's [key] names; null for a row that is none. */
        fun postId(key: Any?): String? = (key as? String)?.takeIf { it.startsWith(KEY) }?.removePrefix(KEY)

        /** Where [place]'s post is in [rows]; null when it is at the top, or no longer there. */
        fun indexOf(rows: List<ThreadsForYouRow>, place: ThreadsPlace): Int? =
            place.postId?.let { id -> rows.indexOfFirst { it is Post && it.post.id == id } }?.takeIf { it >= 0 }
    }
}
