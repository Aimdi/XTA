package com.aimdi.xtanative.networks.ehviewer

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Live e-hentai.org, read as a guest the way XTA reads it. Skipped unless
 * RUN_LIVE=true; the site may turn a data centre away, so CI's live job
 * never blocks a build on it.
 */
class EhViewerLiveTest {
    private fun live() = assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live e-hentai.org")

    @Test
    fun theFrontPageListsGalleriesAndLinksItsNextPage() = runBlocking<Unit> {
        live()
        val page = EhClient(OkHttpXHttp()).frontPage()

        assertTrue(page.items.isNotEmpty(), "the front page lists galleries")
        page.items.forEach { gallery ->
            assertTrue(gallery.gid > 0 && gallery.token.isNotEmpty() && gallery.title.isNotBlank(), "a whole gallery: $gallery")
        }
        assertNotNull(page.next, "the front page links its next page")
        println("LIVE EH front page: ${page.items.size} galleries, ${page.items.count { it.thumbUrl != null }} with thumbnails, next ${page.next}")
    }

    /** A gallery and its first page, written to the log and never failed on. */
    @Test
    fun theFirstGalleryAndItsFirstPageAreLogged() = runBlocking<Unit> {
        live()
        val client = EhClient(OkHttpXHttp())
        val outcome = runCatching {
            val gallery = client.frontPage().items.first()
            val detail = client.galleryDetail(gallery.gid, gallery.token)
            val first = detail.previews.firstOrNull()
            val image = first?.let { client.imagePage(gallery.gid, it.pageToken, it.page) }
            "${detail.gallery.pageCount} pages, ${detail.gallery.tags.size} tags, ${detail.previews.size} tiles " +
                "of ${detail.previewSheetCount} sheets, page 1 at ${image?.imageUrl?.take(60)}"
        }
        println("LIVE EH gallery: " + outcome.fold({ it }, { "refused, ${it::class.simpleName}: ${it.message?.take(160)}" }))
    }
}
