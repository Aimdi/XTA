package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.networks.Page
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

/**
 * The paging rules of XTA's Substack stores: substack_store_recovery, substack_feed_order,
 * substack_publication_reader, substack_search_upgrade, substack_post_identity and the BUG-05 probe.
 */
class SubstackFeedsTest {
    private val size = SubstackFeed.PAGE_SIZE

    private fun post(publication: SubstackPublication, id: String, date: String? = null, reactions: Long? = null, slug: String = id) =
        SubstackPost(id, "Article $id", slug, publication.baseUrl, publication.name, postDate = date, reactionCount = reactions)

    private fun page(publication: SubstackPublication, offset: Int) = (0 until size).map { post(publication, "${offset + it}") }

    private class Reads(var answer: suspend (SubstackPublication, Int) -> List<SubstackPost>) : SubstackPostReads {
        val calls = CopyOnWriteArrayList<Pair<String, Int>>()

        override suspend fun posts(publication: SubstackPublication, limit: Int, offset: Int): List<SubstackPost> {
            calls += publication.baseUrl to offset
            return answer(publication, offset)
        }
    }

    // ---- the home feed ----

    @Test
    fun aRefreshReadsAllThirtyFollowedPublications() = runBlocking {
        val publications = (0 until 30).map { publication("p$it") }
        val reads = Reads { publication, offset -> page(publication, offset).map { it.copy(id = "${publication.id}-${it.id}") } }
        val snapshot = SubstackFeed(reads, publications).refresh()
        assertEquals(30, reads.calls.map { it.first }.toSet().size, "BUG-05: 24 before the fix")
        assertEquals(0, snapshot.pending)
    }

    @Test
    fun theFeedIsNewestFirstTiesByFeedKeyUndatedLast() = runBlocking {
        val alpha = publication("alpha")
        val beta = publication("beta")
        val dates = mapOf(
            alpha to mapOf("1" to "2026-09-01T10:00:00.000Z", "2" to null, "3" to "2026-09-03T08:00:00.000Z", "4" to "2026-09-01T10:00:00Z"),
            beta to mapOf("5" to "2026-09-01T12:00:00+02:00", "6" to "2026-09-02T00:00:00.000Z", "7" to null, "8" to "not a date"),
        )
        val reads = Reads { publication, _ -> dates.getValue(publication).map { (id, date) -> post(publication, id, date, slug = "post-$id") } }
        val snapshot = SubstackFeed(reads, listOf(alpha, beta)).refresh()
        assertEquals(listOf("3", "6", "1", "4", "5", "2", "7", "8"), snapshot.posts.map { it.id })
    }

    @Test
    fun failedPublicationPagesKeepTheirOffsetWhileOthersProgress() = runBlocking {
        val alpha = publication("alpha")
        val beta = publication("beta")
        var failing = true
        val reads = Reads { publication, offset ->
            when {
                offset == 0 -> page(publication, 0)
                publication == alpha && failing -> throw IllegalStateException("Offline")
                else -> listOf(post(publication, "$offset"))
            }
        }
        val feed = SubstackFeed(reads, listOf(alpha, beta))
        feed.refresh()
        val partial = feed.more()
        assertEquals(17, partial.posts.size)
        assertEquals(1, partial.failedCount)
        assertTrue(partial.canLoadMore)
        failing = false
        val recovered = feed.more()
        assertEquals(listOf(0, size, size), reads.calls.filter { it.first == alpha.baseUrl }.map { it.second })
        assertEquals(listOf(0, size), reads.calls.filter { it.first == beta.baseUrl }.map { it.second })
        assertEquals(18, recovered.posts.size)
        assertEquals(0, recovered.failedCount)
        assertFalse(recovered.canLoadMore)
    }

    @Test
    fun aFailedRefreshKeepsWhatWasShownAndAFreshOneReplacesIt() = runBlocking {
        val alpha = publication("alpha")
        val reads = Reads { _, _ -> listOf(post(alpha, "cached")) }
        val feed = SubstackFeed(reads, listOf(alpha))
        feed.refresh()
        reads.answer = { _, _ -> throw IllegalStateException("Offline") }
        val failed = feed.refresh()
        assertEquals(listOf("cached"), failed.posts.map { it.id })
        assertEquals(1, failed.failedCount)
        reads.answer = { _, _ -> listOf(post(alpha, "fresh")) }
        val retried = feed.more()
        assertEquals(0, reads.calls.last().second, "the failed first page is asked again")
        assertEquals(listOf("fresh"), retried.posts.map { it.id })
        assertEquals(0, retried.failedCount)
    }

