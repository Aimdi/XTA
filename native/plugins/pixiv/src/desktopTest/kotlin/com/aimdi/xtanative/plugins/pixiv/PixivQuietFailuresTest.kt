package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.pixiv.FakePixivHttp
import com.aimdi.xtanative.networks.pixiv.answer
import com.aimdi.xtanative.networks.pixiv.illustsJson
import com.aimdi.xtanative.networks.pixiv.workJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Failures the reader used not to hear of: a pull that kept stale content, a creator's works that did not load, a cancellation taken for a failure. */
class PixivQuietFailuresTest {
    @Test
    fun aPullThatFailsKeepsTheListAndSaysWhy(): Unit = runBlocking {
        var failing = false
        val list = PixivList<Int>(CoroutineScope(coroutineContext), { it }, { if (failing) error("broken") else Page(listOf(1, 2), null) })
        list.refresh()
        withTimeout(5_000) { list.state.first { it.loaded } }
        val said = CompletableDeferred<Throwable>()
        val listening = launch(start = CoroutineStart.UNDISPATCHED) { said.complete(list.refreshFailures.first()) }
        failing = true
        list.refresh()
        assertEquals("broken", withTimeout(5_000) { said.await() }.message)
        assertEquals(listOf(1, 2), list.state.value.items)
        assertNull(list.state.value.error, "the list itself stays as it was")
        listening.cancel()
    }

    @Test
    fun catchingFailuresKeepsACancellationACancellation() {
        assertTrue(catchingFailures { error("x") }.isFailure)
        assertEquals(3, catchingFailures { 3 }.getOrNull())
        assertFailsWith<CancellationException> { catchingFailures<Int> { throw CancellationException("left") } }
    }

    @Test
    fun theCreatorsOtherWorksThatFailSayWhyAndRetry() {
        var failing = true
        val http = FakePixivHttp { asked ->
            when (asked.url.encodedPath) {
                "/v1/illust/detail" -> answer("""{"illust": ${workJson(5, userId = 9)}}""")
                "/v1/user/illusts" -> if (failing) answer("{}", 500) else answer(illustsJson(workJson(5, userId = 9), workJson(6, userId = 9)))
                else -> answer(illustsJson())
            }
        }
        withRuntime(PixivTestHost(signedInSettings(), http)) { runtime ->
            val model = PixivWorkModel(runtime, runtime.scope, 5, null)
            withTimeout(5_000) { model.state.first { it.authorWorksError != null } }
            assertTrue(model.state.value.authorWorks.isEmpty())
            failing = false
            model.retryAuthorWorks()
            val shown = withTimeout(5_000) { model.state.first { it.authorWorks.isNotEmpty() } }
            assertEquals(listOf(6L), shown.authorWorks.map { it.id }, "the work itself is not among its creator's others")
            assertNull(shown.authorWorksError)
            assertNotNull(shown.illust)
        }
    }
}
