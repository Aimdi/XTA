package com.aimdi.xtanative.networks.reddit

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** www.reddit.com's pages as XTA's reddit_www_test.dart reads them, from the pages it recorded on 2026-10-20 to 22. */
class RedditWwwTest {
    private fun onePost(attributes: String, inside: String = ""): RedditPost = RedditWww.listing(
        """<html><body><shreddit-app>
          <shreddit-post id="t3_a" permalink="/r/x/comments/a/t/" post-title="T" subreddit-name="x" $attributes>$inside</shreddit-post>
        </shreddit-app></body></html>""",
    ).posts.single()

    @Test
    fun aPicturePostIsReadOutOfItsAttributes() {
        val page = RedditWww.listing(fixture("listing_pics.html"))
        val post = page.posts.first()

        assertEquals(WwwPageKind.READ, page.kind)
        assertEquals("1xawic7", post.id, "the t3_ prefix is the fullname, not the id")
        assertEquals("Early voting today in north Dallas [oc]", post.title)
        assertEquals("pics", post.subreddit)
        assertEquals("d4wnn", post.author)
        assertEquals(12287, post.score)
        assertEquals(411, post.commentCount)
        assertEquals(Instant.parse("2026-10-20T16:17:20.817Z"), post.createdAt)
        assertEquals("/r/pics/comments/1xawic7/early_voting_today_in_north_dallas_oc/", post.permalink)
        assertFalse(post.isSelf)
        assertEquals("https://i.redd.it/bm3fgrkkdnwh1.jpeg", post.url)
        assertEquals("https://i.redd.it/bm3fgrkkdnwh1.jpeg", post.imageUrl)
        assertEquals("Politics", post.flair)
        assertTrue(post.previewImage!!.startsWith("https://preview.redd.it/"))
    }

    @Test
    fun aPostItsMediaMarksNsfwOrPinnedIsReadSoAsIsOneInAnNsfwCommunity() {
        fun flags(telemetry: String) = onePost("", """<shreddit-media-lightbox-listener telemetry="$telemetry"></shreddit-media-lightbox-listener>""")
            .let { it.over18 to it.stickied }

        assertEquals(true to false, flags("{&quot;post&quot;:{&quot;nsfw&quot;:true}}"))
        assertEquals(false to true, flags("{&quot;post&quot;:{&quot;pinned&quot;:true}}"))
        assertEquals(true to false, flags("{&quot;subreddit&quot;:{&quot;nsfw&quot;:true}}"))
        assertEquals(false to false, flags("not json"))
        val pictures = RedditWww.listing(fixture("listing_pics.html")).posts.first()
        assertEquals(false to false, pictures.over18 to pictures.stickied)
    }

    @Test
    fun aPostInAnNsfwCommunityOrBehindABlurIsMarkedAsRedditMarksIt() {
        fun flags(attributes: String, inside: String = "") = onePost(attributes, inside).let { it.over18 to it.spoiler }

        assertEquals(true to false, flags("subreddit-nsfw"))
        assertEquals(true to false, flags("", """<shreddit-blurred-container reason="nsfw" mode="slot"></shreddit-blurred-container>"""))
        assertEquals(false to true, flags("", """<shreddit-blurred-container reason="spoiler" mode="wrap"></shreddit-blurred-container>"""))
        assertEquals(false to false, flags(""))
    }

    @Test
    fun aGalleryCarriesEveryPictureInOrderAtTheSizeItsFeedShows() {
        val post = RedditWww.listing(fixture("listing_pics.html")).posts[1]

        assertEquals("https://www.reddit.com/gallery/1xb4rqw", post.url)
        assertEquals(9, post.galleryImages.size)
        assertTrue("28bh0dy5xowh1.jpeg?width=640" in post.galleryImages[0])
        assertTrue("jdl2e1y5xowh1.jpeg?width=640" in post.galleryImages[1], "lazy pages too")
    }

    @Test
    fun aCrosspostOfOnePictureLinksThePictureAndOfAGalleryTheOriginal() {
        val posts = RedditWww.listing(fixture("listing_pics.html")).posts

        assertEquals("https://www.reddit.com/r/portugal/comments/1x9anef/protestos_no_porto_171026/", posts[2].url)
        assertEquals(20, posts[2].galleryImages.size)
        assertEquals("https://i.redd.it/tlguz3aj4owh1.jpeg", posts[3].url)
        assertNotNull(posts[3].imageUrl)
    }

