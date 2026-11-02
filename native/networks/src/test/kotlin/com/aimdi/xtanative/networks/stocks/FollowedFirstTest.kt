package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.feed.FeedMember
import com.aimdi.xtanative.core.feed.FeedQueries
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.networks.Page
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking

class FollowedFirstTest {
    private val now: Instant = Instant.parse("2026-11-01T12:00:00Z")
    private val weth = "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"
    private val nvda = TickerPostTerms.of("\$NVDA")

    private fun follow(id: String, screenName: String = "user$id") = FeedMember(id, screenName)

    private fun ago(hours: Long) = now.minus(Duration.ofHours(hours))

    /** A thread of one post by [author] [hoursAgo] before [now] carrying [cashtags]. */
    private fun single(id: String, author: String, hoursAgo: Long = 1, cashtags: List<String> = listOf("NVDA")) =
        thread(id, post(id, author = author, cashtags = cashtags, at = ago(hoursAgo)))

    @Test
    fun theTermsAreTheSearchsCashtagsAndQuotedAddresses() {
        val terms = TickerPostTerms.of("(\"$weth\" OR \$weth)")
        assertEquals(setOf("WETH"), terms.cashtags)
        assertEquals(listOf(weth), terms.phrases)
        assertEquals(listOf("%\$WETH%", "%$weth%"), terms.likePatterns)
        assertTrue(TickerPostTerms.of("").isEmpty)
    }

    @Test
    fun aCashtagNamesTheTickerByItsEntityNotItsLetters() {
        assertTrue(nvda.names(post("1", cashtags = listOf("NVDA"))))
        assertFalse(nvda.names(post("2", cashtags = listOf("NVDAX"), text = "\$NVDAX")))
        assertFalse(nvda.names(post("3", text = "NVDA is up")))
    }

    @Test
    fun anEvmAddressNamesItInEitherCaseAnyOtherExactly() {
        assertTrue(TickerPostTerms.of("\"$weth\"").names(post("1", text = "buy ${weth.uppercase()}")))
        val solana = "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263"
        val exact = TickerPostTerms.of("\"$solana\"")
        assertTrue(exact.names(post("2", text = "see $solana")))
        assertFalse(exact.names(post("3", text = "see ${solana.lowercase()}")))
        assertTrue(exact.names(post("4", text = "chart", links = listOf("https://dexscreener.com/solana/$solana"))), "or in its links")
    }

    @Test
    fun aFollowedAccountMustHaveWrittenItARepostIsLeftOut() {
        val followed = setOf("a")
        assertTrue(FollowedPosts.isFollowedPost(post("1", author = "a", cashtags = listOf("NVDA")), followed, nvda))
        assertFalse(FollowedPosts.isFollowedPost(post("2", author = "b", cashtags = listOf("NVDA")), followed, nvda))
        val repost = post("3", author = "a", cashtags = listOf("NVDA"), reposted = post("original-3", author = "z", cashtags = listOf("NVDA")))
        assertFalse(FollowedPosts.isFollowedPost(repost, followed, nvda))
    }

    @Test
    fun theTopPartIsNewestFirstEachPostOnceOnlyFollowedPostsNamingTheTicker() {
        val top = FollowedPosts.section(
            listOf(
                single("old", "a", hoursAgo = 5),
                single("stranger", "z"),
                single("new", "b", hoursAgo = 1),
                single("off-topic", "a", cashtags = listOf("AAPL")),
                single("new", "b", hoursAgo = 1),
                // The same post again, in another thread: a reply under it.
                thread("thread", post("old", author = "a", cashtags = listOf("NVDA"), at = ago(5)), post("reply", author = "z", at = ago(1))),
            ),
            setOf("a", "b"),
            nvda,
            now,
        )
        assertEquals(listOf("new", "old"), top.map { it.id })
    }

    @Test
    fun atMostTenFromTheLastSevenDays() {
        val top = FollowedPosts.section((0 until 12).map { single("p$it", "a", hoursAgo = it + 1L) } + single("stale", "a", hoursAgo = 24 * 8), setOf("a"), nvda, now)
        assertEquals(FollowedPosts.SHOWN, top.size)
        assertFalse(top.any { it.id == "stale" })
        assertEquals("p0", top.first().id)
    }

    @Test
    fun aLaterThreadRepeatingAPostOfTheTopPartIsLeftOut() {
        val top = listOf(single("mine", "a"))
        val page = listOf(single("mine", "a"), thread("context", post("parent", author = "z"), post("mine", author = "a")), single("theirs", "z"))
        assertEquals(listOf("theirs"), FollowedPosts.withoutSection(page, top).map { it.id })
    }

    @Test
    fun theSearchesNarrowTheTickerSearchToFollowedAccountsPackedWithin512Characters() {
        val follows = (0 until 80).map { follow("$it", "account_number_$it") }
        val searches = FollowedPosts.searches("\$NVDA", follows)
        assertEquals(FollowedPosts.SEARCHES_PER_TICKER, searches.size)
        assertTrue(searches.all { it.length <= FeedQueries.LIMIT })
        assertTrue(searches.first().startsWith("\$NVDA (from:account_number_0 OR from:account_number_1 OR "))
        assertTrue(searches.first().endsWith(") -filter:retweets"))
        assertTrue(searches[0].length > FeedQueries.LIMIT - 40, "each search is filled: ${searches[0].length}")
    }

