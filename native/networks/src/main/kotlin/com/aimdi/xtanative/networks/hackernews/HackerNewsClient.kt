package com.aimdi.xtanative.networks.hackernews

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl

/**
 * Hacker News read as a guest, as XTA's plugin reads it: Algolia's search
 * for the lists, search, submissions and threads; Firebase for Best and for
 * users. No login, vote, comment or submission.
 *
 * Lists come 30 stories a page; a page's `next` is the next page's number,
 * to pass back as `cursor`.
 */
class HackerNewsClient(private val http: XHttp) {
    /** One page of [feed]: the first when [cursor] is null. */
    suspend fun feed(feed: HnFeed, cursor: String? = null): Page<HnStory> {
        val page = pageOf(cursor)
        return when (feed) {
            HnFeed.BEST -> best(page)
            HnFeed.TOP -> algolia(SEARCH, "front_page", page)
            HnFeed.NEWEST -> algolia(SEARCH_BY_DATE, "story", page)
            HnFeed.ASK -> algolia(SEARCH, "ask_hn", page)
            HnFeed.SHOW -> algolia(SEARCH, "show_hn", page)
            HnFeed.JOBS -> algolia(SEARCH_BY_DATE, "job", page)
        }
    }

    /** Stories matching [query], most relevant first. */
    suspend fun search(query: String, cursor: String? = null): Page<HnStory> =
        algolia(SEARCH, "story", pageOf(cursor), query)

    /** The stories [author] submitted, newest first: what following an HN account reads. */
    suspend fun submissions(author: String, cursor: String? = null): Page<HnStory> =
        algolia(SEARCH_BY_DATE, "story,author_${author.trim()}", pageOf(cursor))

    /** Story [id] with its comment tree. */
    suspend fun thread(id: Long): HnThread {
        val body = read(url(ALGOLIA).addPathSegments("api/v1/items").addPathSegment("$id").build())
        return parsing { HnParser.thread(J.parse(body)) }
    }

    /** The account [id]. Firebase answers an unknown one with `null`: that is a 404 here. */
    suspend fun user(id: String): HnUser {
        val body = read(url(FIREBASE).addPathSegments("v0/user").addPathSegment("${id.trim()}.json").build())
        return parsing { HnParser.user(J.parse(body), id) }
            ?: throw NetworkRequestException(NETWORK, 404, "No Hacker News user $id")
    }

    /** Best has no search: Firebase's ranked ids, then each story of the page, read together. */
    private suspend fun best(page: Int): Page<HnStory> {
        val ids = HnParser.ids(J.parse(read(url(FIREBASE).addPathSegments("v0/beststories.json").build())))
        val start = page * PAGE_SIZE
        if (start >= ids.size) return Page(emptyList(), next = null)
        val stories = coroutineScope { ids.drop(start).take(PAGE_SIZE).map { id -> async { firebaseStory(id) } }.awaitAll() }
        return Page(stories.filterNotNull(), next = if (start + PAGE_SIZE < ids.size) "${page + 1}" else null)
    }

    private suspend fun firebaseStory(id: Long): HnStory? {
        val body = read(url(FIREBASE).addPathSegments("v0/item").addPathSegment("$id.json").build())
        return parsing { HnParser.firebaseStory(J.parse(body), id) }
    }

    private suspend fun algolia(path: String, tags: String, page: Int, query: String? = null): Page<HnStory> {
        val address = url(ALGOLIA).addPathSegments(path)
            .addQueryParameter("tags", tags)
            .addQueryParameter("page", "$page")
            .addQueryParameter("hitsPerPage", "$PAGE_SIZE")
            .apply { query?.trim()?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("query", it) } }
            .build()
        val body = read(address)
        return parsing { HnParser.searchPage(J.parse(body), page) }
    }

    /** The body of [url]: anything but 200 is a [NetworkRequestException], as XTA treats it. */
    private suspend fun read(url: HttpUrl): String {
        val response = http.get(url, HEADERS, followRedirects = true)
        if (response.code != 200) throw NetworkRequestException(NETWORK, response.code, "Hacker News answered ${response.code}")
        return response.body
    }

    private fun url(host: String) = HttpUrl.Builder().scheme("https").host(host)

    private fun pageOf(cursor: String?): Int = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0

    /** Threads can hold thousands of comments: they are read away from the caller's thread, which may be the UI's. */
    private suspend fun <T> parsing(block: () -> T): T = withContext(Dispatchers.Default) { block() }

    companion object {
        /** How the network names itself in a [NetworkRequestException]. */
        const val NETWORK = "hackernews"
        const val ALGOLIA = "hn.algolia.com"
        const val FIREBASE = "hacker-news.firebaseio.com"
        const val PAGE_SIZE = 30
        const val USER_AGENT = "XTA Hacker News plugin"

        private const val SEARCH = "api/v1/search"
        private const val SEARCH_BY_DATE = "api/v1/search_by_date"
        private val HEADERS = mapOf("User-Agent" to USER_AGENT, "Accept" to "application/json")
    }
}
