package com.aimdi.xtanative.networks.instagram

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Live instagram.com as a guest, as XTA's
 * test/live/instagram_search_smoke_test.dart reads it. Instagram answers
 * datacenter addresses with 429 or its login wall, so the test only asks
 * that the answer is either @instagram's profile or one of those refusals,
 * never a crash. Each route the client tried is logged with what it
 * answered (status, type and a short reason, the names of the cookies it
 * set, never their values), so a run that fails names its cause. Skipped
 * unless RUN_LIVE=true.
 */
class InstagramLiveTest {
    @Test
    fun aGuestProfileIsReadOrInstagramsBlockIsNamed() = runBlocking<Unit> {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live instagram.com")
        val routes = RouteLog(OkHttpXHttp())
        val client = InstagramClient(routes)
        try {
            val profile = client.profile("instagram")
            routes.print()
            assertEquals("instagram", profile.username.lowercase())
            assertTrue(profile.id.isNotEmpty())
            println("LIVE @instagram ${profile.displayName}, ${profile.followerCount} followers")
        } catch (refused: NetworkRequestException) {
            routes.print()
            assertTrue(refused.code in setOf(401, 403, 429), "Instagram refused with ${refused.code}: ${refused.message}; ${routes.summary()}")
            println("LIVE Instagram refused a guest: ${refused.code}")
        } catch (offline: IOException) {
            routes.print()
            println("LIVE Instagram could not be reached: ${offline.message}")
        }
    }

    @Test
    fun aRouteIsDescribedWithoutWhatItSet(): Unit = runBlocking {
        val answers = listOf(
            XResponse(200, "<html><head><title>Instagram</title></head></html>", mapOf("Content-Type" to listOf("text/html"), "Set-Cookie" to listOf("csrftoken=SECRET; Path=/", "mid=OTHER; Path=/"))),
            XResponse(404, """{"message":"useragent mismatch","status":"fail"}""", mapOf("Content-Type" to listOf("application/json"))),
        ).iterator()
        val routes = RouteLog(
            object : XHttp {
                override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse = answers.next()
            },
        )

        val failure = runCatching { InstagramClient(routes).profile("instagram") }.exceptionOrNull()

        assertTrue(failure is NetworkRequestException)
        val lines = routes.lines()
        assertEquals("GET www.instagram.com/ → 200 text/html \"Instagram\", sets csrftoken, mid", lines[0])
        assertEquals("GET i.instagram.com/api/v1/users/web_profile_info/ → 404 application/json \"useragent mismatch\"", lines[1])
        assertTrue(lines.none { "SECRET" in it || "OTHER" in it }, "a cookie's value is never logged")
    }
}

/**
 * [http] noting each route asked and what it answered, as a person reading a
 * failed live run needs it: the host and path (never the query, which names
 * whom was read), the status, the content type, the JSON's own message or the
 * page's title, and the names of the cookies the answer set.
 */
private class RouteLog(private val http: XHttp) : XHttp {
    private val seen = CopyOnWriteArrayList<String>()

    override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse =
        http.get(url, headers, followRedirects).also { seen += describe(url, it) }

    fun lines(): List<String> = seen.toList()

    fun summary(): String = "routes tried: " + seen.joinToString("; ").ifEmpty { "none" }

    fun print() = seen.forEach { println("LIVE Instagram route: $it") }

    private fun describe(url: HttpUrl, response: XResponse): String {
        val type = response.header("Content-Type")?.substringBefore(';')?.trim().orEmpty().ifEmpty { "no type" }
        val reason = reasonOf(response.body)?.let { " \"$it\"" }.orEmpty()
        val cookies = response.headers("Set-Cookie").mapNotNull { it.substringBefore('=').trim().ifEmpty { null } }
        val set = if (cookies.isEmpty()) "" else ", sets " + cookies.joinToString(", ")
        return "GET ${url.host}${url.encodedPath} → ${response.code} $type$reason$set"
    }

    /** The JSON's `message`, else its `status`, else the HTML page's title: a few words, never the body. */
    private fun reasonOf(body: String): String? {
        val json = J.parse(body)
        val said = json["message"].string ?: json["status"].string ?: title.find(body)?.groupValues?.get(1)
        return said?.replace(Regex("\\s+"), " ")?.trim()?.take(80)?.ifEmpty { null }
    }

    private companion object {
        val title = Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE)
    }
}
