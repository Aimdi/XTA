package com.aimdi.xtanative.networks.substack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** A text kept in memory, as a preference holds it. */
class MemoryKept(var text: String? = null) : SubstackKept {
    var writes = 0

    override suspend fun read(): String? = text

    override suspend fun write(text: String) {
        writes++
        this.text = text
    }
}

/** The read marks, likes, saves, pins and search history kept on the device, as XTA keeps them. */
class SubstackLocalTest {
    private fun post(id: String, slug: String = "post-$id", base: String = "https://field.substack.com") =
        SubstackPost(id, "Title $id", slug, base, "Field Notes", canonicalUrl = "$base/p/$slug")

    @Test
    fun readMarksKeepTheNewestFirstUnderEachPostsKeyAndForgetBothKeyAndId(): Unit = runBlocking {
        val kept = MemoryKept("""["old"]""")
        val marks = SubstackReadMarks(kept)
        marks.load()
        marks.markRead(listOf(post("1"), post("2")))
        assertEquals(listOf("field.substack.com/p/post-1", "field.substack.com/p/post-2", "old"), marks.state.value.toList())
        assertTrue(marks.isRead(post("1")))
        assertTrue(marks.isRead(SubstackPost("old", "Old", "x", "https://other.example", "O")), "a read kept by id, as before posts had keys")
        marks.markUnread(post("1"))
        assertFalse(marks.isRead(post("1")))
        assertEquals("""["field.substack.com/p/post-2","old"]""", kept.text)
    }

    @Test
    fun readMarksStopAtTheirCapDroppingTheOldest(): Unit = runBlocking {
        val marks = SubstackReadMarks(MemoryKept())
        marks.markRead((1..SubstackReadMarks.CAP + 5).map { post("$it") })
        assertEquals(SubstackReadMarks.CAP, marks.state.value.size)
        assertTrue(marks.isRead(post("1")))
        assertFalse(marks.isRead(post("${SubstackReadMarks.CAP + 5}")))
    }

    @Test
    fun aDamagedPreferenceReadsAsNothingKept(): Unit = runBlocking {
        assertTrue(SubstackReadMarks(MemoryKept("{oops")).load().isEmpty())
        assertTrue(SubstackPostShelf(MemoryKept("{\"a\":1}"), 10).load().isEmpty())
        assertTrue(SubstackHighlights(MemoryKept("[1,2]")).load().isEmpty())
    }

    @Test
    fun aShelfTogglesEitherCopyOfAPostNewestFirstWithinItsCapacity(): Unit = runBlocking {
        val kept = MemoryKept()
        val saves = SubstackPostShelf(kept, capacity = 2)
        saves.toggle(post("1").copy(bodyHtml = "<p>long</p>"))
        saves.toggle(post("2"))
        saves.toggle(post("3"))
        assertEquals(listOf("3", "2"), saves.state.value.map { it.id })
        val rssCopy = post("rss-slug", slug = "post-3")
        assertTrue(saves.contains(rssCopy), "the RSS copy of a saved post is that post")
        saves.toggle(rssCopy)
        assertEquals(listOf("2"), saves.state.value.map { it.id })
        assertFalse("long" in kept.text.orEmpty(), "a saved post keeps no body")
        assertEquals(listOf("2"), SubstackPostShelf(kept, 2).load().map { it.id }, "read back as XTA wrote it")
    }

    @Test
    fun pinsAreCommaSeparatedInPinOrder(): Unit = runBlocking {
        val kept = MemoryKept(" a , ,b")
        val pins = SubstackPins(kept)
        assertEquals(listOf("a", "b"), pins.load())
        pins.toggle("c")
        pins.toggle("a")
        assertEquals("b,c", kept.text)
        pins.forget("b")
        assertEquals(listOf("c"), pins.state.value)
    }

    @Test
    fun followsListPinnedFirstThenByNameAndPlaceANewOneUnderItsOwnHost() {
        val alpha = SubstackPublication("alpha", "https://alpha.substack.com", "alpha")
        val beta = SubstackPublication("beta", "https://beta.substack.com", "Beta")
        val gamma = SubstackPublication("gamma", "https://gamma.substack.com", "Gamma")
        assertEquals(listOf(gamma, alpha, beta), SubstackFollowing.ordered(listOf(beta, gamma, alpha), listOf("gamma", "gone")))
        val follows = listOf(SubstackFollow(alpha, "2026-01-01 10:00:00"))
        val again = SubstackFollowing.followed(follows, alpha.copy(name = "Alpha", logoUrl = "https://logo"), "2026-11-04 10:00:00")
        assertEquals(listOf(SubstackFollow(alpha.copy(name = "Alpha", logoUrl = "https://logo"), "2026-01-01 10:00:00")), again, "a follow keeps when it was made")
        val news = SubstackPublication("news", "https://news.substack.com", "News")
        val deep = SubstackPublication("news", "https://news.alpha.example", "Alpha News")
        val both = SubstackFollowing.followed(listOf(SubstackFollow(news, "t1")), deep, "t2")
        assertEquals(listOf("news", "news.alpha.example"), both.map { it.publication.id }, "a guessed id another follow holds goes under its host")
        assertEquals(listOf("news.alpha.example"), SubstackFollowing.unfollowed(both, "news").map { it.publication.id })
    }
}
