package com.aimdi.xtanative.store.offline

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.downloads.MediaHttp
import com.aimdi.xtanative.store.downloads.MediaResponse
import com.aimdi.xtanative.store.saved.ChunkStream
import com.aimdi.xtanative.store.saved.answer
import com.aimdi.xtanative.store.saved.tempStore
import com.aimdi.xtanative.store.saved.waitUntil
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import com.aimdi.xtanative.store.saved.timed
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup

class OfflineStoreTest {
    private val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==")
    private val dir: StoreDir = tempStore()
    private val library = File(dir.root, "offline")

    private val servesPng = MediaHttp { _, _ -> answer(200, png, length = png.size.toLong()) }
    private val noNetwork = MediaHttp { _, _ -> error("No network on offline read") }

    private fun store(http: MediaHttp = servesPng, storageLimit: Long = OfflineStore.DEFAULT_STORAGE_LIMIT, imageLimit: Long = 8L * 1024 * 1024) =
        OfflineStore(dir, http, storageLimit, imageLimit)

    private fun article(body: String = "<p>A retained article</p>", id: String = "rss:example:1", url: String = "https://example.com/read/1") =
        OfflineArticle(id, "Reading in the city", "Daily journal", body, buildJsonObject { put("kind", "rss") }, url)

    private val photo = listOf(OfflineMediaSource("https://example.com/a.png"))

    @Test
    fun anArticleKeepsItsTextAndPayloadAcrossTheJson() {
        val kept = article()
        assertEquals(kept, OfflineArticle.fromJson(J(kept.toJson())))
        assertEquals("rss", kept.kind)
    }

    @Test
    fun aColdStartReportsTheExactPicturesKeptAndRendersWithoutTheNetwork() = timed {
        val calls = AtomicInteger()
        val model = store(MediaHttp { url, _ ->
            calls.incrementAndGet()
            if (url.endsWith("/good.png")) answer(200, png, length = png.size.toLong()) else answer(503)
        })
        val kept = article("<p>Text</p><img src=\"/good.png\"><img src=\"/missing.png\">")
        model.keepArticle(kept)
        val entry = model.state.value.entry(kept.id)!!
        assertEquals(OfflineAvailability.PARTIAL, entry.availability)
        assertEquals(2, entry.totalMedia)
        assertEquals(1, entry.files.size)
        assertEquals(2, calls.get())

        val cold = store(noNetwork)
        assertEquals(kept.bodyHtml, cold.article(kept.id)?.bodyHtml)
        val rendered = Jsoup.parse(
            cold.renderArticle(
                kept.id,
                "<html><head><style>p{color:red}</style></head><body><p>Text</p>" +
                    "<img src=\"https://example.com/good.png\" srcset=\"https://example.com/missing.png 2x\">" +
                    "<img src=\"/missing.png\"><video src=\"https://example.com/v.mp4\"></video></body></html>",
            ),
        )
        val images = rendered.select("img")
        assertTrue(images[0].attr("src").startsWith("data:image/png;base64,"))
        assertFalse(images[1].hasAttr("src"))
        assertFalse(images[0].hasAttr("srcset"))
        assertTrue(rendered.select("video").isEmpty())
        assertTrue(rendered.selectFirst("meta")!!.attr("content").contains("default-src 'none'"))
        assertEquals(entry.articleBytes + png.size, cold.state.value.bytes)
    }

    @Test
    fun aKeptArticleHandsItsPicturesOverAsFiles() = timed {
        val kept = article("<p>Text</p><img src=\"/good.png\"><img src=\"/missing.png\">")
        store(MediaHttp { url, _ -> if (url.endsWith("/good.png")) answer(200, png, length = png.size.toLong()) else answer(503) }).keepArticle(kept)
        val cold = store(noNetwork)
        val images = cold.articleImages(kept.id)
        assertEquals(setOf("https://example.com/good.png"), images.keys)
        assertContentEquals(png, images.values.single().readBytes())
        assertTrue(cold.articleImages("not-kept").isEmpty())
    }