    @Test
    fun aVideoPlaysItsStreamUnderItsPosterFrame() {
        val post = RedditWww.listing(fixture("listing_aww.html")).posts.first()

        assertEquals("https://v.redd.it/0570b89w9owh1", post.url)
        assertTrue(post.videoManifestUrl!!.startsWith("https://v.redd.it/0570b89w9owh1/HLSPlaylist.m3u8?"))
        assertTrue(post.hasPlayableVideo)
        assertTrue(post.previewImage!!.startsWith("https://external-preview.redd.it/"))
    }

    @Test
    fun aSelfPostCarriesItsTextWithItsLinks() {
        val post = RedditWww.listing(fixture("listing_flutterdev_top.html")).posts[1]

        assertTrue(post.isSelf)
        assertNull(post.url)
        assertEquals("self.FlutterDev", post.domain)
        assertEquals("Article", post.flair)
        assertTrue(post.selfText!!.startsWith("Spent a while figuring out why #Flutter text looks lighter"))
        assertTrue("https://birju.dev/" in post.selfText!!)
        assertFalse("  " in post.selfText!!, "the markup's indentation is not text")
    }

    @Test
    fun aLinkPostLinksItsArticleWithItsThumbnail() {
        val post = RedditWww.listing(fixture("listing_android.html")).posts.single()

        assertFalse(post.isSelf)
        assertEquals("https://sammyguru.com/galaxy-s27-ultra-snapdragon-overheating/", post.url)
        assertEquals("sammyguru.com", post.domain)
        assertTrue(post.thumbnailUrl!!.startsWith("https://external-preview.redd.it/"))
        assertEquals("Rumour", post.flair)
    }

    @Test
    fun theNextPageStartsAfterTheFullnameItsPartialNames() {
        assertEquals("t3_1x9w7f4", RedditWww.listing(fixture("listing_pics.html")).after)
    }

    @Test
    fun aProfileNamesItsNextPageOnItsPostsInstead() {
        val page = RedditWww.listing(fixture("profile_posts.html"))

        assertEquals(listOf("1vgbkge", "1tvsa59", "3cxedn"), page.posts.map { it.id })
        assertEquals("u_spez", page.posts.first().subreddit, "a profile post lives in the account's own community")
        assertEquals("t3_3cxedn", page.after)
    }

    @Test
    fun aCommunityWithNothingInItReadsAsEmptyNotAsAFailure() {
        val page = RedditWww.listing(WWW_EMPTY)

        assertEquals(WwwPageKind.READ, page.kind)
        assertTrue(page.posts.isEmpty())
        assertNull(page.after)
    }

    @Test
    fun aPostMissingWhatEveryPostHasIsSkippedNotGuessedAt() {
        val page = RedditWww.listing(
            """<html><body><shreddit-app>
              <shreddit-post id="t3_a" post-title="No permalink"></shreddit-post>
              <shreddit-post permalink="/r/x/comments/b/t/" post-title="No id"></shreddit-post>
              <shreddit-post id="t3_c" permalink="/r/x/comments/c/t/" post-title="Kept" subreddit-name="x"></shreddit-post>
            </shreddit-app></body></html>""",
        )

        assertEquals(listOf("c"), page.posts.map { it.id })
    }

    @Test
    fun whatWwwAnsweredWithIsRecognised() {
        assertEquals(WwwPageKind.BROWSER_CHECK, RedditWww.pageKind(fixture("browser_check.html")))
        assertEquals(WwwPageKind.BROWSER_CHECK, RedditWww.pageKind(RedditWww.strip(fixture("browser_check.html"))), "cutting the page keeps the check")
        assertEquals(
            WwwPageKind.BLOCKED,
            RedditWww.pageKind("<html><body><shreddit-app>You've been blocked by network security.</shreddit-app></body></html>"),
        )
        assertEquals(
            WwwPageKind.READ,
            RedditWww.pageKind("""<shreddit-app><shreddit-post id="t3_a" post-title="Blocked by network security?"></shreddit-post>"""),
            "a page with posts is read, whatever a title on it says",
        )
        assertEquals(WwwPageKind.UNREADABLE, RedditWww.pageKind("<shreddit-app>Page not found</shreddit-app>"))
        assertEquals(WwwPageKind.UNREADABLE, RedditWww.pageKind("""{"kind": "Listing"}"""))
    }

