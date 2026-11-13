package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.core.x.http.XResponse
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Live threads.com, as XTA's test/live/sources_smoke_test.dart reads it
 * without an account: a public profile's card, its threads, then the first
 * thread's conversation. Skipped unless RUN_LIVE=true.
 */
class ThreadsLiveTest {
    @Test
    fun aPublicProfileAndItsThreadsReadAsAGuest() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live threads.com")
        val client = ThreadsDirectClient(OkHttpXHttp())

        val profile = client.guestProfile("zuck")
        assertEquals("zuck", profile.username)
        assertTrue(profile.fullName.isNotBlank(), "the page names the profile")

        val threads = client.guestAccount("zuck").items
        assertTrue(threads.isNotEmpty(), "a guest is shown @zuck's threads")
        assertTrue(threads.any { it.handle == "zuck" || it.repostedByHandle == "zuck" })

        val conversation = client.postThread(threads.firstNotNullOf { it.url })
        assertTrue(conversation.isNotEmpty(), "a guest is shown a thread's page")
        println("LIVE Threads @zuck: ${profile.followerCount} followers, ${threads.size} threads, a conversation of ${conversation.size}")
    }

    /**
     * What Threads shows a guest, by shape only, for the next round to read
     * in CI's log: each page's answer (or where it sends a guest), and on the
     * pages that answer, the post and user keys the plugin does not read
     * yet, the features a post carries (polls, GIFs, quotes, carousels,
     * audio) by key path, and any key that names a next page. Key names,
     * paths and counts only; nothing a post or profile says. It never fails
     * on what it finds, only on a crash of its own.
     */
    @Test
    fun theShapeOfWhatAGuestIsShown(): Unit = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live threads.com")
        val http = OkHttpXHttp()
        val pages = listOf("@threads", "@zuck", "@instagram", "@zuck/replies", "search?q=threads", "search?q=threads&serp_type=tags", "")
        val bodies = pages.associateWith { probe(http, it) }
        val post = bodies["@zuck"]?.let { ThreadsPages.chains(it).flatten().firstNotNullOfOrNull { p -> p.url } }
        if (post == null) println("LIVE Threads shape post: no post link on @zuck's page") else probe(http, post.substringAfter("threads.com/"), "post")
    }

    /** One page's answer and shape, logged; its body when it answered 200. */
    private suspend fun probe(http: OkHttpXHttp, path: String, label: String = path.ifEmpty { "front" }): String? {
        delay(PROBE_GAP_MILLIS)
        val url = ThreadsLinks.web("/").resolve(path)!!
        val response = try {
            http.get(url, guestHeaders(), followRedirects = false)
        } catch (e: IOException) {
            println("LIVE Threads shape $label: unreachable (${e.javaClass.simpleName})")
            return null
        }
        if (response.code != 200) {
            println("LIVE Threads shape $label: ${response.code}${sentTo(url, response)}")
            return null
        }
        logShape(label, response.body)
        return response.body
    }

    private fun logShape(label: String, html: String) {
        val roots = ThreadsShape.scripts(html)
        val posts = ThreadsShape.posts(roots)
        val infos = posts.mapNotNull { it["text_post_app_info"] as? JsonObject }
        val chains = ThreadsPages.chains(html)
        println("LIVE Threads shape $label: 200, ${html.length} chars, ${roots.size} scripts, ${chains.size} chains, ${chains.sumOf { it.size }} posts read, ${posts.size} post objects")
        println("LIVE Threads shape $label post keys unread: ${ThreadsShape.told(ThreadsShape.keyCounts(posts).filterKeys { it !in ThreadsShape.READ_POST })}")
        println("LIVE Threads shape $label text_post_app_info keys unread: ${ThreadsShape.told(ThreadsShape.keyCounts(infos).filterKeys { it !in ThreadsShape.READ_INFO })}")
        println("LIVE Threads shape $label features: ${ThreadsShape.told(ThreadsShape.paths(posts, ThreadsShape.FEATURES, depth = 5))}")
        println("LIVE Threads shape $label user keys: ${ThreadsShape.told(ThreadsShape.keyCounts(ThreadsShape.users(roots)), limit = 60)}")
        println("LIVE Threads shape $label queries: ${ThreadsShape.queries(html).joinToString(", ").ifEmpty { "none" }}")
        println("LIVE Threads shape $label paging: ${ThreadsShape.told(ThreadsShape.paths(roots, ThreadsShape.PAGING, depth = 40))}")
    }

    /** Where a redirect sends a guest, by path alone. */
    private fun sentTo(url: HttpUrl, response: XResponse): String =
        response.header("Location")?.let(url::resolve)?.let { " -> ${it.host}${it.encodedPath}" }.orEmpty()

    private fun guestHeaders() = mapOf(
        "User-Agent" to ThreadsRepairs().webUserAgent,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9",
    )

    private companion object {
        const val PROBE_GAP_MILLIS = 2_500L
    }
}
