package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.Page
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** A publication's posts, a page at a time: [SubstackClient.posts], or a stand-in in tests. */
fun interface SubstackPostReads {
    suspend fun posts(publication: SubstackPublication, limit: Int, offset: Int): List<SubstackPost>
}

/** The client's reads of a publication's posts. */
fun SubstackClient.postReads(): SubstackPostReads = SubstackPostReads { publication, limit, offset -> posts(publication, limit, offset).items }

/** One followed publication's first page as it last answered (epoch milliseconds), kept across a restart. */
data class SubstackRememberedPage(val answeredAt: Long, val posts: List<SubstackPost>)

/**
 * The feed as it last stood, as XTA's SubstackFeedMemory writes it: each
 * publication's first page by its feed key, with when it answered.
 */
object SubstackFeedMemory {
    fun encode(pages: Map<String, SubstackRememberedPage>): String = buildJsonObject {
        put("v", 1)
        put(
            "pages",
            JsonObject(
                pages.mapValues { (_, page) ->
                    buildJsonObject {
                        put("answeredAt", Instant.ofEpochMilli(page.answeredAt).toString())
                        put("posts", JsonArray(page.posts.take(SubstackFeed.PAGE_SIZE).map(SubstackPostJson::encode)))
                    }
                },
            ),
        )
    }.toString()

    /** What [text] remembers; nothing for a damaged or foreign text. */
    fun decode(text: String?): Map<String, SubstackRememberedPage> {
        val root = J.parse(text.orEmpty())
        if (root["v"].int != 1) return emptyMap()
        val pages = root["pages"]
        return pages.keys.mapNotNull { key ->
            val page = pages[key]
            val at = SubstackText.instant(page["answeredAt"].string)?.toEpochMilli() ?: return@mapNotNull null
            val posts = page["posts"].list.filter { it.raw is JsonObject }.map(SubstackPostJson::decode).filter { it.id.isNotEmpty() }
            key to SubstackRememberedPage(at, posts)
        }.toMap()
    }
}

/** A post's key in the feed: its publication's address (host case and trailing slashes aside) and its id. */
fun SubstackPost.feedKey(): String = "${sourceUrl(publicationBaseUrl)}\n$id"

private fun sourceUrl(value: String): String {
    val url = value.trim().toHttpUrlOrNull() ?: return value.trim()
    val query = url.encodedQuery?.let { "?$it" }.orEmpty()
    return "${SubstackHosts.origin(url)}${url.encodedPath.trimEnd('/')}$query"
}

private fun SubstackPublication.sourceKey(): String = "$id\n${sourceUrl(baseUrl)}"

