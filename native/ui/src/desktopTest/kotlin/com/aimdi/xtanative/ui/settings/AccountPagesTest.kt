package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.core.x.FileAccountHealthStore
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.XAccountHealth
import com.aimdi.xtanative.core.x.XAccountPolicy
import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.XEndpoints
import com.aimdi.xtanative.core.x.XRateLimits
import com.aimdi.xtanative.core.x.XSigners
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.core.x.signing.TransactionSigner
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.downloads.MediaHttp
import com.aimdi.xtanative.store.downloads.MediaResponse
import com.aimdi.xtanative.store.settings.AccountPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.SettingsStore
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** XTA's ChromeAvatarStore (test/chrome_avatar_test.dart): the picture in the upper left, kept on the device. */
class ChromeAvatarTest {
    private val dir: File = Files.createTempDirectory("avatar").toFile()
    private val settings = SettingsStore(StoreDir(File(dir, "store")))
    private val file = File(dir, "chrome_avatar")
    private val avatar = ChromeAvatar(file, settings, MediaHttp { _, _ -> error("no network in this test") })

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun eachPictureIsANewRevisionAndClearingShowsTheMonogram(): Unit = runBlocking {
        avatar.set(byteArrayOf(1, 2, 3))
        assertEquals(1, settings.get(AccountPrefs.avatarRevision))
        assertContentEquals(byteArrayOf(1, 2, 3), avatar.pictureAt(1)?.readBytes())
        avatar.set(byteArrayOf(4))
        assertEquals(2, settings.get(AccountPrefs.avatarRevision))
        avatar.clear()
        assertEquals(0, settings.get(AccountPrefs.avatarRevision))
        assertFalse(file.exists())
        assertNull(avatar.pictureAt(0))
    }

    @Test
    fun nothingChangesForAnEmptyPicture(): Unit = runBlocking {
        avatar.set(ByteArray(0))
        assertEquals(0, settings.get(AccountPrefs.avatarRevision))
        assertFalse(file.exists())
    }

    @Test
    fun aRevisionWithoutItsFileShowsTheMonogram() {
        assertNull(avatar.pictureAt(3), "a backup brought the revision, not the picture")
        file.writeBytes(byteArrayOf(1))
        assertEquals(file, avatar.pictureAt(3))
        assertNull(avatar.pictureAt(0))
    }

    @Test
    fun aPictureIsReadUpToItsLimit() {
        assertContentEquals(byteArrayOf(7, 8), ChromeAvatar.pictureOf(ByteArrayInputStream(byteArrayOf(7, 8))))
        assertNull(ChromeAvatar.pictureOf(ByteArrayInputStream(ByteArray(0))))
        assertNull(ChromeAvatar.pictureOf(ByteArrayInputStream(ByteArray(ChromeAvatar.MAX_BYTES + 1))))
        assertTrue(ChromeAvatar.isPictureName("Me.JPG"))
        assertFalse(ChromeAvatar.isPictureName("notes.txt"))
        assertFalse(ChromeAvatar.isPictureName("png"))
    }

    private val profile = """{"data":{"user":{"result":{"__typename":"User","rest_id":"1","core":{"screen_name":"reader","name":"Reader"},""" +
        """"avatar":{"image_url":"https://pbs.twimg.com/profile_images/1/me_normal.jpg"}}}}}"""

    private fun client() = XClient(
        http = XHttp { _, _, _ -> XResponse(200, profile) },
        accounts = { listOf(XAccount("made-up-ct0", "reader", mapOf("Cookie" to "ct0=made-up-ct0"))) },
        signers = XSigners({ TransactionSigner(IntArray(48) { it }, "animation") }),
    )

    @Test
    fun theXProfilePhotoIsTakenInItsLargerSize(): Unit = runBlocking {
        val asked = ArrayList<String>()
        val photos = ChromeAvatar(file, settings, MediaHttp { url, _ -> MediaResponse(200, emptyMap(), 3, ByteArrayInputStream(byteArrayOf(9, 9, 9))).also { asked += url } })
        photos.fromProfile(client(), "reader")
        assertEquals(listOf("https://pbs.twimg.com/profile_images/1/me_400x400.jpg"), asked)
        assertContentEquals(byteArrayOf(9, 9, 9), photos.pictureAt(settings.get(AccountPrefs.avatarRevision))?.readBytes())
    }

