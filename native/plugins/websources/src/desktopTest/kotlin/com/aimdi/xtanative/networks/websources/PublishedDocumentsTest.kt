package com.aimdi.xtanative.networks.websources

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.XEndpoints
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * The documents XTA publishes at the repository's root, which installs read
 * from the default branch without a release, read from the checkout with the
 * parsers the app reads them with: `endpoints.json` (X's query ids, through
 * [XEndpoints]), `sources.json` (the web sources' list, through
 * [WebSourceCatalogue]) and every file under `sources/` (through [WebSources]).
 * An edit that an install would drop or refuse fails here.
 */
class PublishedDocumentsTest {
    /** Where installs read the published files from: the branch's root, as raw.githubusercontent.com serves it. */
    private val published = "https://raw.githubusercontent.com/Aimdi/XTA/HEAD/".toHttpUrl()

    @Test
    fun everyEndpointOverrideNamesAKnownOperationWithAQueryId() {
        val body = J.parse(root().resolve("endpoints.json").readText())
        val named = body["endpoints"].keys

        named.forEach { name ->
            assertTrue(name in XEndpoints.shipped, "endpoints.json: $name is no operation XTA calls")
            val id = assertNotNull(body["endpoints"][name].string, "endpoints.json: $name has no query id")
            assertTrue(XEndpoints.isQueryId(id), "endpoints.json: $name's $id is not shaped like a query id")
        }
        assertEquals(named.size, XEndpoints().applyOverrides(body), "endpoints.json: every override is taken")
    }

    @Test
    fun everyListedSourceIsReadAndItsFileIsTheSourceItNames() {
        val listings = WebSourceCatalogue.parse(root().resolve("sources.json").readText(), published.resolve("sources.json")!!)
        val entries = J.parse(root().resolve("sources.json").readText())["sources"].list

        assertTrue(listings.isNotEmpty(), "sources.json lists sources")
        assertEquals(entries.size, listings.size, "sources.json: an entry the app leaves out")
        assertEquals(listings.size, listings.map { it.id }.toSet().size, "sources.json: an id listed twice")
        listings.forEach { listing ->
            val path = listing.url.toString().removePrefix(published.toString())
            assertTrue(listing.url.toString().startsWith(published.toString()), "${listing.id}: ${listing.url} is not a file of this repository")
            val file = root().resolve(path)
            assertTrue(file.isFile, "${listing.id}: $path is not in the repository")
            val read = assertIs<WebSourceFile.Read>(WebSources.read(file.readText()), "${listing.id}: $path is refused")
            assertEquals(listing.id, read.source.id, "$path: its id is not the one sources.json lists")
        }
    }

    @Test
    fun everyFileUnderSourcesIsAReadableSourceThatSourcesJsonLists() {
        val files = root().resolve("sources").listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }
        val listed = WebSourceCatalogue.parse(root().resolve("sources.json").readText(), published.resolve("sources.json")!!)
            .map { it.url.toString().removePrefix(published.toString()) }.toSet()

        assertTrue(files.isNotEmpty(), "sources/ holds source files")
        files.forEach { file ->
            val read = WebSources.read(file.readText())
            assertTrue(read is WebSourceFile.Read, "sources/${file.name}: refused, $read")
            assertTrue("sources/${file.name}" in listed, "sources/${file.name}: sources.json does not list it")
        }
    }

    /** The repository's root, found from wherever the tests run (native/plugins/websources under Gradle). */
    private fun root(): File {
        val found = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "endpoints.json").isFile && File(it, "sources.json").isFile && File(it, "native").isDirectory }
        return assertNotNull(found, "endpoints.json and sources.json beside native/")
    }
}