    @Test
    fun pagingCursorsAreBase64OfTheFullnameWithOrWithoutPadding() {
        assertEquals("t3_1x79qot", RedditWww.cursorName("dDNfMXg3OXFvdA=="))
        assertEquals("t3_3cxedn", RedditWww.cursorName("dDNfM2N4ZWRu"))
        assertEquals("dDNfMXg3OXFvdA==", RedditWww.cursorParam("t3_1x79qot"))
    }

    @Test
    fun anythingElseIsNoCursor() {
        assertNull(RedditWww.cursorName("not base64!"))
        assertNull(RedditWww.cursorName("aGVsbG8="), "\"hello\" is no fullname")
        assertNull(RedditWww.cursorName(""))
    }

    @Test
    fun aThreadNestsRepliesUnderTheCommentTheyAnswer() {
        val thread = RedditWwwComments.thread(fixture("comments.html"))

        assertEquals(WwwPageKind.READ, thread.kind)
        assertEquals(listOf("pht7lgf", "phride0"), thread.comments.map { it.id })
        assertEquals(
            listOf(
                "pht7lgf" to 0,
                "pht92w5" to 1,
                "phride0" to 0,
                "phuyrt3" to 1,
                "more-/r/FlutterDev/comments/1xa75i8/comment/phuyrt3/" to 2,
            ),
            flattenComments(thread.comments).map { it.comment.id to it.depth },
        )
    }

    @Test
    fun aCommentIsReadOutOfItsAttributesAndItsOwnWords() {
        val first = RedditWwwComments.thread(fixture("comments.html")).comments.first()

        assertEquals("TJGhinder", first.author)
        assertEquals(7, first.score)
        assertEquals(Instant.parse("2026-10-20T01:22:39.120Z"), first.createdAt)
        assertEquals("/r/FlutterDev/comments/1xa75i8/comment/pht7lgf/", first.permalink)
        assertTrue(first.body.startsWith("Loved this write up!!"))
        assertFalse("Thanks" in first.body, "a reply's words are the reply's")
        assertFalse(first.isSubmitter)
        assertTrue(first.replies.single().isSubmitter, "is-op marks the post's author")
    }

    @Test
    fun repliesHeldBackAreAStubThatOpensTheirParentsPage() {
        val stub = flattenComments(RedditWwwComments.thread(fixture("comments.html")).comments).last().comment

        assertTrue(stub.isStub)
        assertEquals(1, stub.moreCount)
        assertEquals("/r/FlutterDev/comments/1xa75i8/comment/phuyrt3/", stub.permalink)
    }

    @Test
    fun topLevelCommentsHeldBackAreSaidSo() {
        assertTrue(RedditWwwComments.thread(fixture("comments.html")).moreAtRoot)
    }

    @Test
    fun aCommentsOwnPageKeepsThatCommentAndItsReplies() {
        val thread = RedditWwwComments.thread(
            """<html><body><shreddit-app><shreddit-comment-tree thingid="t1_child">
              <shreddit-comment thingid="t1_parent" author="a" depth="0">
                <div slot="comment"><div class="md"><p>Parent</p></div></div>
                <shreddit-comment thingid="t1_child" author="b" depth="1" parentid="t1_parent">
                  <div slot="comment"><div class="md"><p>Child</p></div></div>
                  <shreddit-comment thingid="t1_grandchild" author="c" depth="2" parentid="t1_child">
                    <div slot="comment"><div class="md"><p>Grandchild</p></div></div>
                  </shreddit-comment>
                </shreddit-comment>
              </shreddit-comment>
              <shreddit-comment thingid="t1_other" author="d" depth="0">
                <div slot="comment"><div class="md"><p>Elsewhere</p></div></div>
              </shreddit-comment>
            </shreddit-comment-tree></shreddit-app></body></html>""",
        )

        assertEquals("Child", thread.comments.single().body)
        assertEquals("Grandchild", thread.comments.single().replies.single().body)
        assertFalse(thread.moreAtRoot)
    }

