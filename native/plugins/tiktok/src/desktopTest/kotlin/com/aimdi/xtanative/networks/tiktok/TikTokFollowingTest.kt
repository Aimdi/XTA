package com.aimdi.xtanative.networks.tiktok

import java.io.IOException
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class TikTokFollowingTest {
    private var now = 1_000_000L
    private val asked = CopyOnWriteArrayList<String>()

    private fun post(id: String, account: String, second: Long) =
        TikTokPost(id = id, desc = "", createdAt = Instant.ofEpochSecond(second), author = TikTokAuthor(account, account))

    private fun following(failing: Set<String> = emptySet(), maxFetches: Int = 20, perAccount: Int = 15) = TikTokFollowing(
        read = { secUid ->
            asked += secUid
            if (secUid in failing) throw IOException("offline")
            listOf(post("$secUid-new", secUid, 200), post("$secUid-old", secUid, 100))
        },
        clock = { now },
        perAccount = perAccount,
        maxFetches = maxFetches,
    )

    @Test
    fun theAccountsPagesAreMergedNewestFirst(): Unit = runBlocking {
        val posts = following(perAccount = 1).timeline(listOf("a", "b"))
        assertEquals(setOf("a-new", "b-new"), posts.map { it.id }.toSet(), "one of each account")
        assertTrue(posts.all { it.createdAt == Instant.ofEpochSecond(200) })
    }

    @Test
    fun aPageJustReadIsReusedUnlessTheReadIsForced(): Unit = runBlocking {
        val following = following()
        following.timeline(listOf("a"))
        following.timeline(listOf("a"))
        assertEquals(listOf("a"), asked)

        following.timeline(listOf("a"), force = true)
        now += TikTokFollowing.TTL_MILLIS + 1
        following.timeline(listOf("a"))
        assertEquals(listOf("a", "a", "a"), asked)
    }

    @Test
    fun aReadAsksAtMostItsBudgetAndTheRestComeNext(): Unit = runBlocking {
        val following = following(maxFetches = 2)
        following.timeline(listOf("a", "b", "c"))
        assertEquals(setOf("a", "b"), asked.toSet())
        asked.clear()
        now += TikTokFollowing.TTL_MILLIS + 1
        following.timeline(listOf("a", "b", "c"), force = true)
        assertTrue("c" in asked, "the account never asked goes first: $asked")
    }

    @Test
    fun oneAccountFailingKeepsTheOthersAndOnlyNothingAtAllFails(): Unit = runBlocking {
        val posts = following(failing = setOf("b")).timeline(listOf("a", "b"))
        assertEquals(listOf("a-new", "a-old"), posts.map { it.id })
        assertFailsWith<IOException> { following(failing = setOf("a")).timeline(listOf("a")) }
    }

    @Test
    fun theTimelineGrowsAsAccountsAnswer(): Unit = runBlocking {
        val seen = CopyOnWriteArrayList<Int>()
        following().timeline(listOf("a", "b")) { seen += it.size }
        assertEquals(2, seen.size)
        assertEquals(4, seen.max())
    }
}
