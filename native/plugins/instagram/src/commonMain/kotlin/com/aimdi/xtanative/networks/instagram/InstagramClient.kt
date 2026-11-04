package com.aimdi.xtanative.networks.instagram

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.Page
import java.io.IOException
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
 * Reads Instagram as XTA's guest-first client does (instagram_client.dart).
 * The homepage hands any visitor its `csrftoken` and `mid` cookies; with
 * those and Instagram's public web app id a guest reads `web_profile_info`,
 * a profile with its newest posts. A session pasted from a browser (or
 * copied from Threads) reads more: people search, the paged feed, Explore.
 * Read-only: nothing is liked, followed or commented on Instagram.
 *
 * A refusal is a [NetworkRequestException] named [NETWORK]: [NOT_FOUND] when
 * there is no such account, [RATE_LIMITED] while Instagram is limiting, 401
 * or 403 when it wants a login (its login page or `login_required`, though
 * answered with 200, is [LOGIN_REQUIRED]), [UNREADABLE] for an answer that is
 * not the JSON asked for, and any other status as Instagram sent it. No
 * connection stays the IOException.
 *
 * @param cookies the Cookie header kept from an earlier start, or pasted; empty reads as a new guest.
 * @param onCookies hears the Cookie header whenever it changes, to keep it for the next start.
 * @param repairs the app id and user agent in force, which XTA's published repairs can replace.
 */
