package com.aimdi.xtanative.plugins.websources

import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.websources.WebSourceClient
import com.aimdi.xtanative.networks.websources.WebSourceFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl

/** A list's next page that failed says so at the list's foot, and is asked for again there at once. */
class WebSourceFeedTest {
    private val lemmy = WebSourceFixtures.source(WebSourceFixtures.posts())

    private class Sites(val answer: (Int) -> XResponse) : XHttp {
        var asked = 0

        override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse = answer(++asked)
    }

    @Test
    fun aFailedNextPageIsShownAndRetriedWithoutThePause(): Unit = runBlocking {
        val sites = Sites { asked ->
            when (asked) {
                1 -> XResponse(200, WebSourceFixtures.text("lemmy_page.json"))
                2 -> XResponse(503, "")
                else -> XResponse(200, """{"posts": [], "next_page": null}""")
            }
        }
        val feed = WebSourceFeed(WebSourceClient(sites, spacingMillis = 0), lemmy, lemmy.feeds.single(), this, clock = { 0L })

        feed.refresh()
        val first = feed.state.first { it.loaded && !it.loading }
        assertNotNull(first.next)

        feed.more()
        val failed = feed.state.first { it.moreError != null }
        assertFalse(failed.loadingMore)
        assertEquals(first.items, failed.items, "what is shown stays")

        feed.more()
        assertEquals(2, sites.asked, "a scroll waits out the pause")

        feed.retryMore()
        val after = feed.state.first { it.moreError == null && !it.loadingMore }
        assertEquals(3, sites.asked)
        assertNull(after.next)
    }
}