    @Test
    fun aCommentWhoseWordsAreGoneKeepsItsRowForItsReplies() {
        val gone = RedditWwwComments.thread(
            """<html><body><shreddit-app><shreddit-comment-tree>
              <shreddit-comment thingid="t1_gone" author="[deleted]" depth="0">
                <shreddit-comment thingid="t1_reply" author="b" depth="1" parentid="t1_gone">
                  <div slot="comment"><div class="md"><p>Still here</p></div></div>
                </shreddit-comment>
              </shreddit-comment>
            </shreddit-comment-tree></shreddit-app></body></html>""",
        ).comments.single()

        assertTrue(gone.isRemoved)
        assertFalse(gone.hasAuthor)
        assertEquals("Still here", gone.replies.single().body)
    }

    @Test
    fun aBigThreadKeepsTheCommentsRedditRendersLateAndWhereTheRestAre() {
        val thread = RedditWwwComments.thread(fixture("comments_big.html"))

        assertEquals(listOf("zizban", "No_Bandicoot2306", "dokelyok", "DrooMighty"), thread.comments.map { it.author })
        assertTrue(thread.comments.all { it.body.isNotEmpty() })
        assertTrue(thread.comments.first().replies.single().isStub, "its replies are held back")
        assertTrue(thread.moreAtRoot)
        assertEquals(
            "/svc/shreddit/more-comments/AskReddit/t3_1xbzqmk?top-level=1&comments-remaining=90&commentPositionOffset=10",
            thread.next?.path,
        )
        assertTrue(thread.next!!.cursor.startsWith("Y29tbWVudHRyZWU6"))
    }

    @Test
    fun theBrowserCheckHoldsNoThread() {
        val thread = RedditWwwComments.thread(fixture("browser_check.html"))

        assertEquals(WwwPageKind.BROWSER_CHECK, thread.kind)
        assertTrue(thread.comments.isEmpty())
    }

    @Test
    fun templatesGoWholeNestedOnesTooUnlessTheyHoldThePagesOwnContent() {
        val page = "<head>x</head><body><p>kept</p>" +
            "<template><shreddit-comment-action-row></shreddit-comment-action-row></template>" +
            "<ac-render-template><template>" +
            "<shreddit-comment thingid=\"t1_a\"><template><b>row</b></template><p>lazy</p></shreddit-comment>" +
            "</template></ac-render-template>" +
            "<script>var x = \"<template>\";</script></body>"

        val cut = RedditWww.strip(page)
        assertEquals(
            "<body><p>kept</p><ac-render-template><shreddit-comment thingid=\"t1_a\"><p>lazy</p></shreddit-comment></ac-render-template></body>",
            cut,
        )
        assertEquals(cut, RedditWww.strip(cut), "cutting twice cuts nothing more")
        assertEquals("<body>", RedditWww.strip("<body><template><p>never closed"))
    }

    @Test
    fun theNextCommentsComeWithWhereTheOnesAfterThemAre() {
        val more = """
            <shreddit-comment thingid="t1_x" author="a" depth="0" permalink="/r/q/comments/p/comment/x/">
              <div slot="comment"><div class="md"><p>Eleventh</p></div></div>
              <shreddit-comment thingid="t1_y" author="b" depth="1" parentid="t1_x">
                <div slot="comment"><div class="md"><p>Its reply</p></div></div>
              </shreddit-comment>
            </shreddit-comment>
            <faceplate-partial id="top-level-more-comments-partial" method="post"
                src="/svc/shreddit/more-comments/q/t3_p?top-level=1&amp;commentPositionOffset=20">
              <input type="hidden" name="cursor" value="next-cursor">
            </faceplate-partial>"""

        for (body in listOf(more, "<html><body><shreddit-comment-tree>$more</shreddit-comment-tree></body></html>")) {
            val page = RedditWwwComments.more(body)
            assertEquals("Eleventh", page.comments.single().body)
            assertEquals("Its reply", page.comments.single().replies.single().body)
            assertEquals(RedditCommentsCursor("/svc/shreddit/more-comments/q/t3_p?top-level=1&commentPositionOffset=20", "next-cursor"), page.next)
        }
    }

