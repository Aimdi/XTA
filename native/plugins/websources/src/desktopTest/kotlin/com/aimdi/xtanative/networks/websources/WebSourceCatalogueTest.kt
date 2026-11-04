package com.aimdi.xtanative.networks.websources

import com.aimdi.xtanative.networks.websources.WebSourceFixtures.source
import com.aimdi.xtanative.networks.websources.WebSourceFixtures.text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.HttpUrl.Companion.toHttpUrl

class WebSourceCatalogueTest {
    @Test
    fun thePublishedListResolvesFilesAgainstItselfAndLeavesOutBadEntries() {
        val listings = WebSourceCatalogue.parse(
            """{"format": 1, "sources": [
              {"id": "lobsters", "name": "Lobsters", "description": "Links", "url": "sources/lobsters.json"},
              {"id": "Bad Id", "name": "x", "url": "x.json"},
              {"id": "plain", "name": "Plain", "url": "http://example.org/plain.json"},
              {"id": "nameless", "url": "n.json"},
              {"id": "nowhere", "name": "Nowhere"}
            ]}""",
            "https://raw.githubusercontent.com/Aimdi/XTA/HEAD/sources.json".toHttpUrl(),
        )

        assertEquals(listOf("lobsters"), listings.map { it.id }, "one without a url is not the list itself, as XTA had it")
        assertEquals("https://raw.githubusercontent.com/Aimdi/XTA/HEAD/sources/lobsters.json".toHttpUrl(), listings.single().url)
        assertEquals("Links", listings.single().description)
        assertTrue(WebSourceCatalogue.parse("oops", "https://x.org/".toHttpUrl()).isEmpty())
    }

    private fun installed(agreed: List<String> = listOf("lobste.rs"), waiting: WebSourceDefinition? = null) = InstalledWebSource(
        source = source(WebSourceFixtures.links()),
        agreedHosts = agreed,
        origin = "https://example.org/lobsters.json".toHttpUrl(),
        waiting = waiting,
    )

    @Test
    fun anUpdateOnAgreedSitesReplacesTheSource() {
        val next = WebSourceUpdates.updated(installed(), WebSourceFile.Read(source(WebSourceFixtures.links(version = 2))))

        assertEquals(2L, next?.source?.version)
        assertNull(next?.waiting)
        assertEquals("https://example.org/lobsters.json".toHttpUrl(), next?.origin)
    }

    @Test
    fun anUpdateAskingForANewSiteWaitsForAgreement() {
        val newer = source(WebSourceFixtures.links(version = 2, hosts = listOf("lobste.rs", "cdn.lobste.rs")))

        val next = WebSourceUpdates.updated(installed(), WebSourceFile.Read(newer))

        assertEquals(1L, next?.source?.version)
        assertEquals(2L, next?.waiting?.version)
    }

    @Test
    fun anUpdateThatIsNotNewerIsAnotherSourceOrDoesNotReadChangesNothing() {
        assertNull(WebSourceUpdates.updated(installed(), WebSourceFile.Read(source(WebSourceFixtures.links()))))
        assertNull(WebSourceUpdates.updated(installed(), WebSourceFile.Read(source(WebSourceFixtures.posts()))))
        assertNull(WebSourceUpdates.updated(installed(), WebSourceFile.Refused(WebSourceRefusal.UNREADABLE, "")))
        val waiting = source(WebSourceFixtures.links(version = 2, hosts = listOf("lobste.rs", "x.org")))
        assertNull(WebSourceUpdates.updated(installed(waiting = waiting), WebSourceFile.Read(waiting)))
    }

    @Test
    fun everySourceXtaPublishesIsValidAndMatchesItsEntry() {
        val base = WebSourceCatalogue.DEFAULT_URL
        val listings = WebSourceCatalogue.parse(text("published/sources.json"), base)

        assertTrue(listings.isNotEmpty())
        assertEquals(listings.size, listings.map { it.id }.toSet().size)
        for (listing in listings) {
            val path = listing.url.encodedPath.substringAfter("/HEAD/")
            val file = WebSources.read(text("published/$path"))
            val source = (file as? WebSourceFile.Read)?.source ?: error("${listing.id}: $file")
            assertEquals(listing.id, source.id)
            assertEquals(listing.name, source.name)
            assertEquals(listing.description, source.description)
        }
    }
}
