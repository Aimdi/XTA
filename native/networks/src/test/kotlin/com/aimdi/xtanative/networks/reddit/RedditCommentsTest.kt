package com.aimdi.xtanative.networks.reddit

import com.aimdi.xtanative.core.json.J
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Comment threads as XTA's reddit_comments_test.dart reads them: Reddit's JSON tree, and old.reddit's markup. */
class RedditCommentsTest {
    private fun t1(
        id: String,
        author: String = "someone",
        body: String = "Well said",
        score: Int = 42,
        isSubmitter: Boolean = false,
        flair: String? = null,
        permalink: String? = null,
        replies: String = "\"\"",
    ) = """{"kind":"t1","data":{"id":"$id","author":"$author","body":${quoted(body)},"score":$score,"created_utc":1769000000.0,""" +
        """"is_submitter":$isSubmitter,"author_flair_text":${flair?.let(::quoted) ?: "null"},""" +
        """"permalink":"${permalink ?: "/r/dartlang/comments/abc/$id/"}","replies":$replies}}"""

    private fun quoted(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun listing(vararg children: String) = """{"kind":"Listing","data":{"children":[${children.joinToString(",")}]}}"""

    private fun comments(json: String, parentPermalink: String? = null) = RedditJson.comments(J.parse(json), parentPermalink)

    @Test
    fun theAuthorsFlairComesWithTheCommentAndAnEmptyOneNotAtAll() {
        val parsed = comments(listing(t1("a", flair = " Mod team "), t1("b", flair = ""), t1("c")))

        assertEquals(listOf("Mod team", null, null), parsed.map { it.authorFlair })
    }

    @Test
    fun aCommentCountsTheRepliesUnderItNotRowsThatStandForMore() {
        val comment = RedditComment(
            id = "a",
            body = "Top",
            replies = listOf(
                RedditComment(id = "b", body = "Reply", replies = listOf(RedditComment(id = "c", body = "Nested"))),
                RedditComment(id = "more", body = "", permalink = "/r/x/comments/y/z/a/", moreCount = 40),
            ),
        )

        assertEquals(2, comment.replyCount)
        assertEquals(1, comment.replies.first().replyCount)
    }

    @Test
    fun anAuthorsParagraphsSurvive() {
        assertEquals("One.\n\nTwo,\nthree.", comments(listing(t1("a", body = "One.\n\nTwo,\nthree."))).single().body)
    }

    @Test
    fun aNestedT1TreeIsRead() {
        val root = comments(listing(t1("a", author = "first", body = "Question", replies = listing(t1("b", author = "second", body = "Answer", score = 7))))).single()

        assertEquals("a", root.id)
        assertEquals("first", root.author)
        assertEquals("Question", root.body)
        assertEquals(42, root.score)
        assertEquals(Instant.ofEpochSecond(1769000000), root.createdAt)
        assertEquals("/r/dartlang/comments/abc/a/", root.permalink)
        assertEquals("b", root.replies.single().id)
        assertEquals("Answer", root.replies.single().body)
        assertEquals(7, root.replies.single().score)
    }

    @Test
    fun aMoreChildBecomesAStubWithTheHeldBackCount() {
        val stub = comments(listing("""{"kind":"more","data":{"count":34,"id":"xyz","children":["c1","c2"]}}""")).single()

        assertTrue(stub.isStub)
        assertEquals(34, stub.moreCount)
        assertEquals("xyz", stub.id)
        assertTrue(stub.body.isEmpty())
        assertNull(stub.permalink)
    }

    @Test
    fun aNestedMoreStubPointsAtItsParentComment() {
        val more = """{"kind":"more","data":{"count":47,"id":"m1","children":["x"]}}"""
        val stub = comments(listing(t1("a", replies = listing(more)))).single().replies.single()

        assertTrue(stub.isStub)
        assertEquals("/r/dartlang/comments/abc/a/", stub.permalink, "a stub with nowhere to go did nothing when tapped")
    }

    @Test
    fun aTopLevelMoreStubPointsAtThePostItself() {
        val stub = comments(listing("""{"kind":"more","data":{"count":500,"id":"m2","children":["y"]}}"""), "/r/dartlang/comments/abc/").single()

        assertEquals("/r/dartlang/comments/abc/", stub.permalink)
    }

    @Test
    fun moreWithoutAnIdUsesItsFirstChildsId() {
        assertEquals("first_child", comments(listing("""{"kind":"more","data":{"count":2,"children":["first_child","second"]}}""")).single().id)
    }

    @Test
    fun anEmptyRepliesStringIsNoReplies() {
        assertTrue(comments(listing(t1("a"))).single().replies.isEmpty())
    }

    @Test
    fun theSubmitterFlagAndMediaTokensAreRead() {
        val comment = comments(listing(t1("op", isSubmitter = true, body = "![gif](giphy|l0HlvtIPzPdt2usKs|downsized)"))).single()

        assertTrue(comment.isSubmitter)
        assertEquals(listOf("https://media.giphy.com/media/l0HlvtIPzPdt2usKs/giphy.gif"), comment.mediaUrls)
        assertTrue(comment.body.isEmpty())
    }

    @Test
    fun aBareImageUrlBecomesMediaAndLeavesNoBody() {
        val comment = comments(listing(t1("pic", body = "https://i.redd.it/abc.gif"))).single()

        assertEquals(listOf("https://i.redd.it/abc.gif"), comment.mediaUrls)
        assertTrue(comment.body.isEmpty())
    }

    @Test
    fun aPlaceholderBesideAPictureIsDroppedAndACaptionKeepsItsWords() {
        val leftover = comments(listing(t1("pic", body = ">image> https://i.redd.it/abc.jpg"))).single()
        val caption = comments(listing(t1("pic", body = "nice one https://i.redd.it/abc.jpg"))).single()

        assertEquals(listOf("https://i.redd.it/abc.jpg"), leftover.mediaUrls)
        assertTrue(leftover.body.isEmpty())
        assertEquals(listOf("https://i.redd.it/abc.jpg"), caption.mediaUrls)
        assertEquals("nice one", caption.body)
    }

    @Test
    fun markdownLinksToPagesStayInTheBodySoTheyCanBeOpened() {
        val comment = comments(
            listing(t1("link", body = "Download>> [OTs-14](https://drive.google.com/drive/folders/abc) [Source](https://x.com/foo/status/1)")),
        ).single()

        assertTrue(comment.mediaUrls.isEmpty())
        assertTrue("[OTs-14](" in comment.body)
        assertTrue("[Source](" in comment.body)
    }

    private fun comment(
        id: String,
        author: String,
        body: String,
        score: String = "42 points",
        replies: String = "",
    ) = """
        <div class="thing id-t1_$id comment" data-fullname="t1_$id" data-author="$author">
          <div class="entry unvoted">
            <p class="tagline">
              <a href="/user/$author" class="author">$author</a>
              <span class="score unvoted">$score</span>
              <time datetime="2026-07-01T10:00:00+00:00">1 hour ago</time>
            </p>
            <form class="usertext"><div class="usertext-body"><div class="md"><p>$body</p></div></div></form>
          </div>
          <div class="child">${if (replies.isEmpty()) "" else """<div class="sitetable listing">$replies</div>"""}</div>
        </div>"""

    private fun threadPage(comments: String, post: String = "") = """
        <!doctype html><html><body>
          <div id="siteTable">$post</div>
          <div class="commentarea"><div class="sitetable nestedlisting">$comments</div></div>
        </body></html>"""

    private fun scraped(body: String, postPermalink: String? = null) = RedditOldComments.comments(body, postPermalink)

    private fun only(body: String) = scraped(threadPage(comment("a", "someone", body))).single()

    @Test
    fun aScrapedCommentHasItsAuthorBodyScoreAndTime() {
        val parsed = only("Well said")

        assertEquals("a", parsed.id)
        assertEquals("someone", parsed.author)
        assertEquals("Well said", parsed.body)
        assertEquals(42, parsed.score)
        assertEquals(Instant.parse("2026-07-01T10:00:00Z"), parsed.createdAt)
    }

    @Test
    fun aPictureCommentShowsThePictureNotTheUrlThatMadeItAndIsKept() {
        val parsed = only("""<a href="https://i.redd.it/abc.gif">https://i.redd.it/abc.gif</a>""")

        assertEquals(listOf("https://i.redd.it/abc.gif"), parsed.mediaUrls)
        assertTrue(parsed.body.isEmpty(), "the text was only the link the picture came from")
    }

    @Test
    fun paragraphsLineBreaksListsAndCodeKeepTheirShape() {
        val body = "First.</p>\n\n<p>Second,<br>\nwith a break.</p>\n" +
            "<ol>\n<li>one</li>\n<li>two</li>\n</ol>\n" +
            "<ul><li>a point</li></ul>\n" +
            "<pre><code>if (x) {\n  y();\n}\n</code></pre>\n<p>Last."

        assertEquals(
            "First.\n\nSecond,\nwith a break.\n\n1. one\n2. two\n\n• a point\n\nif (x) {\n  y();\n}\n\nLast.",
            only(body).body,
            "code keeps its spaces, as ones the folding of spaces leaves alone",
        )
    }

    @Test
    fun aNumberedListCountsFromItsStart() {
        assertEquals("3. three\n4. four", only("""</p><ol start="3"><li>three</li><li>four</li></ol><p>""").body)
    }

    @Test
    fun wordsAroundAPictureLinkSurvive() {
        val parsed = only("""look at <a href="https://i.redd.it/abc.jpg">this</a> please""")

        assertEquals(listOf("https://i.redd.it/abc.jpg"), parsed.mediaUrls)
        assertEquals("look at this please", parsed.body)
    }

    @Test
    fun aLabelledPageLinkIsKeptAsMarkdownAndABareOneAsText() {
        val labelled = only("""Download>> <a href="https://drive.google.com/drive/folders/abc">OTs-14</a> <a href="https://x.com/foo/status/1">Source</a>""")
        val bare = only("""<a href="https://example.com/story">https://example.com/story</a>""")

        assertTrue(labelled.mediaUrls.isEmpty())
        assertEquals("Download>> [OTs-14](https://drive.google.com/drive/folders/abc) [Source](https://x.com/foo/status/1)", labelled.body)
        assertTrue(bare.mediaUrls.isEmpty())
        assertEquals("https://example.com/story", bare.body)
    }

    @Test
    fun theSamePictureTwiceIsShownOnce() {
        assertEquals(
            listOf("https://i.redd.it/x.gif"),
            only("""<a href="https://i.redd.it/x.gif">a</a> <a href="https://i.redd.it/x.gif">b</a>""").mediaUrls,
        )
    }

    @Test
    fun aRedditGifTokenBecomesTheGifAndNeverShowsAsText() {
        val alone = only("![gif](giphy|l0HlvtIPzPdt2usKs|downsized)")
        val beside = only("this exactly ![gif](giphy|abc123XYZ|downsized)")

        assertEquals(listOf("https://media.giphy.com/media/l0HlvtIPzPdt2usKs/giphy.gif"), alone.mediaUrls)
        assertTrue(alone.body.isEmpty())
        assertEquals(1, beside.mediaUrls.size)
        assertEquals("this exactly", beside.body)
    }

    @Test
    fun anInlinedPictureIsPickedUpAndItsPlaceholderDropped() {
        val leftover = only(""">image> <img src="https://i.redd.it/inline.png">""")

        assertEquals(listOf("https://i.redd.it/inline.png"), leftover.mediaUrls)
        assertTrue(leftover.body.isEmpty())
        assertEquals(listOf("https://i.redd.it/inline.png"), only("""<img src="//i.redd.it/inline.png">""").mediaUrls)
    }

    @Test
    fun repliesHangOffTheCommentTheyAnswerAsDeepAsThePageGoes() {
        val deep = comment("a", "x", "one", replies = comment("b", "y", "two", replies = comment("c", "z", "three")))
        val root = scraped(threadPage(deep)).single()

        assertEquals("one", root.body, "a comment does not swallow its replies' words")
        assertEquals("two", root.replies.single().body)
        assertEquals("three", root.replies.single().replies.single().body)
        assertEquals(3, root.totalCount)
        assertEquals(listOf("a", "b"), scraped(threadPage(comment("a", "x", "first") + comment("b", "y", "second"))).map { it.id })
    }

    @Test
    fun aHiddenScoreIsAbsentRatherThanZero() {
        val noScore = """<div class="thing comment" data-fullname="t1_n" data-author="x">
            <div class="entry"><div class="usertext-body"><div class="md">New here</div></div></div></div>"""

        assertNull(scraped(threadPage(noScore)).single().score)
    }

    @Test
    fun theSubmitterAndTheAuthorsFlairAreReadOffTheTagline() {
        val op = """<div class="thing comment" data-fullname="t1_o" data-author="poster">
            <div class="entry"><p class="tagline"><a class="author submitter">poster</a></p>
            <div class="usertext-body"><div class="md">Mine</div></div></div></div>"""
        val flaired = """<div class="thing comment" data-fullname="t1_f" data-author="mod">
            <div class="entry"><p class="tagline"><a class="author">mod</a>
            <span class="flair flair-team" title="Mod team">Mod team</span></p>
            <div class="usertext-body"><div class="md">Rule 3</div></div></div></div>"""

        assertTrue(scraped(threadPage(op)).single().isSubmitter)
        assertEquals("Mod team", scraped(threadPage(flaired)).single().authorFlair)
        assertNull(only("No flair").authorFlair)
    }

    @Test
    fun rowsThatAreNotCommentsAreSkipped() {
        val more = """<div class="thing morechildren" data-fullname="t1_more_x"></div>"""
        val empty = """<div class="thing comment" data-fullname="t1_d"><div class="entry"></div></div>"""

        assertEquals(listOf("a"), scraped(threadPage(more + comment("a", "x", "real"))).map { it.id })
        assertTrue(scraped(threadPage(empty)).isEmpty(), "a deleted comment with no words is left out")
        assertTrue(scraped("<html><body>nothing</body></html>").isEmpty())
        assertTrue(scraped("").isEmpty())
    }

    @Test
    fun flatteningIsDepthFirstWithTheDepthAlongside() {
        val page = threadPage(comment("a", "x", "one", replies = comment("b", "y", "two", replies = comment("c", "z", "three")) + comment("d", "w", "four")))
        val flat = flattenComments(scraped(page))

        assertEquals(listOf("a", "b", "c", "d"), flat.map { it.comment.id })
        assertEquals(listOf(0, 1, 2, 1), flat.map { it.depth })
        assertTrue(flattenComments(emptyList()).isEmpty())
    }

    @Test
    fun thePostsOwnWordsAreReadOffItsThreadPage() {
        val post = """<div class="thing" data-fullname="t3_p"><div class="expando"><form class="usertext">
            <div class="usertext-body"><div class="md"><p>The full text</p></div></div></form></div></div>"""

        assertEquals("The full text", RedditOldComments.selfText(threadPage("", post)))
        assertNull(RedditOldComments.selfText(threadPage(comment("a", "x", "hi"))), "a link post has none")
    }

    private fun postThing(dataUrl: String? = null, expando: String = "") = """
        <div class="thing id-t3_p1 link" data-fullname="t3_p1"${dataUrl?.let { " data-url=\"$it\"" }.orEmpty()}>
          <div class="entry"><p class="title"><a class="title" href="/r/x/comments/p1/t/">A post</a></p></div>
          <div class="expando">$expando</div>
        </div>"""

    private fun media(dataUrl: String? = null, expando: String = "") = RedditOldComments.postMedia(threadPage("", postThing(dataUrl, expando)))

    @Test
    fun thePostsOutboundLinkIsReadOffItsRowButARelativeOneIsNoLink() {
        assertEquals(PostMedia("https://i.redd.it/abc.jpg", emptyList()), media("https://i.redd.it/abc.jpg"))
        assertNull(media("/r/x/comments/p1/t/").url)
    }

    @Test
    fun anExpandedGalleryLeavesItsFilesOnThePageInOrderAndUnescaped() {
        val gallery = """<div class="media-gallery"><img src="https://preview.redd.it/one.jpg?width=640&amp;s=a"><img src="https://preview.redd.it/two.jpg?width=640&amp;s=b"></div>"""

        assertEquals(
            listOf("https://preview.redd.it/one.jpg?width=640&s=a", "https://preview.redd.it/two.jpg?width=640&s=b"),
            media("https://www.reddit.com/gallery/p1", gallery).images,
        )
    }

    @Test
    fun onlyRedditHostedFilesCountAndTheSameFileIsOneFile() {
        assertEquals(
            listOf("https://i.redd.it/real.png"),
            media("https://example.com/story", """<img src="https://example.com/pixel.gif"><img src="https://i.redd.it/real.png">""").images,
        )
        assertEquals(listOf("https://i.redd.it/a.png"), media(expando = """<img src="https://i.redd.it/a.png"><img src="https://i.redd.it/a.png">""").images)
    }

    @Test
    fun copiesOfOnePictureCollapseToTheBestOne() {
        val gallery = """<div class="media-gallery"><img src="https://preview.redd.it/a.jpg?width=320&amp;s=lo">""" +
            """<img src="https://preview.redd.it/a.jpg?width=1080&amp;s=hi"><img src="https://i.redd.it/a.jpg"></div>"""

        assertEquals(listOf("https://i.redd.it/a.jpg"), media("https://www.reddit.com/gallery/p1", gallery).images)
        assertEquals(
            listOf("https://preview.redd.it/abc.jpg?width=1080&s=b"),
            media(expando = """<img src="https://preview.redd.it/abc.jpg?width=320&amp;s=a"><img src="https://preview.redd.it/abc.jpg?width=1080&amp;s=b">""").images,
        )
        assertEquals(PostMedia(null, emptyList()), RedditOldComments.postMedia("<html><body></body></html>"))
    }

    @Test
    fun aLoadMoreControlBecomesAStubPointingAtItsParent() {
        val withMore = comment(
            "a",
            "ann",
            "Parent",
            replies = """<div class="thing morechildren"><a href="javascript:void(0)">load more comments</a> (34 replies)</div>""",
        ).replaceFirst("data-author=\"ann\"", "data-author=\"ann\" data-permalink=\"/r/x/comments/p/t/a/\"")
        val flat = flattenComments(scraped(threadPage(withMore)))

        assertEquals(2, flat.size)
        assertTrue(flat[1].comment.isStub)
        assertEquals(34, flat[1].comment.moreCount)
        assertEquals("/r/x/comments/p/t/a/", flat[1].comment.permalink)
        assertEquals(1, flat[1].depth, "the held-back replies sit under their parent")
    }

    @Test
    fun aDeepThreadContinuationCarriesItsOwnTarget() {
        val deep = comment("a", "ann", "Deep", replies = """<div class="thing morerecursion"><a href="/r/x/comments/p/t/deep/">continue this thread</a></div>""")
        val stub = flattenComments(scraped(threadPage(deep)))[1].comment

        assertTrue(stub.isStub)
        assertEquals("/r/x/comments/p/t/deep/", stub.permalink)
    }

    @Test
    fun aControlWithNowhereToGoIsDroppedRatherThanDeadOnScreen() {
        val page = threadPage(comment("a", "ann", "Parent", replies = """<div class="thing morechildren"><a href="javascript:void(0)">load more comments</a></div>"""))

        assertEquals(1, flattenComments(scraped(page)).size)
    }

    @Test
    fun aRootLoadMoreRowSurvivesPointingAtThePostPage() {
        val page = threadPage(comment("a", "someone", "First") + """<div class="thing morechildren"><a href="#">load more comments</a> (512 replies)</div>""")
        val parsed = scraped(page, "/r/dartlang/comments/abc/")

        assertEquals(2, parsed.size)
        assertTrue(parsed.last().isStub)
        assertEquals(512, parsed.last().moreCount)
        assertEquals("/r/dartlang/comments/abc/", parsed.last().permalink)
    }

    @Test
    fun aRepeatedTopLevelThreadLoadedInPlaceIsDroppedWithItsReplies() {
        val shown = flattenComments(listOf(RedditComment(id = "a", body = "Shown")))
        val incoming = flattenComments(
            listOf(
                RedditComment(id = "a", body = "Shown again", replies = listOf(RedditComment(id = "a1", body = "Its reply"))),
                RedditComment(id = "b", body = "New", replies = listOf(RedditComment(id = "b1", body = "New reply"))),
            ),
        )

        assertEquals(listOf("b", "b1"), withoutShownThreads(shown, incoming).map { it.comment.id })
    }

    @Test
    fun aRemovedCommentIsToldApartFromAStubAndFromAGoneAuthor() {
        assertTrue(RedditComment(id = "r", body = "[removed]").isRemoved)
        assertFalse(RedditComment(id = "s", body = "", permalink = "/r/x/comments/y/", moreCount = 3).isRemoved)
        assertFalse(RedditComment(id = "g", body = "still says something", author = "[deleted]").isRemoved)
    }
}
