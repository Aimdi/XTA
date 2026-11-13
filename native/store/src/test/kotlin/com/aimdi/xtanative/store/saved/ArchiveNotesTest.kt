package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.core.json.J
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ArchiveNotesTest {
    @Test
    fun highlightsTagsAndExtractedTextAreKeptPerArticle() = runBlocking {
        val dir = tempStore()
        val notes = ArchiveNotesStore(dir)
        notes.highlight("a1", "A passage", "My note")
        notes.highlight("a1", "Another", null)
        notes.toggleTag("a1", "science")
        notes.toggleTag("a1", "read")
        notes.toggleTag("a1", "read")
        notes.setExtracted("a1", "Image text")
        val kept = ArchiveNotesStore(dir).of("a1")
        assertEquals(mapOf("A passage" to "My note", "Another" to ""), kept.highlights)
        assertEquals(setOf("science"), kept.tags)
        assertTrue(kept.searchable.contains("My note") && kept.searchable.contains("Image text"))
        notes.removeHighlight("a1", "Another")
        assertEquals(setOf("A passage"), notes.of("a1").highlights.keys)
        assertEquals(ArchiveAnnotation(), notes.of("unknown"))
    }

    @Test
    fun aRestoreAddsWhatIsMissingAndTheEditsMadeHereWin() = runBlocking {
        val notes = ArchiveNotesStore(tempStore())
        notes.highlight("1", "quote", "new edit")
        notes.toggleTag("1", "current")
        notes.setExtracted("1", "current text")
        notes.merge(mapOf("1" to ArchiveAnnotation(mapOf("quote" to "old edit", "other" to "another note"), setOf("imported"), "old text")))
        val restored = notes.of("1")
        assertEquals(mapOf("quote" to "new edit", "other" to "another note"), restored.highlights)
        assertEquals(setOf("current", "imported"), restored.tags)
        assertEquals("current text", restored.extracted)
    }

    @Test
    fun xtasBackupSectionCarriesOnlyAnnotationsAndRoundTrips() {
        val section = J(
            json(
                """{"archive-note:article-1":{"highlights":{"Quotation":"My note"},"tags":["science","read"],"extracted":"Image text"},
                "profile:id:1":{"private":"cache"},"archive-note:invalid":7}""",
            ),
        )
        val annotations = XtaRows.annotations(section)
        assertEquals(setOf("article-1"), annotations.keys)
        assertEquals(annotations, XtaRows.annotations(J(XtaRows.annotationsSection(annotations))))
        assertEquals(setOf("science", "read"), annotations.getValue("article-1").tags)
    }

    @Test
    fun anArticlesTextIsReadWithoutScriptsAndItsCommonWordsSuggestTags() {
        assertEquals("Readable text here", archivePlainText("<html><head><style>p{}</style></head><body><p>Readable</p><script>evil()</script>\n<p>text   here</p></body></html>"))
        val tags = suggestedArchiveTags("Kyoto temples. Kyoto gardens, kyoto food; temples again. This that with from about")
        assertEquals(listOf("kyoto", "temples"), tags.take(2))
        assertTrue("this" !in tags && "about" !in tags)
        assertTrue(suggestedArchiveTags((1..20).joinToString(" ") { "word$it" }).size == 8)
    }

    @Test
    fun aHighlightsDraftIsKeptPerArticleAndPassage() {
        assertEquals(ArchiveNotesStore.draftKey("a", "quote"), ArchiveNotesStore.draftKey("a", "quote"))
        assertNotEquals(ArchiveNotesStore.draftKey("a", "quote"), ArchiveNotesStore.draftKey("a", "other"))
        assertTrue(ArchiveNotesStore.draftKey("a", "quote").startsWith("highlight:a:"))
    }
}
