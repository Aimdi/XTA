package com.aimdi.xtanative.networks.substack

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A discussion laid out as XTA's comments store does it: test/substack_discussion_reader_test.dart. */
class SubstackCommentsTest {
    private fun comment(id: String, parent: String? = null, depth: Int = 0, date: String? = null, body: String = id, author: String = "Reader $id") =
        SubstackComment(id, body, author, parent, date?.let { LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC) }, depth)

    private val discussion = listOf(
        comment("root", body = "Do you keep a notebook on your walks?", author = "Alex"),
        comment("child", parent = "root", depth = 1, body = "Yes. It helps me remember the light and colours.", author = "Maya"),
        comment("nested", parent = "child", depth = 2, body = "That is a lovely idea. I will try it this weekend.", author = "Robin"),
        comment("sibling", body = "Thank you for this thoughtful piece.", author = "Sam"),
    )

    @Test
    fun theOutlineHonoursParentIdsAndKeepsDuplicateOrphanAndCyclicInputsSafe() {
        val comments = listOf(
            comment("a"), comment("b"), comment("a1", parent = "a"), comment("a"),
            comment("orphan", parent = "missing"), comment("c1", parent = "c2"), comment("c2", parent = "c1"),
        )
        val rows = SubstackComments.rows(comments)
        assertEquals(listOf("a", "a1", "b", "orphan", "c1", "c2"), rows.map { it.comment.id })
        assertEquals(1, rows.first().descendants)
        assertFalse("a1" in SubstackComments.rows(comments, collapsed = setOf("a")).map { it.comment.id })
        assertTrue(SubstackComments.rows(comments, collapsed = setOf("a")).first().collapsed)
    }

    @Test
    fun depthAloneNestsCommentsThatCarryNoParent() {
        val rows = SubstackComments.rows(listOf(comment("a"), comment("a1", depth = 1), comment("a2", depth = 2), comment("b")))
        assertEquals(listOf(0, 1, 2, 0), rows.map { it.depth })
        assertEquals(2, rows.first().descendants)
    }

    @Test
    fun aSearchMatchesAuthorAndBodyAndKeepsTheCommentsAboveAsContext() {
        val rows = SubstackComments.rows(discussion, query = "robin weekend")
        assertEquals(listOf("root", "child", "nested"), rows.map { it.comment.id })
        assertEquals(listOf(true, true, false), rows.map { it.contextOnly })
        assertTrue(SubstackComments.rows(discussion, query = "missing words").isEmpty())
        assertTrue(SubstackComments.rows(discussion).none { it.contextOnly })
    }

    @Test
    fun dateOrderSortsSiblingsStablyWithoutDetachingReplies() {
        val comments = listOf(
            comment("a", date = "2026-01-01"), comment("b", date = "2026-01-03"), comment("a1", parent = "a", date = "2026-01-05"),
            comment("c", date = "2026-01-03"), comment("undated"),
        )
        assertEquals(listOf("b", "c", "a", "a1", "undated"), SubstackComments.rows(comments, order = SubstackCommentOrder.NEWEST).map { it.comment.id })
        assertEquals(listOf("a", "a1", "b", "c", "undated"), SubstackComments.rows(comments, order = SubstackCommentOrder.OLDEST).map { it.comment.id })
    }

    @Test
    fun tenThousandNestedCommentsAreCountedAndWalkedWithoutRecursion() {
        val comments = (0 until 10_000).map { comment("$it", parent = if (it == 0) null else "${it - 1}") }
        val rows = SubstackComments.rows(comments)
        assertEquals(10_000, rows.size)
        assertEquals(9_999, rows.first().descendants)
        assertEquals(9_999, rows.last().depth)
        assertEquals(1, SubstackComments.rows(comments, collapsed = setOf("0")).size)
    }

    @Test
    fun theFlattenedFixtureLaysOutUnderItsParents() {
        val rows = SubstackComments.rows(SubstackParser.comments(com.aimdi.xtanative.core.json.J.parse(fixture("comments.json"))))
        assertEquals(listOf("1" to 0, "2" to 1, "4" to 1, "5" to 0, "6" to 1, "7" to 2), rows.map { it.comment.id to it.depth })
        assertEquals(2, rows.first().descendants)
    }
}

