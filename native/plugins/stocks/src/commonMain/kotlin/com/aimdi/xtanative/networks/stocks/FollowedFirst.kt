package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.feed.FeedMember
import com.aimdi.xtanative.core.feed.FeedQueries
import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.networks.CryptoAddresses
import com.aimdi.xtanative.networks.Page
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What names a ticker in a post: its cashtags and, for a token, its quoted
 * contract, as the ticker's posts search spells them (`$NVDA`, `"0x…"`,
 * `("0x…" OR $PEPE)`).
 */
data class TickerPostTerms(val cashtags: Set<String>, val phrases: List<String>) {
    val isEmpty: Boolean get() = cashtags.isEmpty() && phrases.isEmpty()

    /** What a cached feed page must contain to hold such a post (SQL `LIKE`): a cheap first sieve, [names] decides. */
    val likePatterns: List<String> get() = cashtags.map { "%$$it%" } + phrases.map { "%$it%" }

    /** Whether [post] itself names the ticker: by its cashtag entities, not their letters, or by the contract in its text or links. */
    fun names(post: XPost): Boolean = Cashtags.of(post).any { it in cashtags } || phrases.any { mentions(post, it) }

    private fun mentions(post: XPost, phrase: String): Boolean {
        val parts = listOf(post.text) + post.entities.filterIsInstance<XEntity.Link>().map { it.expanded }
        // An EVM address is the same in either case; any other is not.
        val anyCase = CryptoAddresses.isEvm(phrase)
        return parts.any { it.contains(phrase, ignoreCase = anyCase) }
    }

    companion object {
        /** Dart's `\s`, which takes in Unicode's spaces too. */
        private const val SPACE = "\\s\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff"
        private val cashtag = Regex("\\$([^$SPACE()\"]+)")
        private val phrase = Regex("\"([^\"]+)\"")

        /** The terms of a ticker's posts search [query]: its `$` cashtags (upper-cased) and its quoted phrases. */
        fun of(query: String): TickerPostTerms = TickerPostTerms(
            cashtags = cashtag.findAll(query).map { it.groupValues[1].uppercase() }.toSet(),
            phrases = phrase.findAll(query).map { it.groupValues[1] }.toList(),
        )
    }
}

/** The two parts of one ticker's posts. */
enum class FollowedPart { FOLLOWED, OTHERS }

/** What the top part of a ticker's posts holds, and the thread the rest starts at. */
data class FollowedSection(val top: List<XThread> = emptyList(), val firstOther: String? = null) {
    /** The part whose label goes above [threadId], if one starts there. */
    fun partStartingAt(threadId: String): FollowedPart? = when {
        top.isEmpty() -> null
        threadId == top.first().id -> FollowedPart.FOLLOWED
        threadId == firstOther -> FollowedPart.OTHERS
        else -> null
    }
}

/** Kept with a ticker's feed: the first page sets where the parts start, later pages read it. */
class FollowedSectionKeeper {
    private val state = MutableStateFlow(FollowedSection())
    val section: StateFlow<FollowedSection> = state.asStateFlow()

    /** The first page: [top] above [page]'s other threads. */
    fun first(top: List<XThread>, page: List<XThread>): List<XThread> {
        val others = FollowedPosts.withoutSection(page, top)
        state.value = FollowedSection(top, if (top.isEmpty()) null else others.firstOrNull()?.id)
        return top + others
    }

    /** A later page without the top part's posts; when the first page had no other post, this page's first starts the second part. */
    fun next(page: List<XThread>): List<XThread> {
        val current = state.value
        val others = FollowedPosts.withoutSection(page, current.top)
        if (current.top.isNotEmpty() && current.firstOther == null && others.isNotEmpty()) state.value = current.copy(firstOther = others.first().id)
        return others
    }
}

