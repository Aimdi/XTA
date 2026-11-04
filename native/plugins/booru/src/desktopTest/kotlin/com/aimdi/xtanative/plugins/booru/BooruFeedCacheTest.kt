package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.booru.BooruEngine
import com.aimdi.xtanative.networks.booru.BooruImageVariant
import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.networks.booru.BooruRating
import com.aimdi.xtanative.networks.booru.BooruTagCategory
import com.aimdi.xtanative.networks.booru.post
import com.aimdi.xtanative.store.StoreDir
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class BooruFeedCacheTest {
    private val now = Instant.parse("2026-11-04T10:00:00Z")

    private fun dir(): StoreDir = StoreDir(Files.createTempDirectory("booru-cache").toFile())

    private val full = BooruPost(
        id = "42",
        host = "https://danbooru.donmai.us",
        engine = BooruEngine.DANBOORU,
        tags = listOf("hatsune_miku", "solo"),
        rating = BooruRating.SENSITIVE,
        score = 12,
        width = 800,
        height = 600,
        previewUrl = "https://cdn.example/p.jpg",
        sampleUrl = "https://cdn.example/s.jpg",
        fileUrl = "https://cdn.example/f.png",
        fileExt = "png",
        source = "https://www.pixiv.net/artworks/1",
        createdAt = Instant.parse("2026-11-01T08:00:00Z"),
        updatedAt = Instant.parse("2026-11-02T08:00:00Z"),
        tagCategories = mapOf("hatsune_miku" to BooruTagCategory.CHARACTER),
        fileSize = 123_456,
        md5 = "abc",
        favCount = 3,
        upScore = 5,
        downScore = 1,
        parentId = "40",
        hasChildren = true,
        uploader = "maya",
        variants = listOf(BooruImageVariant("https://cdn.example/360.jpg", 360)),
    )

    @Test
    fun aKeptPageReadsBackWithAllAPostSaid(): Unit = runBlocking {
        val cache = BooruFeedCache(dir()) { now }
        val page = KeptPage(listOf(full, post(id = "7")), page = 2, hasMore = false)
        cache.write("latest", page)
        assertEquals(page, cache.read("latest"))
        assertNull(cache.read("following"))
    }

    @Test
    fun aPageKeptMoreThanTwoWeeksAgoIsNotShown(): Unit = runBlocking {
        var clock = now
        val cache = BooruFeedCache(dir()) { clock }
        cache.write("latest", KeptPage(listOf(post()), page = 1, hasMore = true))
        clock = now.plus(Duration.ofDays(13))
        assertEquals(1, cache.read("latest")?.posts?.size)
        clock = now.plus(Duration.ofDays(15))
        assertNull(cache.read("latest"))
    }

    @Test
    fun aDamagedCopyReadsAsNone(): Unit = runBlocking {
        val store = dir()
        store.file(BooruFeedCache.fileName("latest")).write("{\"savedAt\": ")
        assertNull(BooruFeedCache(store) { now }.read("latest"))
    }

    @Test
    fun aPostWithoutItsSiteIsNotRead() {
        assertNull(BooruPostJson.decode(J.parse("""{"id":"1","host":"https://a.example","engine":"nope"}""")))
        assertNull(BooruPostJson.decode(J.parse("""{"host":"https://a.example","engine":"danbooru"}""")))
    }
}
