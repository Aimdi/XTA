package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.FakePixivHttp
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.answer
import com.aimdi.xtanative.networks.pixiv.illustsJson
import com.aimdi.xtanative.networks.pixiv.workJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay

/** A pager's works: read only where the reader stops, kept only around it, their requests cancelled when they go. */
class PixivPagerWorksTest {
    private val works = (1L..10L).map { id -> PixivIllust(id = id, title = "Work $id", caption = "", type = "illust", thumbnailUrl = "https://i.pximg.net/$id.jpg", pageCount = 1, userId = 9, userName = "A", userAccount = "a") }
    private val ids = works.map { it.id }

    @Test
    fun aFlingReadsOnlyTheWorkItStopsOnAndKeepsOnlyItsNeighbours() {
        val http = FakePixivHttp { asked ->
            when (asked.url.encodedPath) {
                "/v1/illust/detail" -> answer("""{"illust": ${workJson(asked.query.getValue("illust_id").toLong(), userId = 9)}}""")
                else -> answer(illustsJson())
            }
        }
        withRuntime(PixivTestHost(signedInSettings(), http)) { runtime ->
            val parent = CoroutineScope(SupervisorJob())
            val pager = PixivPagerWorks(parent) { scope, work -> PixivWorkModel(runtime, scope, work.id, work, autoStart = false) }
            works.forEach { pager.model(it) }
            delay(100)
            assertTrue(http.reads.isEmpty(), "works flung past read nothing: ${http.reads.map { it.url }}")

            pager.settle(ids, 9)
            eventually { http.reads.map { it.url.encodedPath }.toSet() == setOf("/v1/illust/detail", "/v2/illust/related", "/v1/user/illusts") }
            assertEquals(setOf(8L, 9L, 10L), pager.heldIds)
            assertTrue(http.reads.all { it.query["illust_id"] in setOf(null, "10") }, "only the work on screen was read")
            parent.cancel()
        }
    }

    @Test
    fun aWorkLeftBehindHasItsRequestsCancelled() {
        val never = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val http = FakePixivHttp {
            try {
                never.await()
            } finally {
                cancelled.complete(Unit)
            }
            answer("{}")
        }
        withRuntime(PixivTestHost(signedInSettings(), http)) { runtime ->
            val parent = CoroutineScope(SupervisorJob())
            val pager = PixivPagerWorks(parent) { scope, work -> PixivWorkModel(runtime, scope, work.id, work, autoStart = false) }
            pager.model(works[0])
            pager.settle(ids, 0)
            eventually { http.reads.isNotEmpty() }
            (1..5).forEach { pager.model(works[it]) }
            pager.settle(ids, 5)
            kotlinx.coroutines.withTimeout(5_000) { cancelled.await() }
            assertEquals(setOf(4L, 5L, 6L), pager.heldIds, "the first work went with its request")
            parent.cancel()
        }
    }
}
