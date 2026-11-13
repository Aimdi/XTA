package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.feed.Posts.ids
import com.aimdi.xtanative.core.feed.Posts.page
import com.aimdi.xtanative.core.feed.Posts.post
import com.aimdi.xtanative.core.feed.Posts.thread
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.model.XTimelinePage
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** XTA's home_account_filter_test and the For you merge, for the native port. */
class ForYouTest {
    private val one = XAccount("1", "one", emptyMap())
    private val two = XAccount("2", "two", emptyMap())

    private fun at(id: Long, day: Int, by: String = "u$id") = thread(post(id, by = by, at = Instant.parse("2024-%02d-01T00:00:00Z".format(day))))

    @Test
    fun theHomeFeedsNeverRunOutOfAccounts() {
        assertEquals(listOf("2"), HomeAccounts.enabled(listOf(one, two), setOf("1")).map { it.id })
        assertEquals(listOf("1", "2"), HomeAccounts.enabled(listOf(one, two), setOf("1", "2")).map { it.id })
        assertEquals(listOf("1", "2"), HomeAccounts.enabled(listOf(one, two), emptySet()).map { it.id })
        assertTrue(HomeAccounts.canDisable("1", listOf(one, two), emptySet()))
        assertFalse(HomeAccounts.canDisable("2", listOf(one, two), setOf("1")))
        assertFalse(HomeAccounts.canDisable("1", listOf(one), emptySet()))
    }

    @Test
    fun theTurnedOffIdsAreKeptAsXtaKeepsThem() {
        assertTrue(HomeAccounts.decodeIds(null).isEmpty())
        assertTrue(HomeAccounts.decodeIds("").isEmpty())
        assertEquals(listOf("a", "b"), HomeAccounts.decodeIds("""["a","b",""]"""))
        assertTrue(HomeAccounts.decodeIds("not-json").isEmpty())
        assertEquals(listOf("a", "b"), HomeAccounts.decodeIds(HomeAccounts.encodeIds(listOf("a", "b"))))
    }

    @Test
    fun timelinesMergeNewestFirstEachThreadOnce() {
        val merged = ForYou.merge(listOf(listOf(at(1, 1)), listOf(at(2, 6), at(1, 1))))
        assertEquals(listOf("2", "1"), ids(merged))
        val kept = at(3, 6, by = "other")
        assertEquals(listOf(kept), ForYou.dropFromAuthors(listOf(kept, at(4, 6, by = "off")), setOf("off")))
        assertEquals(listOf(kept), ForYou.dropFromAuthors(listOf(kept), emptySet()))
    }

    @Test
    fun cursorsReadBackAsWritten() {
        assertTrue(ForYou.decodeCursors(null).isEmpty())
        assertTrue(ForYou.decodeCursors("plain-cursor").isEmpty())
        assertEquals(mapOf("1" to "c1", "2" to "c2"), ForYou.decodeCursors(ForYou.encodeCursors(mapOf("1" to "c1", "2" to "c2"))))
        assertNull(ForYou.encodeCursors(emptyMap()))
    }

    private class Homes(val pages: Map<Pair<String, String?>, XTimelinePage>, val failing: Set<String> = emptySet()) : HomeReads {
        val asked = ArrayList<Pair<String, String?>>()

        override suspend fun home(account: XAccount, cursor: String?, count: Int): XTimelinePage {
            synchronized(asked) { asked += account.id to cursor }
            if (account.id in failing) throw IOException("offline")
            return pages[account.id to cursor] ?: page()
        }
    }

    @Test
    fun oneAccountPagesOnXsOwnCursor() = runBlocking {
        val homes = Homes(mapOf(("1" to null) to page(at(1, 1), next = "next")))
        val first = ForYou.page(listOf(one), emptySet(), null, homes)
        assertEquals(ForYouPage(listOf(at(1, 1)), "next"), first)
    }

    @Test
    fun severalAccountsPageOnEachOnesCursorAndAFailedOneKeepsItsPlace(): Unit = runBlocking {
        val homes = Homes(
            mapOf(
                ("1" to null) to page(at(1, 1), next = "1a"),
                ("2" to null) to page(at(2, 6), at(1, 1), next = "2a"),
                ("1" to "1a") to page(at(3, 1), next = null),
            ),
        )
        val first = ForYou.page(listOf(one, two), emptySet(), null, homes)
        assertEquals(listOf("2", "1"), ids(first.threads))
        assertEquals(mapOf("1" to "1a", "2" to "2a"), ForYou.decodeCursors(first.next))

        val failing = Homes(homes.pages, failing = setOf("2"))
        val second = ForYou.page(listOf(one, two), emptySet(), first.next, failing)
        assertEquals(listOf("3"), ids(second.threads))
        assertEquals(mapOf("2" to "2a"), ForYou.decodeCursors(second.next), "the account that ran out drops out; the failed one tries again")

        assertFailsWith<IOException> { ForYou.page(listOf(one, two), emptySet(), null, Homes(emptyMap(), failing = setOf("1", "2"))) }
    }

    @Test
    fun postsByTurnedOffAccountsAndHiddenGroupsStayOut() = runBlocking {
        val homes = Homes(mapOf(("2" to null) to page(at(1, 1, by = "1"), at(2, 1, by = "member"), at(3, 1, by = "x"), next = null)))
        val page = ForYou.page(listOf(one, two), setOf("1"), null, homes, excludedAuthorIds = setOf("member"))
        assertEquals(listOf("3"), ids(page.threads))
        assertEquals(listOf<Pair<String, String?>>("2" to null), homes.asked, "a turned-off account is not read")
    }
}