    @Test
    fun aRefreshStaysWithinTheConcurrencyBoundAndPagingRotatesFailures() = runBlocking {
        val publications = (0 until 27).map { publication("pub$it") }
        val gate = CompletableDeferred<Unit>()
        val active = AtomicInteger()
        var most = 0
        val reads = Reads { publication, offset ->
            most = maxOf(most, active.incrementAndGet())
            gate.await()
            active.decrementAndGet()
            if (publication.subdomain.removePrefix("pub").toInt() < 24) throw IllegalStateException("Unavailable")
            page(publication, offset)
        }
        val feed = SubstackFeed(reads, publications)
        val refreshing = async { feed.refresh() }
        repeat(10) { yield() }
        assertEquals(SubstackFeed.CONCURRENCY, active.get())
        gate.complete(Unit)
        val refreshed = refreshing.await()
        assertEquals(SubstackFeed.CONCURRENCY, most)
        assertEquals(27, reads.calls.map { it.first }.toSet().size)
        assertEquals(3 * size, refreshed.posts.size)
        assertEquals(24, refreshed.pending)
        feed.more()
        assertEquals(27 + SubstackFeed.BATCH, reads.calls.size)
        assertTrue(reads.calls.drop(27).all { it.second == 0 }, "failed pages are retried first")
        feed.more()
        assertEquals(3, reads.calls.count { it.second == size })
    }

    @Test
    fun aNewerLoadSupersedesAnOlderOnesLateResults() = runBlocking {
        val alpha = publication("alpha")
        val late = CompletableDeferred<List<SubstackPost>>()
        val reads = Reads { publication, offset -> if (offset == 0) page(publication, 0) else late.await() }
        val feed = SubstackFeed(reads, listOf(alpha))
        feed.refresh()
        val paging = async { feed.more() }
        repeat(5) { yield() }
        assertEquals(1, reads.calls.count { it.second == size })
        feed.more()
        assertEquals(1, reads.calls.count { it.second == size }, "a second load-more while one runs asks nothing")
        reads.answer = { _, _ -> listOf(post(alpha, "new")) }
        feed.refresh()
        late.complete(listOf(post(alpha, "stale")))
        paging.await()
        assertEquals(listOf("new"), feed.snapshot().posts.map { it.id })
    }

    @Test
    fun followingAnotherSetDropsOnlyTheRemovedPublicationsPostsAndLateResults() = runBlocking {
        val alpha = publication("alpha")
        val beta = publication("beta")
        val reads = Reads { publication, _ -> listOf(post(publication, "shared-id")) }
        val feed = SubstackFeed(reads, listOf(alpha, beta))
        assertEquals(2, feed.refresh().posts.size, "the same id on two publications is two posts")
        feed.follow(listOf(beta))
        assertEquals(listOf(beta.baseUrl), feed.snapshot().posts.map { it.publicationBaseUrl })

        val moved = SubstackPublication("beta", "https://custom.example", "beta")
        val delayed = CompletableDeferred<List<SubstackPost>>()
        reads.answer = { _, _ -> delayed.await() }
        val stale = async { feed.refresh() }
        repeat(5) { yield() }
        feed.follow(listOf(moved))
        reads.answer = { publication, _ -> listOf(post(publication, "new")) }
        feed.refresh()
        delayed.complete(listOf(post(beta, "old")))
        stale.await()
        assertEquals(listOf("new" to "https://custom.example"), feed.snapshot().posts.map { it.id to it.publicationBaseUrl })
    }

    @Test
    fun overlappingPagesUpdateTheirPostsAndAPageWithNothingNewStops() = runBlocking {
        val alpha = publication("alpha")
        val reads = Reads { publication, offset -> (0 until size).map { post(publication, "$it", reactions = if (offset == 0) 1 else 10) } }
        val feed = SubstackFeed(reads, listOf(alpha))
        feed.refresh()
        val snapshot = feed.more()
        assertEquals(size, snapshot.posts.size)
        assertTrue(snapshot.posts.all { it.reactionCount == 10L })
        assertFalse(snapshot.canLoadMore)
    }

