package com.aimdi.xtanative.networks.instagram

import com.aimdi.xtanative.core.json.J
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstagramParserTest {
    private fun fixture(name: String) = J.parse(javaClass.getResource("/instagram/$name")!!.readText())

    @Test
    fun aUsernameIsTypedWithAnAtWithoutOneOrAsAProfileLink() {
        assertEquals("instagram", InstagramParser.handle("@Instagram"))
        assertEquals("some.user_1", InstagramParser.handle(" some.user_1 "))
        assertEquals("instagram", InstagramParser.handle("https://www.instagram.com/instagram/"))
        assertEquals("natgeo", InstagramParser.handle("instagram.com/NatGeo"))
        assertEquals("nasa", InstagramParser.handle("https://m.instagram.com/nasa"))
        assertNull(InstagramParser.handle("https://www.instagram.com/p/ABC/"))
        assertNull(InstagramParser.handle("https://www.instagram.com/reel/ABC/"))
        assertNull(InstagramParser.handle("https://www.instagram.com/explore/"))
        assertNull(InstagramParser.handle("https://example.com/nasa"))
        assertNull(InstagramParser.handle("not a handle!"))
        assertNull(InstagramParser.handle("a".repeat(31)))
        assertNull(InstagramParser.handle(""))
        assertNull(InstagramParser.handle(null))
    }

    @Test
    fun webProfileInfoDescribesTheAccount() {
        val profile = assertNotNull(InstagramParser.profile(fixture("web_profile_info.json")))
        assertEquals("25025320", profile.id)
        assertEquals("instagram", profile.username)
        assertEquals("Instagram", profile.displayName)
        assertEquals("Discover what's new on Instagram", profile.biography)
        assertEquals(697_000_000L, profile.followerCount)
        assertEquals(186L, profile.followingCount)
        assertEquals(8213L, profile.mediaCount)
        assertTrue(profile.isVerified)
        assertFalse(profile.isPrivate)
        assertEquals("https://scontent.cdninstagram.com/v/t51.2885-19/hd.jpg", profile.avatarUrl, "the HD picture first")
        assertEquals("https://www.instagram.com/instagram/", profile.url)
    }

    @Test
    fun webProfileInfoCarriesTheNewestPostsByTheProfile() {
        val page = InstagramParser.profileMedia(fixture("web_profile_info.json"))
        assertEquals(listOf("C1aPhoto", "C2Carousel", "C3Reel"), page.items.map { it.shortcode })
        assertEquals("QVFDb3VudGVy", page.next)

        val photo = page.items.first()
        assertEquals("3141592653589793001", photo.id)
        assertEquals("Golden hour in Lisbon #travel @natgeo", photo.caption, "tags and mentions stay in the caption's text")
        assertEquals(Instant.ofEpochSecond(1_700_000_000), photo.createdAt)
        assertEquals(250_000L, photo.likeCount)
        assertEquals(1520L, photo.commentCount)
        assertEquals("https://scontent.cdninstagram.com/v/t51.2885-15/photo.jpg", photo.coverUrl)
        assertEquals("https://www.instagram.com/p/C1aPhoto/", photo.url)
        assertEquals(listOf(photo.coverUrl), photo.displayUrls)
        assertEquals("instagram", photo.author.username)
        assertEquals("25025320", photo.author.pk, "a grid's posts are by the profile")
        assertEquals("https://scontent.cdninstagram.com/v/t51.2885-19/hd.jpg", photo.author.avatarUrl)
        assertTrue(photo.author.isVerified)
    }

    @Test
    fun aSidecarHoldsEverySlideInOrder() {
        val carousel = InstagramParser.profileMedia(fixture("web_profile_info.json")).items[1]
        val slides = listOf("slide1.jpg", "slide2.jpg", "slide3_poster.jpg").map { "https://scontent.cdninstagram.com/v/t51.2885-15/$it" }
        assertEquals(slides, carousel.carouselUrls)
        assertEquals(slides, carousel.displayUrls)
        assertEquals("", carousel.caption)
        assertFalse(carousel.isVideo)
    }

    @Test
    fun aVideoIsAReelShownByItsPoster() {
        val reel = InstagramParser.profileMedia(fixture("web_profile_info.json")).items[2]
        assertTrue(reel.isVideo)
        assertEquals("https://www.instagram.com/reel/C3Reel/", reel.url)
        assertEquals("https://scontent.cdninstagram.com/v/t51.2885-15/reel_poster.jpg", reel.coverUrl)
        assertEquals(7L, reel.commentCount)
    }

    @Test
    fun aGridWithoutEdgesFallsBackToItsMediaNodes() {
        val page = InstagramParser.profileMedia(J.parse(
            """{"user":{"id":"1","username":"natgeo","media":{"nodes":[{"id":"9","code":"OLD","taken_at":1700000000}]}}}""",
        ))
        assertEquals("OLD", page.items.single().shortcode)
        assertEquals("natgeo", page.items.single().author.username)
        assertNull(page.next)
    }

    @Test
    fun theFeedReadsReelsAndCarouselsWithTheirOwnAuthors() {
        val page = InstagramParser.userFeed(fixture("feed_user.json"))
        assertEquals("3141592653589793005_25025320", page.next)

        val (reel, album) = page.items
        assertEquals("3141592653589793004_25025320", reel.id)
        assertEquals("C4Reel", reel.shortcode)
        assertTrue(reel.isVideo)
        assertEquals("A reel #reels", reel.caption)
        assertEquals(3200L, reel.likeCount)
        assertEquals(45L, reel.commentCount)
        assertEquals("https://scontent.cdninstagram.com/v/t51.2885-15/reel_1080.jpg", reel.coverUrl, "the first candidate")
        assertEquals("25025320", reel.author.pk)
        assertTrue(reel.author.isVerified)

        assertFalse(album.isVideo)
        assertEquals("", album.caption)
        assertNull(album.coverUrl)
        assertEquals(
            listOf("album1.jpg", "album2.jpg").map { "https://scontent.cdninstagram.com/v/t51.2885-15/$it" },
            album.displayUrls,
        )
    }

    @Test
    fun aFeedThatHasNoMoreHasNoNextPage() {
        val page = InstagramParser.userFeed(J.parse("""{"items":[],"more_available":false,"next_max_id":"x"}"""))
        assertNull(page.next)
        assertEquals("7", InstagramParser.userFeed(J.parse("""{"items":[],"next_max_id":7}""")).next, "a number is a cursor too")
    }

    @Test
    fun topsearchListsEachUsernameOnce() {
        val users = InstagramParser.topSearch(fixture("topsearch.json"))
        assertEquals(listOf("natgeo", "natgeotravel"), users.map { it.username })

        val natgeo = users.first()
        assertEquals("787132", natgeo.id)
        assertEquals("National Geographic", natgeo.displayName)
        assertTrue(natgeo.isVerified)
        assertEquals("https://scontent.cdninstagram.com/v/t51.2885-19/natgeo.jpg", natgeo.avatarUrl)

        val travel = users.last()
        assertEquals("528817151", travel.id)
        assertEquals("natgeotravel", travel.displayName, "no full name shows the username")
        assertTrue(travel.isPrivate)
    }

    @Test
    fun exploreFindsPostsWhereverItsSectionsHoldThem() {
        val page = InstagramParser.explore(fixture("explore_grid.json"))
        assertEquals(listOf("AAA", "BBB", "CCC"), page.items.map { it.shortcode })
        assertEquals(listOf(false, true, true), page.items.map { it.isVideo })
        assertEquals("12", page.items[1].id)
        assertEquals("reel", page.items[1].caption)
        assertEquals("cur1", page.next)
    }

    @Test
    fun anExploreAnswerWithoutPostsIsEmpty() {
        assertTrue(InstagramParser.explore(J.parse("""{"sectional_items":[]}""")).items.isEmpty())
        assertTrue(InstagramParser.explore(J.parse("null")).items.isEmpty())
    }

    @Test
    fun missingFieldsDoNotThrow() {
        assertNull(InstagramParser.profile(J.parse("""{"data":{}}""")))
        assertTrue(InstagramParser.profileMedia(J.parse("{}")).items.isEmpty())
        assertTrue(InstagramParser.userFeed(J(null)).items.isEmpty())
        assertTrue(InstagramParser.topSearch(J.parse("\"nope\"")).isEmpty())
        assertNull(InstagramParser.mediaNode(J.parse("""{"id":"1","code":"X"}""")), "a post without an author is left out")
    }

    @Test
    fun loginRequiredIsReadFromFailMessages() {
        assertTrue(InstagramParser.loginRequired(fixture("login_required.json")))
        assertTrue(InstagramParser.loginRequired(J.parse("""{"status":"fail","message":"Please login first"}""")))
        assertFalse(InstagramParser.loginRequired(J.parse("""{"status":"ok"}""")))
    }

    @Test
    fun timesInMillisecondsAreReadAndZeroIsNoTime() {
        val inMillis = InstagramParser.mediaNode(J.parse("""{"id":"1","code":"X","taken_at":1700000000000,"user":{"username":"a"}}"""))
        assertEquals(Instant.ofEpochSecond(1_700_000_000), inMillis?.createdAt)
        val none = InstagramParser.mediaNode(J.parse("""{"id":"1","code":"X","taken_at":0,"user":{"username":"a"}}"""))
        assertNull(none?.createdAt)
    }
}
