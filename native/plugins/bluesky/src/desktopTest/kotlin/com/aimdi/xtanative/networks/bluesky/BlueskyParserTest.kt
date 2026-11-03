package com.aimdi.xtanative.networks.bluesky

import com.aimdi.xtanative.core.json.J
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlueskyParserTest {
    private val feed = BlueskyParser.feed(fixtureJson("author_feed.json")).associateBy { it.uri.substringAfterLast('/') }

    @Test
    fun aFeedItemReadsItsAuthorTextPicturesAndCounts() {
        val post = feed.getValue("pics")
        assertEquals("at://did:plc:alice/app.bsky.feed.post/pics", post.uri)
        assertEquals("bafy-pics", post.cid)
        assertEquals("alice.bsky.social", post.handle)
        assertEquals("did:plc:alice", post.did)
        assertEquals("Alice", post.authorName)
        assertEquals("https://cdn.bsky.app/img/avatar/plain/did:plc:alice/a@jpeg", post.avatarUrl)
        assertEquals("https://bsky.app/profile/alice.bsky.social/post/pics", post.url)
        assertEquals(at("2026-08-01T09:00:00Z"), post.publishedAt)
        assertEquals(listOf(2L, 3L, 4L, 1L), listOf(post.replyCount, post.repostCount, post.likeCount, post.quoteCount))
        assertFalse(post.isRepost)
        assertFalse(post.isReply)
    }

    @Test
    fun picturesPreferTheirFullSizeKeepTheirShapeAndAltAndAppearOnce() {
        val (first, second) = feed.getValue("pics").media
        assertEquals(2, feed.getValue("pics").media.size, "the third picture repeats the first")
        assertEquals("https://cdn.bsky.app/img/feed_fullsize/plain/did:plc:alice/1@jpeg", first.url)
        assertEquals(2200.0 / 1312, first.aspectRatio!!, 1e-9)
        assertEquals("A blue sky over the hills", first.alt)
        assertEquals("https://cdn.bsky.app/img/feed_thumbnail/plain/did:plc:alice/2@jpeg", second.url, "a thumbnail when there is no full size")
        assertNull(second.aspectRatio)
        assertFalse(second.isVideo)
    }

    @Test
    fun aRepostKeepsWhoRepostedItAndWhen() {
        val post = feed.getValue("boosted")
        assertTrue(post.isRepost)
        assertEquals("Bob", post.repostedByName)
        assertEquals("bob.bsky.social", post.repostedByHandle)
        assertEquals("did:plc:bob", post.repostedByDid)
        assertEquals("did:plc:bob", post.reposterActor)
        assertEquals(at("2026-08-01T12:00:00Z"), post.repostedAt)
        assertEquals(at("2026-07-30T08:00:00Z"), post.publishedAt)
        assertEquals(post.repostedAt, post.timelineDate, "a repost reaches the timeline when it was reposted")
        assertEquals("carol.bsky.social", post.handle)
    }

    @Test
    fun aReplyNamesWhomAndWhatItAnswers() {
        val post = feed.getValue("answer")
        assertTrue(post.isReply)
        assertEquals("bob.bsky.social", post.replyToHandle)
        assertEquals("at://did:plc:bob/app.bsky.feed.post/question", post.replyToUri)
    }

    @Test
    fun aVideoPlaysItsPlaylistWithItsThumbnailAsThePoster() {
        val video = feed.getValue("clip").media.single()
        assertTrue(video.isVideo)
        assertTrue(video.isPlayable)
        assertFalse(video.isGif)
        assertEquals("https://video.bsky.app/watch/did%3Aplc%3Aalice/bafy-video/playlist.m3u8", video.videoUrl)
        assertEquals("https://video.bsky.app/watch/did%3Aplc%3Aalice/bafy-video/thumbnail.jpg", video.url)
        assertEquals(1080.0 / 1920, video.aspectRatio!!, 1e-9)
        assertEquals("Waves at the coast", video.alt)
    }

    @Test
    fun anExternalEmbedIsALinkCardAndAPostWithOnlyACardIsKept() {
        val post = feed.getValue("story")
        assertEquals("", post.text)
        assertEquals(BlueskyLinkCard("https://example.org/story", "A story", null, "https://cdn.bsky.app/img/feed_thumbnail/plain/did:plc:alice/card@jpeg"), post.linkCard)
        assertTrue(post.media.isEmpty())
    }

    @Test
    fun aQuoteKeepsItsOwnPicturesButNotAQuoteOfItsOwn() {
        val quote = assertNotNull(feed.getValue("look").quotedPost)
        assertEquals("Quoted body", quote.text)
        assertEquals("carol.bsky.social", quote.handle)
        assertEquals("Carol", quote.authorName)
        assertEquals(7L, quote.likeCount)
        assertEquals(listOf("https://cdn.bsky.app/img/feed_fullsize/plain/did:plc:carol/q@jpeg"), quote.images)
        assertNull(quote.quotedPost)
        assertTrue(feed.getValue("look").media.isEmpty(), "the quote's pictures are the quote's")
    }

    @Test
    fun recordWithMediaCarriesTheQuoteBesideItsClip() {
        val post = feed.getValue("both")
        assertEquals("Quoted beside a clip", post.quotedPost?.text)
        val clip = post.media.single()
        assertTrue(clip.isGif)
        assertTrue(clip.isPlayable)
        assertEquals("https://video.bsky.app/watch/did%3Aplc%3Aalice/bafy-gif/thumbnail.jpg", clip.url)
    }

    @Test
    fun aQuoteOfAGonePostOrOfAFeedIsLeftOutButThePostStays() {
        assertNull(feed.getValue("gone-quote").quotedPost)
        assertEquals("This one is gone", feed.getValue("gone-quote").text)
        assertNull(feed.getValue("try-feed").quotedPost, "a quoted custom feed is no post")
    }

    @Test
    fun postsWithNothingToShowOrNoUriAreDropped() {
        assertEquals(11, feed.size)
        assertFalse("blank" in feed)
        assertTrue(feed.values.none { it.text == "no uri" })
    }

    @Test
    fun aPinReasonIsAnOrdinaryPost() {
        val post = feed.getValue("pinned")
        assertFalse(post.isRepost)
        assertNull(post.repostedAt)
    }

    @Test
    fun labelsComeFromThePostItsAuthorAndItsRecordWithoutNegations() {
        val post = feed.getValue("careful")
        assertEquals(listOf("graphic-media", "nudity", "!no-unauthenticated"), post.labels)
        assertTrue(post.isSensitive)
        assertTrue(post.isUnavailable, "its author asked readers without an account not to see it")
        assertTrue(post.warnsContent)
        assertFalse(feed.getValue("pics").warnsContent)
        assertTrue(BlueskyParser.labels(J.parse("""{"labels":false}"""), J(null)).isEmpty())
    }

    @Test
    fun aWarningLabelCoversAPostWithoutHidingIt() {
        val warned = bluePost("w", labels = listOf("!warn"))
        assertTrue(warned.warnsContent)
        assertFalse(warned.isUnavailable)
        assertTrue(bluePost("h", labels = listOf("!hide")).isUnavailable)
        assertFalse(bluePost("m", labels = listOf("nudity")).warnsContent, "a media label covers media, not the text")
    }

    @Test
    fun facetsAreReadInByteOrder() {
        val facets = feed.getValue("pics").facets
        assertEquals(
            listOf(
                BlueskyFacet(8, 24, BlueskyFacet.Kind.MENTION, "did:plc:bob"),
                BlueskyFacet(30, 38, BlueskyFacet.Kind.LINK, "https://bsky.app"),
                BlueskyFacet(39, 43, BlueskyFacet.Kind.TAG, "sky"),
            ),
            facets,
        )
        val runs = BlueskyFacets.runs(feed.getValue("pics").text, facets)
        assertEquals(listOf("@bob.bsky.social", "bsky.app", "#sky"), runs.filter { it.facet != null }.map { it.text })
    }

    @Test
    fun aReshapedFeedIsEmptyNotACrash() {
        assertTrue(BlueskyParser.feed(J(null)).isEmpty())
        assertTrue(BlueskyParser.feed(J.parse("""{"feed":"nope"}""")).isEmpty())
        assertTrue(BlueskyParser.feed(J.parse("""{"feed":[1,"two",null,{"post":[]}]}""")).isEmpty())
        assertTrue(BlueskyParser.posts(J.parse("not json")).isEmpty())
    }

    @Test
    fun aThreadFlattensAncestorsRootFirstAndRepliesDepthFirst() {
        val thread = assertNotNull(BlueskyParser.thread(fixtureJson("post_thread.json")))
        assertEquals("focal", thread.post.text)
        assertEquals(listOf("middle"), thread.ancestors.map { it.text }, "a post not found above is skipped")
        assertEquals(listOf("first reply", "nested", "the author again"), thread.replies.map { it.text }, "blocked and missing replies are skipped")
    }

    @Test
    fun aReplyKnowsItsParentFromItsRecordElseFromWhereItSits() {
        val (first, nested) = assertNotNull(BlueskyParser.thread(fixtureJson("post_thread.json"))).replies
        assertEquals("at://did:plc:alice/app.bsky.feed.post/focal", first.replyToUri)
        assertEquals(first.uri, nested.replyToUri, "the record names no parent; the thread does")
        assertTrue(nested.isReply)
    }

    @Test
    fun repliesDeeperThanXtaReadsAreLeftOut() {
        fun level(depth: Int): String = if (depth == 15) "[]" else """[{"post":${postView("r$depth")},"replies":${level(depth + 1)}}]"""
        val thread = assertNotNull(BlueskyParser.thread(J.parse("""{"thread":{"post":${postView("focal")},"replies":${level(0)}}}""")))
        assertEquals((0..12).map { "r$it" }, thread.replies.map { it.text })
    }

    @Test
    fun aThreadWhoseOpenedPostIsMissingIsNull() {
        assertNull(BlueskyParser.thread(J.parse("""{"thread":{"${'$'}type":"app.bsky.feed.defs#notFoundPost","uri":"at://x"}}""")))
        assertNull(BlueskyParser.thread(J.parse("{}")))
    }

    @Test
    fun aProfileReadsItsCountsBannerJoinDateAndPin() {
        val profile = BlueskyParser.profile(fixtureJson("profile.json"))
        assertEquals("did:plc:z72i7hdynmk6r22z27h6tvur", profile.did)
        assertEquals("bsky.app", profile.handle)
        assertEquals("Bluesky", profile.displayName)
        assertTrue(profile.bannerUrl!!.startsWith("https://cdn.bsky.app/img/banner/"))
        assertEquals(at("2023-04-12T04:53:57.057Z"), profile.createdAt)
        assertEquals(33_000_000L, profile.followersCount)
        assertEquals(650L, profile.postsCount, "a count sent as a string still counts")
        assertEquals("at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.post/3l6oveex3ii2l", profile.pinnedPostUri)
        assertEquals(profile.did, profile.actor)
    }

    @Test
    fun blankProfileFieldsAreNullAndANameFallsBackToTheHandle() {
        val profile = BlueskyParser.profile(J.parse("""{"handle":" alice.test ","displayName":" ","banner":"  ","createdAt":"not-a-date","pinnedPost":true}"""))
        assertEquals("alice.test", profile.displayName)
        assertNull(profile.bannerUrl)
        assertNull(profile.createdAt)
        assertNull(profile.pinnedPostUri)
        assertEquals("alice.test", profile.actor, "without a DID the handle is the actor")
    }

    @Test
    fun aListComesWithItsMembersAndPeopleWithoutANameAreDropped() {
        val members = BlueskyParser.listMembers(fixtureJson("list.json"))
        assertEquals(BlueskyListInfo("at://did:plc:alice/app.bsky.graph.list/3abc", "Cool people", 2, "Friends", null, "alice.bsky.social"), members.list)
        assertEquals(listOf("bob.bsky.social", "carol.bsky.social"), members.members.items.map { it.handle })
        assertEquals("c2", members.members.next)
        assertNull(BlueskyParser.listMembers(J.parse("""{"items":[]}""")).list)
    }

    @Test
    fun listsWithoutAUriAreDropped() {
        val page = BlueskyParser.lists(J.parse("""{"cursor":"l2","lists":[{"uri":"at://did:plc:a/app.bsky.graph.list/1","name":"Cool","listItemCount":4},{"name":"no uri"}]}"""))
        assertEquals(listOf("Cool"), page.items.map { it.name })
        assertEquals(4L, page.items.single().itemCount)
        assertEquals("l2", page.next)
    }

    @Test
    fun feedGeneratorsFallBackToTheirRecordKeyAndNeedAUri() {
        val feeds = BlueskyParser.feedGenerators(fixtureJson("feed_generators.json"))
        assertEquals(listOf("Discover", "cats"), feeds.map { it.displayName })
        assertEquals("bsky.app", feeds.first().creatorHandle)
        assertEquals("Trending content from your personal network", feeds.first().description)
        val single = BlueskyParser.feedGenerators(J.parse("""{"view":{"uri":"at://did:plc:x/app.bsky.feed.generator/for-you","displayName":"For You"}}"""))
        assertEquals("For You", single.single().displayName)
        assertTrue(BlueskyParser.feedGenerators(J.parse("""{"feeds":"nope"}""")).isEmpty())
    }

    @Test
    fun aStarterPackNamesItsListAsAViewAUriOrInItsRecord() {
        fun listUri(body: String) = BlueskyParser.starterPackListUri(J.parse(body))
        assertEquals("at://did:plc:a/app.bsky.graph.list/1", listUri("""{"starterPack":{"list":{"uri":"at://did:plc:a/app.bsky.graph.list/1"}}}"""))
        assertEquals("at://did:plc:a/app.bsky.graph.list/2", listUri("""{"starterPack":{"list":"at://did:plc:a/app.bsky.graph.list/2"}}"""))
        assertEquals("at://did:plc:a/app.bsky.graph.list/3", listUri("""{"starterPack":{"record":{"list":"at://did:plc:a/app.bsky.graph.list/3"}}}"""))
        assertNull(listUri("""{"starterPack":{}}"""))
    }

    @Test
    fun theSearchCursorAsksForPostsOlderThanTheOldestShown() {
        assertEquals("until:2026-09-20T10:00:00.000Z", BlueskyParser.searchCursor(fixtureJson("search_posts.json")), "by its writing time where it was never indexed")
        assertNull(BlueskyParser.searchCursor(J.parse("""{"posts":[${postView("last")}]}""")), "no cursor, no next page")
        assertNull(BlueskyParser.searchCursor(J.parse("""{"cursor":"x","posts":[]}""")))
        assertEquals("2026-09-20T10:00:00.000Z", BlueskyParser.searchUntil("until:2026-09-20T10:00:00.000Z"))
        assertNull(BlueskyParser.searchUntil("25"))
    }

    @Test
    fun timesReadAsDartReadsThemAndWriteBackTheSame() {
        assertEquals(at("2026-08-01T10:00:00Z"), BlueskyTime.parse("2026-08-01T10:00:00Z"))
        assertEquals(at("2026-08-01T08:00:00Z"), BlueskyTime.parse("2026-08-01T10:00:00+02:00"))
        assertEquals(at("2026-08-01T10:00:00.123456Z"), BlueskyTime.parse("2026-08-01 10:00:00.1234567Z"), "past microseconds is cut off")
        assertNull(BlueskyTime.parse("not-a-date"))
        assertNull(BlueskyTime.parse(" 2026-08-01T10:00:00Z"))
        assertNotNull(BlueskyTime.parse("2026-01-01"), "a date alone is midnight, local time")
        assertEquals("2026-08-01T10:00:00.000Z", BlueskyTime.iso(at("2026-08-01T10:00:00Z")))
        assertEquals("2026-10-22T19:57:01.002Z", BlueskyTime.iso(at("2026-10-22T19:57:01.002Z")))
        assertEquals("2026-10-22T19:57:01.002003Z", BlueskyTime.iso(at("2026-10-22T19:57:01.002003Z")))
    }
}
