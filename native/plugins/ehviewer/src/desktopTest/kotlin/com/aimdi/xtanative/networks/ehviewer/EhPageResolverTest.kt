package com.aimdi.xtanative.networks.ehviewer

import com.aimdi.xtanative.networks.NetworkRequestException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** XTA's test/eh_page_resolver_test.dart, and the reader's page count and preloading from test/eh_reader_store_test.dart. */
class EhPageResolverTest {
    private fun pages(first: Int, last: Int) = EhFakeSite.pages(first, last)

    private fun CoroutineScope.resolver(site: EhFakeSite, previews: List<EhPreview> = emptyList(), total: Int = site.total) =
        EhPageResolver(EhClient(site), site.gallery, total, previews, scope = this)

    @Test
    fun aFullFirstSheetGivesItsOwnLength() {
        assertEquals(40, EhPageResolver.sheetSizeFrom(sheet = 0, previews = pages(1, 40), total = 100))
        assertEquals(20, EhPageResolver.sheetSizeFrom(sheet = 0, previews = pages(1, 20), total = 100))
    }

    @Test
    fun aLaterSheetGivesItByThePageItStartsAt() {
        assertEquals(40, EhPageResolver.sheetSizeFrom(sheet = 2, previews = pages(81, 100), total = 100))
        assertEquals(20, EhPageResolver.sheetSizeFrom(sheet = 3, previews = pages(61, 80), total = 100))
    }

    @Test
    fun aSingleSheetAnEmptyOneOrOneThatDoesNotLineUpSaysNothing() {
        assertNull(EhPageResolver.sheetSizeFrom(sheet = 0, previews = pages(1, 12), total = 12))
        assertNull(EhPageResolver.sheetSizeFrom(sheet = 1, previews = emptyList(), total = 50))
        assertNull(EhPageResolver.sheetSizeFrom(sheet = 3, previews = pages(81, 100), total = 100))
    }

    @Test
    fun onlyTheUnbrokenRunFromPage1CountsAsTheFirstSheets() {
        assertEquals(listOf(1, 2, 3), EhPageResolver.leadingRun(pages(1, 3) + pages(5, 9)).map { it.page })
        assertTrue(EhPageResolver.leadingRun(pages(2, 5)).isEmpty())
    }

    @Test
    fun fetchesTheSheetAPageSitsOnSizedFromThePreviewsItWasGiven() = runBlocking<Unit> {
        val site = EhFakeSite(total = 100, sheetSize = 40)
        val resolver = resolver(site, pages(1, 40))

        val image = resolver.resolve(50).await()

        assertEquals("https://a.hath.example/h/50/050.webp", image?.imageUrl)
        assertEquals(listOf("1"), site.sheetRequests.map { it.queryParameter("p") })
        assertIs<EhPageSlot.Ready>(resolver.slots.value[50])
    }

    @Test
    fun aWrongGuessAtTheSheetSizeCorrectsItselfFromTheSheetItGot() = runBlocking<Unit> {
        val site = EhFakeSite(total = 100, sheetSize = 40)
        val resolver = resolver(site)

        assertNotNull(resolver.resolve(50).await())
        assertEquals(listOf("2", "1"), site.sheetRequests.map { it.queryParameter("p") })

        resolver.resolve(60).await()
        assertEquals(2, site.sheetRequests.size, "the second sheet came back with page 60 on it")
    }

    @Test
    fun cachesPagesAndSharesARequestAlreadyInFlight() = runBlocking<Unit> {
        val site = EhFakeSite()
        val resolver = resolver(site, pages(1, 20))

        val asked = listOf(resolver.resolve(3), resolver.resolve(3))
        val both = asked.awaitAll()
        resolver.resolve(3).await()

        assertSame(asked[0], asked[1])
        assertSame(both[0], both[1])
        assertEquals(1, site.imageRequests(3).size)
        assertTrue(site.sheetRequests.isEmpty())
    }