    @Test
    fun missingFilesLowerWhatIsShownAsKept() = timed {
        val model = store()
        val kept = article("<img src=\"https://example.com/a.png\">")
        model.keepArticle(kept)
        val entry = model.state.value.entry(kept.id)!!
        model.mediaFile(entry, entry.files.single())!!.delete()
        model.refresh()
        assertEquals(OfflineAvailability.PARTIAL, model.state.value.entry(kept.id)?.availability)
        assertEquals(entry.articleBytes, model.state.value.bytes)
        File(library, "${entry.directory}/article.json").delete()
        model.refresh()
        assertEquals(OfflineAvailability.UNAVAILABLE, model.state.value.entry(kept.id)?.availability)
        assertNull(model.article(kept.id))
        assertEquals(0, model.state.value.bytes)
    }

    @Test
    fun aLinkByAnotherIdFindsTheArticleKeptAtItsAddress() = timed {
        store().keepArticle(article("<p>Retained body</p>", id = "substack:1:123", url = "https://journal.substack.com/p/a-story"))
        val found = store(noNetwork).article("substack:journal:a-story", canonicalUrl = "https://journal.substack.com/p/a-story/#comments")
        assertEquals("substack:1:123", found?.id)
    }

    @Test
    fun anEmptyBodyIsNeverKeptAndSvgIsNeverEmbedded() = timed {
        val model = store()
        model.keepArticle(article(""))
        assertNull(model.state.value.entry("rss:example:1"))
        assertTrue("rss:example:1" in model.state.value.failed)
        assertNull(offlineImageMime("<svg onload=\"evil()\"/>".toByteArray()))
        assertEquals("image/png", offlineImageMime(png))
    }

    @Test
    fun theStorageAndPictureLimitsApplyBeforeAnythingIsShown() = timed {
        val tight = store(storageLimit = png.size - 1L)
        tight.keepMedia("saved:1", "Photo", "Maya", photo)
        assertTrue(tight.state.value.entries.isEmpty())
        assertTrue("saved:1" in tight.state.value.failed)

        val limited = OfflineStore(tempStore(), servesPng, imageLimit = png.size - 1L)
        limited.keepArticle(article("<img src=\"https://example.com/a.png\">"))
        val entry = limited.state.value.entry("rss:example:1")!!
        assertTrue(entry.files.isEmpty())
        assertEquals(OfflineAvailability.PARTIAL, entry.availability)
    }

