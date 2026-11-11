package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Live threads.com's front page as a logged-out browser gets it: the For you
 * page a reader without a session is shown. Skipped unless RUN_LIVE=true, and
 * when Threads refuses the read or shows a guest its sign-in page: either is
 * Meta's to decide, and the screen has a state for each. No session is used.
 */
class ThreadsForYouLiveTest {
    @Test
    fun aGuestIsShownThreadsRecommendedPosts(): Unit = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live threads.com")
        val forYou = ThreadsForYou(ThreadsDirectClient(OkHttpXHttp()))
        val page = try {
            forYou.page()
        } catch (e: ThreadsException) {
            assumeTrue(false, "Threads refused the front page: ${e.failure} ${e.code}")
            return@runBlocking
        } catch (e: IOException) {
            assumeTrue(false, "threads.com could not be reached: ${e.javaClass.simpleName}")
            return@runBlocking
        }
        assumeTrue(page.posts.isNotEmpty(), "Threads showed a guest no For you page (its sign-in page instead)")

        assertTrue(page.source == ThreadsForYouSource.GUEST)
        assertTrue(page.posts.all { it.handle.isNotBlank() }, "each recommended post names its author")
        assertTrue(page.posts.any { it.url != null }, "and links to itself")
        val pictures = page.posts.flatMap { it.media }
        assertTrue(pictures.all { it.images.firstOrNull()?.url == it.url }, "a picture's own address is its largest size")
        val sized = pictures.flatMap { it.images }.count { it.width != null }
        println("LIVE Threads For you (guest): ${page.posts.size} posts, ${pictures.size} pictures, ${pictures.sumOf { it.images.size }} sizes ($sized sized)")
    }
}
