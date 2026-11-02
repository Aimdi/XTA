package com.aimdi.xtanative.networks.threads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Who to suggest following (XTA's threads_discovery_test). */
class ThreadsDiscoveryTest {
    private fun post(handle: String, name: String? = null, repostedBy: String? = null) = ThreadsPost(
        id = "https://www.threads.com/@$handle/post/1",
        handle = handle,
        authorName = name ?: handle,
        text = "hi",
        repostedByHandle = repostedBy,
    )

    @Test
    fun repostedAuthorsComeFirstAndFollowedOnesAreSkipped() {
        val people = ThreadsDiscovery.peopleToFollow(
            posts = listOf(post("alice"), post("bob", name = "Bob", repostedBy = "alice"), post("cara", repostedBy = "alice"), post("alice")),
            alreadyFollows = { it == "alice" },
        )
        assertEquals(listOf("bob", "cara"), people.map { it.handle })
        assertTrue(people.first().fromRepost)
        assertEquals("Bob", people.first().name)
    }

    @Test
    fun theStripIsCappedAndEmptyHandlesAreIgnored() {
        val people = ThreadsDiscovery.peopleToFollow(
            posts = listOf(post("")) + (0 until 20).map { post("user$it") },
            alreadyFollows = { false },
            cap = 3,
        )
        assertEquals(listOf("user0", "user1", "user2"), people.map { it.handle })
    }

    @Test
    fun aNamelessAuthorIsNamedByTheirHandle() {
        val person = ThreadsDiscovery.peopleToFollow(listOf(post("Maya", name = " ")), alreadyFollows = { false }).single()
        assertEquals("maya", person.handle)
        assertEquals("maya", person.name)
    }

    @Test
    fun theStarterHandlesAreThePublicOnesAGuestCanTry() {
        assertEquals(listOf("zuck", "mosseri", "meta"), ThreadsDiscovery.STARTER_HANDLES)
    }
}
