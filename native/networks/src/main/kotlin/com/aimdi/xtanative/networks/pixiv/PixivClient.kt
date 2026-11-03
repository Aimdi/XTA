package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.Page
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val API_HOST = "app-api.pixiv.net"
private const val API_BASE = "https://$API_HOST"
private const val FORM = "application/x-www-form-urlencoded"
internal const val ANDROID = "for_android"

/**
 * The salt behind `X-Client-Hash`. Pixiv's token endpoint wants every request
 * to carry the current time and an MD5 of it with this salt, as the official
 * app sends them; without the pair it refuses even a valid refresh token.
 */
internal const val CLIENT_HASH_SALT = "28c1fdd170a5204386cb1313c7077b34f83e4aaf4aa829ce78c231e05b0bae2c"

/**
 * Pixiv's app API (`app-api.pixiv.net`), read as XTA reads it. Read-only:
 * nothing is bookmarked, followed, commented on or liked on Pixiv.
 *
 * Every request carries the signed time pair the official app sends; the
 * signed-in ones also carry a bearer token, traded for the stored refresh
 * token when it runs out, and once more, replaying the request, when Pixiv
 * refuses it. Refreshes asked for together share one round trip. The token
 * only ever goes to the app API's own host. A connection Pixiv's edge drops
 * before answering is sent again by [XHttp] (and OkHttp) below this client.
 *
 * The feature reads live beside it over this transport: [PixivDiscoveryApi],
 * [PixivSearchApi], [PixivSocialApi], [PixivNovelApi], [PixivCommentsApi] and
 * [PixivBookmarkApi].
 */
