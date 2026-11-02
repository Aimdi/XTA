package com.aimdi.xtanative.networks.tiktok

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.Page
import java.io.IOException
import java.time.Instant
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Reads TikTok without an account, as XTA's guest client does
 * (tiktok_client.dart): profile and video pages carry their data as JSON
 * for TikTok's web app, the creator video list answers unsigned requests
 * from a made-up web device, and search is put together from TikTok's
 * suggestions, its discover page and profile pages, as its own search
 * wants a signature a guest cannot make. Read-only: nothing is liked,
 * followed or commented on TikTok.
 *
 * A refusal is a [NetworkRequestException] named [NETWORK]: [NOT_FOUND] when
 * there is no such account or video, [RATE_LIMITED] while TikTok is limiting,
 * [UNREADABLE] for an answer that is not the JSON asked for or a video list
 * TikTok refused by status, and any other status as TikTok sent it. No
 * connection stays the IOException.
 *
 * @param session the guest session kept from an earlier start; empty starts a new guest.
 * @param onSession hears the session whenever its cookies or device id change, to keep it for the next start.
 * @param repairs the aid and user agent in force, which XTA's published repairs can replace.
 */
class TikTokClient(
    private val http: XHttp,
    session: TikTokSession = TikTokSession(),
    private val onSession: (TikTokSession) -> Unit = {},
    private val repairs: () -> TikTokRepairs = { TikTokRepairs() },
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var jar: Map<String, String> = keptCookies(session.cookies)

    @Volatile
    private var device: String = session.deviceId.trim()

    /** Discover's people and when they were read: kept for [DISCOVER_KEPT_MILLIS]. */
    @Volatile
    private var discovered: Pair<Long, List<TikTokSearchUser>>? = null

    /** The cookies TikTok set, by name: what an embedded player is given. */
    val cookies: Map<String, String> get() = jar

    /** The cookies TikTok set, as a Cookie header. */
    val cookieHeader: String get() = headerOf(jar)

    /** The browser every request reads as: an embedded player should be the same one. */
    val userAgent: String get() = repairs().userAgent

    /** The headers TikTok's video files want: the browser, tiktok.com as referer and origin, and the guest cookies. */
    val playbackHeaders: Map<String, String>
        get() = buildMap {
            put("User-Agent", userAgent)
            put("Referer", "$WEB/")
            put("Origin", WEB)
            cookieHeader.takeIf { it.isNotEmpty() }?.let { put("Cookie", it) }
        }

    /** The device id the web API is asked with: the kept one, else one made up once and kept. */
    val deviceId: String
        get() = synchronized(this) {
            device.ifEmpty { randomDeviceId(random).also { device = it; changed() } }
        }

    /** Forgets the guest session, cookies and device id: the next read starts as a new guest. */
    fun clearSession() {
        synchronized(this) {
            jar = emptyMap()
            device = ""
            changed()
        }
    }

    /** The account [handle] names ("@name", "name" or a link to it), from its profile page. A private account is returned too. */
    suspend fun profile(handle: String): TikTokProfile {
        val key = TikTokParser.handle(handle) ?: throw refusal(NOT_FOUND, "Not a TikTok username: $handle")
        val url = WEB_URL.newBuilder().addPathSegment("@$key").build()
        return page(get(url, html = true), TikTokParser::profileHtml) ?: throw refusal(NOT_FOUND, "No TikTok account @$key")
    }

    /**
     * The account [secUid]'s videos, newest first, [count] a page, from the
     * unsigned creator list; [cursor] is the page's `next`. An empty page
     * that says there is more is TikTok refusing the device id, so the list
     * is asked once more with a new one.
     */
    suspend fun creatorItems(secUid: String, cursor: String? = null, count: Int = 15): Page<TikTokPost> {
        val first = itemList(secUid, cursor, count)
        if (first.statusCode == NO_SUCH_ACCOUNT) throw refusal(NOT_FOUND, "No TikTok account $secUid")
        val list = if (first.posts.isEmpty() && first.hasMore) {
            rotateDeviceId()
            itemList(secUid, cursor, count)
        } else {
            first
        }
        return checked(list, secUid).page
    }

    /** One video, from its page; [handle] fills the page's address and may be left out. */
    suspend fun video(id: String, handle: String? = null): TikTokPost {
        val url = WEB_URL.newBuilder().addPathSegment("@${handle?.ifEmpty { null } ?: "_"}")
            .addPathSegment("video").addPathSegment(id)
            .build()
        return page(get(url, html = true), TikTokParser::videoHtml) ?: throw refusal(NOT_FOUND, "No TikTok video $id")
    }

    /** The searches TikTok suggests as [keyword] is typed. */
    suspend fun suggestQueries(keyword: String): List<String> {
        val q = keyword.trim()
        if (q.isEmpty()) return emptyList()
        val url = api("api/search/general/preview", fromPage = "search", "keyword" to q)
        // XTA wrote the words into the referer as typed; a header cannot carry
        // every character, so they go in encoded.
        val referer = WEB_URL.newBuilder().addPathSegment("search").addQueryParameter("q", q).build()
        return json(get(url, referer = referer.toString()), url, TikTokParser::suggestList)
    }

    /** The searches trending on TikTok now. */
    suspend fun trendingQueries(): List<String> {
        val url = api("api/search/suggest/guide", fromPage = "search")
        return json(get(url, referer = "$WEB/search"), url, TikTokParser::suggestList)
    }

    /** The people on TikTok's discover page, read again after ten minutes. */
    suspend fun discoverUsers(): List<TikTokSearchUser> {
        discovered?.let { (at, users) -> if (clock() - at < DISCOVER_KEPT_MILLIS) return users }
        val url = WEB_URL.newBuilder().addPathSegments("node/share/discover").build()
        val users = json(get(url, referer = "$WEB/"), url, TikTokParser::discoverUsers)
        discovered = clock() to users
        return users
    }

    /**
     * A guest search for [query]: people found by guessing their usernames
     * from the words and TikTok's suggestions, plus discover's people who
     * match; then the newest videos of the first two. A part that fails is
     * left empty; the search itself does not fail.
     */
    suspend fun search(query: String): TikTokSearchPage {
        val q = query.trim()
        if (q.isEmpty()) return TikTokSearchPage()
        val suggestions = orNull { suggestQueries(q) }.orEmpty()
        val users = people(q, suggestions)
        return TikTokSearchPage(users = users, posts = videosOf(users), suggestions = suggestions)
    }

    private suspend fun people(query: String, suggestions: List<String>): List<TikTokSearchUser> {
        val discover = orNull { discoverUsers() }.orEmpty()
        val handles = TikTokSearch.handleCandidates(query, suggestions).take(PROFILES_PER_SEARCH)
        val found = coroutineScope { handles.map { async { orNull { profile(it) } } }.awaitAll() }
        return TikTokSearch.people(query, found.filterNotNull().map(TikTokSearchUser::of), discover)
    }

    private suspend fun videosOf(users: List<TikTokSearchUser>): List<TikTokPost> =
        users.mapNotNull { it.secUid?.takeIf(String::isNotEmpty) }
            .take(ACCOUNTS_WITH_VIDEOS)
            .flatMap { orNull { creatorItems(it, count = 8).items }.orEmpty() }
            .sortedByDescending { it.createdAt ?: Instant.EPOCH }
            .distinctBy { it.id }
            .take(VIDEOS_PER_SEARCH)

    private suspend fun itemList(secUid: String, cursor: String?, count: Int): TikTokItemList {
        val url = api(
            "api/creator/item_list",
            fromPage = "user",
            "count" to "$count",
            "cursor" to (cursor ?: "0"),
            "secUid" to secUid,
            "type" to "1",
        )
        return json(get(url, referer = "$WEB/"), url, TikTokParser::itemList)
    }

    private fun checked(list: TikTokItemList, secUid: String): TikTokItemList = when {
        list.statusCode == NO_SUCH_ACCOUNT -> throw refusal(NOT_FOUND, "No TikTok account $secUid")
        list.statusCode != null && list.statusCode != 0 && list.posts.isEmpty() ->
            throw refusal(UNREADABLE, "TikTok refused the video list with status ${list.statusCode}")
        else -> list
    }

    /** An address on TikTok's web API, with the query a web browser on Windows sends. */
    private fun api(path: String, fromPage: String, vararg extra: Pair<String, String>): HttpUrl =
        WEB_URL.newBuilder().addPathSegments(path).addPathSegment("")
            .apply { (webQuery(fromPage) + extra).forEach { (name, value) -> addQueryParameter(name, value) } }
            .build()

    private fun webQuery(fromPage: String): List<Pair<String, String>> = listOfNotNull(
        "aid" to repairs().aid,
        "app_language" to "en",
        "app_name" to "tiktok_web",
        "browser_language" to "en-US",
        "browser_name" to "Mozilla",
        "browser_online" to "true",
        "browser_platform" to "Win32",
        "browser_version" to "5.0 (Windows)",
        "channel" to "tiktok_web",
        "cookie_enabled" to "true",
        "device_id" to deviceId,
        "device_platform" to "web_pc",
        "focus_state" to "true",
        "from_page" to fromPage,
        "history_len" to "2",
        "is_fullscreen" to "false",
        "is_page_visible" to "true",
        "language" to "en",
        "os" to "windows",
        "priority_region" to "",
        "referer" to "",
        "region" to "US",
        "screen_height" to "1080",
        "screen_width" to "1920",
        "tz_name" to "UTC",
        "webcast_language" to "en",
        jar["msToken"]?.takeIf { it.isNotEmpty() }?.let { "msToken" to it },
    )

    private suspend fun get(url: HttpUrl, referer: String? = null, html: Boolean = false): XResponse {
        val response = http.get(url, headers(referer, html), followRedirects = true)
        // Every Set-Cookie line, not only the first: OkHttp keeps them apart.
        remember(response.headers("set-cookie").joinToString(",").ifEmpty { null })
        if (!response.isSuccessful) throw refusal(response.code, "TikTok answered ${response.code} to ${url.encodedPath}")
        return response
    }

    private fun headers(referer: String?, html: Boolean): Map<String, String> = buildMap {
        put("User-Agent", userAgent)
        put("Accept", if (html) ACCEPT_HTML else ACCEPT_JSON)
        put("Accept-Language", ACCEPT_LANGUAGE)
        referer?.let { put("Referer", it) }
        cookieHeader.takeIf { it.isNotEmpty() }?.let { put("Cookie", it) }
    }

    /** [response]'s JSON through [parse], away from the caller's thread. */
    private suspend fun <T> json(response: XResponse, url: HttpUrl, parse: (J) -> T): T = withContext(Dispatchers.Default) {
        val json = J.parse(response.body)
        if (json.raw !is JsonObject && json.raw !is JsonArray) throw refusal(UNREADABLE, "TikTok's answer to ${url.encodedPath} is not JSON")
        parse(json)
    }

    /** A page's HTML through [parse], away from the caller's thread: TikTok's pages are large. */
    private suspend fun <T> page(response: XResponse, parse: (String) -> T): T = withContext(Dispatchers.Default) { parse(response.body) }

    /** [read]'s answer, or null when TikTok refused it or could not be reached. */
    private suspend fun <T> orNull(read: suspend () -> T): T? = try {
        read()
    } catch (refused: NetworkRequestException) {
        null
    } catch (offline: IOException) {
        null
    }

    private fun remember(setCookie: String?) {
        if (setCookie == null) return
        val set = setCookie.split(SET_COOKIE_SPLIT).mapNotNull(::cookieOf)
        synchronized(this) {
            jar = jar + set
            changed()
        }
    }

    private fun rotateDeviceId() {
        synchronized(this) {
            device = randomDeviceId(random)
            changed()
        }
    }

    private fun changed() = onSession(TikTokSession(headerOf(jar), device))

    companion object {
        /** The network every refusal names. */
        const val NETWORK = "tiktok"
        const val NOT_FOUND = 404
        const val RATE_LIMITED = 429

        /** An answer that is not the JSON asked for, or a video list TikTok refused by its status. */
        const val UNREADABLE = 502

        /** The device ids, found by yt-dlp, that TikTok's unsigned web list accepts. */
        const val DEVICE_ID_MIN = 7_250_000_000_000_000_000L
        const val DEVICE_ID_MAX = 7_351_147_085_025_500_000L

        /** How long discover's people are kept before being read again. */
        const val DISCOVER_KEPT_MILLIS = 10 * 60 * 1000L

        /** TikTok's status for a video list of an account that does not exist. */
        private const val NO_SUCH_ACCOUNT = 10201
        private const val PROFILES_PER_SEARCH = 6
        private const val ACCOUNTS_WITH_VIDEOS = 2
        private const val VIDEOS_PER_SEARCH = 16

        private const val WEB = "https://www.tiktok.com"
        private val WEB_URL = "$WEB/".toHttpUrl()
        private const val ACCEPT_HTML = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        private const val ACCEPT_JSON = "application/json, text/plain, */*"
        private const val ACCEPT_LANGUAGE = "en-US,en;q=0.9"

        /** Set-Cookie values joined into one are split where the next cookie's name begins, not at an expiry date's comma. */
        private val SET_COOKIE_SPLIT = Regex(",(?=[^;]+=)")

        /** A device id in the range TikTok's unsigned web list accepts. */
        fun randomDeviceId(random: Random = Random.Default): String {
            val share = random.nextInt(1 shl 30).toDouble() / (1 shl 30)
            return (DEVICE_ID_MIN + (share * (DEVICE_ID_MAX - DEVICE_ID_MIN)).roundToLong()).toString()
        }

        private fun refusal(code: Int, message: String) = NetworkRequestException(NETWORK, code, message)

        private fun headerOf(cookies: Map<String, String>): String = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

        /** The kept Cookie header ("name=value; …"); a cookie without a name is dropped. */
        private fun keptCookies(raw: String): Map<String, String> = raw.split(';').mapNotNull(::cookieOf).toMap()

        private fun cookieOf(piece: String): Pair<String, String>? {
            val pair = piece.substringBefore(';').trim()
            val equals = pair.indexOf('=')
            return if (equals > 0) pair.substring(0, equals).trim() to pair.substring(equals + 1).trim() else null
        }
    }
}
