package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import java.io.IOException
import kotlin.test.Test
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * How the paths a signed-in reader's own lists are tried at answer a
 * guest, for the next round to read in CI's log (`LIVE Threads signed in
 * …`): the Following feed ([ThreadsOwnFeeds]), the saved and liked posts,
 * and the activity ([ThreadsActivity]). A real session path sends a guest
 * to sign in (302), as the replies' and the current user's do (the
 * controls); a wrong one is a 404. threads.com's own pages for them
 * (`/activity`, `/saved`, `/liked`, `/following`) are asked too, for where
 * they send a guest. Status codes and where a redirect leads only; no
 * session is sent and nothing is read of an answer's words. It never fails
 * on what it finds. Skipped unless RUN_LIVE=true.
 */
class ThreadsSignedInLiveTest {
    @Test
    fun howTheSignedInReadersOwnPathsAnswerAGuest(): Unit = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live threads.com")
        val http = OkHttpXHttp()
        val media = try {
            ThreadsDirectClient(http).guestAccount("zuck").items.firstNotNullOfOrNull(ThreadsPeople::mediaId)
        } catch (e: Exception) {
            println("LIVE Threads signed in: no post on @zuck's page (${e.javaClass.simpleName})")
            null
        }
        val controls = listOfNotNull(
            media?.let { "control" to "/api/v1/text_feed/$it/replies/" },
            "control" to "/api/v1/accounts/current_user/",
        )
        val asked = controls +
            ThreadsOwnFeed.entries.flatMap { feed -> ThreadsOwnFeeds.paths(feed).map { feed.name.lowercase() to it } } +
            ThreadsActivity.PATHS.map { "activity" to it }
        asked.forEachIndexed { index, (list, path) -> answer(http, "api $list path ${index + 1}", ThreadsLinks.web(path, emptyList()), JSON) }
        for (page in PAGES) answer(http, "page /$page", ThreadsLinks.web("/$page"), HTML)
    }

    /** How [url] answers a guest: its status, and where a redirect sends them (host and path, a post's code hidden). */
    private suspend fun answer(http: OkHttpXHttp, label: String, url: HttpUrl, headers: Map<String, String>) {
        delay(GAP_MILLIS)
        val told = try {
            val response = http.get(url, headers, followRedirects = false)
            val sent = response.header("Location")?.let(url::resolve)?.let { " -> ${it.host}${it.encodedPath}" }.orEmpty()
            "${response.code}$sent"
        } catch (e: IOException) {
            "unreachable (${e.javaClass.simpleName})"
        }
        println("LIVE Threads signed in $label (${url.encodedPath.replace(DIGITS, "<id>")}) as a guest: $told")
    }

    private companion object {
        val PAGES = listOf("activity", "saved", "liked", "following")
        const val GAP_MILLIS = 2_500L
        val DIGITS = Regex("\\d{4,}")
        val HTML = mapOf(
            "User-Agent" to ThreadsRepairs().webUserAgent,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9",
        )
        val JSON = mapOf("User-Agent" to ThreadsRepairs().webUserAgent, "Accept" to "application/json", "X-IG-App-ID" to ThreadsRepairs().appId)
    }
}