    @Test
    fun anRssPageAfterAnApiPageRepeatsNoPostAndTheApiCopyStays() = runBlocking {
        val fieldNotes = publication("fieldnotes")
        fun api(slug: String, id: Int) = SubstackPost("$id", "Title of $slug", slug, fieldNotes.baseUrl, "Field Notes", canonicalUrl = "${fieldNotes.baseUrl}/p/$slug")
        val reads = Reads { _, offset ->
            (0 until size).map { if (offset == 0) api("post-$it", 1000 + it) else api("post-${it + offset - size}", 0).copy(id = "post-${it + offset - size}") }
        }
        val feed = SubstackFeed(reads, listOf(fieldNotes))
        feed.refresh()
        val slugs = feed.more().posts.map { it.slug }
        assertEquals(slugs.toSet().size, slugs.size, "duplicates before BUG-02 was fixed")
        assertEquals("1000", feed.snapshot().posts.first { it.slug == "post-0" }.id)
    }

    @Test
    fun aRefreshThatIsNotForcedSkipsPublicationsAnsweredWithinTheWindow() = runBlocking {
        var now = 0L
        val alpha = publication("alpha")
        val reads = Reads { publication, _ -> listOf(post(publication, "a")) }
        val feed = SubstackFeed(reads, listOf(alpha), clock = { now })
        feed.refresh(force = false)
        feed.refresh(force = false)
        assertEquals(1, reads.calls.size, "a second open inside the window asks nothing")
        feed.refresh()
        assertEquals(2, reads.calls.size, "pull to refresh asks again")
        now += SubstackFeed.FRESH_MS
        feed.refresh(force = false)
        assertEquals(3, reads.calls.size)
    }

    @Test
    fun feedFiltersAreLocalAndTheSavedFilterAsksTheLibrary() {
        val alpha = publication("alpha")
        val free = post(alpha, "a")
        val paid = post(alpha, "p").copy(audience = "only_paid")
        val episode = post(alpha, "e").copy(audioUrl = "https://cdn.example/e.mp3")
        val posts = listOf(free, paid, episode)
        fun shown(filter: SubstackFeedFilter, read: Set<String> = emptySet(), saved: Set<String> = emptySet()) =
            posts.filter { SubstackFilters.matches(it, filter, read) { post -> post.id in saved } }.map { it.id }
        assertEquals(listOf("p", "e"), shown(SubstackFeedFilter.UNREAD, read = setOf(free.key)))
        assertEquals(listOf("p"), shown(SubstackFeedFilter.PAID))
        assertEquals(listOf("a", "e"), shown(SubstackFeedFilter.FREE))
        assertEquals(listOf("e"), shown(SubstackFeedFilter.PODCAST))
        assertEquals(listOf("a"), shown(SubstackFeedFilter.SAVED, saved = setOf("a")))
        assertEquals(emptyList(), shown(SubstackFeedFilter.VIDEO))
    }

    // ---- a publication's archive ----

    private val example = SubstackPublication("example", "https://example.substack.com", "Example")

    private fun archivePost(id: Int, date: String? = null, audience: String? = null, audio: String? = null, video: Boolean = false, likes: Long? = null) =
        SubstackPost("$id", "Article $id", "article-$id", example.baseUrl, example.name, postDate = date, audience = audience, audioUrl = audio, hasVideoUpload = video, reactionCount = likes)

    @Test
    fun aFailedNextPageKeepsTheArticlesAndRetryAsksTheFailedOffset() = runBlocking {
        val asked = ArrayList<Int>()
        var fail = false
        val archive = SubstackArchive({ limit, offset ->
            asked += offset
            if (fail) throw IllegalStateException("offline") else (0 until if (offset == 0) limit else 1).map { archivePost(offset + it) }
        })
        archive.first()
        fail = true
        val failed = archive.more()
        assertEquals(20, failed.posts.size)
        assertEquals(20, failed.offset)
        assertTrue(failed.failedMore)
        fail = false
        val retried = archive.retry()
        assertEquals(listOf(0, 20, 20), asked)
        assertEquals(21, retried.posts.size)
        assertNull(retried.error)
        assertFalse(retried.canLoadMore, "a short page is the last")
    }

