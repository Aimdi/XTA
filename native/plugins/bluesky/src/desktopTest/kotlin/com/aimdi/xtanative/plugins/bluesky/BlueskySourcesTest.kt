package com.aimdi.xtanative.plugins.bluesky

import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.bluesky.Bluesky
import com.aimdi.xtanative.networks.bluesky.BlueskyFeedGenerator
import com.aimdi.xtanative.networks.bluesky.BlueskyPost
import com.aimdi.xtanative.networks.bluesky.BlueskySelection
import com.aimdi.xtanative.networks.bluesky.bluePost
import com.aimdi.xtanative.networks.bluesky.ok
import com.aimdi.xtanative.networks.bluesky.postView
import com.aimdi.xtanative.store.settings.BlueskyPrefs
import com.aimdi.xtanative.ui.plugins.PagedItems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import okhttp3.HttpUrl

/** XTA's bluesky_feed_browser_test.dart: Discover's custom feeds, the lists, and the pages read of each. */
class BlueskySourcesTest {
    private suspend fun settle() = repeat(10) { yield() }

    private fun page(vararg ids: String, next: String? = null) = Page(ids.map { bluePost(it) }, next)

    private fun texts(pages: PagedItems<BlueskyPost>) = pages.state.value.items.map { it.text }

    @Test
    fun eachFeedKeepsItsOwnPagesSoALateOrFailedReadNeverShowsInAnother() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        val answers = mutableMapOf<String, CompletableDeferred<Page<BlueskyPost>>>()
        val sources = SourcePages(scope) { uri, _ -> CompletableDeferred<Page<BlueskyPost>>().also { answers[uri] = it }.await() }
        val a = sources.of("one", "A")
        val b = sources.of("one", "B")
        settle()
        assertTrue(b.state.value.loading, "the feed picked shows at once, loading")
        answers.getValue("B").complete(page("b"))
        settle()
        answers.getValue("A").complete(page("a"))
        settle()
        assertEquals(listOf("b"), texts(b))
        assertEquals(listOf("a"), texts(a))
        assertSame(a, sources.of("one", "A"), "going back to a feed shows what was read of it")