class PixivClient(
    internal val http: XHttp,
    private val store: PixivCredentialStore,
    /** The reader's Show R-18 and Hide AI choices, read at each list. */
    private val filters: () -> PixivFilters = { PixivFilters() },
    /** The language XTA Native is shown in (`ja`, `zh-Hant-TW`, ...): Pixiv translates tags into it. */
    internal val locale: () -> String = { Locale.getDefault().toLanguageTag() },
    private val clock: () -> Long = System::currentTimeMillis,
    val app: PixivApp = PixivApp(),
) {
    private val writing = Any()
    private val refreshing = Mutex()

    @Volatile
    private var refreshes = 0

    /** The last refresh's outcome, and which refresh it was: calls that waited on it take it as theirs. */
    @Volatile
    private var lastRefresh: Pair<Int, Result<PixivAuthUser>>? = null

    val credentials: PixivCredentials get() = store.credentials
    val showR18: Boolean get() = filters().showR18
    val hideAi: Boolean get() = filters().hideAi
    val isPremium: Boolean get() = store.credentials.isPremium

    /** The signed-in account's id, or null until a token response named it. */
    val storedUserId: Long? get() = store.credentials.userId.takeIf { it != 0L }

    private val refreshToken: String get() = store.credentials.refreshToken.trim()

    /** GETs an app-API [path] (or an absolute URL, such as a list's `next_url`) and parses its JSON. [auth] false sends no token. */
    suspend fun getJson(path: String, query: Map<String, String> = emptyMap(), auth: Boolean = true): J {
        val url = requireNotNull(pixivUrl(path, query)) { "Not a Pixiv address: $path" }
        val response = call(url, auth)
        val json = withContext(Dispatchers.Default) { J.parse(response.body) }
        return json.takeIf(::isAnswer) ?: throw NetworkRequestException(PIXIV_NETWORK, response.code, "${url.encodedPath}: not JSON")
    }

    /** The page a list's `next_url` points at. */
    suspend fun getNextJson(next: String): J = getJson(next)

    /** A list's first page from [path], or the page [next] points at when there is one. */
    suspend fun getPage(path: String, query: Map<String, String> = emptyMap(), next: String? = null): J =
        if (next.isNullOrEmpty()) getJson(path, query) else getNextJson(next)

    /** GETs a page Pixiv answers in HTML, such as a novel's text. */
    suspend fun getText(path: String, query: Map<String, String> = emptyMap(), auth: Boolean = true): String {
        val url = requireNotNull(pixivUrl(path, query)) { "Not a Pixiv address: $path" }
        return call(url, auth, mapOf("Accept" to "text/html")).body
    }

    /** A list payload as the reader's filters show it; [ownList] keeps all the reader saved or posted, R-18 and AI works included. */
    fun illustPage(json: J, includeR18: Boolean? = null, includeAi: Boolean? = null, ownList: Boolean = false): Page<PixivIllust> =
        Page(PixivIllust.list(json, includeR18 ?: (ownList || showR18), includeAi ?: (ownList || !hideAi)), nextOf(json))

    /**
     * Trades the refresh token for a fresh access token, keeps both, and
     * answers whose account it is. Calls made while one is on its way share
     * it. When the reader switched accounts meanwhile, the answer belongs to
     * the old one, so the account now in use is refreshed instead.
     */
    suspend fun refreshAccessToken(): PixivAuthUser {
        val asked = refreshes
        return refreshing.withLock {
            when (val shared = lastRefresh?.takeIf { it.first > asked }) {
                null -> refreshOnce()
                else -> shared.second.getOrThrow()
            }
        }
    }

    /** Confirms the refresh token still works, and who it is. */
    suspend fun verify(): PixivAuthUser = refreshAccessToken()

    /** Warms a usable access token without refreshing one that is still good. */
    suspend fun ensureAccessToken() {
        accessToken()
    }

    /** The signed-in account's id, refreshing only when no token response named it yet. */
    suspend fun ensureUserId(): Long {
        accessToken()
        storedUserId?.let { return it }
        val user = refreshAccessToken()
        if (user.id == 0L) throw unusableAnswer("token user has no id")
        return user.id
    }

    /** Trades a sign-in's one-time [code] for tokens; [applyLoginTokens] then puts them in use. */
    suspend fun exchangeCode(code: String, codeVerifier: String): PixivLoginTokens {
        val json = tokenRequest(
            "grant_type" to "authorization_code",
            "code" to code,
            "code_verifier" to codeVerifier,
            "redirect_uri" to PixivLogin.REDIRECT_URI,
            "include_policy" to "true",
        )
        val access = json["access_token"].string.orEmpty()
        val refresh = json["refresh_token"].string.orEmpty()
        if (access.isEmpty() || refresh.isEmpty()) throw unusableAnswer("token response missing tokens")
        return PixivLoginTokens(access, refresh, json["expires_in"].long ?: 3600, PixivAuthUser.parse(json["user"]))
    }

    /** Puts a sign-in's tokens in use, its account's id with them even when the answer named none. */
    fun applyLoginTokens(tokens: PixivLoginTokens): PixivAuthUser {
        keep(PixivCredentials(tokens.refreshToken, tokens.accessToken, expiry(tokens.expiresInSeconds), tokens.user.id, tokens.user.isPremium))
        return tokens.user
    }

    /** Makes another stored account the one every request uses; its access token is not kept, so the next read refreshes one. */
    fun switchTo(userId: Long, refreshToken: String, isPremium: Boolean = false) =
        keep(PixivCredentials.account(userId, refreshToken, isPremium))

    /** Puts a pasted refresh [token] in use. Whose it is stays unknown until a token check names them. */
    fun useRefreshToken(token: String) = keep(PixivCredentials.pasted(token))

    /** Leaves no account to read as; the reads that need one fail with [PixivNoAccountException]. */
    fun signOut() = keep(PixivCredentials.NONE)

    private fun keep(credentials: PixivCredentials) {
        synchronized(writing) { store.credentials = credentials }
    }

    private suspend fun refreshOnce(): PixivAuthUser {
        val outcome = try {
            Result.success(refreshFor(refreshToken))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
        lastRefresh = ++refreshes to outcome
        return outcome.getOrThrow()
    }

    private suspend fun refreshFor(sent: String): PixivAuthUser {
        if (sent.isEmpty()) throw PixivNoAccountException()
        val json = tokenRequest("grant_type" to "refresh_token", "include_policy" to "true", "refresh_token" to sent)
        return keepRefreshed(json, sent) ?: refreshFor(refreshToken)
    }

    /** Keeps a token response for [sent]; null when another account took its place meanwhile. */
    private fun keepRefreshed(json: J, sent: String): PixivAuthUser? {
        synchronized(writing) {
            val old = store.credentials
            if (old.refreshToken.trim() != sent) return null
            val access = json["access_token"].string?.ifEmpty { null } ?: throw unusableAnswer("token response missing access_token")
            val user = PixivAuthUser.parse(json["user"])
            store.credentials = old.copy(
                accessToken = access,
                refreshToken = json["refresh_token"].string?.ifEmpty { null } ?: old.refreshToken,
                accessExpiresAt = expiry(json["expires_in"].long ?: 3600),
                userId = user.id.takeIf { it != 0L } ?: old.userId,
                isPremium = if (json["user"].exists) user.isPremium else old.isPremium,
            )
            return user
        }
    }

    private fun expiry(seconds: Long): Long = clock() + (seconds - 60) * 1000

    private suspend fun accessToken(): String {
        val held = store.credentials
        if (held.accessToken.isNotEmpty() && held.accessExpiresAt > clock()) return held.accessToken
        refreshAccessToken()
        return store.credentials.accessToken
    }

    /** A token for the replay after [refused] was turned away: one another request already swapped in is reused. */
    private suspend fun reauthorize(refused: String): String {
        val current = store.credentials.accessToken
        if (current.isNotEmpty() && current != refused) return current
        refreshAccessToken()
        return store.credentials.accessToken
    }

    private suspend fun tokenRequest(vararg fields: Pair<String, String>): J {
        val form = listOf("client_id" to app.clientId, "client_secret" to app.clientSecret) + fields
        val response = http.post(PixivLogin.TOKEN_URL.toHttpUrl(), baseHeaders(), formOf(form), FORM)
        if (!response.isSuccessful) throw tokenFailure(response)
        return J.parse(response.body).takeIf(::isAnswer)
            ?: throw NetworkRequestException(PIXIV_NETWORK, response.code, "token endpoint: not JSON")
    }

    /** The token endpoint says why it refused: a wrong token (`invalid_grant`) reads differently from a request it does not trust. */
    private fun tokenFailure(response: XResponse): NetworkRequestException {
        val body = J.parse(response.body)
        val detail = body["errors"]["system"]["message"].string ?: body["error_description"].string ?: body["error"].string
            ?: "HTTP ${response.code}"
        return NetworkRequestException(PIXIV_NETWORK, response.code, "token endpoint: $detail (HTTP ${response.code})")
    }

    private suspend fun call(url: HttpUrl, auth: Boolean, extra: Map<String, String> = emptyMap()): XResponse {
        if (!auth || url.host != API_HOST) return checked(url, http.get(url, baseHeaders() + extra, true))
        val token = accessToken()
        val first = http.get(url, bearer(token) + extra, true)
        val answer = if (isTokenRefused(first)) http.get(url, bearer(reauthorize(token)) + extra, true) else first
        return checked(url, answer)
    }

    private fun bearer(token: String): Map<String, String> = baseHeaders() + ("Authorization" to "Bearer $token")

    /** Signed afresh for each request: Pixiv compares the time with its own clock. */
    private fun baseHeaders(): Map<String, String> {
        val time = pixivClientTime(clock())
        return linkedMapOf(
            "User-Agent" to app.userAgent,
            "App-OS" to "android",
            "App-OS-Version" to "11",
            "App-Version" to app.version,
            "Accept" to "application/json",
            "Accept-Language" to pixivAcceptLanguage(locale()),
            "X-Client-Time" to time,
            "X-Client-Hash" to pixivClientHash(time),
        )
    }

    private fun checked(url: HttpUrl, response: XResponse): XResponse {
        if (response.isSuccessful) return response
        val endpoint = url.encodedPath
        throw when {
            isRateLimited(response) -> NetworkRequestException(PIXIV_NETWORK, 429, "$endpoint: rate limited (HTTP ${response.code})")
            isTokenRefused(response) -> NetworkRequestException(PIXIV_NETWORK, 401, "$endpoint: token refused (HTTP ${response.code})")
            else -> NetworkRequestException(PIXIV_NETWORK, response.code, "$endpoint: HTTP ${response.code}")
        }
    }

    /** Pixiv refuses an expired or revoked access token with a 400 about "the OAuth process" far more often than with a 401. */
    private fun isTokenRefused(response: XResponse): Boolean =
        response.code == 401 || (response.code == 400 && "OAuth" in errorMessage(response))

    /** Pixiv rate-limits with a 4xx (usually 400 or 403) saying "Rate Limit" far more often than with a 429. */
    private fun isRateLimited(response: XResponse): Boolean =
        response.code == 429 || (response.code in 400..499 && "rate limit" in errorMessage(response).lowercase())

    private fun errorMessage(response: XResponse): String = J.parse(response.body)["error"]["message"].string.orEmpty()

    /** New works from followed creators: [restrict] is `all`, `public` or `private`. */
    suspend fun following(restrict: String = "all", next: String? = null): Page<PixivIllust> =
        illustPage(getPage("/v2/illust/follow", mapOf("restrict" to restrict), next))

    /** The personal For you feed, today's ranking mixed in when it runs thin. */
    suspend fun recommended(next: String? = null): Page<PixivIllust> = illustPage(
        getPage(
            "/v1/illust/recommended",
            mapOf("include_ranking_illusts" to "true", "include_privacy_policy" to "true", "filter" to ANDROID),
            next,
        ),
    )

    /**
     * Creators Pixiv relates to [seedUserId], each with a few works; the
     * reader's choices apply unless told otherwise. `seed_user_id` goes last,
     * as Pixiv asks clients to put it.
     */
    suspend fun relatedUsers(seedUserId: Long, includeR18: Boolean? = null, includeAi: Boolean? = null): List<PixivUserPreview> =
        PixivUserPreview.list(
            getJson("/v1/user/related", mapOf("filter" to ANDROID, "seed_user_id" to "$seedUserId")),
            includeR18 ?: showR18,
            includeAi ?: !hideAi,
        )

    /** What Pixiv is drawing right now, each tag with a work standing for it. */
    suspend fun trendingTags(): List<PixivTrendTag> =
        PixivTrendTag.list(getJson("/v1/trending-tags/illust", mapOf("filter" to ANDROID)), showR18, !hideAi)

    /** Tags suggested while [word] is typed, translated where Pixiv has it; nothing is asked for a blank word. */
    suspend fun autocomplete(word: String): List<PixivTrendTag> {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return emptyList()
        val json = getJson("/v2/search/autocomplete", mapOf("word" to trimmed, "merge_plain_keyword_results" to "true"))
        return json["tags"].list.mapNotNull { tag ->
            tag["name"].string?.ifEmpty { null }?.let { PixivTrendTag(it, tag["translated_name"].string) }
        }
    }

    /**
     * [userId]'s bookmarked works, usually the reader's own: Pixiv's likes are
     * bookmarks, and the call 404s without the id. They keep R-18 and AI works,
     * saved on purpose. [tag] narrows them to one bookmark tag
     * ([PIXIV_UNCLASSIFIED_TAG] for untagged ones).
     */
    suspend fun bookmarks(userId: Long, restrict: String = "public", tag: String? = null, next: String? = null): Page<PixivIllust> =
        illustPage(getPage("/v1/user/bookmarks/illust", bookmarkQuery(userId, restrict, tag), next), ownList = true)

    /** One work's full record. */
    suspend fun illustDetail(illustId: Long): PixivIllust =
        PixivIllust.parse(getJson("/v1/illust/detail", mapOf("illust_id" to "$illustId"))["illust"])
            ?: throw unusableAnswer("no usable illust $illustId")

    /** An ugoira's frame archive and delays. */
    suspend fun ugoiraMetadata(illustId: Long): PixivUgoira =
        PixivUgoira.parse(getJson("/v1/ugoira/metadata", mapOf("illust_id" to "$illustId")))
            ?: throw unusableAnswer("no ugoira frames for $illustId")

    /** Works Pixiv relates to [illustId]; R-18 ones only with [includeR18], the reader's choice unless told. */
    suspend fun related(illustId: Long, next: String? = null, includeR18: Boolean? = null): Page<PixivIllust> = illustPage(
        getPage("/v2/illust/related", mapOf("illust_id" to "$illustId", "filter" to ANDROID), next),
        includeR18 = includeR18 ?: showR18,
    )

    /** Works related to [seed]. An R-18 seed's are R-18 themselves, and the reader opened it on purpose, so they stay. */
    suspend fun related(seed: PixivIllust, next: String? = null): Page<PixivIllust> =
        related(seed.id, next, includeR18 = seed.isR18 || showR18)

    /** One kind of [userId]'s works; [ownList] keeps all a creator posted, for their own profile. */
    suspend fun userIllusts(
        userId: Long,
        type: PixivWorkType = PixivWorkType.ILLUST,
        ownList: Boolean = false,
        next: String? = null,
    ): Page<PixivIllust> = illustPage(
        getPage("/v1/user/illusts", mapOf("user_id" to "$userId", "type" to type.api, "filter" to ANDROID), next),
        ownList = ownList,
    )

    internal fun bookmarkQuery(userId: Long, restrict: String, tag: String?): Map<String, String> = buildMap {
        put("user_id", "$userId")
        put("restrict", restrict)
        if (!tag.isNullOrEmpty()) put("tag", tag)
        put("filter", ANDROID)
    }
}

/**
 * The `Accept-Language` Pixiv localises tags for, from a locale such as `ja`,
 * `zh_Hant` or `pt-BR`. Pixiv speaks five languages; every other locale reads
 * English rather than Pixiv's Japanese default.
 */
fun pixivAcceptLanguage(locale: String): String = when (val language = pixivLanguageOf(locale)) {
    PixivLanguage.TRADITIONAL_CHINESE -> "zh-TW"
    PixivLanguage.SIMPLIFIED_CHINESE -> "zh-CN"
    else -> language.code
}

/** The languages Pixiv and Pixivision write in. */
internal enum class PixivLanguage(val code: String) {
    JAPANESE("ja"),
    KOREAN("ko"),
    TRADITIONAL_CHINESE("zh-tw"),
    SIMPLIFIED_CHINESE("zh"),
    ENGLISH("en"),
}

private val traditionalChinese = setOf("hant", "tw", "hk", "mo")

/** The language of [locale] Pixiv writes in, English for the ones it does not. */
internal fun pixivLanguageOf(locale: String): PixivLanguage {
    val parts = locale.lowercase().split('-', '_')
    return when (parts.first()) {
        "ja" -> PixivLanguage.JAPANESE
        "ko" -> PixivLanguage.KOREAN
        "zh" -> if (parts.drop(1).any { it in traditionalChinese }) PixivLanguage.TRADITIONAL_CHINESE else PixivLanguage.SIMPLIFIED_CHINESE
        else -> PixivLanguage.ENGLISH
    }
}

private val clientTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'+00:00'").withZone(ZoneOffset.UTC)

/** The time as the official app writes it, which the hash is of: whole seconds, and `+00:00` rather than `Z`. */
internal fun pixivClientTime(epochMillis: Long): String = clientTime.format(Instant.ofEpochMilli(epochMillis))

internal fun pixivClientHash(time: String): String =
    MessageDigest.getInstance("MD5").digest((time + CLIENT_HASH_SALT).toByteArray()).joinToString("") { "%02x".format(it) }

/** An app-API path, or an absolute https address as Pixiv's `next_url` writes it, with [query] in place of its own; null for anything else. */
internal fun pixivUrl(path: String, query: Map<String, String> = emptyMap()): HttpUrl? {
    val full = when {
        path.startsWith("https://") -> path
        path.startsWith("/") -> "$API_BASE$path"
        else -> return null
    }
    val url = full.toHttpUrlOrNull() ?: return null
    if (query.isEmpty()) return url
    return url.newBuilder().query(null).apply { query.forEach { (name, value) -> addQueryParameter(name, value) } }.build()
}

/**
 * Whether [json] is an answer at all: Pixiv answers with JSON objects, and the
 * lenient parser would take a maintenance page for a bare string.
 */
private fun isAnswer(json: J): Boolean = json.raw is JsonObject || json.raw is JsonArray

/** Where a list goes on: its `next_url`, when that is an address the client can ask. */
internal fun nextOf(json: J): String? = PixivText.nonEmpty(json["next_url"].string)?.takeIf { pixivUrl(it) != null }

private fun formOf(fields: List<Pair<String, String>>): String =
    fields.joinToString("&") { (name, value) -> "${URLEncoder.encode(name, Charsets.UTF_8)}=${URLEncoder.encode(value, Charsets.UTF_8)}" }
