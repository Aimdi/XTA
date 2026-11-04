package com.aimdi.xtanative.networks.hackernews

import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl

/** The screens' readers: Following over the accounts followed here, a thread, a user's page. */
class HnReadersTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun stop() = scope.cancel()

    private fun fixture(name: String) = javaClass.getResource("/hackernews/$name")!!.readText()

    private class Fake(val answer: (HttpUrl) -> XResponse) : XHttp {
        override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse = answer(url)
    }

    private fun <T> HnReader<T>.readNow(): HnReader.State<T> = runBlocking {
        refresh()
        withTimeout(5_000) { state.first { it.loaded } }
    }

    @Test
    fun followingKeepsTheAccountsThatAnswered() {
        val fake = Fake { url -> if ("author_broken" in url.queryParameter("tags").orEmpty()) XResponse(500, "") else XResponse(200, fixture("front_page.json")) }
        val read = HnFollowingReader(HackerNewsClient(fake), { listOf("pg", "broken") }, scope).readNow()
        assertTrue(read.value.orEmpty().isNotEmpty())
        assertEquals(null, read.error)
    }

    @Test
    fun followingFailsOnlyWhenNoAccountAnswered() {
        val read = HnFollowingReader(HackerNewsClient(Fake { XResponse(503, "") }), { listOf("pg", "dang") }, scope).readNow()
        assertNotNull(read.error)
        assertEquals(null, read.value)
    }

    @Test
    fun followingNobodyReadsNothing() {
        val read = HnFollowingReader(HackerNewsClient(Fake { error("nothing should be asked") }), { emptyList() }, scope).readNow()
        assertEquals(emptyList(), read.value)
    }

    @Test
    fun aThreadShowsItsStoryUntilItsCommentsArrive() {
        val seed = HnStory(8863, "My YC app")
        val reader = HnThreadReader(HackerNewsClient(Fake { XResponse(200, fixture("thread.json")) }), 8863, seed, scope)
        assertEquals(seed, reader.state.value.value?.story)
        val read = reader.readNow()
        assertTrue(read.value!!.comments.isNotEmpty())
    }

    @Test
    fun aUsersPageReadsTheAccountAndItsStories() {
        val fake = Fake { url -> XResponse(200, fixture(if (url.host == HackerNewsClient.FIREBASE) "user_pg.json" else "front_page.json")) }
        val read = HnUserReader(HackerNewsClient(fake), "pg", scope).readNow()
        assertEquals("pg", read.value?.user?.id)
        assertTrue(read.value!!.stories.isNotEmpty())
    }
}