/** Which links are posts, and the links inside notes and comments: substack_links and substack_discussion_reader tests. */
class SubstackLinksTest {
    @Test
    fun aPostOnAPublicationSubdomainOrTheShareHostIsALink() {
        assertEquals(SubstackPostLink("https://astralcodexten.substack.com", "the-media-very-rarely-lies"), SubstackLinks.postLink("https://astralcodexten.substack.com/p/the-media-very-rarely-lies"))
        assertEquals(SubstackPostLink("https://platformer.substack.com", "the-deal"), SubstackLinks.postLink("https://open.substack.com/pub/platformer/p/the-deal?utm_source=share"))
        assertEquals("the-deal", SubstackLinks.postLink("https://platformer.substack.com/p/the-deal/comments")?.slug)
        assertEquals("bar", SubstackLinks.postLink("https://foo.substack.com/p/bar?utm_medium=email#footnote-1")?.slug)
    }

    @Test
    fun aCustomDomainIsOnlyClaimedWhenItIsFollowed() {
        val url = "https://www.thefp.com/p/some-essay"
        assertEquals(null, SubstackLinks.postLink(url))
        assertEquals(SubstackPostLink("https://www.thefp.com", "some-essay"), SubstackLinks.postLink(url, listOf("https://www.thefp.com")))
    }

    @Test
    fun pagesOfAPublicationSubstacksOwnPagesAndOtherSitesAreNoPost() {
        listOf(
            "https://foo.substack.com", "https://foo.substack.com/", "https://foo.substack.com/archive", "https://foo.substack.com/about",
            "https://foo.substack.com/subscribe", "https://foo.substack.com/notes", "https://foo.substack.com/p",
            "https://substack.com/@someone", "https://substack.com/@someone/note/c-12345", "https://www.substack.com/pricing",
            "https://open.substack.com/pub/platformer", "https://x.com/jack/status/20", "https://example.com/p/looks-like-substack",
            "https://notsubstack.com/p/nope", "mailto:someone@substack.com", "not a url at all", "",
            "https://evilsubstack.com/p/post", "https://foo.substack.com.evil.tld/p/post", "https://substack.com/home/post/p-147371717",
        ).forEach { assertEquals(null, SubstackLinks.postLink(it), it) }
    }

    @Test
    fun aStubCarriesWhatTheReaderNeedsToLoadTheRealPost() {
        val link = SubstackLinks.postLink("https://platformer.substack.com/p/the-deal")!!
        val stub = SubstackLinks.stub(link)
        assertEquals("the-deal", stub.slug)
        assertEquals("https://platformer.substack.com", stub.publicationBaseUrl)
        assertEquals("https://platformer.substack.com", stub.publication.baseUrl)
        assertEquals("https://platformer.substack.com/p/the-deal", stub.canonicalUrl)
        assertEquals("platformer", stub.publicationName)
        assertTrue(stub.title.isNotEmpty(), "the reader never opens with a blank title")
        assertEquals("Platformer", SubstackLinks.stub(link, "Platformer").publicationName)
    }

    @Test
    fun aPostLinkOnAnUnknownDomainIsACandidateOnItsOwnHost() {
        assertEquals(SubstackPostLink("https://www.example.org", "hello-world"), SubstackLinks.customDomainCandidate("https://www.example.org/p/hello-world?utm=x"))
        assertEquals(SubstackPostLink("https://example.org", "x"), SubstackLinks.customDomainCandidate("http://example.org/p/x"))
        listOf(
            "https://foo.substack.com/p/x", "https://example.org/posts/x", "https://example.org/p/archive",
            "https://www.instagram.com/p/Cabc123/", "ftp://example.org/p/x",
        ).forEach { assertEquals(null, SubstackLinks.customDomainCandidate(it), it) }
        assertEquals(null, SubstackLinks.customDomainCandidate("https://news.example.org/p/x", listOf("https://news.example.org")))
    }

    @Test
    fun linksInADiscussionKeepTheirTextAndLeavePunctuationOut() {
        val text = "See (https://example.org/a), then https://example.net/path_(topic). And javascript:alert(1)"
        val parts = SubstackLinks.textParts(text)
        assertEquals(text, parts.joinToString("") { it.text })
        assertEquals(listOf("https://example.org/a", "https://example.net/path_(topic)"), parts.mapNotNull { it.url })
        assertEquals(null, SubstackLinks.webUrl("javascript:alert(1)"))
        assertEquals(null, SubstackLinks.webUrl("https://"))
        assertEquals(listOf(SubstackTextPart("No link here")), SubstackLinks.textParts("No link here"))
    }
}
