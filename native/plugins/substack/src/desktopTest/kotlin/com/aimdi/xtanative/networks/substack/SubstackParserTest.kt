package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.core.json.J
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Substack's JSON as XTA reads it: substack_features, substack_protocol_regression and substack_discovery_tts tests. */
class SubstackParserTest {
    private val base = "https://fieldnotes.substack.com"

    private fun post(json: String, includeBody: Boolean = true) = SubstackParser.post(J.parse(json), base, "Field Notes", includeBody)

    @Test
    fun aWholePostGivesItsBodyBylineCountsAndPublication() {
        val json = J.parse(fixture("post.json"))
        val post = SubstackParser.post(json, base, "Field Notes")
        assertEquals("990001", post.id)
        assertEquals("a-long-read", post.slug)
        assertEquals("Perf Author", post.authorName)
        assertEquals(120L, post.reactionCount)
        assertEquals(340L, post.commentCount)
        assertEquals(1200L, post.wordCount)
        assertEquals(Instant.parse("2026-10-01T08:00:00Z"), post.publishedAt)
        assertTrue(post.bodyHtml!!.startsWith("<h2>Start</h2>"))
        assertTrue(post.hasApiId)
        assertFalse(post.isPaywalled)
        assertEquals("The reader by the quiet river", post.excerpt)

        val publication = SubstackParser.publicationOfPost(json, base.toHttpUrl())
        assertEquals(SubstackPublication("fieldnotes", base, "Field Notes", "Notes from the field", "https://substackcdn.com/logo.png"), publication)
    }

    @Test
    fun aListingKeepsPostsAroundRowsOfAnotherShapeAndDropsTheBody() {
        val posts = SubstackParser.posts(J.parse(fixture("archive.json")).list, base) { "Field Notes" }
        assertEquals(listOf("101", "102", "103", "104"), posts.map { it.id })
        val (hello, paid, episode, clip) = posts
        assertNull(hello.bodyHtml, "a listing never carries the body")
        assertEquals("world", hello.excerpt)
        assertEquals(5L, hello.reactionCount, "the per-emoji map is summed when there is no total")
        assertTrue(paid.isPaywalled)
        assertEquals("The paid part starts here", paid.excerpt, "Substack's ... placeholder is no excerpt")
        assertEquals(0L, paid.commentCount, "a negative count never shows as negative")
        assertNull(paid.reactionCount, "a count that is no number is none")
        assertTrue(episode.isPodcast)
        assertEquals("https://api.substack.com/feed/podcast/103/ep.mp3", episode.audioUrl)
        assertTrue(clip.isVideo)
        assertFalse(clip.isPodcast)
    }

    @Test
    fun badOptionalScalarsAreNullWithoutLosingThePost() {
        val odd = post(
            """{"id":"one","slug":"article-one","title":"Article one","subtitle":7,"description":false,"post_date":[],
            "canonical_url":4,"cover_image":{},"body_html":true,"audience":[],"type":13,"publishedBylines":[{"name":false}],
            "podcast_url":10,"reaction_count":"many","comment_count":-4,"wordcount":"many"}""",
        )
        assertEquals("one", odd.id)
        assertNull(odd.subtitle)
        assertNull(odd.authorName)
        assertNull(odd.reactionCount)
        assertNull(odd.wordCount)
        assertNull(odd.publishedAt)
        assertEquals(0L, odd.commentCount)
        assertFalse(odd.isPodcast)
        assertEquals("slug-only", post("""{"slug":"slug-only","title":"T"}""").id, "a post without a number is named by its slug")
        assertNull(post("""{"id":1,"slug":"s","title":"T"}""").reactionCount, "nothing carried is null, not zero")
        assertTrue(post("""{"id":1,"slug":"s","title":"T","video_upload":{"id":1}}""").isVideo)
        assertTrue(post("""{"id":1,"slug":"s","title":"T","type":"video"}""").isVideo)
        assertFalse(post("""{"id":1,"slug":"s","title":"T","podcast_url":""}""").isPodcast, "an empty URL is no episode")
    }

    @Test
    fun paidAudiencesArePaywalledAndAPaidPostMayStillCarryItsOpening() {
        listOf("only_paid", "only_paying", "founding", "only_founding", "ONLY_PAID").forEach {
            assertTrue(post("""{"id":1,"slug":"s","title":"T","audience":"$it"}""").isPaywalled, it)
        }
        assertFalse(post("""{"id":1,"slug":"s","title":"T","audience":"everyone"}""").isPaywalled)
        val opening = post("""{"id":1,"slug":"s","title":"T","audience":"only_paid","body_html":"<p>The opening.</p>"}""")
        assertEquals("<p>The opening.</p>", opening.bodyHtml)
        assertNull(post("""{"id":1,"slug":"s","title":"T","audience":"only_paid"}""").bodyHtml)
    }

