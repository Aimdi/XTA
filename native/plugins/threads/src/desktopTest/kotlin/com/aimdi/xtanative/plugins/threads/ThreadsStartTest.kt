package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsPost
import kotlin.test.Test
import kotlin.test.assertEquals

/** What a first run with no one followed shows, and who it offers to follow. */
class ThreadsStartTest {
    private fun post(handle: String, repost: Boolean = false) = ThreadsPost(
        id = handle,
        handle = handle,
        authorName = handle.uppercase(),
        text = "t",
        url = "https://www.threads.com/@$handle/post/C1",
        repostedByHandle = if (repost) "someone" else null,
    )

    @Test
    fun theFirstOpenWithNoOneFollowedIsForYouAndAKeptSectionWins() {
        assertEquals(ThreadsSection.FOR_YOU, ThreadsForYouKept.openingSection(null, follows = false))
        assertEquals(ThreadsSection.HOME, ThreadsForYouKept.openingSection(null, follows = true))
        val kept = ThreadsForYouKept(section = ThreadsSection.LIKED).encode()
        assertEquals(ThreadsSection.LIKED, ThreadsForYouKept.openingSection(kept, follows = false))
        assertEquals(ThreadsSection.HOME, ThreadsForYouKept.openingSection(ThreadsForYouKept().encode(), follows = false))
    }

    @Test
    fun theStartersComeFirstThenForYousAuthorsNoneFollowedAndNoneTwice() {
        val people = ThreadsStarters.people(listOf(post("ann"), post("zuck"), post("bob", repost = true), post("ann"))) { it == "mosseri" }
        assertEquals(listOf("zuck", "meta", "bob", "ann"), people.map { it.handle })
        val many = ThreadsStarters.people((1..20).map { post("p$it") }) { false }
        assertEquals(ThreadsStarters.CAP, many.size)
    }
}
