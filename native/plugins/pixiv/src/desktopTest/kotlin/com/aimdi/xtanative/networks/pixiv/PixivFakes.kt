package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import java.net.URLDecoder
import okhttp3.HttpUrl

/** One request a [FakePixivHttp] was asked. */
internal class Asked(
    val method: String,
    val url: HttpUrl,
    val headers: Map<String, String>,
    val body: String? = null,
    val contentType: String? = null,
    val followRedirects: Boolean = true,
) {
    val query: Map<String, String> get() = url.queryParameterNames.associateWith { url.queryParameter(it).orEmpty() }

    /** A POST's form fields, decoded. */
    val form: Map<String, String>
        get() = body.orEmpty().split('&').filter { it.isNotEmpty() }.associate { field ->
            decode(field.substringBefore('=')) to decode(field.substringAfter('=', ""))
        }

    val isTokenCall: Boolean get() = url.host == "oauth.secure.pixiv.net"

    private fun decode(text: String) = URLDecoder.decode(text, Charsets.UTF_8)
}

/** Pixiv as tests play it: every request is kept, and [answer] says what comes back. */
internal class FakePixivHttp(private val answer: suspend (Asked) -> XResponse) : XHttp {
    val asked = mutableListOf<Asked>()

    val reads: List<Asked> get() = asked.filterNot { it.isTokenCall }
    val tokenCalls: List<Asked> get() = asked.filter { it.isTokenCall }

    override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse =
        record(Asked("GET", url, headers, followRedirects = followRedirects))

    override suspend fun post(url: HttpUrl, headers: Map<String, String>): XResponse = record(Asked("POST", url, headers))

    override suspend fun post(url: HttpUrl, headers: Map<String, String>, body: String, contentType: String): XResponse =
        record(Asked("POST", url, headers, body, contentType))

    private suspend fun record(request: Asked): XResponse {
        asked += request
        return answer(request)
    }
}

internal object Fixtures {
    fun text(name: String): String = javaClass.getResource("/pixiv/$name")!!.readText()

    fun json(name: String): J = J.parse(text(name))
}

/** A fixed now: 2026-08-06T01:02:03Z. */
internal const val NOW = 1_785_978_123_000L

internal fun answer(body: String, code: Int = 200) = XResponse(code, body)

/** A token endpoint answer for [access]; [user] null leaves the user out. */
internal fun tokenAnswer(
    access: String,
    refresh: String = "refresh-next",
    user: String? = """{"id": "7", "name": "Reader", "account": "reader"}""",
): XResponse = answer(
    """{"access_token": "$access", "refresh_token": "$refresh", "expires_in": 3600${if (user == null) "" else ", \"user\": $user"}}""",
)

/** A reader signed in as [userId] with a still-valid access token. */
internal fun signedIn(userId: Long = 77, access: String = "access-1") = MemoryPixivCredentials(
    PixivCredentials(refreshToken = "refresh-me", accessToken = access, accessExpiresAt = NOW + 3_600_000, userId = userId),
)

internal fun pixiv(
    http: XHttp,
    credentials: PixivCredentialStore = signedIn(),
    filters: PixivFilters = PixivFilters(),
    locale: String = "en",
) = PixivClient(http, credentials, filters = { filters }, locale = { locale }, clock = { NOW })

/** A minimal work, as list fixtures write them. */
internal fun workJson(id: Long, r18: Boolean = false, ai: Boolean = false, type: String = "illust", userId: Long = 2): String =
    """{"id": $id, "title": "Work $id", "type": "$type", "image_urls": {"medium": "https://i.pximg.net/c/540x540_70/img-master/$id.jpg"},
       "user": {"id": $userId, "name": "A", "account": "a"}, "page_count": 1, "x_restrict": ${if (r18) 1 else 0},
       "sanity_level": 2, "illust_ai_type": ${if (ai) 2 else 1}}"""

internal fun illustsJson(vararg works: String, next: String? = null): String =
    """{"illusts": [${works.joinToString()}], "next_url": ${next?.let { "\"$it\"" } ?: "null"}}"""

/** A minimal novel, as list fixtures write them. */
internal fun novelJson(id: Long, xRestrict: Int = 0, aiType: Int = 1): String =
    """{"id": $id, "title": "Novel $id", "user": {"id": 2, "name": "A", "account": "a", "profile_image_urls": {}},
       "x_restrict": $xRestrict, "novel_ai_type": $aiType}"""

internal fun novelsJson(vararg novels: String, next: String? = null): String =
    """{"novels": [${novels.joinToString()}], "next_url": ${next?.let { "\"$it\"" } ?: "null"}}"""
