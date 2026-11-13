package com.aimdi.xtanative.plugins.ehviewer

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.ehviewer.EhCategory
import com.aimdi.xtanative.networks.ehviewer.EhGallery
import com.aimdi.xtanative.store.StoreDir
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class EhKeptTest {
    private val dir = StoreDir(Files.createTempDirectory("eh-kept").toFile())
    private var now = Instant.parse("2026-08-11T21:36:00Z")
    private val clock = { now }

    private fun gallery(gid: Long, title: String = "Gallery $gid") = EhGallery(
        gid = gid,
        token = "tok$gid",
        title = title,
        titleJpn = "ギャラリー",
        category = EhCategory.ARTIST_CG,
        thumbUrl = "https://ehgt.org/t/$gid.jpg",
        uploader = "someone",
        postedAt = Instant.parse("2026-01-02T03:04:05Z"),
        pageCount = 24,
        rating = 4.5,
        tags = listOf("female:glasses"),
    )

    @Test
    fun aFavouriteIsKeptAsXtasRowAndATapAgainTakesItAway(): Unit = runBlocking {
        val favorites = EhFavorites(dir.file("favorites.json"), clock)
        favorites.toggle(gallery(1))

        assertTrue(favorites.contains(1))
        val row = J.parse(favorites.export()).list.single()
        assertEquals(1L, row["gid"].long)
        assertEquals("Artist CG", row["category"].string, "XTA keeps the site's label")
        assertEquals("2026-01-02T03:04:05.000Z", row["posted_at"].string)
        assertEquals("2026-08-11T21:36:00.000Z", row["favorited_at"].string)

        favorites.toggle(gallery(1))
        assertFalse(favorites.contains(1))
    }

    @Test
    fun favouritesAreListedNewestFirstAndABackupTakesTheirPlace(): Unit = runBlocking {
        val favorites = EhFavorites(dir.file("favorites.json"), clock)
        favorites.toggle(gallery(1))
        now = now.plusSeconds(60)
        favorites.toggle(gallery(2))
        assertEquals(listOf(2L, 1L), favorites.load().map { it.gallery.gid })

        val backup = """[{"gid":7,"token":"t7","title":"Seven","favorited_at":"2026-01-01T00:00:00.000"},{"gid":8,"title":"no token"}]"""
        favorites.import(backup)
        assertEquals(listOf(7L), favorites.load().map { it.gallery.gid }, "a row without its token is left out")
        assertEquals(listOf(7L), EhFavorites(dir.file("favorites.json"), clock).load().map { it.gallery.gid }, "and kept on disk")
    }

    @Test
    fun theHistoryRemembersThePageLastReadAndNeverBeforeTheFirst(): Unit = runBlocking {
        val history = EhHistory(dir.file("history.json"), clock)
        history.load()
        history.remember(gallery(1), page = 0)
        assertEquals(1, history.lastPage(1))
        now = now.plusSeconds(5)
        history.remember(gallery(2), page = 3)
        now = now.plusSeconds(5)
        history.remember(gallery(1), page = 12)

        assertEquals(listOf(1L, 2L), history.load().map { it.gallery.gid })
        assertEquals(12, history.lastPage(1))
        assertEquals(12, J.parse(history.export()).list.first()["last_page"].int)

        history.clear()
        assertEquals(emptyList(), history.load())
        assertNull(history.lastPage(1))
    }

    @Test
    fun aKeptRowGivesBackTheGalleryWithoutItsTags() {
        val kept = EhRows.gallery(EhRows.of(gallery(3)))
        assertEquals(gallery(3).copy(tags = emptyList()), kept)
    }
}
