package com.aimdi.xtanative.networks.reddit

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.networks.NetworkRequestException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Signing in to Reddit (XTA's `RedditAuth`), for a reader who wants their own
 * account's rate limits, the most reliable route Reddit leaves open. It is
 * Reddit's authorization-code flow for installed apps and asks for read
 * scopes only: XTA never posts, votes or subscribes. Only the refresh token
 * is kept by the app; access tokens live an hour and are minted again.
 *
 * Exchanging a code and minting a token are POSTs to Reddit's token
 * endpoint: they sign the reader in, they write nothing to Reddit.
 */
class RedditAuth(private val http: XHttp) {
    /** Trades the one-time code from the redirect for the refresh token the app keeps. */
    suspend fun exchangeCode(clientId: String, code: String): String =
        tokenRequest(clientId, mapOf("grant_type" to "authorization_code", "code" to code, "redirect_uri" to REDIRECT_URI), "refresh_token")

    /** A fresh access token for a signed-in reader, without asking them again. */
    suspend fun accessToken(clientId: String, refreshToken: String): String =
        tokenRequest(clientId, mapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken), "access_token")

    /**
     * The signed-in account's name, which is what the `identity` scope is for:
     * "signed in" alone says little on a device that held two accounts. Null
     * when Reddit does not say: the name is decoration, and a sign-in that
     * reads fine must not look broken because this one read did not answer.
     */
    suspend fun accountName(accessToken: String): String? = recovering {
        val response = http.get(ME_URL, mapOf("Authorization" to "Bearer $accessToken", "User-Agent" to RedditClient.API_USER_AGENT), followRedirects = true)
        if (response.code == 200) J.parse(response.body)["name"].string else null
    }

    /**
     * One token request. A revoked refresh token comes back as `invalid_grant`,
     * on a 400 and sometimes a 200, and is read before the status: unlike any
     * other answer it means the session is over (a 401, as a rejected client
     * id is). A body that is not JSON is a block page, not a verdict.
     */
    private suspend fun tokenRequest(clientId: String, form: Map<String, String>, want: String): String {
        require(clientId.isNotBlank()) { "Signing in still needs a client id" }
        val headers = mapOf(
            "Authorization" to "Basic " + Base64.getEncoder().encodeToString("${clientId.trim()}:".toByteArray(Charsets.UTF_8)),
            "User-Agent" to RedditClient.API_USER_AGENT,
        )
        val body = form.entries.joinToString("&") { (name, value) -> "$name=${encodeComponent(value)}" }
        val response = http.post(RedditTransport.TOKEN_URL, headers, body, RedditTransport.FORM)
        if (response.code == 401) throw refused(401, "Reddit rejected the client id")
        val answer = J.parse(response.body)
        if (answer["error"].string == "invalid_grant") throw refused(401, "Reddit no longer accepts this session")
        if (response.code != 200) throw unreadable("HTTP ${response.code} from the token endpoint", response.code)
        // J reads leniently, so a page of HTML comes back as a bare string rather than as nothing.
        if (answer.raw !is JsonObject && answer.raw !is JsonArray) {
            throw unreadable("The token endpoint answered with something that is not JSON", response.code)
        }
        return answer[want].string?.ifEmpty { null } ?: throw unreadable("No $want in the token response", response.code)
    }

    companion object {
        /**
         * Where Reddit sends the browser back to. It never resolves: the login
         * screen intercepts it, so any scheme works as long as the registered
         * app carries exactly this one.
         */
        const val REDIRECT_URI = "xta://reddit-auth"

        /** Read listings, and the account's name. No write scope at all. */
        const val SCOPES = "read,identity"

        private val ME_URL = "${RedditHosts.OAUTH}/api/v1/me".toHttpUrl()

        /** The page the reader signs in on; `duration=permanent` is what makes Reddit hand out a refresh token. */
        fun authorizeUrl(clientId: String, state: String): HttpUrl = redditUrl(
            "${RedditHosts.WWW}/api/v1/authorize.compact",
            mapOf(
                "client_id" to clientId.trim(),
                "response_type" to "code",
                "state" to state,
                "redirect_uri" to REDIRECT_URI,
                "duration" to "permanent",
                "scope" to SCOPES,
            ),
        )

        /**
         * A fresh OAuth `state` from the platform's secure random: the value
         * [codeFrom] compares, which a guessable one (the clock) would let
         * another page bind the reader's app to someone else's account with.
         */
        fun oauthState(random: SecureRandom = SecureRandom()): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

        /** Whether [url] is Reddit coming back to the app, whatever it brought. */
        fun isRedirect(url: String): Boolean = url.startsWith(REDIRECT_URI)

        /**
         * The code Reddit put on the redirect, or null when [url] is not the
         * redirect, carried an error, or carried another state: that check is
         * what stops another page from injecting a code.
         */
        fun codeFrom(url: String, expectedState: String): String? {
            if (!isRedirect(url)) return null
            val query = queryOf(url)
            return query["code"]?.ifEmpty { null }?.takeIf { query["state"] == expectedState }
        }

        /** Whether the redirect says the reader declined, so the login can close quietly. */
        fun deniedIn(url: String): Boolean = isRedirect(url) && queryOf(url)["error"] != null

        private fun queryOf(url: String): Map<String, String> {
            val raw = runCatching { URI(url).rawQuery }.getOrNull() ?: url.substringAfter('?', "").substringBefore('#')
            return raw.split('&').filter { it.isNotEmpty() }.associate { pair ->
                decode(pair.substringBefore('=')) to decode(pair.substringAfter('=', ""))
            }
        }

        private fun decode(text: String) = runCatching { URLDecoder.decode(text, Charsets.UTF_8) }.getOrDefault(text)

        /** Dart's `Uri.encodeComponent`: everything but `A-Z a-z 0-9 - _ . ! ~ * ' ( )`. */
        private fun encodeComponent(value: String) = URLEncoder.encode(value, Charsets.UTF_8)
            .replace("+", "%20").replace("%21", "!").replace("%27", "'").replace("%28", "(").replace("%29", ")").replace("%7E", "~")
    }
}