    @Test
    fun readingOnFollowsTheNextPageLinkInsteadOfAPreviewSheet() = runBlocking<Unit> {
        val site = EhFakeSite(total = 60)
        val resolver = resolver(site, listOf(EhFakeSite.preview(45)))

        resolver.resolve(45).await()
        val next = resolver.resolve(46).await()

        assertTrue(next!!.imageUrl.contains("/h/46/"))
        assertEquals(EhFakeSite.tokenOf(44), resolver.previewOf(44)?.pageToken)
        assertTrue(site.sheetRequests.isEmpty())
    }

    @Test
    fun reloadAsksForAnotherImageServerWithThePagesNlKey() = runBlocking<Unit> {
        val site = EhFakeSite()
        val resolver = resolver(site, pages(1, 20))
        resolver.resolve(3).await()

        val pending = resolver.reload(3)
        assertEquals(EhPageSlot.Loading, resolver.slots.value[3])
        val reloaded = pending.await()

        assertEquals("nl-3", site.requests.last().queryParameter("nl"))
        assertTrue(reloaded!!.imageUrl.startsWith("https://b.hath.example/"))
        assertSame(reloaded, resolver.imageOf(3))
    }

    @Test
    fun aFailedPageStaysFailedUntilItIsRetried() = runBlocking<Unit> {
        val site = EhFakeSite(failing = setOf(4))
        val resolver = resolver(site, pages(1, 20))

        assertNull(resolver.resolve(4).await())
        val failed = assertIs<EhPageSlot.Failed>(resolver.slots.value[4])
        assertEquals(500, assertIs<NetworkRequestException>(failed.error).code)
        resolver.resolve(4).await()
        assertEquals(1, site.imageRequests(4).size, "preloading never asks again")

        site.failingPages.clear()
        assertNotNull(resolver.retry(4).await())
        assertIs<EhPageSlot.Ready>(resolver.slots.value[4])
    }

    @Test
    fun pagesOutsideTheGalleryAreNeverAskedFor() = runBlocking<Unit> {
        val site = EhFakeSite()
        val resolver = resolver(site)

        assertNull(resolver.resolve(0).await())
        assertNull(resolver.resolve(31).await())
        assertNull(resolver.reload(31).await())
        assertTrue(site.requests.isEmpty())
    }

    @Test
    fun aPageNoSheetListsFailsAsNotFound() = runBlocking<Unit> {
        val site = EhFakeSite(total = 10)
        val resolver = resolver(site, total = 12)

        assertNull(resolver.resolve(12).await())
        val failed = assertIs<EhPageSlot.Failed>(resolver.slots.value[12])
        assertEquals(EhErrorKind.NOT_FOUND, EhErrorKind.of(failed.error))
    }

    @Test
    fun aClosedResolverAsksForNothing() = runBlocking<Unit> {
        val site = EhFakeSite()
        val resolver = resolver(site, pages(1, 20))
        resolver.close()

        assertNull(resolver.resolve(3).await())
        assertTrue(site.requests.isEmpty())
        assertTrue(resolver.slots.value.isEmpty())
    }

    @Test
    fun theScopeThatMadeAResolverIsNeverKeptWaitingByIt() = runBlocking<Unit> {
        val ended = withTimeoutOrNull(5_000) {
            coroutineScope { resolver(EhFakeSite(), pages(1, 20)).resolve(3) }
            true
        }
        assertEquals(true, ended, "a read ahead never awaited nor closed still lets its scope end")
    }

    @Test
    fun preloadsTheNextThreePagesAndTheOneBeforeInsideTheGallery() {
        assertEquals(listOf(6, 7, 8, 4), EhPageResolver.preloadPages(5, 30))
        assertEquals(listOf(2, 3, 4), EhPageResolver.preloadPages(1, 30))
        assertEquals(listOf(30, 28), EhPageResolver.preloadPages(29, 30))
    }

    @Test
    fun thePageCountFallsBackToTheFurthestPageKnown() {
        fun gallery(pages: Int?) = EhGallery(gid = 1, token = "t", title = "T", pageCount = pages)
        val previews = listOf(EhPreview(pageToken = "a", page = 12))

        assertEquals(30, EhPageResolver.totalOf(gallery(30), previews, initialPage = 3))
        assertEquals(12, EhPageResolver.totalOf(gallery(null), previews, initialPage = 3))
        assertEquals(7, EhPageResolver.totalOf(gallery(0), emptyList(), initialPage = 7))
    }
}