    @Test
    fun aFailedFirstPageKeepsTheArticlesAndRetryAsksTheFirstPage() = runBlocking {
        val asked = ArrayList<Int>()
        var answer: (Int) -> List<SubstackPost> = { offset -> (0 until 20).map { archivePost(offset + it) } }
        val archive = SubstackArchive({ _, offset -> asked += offset; answer(offset) })
        archive.first()
        answer = { throw IllegalStateException("offline") }
        val failed = archive.first()
        assertEquals(20, failed.posts.size)
        assertFalse(failed.failedMore)
        answer = { listOf(archivePost(42)) }
        assertEquals(listOf("42"), archive.retry().posts.map { it.id })
        assertEquals(listOf(0, 0, 0), asked)
    }

    @Test
    fun repeatedServerPagesStopPagingAndDuplicatesShowOnce() = runBlocking {
        val asked = ArrayList<Int>()
        val archive = SubstackArchive({ _, offset ->
            asked += offset
            (0 until 20).map { archivePost(it, likes = if (offset == 0) null else 50) }
        })
        archive.first()
        val repeated = archive.more()
        assertEquals(20, repeated.posts.size)
        assertFalse(repeated.canLoadMore)
        assertEquals(50L, repeated.posts.first().reactionCount, "the newer copy of a post wins")
        archive.more()
        assertEquals(listOf(0, 20), asked)
        val doubled = SubstackArchive({ _, _ -> listOf(archivePost(1), archivePost(1), archivePost(2)) }).first()
        assertEquals(listOf("1", "2"), doubled.posts.map { it.id })
    }

    @Test
    fun anArchiveAndItsSearchAskTheirOwnEndpointsAndOffsets() = runBlocking {
        val http = FakeHttp { asked -> ok("[" + (0 until 20).joinToString(",") { """{"id":${asked.url.queryParameter("offset")!!.toInt() + it},"slug":"s$it","title":"T"}""" } + "]") }
        val client = clientOf(http)
        SubstackArchive.of(client, example).also { it.first(); it.more() }
        SubstackArchive.search(client, example, "climate").also { it.first(); it.more() }
        assertEquals(
            listOf("/api/v1/posts" to "0", "/api/v1/posts" to "20", "/api/v1/archive" to "0", "/api/v1/archive" to "20"),
            http.urls.map { it.encodedPath to it.queryParameter("offset") },
        )
        assertEquals(listOf("20", "20", "20", "20"), http.urls.map { it.queryParameter("limit") })
        assertEquals("climate", http.urls.last().queryParameter("search"))
    }

    @Test
    fun publicationFiltersAndOrdersLeaveTheLoadedPostsAlone() {
        val posts = listOf(archivePost(1), archivePost(2, audience = "only_paid"), archivePost(3, audio = "https://example.com/a.mp3"), archivePost(4, video = true))
        assertEquals(3, SubstackFilters.publicationPosts(posts, SubstackPublicationFilter.UNREAD, SubstackOrder.NEWEST, setOf("1")).size)
        assertEquals(listOf("1", "3", "4"), SubstackFilters.publicationPosts(posts, SubstackPublicationFilter.FREE, SubstackOrder.NEWEST, emptySet()).map { it.id })
        assertEquals(listOf("3"), SubstackFilters.publicationPosts(posts, SubstackPublicationFilter.PODCASTS, SubstackOrder.NEWEST, emptySet()).map { it.id })
        assertEquals(listOf("4"), SubstackFilters.publicationPosts(posts, SubstackPublicationFilter.VIDEOS, SubstackOrder.NEWEST, emptySet()).map { it.id })

        val dated = listOf(
            archivePost(1), archivePost(2, date = "2026-04-02T10:00:00+02:00", likes = 10),
            archivePost(3, date = "2026-04-02T08:30:00Z", likes = 20), archivePost(4, date = "2026-04-02T08:00:00Z", likes = 10),
        )
        fun order(order: SubstackOrder) = SubstackFilters.publicationPosts(dated, SubstackPublicationFilter.ALL, order, emptySet()).map { it.id }
        assertEquals(listOf("3", "2", "4", "1"), order(SubstackOrder.NEWEST))
        assertEquals(listOf("2", "4", "3", "1"), order(SubstackOrder.OLDEST))
        assertEquals("3", order(SubstackOrder.POPULAR).first())
        assertEquals(listOf("3"), SubstackFilters.loaded(dated, " ARTICLE 3 ", SubstackOrder.NEWEST).map { it.id })
        assertEquals(listOf("3", "2", "4", "1"), SubstackFilters.loaded(dated, "", SubstackOrder.POPULAR).map { it.id })
    }

