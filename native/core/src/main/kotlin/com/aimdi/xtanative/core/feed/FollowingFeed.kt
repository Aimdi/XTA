package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.XNoAccountException
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XTimelinePage
import com.aimdi.xtanative.core.x.signing.SigningUnavailableException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/** An account in the Following feed, oldest follow first as XTA orders them. */
data class FeedMember(val id: String, val screenName: String, val followedAt: Long = 0)

/** The two reads the feed makes. */
interface FeedReads {
    suspend fun search(query: String, cursor: String?): XTimelinePage
    suspend fun userPosts(userId: String): XTimelinePage
}

/** The feed's reads over X. */
fun XClient.feedReads(): FeedReads {
    val client = this
    return object : FeedReads {
        override suspend fun search(query: String, cursor: String?) = client.search(query, cursor)
        override suspend fun userPosts(userId: String) = client.userPosts(userId)
    }
}

/**
 * The Following feed, as XTA's group feed reads it: the followed accounts are
 * packed into as few `from:` searches as fit, each chunk is searched, and a
 * chunk whose search fails falls back to its accounts' own posts.
 *
 * Unlike XTA, the chunks are merged in time order ([MergedTimeline]), so
 * loading more never puts a newer post under an older one.
 */
class FollowingFeed private constructor(timeline: MergedTimeline, val chunkCount: Int) : FeedTimeline by timeline {
    companion object {
        private const val FALLBACK_CONCURRENCY = 2

        operator fun invoke(
            members: List<FeedMember>,
            reads: FeedReads,
            includeReplies: Boolean = true,
            includeReposts: Boolean = true,
            concurrency: Int = 3,
            pageSize: Int = 20,
            fallbackMillis: Long = 12_000,
        ): FollowingFeed {
            val chunks = FeedQueries.pack(
                members.sortedWith(compareBy<FeedMember> { it.followedAt }.thenBy { it.id }),
                { FeedQueries.term(it.screenName) },
                includeReplies,
                includeReposts,
            )
            val sources = chunks.map { chunk ->
                val query = FeedQueries.query(chunk.map { FeedQueries.term(it.screenName) }, includeReplies, includeReposts)
                TimelineSource(query) { cursor -> searchOrOwnPosts(reads, query, chunk, cursor, fallbackMillis) }
            }
            val wanted = { thread: XThread ->
                val post = thread.posts.firstOrNull()
                post != null && (includeReposts || !post.isRepost) && (includeReplies || post.replyToScreenName == null)
            }
            return FollowingFeed(MergedTimeline(sources, concurrency, pageSize, wanted), chunks.size)
        }

        /**
         * A chunk's search; when its first page fails, its accounts' own posts as
         * one last page, as XTA does: two at a time, within [fallbackMillis].
         */
        private suspend fun searchOrOwnPosts(
            reads: FeedReads,
            query: String,
            members: List<FeedMember>,
            cursor: String?,
            fallbackMillis: Long,
        ): XTimelinePage = try {
            reads.search(query, cursor)
        } catch (error: Throwable) {
            if (error is CancellationException || cursor != null || !fallsBack(error)) throw error
            val own = ownPosts(reads, members, fallbackMillis)
            if (own.isEmpty()) throw error
            XTimelinePage(own, bottomCursor = null, topCursor = null)
        }

        private suspend fun ownPosts(reads: FeedReads, members: List<FeedMember>, fallbackMillis: Long): List<XThread> {
            val gate = Semaphore(FALLBACK_CONCURRENCY)
            val pages = ArrayList<XTimelinePage>()
            withTimeoutOrNull(fallbackMillis) {
                coroutineScope {
                    members.map { member ->
                        async {
                            gate.withPermit { runCatching { reads.userPosts(member.id) }.getOrNull() }?.let { page -> synchronized(pages) { pages += page } }
                        }
                    }.awaitAll()
                }
            }
            return synchronized(pages) { pages.flatMap { it.threads } }
        }

        /**
         * Whether profiles are worth asking for after a failed search: not when
         * there is no connection, no account, or no way to sign requests.
         */
        fun fallsBack(error: Throwable): Boolean =
            error !is IOException && error !is XNoAccountException && error !is SigningUnavailableException
    }
}
