package com.aimdi.xtanative.networks.websources

import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.Page
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A read a source may not make, refused before or while it is made: an
 * address that is not HTTPS or not on a site the source lists (a redirect
 * included, and a fourth redirect in a row), or a page larger than a source
 * may read. A site's own refusal is a [NetworkRequestException]; no answer,
 * or none in time, an [IOException].
 */
class WebSourceLimitException(val kind: Kind, val url: String) : Exception("${kind.name.lowercase()}: $url") {
    enum class Kind { BLOCKED, TOO_LARGE }
}

/**
 * The only network code of web sources: GET requests, over HTTPS, to the
 * sites a source lists, within the limits of docs/web-sources.md. No
 * cookies, no accounts, sent as `XTA web sources`; redirects are followed by
 * hand, at most three, each to a listed site; one request at a time per
 * source, a second apart. A request that takes over 20 seconds fails as one
 * with no answer (an [IOException]); OkHttp's blocking call ends on its own
 * timeout, so give [http] a client whose call timeout is 20 seconds for the
 * connection to be dropped then too.
 */
class WebSourceClient(
    private val http: XHttp,
    private val spacingMillis: Long = REQUEST_SPACING_MILLIS,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private class Turn {
        val mutex = Mutex()
        var readyAt = 0L
    }

    private val turns = ConcurrentHashMap<String, Turn>()

    /**
     * One page of [list]: its first when [cursor] is null, else the page a
     * previous page's `next` named. Posts already [shown] (by [WebSourceItem.key])
     * are left out, and a page that brings nothing new ends the list, so a
     * site that repeats its last page is not read forever.
     */
    suspend fun list(
        source: WebSourceDefinition,
        list: WebSourceList,
        query: String? = null,
        cursor: String? = null,
        shown: Set<String> = emptySet(),
    ): Page<WebSourceItem> {
        val (number, url) = if (cursor == null) {
            list.firstPage to (WebSourcePages.listUrl(list, query = query) ?: throw WebSourceLimitException(WebSourceLimitException.Kind.BLOCKED, list.url))
        } else {
            pageOf(cursor)
        }
        val body = page(source, url, list.type)
        val read = withContext(Dispatchers.Default) { WebSourcePages.read(source, list, body, url, number, query) }
        val seen = shown.toMutableSet()
        val fresh = read.items.filter { seen.add(it.key) }
        return Page(fresh, next = read.next?.takeIf { fresh.isNotEmpty() }?.let { "${number + 1} $it" })
    }

    /**
     * [item]'s full text as markup: its own `html` field, else the page its
     * source's article rule names. Null when there is neither, or the article
     * address cannot be built or is off the source's sites: the post's link
     * is what opens then.
     */
    suspend fun article(source: WebSourceDefinition, item: WebSourceItem): String? {
        item.html?.let { return it }
        val article = source.article ?: return null
        val url = WebSourcePages.articleUrl(article, item)?.takeIf(source::allows) ?: return null
        val body = page(source, url, article.type)
        return withContext(Dispatchers.Default) { WebSourcePages.articleHtml(article, body) }
    }

    /** The page at [url] for [source], in turn with the source's other requests. */
    suspend fun page(source: WebSourceDefinition, url: HttpUrl, type: WebSourcePageType): String =
        inTurn(source.id) { fetch(url, type, source::allows, MAX_PAGE_BYTES) }

    /**
     * A source file or the published list, from any HTTPS address: where a
     * source is published is the reader's choice, not the source's.
     */
    suspend fun file(url: HttpUrl): String = fetch(url, WebSourcePageType.JSON, ::anyHttps, WebSources.MAX_FILE_BYTES + 1)

    /** The sources XTA publishes. */
    suspend fun catalogue(url: HttpUrl = WebSourceCatalogue.DEFAULT_URL): List<WebSourceListing> = WebSourceCatalogue.parse(file(url), url)

    /** The source file at [url], read and checked; one too large is refused as a file over 64 KB. */
    suspend fun sourceFile(url: HttpUrl): WebSourceFile = try {
        WebSources.read(file(url))
    } catch (large: WebSourceLimitException) {
        if (large.kind != WebSourceLimitException.Kind.TOO_LARGE) throw large
        WebSourceFile.Refused(WebSourceRefusal.TOO_LARGE, "64 KB")
    }

    /**
     * [installed] after its file is read again from its origin
     * ([WebSourceUpdates.updated]); null when nothing changes, a file that
     * cannot be read included.
     */
    suspend fun update(installed: InstalledWebSource): InstalledWebSource? {
        val origin = installed.origin ?: return null
        val fetched = try {
            WebSources.read(file(origin))
        } catch (failure: IOException) {
            WebSourceFile.Refused(WebSourceRefusal.UNREADABLE, failure.javaClass.simpleName)
        } catch (failure: NetworkRequestException) {
            WebSourceFile.Refused(WebSourceRefusal.UNREADABLE, "${failure.code}")
        } catch (failure: WebSourceLimitException) {
            WebSourceFile.Refused(WebSourceRefusal.UNREADABLE, failure.kind.name.lowercase())
        }
        return WebSourceUpdates.updated(installed, fetched)
    }

    /** One request at a time per [key], each starting [spacingMillis] after the last one ended. */
    private suspend fun <T> inTurn(key: String, request: suspend () -> T): T {
        val turn = turns.computeIfAbsent(key) { Turn() }
        return turn.mutex.withLock {
            val wait = turn.readyAt - clock()
            if (wait > 0) delay(wait)
            try {
                request()
            } finally {
                turn.readyAt = clock() + spacingMillis
            }
        }
    }

    private suspend fun fetch(start: HttpUrl, type: WebSourcePageType, allowed: (HttpUrl) -> Boolean, maxBytes: Int): String {
        var url = start
        repeat(MAX_REDIRECTS + 1) {
            if (!allowed(url)) throw WebSourceLimitException(WebSourceLimitException.Kind.BLOCKED, url.toString())
            val response = send(url, type)
            val location = response.header("location")
            if (response.code !in REDIRECTS || location == null) return bounded(response, url, maxBytes)
            url = url.resolve(location) ?: throw WebSourceLimitException(WebSourceLimitException.Kind.BLOCKED, location)
        }
        throw WebSourceLimitException(WebSourceLimitException.Kind.BLOCKED, url.toString())
    }

    private suspend fun send(url: HttpUrl, type: WebSourcePageType): XResponse =
        withTimeoutOrNull(REQUEST_TIMEOUT_MILLIS) { http.get(url, headers(type), followRedirects = false) }
            ?: throw SocketTimeoutException("$url did not answer in ${REQUEST_TIMEOUT_MILLIS / 1000} s")

    private fun bounded(response: XResponse, url: HttpUrl, maxBytes: Int): String {
        if (response.code != 200) throw NetworkRequestException(WebSources.NETWORK, response.code, "$url answered ${response.code}")
        val declared = response.header("content-length")?.toLongOrNull() ?: 0
        if (declared > maxBytes || response.body.toByteArray(Charsets.UTF_8).size > maxBytes) {
            throw WebSourceLimitException(WebSourceLimitException.Kind.TOO_LARGE, url.toString())
        }
        return response.body
    }

    private fun anyHttps(url: HttpUrl): Boolean =
        url.scheme == "https" && url.host.isNotEmpty() && url.username.isEmpty() && url.password.isEmpty()

    /** A cursor this client handed out: the page's number, a space, and its address. */
    private fun pageOf(cursor: String): Pair<Int, HttpUrl> {
        val number = cursor.substringBefore(' ').toIntOrNull()
        val url = cursor.substringAfter(' ', "").toHttpUrlOrNull()
        require(number != null && url != null) { "Not a web source cursor: $cursor" }
        return number to url
    }

    companion object {
        const val USER_AGENT = "XTA web sources"
        const val MAX_PAGE_BYTES = 4 * 1024 * 1024
        const val REQUEST_TIMEOUT_MILLIS = 20_000L
        const val REQUEST_SPACING_MILLIS = 1_000L
        const val MAX_REDIRECTS = 3

        private val REDIRECTS = setOf(301, 302, 303, 307, 308)

        private fun headers(type: WebSourcePageType): Map<String, String> = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to when (type) {
                WebSourcePageType.JSON -> "application/json"
                WebSourcePageType.HTML -> "text/html,application/xhtml+xml"
                WebSourcePageType.RSS -> "application/rss+xml, application/atom+xml, application/xml, text/xml"
            },
        )
    }
}