    @Test
    fun theLastOfThemOrAPageThatIsNotThemNamesNothingFurther() {
        val last = RedditWwwComments.more("""<shreddit-comment thingid="t1_z" depth="0"><div slot="comment"><p>End</p></div></shreddit-comment>""")

        assertEquals("End", last.comments.single().body)
        assertFalse(last.moreAtRoot)
        assertNull(last.next)
        assertEquals(WwwPageKind.BROWSER_CHECK, RedditWwwComments.more(fixture("browser_check.html")).kind)
    }

    @Test
    fun searchPostsComeFromTheirTrackingJsonAndTheirCounterRow() {
        val posts = RedditWwwSearch.posts(fixture("search_posts.html"))
        val first = posts.first()

        assertEquals(3, posts.size)
        assertEquals("1xb5vks", first.id)
        assertEquals("🚀 I built Flutter Version Bumper — a CLI tool to automate Flutter & Dart releases", first.title)
        assertEquals("/r/FlutterDev/comments/1xb5vks/i_built_flutter_version_bumper_a_cli_tool_to/", first.permalink)
        assertEquals("FlutterDev", first.subreddit)
        assertEquals("CandidateWhich7277", first.author)
        assertEquals(1, first.score)
        assertEquals(0, first.commentCount)
        assertEquals(Instant.parse("2026-10-20T22:15:02.125Z"), first.createdAt)
        assertTrue(first.isSelf, "a result names no link target")
        assertTrue(posts[1].thumbnailUrl!!.startsWith("https://preview.redd.it/"))
    }

    @Test
    fun searchedCommunitiesCarryTheirPictureAndDescription() {
        val communities = RedditWwwSearch.communities(fixture("search_communities.html"))
        val first = communities.first()

        assertEquals(listOf("FlutterDev", "flutterhelp", "FlutterApps"), communities.map { it.name })
        assertEquals("A community for the publishing of news and discussion about Flutter.", first.description)
        assertEquals("https://styles.redditmedia.com/t5_2x3q8/styles/communityIcon_dsw8tf6mg06b1.png", first.iconUrl)
        assertNull(first.subscribers, "weekly visitors are not subscribers")
    }

    @Test
    fun searchedPeopleCarryTheirKarma() {
        val people = RedditWwwSearch.people(fixture("search_people.html"))

        assertEquals(RedditUserResult("spez", 941063), people.first())
        assertEquals(3, people.size)
    }

    @Test
    fun aPageThatIsNotASearchYieldsNothingRatherThanThrowing() {
        assertTrue(RedditWwwSearch.posts(fixture("browser_check.html")).isEmpty())
        assertTrue(RedditWwwSearch.communities("<html><body>nothing</body></html>").isEmpty())
    }

    private val thread = "/r/FlutterDev/comments/1xa75i8/why_flutter_text_looks_lighter_than_swiftui_on_ios/"

    private fun embed(name: String, permalink: String = thread) = RedditWwwEmbed.post(fixture(name), permalink)

    @Test
    fun anEmbeddedSelfPostBringsItsWordsAuthorCountsAndAge() {
        val post = embed("embed_self.html")!!

        assertEquals("1xa75i8", post.id)
        assertEquals("Why Flutter Text Looks Lighter Than SwiftUI on iOS", post.title)
        assertEquals("FlutterDev", post.subreddit)
        assertEquals(thread, post.permalink)
        assertEquals("birjuvachhani", post.author)
        assertEquals(68, post.score)
        assertEquals(15, post.commentCount)
        assertEquals(Instant.parse("2026-10-19T19:53:26.523Z"), post.createdAt)
        assertTrue(post.isSelf)
        assertNull(post.url)
        assertEquals("self.FlutterDev", post.domain)
        assertFalse(post.over18)
        assertTrue(post.selfText!!.startsWith("Spent a while figuring out why #Flutter text looks lighter"))
        assertTrue("on #iOS.\n\nWrote up the whole thing" in post.selfText!!, "paragraphs stay paragraphs")
        assertTrue(post.selfText!!.endsWith("https://birju.dev/posts/looking-into-flutter-vs-swiftui-font-rendering/"))
    }

    @Test
    fun aLongEmbeddedPostArrivesWholeNotCutWhereTheCardFadesIt() {
        val post = embed("embed_long.html", "/user/spez/comments/1vgbkge/modernizing_reddits_infrastructure_with_you/")!!

        assertTrue(post.selfText!!.length > 5000)
        assertTrue(post.selfText!!.endsWith("edit: added links"))
        assertEquals("u_spez", post.subreddit, "a profile post lives in the account's own community")
        assertEquals("/user/spez/comments/1vgbkge/modernizing_reddits_infrastructure_with_you/", post.permalink)
    }

