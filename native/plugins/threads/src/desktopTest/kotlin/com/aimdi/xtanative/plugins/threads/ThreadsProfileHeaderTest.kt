package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsBioLink
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.networks.threads.ThreadsProfileFacts
import com.aimdi.xtanative.networks.threads.ThreadsProfilePage
import com.aimdi.xtanative.networks.threads.ThreadsTextFragment
import com.aimdi.xtanative.networks.threads.ThreadsTextFragment.Kind
import com.aimdi.xtanative.store.StoreFile
import java.nio.file.Files
import java.time.Instant
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** A profile's header past XTA's: every link it lists, exact counts, a private note, all kept on disk. */
class ThreadsProfileHeaderTest {
    private val zuck = ThreadsProfile(
        pk = "314216", id = "314216", username = "zuck", fullName = "Mark Zuckerberg",
        biography = "Hi @dana", followerCount = 5_812_345, mediaCount = 1_330, externalUrl = "https://about.meta.com",
        facts = ThreadsProfileFacts(
            bioFragments = listOf(ThreadsTextFragment(Kind.TEXT, "Hi "), ThreadsTextFragment(Kind.MENTION, "@dana", "dana")),
            links = listOf(
                ThreadsBioLink("https://www.about.meta.com/", "Meta"),
                ThreadsBioLink("https://www.threads.com/@zuck"),
                ThreadsBioLink("https://example.org/blog"),
            ),
            roundedPosts = true,
        ),
    )

    @Test
    fun theWebsiteComesFirstThenTheBiosLinksEachOnceAndNeverItsOwnThreadsPage() {
        val links = ThreadsProfileHeader.links(zuck)
        assertEquals(listOf("https://about.meta.com", "https://example.org/blog"), links.map { it.url })
        assertEquals(listOf("about.meta.com", "example.org/blog"), links.map(ThreadsProfileHeader::label))
        assertEquals("Meta", ThreadsProfileHeader.label(ThreadsBioLink("https://about.meta.com", "Meta")))
    }

    @Test
    fun onlyAFigureMetaKeptIsShownWholeOnATapGroupedAsTheReadersLanguageGroups() {
        assertTrue(ThreadsProfileHeader.isExact(zuck, ThreadsCount.FOLLOWERS))
        assertFalse(ThreadsProfileHeader.isExact(zuck, ThreadsCount.POSTS), "the meta tags' count may be rounded")
        assertEquals("5,812,345", ThreadsProfileHeader.exact(5_812_345, Locale.ENGLISH))
        assertEquals("5.812.345", ThreadsProfileHeader.exact(5_812_345, Locale.GERMAN))
    }

    @Test
    fun repostsLeaveTheThreadsTabForTheirOwn() {
        val own = ThreadsPost(id = "1", handle = "zuck", authorName = "Zuck", text = "mine")
        val shared = ThreadsPost(id = "2", handle = "meta", authorName = "Meta", text = "theirs").repostedBy("3", "zuck", "Zuck")
        val reply = ThreadsPost(id = "4", handle = "zuck", authorName = "Zuck", text = "re", isReply = true)
        val page = ThreadsProfilePage(zuck, listOf(own, shared, reply))
        assertEquals(listOf("1"), ThreadsProfileHeader.threads(page).map { it.id })
        assertEquals(listOf("3"), ThreadsProfileHeader.reposts(page).map { it.id })
    }

    @Test
    fun theRepostsTabTakesTheRepostsPageNewestFirstEachOnce() {
        val at = { s: Long -> Instant.ofEpochSecond(s) }
        val older = ThreadsPost(id = "2", handle = "meta", authorName = "Meta", text = "a", publishedAt = at(100)).repostedBy("3", "zuck", "Zuck")
        val newer = ThreadsPost(id = "5", handle = "ola", authorName = "Ola", text = "b").repostedBy("6", "zuck", "Zuck", at(200))
        val page = ThreadsProfilePage(zuck, listOf(older))
        assertEquals(listOf("6", "3"), ThreadsProfileHeader.reposts(page, listOf(older, newer)).map { it.id })
    }

    @Test
    fun aKeptProfileKeepsItsSplitBioItsLinksAndWhichCountsWereRounded() {
        val kept = ThreadsProfilesKept.pageOf(J.parse(ThreadsProfilesKept.json(ThreadsProfilePage(zuck, emptyList())).toString()))
        assertEquals(zuck, kept!!.profile)
    }

    @Test
    fun aNoteIsKeptByHandleTrimmedAndABlankOneRemovesIt() = runBlocking {
        val file = StoreFile(Files.createTempDirectory("threads-notes").resolve("notes.json").toFile())
        val notes = ThreadsAccountNotes(file)
        notes.keep("@Zuck", "  met at a conference ")
        assertEquals(mapOf("zuck" to "met at a conference"), ThreadsAccountNotes(file).load())
        notes.keep("zuck", "   ")
        assertEquals(emptyMap(), ThreadsAccountNotes(file).load())
        assertEquals(emptyMap(), ThreadsAccountNotes.decode("not json"))
    }
}
