package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Live threads.com, as XTA's test/live/sources_smoke_test.dart reads it
 * without an account: a public profile's card, its threads, then the first
 * thread's conversation. Skipped unless RUN_LIVE=true.
 */
class ThreadsLiveTest {
    @Test
    fun aPublicProfileAndItsThreadsReadAsAGuest() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live threads.com")
        val client = ThreadsDirectClient(OkHttpXHttp())

        val profile = client.guestProfile("zuck")
        assertEquals("zuck", profile.username)
        assertTrue(profile.fullName.isNotBlank(), "the page names the profile")

        val threads = client.guestAccount("zuck").items
        assertTrue(threads.isNotEmpty(), "a guest is shown @zuck's threads")
        assertTrue(threads.any { it.handle == "zuck" || it.repostedByHandle == "zuck" })

        val conversation = client.postThread(threads.firstNotNullOf { it.url })
        assertTrue(conversation.isNotEmpty(), "a guest is shown a thread's page")
        println("LIVE Threads @zuck: ${profile.followerCount} followers, ${threads.size} threads, a conversation of ${conversation.size}")
    }
}