    @Test
    fun aStreamThatGrowsPastItsLimitOrBreaksLeavesNothingBehind() = timed {
        val overflow = store(imageLimit = 32, http = MediaHttp { _, _ -> MediaResponse(200, emptyMap(), null, ChunkStream(png.copyOfRange(0, 16), png.copyOfRange(16, png.size)).apply { end() }) })
        overflow.keepMedia("saved:1", "Photo", "Maya", photo)
        assertTrue(overflow.state.value.entries.isEmpty())
        assertTrue(library.listFiles().orEmpty().isEmpty())

        val broken = store(MediaHttp { _, _ -> MediaResponse(200, emptyMap(), null, ChunkStream(png.copyOfRange(0, 16)).apply { fail(IOException("Interrupted")) }) })
        broken.keepMedia("saved:2", "Photo", "Maya", photo)
        assertTrue(broken.state.value.entries.isEmpty())
        assertTrue(broken.state.value.busy.isEmpty())
        assertTrue("saved:2" in broken.state.value.failed)
        assertTrue(library.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun removingStopsACopyStillBeingFetched() = timed {
        val started = CompletableDeferred<Unit>()
        val stream = ChunkStream()
        val model = store(MediaHttp { _, _ ->
            started.complete(Unit)
            MediaResponse(200, emptyMap(), null, stream)
        })
        val keeping = async { model.keepMedia("saved:1", "Photo", "Maya", photo) }
        started.await()
        waitUntil { "saved:1" in model.state.value.busy }
        val removal = async { model.remove("saved:1") }
        waitUntil { "saved:1" !in model.state.value.busy }
        stream.add(png)
        stream.end()
        keeping.await()
        removal.await()
        assertTrue(model.state.value.entries.isEmpty())
        assertTrue(model.state.value.busy.isEmpty())
        assertTrue(library.listFiles().orEmpty().isEmpty())
    }

    /** A folder where the manifest's next version is written makes that write fail. */
    private fun blockManifestWrites() = File(library, "manifest.json.part").mkdirs()

    @Test
    fun aFailedReplacementKeepsTheCopyBeforeIt() = timed {
        val model = store()
        val kept = article()
        model.keepArticle(kept)
        val bytes = model.state.value.bytes
        blockManifestWrites()
        model.keepArticle(article("<p>Replacement</p>"))
        assertTrue(kept.id in model.state.value.failed)
        assertEquals(kept.bodyHtml, model.article(kept.id)?.bodyHtml)
        assertEquals(bytes, model.state.value.bytes)
    }

    @Test
    fun aRemovalOrClearWhoseManifestCannotBeWrittenShowsNothingAsReady() = timed {
        val model = store()
        val kept = article()
        model.keepArticle(kept)
        blockManifestWrites()
        model.remove(kept.id)
        assertTrue(model.state.value.storageError)
        assertEquals(OfflineAvailability.UNAVAILABLE, model.state.value.entry(kept.id)?.availability)
        assertEquals(0, model.state.value.bytes)
        assertNull(model.article(kept.id))
        val cold = store()
        cold.load()
        assertFalse(cold.state.value.entry(kept.id)!!.canOpen)

        val otherDir = tempStore()
        val other = OfflineStore(otherDir, servesPng)
        other.keepArticle(kept)
        File(otherDir.root, "offline/manifest.json.part").mkdirs()
        other.clear()
        assertTrue(other.state.value.storageError)
        assertEquals(OfflineAvailability.UNAVAILABLE, other.state.value.entry(kept.id)?.availability)
        assertEquals(0, other.state.value.bytes)
    }

    @Test
    fun clearRemovesEveryCopyAndALoadClearsWhatACrashLeft() = timed {
        val model = store()
        model.keepArticle(article())
        File(library, "interrupted.tmp").apply { mkdirs(); File(this, "media-0").writeBytes(png) }
        model.refresh()
        assertFalse(File(library, "interrupted.tmp").exists())
        model.clear()
        assertTrue(model.state.value.entries.isEmpty())
        assertEquals(0, model.state.value.bytes)
        val cold = store()
        cold.load()
        assertTrue(cold.state.value.entries.isEmpty())
        assertEquals(listOf("manifest.json"), library.list()?.toList())
    }

    @Test
    fun aDamagedManifestShowsAnErrorAndARefreshRecovers() = timed {
        library.mkdirs()
        File(library, "manifest.json").writeText("{broken")
        val model = store()
        model.load()
        assertTrue(model.state.value.storageError)
        File(library, "manifest.json").writeText("""{"version":1,"entries":[]}""")
        model.refresh()
        assertFalse(model.state.value.storageError)
        assertFalse(model.state.value.loading)
    }

    @Test
    fun aKeptCopyIsReadBackOnTheNextLaunch() = timed {
        store().keepMedia("saved:1", "Photo", "Maya", photo, url = "https://x.com/i/status/1", sensitive = true)
        val cold = store(noNetwork)
        cold.load()
        val entry = cold.state.value.entry("saved:1")
        assertNotNull(entry)
        assertEquals(OfflineAvailability.AVAILABLE, entry.availability)
        assertTrue(entry.sensitive)
        assertEquals("image/png", entry.files.single().mime)
        assertContentEquals(png, cold.mediaFile(entry, entry.files.single())!!.readBytes())
    }
}
