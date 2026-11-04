package com.aimdi.xtanative.plugins.bluesky

import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.bluesky.Bluesky
import com.aimdi.xtanative.networks.bluesky.BlueskyFeedGenerator
import com.aimdi.xtanative.networks.bluesky.BlueskyListInfo
import com.aimdi.xtanative.networks.bluesky.BlueskyPaging
import com.aimdi.xtanative.networks.bluesky.BlueskyPost
import com.aimdi.xtanative.networks.bluesky.BlueskySelection
import com.aimdi.xtanative.networks.bluesky.BlueskyUrls
import com.aimdi.xtanative.ui.plugins.PagedItems
import com.aimdi.xtanative.ui.plugins.problemOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The pages read of each custom feed or list, by AppView and URI, the last
 * sixteen opened (bluesky_source_reader.dart): going back to one shows what
 * was read of it, and a pull reads it again.
 */
internal class SourcePages(private val scope: CoroutineScope, private val read: suspend (uri: String, cursor: String?) -> Page<BlueskyPost>) {
    private val pages = object : LinkedHashMap<String, PagedItems<BlueskyPost>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PagedItems<BlueskyPost>>?) = size > KEPT
    }

    @Synchronized
    fun of(server: String, uri: String): PagedItems<BlueskyPost> =
        pages.getOrPut("$server\n$uri") { PagedItems(scope, key = { it.uri }) { cursor -> read(uri, cursor) } }

    private companion object {
        const val KEPT = 16
    }
}

/** A list of things read page by page (feeds of the catalog, someone's lists), each once, as XTA's stores page them. */
internal data class Listing<T>(
    val items: List<T> = emptyList(),
    val next: String? = null,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val problem: Problem? = null,
    val used: Set<String> = emptySet(),
) {
    val more: Boolean get() = next != null && !loading

    /** [page] after what is listed ([more]) or in its place, each item once by [key]; a cursor that comes round again ends it. */
    fun with(page: Page<T>, cursor: String?, more: Boolean, key: (T) -> String): Listing<T> {
        val used = if (cursor == null) emptySet() else used + cursor
        return Listing(BlueskyPaging.merge(if (more) items else emptyList(), page.items, key), BlueskyPaging.next(page.next, used), loaded = true, used = used)
    }
}

/**
 * Discover's custom feeds (bluesky_feeds_store.dart's BlueskyAlgoStore): the
 * feed open, which XTA remembers per AppView; the feeds pinned (in
 * Bluesky's preferences), those the reader's own handle made, and the
 * popular ones, which the catalog searches. A failed catalog never keeps a
 * pinned feed from being read.
 */
internal class BlueskyFeeds(private val app: BlueskyApp, private val scope: CoroutineScope) {
    data class State(
        val selected: BlueskySelection = DISCOVER,
        val created: List<BlueskyFeedGenerator> = emptyList(),
        val createdProblem: Problem? = null,
        val query: String = "",
        val catalog: Listing<BlueskyFeedGenerator> = Listing(),
    )

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()

    private val pages = SourcePages(scope) { uri, cursor -> app.client.feed(uri, cursor = cursor) }
    private var context: String? = null
    private var catalogRequest = 0

    fun pages(server: String, uri: String): PagedItems<BlueskyPost> = pages.of(server, uri)

    /** The feed remembered on this AppView, the catalog's first page and the reader's own feeds, once per AppView and handle. */
    fun ensureLoaded(force: Boolean = false) {
        val server = app.prefs.appView.value
        val handle = app.prefs.handle.value
        val now = "$server\n$handle"
        if (!force && context == now) return
        val serverChanged = context?.substringBefore('\n') != server
        context = now
        if (serverChanged) mutable.update { State(selected = app.prefs.selection(server, lists = false) ?: DISCOVER) }
        searchCatalog(mutable.value.query)
        loadCreated(handle)
    }

    /** The feeds offered in Discover's row, after Discover itself: pinned, the reader's own, popular, each once. */
    fun offered(pinned: List<BlueskyFeedGenerator>): List<BlueskyFeedGenerator> =
        (pinned + state.value.created + state.value.catalog.items).filter { it.uri.isNotEmpty() && it.uri != Bluesky.DISCOVER_FEED_URI }.distinctBy { it.uri }

    /** What is known of the feed open: its name, maker and description, when it is among those offered. */
    fun described(pinned: List<BlueskyFeedGenerator>): BlueskyFeedGenerator? =
        (pinned + state.value.created + state.value.catalog.items).firstOrNull { it.uri == state.value.selected.uri }

    fun open(uri: String, name: String) {
        val selection = BlueskySelection(uri, name)
        mutable.update { it.copy(selected = selection) }
        app.launch { app.prefs.select(app.prefs.appView.value, lists = false, selection) }
    }

    /** What "Open a feed" was given, a feed's link or `at://` URI, opened; the reason it was refused, else null. */
    suspend fun openTyped(typed: String, invalid: String): String? {
        val ref = BlueskyUrls.feedRef(typed) ?: return invalid
        val uri = app.client.resolveFeedUri(ref)
        val known = (app.prefs.pinnedFeeds.value + state.value.created + state.value.catalog.items).firstOrNull { it.uri == uri }
        open(uri, known?.displayName ?: BlueskyUrls.rkeyOf(uri) ?: uri)
        return null
    }

