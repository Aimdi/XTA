package com.aimdi.xtanative.networks.ehviewer

import com.aimdi.xtanative.networks.Page
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** XTA's EhFeedStore, which the home tabs and every search read their lists through. */
class EhFeedTest {
    private fun gallery(gid: Long) = EhGallery(gid = gid, token = "t$gid", title = "G$gid")

    private val EhFeed.gids get() = state.value.galleries.map { it.gid }

    @Test
    fun refreshReadsTheFirstPageAndLoadMoreFollowsEachNextLinkToTheLast() = runBlocking<Unit> {
        val asked = mutableListOf<String?>()
        val feed = EhFeed { next ->
            asked += next
            when (next) {
                null -> Page(listOf(gallery(1), gallery(2)), "https://e-hentai.org/?next=2")
                else -> Page(listOf(gallery(3)), null)
            }
        }

        feed.refresh()
        assertEquals(listOf(1L, 2L), feed.gids)
        assertTrue(feed.state.value.hasMore)

        feed.loadMore()
        assertEquals(listOf(1L, 2L, 3L), feed.gids)
        assertFalse(feed.state.value.hasMore)

        feed.loadMore()
        assertEquals(listOf(null, "https://e-hentai.org/?next=2"), asked, "nothing is read past the last page")
    }

    @Test
    fun aFailedLoadMoreKeepsWhatIsShownAndIsTriedAgain() = runBlocking<Unit> {
        var failing = true
        val feed = EhFeed { next ->
            when {
                next == null -> Page(listOf(gallery(1)), "n2")
                failing -> throw IOException("dropped")
                else -> Page(listOf(gallery(2)), null)
            }
        }
        feed.refresh()

        feed.loadMore()
        assertEquals(listOf(1L), feed.gids)
        assertFalse(feed.state.value.loadingMore)
        assertNull(feed.state.value.error)

        failing = false
        feed.loadMore()
        assertEquals(listOf(1L, 2L), feed.gids)
    }

    @Test
    fun aFailedRefreshKeepsItsErrorUntilOneSucceeds() = runBlocking<Unit> {
        var failing = true
        val feed = EhFeed { if (failing) throw EhException(EhErrorKind.BANNED, "banned") else Page(listOf(gallery(1)), null) }

        feed.refresh()
        assertEquals(EhErrorKind.BANNED, EhErrorKind.of(assertIs<EhException>(feed.state.value.error)))
        assertFalse(feed.state.value.loading)
        feed.loadMore()
        assertTrue(feed.gids.isEmpty(), "without a first page there is no next one")

        failing = false
        feed.refresh()
        assertNull(feed.state.value.error)
        assertEquals(listOf(1L), feed.gids)
    }
}
