package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.store.StoreFile
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** Following several accounts at once, and Home reading on through many. */
class ThreadsFollowingTest {
    private val folder = Files.createTempDirectory("threads-following").toFile().apply { deleteOnExit() }

    @Test
    fun addAccountTakesHandlesAtHandlesAndLinksSeveralAtOnceEachOnce() {
        assertEquals(listOf("zuck"), ThreadsAccounts.handlesIn(" @Zuck "))
        assertEquals(
            listOf("zuck", "mosseri", "meta"),
            ThreadsAccounts.handlesIn("@zuck, mosseri\nhttps://www.threads.net/@meta zuck;https://www.threads.com/@mosseri/post/C1"),
        )
        assertNull(ThreadsAccounts.handlesIn("   "))
        assertNull(ThreadsAccounts.handlesIn("zuck not/a/handle"), "nothing half-read is followed")
    }

    @Test
    fun severalAccountsAreFollowedInOneWrite(): Unit = runBlocking {
        val accounts = ThreadsAccounts(StoreFile(folder.resolve("accounts.json")))
        accounts.add(ThreadsAccount("zuck", "Mark"))
        accounts.addAll(listOf(ThreadsAccount("meta", "meta"), ThreadsAccount("ZUCK", "zuck"), ThreadsAccount(" ", " ")))
        assertEquals(listOf("meta", "zuck"), accounts.load().map { it.id })
    }

    @Test
    fun homeReadsOnWhileALoadChangesHowManyAreLeftAndStopsWhenItDoesNot() {
        assertTrue(ThreadsCatchUp.readsOn(null, 30))
        assertTrue(ThreadsCatchUp.readsOn(30, 10))
        assertTrue(ThreadsCatchUp.readsOn(0, 12), "a follow added some")
        assertFalse(ThreadsCatchUp.readsOn(10, 10), "the same left unread: failing accounts or a throttle")
        assertFalse(ThreadsCatchUp.readsOn(10, 0))
    }
}