    /** The catalog's feeds for [query] (the popular ones when it is empty), from the first page, or the next. */
    fun searchCatalog(query: String, more: Boolean = false) {
        val text = query.trim()
        val before = mutable.value.catalog
        if (more && !before.more) return
        val request = ++catalogRequest
        val cursor = if (more) before.next else null
        mutable.update { it.copy(query = text, catalog = (if (more || text == it.query) before else Listing()).copy(loading = true, problem = null)) }
        scope.launch {
            try {
                val page = app.client.popularFeedGenerators(cursor = cursor, query = text)
                if (request == catalogRequest) mutable.update { it.copy(catalog = it.catalog.with(page, cursor, more) { feed -> feed.uri }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (request == catalogRequest) mutable.update { it.copy(catalog = it.catalog.copy(loading = false, problem = problemOf(e))) }
            }
        }
    }

    private fun loadCreated(handle: String) {
        scope.launch {
            try {
                val feeds = if (handle.isEmpty()) emptyList() else app.client.actorFeeds(handle).items
                mutable.update { it.copy(created = feeds, createdProblem = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(createdProblem = problemOf(e)) }
            }
        }
    }

    fun pin(feed: BlueskyFeedGenerator) = app.launch { app.prefs.pinnedFeeds { pinned -> if (pinned.any { it.uri == feed.uri }) pinned else pinned + feed } }

    fun unpin(uri: String) = app.launch { app.prefs.pinnedFeeds { pinned -> pinned.filterNot { it.uri == uri } } }

    companion object {
        /** Bluesky's Discover feed, open until the reader opens another; its name is said in the reader's language. */
        val DISCOVER = BlueskySelection(Bluesky.DISCOVER_FEED_URI, "")
    }
}

/**
 * The Lists tab's lists (bluesky_feeds_store.dart's BlueskyListsStore): the
 * list open, remembered per AppView, else the first pinned; the lists
 * pinned, and those of the reader's handle, or of the account a look-up named.
 */
internal class BlueskyLists(private val app: BlueskyApp, private val scope: CoroutineScope) {
    data class State(val selected: BlueskySelection? = null, val actor: String = "", val lists: Listing<BlueskyListInfo> = Listing())

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()

    private val pages = SourcePages(scope) { uri, cursor -> app.client.listFeed(uri, cursor = cursor) }
    private var context: String? = null
    private var request = 0

    fun pages(server: String, uri: String): PagedItems<BlueskyPost> = pages.of(server, uri)

    /** The list remembered here and the handle's lists, once per AppView and handle. */
    fun ensureLoaded(force: Boolean = false) {
        val server = app.prefs.appView.value
        val handle = app.prefs.handle.value
        val now = "$server\n$handle"
        if (!force && context == now) return
        context = now
        val remembered = app.prefs.selection(server, lists = true)
        val first = app.prefs.pinnedLists.value.firstOrNull()?.let { BlueskySelection(it.uri, it.name) }
        mutable.update { State(selected = remembered ?: it.selected ?: first, actor = handle) }
        if (handle.isNotEmpty()) lookUp(handle)
    }

    /** The lists offered in the row: pinned, then those listed, each once. */
    fun offered(pinned: List<BlueskyListInfo>): List<BlueskyListInfo> = (pinned + state.value.lists.items).filter { it.uri.isNotEmpty() }.distinctBy { it.uri }

    fun open(uri: String, name: String) {
        val selection = BlueskySelection(uri, name)
        mutable.update { it.copy(selected = selection) }
        app.launch { app.prefs.select(app.prefs.appView.value, lists = true, selection) }
    }

    /** The lists [actor] made, from the first page or the next. */
    fun lookUp(actor: String, more: Boolean = false) {
        val before = mutable.value
        if (actor.isBlank() || (more && !before.lists.more)) return
        val turn = ++request
        val cursor = if (more) before.lists.next else null
        mutable.update { it.copy(actor = actor, lists = (if (more || actor == it.actor) it.lists else Listing()).copy(loading = true, problem = null)) }
        scope.launch {
            try {
                val page = app.client.lists(actor, cursor = cursor)
                if (turn == request) mutable.update { it.copy(lists = it.lists.with(page, cursor, more) { list -> list.uri }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (turn == request) mutable.update { it.copy(lists = it.lists.copy(loading = false, problem = problemOf(e))) }
            }
        }
    }

    /**
     * What "Look up lists" was given: a list's link opens that list, an
     * account's handle lists its lists; the reason it was refused, else null.
     */
    suspend fun lookUpTyped(typed: String, invalid: String): String? {
        BlueskyUrls.listRef(typed)?.let { ref ->
            val uri = app.client.resolveListUri(ref)
            open(uri, BlueskyUrls.rkeyOf(uri) ?: uri)
            return null
        }
        val handle = BlueskyUrls.handle(typed) ?: return invalid
        lookUp(handle)
        return null
    }

    fun pin(list: BlueskyListInfo) = app.launch { app.prefs.pinnedLists { pinned -> if (pinned.any { it.uri == list.uri }) pinned else pinned + list } }

    fun unpin(uri: String) = app.launch { app.prefs.pinnedLists { pinned -> pinned.filterNot { it.uri == uri } } }
}