    @Test
    fun accountsSeenPostingTheTickerGoFirst() {
        val follows = listOf(follow("1"), follow("2"), follow("3"))
        assertEquals(listOf("\$NVDA (from:user3 OR from:user1 OR from:user2) -filter:retweets"), FollowedPosts.searches("\$NVDA", follows, knownPosters = setOf("3")))
        assertTrue(FollowedPosts.searches("", follows).isEmpty())
    }

    @Test
    fun aTickersSearchesAreKeptFiveMinutesAndAFailedOneAddsNothing() = runBlocking {
        var clock = now
        val asked = mutableListOf<String>()
        val source = FollowedTickerSource(
            search = { query ->
                asked.add(query)
                if (asked.size == 2) throw IllegalStateException("429")
                listOf(single("found-${asked.size}", "1"))
            },
            now = { clock },
        )
        val follows = listOf(follow("1"))
        assertEquals("found-1", source.fromX("\$NVDA", follows).await().single().id)
        assertEquals("found-1", source.fromX("\$NVDA", follows).await().single().id)
        assertEquals(1, asked.size)
        clock = clock.plus(FollowedPosts.KEPT_FOR)
        assertTrue(source.fromX("\$NVDA", follows).await().isEmpty(), "the search failed")
        assertEquals(2, asked.size)
    }

    @Test
    fun theDeviceAnswersNothingWhenItsPostsCannotBeRead() = runBlocking {
        val source = FollowedTickerSource(search = { emptyList() }, cached = { throw IllegalStateException("locked") })
        assertTrue(source.onDevice(nvda).isEmpty())
    }

    private val keeper = FollowedSectionKeeper()

    private val general: suspend (String?) -> Page<XThread> = { cursor ->
        if (cursor == null) Page(listOf(single("mine", "1"), single("theirs", "z")), "next") else Page(listOf(single("mine", "1"), single("later", "z")), null)
    }

    private fun source(cached: List<XThread> = emptyList(), searched: List<XThread> = emptyList()) =
        FollowedTickerSource(search = { searched }, cached = { cached }, now = { now })

    @Test
    fun theFirstPagePutsTheFollowedPostsOnTopALaterPageLeavesThemOut() = runBlocking {
        val follows = listOf(follow("1"))
        val first = FollowedPosts.page(null, "\$NVDA", follows, keeper, source(listOf(single("device", "1", hoursAgo = 2)), listOf(single("mine", "1"))), read = general)
        assertEquals(listOf("mine", "device", "theirs"), first.items.map { it.id })
        assertEquals("next", first.next)
        val section = keeper.section.value
        assertEquals(FollowedPart.FOLLOWED, section.partStartingAt("mine"))
        assertEquals(FollowedPart.OTHERS, section.partStartingAt("theirs"))
        assertNull(section.partStartingAt("device"))

        val next = FollowedPosts.page("next", "\$NVDA", follows, keeper, source(), read = general)
        assertEquals(listOf("later"), next.items.map { it.id })
        assertNull(next.next)
    }

    @Test
    fun withoutFollowedPostsThePageIsAsBeforeWithNoLabels() = runBlocking {
        val first = FollowedPosts.page(null, "\$NVDA", emptyList(), keeper, source(searched = listOf(single("mine", "1"))), read = general)
        assertEquals(listOf("mine", "theirs"), first.items.map { it.id })
        assertNull(keeper.section.value.partStartingAt("mine"))
    }

    @Test
    fun theFirstPageDoesNotWaitPastItsTimeForX() = runBlocking {
        val never = CompletableDeferred<List<XThread>>()
        val slow = FollowedTickerSource(search = { never.await() }, cached = { listOf(single("device", "1")) }, now = { now })
        val started = System.nanoTime()
        val first = FollowedPosts.page(null, "\$NVDA", listOf(follow("1")), keeper, slow, wait = Duration.ofMillis(200), read = general)
        val waited = Duration.ofNanos(System.nanoTime() - started)
        assertEquals(listOf("device", "mine", "theirs"), first.items.map { it.id })
        assertTrue(waited >= Duration.ofMillis(200) && waited < Duration.ofSeconds(5), "waited $waited")
        never.complete(emptyList())
    }

    @Test
    fun whenTheFirstPageHadNoOtherPostTheNextPagesFirstStartsTheSecondPart() {
        val top = listOf(single("mine", "1"))
        assertEquals(listOf("mine"), keeper.first(top, listOf(single("mine", "1"))).map { it.id })
        assertNull(keeper.section.value.firstOther)
        assertEquals(listOf("theirs"), keeper.next(listOf(single("mine", "1"), single("theirs", "z"))).map { it.id })
        assertEquals(FollowedPart.OTHERS, keeper.section.value.partStartingAt("theirs"))
        keeper.first(emptyList(), top)
        assertNull(keeper.section.value.partStartingAt("mine"), "no labels without a top part")
    }
}