/** How reads should reach Reddit for now, and whether Reddit ended the stored sign-in on the way. */
data class ResolvedRedditSession(
    val session: RedditSession,
    /** Reddit no longer accepts the refresh token: the app forgets it, and the reader is signed out. */
    val signedOut: Boolean = false,
)

/**
 * The session every read shares (XTA's `RedditReadSession.resolve` and the
 * access token its `RedditPostSource` keeps): one access token across
 * surfaces and refreshes rather than one per read, minted once even when
 * several reads ask at the same moment.
 */
class RedditSignIn(private val auth: RedditAuth, private val clock: () -> Long = System::currentTimeMillis) {
    private val minting = Mutex()

    @Volatile private var minted: Minted? = null

    private data class Minted(val refreshToken: String, val clientId: String, val token: String, val expiresAt: Long)

    /**
     * The account-free route when [preferPublic], even with a credential
     * there; as the signed-in account when there is a [refreshToken]; with
     * [clientId] alone otherwise. A token Reddit will not mint for a passing
     * reason (offline, throttled, a block page) falls back to that last
     * session; only Reddit refusing the grant signs the reader out.
     */
    suspend fun resolve(clientId: String, refreshToken: String?, preferPublic: Boolean): ResolvedRedditSession {
        val fallback = RedditSession(clientId = clientId)
        if (preferPublic) return ResolvedRedditSession(fallback.copy(preferPublic = true))
        if (refreshToken.isNullOrBlank() || clientId.isBlank()) return ResolvedRedditSession(fallback)
        return minting.withLock {
            minted?.takeIf { it.refreshToken == refreshToken && it.clientId == clientId && clock() < it.expiresAt }
                ?.let { return@withLock ResolvedRedditSession(fallback.copy(userToken = it.token)) }
            orElse({
                val token = auth.accessToken(clientId, refreshToken)
                minted = Minted(refreshToken, clientId, token, clock() + TOKEN_TTL_MILLIS)
                ResolvedRedditSession(fallback.copy(userToken = token))
            }) { failure -> ResolvedRedditSession(fallback, signedOut = (failure as? NetworkRequestException)?.code == 401) }
        }
    }

    /** Forgets the minted token: the reader signed out or in as someone else. */
    fun forget() {
        minted = null
    }

    companion object {
        /** Reddit's access tokens live an hour; minting one is a request, so one is kept a little less than that. */
        const val TOKEN_TTL_MILLIS = 50 * 60 * 1000L
    }
}