/**
 * Where the posts of followed accounts about a ticker are looked for: the
 * feed pages kept on the device ([cached], by the ticker's terms), then a
 * few X searches ([search]), each ticker's kept [FollowedPosts.KEPT_FOR].
 * The searches run in [scope], so a page that stops waiting leaves them to
 * finish for the next load.
 */
class FollowedTickerSource(
    private val search: suspend (query: String) -> List<XThread>,
    private val cached: suspend (TickerPostTerms) -> List<XThread> = { emptyList() },
    val now: () -> Instant = Instant::now,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private class Kept(val at: Instant, val threads: Deferred<List<XThread>>)

    private val lock = Any()
    private val searched = HashMap<String, Kept>()

    /** The device's posts that may name the ticker; none when they cannot be read. */
    suspend fun onDevice(terms: TickerPostTerms): List<XThread> = orNothing { cached(terms) }

    /**
     * What X's searches for [query] by [follows] find ([FollowedPosts.searches],
     * [knownPosters] first), the same answer for [FollowedPosts.KEPT_FOR]. A
     * search that fails adds nothing.
     */
    fun fromX(query: String, follows: List<FeedMember>, knownPosters: Set<String> = emptySet()): Deferred<List<XThread>> = synchronized(lock) {
        val at = now()
        searched.values.removeIf { Duration.between(it.at, at) >= FollowedPosts.KEPT_FOR }
        searched.getOrPut(query) { Kept(at, scope.async { searchAll(FollowedPosts.searches(query, follows, knownPosters)) }) }.threads
    }

    private suspend fun searchAll(queries: List<String>): List<XThread> = coroutineScope {
        queries.map { query -> async { orNothing { search(query) } } }.awaitAll().flatten()
    }

    private suspend fun orNothing(read: suspend () -> List<XThread>): List<XThread> = try {
        read()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Which posts about one ticker are by accounts the reader follows, and the
 * searches that find more of them, as XTA's stocks_followed_posts.dart and
 * stocks_followed_source.dart put them first: on Stocks with a ticker picked
 * (or a watchlist of one), on a ticker's page and on a token's page. The
 * whole watchlist's feed stays as it is.
 */
object FollowedPosts {
    /** The most posts the top part shows, and how old they may be. */
    const val SHOWN = 10
    val WITHIN: Duration = Duration.ofDays(7)

    /** The most X searches one ticker spends on followed accounts. */
    const val SEARCHES_PER_TICKER = 3

    /** How much longer a ticker's first page waits for those searches; what comes later is kept for the next load. */
    val SEARCH_WAIT: Duration = Duration.ofSeconds(3)

    /** How long a ticker's searches are kept, so picking it again asks X nothing new. */
    val KEPT_FOR: Duration = Duration.ofMinutes(5)

    /** [post] counts when a followed account wrote it (a repost is someone else's post) and it names the ticker. */
    fun isFollowedPost(post: XPost, followed: Set<String>, terms: TickerPostTerms): Boolean =
        post.reposted == null && post.author.id in followed && terms.names(post)

    /** The followed accounts that wrote such a post in [threads]. */
    fun posters(threads: Iterable<XThread>, followed: Set<String>, terms: TickerPostTerms): Set<String> =
        threads.flatMap { it.posts }.filter { isFollowedPost(it, followed, terms) }.map { it.author.id }.toSet()

    /**
     * The top part from everything [found]: threads with a counting post from
     * the last [WITHIN], each post once, newest first, at most [SHOWN].
     */
    fun section(found: Iterable<XThread>, followed: Set<String>, terms: TickerPostTerms, now: Instant): List<XThread> {
        val cutoff = now.minus(WITHIN)
        val dated = found.mapNotNull { thread -> postedAt(thread, followed, terms)?.takeIf { it.isAfter(cutoff) }?.let { thread to it } }
        return dated.sortedByDescending { it.second }.fold(emptyList()) { section, (thread, _) ->
            if (section.size < SHOWN && withoutSection(listOf(thread), section).isNotEmpty()) section + thread else section
        }
    }

    /** [threads] without those that repeat a thread or a post of [section]. */
    fun withoutSection(threads: Iterable<XThread>, section: Iterable<XThread>): List<XThread> {
        val ids = section.map { it.id }.toSet()
        val posts = section.flatMap { thread -> thread.posts.map { it.id } }.toSet()
        return threads.filter { thread -> thread.id !in ids && thread.posts.none { it.id in posts } }
    }

    /**
     * The X searches for [query]'s posts by [follows], packed as the
     * Following feed packs its accounts but counting [query] in each search's
     * length (`$NVDA (from:a OR from:b) -filter:retweets`, at most 512
     * characters), at most [SEARCHES_PER_TICKER]. Accounts in [knownPosters]
     * go first, then the others in [follows]' order.
     */
    fun searches(query: String, follows: List<FeedMember>, knownPosters: Set<String> = emptySet()): List<String> {
        if (query.isEmpty()) return emptyList()
        val (known, others) = follows.partition { it.id in knownPosters }
        val terms = (known + others).map { FeedQueries.term(it.screenName) }
        return packWithin(terms, query).take(SEARCHES_PER_TICKER)
            .map { chunk -> "$query ${FeedQueries.query(chunk, includeReplies = true, includeReposts = false)}" }
    }

    /**
     * A page of [query]'s posts, as [read] gets them: on the first ([cursor]
     * null) the followed accounts' posts that name the ticker go on top, from
     * the device and what X said within [wait] of the page arriving, and
     * [keeper] notes where the parts start; later pages leave those posts out.
     * Without follows, or for a search that names no ticker, the pages are
     * [read]'s own.
     */
    suspend fun page(
        cursor: String?,
        query: String,
        follows: List<FeedMember>,
        keeper: FollowedSectionKeeper,
        source: FollowedTickerSource,
        wait: Duration = SEARCH_WAIT,
        read: suspend (cursor: String?) -> Page<XThread>,
    ): Page<XThread> {
        if (cursor != null) {
            val next = read(cursor)
            return Page(keeper.next(next.items), next.next)
        }
        val terms = TickerPostTerms.of(query)
        if (follows.isEmpty() || terms.isEmpty) {
            val first = read(null)
            return Page(keeper.first(emptyList(), first.items), first.next)
        }
        return coroutineScope {
            val followed = follows.map { it.id }.toSet()
            val onDevice = async { source.onDevice(terms) }
            val fromX = async { source.fromX(query, follows, posters(onDevice.await(), followed, terms)) }
            val first = read(null)
            val searched = withTimeoutOrNull(wait.toMillis()) { fromX.await().await() }.orEmpty()
            val top = section(onDevice.await() + searched, followed, terms, source.now())
            Page(keeper.first(top, first.items), first.next)
        }
    }

    /** When [thread]'s newest counting post was written; null when none counts. */
    private fun postedAt(thread: XThread, followed: Set<String>, terms: TickerPostTerms): Instant? =
        thread.posts.filter { isFollowedPost(it, followed, terms) }.mapNotNull { it.createdAt }.maxOrNull()

    /**
     * [FeedQueries.pack] for searches that narrow [within]: its length and a
     * space count against each chunk, as XTA's packFeedChunks counts them.
     */
    private fun packWithin(terms: List<String>, within: String): List<List<String>> {
        val separator = " OR "
        val overhead = FeedQueries.filters(includeReplies = true, includeReposts = false).joinToString(" ").length + 3 + within.length + 1
        val chunks = ArrayList<MutableList<String>>()
        var termsLength = 0
        for (term in terms) {
            if (chunks.isNotEmpty() && overhead + termsLength + separator.length + term.length <= FeedQueries.LIMIT) {
                chunks.last().add(term)
                termsLength += separator.length + term.length
            } else {
                chunks.add(mutableListOf(term))
                termsLength = term.length
            }
        }
        return chunks
    }
}
