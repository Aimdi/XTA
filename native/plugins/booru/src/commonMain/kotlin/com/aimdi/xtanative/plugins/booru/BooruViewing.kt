package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.networks.booru.BooruPost
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** A list the viewer swipes through: its posts, the feed that loads more of them, and the search they came from. */
internal class ViewerSource(val posts: List<BooruPost>, val feed: BooruFeed? = null, val search: BooruSearch? = null)

/**
 * The lists the viewer was opened on, by key, for the route that opens it: a
 * route is text, a list is not. The last [capacity] are kept while the plugin
 * runs; a viewer whose list is gone reads its post alone.
 */
internal class BooruViewers(private val capacity: Int = 16) {
    private val kept = LinkedHashMap<String, ViewerSource>()

    @Synchronized
    fun put(source: ViewerSource): String {
        val key = UUID.randomUUID().toString()
        kept[key] = source
        while (kept.size > capacity) kept.remove(kept.keys.first())
        return key
    }

    @Synchronized
    fun get(key: String): ViewerSource? = kept[key]
}

/** How the viewer follows the list it swipes through (booru_pager_store.dart). */
internal object BooruPager {
    /** How near the end of the list a page asks the feed for more. */
    const val PREFETCH = 4

    /** The pictures worth loading ahead of the post at [index]: the next one's, then the previous one's; a video never. */
    fun preloadUrls(posts: List<BooruPost>, index: Int): List<String> =
        listOf(index + 1, index - 1).filter { it in posts.indices && !posts[it].isVideo }.map { posts[it].displayUrl }

    /** Whether [next] is [current] with more posts after it: a feed refreshed into other posts leaves the viewer as it was. */
    fun extends(current: List<BooruPost>, next: List<BooruPost>): Boolean =
        next.size > current.size && current.isNotEmpty() && current.first().key == next.first().key && current.last().key == next[current.lastIndex].key
}

/**
 * What one viewer has already asked a host about its posts (booru_post_lookups.dart):
 * a post's tag kinds, related posts, notes and pools. Answers are held while the
 * viewer is open, the last [capacity] of them; a failed one is asked again next time.
 */
internal class BooruLookups(private val scope: CoroutineScope, private val capacity: Int = 40) {
    private val answers = LinkedHashMap<String, Deferred<Any?>>(16, 0.75f, true)

    /** The answer for [key], asked with [ask] only the first time. */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> remember(key: String, ask: suspend () -> T): T {
        val answer = synchronized(answers) {
            answers.getOrPut(key) { scope.async { ask() } }.also { while (answers.size > capacity) answers.remove(answers.keys.first()) }
        }
        return try {
            answer.await() as T
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            synchronized(answers) { if (answers[key] === answer) answers.remove(key) }
            throw error
        }
    }
}