    @Test
    fun anEmbeddedPicturePostLinksItsPictureUnderTheScaledCopy() {
        val post = embed("embed_image.html", "/r/pics/comments/1xawic7/early_voting_today_in_north_dallas_oc/")!!

        assertFalse(post.isSelf)
        assertEquals("https://i.redd.it/bm3fgrkkdnwh1.jpeg", post.url)
        assertEquals("https://i.redd.it/bm3fgrkkdnwh1.jpeg", post.imageUrl)
        assertTrue("preview.redd.it/early-voting-today-in-north-dallas-v0-bm3fgrkkdnwh1.jpeg" in post.previewImage!!)
        assertEquals("i.redd.it", post.domain)
    }

    @Test
    fun anEmbeddedGalleryBringsEveryPictureInOrder() {
        val post = embed("embed_gallery.html", "/r/pics/comments/1xb4rqw/i_painted_an_ocean_front_tennis_court_scene_set/")!!

        assertEquals("https://www.reddit.com/gallery/1xb4rqw", post.url)
        assertEquals(9, post.galleryImages.size)
        assertTrue("28bh0dy5xowh1.jpeg?width=640" in post.galleryImages[0])
        assertTrue("jdl2e1y5xowh1.jpeg?width=640" in post.galleryImages[1])
    }

    @Test
    fun anEmbeddedVideoBringsItsStreamAndPosterFrame() {
        val post = embed("embed_video.html", "/r/aww/comments/1xb1imj/update_odin_the_im_not_a_morning_person_chihuahua/")!!

        assertEquals("https://v.redd.it/0570b89w9owh1", post.url)
        assertTrue(post.videoManifestUrl!!.startsWith("https://v.redd.it/0570b89w9owh1/HLSPlaylist.m3u8?"))
        assertTrue(post.previewImage!!.startsWith("https://external-preview.redd.it/"))
        assertTrue(post.hasPlayableVideo)
        assertEquals(4888, post.score)
        assertEquals(356, post.commentCount)
    }

    @Test
    fun anEmbeddedLinkPostLinksItsArticle() {
        val post = embed("embed_link.html", "/r/Android/comments/1xaxenh/galaxy_s27_ultra_could_switch_to_exynos_2700_over/")!!

        assertEquals("https://sammyguru.com/galaxy-s27-ultra-snapdragon-overheating/", post.url)
        assertEquals("sammyguru.com", post.domain)
        assertFalse(post.isSelf)
        assertNull(post.selfText)
        assertEquals("rdh2dmd", post.author)
    }

    @Test
    fun anEmbeddedCrosspostsTitleIsItsOwnAndItLinksTheOriginalsPictures() {
        val post = embed("embed_crosspost.html", "/r/pics/comments/1xapc2a/protests_in_porto_portugal_171026/")!!

        assertEquals("Protests in Porto, Portugal. 17.10.26", post.title)
        assertEquals("https://www.reddit.com/gallery/1x9anef", post.url)
        assertEquals(1074, post.score)
        assertEquals(50, post.commentCount)
    }

    @Test
    fun aPostThatIsGoneAndAPageThatIsNotACardYieldNothing() {
        assertNull(embed("embed_gone.html", "/r/FlutterDev/comments/zzzzzzz/no_such_post/"))
        assertNull(embed("browser_check.html"))
        assertNull(RedditWwwEmbed.post("<html><body>nothing</body></html>", thread))
    }

    @Test
    fun aPageNamesItsCommunity() {
        assertEquals("FlutterDev", RedditWww.communityOf("/r/FlutterDev/comments/1xa75i8/why/"))
        assertEquals("u_spez", RedditWww.communityOf("/user/spez/comments/1vgbkge/x/"))
        assertEquals("u_spez", RedditWww.communityOf("/u/spez/comments/1vgbkge/x/"))
        assertNull(RedditWww.communityOf("/r/FlutterDev/about/"))
        assertNull(RedditWww.communityOf("/comments/1xa75i8/"))
    }
}