    // ---- notes ----

    private fun note(id: String, reactions: Long? = null) = SubstackNote(id, id, reactionCount = reactions)

    @Test
    fun notePagesKeepTheirIssuingHostAndARefreshAsksTheNextHost() = runBlocking {
        val calls = ArrayList<Pair<String?, String?>>()
        val notes = SubstackNotes({ host, cursor ->
            calls += host to cursor
            Page(listOf(note(cursor ?: "first")), if (cursor == null) "next" else null)
        }, { listOf("alpha.substack.com", "beta.substack.com") })
        notes.refresh()
        notes.more()
        notes.refresh()
        assertEquals(listOf<Pair<String?, String?>>("alpha.substack.com" to null, "alpha.substack.com" to "next", "beta.substack.com" to null), calls)
    }

    @Test
    fun aFailedNotesPageKeepsTheNotesAndRetriesTheSameCursor() = runBlocking {
        var fail = true
        val cursors = ArrayList<String?>()
        val notes = SubstackNotes({ _, cursor ->
            cursors += cursor
            if (cursor != null && fail) throw IllegalStateException("Offline")
            Page(listOf(note(cursor ?: "first")), if (cursor == null) "next" else null)
        }, { listOf("alpha.substack.com") })
        notes.refresh()
        val failed = notes.more()
        assertEquals(listOf("first"), failed.notes.map { it.id })
        assertEquals("next", failed.next)
        assertTrue(failed.error != null)
        fail = false
        val retried = notes.more()
        assertEquals(listOf(null, "next", "next"), cursors)
        assertEquals(listOf("first", "next"), retried.notes.map { it.id })
        assertNull(retried.error)
    }

    @Test
    fun aCursorSeenBeforeEndsThePagingAndANoteSeenAgainReplacesItselfInPlace() = runBlocking {
        val calls = AtomicInteger()
        val notes = SubstackNotes({ _, cursor ->
            calls.incrementAndGet()
            Page(listOfNotNull(note("same", if (cursor == null) 1 else 5), cursor?.let { note(it) }), when (cursor) { null -> "a"; "a" -> "b"; else -> "a" })
        }, { emptyList() })
        notes.refresh()
        repeat(3) { notes.more() }
        assertEquals(3, calls.get())
        assertNull(notes.state.next)
        assertEquals(listOf("same", "a", "b"), notes.state.notes.map { it.id })
        assertEquals(5L, notes.state.notes.first().reactionCount)
    }

    @Test
    fun notesAskSubstackComWhenNothingIsFollowedAndTheHostsOfFollowsOtherwise() = runBlocking {
        val http = FakeHttp { ok("""{"items":[],"nextCursor":null}""") }
        val client = clientOf(http)
        var follows = emptyList<SubstackPublication>()
        val notes = SubstackNotes.of(client) { follows }
        notes.refresh()
        follows = listOf(SubstackPublication("pe", "https://newsletter.pragmaticengineer.com", "PE"))
        notes.refresh()
        assertEquals(listOf("substack.com", "newsletter.pragmaticengineer.com"), http.hosts)
    }

    // ---- searching the followed publications' archives ----

    private fun article(id: String, pub: String = "one", day: Int = 1) =
        SubstackPost(id, "Article $id", id, "https://$pub.substack.com", "Publication $pub", postDate = "2026-09-${day.toString().padStart(2, '0')}T12:00:00Z")

    @Test
    fun aSearchMergesNewestFirstWithoutConfusingEqualIdsOnTwoPublications() = runBlocking {
        val search = SubstackPostSearch({ publication, _, _ -> listOf(article("same", publication.id, day = if (publication.id == "one") 1 else 2)) }, listOf(publication("one"), publication("two")))
        assertEquals(listOf("Publication two", "Publication one"), search.first().posts.map { it.publicationName })
    }