    @Test
    fun aBylineOfAnotherShapeStillGivesThePublicationAtTheHostThatServedIt() {
        val publication = SubstackParser.publicationOfPost(
            J.parse("""{"publishedBylines":[{"publicationUsers":[{"publication":{"name":42,"subdomain":false,"hero_text":[],"logo_url":{}}}]}]}"""),
            "https://www.platformer.news".toHttpUrl(),
        )
        assertEquals(SubstackPublication("platformer", "https://www.platformer.news", "platformer"), publication)
        val custom = SubstackParser.publicationOfPost(
            J.parse("""{"publishedBylines":[{"publicationUsers":[{"publication":{"name":"Astral Codex Ten","subdomain":"astralcodexten","custom_domain":"www.astralcodexten.com"}}]}]}"""),
            "https://astralcodexten.substack.com".toHttpUrl(),
        )
        assertEquals("https://astralcodexten.substack.com", custom.baseUrl, "an advertised custom domain is not where the posts came from")
        assertEquals("Astral Codex Ten", custom.name)
    }

    @Test
    fun discoveredPublicationsTakeTheirAddressFromBaseUrlCustomDomainOrSubdomain() {
        val results = J.parse(fixture("publication_search.json"))["results"].list.map(SubstackParser::discovered)
        val (bytes, platformer, orphan, badHandle) = results
        assertEquals(SubstackPublication("bytebytego", "https://blog.bytebytego.com", "ByteByteGo Newsletter", "Explain complex systems", "https://example.com/logo.png"), bytes)
        assertEquals(SubstackPublication("platformer", "https://platformer.substack.com", "Platformer"), platformer)
        assertNull(orphan, "nothing names it")
        assertNull(badHandle, "a subdomain that is no handle makes no address")
        val customOnly = SubstackParser.discovered(J.parse("""{"name":"PE","custom_domain":"newsletter.pragmaticengineer.com"}"""))
        assertEquals("https://newsletter.pragmaticengineer.com", customOnly?.baseUrl)
        assertEquals("newsletter", customOnly?.subdomain)
    }

    @Test
    fun aProfileGivesItsPrimaryPublicationElseItsFirstPublication() {
        val primary = SubstackParser.publicationOfProfile(
            J.parse("""{"handle":"platformer","primaryPublication":{"name":"Platformer","subdomain":"platformer","custom_domain":"www.platformer.news"}}"""),
        )
        assertEquals(SubstackPublication("platformer", "https://www.platformer.news", "Platformer"), primary)
        val listed = SubstackParser.publicationOfProfile(
            J.parse("""{"publicationUsers":[{"publication":{"name":"x"}},{"publication":{"name":"Casey","subdomain":"casey"}}]}"""),
        )
        assertEquals("https://casey.substack.com", listed?.baseUrl)
        assertNull(SubstackParser.publicationOfProfile(J.parse("[]")))
    }

    @Test
    fun readerNotesKeepTheirSiblingsAroundMissingAuthorsAndOddCounts() {
        val notes = J.parse(fixture("reader_feed.json"))["items"].list.map(SubstackParser::note)
        val first = notes[0]
        assertEquals("1", first.id)
        assertEquals("Hello note", first.body)
        assertEquals("https://substack.com/@ann/note/c-1", first.url)
        assertEquals("https://substackcdn.com/note.png", first.imageUrl)
        assertEquals("Ann Writes", first.publication?.name)
        assertEquals(3L, first.reactionCount)
        assertEquals(Instant.parse("2026-08-01T10:00:00Z"), first.at)
        val anonymous = notes[1]
        assertNull(anonymous.authorName)
        assertNull(anonymous.authorHandle)
        assertNull(anonymous.url)
        assertNull(anonymous.reactionCount)
        assertNull(anonymous.publication)
        assertNull(anonymous.at)
        assertEquals("", notes[2].body, "a body of another shape is none")
        assertEquals(0L, notes[3].reactionCount)
        assertEquals("https://substack.com/@jordan/note/c-4", notes[3].url)
        assertEquals("", notes[4].id)
        assertEquals("", SubstackParser.note(J.parse("""{"comment":null,"entity_key":false}""")).id)
    }

    @Test
    fun categoriesReadNumbersOnlyAndTrimTheirNames() {
        val categories = J.parse(fixture("categories.json")).list.map(SubstackParser::category)
        assertEquals(SubstackCategory(1, "Science", "science"), categories[0])
        assertEquals(SubstackCategory(2, "Arts", "arts"), categories[1])
        assertEquals(0L, categories[4].id, "an id in quotes is no number")
    }

    @Test
    fun recommendationsComeFromThePreloadsEachPublicationOnce() {
        val recommendations = SubstackPages.recommendations(fixture("recommendations.html"))
        assertEquals(listOf("peterwildeford", "bigtechnology"), recommendations.map { it.publication.id })
        val first = recommendations.first()
        assertEquals("The Power Law", first.publication.name)
        assertEquals("https://blog.peterwildeford.com", first.publication.baseUrl)
        assertEquals("Why I read this", first.blurb)
        assertNull(recommendations[1].blurb, "a blank blurb is none")
        assertTrue(SubstackPages.recommendations("").isEmpty())
        assertTrue(SubstackPages.recommendations("<html></html>").isEmpty())
        assertTrue(SubstackParser.recommendations(J.parse("\"nope\"")).isEmpty())
    }

