package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.Page
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

/** The home tab's feed, notes, panes and article cache, as XTA's substack_store, substack_screen and substack_article_cache tests drive them. */
class SubstackHomeTest {
    private val now = 1_800_000_000_000L
    private val alpha = SubstackPublication("alpha", "https://alpha.substack.com", "Alpha", logoUrl = "https://logo/alpha")
    private val beta = SubstackPublication("beta", "https://beta.substack.com", "Beta")

    private fun post(publication: SubstackPublication, n: Int, date: String = "2026-10-0${n}T10:00:00Z") = SubstackPost(
        "${publication.id}$n", "${publication.name} $n", "${publication.id}-$n", publication.baseUrl, publication.name, postDate = date,
        canonicalUrl = "${publication.baseUrl}/p/${publication.id}-$n", reactionCount = n.toLong(),
    )

    private suspend fun settle() = repeat(200) { yield() }

    @Test
    fun theRememberedFeedReadsBackAsItWasWrittenAndADamagedOneAsNothing() {
        val pages = mapOf("alpha\nhttps://alpha.substack.com" to SubstackRememberedPage(now, listOf(post(alpha, 1), post(alpha, 2))))
        assertEquals(pages, SubstackFeedMemory.decode(SubstackFeedMemory.encode(pages)))
        listOf(null, "", "{", """{"v":2,"pages":{}}""", """{"v":1,"pages":{"k":{"answeredAt":"never","posts":[]}}}""")
            .forEach { assertTrue(SubstackFeedMemory.decode(it).isEmpty(), "$it") }
    }

    @Test
    fun aRestoredFeedPaintsAtOnceAndReadsOnlyThePublicationsThatAreNoLongerFresh(): Unit = runBlocking {
        val asked = ArrayList<String>()
        val reads = SubstackPostReads { publication, _, _ -> asked += publication.id; listOf(post(publication, 3)) }
        val memory = mapOf(
            "alpha\nhttps://alpha.substack.com" to SubstackRememberedPage(now - 60_000, listOf(post(alpha, 1))),
            "beta\nhttps://beta.substack.com" to SubstackRememberedPage(now - SubstackFeed.FRESH_MS - 1, listOf(post(beta, 1))),
        )
        var remembered: Map<String, SubstackRememberedPage> = emptyMap()
        val home = SubstackHomeFeed(reads, this, recall = { memory }, remember = { remembered = it }, clock = { now })
        home.follow(listOf(alpha, beta))
        settle()
        assertEquals(listOf("beta"), asked, "the fresh publication is not asked again")
        assertEquals(listOf("alpha1", "beta3"), home.state.value.posts.map { it.id }.sorted(), "a page read again replaces the remembered one")
        assertTrue(home.state.value.loaded)
        assertEquals(now - 60_000, remembered["alpha\nhttps://alpha.substack.com"]?.answeredAt, "a fresh page keeps when it answered")
        assertEquals(now, remembered["beta\nhttps://beta.substack.com"]?.answeredAt)
    }

    @Test
    fun aFailedRefreshKeepsThePostsAndSaysSoUntilARefreshAnswers(): Unit = runBlocking {
        var failing = false
        val reads = SubstackPostReads { publication, _, _ ->
            if (failing && publication.id == "beta") throw NetworkRequestException(Substack.NETWORK, 503, "down")
            listOf(post(publication, 1))
        }
        val home = SubstackHomeFeed(reads, this, clock = { now })
        home.follow(listOf(alpha, beta))
        settle()
        assertEquals(2, home.state.value.posts.size)
        assertFalse(home.state.value.failed)
        failing = true
        home.refresh(force = true)
        settle()
        assertEquals(2, home.state.value.posts.size, "what was shown stays")
        assertTrue(home.state.value.failed)
        assertEquals(503, (home.state.value.refreshError as NetworkRequestException).code)
        failing = false
        home.refresh(force = false)
        settle()
        assertFalse(home.state.value.failed, "a failure is asked again even when the feed is fresh")
    }

    @Test
    fun followingNobodyIsALoadedEmptyFeed(): Unit = runBlocking {
        val home = SubstackHomeFeed({ _, _, _ -> error("nothing to read") }, this, clock = { now })
        home.follow(emptyList())
        settle()
        assertTrue(home.state.value.loaded)
        assertTrue(home.state.value.posts.isEmpty())
    }

    @Test
    fun notesSayWhenTheirNextPageFailedAndKeepWhatWasShown(): Unit = runBlocking {
        var fail = false
        val notes = SubstackNotes({ _, cursor ->
            if (fail) throw NetworkRequestException(Substack.NETWORK, 500, "x")
            Page(listOf(SubstackNote("n${cursor ?: 0}", "Note")), if (cursor == null) "1" else null)
        }, { listOf("alpha.substack.com") })
        val feed = SubstackNotesFeed(notes, this, { listOf(alpha) }, clock = { now })
        feed.refresh(force = false)
        settle()
        assertEquals(listOf("n0"), feed.state.value.notes.map { it.id })
        assertTrue(feed.state.value.canLoadMore)
        fail = true
        feed.loadMore()
        settle()
        assertNotNull(feed.state.value.loadMoreError)
        assertNull(feed.state.value.refreshError)
        assertEquals(listOf("n0"), feed.state.value.notes.map { it.id })
        fail = false
        feed.loadMore()
        settle()
        assertEquals(listOf("n0", "n1"), feed.state.value.notes.map { it.id })
        assertFalse(feed.state.value.canLoadMore)
    }

