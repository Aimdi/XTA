package com.aimdi.xtanative.networks.reddit

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import java.net.URLEncoder
import java.util.Base64
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * How XTA's Reddit client introduces itself and what it keeps between
 * requests: the cookies the website set, and the app-only token a client id
 * buys. The API is asked with the agent Reddit's rules ask for; the website,
 * which turns away anything announcing itself as a bot, is asked as a browser.
 */
internal class RedditTransport(
    private val http: XHttp,
    private val webUserAgent: String,
    private val clock: () -> Long,
    /** How long one request may wait for its answer: XTA's `_timeout`, 20 seconds. */
    private val timeoutMillis: Long = REQUEST_TIMEOUT_MILLIS,
) {
    /**
     * Cookies the public hosts set, kept for the client's life: a client that
     * never carries one looks like a fresh stranger on every request, and the
     * over-18 consent, once given, is answered once rather than per community.
     */
    private val cookies = LinkedHashMap<String, String>()

    @Volatile private var token: String? = null

    @Volatile private var tokenExpiry = 0L

    val hasToken: Boolean get() = token != null && tokenExpiry > clock()

    fun forgetToken() {
        token = null
        tokenExpiry = 0L
    }

    /** The bearer [session] reads with: its account's token, else an app-only one for its client id. */
    suspend fun token(session: RedditSession): String = session.userToken ?: authorize(session.clientId)

    /**
     * An app-only token for [clientId], cached until a minute before it
     * expires: Reddit's `installed_client` grant, which authenticates the app,
     * not a user, with a device id that says "do not track".
     *
     * @throws IllegalArgumentException when [clientId] is blank.
     */
    suspend fun authorize(clientId: String): String {
        token?.takeIf { hasToken }?.let { return it }
        require(clientId.isNotBlank()) { "Missing client id" }
        val response = withDeadline(timeoutMillis, TOKEN_URL) {
            http.post(
                TOKEN_URL,
                mapOf("Authorization" to basicAuth(clientId), "User-Agent" to RedditClient.API_USER_AGENT),
                "grant_type=https://oauth.reddit.com/grants/installed_client&device_id=${RedditClient.DEVICE_ID}",
                FORM,
            )
        }
        if (response.code != 200) throw failureOf(response)
        val answer = objectOf(response)
        val fresh = answer["access_token"].string?.ifEmpty { null } ?: throw unreadable("No access_token in the token response", response.code)
        token = fresh
        // A minute early, so a request in flight cannot land on a dead token.
        tokenExpiry = clock() + ((answer["expires_in"].integer ?: 3600) - 60) * 1000L
        return fresh
    }

    /**
     * One GET, with [token] when there is one and with [consent] cookies on
     * top of the kept ones. A 401 drops the cached token so the next read
     * authorises again; with a token, anything but 200 is a failure.
     */
    suspend fun read(url: HttpUrl, token: String? = null, consent: Map<String, String> = emptyMap()): XResponse {
        val public = url.host != RedditHosts.OAUTH_HOST
        val jar = jar() + consent
        val headers = buildMap {
            if (token != null) put("Authorization", "Bearer $token")
            put("User-Agent", if (public) webUserAgent else RedditClient.API_USER_AGENT)
            // The website weighs these too; their absence is another bot tell.
            if (public) {
                put("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                put("Accept-Language", LANGUAGE)
            }
            if (public && jar.isNotEmpty()) put("Cookie", cookieHeader(jar))
        }
        val response = withDeadline(timeoutMillis, url) { http.get(url, headers, followRedirects = true) }
        remember(response, consent)
        if (response.code == 401) forgetToken()
        if (token != null && response.code != 200) throw failureOf(response, url)
        return response
    }

    /** One form POST to the website, as its own page sends one: from its origin, with the cookies it set. */
    suspend fun submit(url: HttpUrl, form: Map<String, String>): XResponse {
        val jar = jar()
        val headers = buildMap {
            put("User-Agent", webUserAgent)
            put("Accept", "text/html,*/*;q=0.8")
            put("Accept-Language", LANGUAGE)
            put("Origin", RedditHosts.WWW)
            if (jar.isNotEmpty()) put("Cookie", cookieHeader(jar))
        }
        val body = form.entries.joinToString("&") { (name, value) -> "${formEncode(name)}=${formEncode(value)}" }
        val response = withDeadline(timeoutMillis, url) { http.post(url, headers, body, "$FORM; charset=utf-8") }
        remember(response, emptyMap())
        return response
    }

    /** The value of the cookie [name] the website set, if it did. */
    fun cookie(name: String): String? = synchronized(cookies) { cookies[name] }

    private fun jar(): Map<String, String> = synchronized(cookies) { LinkedHashMap(cookies) }

    /**
     * Keeps what the answer set, and [sent] consent once Reddit accepted it:
     * a refused one would otherwise ride along on every later request.
     * Several cookies can arrive comma-joined; only each `name=value` head matters.
     */
    private fun remember(response: XResponse, sent: Map<String, String>) = synchronized(cookies) {
        if (response.code == 200) cookies.putAll(sent)
        // Every Set-Cookie line, not only the first: OkHttp keeps them apart.
        response.headers("set-cookie").flatMap { it.split(cookieSeparator) }.forEach { piece ->
            val pair = piece.substringBefore(';').trim()
            val equals = pair.indexOf('=')
            if (equals > 0) cookies[pair.substring(0, equals).trim()] = pair.substring(equals + 1).trim()
        }
    }

    /** Reddit's answer as a failure, naming the host: which tells an account's read from an account-free one. */
    fun failureOf(response: XResponse, url: HttpUrl? = null): NetworkRequestException =
        refused(response.code, if (url == null) "HTTP ${response.code}" else "HTTP ${response.code} from ${url.host}")

    /** The JSON object [response] holds; a page that is not one (a block page, most likely) is a failure. */
    fun objectOf(response: XResponse): J =
        J.parse(response.body).takeIf { it.raw is JsonObject } ?: throw unreadable("Response was not a JSON object", response.code)

    /** The JSON array [response] holds, as a thread's `[post, comments]` is. */
    fun arrayOf(response: XResponse): J =
        J.parse(response.body).takeIf { it.raw is JsonArray } ?: throw unreadable("Response was not a JSON array", response.code)

    private fun cookieHeader(jar: Map<String, String>) = jar.entries.joinToString("; ") { "${it.key}=${it.value}" }

    /** An installed app has no secret, so the password half is empty. */
    private fun basicAuth(clientId: String) =
        "Basic " + Base64.getEncoder().encodeToString("${clientId.trim()}:".toByteArray(Charsets.UTF_8))

    private fun formEncode(value: String) = URLEncoder.encode(value, Charsets.UTF_8)

    companion object {
        val TOKEN_URL = "${RedditHosts.WWW}/api/v1/access_token".toHttpUrl()
        const val FORM = "application/x-www-form-urlencoded"

        /** XTA's `RedditClient._timeout`: no read may wait longer than this. */
        const val REQUEST_TIMEOUT_MILLIS = 20_000L

        private const val LANGUAGE = "en-US,en;q=0.9"
        private val cookieSeparator = Regex(",(?=[^;]+=)")
    }
}