    @Test
    fun similarDropsTheSeedPrefersRecommendationsThenPadsWithSearch() {
        val seed = publication("platformer", name = "Platformer")
        val big = publication("bigtechnology", name = "Big Technology")
        val merged = SubstackParser.mergeSimilar(
            seed,
            recommended = listOf(SubstackRecommendation(big, "Casey")),
            searched = listOf(seed, big, SubstackPublication("user-mag", "https://www.usermag.co", "User Mag")),
        )
        assertEquals(listOf("bigtechnology", "user-mag"), merged.map { it.publication.id })
        assertEquals("Casey", merged.first().blurb)
        assertEquals(2, SubstackParser.mergeSimilar(seed, searched = (1..5).map { publication("p$it") }, limit = 2).size)
    }

    @Test
    fun theDiscussionFlattensInReadingOrderWithDeletedCommentsSkipped() {
        val comments = SubstackParser.comments(J.parse(fixture("comments.json")))
        assertEquals(listOf("1", "2", "4", "5", "6", "7"), comments.map { it.id })
        assertEquals(listOf(0, 1, 1, 0, 0, 0), comments.map { it.depth })
        assertEquals(listOf(null, "1", "1", null, "5", "6"), comments.map { it.parentId })
        assertEquals("Under a deleted reply", comments[2].body, "a deleted comment vanishes, its reply keeps its place")
        assertEquals(Instant.parse("2026-07-01T10:00:00Z"), comments[0].at)
        assertNull(comments[1].at)
    }

    @Test
    fun aPayloadThatNoLongerFitsIsAnEmptyDiscussion() {
        listOf("null", "\"nonsense\"", """{"comments":"nope"}""", """{"comments":[42,null]}""", "{broken").forEach {
            assertTrue(SubstackParser.comments(J.parse(it)).isEmpty(), it)
        }
    }

    @Test
    fun aRunawayThreadStopsIndentingAtTheCapButKeepsEveryReply() {
        val chain = (11 downTo 0).fold("""{"id":12,"body":"level 12"}""") { inner, id -> """{"id":$id,"body":"level $id","children":[$inner]}""" }
        val comments = SubstackParser.comments(J.parse("""{"comments":[$chain]}"""))
        assertEquals(13, comments.size, "nine before BUG-01 was fixed: the cap ended the walk")
        assertEquals(8, comments.maxOf { it.depth })
        assertEquals("11", comments.last().parentId)
    }

    @Test
    fun repeatedIdentitiesAndVeryDeepDeletedChainsEndSafely() {
        val duplicate = SubstackParser.comments(
            J.parse("""[{"id":"one","body":"First"},{"id":"one","body":"Duplicate","children":[{"id":"two","body":"Still readable"}]}]"""),
        )
        assertEquals(listOf("one", "two"), duplicate.map { it.id })
        fun chain(levels: Int) = "[" + "{\"children\":[".repeat(levels) + """{"id":"leaf","body":"Leaf"}""" + "]}".repeat(levels) + "]"
        assertEquals(listOf("leaf"), SubstackParser.comments(J.parse(chain(150))).map { it.id }, "deleted comments all the way down")
        assertTrue(SubstackParser.comments(J.parse(chain(21_000))).isEmpty(), "nesting too deep to parse is an empty discussion, not a throw")
        val wide = "[" + "{\"body\":\"\"},".repeat(20_000) + """{"id":"late","body":"Late"}]"""
        assertTrue(SubstackParser.comments(J.parse(wide)).isEmpty(), "the walk stops after 20 000 nodes")
    }

    @Test
    fun beehiivPostsMapPremiumToPaidAndTheirSlugToTheCanonicalUrl() {
        val rows = J.parse(fixture("beehiiv_posts.json"))
        assertTrue(SubstackParser.looksLikeBeehiivPosts(rows))
        val posts = rows["posts"].list.map { SubstackParser.beehiivPost(it, "https://www.garbageday.email", "Garbage Day") }
        val (miles, premium, untitled) = posts
        assertEquals("https://www.garbageday.email/p/miles-morales-probably-couldn-t-happen-now", miles.canonicalUrl)
        assertEquals("Adam Bumas", miles.authorName)
        assertFalse(miles.isPaywalled)
        assertEquals("Read to the end for a very good dog video", miles.subtitle)
        assertNotNull(miles.publishedAt)
        assertTrue(premium.isPaywalled)
        assertEquals("Premium dispatch", premium.title)
        assertEquals("", untitled.title)
        assertTrue(SubstackParser.looksLikeBeehiivPosts(J.parse("""{"posts":[],"pagination":{"total_pages":1}}""")))
        assertFalse(SubstackParser.looksLikeBeehiivPosts(J.parse("""[{"title":"Hello","slug":"hello"}]""")))
        assertFalse(SubstackParser.looksLikeBeehiivPosts(J.parse("""{"posts":[null],"pagination":{}}""")))
    }
}
