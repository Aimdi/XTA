package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.pixiv.PIXIV_NETWORK
import com.aimdi.xtanative.networks.pixiv.PixivNoAccountException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** A first page read once for every surface showing the same list, a while; and what a failed read leaves in the app's log. */
class PixivFirstPagesTest {
    @Test
    fun aKeptPageIsGoneAfterItsTime() {
        var now = 0L
        val pages = PixivFirstPages(keepMillis = 1_000) { now }
        pages.put("walkthrough", Page(listOf(1, 2), "p2"))
        now = 900
        assertEquals(listOf(1, 2), pages.get<Int>("walkthrough")?.items)
        now = 1_100
        assertNull(pages.get<Int>("walkthrough"))
    }

    @Test
    fun anotherSurfaceShowsThePageReadWithoutAskingAgainAndAPullAsksAfresh(): Unit = runBlocking {
        val shared = PixivFirstPages()
        var asked = 0
        val read: suspend (String?) -> Page<Int> = { next ->
            asked++
            if (next == null) Page(listOf(asked, 10), "p2") else Page(listOf(20), null)
        }
        val scope = CoroutineScope(coroutineContext)
        val first = PixivList(scope, { it }, read).sharedThrough(shared) { "walkthrough" }
        first.ensure()
        withTimeout(5_000) { first.state.first { it.loaded } }
        val second = PixivList(scope, { it }, read).sharedThrough(shared) { "walkthrough" }
        second.ensure()
        assertEquals(listOf(1, 10), second.state.value.items, "shown at once, from the first surface's read")
        assertEquals(1, asked)
        second.more()
        withTimeout(5_000) { second.state.first { !it.hasMore && !it.loadingMore } }
        assertEquals(listOf(1, 10, 20), second.state.value.items, "its next page goes on from the kept one")
        second.refresh()
        withTimeout(5_000) { second.state.first { it.items.firstOrNull() == 3 } }
        val third = PixivList(scope, { it }, read).sharedThrough(shared) { "walkthrough" }
        third.ensure()
        assertEquals(listOf(3, 10), third.state.value.items, "a pull's page is the one the next surface shows")
    }

    @Test
    fun aListWithoutAKeyNowKeepsItsOwnPages(): Unit = runBlocking {
        val shared = PixivFirstPages()
        var asked = 0
        val scope = CoroutineScope(coroutineContext)
        val make = { PixivList(scope, { it }, { _: String? -> asked++; Page(listOf(1), null) }).sharedThrough(shared) { null } }
        make().apply { ensure(); withTimeout(5_000) { state.first { it.loaded } } }
        make().apply { ensure(); withTimeout(5_000) { state.first { it.loaded } } }
        assertEquals(2, asked)
    }

    @Test
    fun whatTheDeviceKeepsOffersNoRetryWhenEmpty() {
        val local = PixivLocalList<Int, Int>(CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), { it }) { it }
        assertFalse(local.list.offersRetry)
        assertTrue(PixivList<Int>(CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), { it }, { Page(emptyList(), null) }).offersRetry)
    }

    @Test
    fun aFailureIsLoggedByKindAndCodeWithoutItsWords() {
        val secret = NetworkRequestException(PIXIV_NETWORK, 403, "https://app-api.pixiv.net/v1/x?access_token=abc&word=private")
        val line = PixivLog.line("a list's first page", secret)
        assertEquals("XTA Native: pixiv: a list's first page failed: UNAUTHORIZED (NetworkRequestException 403)", line)
        assertFalse("abc" in line || "private" in line || "pixiv.net/v1" in line)
        assertEquals("XTA Native: pixiv: a read failed: NETWORK (IOException)", PixivLog.line("a read", IOException("refresh_token=xyz")))
        assertEquals("XTA Native: pixiv: a read failed: NOT_SIGNED_IN (PixivNoAccountException)", PixivLog.line("a read", PixivNoAccountException()))
    }

    @Test
    fun aFailedFirstPageReachesTheLog(): Unit = runBlocking {
        val lines = mutableListOf<String>()
        val before = PixivLog.write
        PixivLog.write = { lines += it }
        try {
            val list = PixivList<Int>(CoroutineScope(coroutineContext), { it }, { throw IOException("offline") })
            list.refresh()
            withTimeout(5_000) { list.state.first { it.error != null } }
        } finally {
            PixivLog.write = before
        }
        assertEquals(listOf("XTA Native: pixiv: a list's first page failed: NETWORK (IOException)"), lines)
    }
}