    @Test
    fun homeShowsTheFollowedPublicationsPostsThroughItsFilterAndInboxOnlyTheUnread() {
        val posts = listOf(post(alpha, 1), post(alpha, 2).copy(audience = "only_paid"), post(beta, 3), post(SubstackPublication("gone", "https://gone.substack.com", "Gone"), 4))
        val read = setOf(posts[0].key)
        val everything = SubstackHomeRules.home(posts, listOf(alpha, beta), SubstackFeedFilter.ALL, read, { false }, SubstackLoadedOptions())
        assertEquals(listOf("beta3", "alpha2", "alpha1"), everything.map { it.id }, "newest first; an unfollowed publication's posts go")
        assertEquals(listOf("alpha2"), SubstackHomeRules.home(posts, listOf(alpha, beta), SubstackFeedFilter.PAID, read, { false }, SubstackLoadedOptions()).map { it.id })
        assertEquals(listOf("alpha2", "beta3"), SubstackHomeRules.inbox(posts, listOf(alpha, beta), read, SubstackLoadedOptions(order = SubstackOrder.OLDEST)).map { it.id })
        assertEquals(listOf("beta3"), SubstackHomeRules.inbox(posts, listOf(alpha, beta), read, SubstackLoadedOptions(query = "beta")).map { it.id })
        assertEquals("https://logo/alpha", SubstackHomeRules.logoOf(listOf(alpha, beta), posts[1]))
        assertEquals(setOf("https://alpha.substack.com", "https://beta.substack.com", "https://gone.substack.com"), SubstackHomeRules.withUnread(posts, read))
        assertEquals(2, SubstackHomeRules.activeCount(SubstackLoadedOptions("x", SubstackOrder.POPULAR), SubstackFeedFilter.ALL))
        assertTrue(SubstackHomeRules.filtered(SubstackLoadedOptions(), SubstackFeedFilter.SAVED))
        assertFalse(SubstackHomeRules.filtered(SubstackLoadedOptions(query = "x"), null))
    }

    @Test
    fun theLibraryFindsPostsByTitleOrByAKeptBodyWhosePassageItShows() {
        val posts = listOf(post(alpha, 1), post(beta, 2))
        val found = SubstackHomeRules.library(posts, "Alpha", mapOf(posts[1].key to "…a passage…"))
        assertEquals(listOf(posts[0] to null, posts[1] to "…a passage…"), found)
        assertEquals(listOf(posts[0] to null, posts[1] to null), SubstackHomeRules.library(posts, "", emptyMap()))
        assertTrue(SubstackHomeRules.matches("ALPHA.SUB", alpha))
    }

    @Test
    fun theArticleCacheKeepsTheNewestWithinItsLimitsAndForgetsTheStale(): Unit = runBlocking {
        val files = MemoryFiles()
        var clock = now
        val cache = SubstackArticleCache(files, maxArticles = 2, maxBytes = 100_000, clock = { clock })
        (1..3).forEach { cache.put(post(alpha, it).copy(bodyHtml = "<p>Body $it</p>")) }
        assertNull(cache.get(alpha, "alpha-1"), "the oldest goes past the limit")
        assertEquals("<p>Body 3</p>", cache.get(alpha, "alpha-3")?.bodyHtml)
        assertEquals(3, files.texts.size, "two articles and the index")
        clock += 15L * 24 * 60 * 60 * 1000
        assertNull(cache.get(alpha, "alpha-3"), "past its time")
        cache.put(post(alpha, 4))
        assertEquals(3, files.texts.size, "a post without a body is not kept")
        assertTrue(SubstackArticleCache.isCurrent(post(alpha, 1), post(alpha, 1).copy(postDate = null)))
        assertFalse(SubstackArticleCache.isCurrent(post(alpha, 1), post(alpha, 1).copy(title = "Edited")))
        val sizes = listOf(SubstackArticleCache.Entry("a", 60, 1), SubstackArticleCache.Entry("b", 60, 2), SubstackArticleCache.Entry("c", 500, 3))
        assertEquals(listOf("c"), SubstackArticleCache.keep(sizes, articles = 5, bytes = 100).map { it.key })
    }
}

/** Files in memory, as the plugin's folder holds them. */
class MemoryFiles : SubstackFiles {
    val texts = LinkedHashMap<String, String>()

    override suspend fun read(name: String): String? = texts[name]

    override suspend fun write(name: String, text: String) {
        texts[name] = text
    }

    override suspend fun delete(name: String) {
        texts.remove(name)
    }
}
