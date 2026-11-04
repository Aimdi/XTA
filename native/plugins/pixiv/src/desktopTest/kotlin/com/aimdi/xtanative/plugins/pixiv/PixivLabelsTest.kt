package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivRanking
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.w3c.dom.Element

/**
 * The labels Pixiv's screens look up by name rather than through a property
 * of Strings, so the compiler cannot see one go missing: a missing one would
 * show its key ("ranking_day") on a chip.
 */
class PixivLabelsTest {
    private val asked: Set<String> = (PixivRanking.ILLUST_MODES + PixivRanking.NOVEL_MODES).map(PixivRankingPins::labelName).toSet()

    private val strings: File by lazy {
        listOf("strings", "native/plugins/pixiv/strings", "plugins/pixiv/strings").map(::File).firstOrNull { it.resolve("values").isDirectory }
            ?: fail("Pixiv's strings not found from ${File(".").absolutePath}")
    }

    private fun names(file: File): Set<String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }.map { it.getAttribute("name") }.toSet()
    }

    @Test
    fun everyRankingBoardIsLabelledInEnglish() {
        assertEquals(13, asked.size)
        asked.forEach { name -> assertNotEquals(name, Strings.get(name, "en"), "$name has no English text") }
    }

    @Test
    fun everyRankingBoardIsLabelledInEveryLanguage() {
        val folders = strings.listFiles { file -> file.isDirectory && file.name.startsWith("values") }.orEmpty().sorted()
        assertEquals(29, folders.size, "language folders: ${folders.map { it.name }}")
        folders.forEach { folder ->
            val missing = asked - names(folder.resolve("strings.xml"))
            assertTrue(missing.isEmpty(), "${folder.name} lacks $missing")
        }
    }
}
