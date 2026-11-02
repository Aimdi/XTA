package com.aimdi.xtanative.pluginhost

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogueTest {
    private val sha = "ab".repeat(32)

    private val documented = """{"apiVersion": 1, "plugins": [{
      "id": "reddit", "version": "1.0.3", "apiVersion": 1, "available": true,
      "private": false, "category": "communities",
      "name": {"en": "Reddit", "de": "Reddit"}, "description": {"en": "Read Reddit", "de": "Reddit lesen"},
      "android": {"url": "https://example.org/xta-plugin-reddit-1.0.3.apk", "sha256": "$sha", "size": 412345},
      "desktop": {"url": "https://example.org/xta-plugin-reddit-1.0.3.jar", "sha256": "${"CD".repeat(32)}", "size": 398765}
    }]}"""

    @Test
    fun theDocumentPluginsMdDescribesReadsWhole() {
        val catalogue = assertNotNull(PluginCatalogue.parse(documented))
        assertEquals(1, catalogue.apiVersion)
        val reddit = catalogue.entries.single()
        assertEquals("reddit", reddit.id)
        assertEquals("1.0.3", reddit.version)
        assertEquals(1, reddit.apiVersion)
        assertTrue(reddit.available)
        assertEquals(false, reddit.isPrivate)
        assertEquals(PluginCategory.COMMUNITIES, reddit.category)
        assertEquals("Reddit lesen", reddit.description.resolve(Locale.GERMAN))
        assertEquals(PluginArtifact("https://example.org/xta-plugin-reddit-1.0.3.apk", sha, 412345), reddit.artifact(PluginPlatform.ANDROID))
        assertEquals(PluginArtifact("https://example.org/xta-plugin-reddit-1.0.3.jar", "cd".repeat(32), 398765), reddit.artifact(PluginPlatform.DESKTOP))
    }

    @Test
    fun aDocumentThatNoLongerFitsIsNoCatalogueRatherThanAThrow() {
        listOf("{}", "[]", "\"nonsense\"", """{"plugins": "nope"}""", "<html>rate limited</html>", "").forEach { body ->
            assertNull(PluginCatalogue.parse(body), body)
        }
        assertEquals(emptyList(), PluginCatalogue.parse("""{"plugins": [1, 2, "three"]}""")?.entries)
    }

    @Test
    fun anEntryWithoutAnIdOrVersionOrWithOneThatCouldNotBeAFolderIsIgnored() {
        val ids = PluginCatalogue.parse(
            """{"plugins": [{"note": "soon"}, {"id": "reddit"}, {"id": "../etc", "version": "1"}, {"id": ".downloads", "version": "1"},
               {"id": "Reddit", "version": "1"}, {"id": "pixiv", "version": "../1"}, {"id": "stocks", "version": "2.0.0-beta+7"}]}""",
        )?.entries?.map { it.id }
        assertEquals(listOf("stocks"), ids)
    }

    @Test
    fun theFirstEntryForAnIdWins() {
        val catalogue = PluginCatalogue.parse("""{"plugins": [{"id": "reddit", "version": "1.0.0"}, {"id": "reddit", "version": "9.0.0"}]}""")
        assertEquals(listOf("1.0.0"), catalogue?.entries?.map { it.version })
    }

    @Test
    fun onlyFalseItselfHoldsAnEntryBackAndOnlyTrueMakesOnePrivate() {
        val entries = PluginCatalogue.parse(
            """{"plugins": [{"id": "a", "version": "1", "available": false, "private": true}, {"id": "b", "version": "1", "available": "false", "private": "true"},
               {"id": "c", "version": "1"}]}""",
        )!!.entries
        assertEquals(listOf(false, true, true), entries.map { it.available })
        assertEquals(listOf(true, false, false), entries.map { it.isPrivate })
    }

    @Test
    fun aFileThatIsNotWhollyDescribedIsNoFile() {
        val files = listOf(
            """{"sha256": "$sha", "size": 1}""",
            """{"url": "ftp://example.org/a.jar", "sha256": "$sha", "size": 1}""",
            """{"url": "https://example.org/a.jar", "sha256": "abc", "size": 1}""",
            """{"url": "https://example.org/a.jar", "sha256": "$sha", "size": 0}""",
            """{"url": "https://example.org/a.jar", "sha256": "$sha"}""",
            "\"https://example.org/a.jar\"",
        )
        files.forEach { desktop ->
            val entry = PluginCatalogue.parse("""{"plugins": [{"id": "a", "version": "1", "desktop": $desktop}]}""")!!.entries.single()
            assertNull(entry.artifact(PluginPlatform.DESKTOP), desktop)
        }
        val sizeAsText = PluginCatalogue.parse("""{"plugins": [{"id": "a", "version": "1", "desktop": {"url": "https://example.org/a.jar", "sha256": "$sha", "size": "12"}}]}""")
        assertEquals(12, sizeAsText!!.entries.single().artifact(PluginPlatform.DESKTOP)?.size)
    }

    @Test
    fun anEntryWithoutAnApiVersionTakesTheDocumentsAndADocumentWithoutOneTheFirst() {
        assertEquals(3, PluginCatalogue.parse("""{"apiVersion": 3, "plugins": [{"id": "a", "version": "1"}]}""")!!.entries.single().apiVersion)
        assertEquals(1, PluginCatalogue.parse("""{"plugins": [{"id": "a", "version": "1"}]}""")!!.entries.single().apiVersion)
        assertEquals(2, PluginCatalogue.parse("""{"apiVersion": 3, "plugins": [{"id": "a", "version": "1", "apiVersion": 2}]}""")!!.entries.single().apiVersion)
    }

    @Test
    fun aCategoryThisAppDoesNotKnowIsListedAsOther() {
        val categories = PluginCatalogue.parse(
            """{"plugins": [{"id": "a", "version": "1", "category": "Art"}, {"id": "b", "version": "1", "category": "weather"}, {"id": "c", "version": "1"}]}""",
        )!!.entries.map { it.category }
        assertEquals(listOf(PluginCategory.ART, PluginCategory.OTHER, PluginCategory.OTHER), categories)
    }

    @Test
    fun aNameFallsBackFromTheFullTagToTheLanguageToEnglishToAny() {
        val name = LocalizedText(mapOf("en" to "Bookmarks", "pt" to "Favoritos", "pt_BR" to "Marcadores", "zh-Hant" to "書籤"))
        assertEquals("Marcadores", name.resolve(Locale.forLanguageTag("pt-BR")))
        assertEquals("Favoritos", name.resolve(Locale.forLanguageTag("pt-PT")))
        assertEquals("書籤", name.resolve(Locale.forLanguageTag("zh-Hant-TW")))
        assertEquals("Bookmarks", name.resolve(Locale.GERMAN))
        assertEquals("Signets", LocalizedText(mapOf("fr" to "Signets")).resolve(Locale.GERMAN))
        assertNull(LocalizedText.EMPTY.resolve(Locale.GERMAN))
        val plain = PluginCatalogue.parse("""{"plugins": [{"id": "a", "version": "1", "name": "Hacker News", "description": {"en": " "}}]}""")!!.entries.single()
        assertEquals("Hacker News", plain.name.resolve(Locale.JAPANESE))
        assertNull(plain.description.resolve(Locale.ENGLISH), "a blank text is no text")
    }
}