        val failing = sources.of("one", "C")
        settle()
        answers.getValue("C").completeExceptionally(IllegalStateException("failed"))
        settle()
        assertTrue(texts(failing).isEmpty())
        assertNotNull(failing.state.value.problem)
        assertNotSame(a, sources.of("two", "A"), "another AppView reads the feed anew")
        scope.cancel()
    }

    @Test
    fun aFailedRefreshKeepsThePostsShownAndATryAgainClearsIt() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        var fail = false
        val pages = PagedItems(scope, key = BlueskyPost::uri) { if (fail) error("failed") else page("a") }
        settle()
        fail = true
        pages.refresh()
        settle()
        assertEquals(listOf("a"), texts(pages))
        assertNotNull(pages.state.value.problem)
        fail = false
        pages.refresh()
        settle()
        assertNull(pages.state.value.problem)
        scope.cancel()
    }

    @Test
    fun pagingAsksOnceAtATimeKeepsTheFeedsOrderAndStopsAtARepeatedCursor() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        val next = CompletableDeferred<Page<BlueskyPost>>()
        var calls = 0
        val pages = PagedItems(scope, key = BlueskyPost::uri) { cursor ->
            calls++
            if (cursor == null) page("older", "newer", next = "c1") else next.await()
        }
        settle()
        assertEquals(listOf("older", "newer"), texts(pages))
        pages.more()
        settle()
        pages.more()
        settle()
        assertEquals(2, calls)
        assertTrue(pages.state.value.loadingMore)
        next.complete(page("newer", "third", next = "c1"))
        settle()
        assertEquals(listOf("older", "newer", "third"), texts(pages))
        assertNull(pages.state.value.next)
        scope.cancel()
    }

    @Test
    fun aFailedPageKeepsItsCursorForATryAgain() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        var fail = true
        val pages = PagedItems(scope, key = BlueskyPost::uri) { cursor ->
            when {
                cursor == null -> page("first", next = "c1")
                fail -> error("failed")
                else -> page("second")
            }
        }
        settle()
        pages.more()
        settle()
        assertEquals(listOf("first"), texts(pages))
        assertEquals("c1", pages.state.value.next)
        assertNotNull(pages.state.value.moreProblem)
        fail = false
        pages.retryMore()
        settle()
        assertEquals(listOf("first", "second"), texts(pages))
        assertNull(pages.state.value.moreProblem)
        scope.cancel()
    }

    @Test
    fun anEmptyFeedKeepsItsCursorAndAnEndedScopeTakesNoLateAnswer() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        val empty = PagedItems(scope, key = BlueskyPost::uri) { page(next = "next") }
        settle()
        assertTrue(empty.state.value.loaded && empty.state.value.items.isEmpty())
        assertEquals("next", empty.state.value.next, "the next page can still be asked for")

        val late = CompletableDeferred<Page<BlueskyPost>>()
        val ending = PagedItems(scope, key = BlueskyPost::uri) { late.await() }
        settle()
        scope.cancel()
        late.complete(page("late"))
        settle()
        assertTrue(texts(ending).isEmpty())
    }

    private fun feedPage(vararg ids: String, cursor: String? = null) =
        ok("""{"feed":[${ids.joinToString(",") { """{"post":${postView(it)}}""" }}]${cursor?.let { ""","cursor":"$it"""" } ?: ""}}""")

    private fun catalog(vararg names: String, cursor: String? = null) = ok(
        """{"feeds":[${names.joinToString(",") { """{"uri":"at://did:plc:a/app.bsky.feed.generator/$it","displayName":"$it","description":"Description $it","creator":{"handle":"curator.test"}}""" }}]""" +
            (cursor?.let { ""","cursor":"$it"""" } ?: "") + "}",
    )

    private fun lists(vararg names: String, cursor: String? = null) = ok(
        """{"lists":[${names.joinToString(",") { """{"uri":"at://did:plc:a/app.bsky.graph.list/$it","name":"$it"}""" }}]""" +
            (cursor?.let { ""","cursor":"$it"""" } ?: "") + "}",
    )

    private fun method(url: HttpUrl) = url.encodedPath.substringAfterLast('/')

    private suspend fun moveTo(app: BlueskyApp, server: String) {
        app.prefs.set(BlueskyPrefs.instance, server)
        eventually { app.prefs.appView.value == server }
    }

    @Test
    fun aFailedCatalogNeverKeepsDiscoverFromReadingAndCanBeTriedAgain() = runBlocking {
        var fail = true
        val host = FakeHost { url -> if (method(url) == "app.bsky.feed.getFeed") feedPage("ready") else if (fail) XResponse(500, "fail") else catalog("science") }
        val app = BlueskyApp(host)
        app.feeds.ensureLoaded()
        val discover = app.feeds.pages(app.prefs.appView.value, Bluesky.DISCOVER_FEED_URI)
        eventually { discover.state.value.loaded && app.feeds.state.value.catalog.problem != null }
        assertEquals(listOf("ready"), texts(discover))
        fail = false
        app.feeds.searchCatalog("")
        eventually { app.feeds.state.value.catalog.loaded }
        assertEquals(listOf("science"), app.feeds.state.value.catalog.items.map { it.displayName })
        assertNull(app.feeds.state.value.catalog.problem)
        app.close()
    }

    @Test
    fun theFeedLastOpenedIsOpenAgainWithoutWaitingForTheCatalog() = runBlocking {
        val catalogAnswer = CompletableDeferred<XResponse>()
        val host = FakeHost { url -> if (method(url) == "app.bsky.feed.getFeed") feedPage("selected") else catalogAnswer.await() }
        val first = BlueskyApp(host)
        first.feeds.open("at://did:plc:a/app.bsky.feed.generator/a", "My feed")
        eventually { first.prefs.selection(first.prefs.appView.value, lists = false) != null }
        first.close()

        val app = BlueskyApp(host)
        app.feeds.ensureLoaded()
        assertEquals(BlueskySelection("at://did:plc:a/app.bsky.feed.generator/a", "My feed"), app.feeds.state.value.selected)
        assertTrue(app.feeds.state.value.catalog.loading)
        val pages = app.feeds.pages(app.prefs.appView.value, app.feeds.state.value.selected.uri)
        eventually { pages.state.value.loaded }
        assertEquals(listOf("selected"), texts(pages))
        catalogAnswer.complete(catalog())
        app.close()
    }

    @Test
    fun aLateCatalogSearchIsDroppedAndAPinStays() = runBlocking {
        val old = CompletableDeferred<XResponse>()
        val host = FakeHost { url -> if (url.queryParameter("query") == "old") old.await() else catalog("new") }
        val app = BlueskyApp(host)
        app.feeds.searchCatalog("old")
        app.feeds.searchCatalog("new")
        app.feeds.pin(BlueskyFeedGenerator("at://did:plc:a/app.bsky.feed.generator/a", "Pinned"))
        eventually { app.feeds.state.value.catalog.loaded && app.prefs.pinnedFeeds.value.isNotEmpty() }
        old.complete(catalog("old"))
        eventually { host.asked.size == 2 }
        Thread.sleep(50)
        assertEquals("new", app.feeds.state.value.query)
        assertEquals(listOf("new"), app.feeds.state.value.catalog.items.map { it.displayName })
        assertEquals(listOf("at://did:plc:a/app.bsky.feed.generator/a"), app.prefs.pinnedFeeds.value.map { it.uri })
        app.close()
    }

    @Test
    fun aCatalogCursorThatComesRoundAgainEndsItAndRepeatsAreDropped() = runBlocking {
        val host = FakeHost { url -> if (url.queryParameter("cursor") == null) catalog("a", cursor = "next") else catalog("a", "b", cursor = "next") }
        val app = BlueskyApp(host)
        app.feeds.searchCatalog("")
        eventually { app.feeds.state.value.catalog.loaded }
        app.feeds.searchCatalog("", more = true)
        eventually { app.feeds.state.value.catalog.items.size == 2 && !app.feeds.state.value.catalog.loading }
        assertEquals(listOf("a", "b"), app.feeds.state.value.catalog.items.map { it.displayName })
        assertNull(app.feeds.state.value.catalog.next)
        app.close()
    }

    @Test
    fun afterAnotherAppViewTheCatalogAndTheListsStartAgainWithoutTheOldCursor() = runBlocking {
        val host = FakeHost { url -> if (method(url) == "app.bsky.graph.getLists") lists(url.host, cursor = "next") else catalog(url.host, cursor = "next") }
        val app = BlueskyApp(host)
        moveTo(app, "https://one.test")
        app.feeds.searchCatalog("science")
        app.lists.lookUp("reader.test")
        eventually { app.feeds.state.value.catalog.loaded && app.lists.state.value.lists.loaded }
        moveTo(app, "https://two.test")
        app.feeds.searchCatalog("science", more = true)
        app.lists.lookUp("reader.test", more = true)
        eventually { app.feeds.state.value.catalog.items.map { it.displayName } == listOf("two.test") && app.lists.state.value.lists.items.map { it.name } == listOf("two.test") }
        assertTrue(host.asked.filter { it.host == "two.test" }.all { it.queryParameter("cursor") == null })
        app.close()
    }

    @Test
    fun theAccountLookedUpIsListedAgainOnAnotherAppView() = runBlocking {
        val host = FakeHost { url -> lists(url.host) }
        val app = BlueskyApp(host)
        moveTo(app, "https://one.test")
        app.lists.ensureLoaded()
        app.lists.lookUp("reader.test")
        eventually { app.lists.state.value.lists.loaded }
        assertNull(app.lists.state.value.selected)
        moveTo(app, "https://two.test")
        app.lists.ensureLoaded()
        eventually { app.lists.state.value.lists.items.map { it.name } == listOf("two.test") }
        assertEquals("reader.test", app.lists.state.value.actor)
        assertEquals("reader.test", host.asked.last().queryParameter("actor"))
        app.close()
    }

    @Test
    fun aStaleLookUpIsDroppedAndTheNewAccountsListsArePaged() = runBlocking {
        val old = CompletableDeferred<XResponse>()
        val host = FakeHost { url ->
            when {
                url.queryParameter("actor") == "old.test" -> old.await()
                url.queryParameter("cursor") != null -> lists("B", cursor = "next")
                else -> lists("A", cursor = "next")
            }
        }
        val app = BlueskyApp(host)
        app.lists.lookUp("old.test")
        app.lists.lookUp("new.test")
        eventually { app.lists.state.value.lists.loaded }
        app.lists.lookUp("new.test", more = true)
        eventually { app.lists.state.value.lists.items.size == 2 && !app.lists.state.value.lists.loading }
        old.complete(lists())
        Thread.sleep(50)
        assertEquals("new.test", app.lists.state.value.actor)
        assertEquals(listOf("A", "B"), app.lists.state.value.lists.items.map { it.name })
        assertNull(app.lists.state.value.lists.next)
        app.close()
    }

    @Test
    fun pinsMadeTogetherKeepThoseBeforeAndTheirOrder() = runBlocking {
        val host = FakeHost { ok("{}") }
        val app = BlueskyApp(host)
        app.prefs.pinnedFeeds { listOf(BlueskyFeedGenerator(Bluesky.DISCOVER_FEED_URI, "Discover")) }
        coroutineScope {
            launch { app.prefs.pinnedFeeds { it + BlueskyFeedGenerator("at://did:plc:a/app.bsky.feed.generator/a", "A") } }
            launch { app.prefs.pinnedFeeds { it + BlueskyFeedGenerator("at://did:plc:a/app.bsky.feed.generator/b", "B") } }
        }
        eventually { app.prefs.pinnedFeeds.value.size == 3 }
        assertEquals(
            listOf(Bluesky.DISCOVER_FEED_URI, "at://did:plc:a/app.bsky.feed.generator/a", "at://did:plc:a/app.bsky.feed.generator/b"),
            app.prefs.pinnedFeeds.value.map { it.uri },
        )
        app.close()
    }

    @Test
    fun enteringAgainReadsNothingAgainAndAPinIsKeptUntilUnpinned() = runBlocking {
        val host = FakeHost { url -> if (method(url) == "app.bsky.feed.getFeed") feedPage("Algo") else catalog("science") }
        val app = BlueskyApp(host)
        app.feeds.ensureLoaded()
        val discover = app.feeds.pages(app.prefs.appView.value, Bluesky.DISCOVER_FEED_URI)
        eventually { discover.state.value.loaded && app.feeds.state.value.catalog.loaded }
        app.feeds.ensureLoaded()
        assertSame(discover, app.feeds.pages(app.prefs.appView.value, Bluesky.DISCOVER_FEED_URI))
        Thread.sleep(50)
        assertEquals(listOf("app.bsky.unspecced.getPopularFeedGenerators", "app.bsky.feed.getFeed"), host.asked.map(::method).sorted().reversed())
        app.feeds.ensureLoaded(force = true)
        eventually { host.asked.count { method(it) == "app.bsky.unspecced.getPopularFeedGenerators" } == 2 }

        val cats = BlueskyFeedGenerator("at://did:plc:a/app.bsky.feed.generator/cats", "Cats")
        app.feeds.pin(cats)
        app.feeds.pin(cats)
        eventually { app.prefs.pinnedFeeds.value == listOf(cats) }
        assertTrue(host.values.value!![BlueskyPrefs.pinnedFeeds].contains("generator/cats"), "kept in the settings")
        app.feeds.unpin(cats.uri)
        eventually { app.prefs.pinnedFeeds.value.isEmpty() }
        app.close()
    }
}