    @Test
    fun aPartialFailureKeepsTheOtherArticlesAndRetriesOnlyTheFailedPublication() = runBlocking {
        val calls = CopyOnWriteArrayList<String>()
        var twoFails = true
        val search = SubstackPostSearch({ publication, _, offset ->
            calls += publication.id
            if (publication.id == "two" && twoFails) throw IllegalStateException("two offline")
            listOf(article("${publication.id}-$offset", publication.id))
        }, listOf(publication("one"), publication("two")))
        val partial = search.first()
        assertEquals(1, partial.failedCount)
        assertEquals(1, partial.posts.size)
        twoFails = false
        val retried = search.retryFailed()
        assertEquals(0, retried.failedCount)
        assertEquals(1, calls.count { it == "one" })
        assertEquals(2, calls.count { it == "two" })
        assertEquals(2, retried.posts.size)
    }

    @Test
    fun pagingUsesEachPublicationsOwnOffsetAndStopsAnUnchangedPage() = runBlocking {
        val calls = CopyOnWriteArrayList<Pair<String, Int>>()
        val search = SubstackPostSearch({ publication, _, offset ->
            calls += publication.id to offset
            if (publication.id == "one") listOf(article("a", day = if (offset == 0) 1 else 5)) else emptyList()
        }, listOf(publication("one"), publication("two")))
        search.first()
        val more = search.more()
        assertEquals(listOf("one" to 0, "two" to 0, "one" to 20), calls)
        assertEquals(1, more.posts.size)
        assertEquals(5, more.posts.single().publishedAt!!.atZone(java.time.ZoneOffset.UTC).dayOfMonth, "the newer copy updates the row")
        assertFalse(more.hasMore)
    }

    @Test
    fun onlyFourPublicationSearchesRunBeforeTheNextBatchStarts() = runBlocking {
        val gates = (0 until 4).map { CompletableDeferred<List<SubstackPost>>() }
        val started = CopyOnWriteArrayList<String>()
        val search = SubstackPostSearch({ publication, _, _ ->
            started += publication.id
            val index = publication.id.removePrefix("pub").toInt()
            if (index < 4) gates[index].await() else emptyList()
        }, (0 until 9).map { publication("pub$it") })
        val running = async { search.first() }
        repeat(10) { yield() }
        assertEquals(4, started.size)
        gates.forEach { it.complete(emptyList()) }
        running.await()
        assertEquals(9, started.size)
    }

    // ---- discovering publications ----

    @Test
    fun nameResultsPageDeduplicateAndStopAtARepeatedPage() = runBlocking {
        val pages = ArrayDeque(listOf(listOf(publication("one")), listOf(publication("one"), publication("two")), listOf(publication("two"))))
        val asked = ArrayList<Int>()
        val search = SubstackPublicationSearch { page -> asked += page; Page(pages.removeFirst(), "${page + 1}") }
        search.first()
        search.more()
        val last = search.more()
        search.more()
        assertEquals(listOf(0, 1, 2), asked)
        assertEquals(listOf("one", "two"), last.publications.map { it.id })
        assertFalse(last.hasMore)
    }

    @Test
    fun aFailedDiscoveryPageKeepsTheListAndAsksThatPageAgain() = runBlocking {
        val asked = ArrayList<Int>()
        var fail = false
        val search = SubstackPublicationSearch { page ->
            asked += page
            if (fail) throw IllegalStateException("offline") else Page(listOf(publication("p$page")), "${page + 1}")
        }
        search.first()
        fail = true
        val failed = search.more()
        assertEquals(1, failed.publications.size)
        assertTrue(failed.retryMore)
        fail = false
        val retried = search.more()
        assertEquals(listOf(0, 1, 1), asked)
        assertNull(retried.error)
        assertEquals(2, retried.publications.size)
    }

    @Test
    fun aCategoryLeaderboardPagesWithinItsCategory() = runBlocking {
        val http = FakeHttp { ok("""{"publications":[{"subdomain":"p${it.url.queryParameter("page")}"}]}""") }
        val search = SubstackPublicationSearch.category(clientOf(http), 2)
        search.first()
        search.more()
        assertEquals(listOf("/api/v1/category/public/2/all" to "0", "/api/v1/category/public/2/all" to "1"), http.urls.map { it.encodedPath to it.queryParameter("page") })
        assertEquals(listOf("p0", "p1"), search.state.publications.map { it.id })
    }
}
