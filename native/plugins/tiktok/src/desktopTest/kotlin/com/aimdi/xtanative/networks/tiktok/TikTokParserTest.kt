package com.aimdi.xtanative.networks.tiktok

import com.aimdi.xtanative.core.json.J
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TikTokParserTest {
    private fun text(name: String) = javaClass.getResource("/tiktok/$name")!!.readText()

    private fun fixture(name: String) = J.parse(text(name))

    /** A page carrying [scope] as TikTok's app JSON. */
    private fun page(scope: String) = "<html><script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\">{\"__DEFAULT_SCOPE__\":$scope}</script></html>"

    @Test
    fun aUsernameIsTypedWithAnAtWithoutOneOrAsALink() {
        assertEquals("tiktok", TikTokParser.handle("@TikTok"))
        assertEquals("some.user_1", TikTokParser.handle(" some.user_1 "))
        assertEquals("ab", TikTokParser.handle("ab"))
        assertNull(TikTokParser.handle("a"), "a username has two characters at least")
        assertNull(TikTokParser.handle("a".repeat(25)))
        assertNull(TikTokParser.handle("https://www.tiktok.com/@x"))
        assertEquals("tiktok", TikTokParser.handle("https://www.tiktok.com/@tiktok"))
        assertEquals("tiktok", TikTokParser.handle("https://www.tiktok.com/@tiktok/video/123"))
        assertEquals("tiktok", TikTokParser.handle("tiktok.com/@TikTok"))
        assertNull(TikTokParser.handle("https://www.tiktok.com/tag/cats"))
        assertNull(TikTokParser.handle("https://vm.tiktok.com/ZMxxx"))
        assertNull(TikTokParser.handle("https://vt.tiktok.com/ZMxxx"))
        assertNull(TikTokParser.handle("charli d amelio"))
        assertNull(TikTokParser.handle(null))
    }

    @Test
    fun aProfilePageCarriesTheAccount() {
        val profile = assertNotNull(TikTokParser.profileHtml(text("profile.html")))
        assertEquals("107955", profile.id)
        assertEquals("tiktok", profile.uniqueId)
        assertEquals("MS4wLjABAAAAv7iSuuXDJGDvJkmH_vz1qkDZYo1apxgzaxdBSeIuPiM", profile.secUid)
        assertEquals("TikTok", profile.displayName)
        assertEquals("One TikTok can make a big impact", profile.signature)
        assertEquals(89_000_000L, profile.followerCount)
        assertEquals(1L, profile.followingCount)
        assertEquals(1200L, profile.videoCount)
        assertEquals(3_300_000_000L, profile.heartCount, "more likes than an Int holds")
        assertTrue(profile.verified)
        assertFalse(profile.privateAccount)
        assertEquals("https://p16-sign-va.tiktokcdn.com/tos-maliva-avt-0068/large.jpeg", profile.avatarUrl, "the largest picture first")
        assertEquals("https://www.tiktok.com/@tiktok", profile.url)
    }

    @Test
    fun htmlEntitiesInThePagesJsonAreUndone() {
        val json = """{"__DEFAULT_SCOPE__":{"webapp.user-detail":{"userInfo":{"user":{"secUid":"MS4w","uniqueId":"tiktok","nickname":"Tom &amp; Jerry"}}}}}"""
        val html = "<html><script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\">${json.replace("\"", "&quot;")}</script></html>"
        val profile = assertNotNull(TikTokParser.profileHtml(html))
        assertEquals("tiktok", profile.uniqueId)
        assertEquals("Tom & Jerry", profile.nickname)
        assertEquals("tiktok", profile.id, "no id is the username")
    }

    @Test
    fun aPrivateAccountIsReadNotRefused() {
        val profile = TikTokParser.profileHtml(text("profile.html").replace("\"privateAccount\":false", "\"privateAccount\":true"))
        assertTrue(assertNotNull(profile).privateAccount)
    }

    @Test
    fun aVideoPageCarriesTheVideoAndEveryFile() {
        val post = assertNotNull(TikTokParser.videoHtml(text("video.html")))
        assertEquals("7300000000000000001", post.id)
        assertEquals("Hello #fyp @tiktok", post.desc, "tags and mentions stay in the caption's text")
        assertEquals(Instant.ofEpochSecond(1_700_000_000), post.createdAt, "a time sent as a string")
        assertEquals("tiktok", post.author.uniqueId)
        assertEquals("MS4wLjABAAAAv7iSuuXDJGDvJkmH_vz1qkDZYo1apxgzaxdBSeIuPiM", post.author.secUid)
        assertTrue(post.author.verified)
        assertEquals(listOf(51_000L, 830L, 1200L, 2_400_000L), listOf(post.diggCount, post.commentCount, post.shareCount, post.playCount))
        assertEquals(15, post.durationSeconds)
        assertEquals("0:15", post.durationText)
        assertEquals(576f / 1024, post.aspectRatio)
        assertEquals("https://p16-sign.tiktokcdn-us.com/obj/origin.jpeg", post.coverUrl, "the original cover first")
        assertEquals("https://www.tiktok.com/@tiktok/video/7300000000000000001", post.url)
        assertEquals("https://www.tiktok.com/embed/v3/7300000000000000001", post.embedUrl)

        val tos = "https://v16-webapp-prime.tiktok.com/video/tos"
        assertEquals(
            listOf(
                TikTokVideoSource("$tos/720.mp4", "720p"),
                TikTokVideoSource("https://v19-webapp-prime.tiktok.com/video/tos/720.mp4", "720p"),
                TikTokVideoSource("$tos/540.mp4", "540p"),
                TikTokVideoSource("$tos/play.mp4?a=1988", null),
                TikTokVideoSource("$tos/download.mp4?a=1988", "download"),
            ),
            post.sources,
            "the ladder, then playAddr and downloadAddr; tiktok.com's own player link is no file",
        )
        assertEquals("$tos/720.mp4", post.playUrl)
        assertFalse(post.isPhoto)
    }

    @Test
    fun aDurationInMillisecondsIsRoundedToSeconds() {
        val post = TikTokParser.videoHtml(page(
            """{"webapp.video-detail":{"itemInfo":{"itemStruct":{"id":"123","desc":"clip","createTime":1700000000,""" +
                """"author":{"uniqueId":"tiktok","nickname":"TikTok"},""" +
                """"video":{"playAddr":{"urlList":["https://v16-webapp-prime.us.tiktok.com/play.mp4"]},"duration":12500,"width":720,"height":1280}}}}}""",
        ))
        assertEquals(13, post?.durationSeconds)
        assertEquals("https://v16-webapp-prime.us.tiktok.com/play.mp4", post?.playUrl, "playAddr as an object with its urlList")
        assertEquals(720f / 1280, post?.aspectRatio)
    }

    @Test
    fun theCreatorListReadsVideosAndPhotoPosts() {
        val list = TikTokParser.itemList(fixture("item_list.json"))
        assertEquals(0, list.statusCode)
        assertTrue(list.hasMore, "hasMorePrevious is what pages back in time")
        assertEquals("1699980000000", list.cursor, "no cursor given: the last video's time in milliseconds")
        assertEquals("1699980000000", list.page.next)

        val (video, photo) = list.posts
        assertEquals(listOf("1080p", "720p", null, "download"), video.sources.map { it.label })
        assertEquals("https://p16-sign.tiktokcdn-us.com/obj/second.jpeg", video.coverUrl)
        assertEquals(8, video.durationSeconds)
        assertEquals(98_000L, video.playCount)

        assertTrue(photo.isPhoto)
        assertEquals("https://p16-sign.tiktokcdn-us.com/obj/photo_cover.jpeg", photo.coverUrl)
        assertTrue(photo.sources.isEmpty())
        assertEquals("", photo.durationText)
    }

    @Test
    fun theListsOwnCursorWinsAndMayBeANumber() {
        val list = TikTokParser.itemList(J.parse(
            """{"statusCode":0,"hasMorePrevious":true,"cursor":987654,"itemList":[{"id":"9","desc":"hi","createTime":1700000000,""" +
                """"author":{"uniqueId":"bob","nickname":"Bob"},"stats":{"diggCount":5},"video":{"playAddr":"https://v16.tiktokcdn.com/a.mp4",""" +
                """"bitrateInfo":[{"PlayAddr":{"UrlKey":"id_h264_720p_1","UrlList":["https://v16.tiktokcdn.com/720.mp4"]}}],""" +
                """"cover":"https://p16.tiktokcdn.com/c.jpg","width":1080,"height":1920,"duration":8}}]}""",
        ))
        assertEquals("987654", list.cursor)
        assertEquals("bob", list.posts.single().author.uniqueId)
        assertEquals("720p", list.posts.single().sources.first().label)
        assertEquals(5L, list.posts.single().diggCount)
    }

    @Test
    fun anEmptyListKeepsItsHasMore() {
        val list = TikTokParser.itemList(J.parse("""{"statusCode":0,"hasMore":true,"itemList":[]}"""))
        assertTrue(list.posts.isEmpty())
        assertTrue(list.hasMore)
        assertNull(list.cursor)
        assertNull(list.page.next, "nothing to page from")
    }

    @Test
    fun aPhotoPostsCoverStandsInWhenTheVideoHasNone() {
        val list = TikTokParser.itemList(J.parse(
            """{"statusCode":0,"itemList":[{"id":"photo-1","author":{"uniqueId":"photo","nickname":"Photo"},""" +
                """"imagePost":{"cover":{"imageURL":{"urlList":["https://p16.tiktokcdn.com/cover.jpg"]}},"images":[]},"video":{}}]}""",
        ))
        assertEquals("https://p16.tiktokcdn.com/cover.jpg", list.posts.single().coverUrl)
        assertTrue(list.posts.single().isPhoto)
    }

    @Test
    fun tiktoksOwnPlayerLinksAreNoFilesAndAVideoWithoutAnAuthorIsLeftOut() {
        val list = TikTokParser.itemList(J.parse(
            """{"statusCode":0,"itemList":[{"id":"1","author":{"uniqueId":"a","nickname":"A"},""" +
                """"video":{"playAddr":"https://www.tiktok.com/aweme/v1/play/"}},{"id":"2"}]}""",
        ))
        assertNull(list.posts.single().playUrl)
        assertFalse(list.posts.single().isPhoto, "no cover either")
    }

    @Test
    fun noSuchAccountsStatusIsKeptForTheClient() {
        val list = TikTokParser.itemList(J.parse("""{"statusCode":10201,"itemList":[]}"""))
        assertEquals(10201, list.statusCode)
        assertTrue(list.posts.isEmpty())
    }

    @Test
    fun aPageWithoutTheAppsJsonIsNull() {
        assertNull(TikTokParser.profileHtml("<html></html>"))
        assertNull(TikTokParser.videoHtml("<html></html>"))
        assertNull(TikTokParser.profileHtml(page("{}")))
        assertNull(TikTokParser.universalScope("<html><script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\">not json</script></html>"))
    }

    @Test
    fun theDurationShowsMinutesAndHidesZero() {
        val author = TikTokAuthor(uniqueId = "a", nickname = "A")
        assertEquals("1:05", TikTokPost(id = "1", desc = "", createdAt = null, author = author, durationSeconds = 65).durationText)
        assertEquals("", TikTokPost(id = "1", desc = "", createdAt = null, author = author).durationText)
        assertEquals(9f / 16, TikTokPost(id = "1", desc = "", createdAt = null, author = author).aspectRatio, "portrait without a size")
    }

    @Test
    fun discoverCardsOfPeopleBecomeSearchUsers() {
        val users = TikTokParser.discoverUsers(fixture("discover.json"))
        assertEquals(listOf("therock", "charlidamelio"), users.map { it.uniqueId }, "tags and music are not people; each person once")

        val rock = users.first()
        assertEquals("The Rock", rock.displayName)
        assertEquals("Mana", rock.signature)
        assertTrue(rock.verified)
        assertEquals(79_000_000L, rock.followerCount)
        assertEquals("https://p16-sign-va.tiktokcdn.com/therock.jpeg", rock.avatarUrl)
        assertNull(rock.secUid, "discover gives no secUid")
    }

    @Test
    fun aDiscoverCardNamesItsPersonByLinkOrSubtitle() {
        val card = TikTokParser.discoverUser(J.parse("""{"type":2,"title":"The Rock","subTitle":"@therock"}"""))
        assertEquals("therock", card?.uniqueId)
        assertNull(TikTokParser.discoverUser(J.parse("""{"type":2,"title":"nobody"}""")))
    }

    @Test
    fun suggestionsAsTypedAndTrendingAreReadTogether() {
        assertEquals(
            listOf("cats meowing", "cats", "NFL Preseason"),
            TikTokParser.suggestList(J.parse(
                """{"sug_list":[{"content":"cats meowing"},{"word_record":{"words_content":"cats"}}],"data":[{"word":"NFL Preseason"}]}""",
            )),
        )
        assertEquals(listOf("nba highlights", "NBA", "nba youngboy"), TikTokParser.suggestList(fixture("suggest_preview.json")))
        assertEquals(listOf("NFL Preseason", "cats meowing"), TikTokParser.suggestList(fixture("suggest_guide.json")))
        assertTrue(TikTokParser.suggestList(J.parse("""{"status_code":0}""")).isEmpty())
    }

    @Test
    fun aMadeUpDeviceIdIsInTheRangeTheListAccepts() {
        val lowest = object : Random() {
            override fun nextBits(bitCount: Int) = 0
        }
        assertEquals(TikTokClient.DEVICE_ID_MIN.toString(), TikTokClient.randomDeviceId(lowest))
        repeat(20) {
            val id = TikTokClient.randomDeviceId(Random(it)).toLong()
            assertTrue(id in TikTokClient.DEVICE_ID_MIN..TikTokClient.DEVICE_ID_MAX, "$id")
        }
    }
}