/** [read] as a [Result]; a cancelled coroutine stays cancelled. */
private inline fun <T> attempt(read: () -> T): Result<T> = try {
    Result.success(read())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/**
 * The home feed: every followed publication's posts merged, newest first, as
 * XTA's SubstackFeedStore reads them (BUG-05 and BUG-02 fixed). A refresh reads
 * the first page of every publication, [concurrency] at a time; loading more
 * reads the next page of at most [batch] publications, those never tried or
 * tried longest ago first, so one that keeps failing cannot starve the rest. A
 * failed page keeps what its publication showed and is asked again at the same
 * offset. A publication stops paging when a page is short or brings no post
 * it had not shown.
 */
class SubstackFeed(
    private val reads: SubstackPostReads,
    publications: List<SubstackPublication>,
    private val pageSize: Int = PAGE_SIZE,
    private val concurrency: Int = CONCURRENCY,
    private val batch: Int = BATCH,
    /** How long a publication's first page counts as fresh, so a refresh that is not forced skips it. */
    private val freshMillis: Long = FRESH_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * Where the feed stands. [posts] is every loaded post, one copy each, newest
     * first (ties by [feedKey], undated last); [SubstackFilters.matches] filters it.
     */
    data class Snapshot(val posts: List<SubstackPost>, val canLoadMore: Boolean, val failures: List<Throwable>, val pending: Int) {
        val failedCount: Int get() = failures.size
    }

    private data class Source(
        val posts: List<SubstackPost> = emptyList(),
        val offset: Int = 0,
        val loaded: Boolean = false,
        val hasMore: Boolean = true,
        val error: Throwable? = null,
        val failedOffset: Int? = null,
        val attempt: Int = 0,
        val answeredAt: Long? = null,
    )

    private val lock = Mutex()
    private var followed: List<SubstackPublication> = unique(publications)
    private val sources = LinkedHashMap<String, Source>()
    private var generation = 0
    private var loading = false
    private var attempts = 0

    /** Follows [publications] from now on: what they already showed stays, results for the old set are dropped. */
    suspend fun follow(publications: List<SubstackPublication>) = lock.withLock {
        followed = unique(publications)
        val keys = followed.map { it.sourceKey() }.toSet()
        sources.keys.retainAll(keys)
        generation++
        loading = false
    }

    /** The first page of every publication again; unless [force], one answered within [freshMillis] is skipped. */
    suspend fun refresh(force: Boolean = true): Snapshot = load(more = false, force = force)

    /** The next page of the publications that have more, or whose last page failed. A call while another load runs does nothing. */
    suspend fun more(): Snapshot = load(more = true, force = true)

    /** Where the feed stands now, nothing asked. */
    suspend fun snapshot(): Snapshot = lock.withLock { snapshotLocked() }

    /** Each followed publication's first page as it last answered, for [restore] after a restart; bodies are never kept. */
    suspend fun remembered(): Map<String, SubstackRememberedPage> = lock.withLock {
        followed.mapNotNull { publication ->
            val key = publication.sourceKey()
            val source = sources[key] ?: return@mapNotNull null
            val at = source.answeredAt ?: return@mapNotNull null
            if (source.posts.isEmpty()) null else key to SubstackRememberedPage(at, source.posts.take(pageSize).map { it.copy(bodyHtml = null) })
        }.toMap()
    }

    /**
     * Paints the feed as it last stood ([remembered]) before anything is asked:
     * a publication that answered within [freshMillis] counts as loaded, the
     * others show and are read again. Only before the first read; whether it
     * painted anything.
     */
    suspend fun restore(pages: Map<String, SubstackRememberedPage>): Boolean = lock.withLock {
        if (sources.isNotEmpty()) return@withLock false
        val keys = followed.mapTo(HashSet()) { it.sourceKey() }
        for ((key, page) in pages) {
            if (key !in keys || page.posts.isEmpty()) continue
            val fresh = clock() - page.answeredAt < freshMillis
            sources[key] = Source(posts = page.posts, offset = page.posts.size, loaded = fresh, answeredAt = page.answeredAt.takeIf { fresh })
        }
        sources.isNotEmpty()
    }

    private suspend fun load(more: Boolean, force: Boolean): Snapshot {
        val (request, candidates) = lock.withLock {
            if (more && (loading || !snapshotLocked().canLoadMore)) return@withLock null
            loading = true
            val chosen = candidates(more).filter { more || force || !fresh(it.sourceKey()) }
            ++generation to if (more) chosen.take(batch) else chosen
        } ?: return snapshot()
        val permits = Semaphore(concurrency)
        coroutineScope { candidates.map { publication -> async { permits.withPermit { read(publication, request, more) } } }.awaitAll() }
        return lock.withLock {
            if (request == generation) loading = false
            snapshotLocked()
        }
    }

    private fun candidates(more: Boolean): List<SubstackPublication> = followed
        .filter { publication ->
            val source = sources[publication.sourceKey()]
            !more || source == null || source.error != null || source.hasMore
        }
        .sortedWith(compareBy({ if ((sources[it.sourceKey()]?.attempt ?: 0) == 0) 0 else 1 }, { sources[it.sourceKey()]?.attempt ?: 0 }))

    private suspend fun read(publication: SubstackPublication, request: Int, more: Boolean) {
        val key = publication.sourceKey()
        val (before, offset, turn) = lock.withLock {
            val before = sources[key] ?: Source()
            Triple(before, if (more) before.failedOffset ?: before.offset else 0, ++attempts)
        }
        val result = attempt { reads.posts(publication, pageSize, offset) }
        lock.withLock {
            if (request != generation) return
            sources[key] = result.fold(
                onSuccess = { posts ->
                    val shown = before.posts.map { it.key }.toSet()
                    val progressed = offset == 0 || posts.any { it.key !in shown }
                    Source(
                        posts = merged(if (offset > 0) before.posts + posts else posts),
                        offset = offset + pageSize,
                        loaded = true,
                        hasMore = progressed && posts.size >= pageSize,
                        attempt = turn,
                        answeredAt = if (offset == 0) clock() else before.answeredAt,
                    )
                },
                onFailure = { error -> before.copy(error = error, failedOffset = offset, attempt = turn) },
            )
        }
    }

    private fun fresh(key: String): Boolean {
        val source = sources[key] ?: return false
        val answered = source.answeredAt ?: return false
        return source.loaded && source.error == null && clock() - answered < freshMillis
    }

    private fun snapshotLocked(): Snapshot {
        val pages = followed.mapNotNull { sources[it.sourceKey()] }
        val pending = followed.count { sources[it.sourceKey()]?.loaded != true }
        val posts = merged(pages.flatMap { it.posts }).sortedWith(
            compareByDescending<SubstackPost> { it.publishedAt?.toEpochMilli() ?: Long.MIN_VALUE }.thenBy { it.feedKey() },
        )
        return Snapshot(posts, pending > 0 || pages.any { it.hasMore || it.error != null }, pages.mapNotNull { it.error }, pending)
    }

    private fun merged(posts: List<SubstackPost>): List<SubstackPost> = SubstackPosts.mergeCopies(posts.filter { it.id.isNotEmpty() })

    private fun unique(publications: List<SubstackPublication>): List<SubstackPublication> =
        publications.associateBy { it.sourceKey() }.values.toList()

    companion object {
        /** Posts read per publication and page, as XTA's feed reads them. */
        const val PAGE_SIZE = 8
        const val CONCURRENCY = 3

        /** Publications read per load-more pass; a refresh reads every one. */
        const val BATCH = 24
        const val FRESH_MS = 10 * 60_000L
    }
}

/**
 * A list read by offset, as XTA's publication page reads a publication's
 * archive or its archive search: [pageSize] at a time, one copy of each post.
 * Paging stops at a short page, or at a page that brings nothing new. A failed
 * page keeps what was shown, and [retry] asks for that same page again.
 */
class SubstackArchive(private val read: suspend (limit: Int, offset: Int) -> List<SubstackPost>, private val pageSize: Int = PAGE_SIZE) {
    /** The posts shown, where the next page starts, and how the last read went. */
    data class State(
        val posts: List<SubstackPost> = emptyList(),
        val offset: Int = 0,
        val canLoadMore: Boolean = false,
        val loading: Boolean = false,
        val error: Throwable? = null,
        /** Whether the page that failed was a later one, which [retry] asks again. */
        val failedMore: Boolean = false,
    )

    private val lock = Mutex()
    private var generation = 0

    var state: State = State()
        private set

    /** The first page, replacing what was shown once it answers. */
    suspend fun first(): State = fetch(more = false)

    /** The next page; nothing while a page is loading or when there is no more. */
    suspend fun more(): State {
        if (lock.withLock { state.loading || !state.canLoadMore }) return state
        return fetch(more = true)
    }

    /** The page that failed, again. */
    suspend fun retry(): State {
        if (lock.withLock { state.loading }) return state
        return fetch(more = state.failedMore)
    }

    private suspend fun fetch(more: Boolean): State {
        val (request, prior) = lock.withLock {
            val prior = state
            state = prior.copy(loading = true, error = null, failedMore = false)
            ++generation to prior
        }
        val offset = if (more) prior.offset else 0
        val result = attempt { read(pageSize, offset) }
        return lock.withLock {
            if (request != generation) return@withLock state
            state = result.fold(
                onSuccess = { incoming ->
                    val before = if (more) prior.posts else emptyList()
                    val posts = SubstackPosts.mergeCopies(before + incoming)
                    State(posts, offset + pageSize, canLoadMore = incoming.size >= pageSize && posts.size > before.size)
                },
                onFailure = { error -> prior.copy(loading = false, error = error, failedMore = more) },
            )
            state
        }
    }

    companion object {
        const val PAGE_SIZE = 20

        /** [publication]'s archive, newest first. */
        fun of(client: SubstackClient, publication: SubstackPublication, pageSize: Int = PAGE_SIZE) =
            SubstackArchive({ limit, offset -> client.posts(publication, limit, offset).items }, pageSize)

        /** [publication]'s archive searched for [query]: paged on its own, apart from the archive. */
        fun search(client: SubstackClient, publication: SubstackPublication, query: String, pageSize: Int = PAGE_SIZE) =
            SubstackArchive({ limit, offset -> client.search(publication, query, limit, offset).items }, pageSize)
    }
}

/**
 * Public Notes, as XTA's SubstackNotesStore reads them: each refresh asks the
 * next followed publication's host in turn (substack.com when there is none),
 * and every later page goes back to the host that issued its cursor. A cursor
 * seen before ends the paging; a note seen again replaces itself in place.
 */
class SubstackNotes(
    private val read: suspend (host: String?, cursor: String?) -> Page<SubstackNote>,
    private val hosts: () -> List<String>,
) {
    /** The notes shown, the cursor of the next page (null at the end), and how the last read went. */
    data class State(val notes: List<SubstackNote> = emptyList(), val next: String? = null, val error: Throwable? = null)

    private val lock = Mutex()
    private var generation = 0
    private var hostIndex = 0
    private var host: String? = null
    private var loadedHosts: List<String>? = null
    private val cursors = HashSet<String>()

    var state: State = State()
        private set

    /** The first page from the next host in turn; what was shown stays until it answers. */
    suspend fun refresh(): State = load(more = false)

    /** The next page from the host that gave the cursor; a refresh instead when the followed hosts changed. */
    suspend fun more(): State {
        val changed = lock.withLock {
            if (state.next == null) return state
            loadedHosts != hosts()
        }
        return load(more = !changed)
    }

    private suspend fun load(more: Boolean): State {
        val current = hosts()
        val (request, asked, cursor) = lock.withLock {
            val asked = if (more) host else current.takeIf { it.isNotEmpty() }?.let { it[hostIndex++ % it.size] }
            Triple(++generation, asked, if (more) state.next else null)
        }
        val result = attempt { read(asked, cursor) }
        return lock.withLock {
            if (request != generation) return@withLock state
            state = result.fold(
                onSuccess = { page ->
                    if (!more) cursors.clear()
                    cursor?.let(cursors::add)
                    val next = page.next?.trim()?.takeIf { it.isNotEmpty() && it !in cursors }
                    host = asked
                    loadedHosts = current
                    val notes = LinkedHashMap<String, SubstackNote>()
                    (if (more) state.notes + page.items else page.items).filter { it.id.isNotEmpty() }.forEach { notes[it.id] = it }
                    State(notes.values.toList(), next)
                },
                onFailure = { error -> state.copy(error = error) },
            )
            state
        }
    }

    companion object {
        /** Notes from [client], asking the hosts of [publications] in turn. */
        fun of(client: SubstackClient, publications: () -> List<SubstackPublication>) = SubstackNotes(
            read = { host, cursor -> client.notes(host, cursor) },
            hosts = { publications().map { SubstackHosts.hostOf(it.baseUrl) }.filter { it.isNotEmpty() } },
        )
    }
}

/**
 * A search across the followed publications' archives, as XTA's search sheet
 * runs it: [batch] publications at a time, each paged on its own offset, the
 * results merged newest first. A publication whose page fails keeps its
 * results and is the only one [retryFailed] asks again; one whose page brings
 * nothing new stops.
 */
class SubstackPostSearch(
    private val read: suspend (publication: SubstackPublication, limit: Int, offset: Int) -> List<SubstackPost>,
    publications: List<SubstackPublication>,
    private val pageSize: Int = PAGE_SIZE,
    private val batch: Int = BATCH,
) {
    /** One publication's part of the search: its results, its next offset, and whether its last page failed. */
    data class Slice(
        val publication: SubstackPublication,
        val posts: List<SubstackPost> = emptyList(),
        val offset: Int = 0,
        val hasMore: Boolean = true,
        val error: Throwable? = null,
    )

    /** Every slice's results merged newest first, one copy of each. */
    data class State(val posts: List<SubstackPost> = emptyList(), val slices: List<Slice> = emptyList(), val hasMore: Boolean = false) {
        val failedCount: Int get() = slices.count { it.error != null }
    }

    private val lock = Mutex()
    private val publications = publications.distinctBy { it.id.ifEmpty { it.baseUrl.lowercase() } }

    var state: State = State(slices = this.publications.map { Slice(it) })
        private set

    /** Every publication's first page. */
    suspend fun first(): State = run(lock.withLock { state.slices.indices.toList() }, replace = true)

    /** The next page of each publication that has more, one whose last page failed included. */
    suspend fun more(): State = run(lock.withLock { state.slices.indices.filter { state.slices[it].hasMore } }, replace = false)

    /** The publications whose last page failed, again. */
    suspend fun retryFailed(): State = run(lock.withLock { state.slices.indices.filter { state.slices[it].error != null } }, replace = false)

    private suspend fun run(targets: List<Int>, replace: Boolean): State {
        for (chunk in targets.chunked(batch)) {
            val slices = lock.withLock { state.slices }
            val done = coroutineScope { chunk.map { index -> async { index to search(slices[index], replace) } }.awaitAll() }
            lock.withLock {
                val updated = state.slices.toMutableList()
                done.forEach { (index, slice) -> updated[index] = slice }
                state = State(merged(updated), updated, updated.any { it.hasMore && it.error == null })
            }
        }
        return lock.withLock { state }
    }

    private suspend fun search(before: Slice, replace: Boolean): Slice {
        val offset = if (replace) 0 else before.offset
        return attempt { read(before.publication, pageSize, offset) }.fold(
            onSuccess = { posts ->
                val merged = LinkedHashMap<String, SubstackPost>()
                if (!replace && offset > 0) before.posts.forEach { merged[searchKey(it)] = it }
                val count = merged.size
                posts.forEach { merged[searchKey(it)] = it }
                Slice(before.publication, merged.values.toList(), offset + pageSize, hasMore = merged.size > count)
            },
            onFailure = { error -> before.copy(error = error) },
        )
    }

    private fun merged(slices: List<Slice>): List<SubstackPost> {
        val seen = HashSet<String>()
        return slices.flatMap { it.posts }.filter { seen.add(searchKey(it)) }
            .sortedByDescending { it.publishedAt?.toEpochMilli() ?: 0 }
    }

    private fun searchKey(post: SubstackPost): String = "${post.publicationBaseUrl.lowercase()}\n${post.id.ifEmpty { post.slug }}"

    companion object {
        const val PAGE_SIZE = 20
        const val BATCH = 4

        /** [query] in the archives of [publications] through [client]. */
        fun of(client: SubstackClient, publications: List<SubstackPublication>, query: String) =
            SubstackPostSearch({ publication, limit, offset -> client.search(publication, query, limit, offset).items }, publications)
    }
}

/**
 * Publications found page by page, as XTA's discovery lists them: a name
 * search or a category's leaderboard. A page that adds no publication not
 * already listed ends the paging; a failed page keeps what was listed and is
 * asked again.
 */
class SubstackPublicationSearch(private val read: suspend (page: Int) -> Page<SubstackPublication>) {
    /** The publications listed, one of each, the page to ask next, and how the last read went. */
    data class State(
        val publications: List<SubstackPublication> = emptyList(),
        val hasMore: Boolean = false,
        val nextPage: Int = 0,
        val error: Throwable? = null,
        /** Whether the page that failed was a later one. */
        val retryMore: Boolean = false,
    )

    private val lock = Mutex()
    private var generation = 0

    var state: State = State()
        private set

    /** The first page, replacing what was listed once it answers. */
    suspend fun first(): State = load(more = false)

    /** The next page, or the one that failed. */
    suspend fun more(): State {
        if (lock.withLock { !state.hasMore && !state.retryMore }) return state
        return load(more = true)
    }

    private suspend fun load(more: Boolean): State {
        val (request, before) = lock.withLock { ++generation to state }
        val page = if (more) before.nextPage else 0
        val result = attempt { read(page) }
        return lock.withLock {
            if (request != generation) return@withLock state
            state = result.fold(
                onSuccess = { found ->
                    val seen = HashSet<String>()
                    val items = ((if (more) before.publications else emptyList()) + found.items).filter { seen.add(it.id.ifEmpty { it.baseUrl.lowercase() }) }
                    val added = items.size - if (more) before.publications.size else 0
                    State(items, hasMore = found.next != null && added > 0, nextPage = page + 1)
                },
                onFailure = { error -> before.copy(error = error, retryMore = more) },
            )
            state
        }
    }

    companion object {
        /** Publications named by [query], or the one it addresses. */
        fun of(client: SubstackClient, query: String) = SubstackPublicationSearch { page -> client.findPublications(query, page) }

        /** A category's leaderboard. */
        fun category(client: SubstackClient, categoryId: Long, tier: String = "all") =
            SubstackPublicationSearch { page -> client.categoryPublications(categoryId, tier, page) }
    }
}
