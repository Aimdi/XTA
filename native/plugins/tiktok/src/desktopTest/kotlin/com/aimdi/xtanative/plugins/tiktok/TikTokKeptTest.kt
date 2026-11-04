package com.aimdi.xtanative.plugins.tiktok

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.tiktok.TikTokProfile
import com.aimdi.xtanative.networks.tiktok.TikTokSession
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.TikTokPrefs
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class TikTokKeptTest {
    private val dir = StoreDir(Files.createTempDirectory("tiktok-kept").toFile())
    private var now = Instant.parse("2026-08-12T10:00:00Z")

    private fun profile(handle: String) = TikTokProfile(
        id = "1",
        secUid = "MS4wLjABAAAA_$handle",
        uniqueId = handle,
        nickname = "",
        signature = "Hi",
        avatarUrl = "https://p16.tiktokcdn.com/$handle.jpg",
    )

    @Test
    fun aFollowIsKeptAsXtasRowTheNewestFirst(): Unit = runBlocking {
        val follows = TikTokFollows(dir.file("follows.json")) { now }
        follows.follow(profile("NBA"))
        now = now.plusSeconds(60)
        follows.follow(profile("tiktok"))

        assertEquals(listOf("tiktok", "nba"), follows.load().map { it.id })
        assertTrue(follows.isFollowed("NBA"))
        val row = J.parse(follows.export()).list.last()
        assertEquals("nba", row["id"].string)
        assertEquals("MS4wLjABAAAA_NBA", row["sec_uid"].string)
        assertEquals("NBA", row["name"].string, "an account without a nickname is named by its handle")
        assertEquals(1, row["in_feed"].int)
        assertEquals("2026-08-12T10:00:00.000Z", row["created_at"].string)
    }

    @Test
    fun anUnfollowTakesTheRowAwayAndABackupTakesTheirPlace(): Unit = runBlocking {
        val follows = TikTokFollows(dir.file("follows.json")) { now }
        follows.follow(profile("nba"))
        follows.unfollow("NBA")
        assertFalse(follows.isFollowed("nba"))

        follows.import("""[{"id":"charlidamelio","sec_uid":"MS4","name":"Charli","created_at":"2026-01-01T00:00:00.000"},{"id":"broken"}]""")
        assertEquals(listOf("charlidamelio"), TikTokFollows(dir.file("follows.json")).load().map { it.id })
    }

    @Test
    fun theGuestSessionIsKeptInThePluginsFolderUnderXtasKeys(): Unit = runBlocking {
        val file = TikTokSessionFile(dir.file("session.json"))
        assertEquals(TikTokSession(), file.load())
        file.save(TikTokSession(cookies = "ttwid=abc; msToken=x", deviceId = "7300000000000000000"))

        assertEquals(TikTokSession("ttwid=abc; msToken=x", "7300000000000000000"), file.load())
        val stored = J.parse(TikTokSessionFile.encode(TikTokSession("c", "d")))
        assertEquals("c", stored[TikTokPrefs.cookies.key].string)
        assertEquals("d", stored[TikTokPrefs.deviceId.key].string)
    }

    @Test
    fun heartsToggleAndTheNewestTwoHundredStay() {
        val liked = TikTokMarks.withLikeToggled(Settings(), "1")
        assertEquals(listOf("1"), TikTokMarks.likes(liked))
        assertEquals(emptyList(), TikTokMarks.likes(TikTokMarks.withLikeToggled(liked, "1")))

        val many = (1..TikTokMarks.LIKES_KEPT).fold(Settings()) { settings, n -> TikTokMarks.withLikeToggled(settings, "$n") }
        val more = TikTokMarks.likes(TikTokMarks.withLikeToggled(many, "new"))
        assertEquals(TikTokMarks.LIKES_KEPT, more.size)
        assertEquals("new", more.last())
        assertFalse("1" in more)
    }

    @Test
    fun aSearchIsRememberedAsXtaKeepsAHandle() {
        val settings = TikTokMarks.withSearch(TikTokMarks.withSearch(Settings(), "@NBA "), "dance")
        assertEquals(listOf("dance", "nba"), settings[TikTokPrefs.searchHistory])
        assertEquals(listOf("nba", "dance"), TikTokMarks.withSearch(settings, "nba")[TikTokPrefs.searchHistory])
        assertEquals(settings, TikTokMarks.withSearch(settings, "  @ "))
    }
}