    @Test
    fun aProfilePhotoThatCannotBeLoadedChangesNothing(): Unit = runBlocking {
        val missing = ChromeAvatar(file, settings, MediaHttp { _, _ -> MediaResponse(404, emptyMap(), null, ByteArrayInputStream(ByteArray(0))) })
        assertTrue(runCatching { missing.fromProfile(client(), "reader") }.isFailure)
        assertEquals(0, settings.get(AccountPrefs.avatarRevision))
        assertFalse(file.exists())
    }
}

/** XTA's trimImageDiskCache: pictures cached longer ago than the age go at launch, the cache's journal never. */
class ImageCacheAgeTest {
    private val dir: File = Files.createTempDirectory("image-cache").toFile()
    private val now = 1_784_548_800_000L
    private val day = 24 * 60 * 60 * 1000L

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun written(name: String, daysAgo: Int) = File(dir, name).apply {
        writeText(name)
        setLastModified(now - daysAgo * day)
    }

    @Test
    fun picturesOlderThanThirtyDaysGo() {
        val old = written("aaaa.1", daysAgo = 31)
        val fresh = written("bbbb.1", daysAgo = 29)
        val journal = written("journal", daysAgo = 90)
        val backup = written("journal.bkp", daysAgo = 90)
        assertEquals(1, ImageCacheAge.trim(dir, now))
        assertFalse(old.exists())
        assertTrue(fresh.exists())
        assertTrue(journal.exists() && backup.exists())
        assertEquals(30, ImageCacheAge.MAX_AGE_DAYS)
    }

    @Test
    fun aFolderThatIsNotThereIsLeftAlone(): Unit = assertEquals(0, ImageCacheAge.trim(File(dir, "missing"), now))
}

/** XTA's diagnostics_model.dart: an account whose sign-in looks broken rests, and the report says until when. */
class DiagnosticsHealthTest {
    private val dir: File = Files.createTempDirectory("health").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun aFlaggedAccountRestsUntilItsCooldownEnds() {
        val now = Instant.parse("2026-11-03T10:00:00Z")
        val health = FileAccountHealthStore(File(dir, "account-health.json"))
        val flaggedAt = now.toEpochMilli() - 60_000
        health.write("made-up-ct0", XAccountHealth(XAccountPolicy.NOT_FOUND_THRESHOLD, flaggedAt))
        health.write("stale-ct0", XAccountHealth(XAccountPolicy.NOT_FOUND_THRESHOLD, now.toEpochMilli() - XAccountPolicy.NOT_FOUND_COOLDOWN_MILLIS - 1))
        val report = DiagnosticsReport.of(
            appVersion = "1.0",
            accounts = listOf(XAccount("made-up-ct0", "reader", emptyMap()), XAccount("stale-ct0", "rested", emptyMap())),
            endpoints = XEndpoints(),
            limits = XRateLimits(),
            settings = Settings(),
            crashReports = 0,
            now = now,
            health = health,
            operations = listOf("2026-11-03T09:59:00Z UserTweets failed (unavailable 404) 120ms"),
        )
        val (broken, rested) = report.accounts
        assertEquals(Instant.ofEpochMilli(flaggedAt + XAccountPolicy.NOT_FOUND_COOLDOWN_MILLIS), broken.signInBrokenUntil)
        assertFalse(broken.isHealthy)
        assertNull(rested.signInBrokenUntil, "its rest is over")
        assertTrue(rested.isHealthy)
        val text = report.toPlainText()
        assertTrue("@reader: auth broken until ${broken.signInBrokenUntil}" in text, text)
        assertTrue(text.endsWith("recent reads (local timing, no request content):\n2026-11-03T09:59:00Z UserTweets failed (unavailable 404) 120ms"), text)
        assertFalse("made-up-ct0" in text)
    }
}
