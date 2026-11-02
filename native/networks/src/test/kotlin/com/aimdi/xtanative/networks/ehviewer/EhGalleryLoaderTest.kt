package com.aimdi.xtanative.networks.ehviewer

import com.aimdi.xtanative.networks.NetworkRequestException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** XTA's test/eh_gallery_store_test.dart. */
class EhGalleryLoaderTest {
    private fun EhGalleryLoader.pages() = state.value.previews.map { it.page }

    @Test
    fun theFirstLoadReadsTheDetailAndItsFirstPreviewSheet() = runBlocking<Unit> {
        val server = EhFixtureServer()
        val loader = EhGalleryLoader(server.client(), fixtureGallery)
        assertNull(loader.state.value.detail)
        assertSame(fixtureGallery, loader.shown)

        loader.load()

        val state = loader.state.value
        val detail = state.detail!!
        assertEquals("Sommer Book", detail.gallery.title)
        assertEquals("Alice", detail.gallery.uploader)
        assertEquals(EhCategory.DOUJINSHI, detail.gallery.category)
        assertEquals(setOf("female:twin tails"), detail.weakTags)
        assertEquals(3, detail.previewSheetCount)
        assertEquals(listOf(1, 2, 3, 4), loader.pages())
        assertEquals(1, state.nextSheet)
        assertTrue(state.hasMorePreviews)
        assertFalse(state.loading)
        assertSame(detail.gallery, loader.shown)
        assertEquals(listOf(0), server.sheetsRequested)
        assertEquals(listOf("Alice", "Reader 1", "Reader 2", "Reader 3"), detail.comments.map { it.author })
        assertTrue(detail.comments.first().uploader)
    }

    @Test
    fun previewSheetsAppendInOrderUntilTheLastOne() = runBlocking<Unit> {
        val server = EhFixtureServer()
        val loader = EhGalleryLoader(server.client(), fixtureGallery)
        loader.load()

        loader.loadMorePreviews()
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), loader.pages())
        assertEquals(2, loader.state.value.nextSheet)

        loader.nearEnd()
        assertEquals((1..12).toList(), loader.pages())
        assertFalse(loader.state.value.hasMorePreviews)

        loader.nearEnd()
        loader.loadMorePreviews()
        assertEquals(listOf(0, 1, 2), server.sheetsRequested)
    }

    @Test
    fun aFailedSheetStopsTheScrollTriggerButNotTheButton() = runBlocking<Unit> {
        val server = EhFixtureServer()
        val loader = EhGalleryLoader(server.client(), fixtureGallery)
        loader.load()

        server.failures = 1
        loader.nearEnd()
        assertTrue(loader.state.value.moreFailed)
        assertFalse(loader.state.value.loadingMore)
        assertEquals(listOf(1, 2, 3, 4), loader.pages())

        loader.nearEnd()
        assertEquals(listOf(0, 1), server.sheetsRequested)

        loader.loadMorePreviews()
        assertFalse(loader.state.value.moreFailed)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), loader.pages())
        assertEquals(listOf(0, 1, 1), server.sheetsRequested)
    }

    @Test
    fun theContinuePageComesFromHistoryPastTheFirstPageOnly() = runBlocking<Unit> {
        val loader = EhGalleryLoader(EhFixtureServer().client(), fixtureGallery)
        assertNull(loader.continuePage(null))
        assertNull(loader.continuePage(1))
        assertEquals(7, loader.continuePage(7))

        loader.load()
        assertEquals(24, loader.continuePage(30), "held inside the gallery's 24 pages once they are known")
    }

    @Test
    fun aFailedLoadKeepsTheErrorUntilARetrySucceeds() = runBlocking<Unit> {
        val server = EhFixtureServer().apply { failures = 1 }
        val loader = EhGalleryLoader(server.client(), fixtureGallery)

        loader.load()
        assertNull(loader.state.value.detail)
        val error = assertIs<NetworkRequestException>(loader.state.value.error)
        assertEquals(EhErrorKind.BAD_RESPONSE, EhErrorKind.of(error))
        assertFalse(loader.state.value.loading)

        loader.load()
        assertNull(loader.state.value.error)
        assertEquals("Sommer Book", loader.state.value.detail?.gallery?.title)
        assertEquals(listOf(1, 2, 3, 4), loader.pages())
    }

    @Test
    fun aSheetThatLandsAfterAReloadIsDropped() = runBlocking<Unit> {
        val server = EhFixtureServer()
        val loader = EhGalleryLoader(server.client(), fixtureGallery)
        loader.load()

        val held = server.hold(sheet = 1)
        val more = launch(start = CoroutineStart.UNDISPATCHED) { loader.loadMorePreviews() }
        assertTrue(loader.state.value.loadingMore)
        loader.load()
        held.complete(Unit)
        more.join()

        assertEquals(listOf(1, 2, 3, 4), loader.pages())
        assertEquals(1, loader.state.value.nextSheet)
        assertFalse(loader.state.value.loadingMore)
        assertEquals(listOf(0, 1, 0), server.sheetsRequested)
    }

    @Test
    fun aClosedLoaderTakesNothingIn() = runBlocking<Unit> {
        val loader = EhGalleryLoader(EhFixtureServer().client(), fixtureGallery)
        loader.close()
        loader.load()
        assertNull(loader.state.value.detail)
    }

    @Test
    fun mergingKeepsEachPageOnceInOrder() {
        val a = EhPreview(pageToken = "a", page = 1)
        val b = EhPreview(pageToken = "b", page = 2)
        val c = EhPreview(pageToken = "c", page = 3)
        assertEquals(listOf("a", "b", "c"), EhGalleryState.merge(listOf(a, c), listOf(b, c.copy(pageToken = "c2"))).map { it.pageToken })
    }
}
