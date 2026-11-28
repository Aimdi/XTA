package com.aimdi.xtanative.ui.saved

import com.aimdi.xtanative.store.saved.SavedPost
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertTrue

class FolderExportTest {
    private fun link(url: String, text: String): JsonElement =
        Json.parseToJsonElement("""{"xtaPlugin":"link","source":"rss","url":"$url","author":"Blog","text":"$text"}""")

    @Test
    fun `a folder's export holds its posts under its name and leaves out what was not kept`() {
        val posts = listOf(
            SavedPost("a", null, link("https://example.com/a", "first <b>"), folderId = "f", savedAt = 2),
            SavedPost("b", null, null, folderId = "f", savedAt = 1),
        )
        val markdown = exportedText("Reading", posts, ExportFormat.MARKDOWN)
        assertTrue(markdown.startsWith("# Reading"), markdown)
        assertTrue("https://example.com/a" in markdown)
        val html = exportedText("Reading", posts, ExportFormat.HTML)
        assertTrue("<h1>Reading</h1>" in html)
        assertTrue("first &lt;b&gt;" in html)
    }
}
