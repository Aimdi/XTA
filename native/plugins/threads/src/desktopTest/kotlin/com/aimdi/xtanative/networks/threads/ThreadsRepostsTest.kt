package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.XResponse
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Reposts as a guest is shown them: a profile's own page carries none (CI's
 * probe saw them under `/@handle/reposts`, as `share_info.reposted_post`),
 * so its Reposts tab and Home read that page; Home shows a post once,
 * whoever brought it.
 */
class ThreadsRepostsTest {
    private val time = FakeTime()

    private fun repost(pk: String, by: String, of: String, author: String, at: Long) = postJson(
        pk,
        user = by,
        text = "",
        takenAt = at,
        info = mapOf("share_info" to mapOf("reposted_post" to postJson(of, user = author, takenAt = at - 3_600))),
    )

    private val repostsPage = page(
        listOf(
            listOf(repost("10", by = "zuck", of = "2", author = "meta", at = 1_767_200_000)),
            listOf(postJson("3", user = "zuck")),
            listOf(repost("11", by = "someone", of = "4", author = "meta", at = 1_767_100_000)),
        ),
    )

    @Test
    fun aRepostsPageGivesWhatTheProfileRepostedAsTheOriginalsByThem() {
        val read = ThreadsPages.reposts(ThreadsPages.chains(repostsPage), "zuck")
        assertEquals(listOf("10"), read.map { it.id })
        assertEquals("meta", read.single().handle)
        assertEquals("zuck", read.single().repostedByHandle)
        assertEquals(Instant.ofEpochSecond(1_767_200_000), read.single().publishedAt, "dated when it was reposted")
    }

    @Test
    fun theClientReadsTheProfilesRepostsPage() = runBlocking {
        val meta = FakeMeta { ok(repostsPage) }
        val read = direct(meta, time = time).guestReposts("ZUCK")
        assertEquals("/@zuck/reposts", meta.asked.single().path)
        assertEquals(listOf("10"), read.items.map { it.id })
    }

    @Test
    fun anOriginalAndItsRepostAreOneCardTheNewest() {
        val original = ThreadsParser.post(j(postJson("2", user = "meta", takenAt = 1_767_196_400)))!!
        val shared = ThreadsPages.reposts(ThreadsPages.chains(repostsPage), "zuck").single()
        val other = ThreadsParser.post(j(postJson("5", user = "ola", takenAt = 1_767_000_000)))!!
        val mixed = ThreadsFeedMix.merge(listOf(original, other), listOf(shared))
        assertEquals(listOf("10", "5"), mixed.map { it.id })
        assertTrue(mixed.first().isRepost)
    }

    @Test
    fun homesExtrasReadAFewPagesPerLoadAndReuseThemForHalfAnHour() = runBlocking {
        val meta = FakeMeta { request ->
            val handle = request.path.removePrefix("/@").removeSuffix("/reposts")
            ok(page(listOf(listOf(repost("r-$handle", by = handle, of = "o-$handle", author = "meta", at = 1_767_200_000)))))
        }
        val reader = ThreadsReader(direct(meta, time = time), ThreadsRssHub(meta), ThreadsXy(meta), clock = time.clock)
        val handles = listOf("a", "b", "c", "d", "e")
        val first = reader.extras(handles)
        assertEquals(ThreadsReader.EXTRAS_PER_LOAD, meta.asked.size)
        assertEquals(ThreadsReader.EXTRAS_PER_LOAD, first.size)
        reader.extras(handles)
        assertEquals(handles.size, meta.asked.size, "the next load reads the pages not read yet")
        assertEquals(handles.size, reader.heldExtras(handles).size)
        reader.extras(handles)
        assertEquals(handles.size, meta.asked.size, "within half an hour nothing is asked again")
    }

    @Test
    fun aFailedRepostsPageCostsHomeNothing() = runBlocking {
        val meta = FakeMeta { XResponse(500, "down") }
        val reader = ThreadsReader(direct(meta, time = time), ThreadsRssHub(meta), ThreadsXy(meta), clock = time.clock)
        assertEquals(emptyList(), reader.extras(listOf("a")))
        assertEquals(emptyList(), reader.repostsPage("a").items)
    }
}
