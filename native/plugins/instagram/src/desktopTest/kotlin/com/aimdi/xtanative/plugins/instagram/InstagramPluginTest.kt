package com.aimdi.xtanative.plugins.instagram

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.instagram.InstagramAuthor
import com.aimdi.xtanative.networks.instagram.InstagramClient
import com.aimdi.xtanative.networks.instagram.InstagramPost
import com.aimdi.xtanative.networks.instagram.InstagramProfile
import com.aimdi.xtanative.store.StoreFile
import com.aimdi.xtanative.store.settings.InstagramPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.ThreadsPrefs
import java.io.IOException
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class InstagramPluginTest {
    private val folder = Files.createTempDirectory("instagram").toFile().apply { deleteOnExit() }

    @Test
    fun followsAreXtasRowsTheNewestFirst(): Unit = runBlocking {
        var now = Instant.parse("2026-11-01T08:00:00Z")
        val follows = InstagramFollows(StoreFile(folder.resolve("follows.json"))) { now }
        follows.follow(InstagramProfile(id = "25025320", username = "Instagram", fullName = "Instagram", biography = "Bringing you closer"))
        now = now.plusSeconds(60)
        follows.follow(InstagramAuthor(username = "natgeo", fullName = "National Geographic"))

        val rows = follows.load()
        assertEquals(listOf("natgeo", "instagram"), rows.map { it.id })
        assertEquals("natgeo", rows.first().pk, "without Instagram's id, the username stands in")
        assertEquals("25025320", rows.last().pk)
        val exported = J.parse(follows.export()).list
        assertEquals(setOf("id", "pk", "name", "avatar_url", "signature", "in_feed", "created_at"), exported.first().keys)
        assertEquals("Bringing you closer", exported.last()["signature"].string)

        val restored = InstagramFollows(StoreFile(folder.resolve("other.json")))
        restored.import(follows.export())
        assertEquals(rows, restored.load())
        restored.unfollow("NatGeo")
        assertTrue(InstagramFollows.follows(restored.state.value, "INSTAGRAM"))
        assertEquals(false, InstagramFollows.follows(restored.state.value, "natgeo"))
    }

    @Test
    fun likesStayHereTheNewestFirstAsManyAsXtaKeeps() {
        val liked = InstagramLikes.toggled(Settings(), "1").let { InstagramLikes.toggled(it, "2") }
        assertEquals(listOf("2", "1"), InstagramLikes.ids(liked))
        assertEquals("""["2","1"]""", liked[InstagramPrefs.likedPosts])
        assertEquals(listOf("1"), InstagramLikes.ids(InstagramLikes.toggled(liked, "2")))
        val full = (1..InstagramLikes.CAP).map { "$it" }
        assertEquals("new", InstagramLikes.toggled(full, "new").first(), "a new like is kept when the list is full")
        assertEquals(InstagramLikes.CAP, InstagramLikes.toggled(full, "new").size)
    }

    @Test
    fun theSearchHistoryKeepsHandlesLowerCaseWithoutTheirAt() {
        assertEquals(listOf("natgeo", "nasa"), InstagramSearchHistory.remembered(listOf("nasa", "natgeo"), " @NatGeo "))
        assertEquals(listOf("nasa"), InstagramSearchHistory.remembered(listOf("nasa"), "@"))
        assertEquals(InstagramSearchHistory.CAP, InstagramSearchHistory.remembered((1..20).map { "h$it" }, "new").size)
    }

    @Test
    fun failuresSayWhatXtaSaysOfWhatInstagramAnswered() {
        fun refused(code: Int) = NetworkRequestException(InstagramClient.NETWORK, code, "x")
        assertEquals(Strings.error_not_found, InstagramFailures.of(refused(404)))
        assertEquals(Strings.error_rate_limited, InstagramFailures.of(refused(429)))
        assertEquals(Strings.error_login, InstagramFailures.of(refused(401)))
        assertEquals(Strings.error_login, InstagramFailures.of(refused(403)))
        assertEquals(Strings.error_response, InstagramFailures.of(refused(502)))
        assertEquals(Strings.error_network, InstagramFailures.of(IOException("offline")))
        assertEquals(Strings.error_rate_limited, InstagramFailures.of(Problem.RateLimited))
        assertEquals(Strings.error_not_found, InstagramFailures.of(Problem.Refused(404)))
        assertEquals(Strings.error_network, InstagramFailures.of(Problem.Offline))
    }

    @Test
    fun aProfileLinkOpensHereAndAPostOnTheSite() {
        assertEquals("natgeo", InstagramRoutes.profileOf(InstagramRoutes.forUrl("https://www.instagram.com/NatGeo/")!!))
        assertNull(InstagramRoutes.forUrl("https://www.instagram.com/p/ABC/"))
        assertNull(InstagramRoutes.profileOf("elsewhere?handle=x"))
    }

    @Test
    fun aCarouselOpensEverySlideInTheViewer() {
        val post = InstagramPost("1", "ABC", "", null, InstagramAuthor("natgeo", "NatGeo"), coverUrl = "https://cdn/c.jpg", carouselUrls = listOf("https://cdn/1.jpg", "https://cdn/2.jpg"))
        assertEquals(listOf("https://cdn/1.jpg", "https://cdn/2.jpg"), InstagramMedia.photos(post).map { it.imageUrl })
        assertEquals(listOf("https://cdn/c.jpg"), InstagramMedia.photos(post.copy(carouselUrls = emptyList())).map { it.imageUrl })
    }

    @Test
    fun aCarouselOfPhotosHasADotEachAndAVideosCoversACounter() {
        val one = InstagramPost("1", "ABC", "", null, InstagramAuthor("natgeo", "NatGeo"), coverUrl = "https://cdn/c.jpg")
        val carousel = one.copy(carouselUrls = listOf("https://cdn/1.jpg", "https://cdn/2.jpg"))
        assertEquals(PageMark.NONE, InstagramMedia.pageMark(one))
        assertEquals(PageMark.DOTS, InstagramMedia.pageMark(carousel), "as XTA's PluginPostMedia pages a post's photos")
        assertEquals(PageMark.COUNTER, InstagramMedia.pageMark(carousel.copy(isVideo = true)), "as instagram_post_card.dart marks a video's covers")
        assertEquals(PageMark.NONE, InstagramMedia.pageMark(one.copy(isVideo = true)))
    }

    @Test
    fun aPullOnAccountsReadsTheRowsKeptHereAgain(): Unit = runBlocking {
        val file = StoreFile(folder.resolve("pulled.json"))
        val follows = InstagramFollows(file)
        follows.follow(InstagramAuthor(username = "natgeo", fullName = "National Geographic"))
        assertEquals(listOf("natgeo"), follows.state.value?.map { it.id })

        val other = InstagramFollows(file)
        other.follow(InstagramAuthor(username = "nasa", fullName = "NASA"))
        assertEquals(listOf("natgeo"), follows.load().map { it.id }, "what was read stays until it is read again")
        assertEquals(setOf("nasa", "natgeo"), follows.reload().map { it.id }.toSet())
        assertEquals(setOf("nasa", "natgeo"), follows.state.value?.map { it.id }?.toSet())
    }

    @Test
    fun everyGlyphDraws() {
        InstagramIcons.all.forEach { assertTrue(it.root.iterator().hasNext(), it.name) }
    }

    @Test
    fun threadsCookiesAreCopiedAsInstagramsSession() {
        val session = "sessionid=s1; csrftoken=c1; ds_user_id=9; mid=m1; ig_did=d1"
        assertNull(InstagramThreadsSession.of(Settings()), "Threads holds none")
        val copied = InstagramThreadsSession.of(Settings().with(ThreadsPrefs.directCookies, " $session "))
        assertEquals(session, copied)
        assertTrue(InstagramClient.hasSession(copied!!), "the names are shared: Instagram reads with them")
    }
}
