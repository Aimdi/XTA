package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.store.backup.XtaTime
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The tab as it last stood (threads_feed_snapshot.dart): the newest posts,
 * and when each followed account last answered. Restored, it paints the tab
 * at once and, while an account is inside its ten minutes, spares Meta the
 * same question after a restart. Kept in XTA's `plugin.threads.feed_snapshot`.
 */
internal data class ThreadsFeedSnapshot(val posts: List<ThreadsPost> = emptyList(), val answeredAt: Map<String, Instant> = emptyMap()) {
    val isEmpty: Boolean get() = posts.isEmpty()

    fun encode(): String = JsonObject(
        mapOf(
            "v" to JsonPrimitive(VERSION),
            "answeredAt" to JsonObject(answeredAt.mapValues { JsonPrimitive(XtaTime.iso(it.value)) }),
            "posts" to JsonArray(posts.take(CAP).map(ThreadsPostJson::json)),
        ),
    ).toString()

    /** Only what the reader still follows: an account dropped since must not come back from the snapshot. */
    fun followedBy(handles: Set<String>): ThreadsFeedSnapshot =
        ThreadsFeedSnapshot(posts.filter { it.sourceHandle in handles }, answeredAt.filterKeys { it in handles })

    /** One account's own posts, as the reader's cache of each account holds them: a repost is its reposter's. */
    fun postsOf(handle: String): List<ThreadsPost> = posts.filter { it.sourceHandle == handle }

    companion object {
        /** How many of the newest posts are kept. */
        const val CAP = 120
        private const val VERSION = 1

        fun decode(text: String?): ThreadsFeedSnapshot {
            val root = J.parse(text.orEmpty())
            if (root["v"].long != VERSION.toLong()) return ThreadsFeedSnapshot()
            val answered = root["answeredAt"].let { at -> at.keys.mapNotNull { key -> XtaTime.parse(at[key].string)?.let { key to it } }.toMap() }
            return ThreadsFeedSnapshot(root["posts"].list.mapNotNull(ThreadsPostJson::post), answered)
        }
    }
}