class InstagramClient(
    private val http: XHttp,
    cookies: String = "",
    private val onCookies: (String) -> Unit = {},
    private val repairs: () -> InstagramRepairs = { InstagramRepairs() },
) {
    @Volatile
    private var jar: Map<String, String> = pastedCookies(cookies)

    /** The cookies held, as a Cookie header. */
    val cookieHeader: String get() = headerOf(jar)

    /** Whether a whole session is held: the five cookies Instagram's web app signs in with. */
    val hasSession: Boolean get() = isSession(jar)

    /** Reads from now on with a pasted Cookie header: a browser's, or the Threads session XTA already holds. */
    fun setCookies(raw: String) {
        keep { pastedCookies(raw) }
    }

    /** Forgets every cookie: the next read starts as a new guest. */
    fun clearSession() {
        keep { emptyMap() }
    }

    /** The account [handle] names ("@name", "name" or a profile link). A private account is returned too, for its lock. */
    suspend fun profile(handle: String): InstagramProfile {
        val key = keyOf(handle)
        return webProfile(key, InstagramParser::profile) ?: throw refusal(NOT_FOUND, "No Instagram account @$key")
    }

    /** The newest posts that `web_profile_info` carries beside [handle]'s profile, and the cursor after them. */
    suspend fun profileMedia(handle: String): Page<InstagramPost> = webProfile(keyOf(handle), InstagramParser::profileMedia)

    /** The account [pk]'s posts after [cursor] from the API feed (`feed/user`), which wants a session. */
    suspend fun userFeed(pk: String, cursor: String? = null, count: Int = 12): Page<InstagramPost> {
        val url = API.newBuilder().addPathSegments("api/v1/feed/user").addPathSegment(pk).addPathSegment("")
            .addQueryParameter("count", "$count")
            .maxId(cursor)
            .build()
        return json(get(url, referer = "$WEB/"), url, InstagramParser::userFeed)
    }

    /**
     * A profile's posts page by page, as XTA's profile screen reads them: the
     * first page from [profileMedia], the next ones from [userFeed]. Only a
     * session pages on; a guest gets the first page alone. [pk] spares a
     * second profile read when the screen already has the profile.
     */
    suspend fun profilePosts(handle: String, cursor: String? = null, pk: String? = null): Page<InstagramPost> = when {
        cursor == null -> profileMedia(handle).let { first -> if (hasSession) first else Page(first.items, null) }
        !hasSession -> Page(emptyList(), null)
        else -> userFeed(pk ?: profile(handle).id, cursor)
    }

    /**
     * For you: Instagram's own Explore when a session reads it, else the
     * guest mix of public accounts ([guestDiscover]), which is one page.
     */
    suspend fun forYou(cursor: String? = null): Page<InstagramPost> {
        val explore = if (hasSession) exploreUnlessRefused(cursor) else null
        return when {
            explore != null && explore.items.isNotEmpty() -> explore
            cursor.isNullOrEmpty() -> guestDiscover()
            else -> Page(emptyList(), null)
        }
    }

    /** Explore for a session: the web grid, or `topical_explore` when the grid is refused. */
    suspend fun exploreFeed(cursor: String? = null): Page<InstagramPost> {
        warmGuest()
        return try {
            explore(WEB_URL, "api/v1/discover/web/explore_grid", cursor)
        } catch (refused: NetworkRequestException) {
            if (refused.code == NOT_FOUND) throw refused
            explore(API, "api/v1/discover/topical_explore", cursor)
        }
    }

    /**
     * The guest For you: the newest [perAccount] posts of each of
     * [InstagramDiscovery.HANDLES], read side by side and interleaved. It
     * fails only when every account failed, with a rate limit first if any.
     */
    suspend fun guestDiscover(perAccount: Int = 4): Page<InstagramPost> {
        warmGuest()
        val reads = coroutineScope {
            InstagramDiscovery.HANDLES.map { handle -> async { attempt { profileMedia(handle).items.take(perAccount) } } }.awaitAll()
        }
        val posts = InstagramDiscovery.interleave(reads.map { it.getOrDefault(emptyList()) })
        val failures = reads.mapNotNull { it.exceptionOrNull() }
        if (posts.isEmpty() && failures.isNotEmpty()) {
            throw failures.firstOrNull { (it as? NetworkRequestException)?.code == RATE_LIMITED } ?: failures.first()
        }
        return Page(posts, null)
    }

    /** People search (`topsearch`): one list, not paged. Instagram answers a guest with its login wall. */
    suspend fun searchUsers(query: String): List<InstagramSearchUser> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        warmGuest()
        val url = WEB_URL.newBuilder().addPathSegments("api/v1/web/search/topsearch").addPathSegment("")
            .addQueryParameter("query", q)
            .addQueryParameter("context", "user")
            .build()
        return json(get(url, referer = "$WEB/"), url, InstagramParser::topSearch)
    }

    /** Visits the homepage for the `csrftoken` and `mid` cookies a guest reads with, unless they are held already. */
    suspend fun warmGuest() {
        if ("csrftoken" in jar && "mid" in jar) return
        get(WEB_URL, html = true)
    }

    /** `web_profile_info` from the API host, or from www when the API host is rate-limited or wants a login. */
    private suspend fun <T> webProfile(username: String, parse: (J) -> T): T {
        warmGuest()
        return try {
            webProfileAt(API, username, parse)
        } catch (refused: NetworkRequestException) {
            if (!isWallOrLimit(refused.code)) throw refused
            webProfileAt(WEB_URL, username, parse)
        }
    }

    private suspend fun <T> webProfileAt(origin: HttpUrl, username: String, parse: (J) -> T): T {
        val url = origin.newBuilder().addPathSegments("api/v1/users/web_profile_info").addPathSegment("")
            .addQueryParameter("username", username)
            .build()
        return json(get(url, referer = "$WEB/$username/"), url, parse)
    }

    private suspend fun explore(origin: HttpUrl, path: String, cursor: String?): Page<InstagramPost> {
        val url = origin.newBuilder().addPathSegments(path).addPathSegment("")
            .addQueryParameter("is_prefetch", "false")
            .addQueryParameter("omit_cover_media", "false")
            .addQueryParameter("module", "explore_popular")
            .maxId(cursor)
            .build()
        return json(get(url, referer = "$WEB/explore/"), url, InstagramParser::explore)
    }

    private suspend fun exploreUnlessRefused(cursor: String?): Page<InstagramPost>? = try {
        exploreFeed(cursor)
    } catch (refused: NetworkRequestException) {
        if (refused.code == NOT_FOUND) throw refused
        null
    }

    private suspend fun <T> attempt(read: suspend () -> T): Result<T> = try {
        Result.success(read())
    } catch (refused: NetworkRequestException) {
        Result.failure(refused)
    } catch (offline: IOException) {
        Result.failure(offline)
    }

    private suspend fun get(url: HttpUrl, referer: String? = null, html: Boolean = false): XResponse {
        val response = http.get(url, headers(referer, html), followRedirects = true)
        // Every Set-Cookie line, not only the first: OkHttp keeps them apart.
        remember(response.headers("set-cookie").joinToString(",").ifEmpty { null })
        refusalOf(response, url, html)?.let { throw it }
        return response
    }

    private fun headers(referer: String?, html: Boolean): Map<String, String> {
        val values = repairs()
        val held = jar
        return buildMap {
            put("User-Agent", values.userAgent)
            put("Accept", if (html) ACCEPT_HTML else ACCEPT_JSON)
            put("Accept-Language", ACCEPT_LANGUAGE)
            put("X-IG-App-ID", values.appId)
            put("X-Requested-With", "XMLHttpRequest")
            put("Origin", WEB)
            referer?.let { put("Referer", it) }
            held["csrftoken"]?.let { put("X-CSRFToken", it) }
            if (held.isNotEmpty()) put("Cookie", headerOf(held))
        }
    }

    private fun refusalOf(response: XResponse, url: HttpUrl, html: Boolean): NetworkRequestException? = when {
        !response.isSuccessful -> refusal(response.code, "Instagram answered ${response.code} to ${url.encodedPath}")
        !html && isLoginPage(response.body) -> refusal(LOGIN_REQUIRED, "Instagram answered ${url.encodedPath} with its login page")
        else -> null
    }

    /** [response]'s JSON through [parse], away from the caller's thread; `login_required` in its place is a refusal. */
    private suspend fun <T> json(response: XResponse, url: HttpUrl, parse: (J) -> T): T = withContext(Dispatchers.Default) {
        val json = J.parse(response.body)
        when {
            json.raw !is JsonObject && json.raw !is JsonArray ->
                throw refusal(UNREADABLE, "Instagram's answer to ${url.encodedPath} is not JSON")
            InstagramParser.loginRequired(json) -> throw refusal(LOGIN_REQUIRED, "Instagram wants a login for ${url.encodedPath}")
            else -> parse(json)
        }
    }

    private fun remember(setCookie: String?) {
        if (setCookie == null) return
        val set = setCookie.split(SET_COOKIE_SPLIT).mapNotNull(::cookieOf)
        keep { it + set }
    }

    private fun keep(change: (Map<String, String>) -> Map<String, String>) = synchronized(this) {
        jar = change(jar)
        onCookies(headerOf(jar))
    }

    private fun keyOf(handle: String): String =
        InstagramParser.handle(handle) ?: throw refusal(NOT_FOUND, "Not an Instagram username: $handle")

    private fun HttpUrl.Builder.maxId(cursor: String?): HttpUrl.Builder = apply {
        if (!cursor.isNullOrEmpty()) addQueryParameter("max_id", cursor)
    }

    companion object {
        /** The network every refusal names. */
        const val NETWORK = "instagram"
        const val NOT_FOUND = 404
        const val RATE_LIMITED = 429

        /** Instagram's login wall: its login page or `login_required` where JSON was asked for, both answered with 200. */
        const val LOGIN_REQUIRED = 401

        /** An answer that is not the JSON asked for. */
        const val UNREADABLE = 502

        private const val WEB = "https://www.instagram.com"
        private val WEB_URL = "$WEB/".toHttpUrl()
        private val API = "https://i.instagram.com/".toHttpUrl()
        private const val ACCEPT_HTML = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        private const val ACCEPT_JSON = "application/json, text/plain, */*"
        private const val ACCEPT_LANGUAGE = "en-US,en;q=0.9"

        /** The cookies Instagram's web app is signed in with: all five make a session. */
        private val SESSION_COOKIES = listOf("sessionid", "csrftoken", "ds_user_id", "mid", "ig_did")

        /** Set-Cookie values joined into one are split where the next cookie's name begins, not at an expiry date's comma. */
        private val SET_COOKIE_SPLIT = Regex(",(?=[^;]+=)")

        /** Whether [cookieHeader] is a whole session, as a client holding it would read. */
        fun hasSession(cookieHeader: String): Boolean = isSession(pastedCookies(cookieHeader))

        private fun isSession(held: Map<String, String>): Boolean = SESSION_COOKIES.all { !held[it].isNullOrEmpty() }

        private fun refusal(code: Int, message: String) = NetworkRequestException(NETWORK, code, message)

        private fun isWallOrLimit(code: Int): Boolean = code == 401 || code == 403 || code == RATE_LIMITED

        private fun isLoginPage(body: String): Boolean = !body.startsWith("{") && !body.startsWith("[") && "<html" in body

        private fun headerOf(cookies: Map<String, String>): String = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

        /** A pasted Cookie header ("name=value; …"), keeping only cookies with a name and a value. */
        private fun pastedCookies(raw: String): Map<String, String> =
            raw.split(';').mapNotNull(::cookieOf).filter { (name, value) -> name.isNotEmpty() && value.isNotEmpty() }.toMap()

        private fun cookieOf(piece: String): Pair<String, String>? {
            val pair = piece.substringBefore(';').trim()
            val equals = pair.indexOf('=')
            return if (equals > 0) pair.substring(0, equals).trim() to pair.substring(equals + 1).trim() else null
        }
    }
}
