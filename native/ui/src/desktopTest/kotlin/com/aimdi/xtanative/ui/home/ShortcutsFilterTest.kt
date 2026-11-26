package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.keys_ctrl
import com.aimdi.xtanative.ui.generated.resources.keys_refresh
import com.aimdi.xtanative.ui.generated.resources.keys_text_size
import com.aimdi.xtanative.ui.generated.resources.settings_accessibility
import com.aimdi.xtanative.ui.generated.resources.settings_general
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The F1 list's filter finds rows by what they do, their keys and their group. */
class ShortcutsFilterTest {
    /** A few texts as the reader would see them; the rest by their resource's name. */
    private val said = mapOf(
        Res.string.keys_refresh to "Refresh the page",
        Res.string.keys_ctrl to "Ctrl",
        Res.string.settings_general to "General",
        Res.string.settings_accessibility to "Accessibility",
        Res.string.keys_text_size to "Text size",
    )
    private val text: (StringResource) -> String = { said[it] ?: it.key }

    private fun find(query: String) = DesktopShortcuts.matching(DesktopShortcuts.sections, query, text)

    @Test
    fun aBlankQueryKeepsEveryRow() {
        assertEquals(DesktopShortcuts.sections, find(""))
        assertEquals(DesktopShortcuts.sections, find("   "))
    }

    @Test
    fun aRowIsFoundByWhatItDoesIgnoringCase() {
        val found = find("REFRESH")
        assertEquals(listOf(Res.string.settings_general), found.map { it.title })
        assertEquals(listOf(Res.string.keys_refresh), found.single().shortcuts.map { it.does })
    }

    @Test
    fun aRowIsFoundByItsKeysAndEveryWordMustMatch() {
        assertEquals(listOf(Res.string.keys_refresh), find("f5").flatMap { it.shortcuts }.map { it.does })
        assertEquals(listOf(Res.string.keys_refresh), find("ctrl refresh").flatMap { it.shortcuts }.map { it.does })
    }

    @Test
    fun aGroupsTitleFindsItsRows() {
        val found = find("accessibility")
        assertEquals(DesktopShortcuts.sections.filter { it.title == Res.string.settings_accessibility }, found)
    }

    @Test
    fun nothingFoundLeavesNoGroups() {
        assertTrue(find("zzz-no-such-key").isEmpty())
    }

    @Test
    fun theFilterReadsEveryTextTheListShows() {
        assertTrue(Res.string.keys_refresh in DesktopShortcuts.texts)
        assertTrue(Res.string.keys_ctrl in DesktopShortcuts.texts)
        assertTrue(Res.string.settings_general in DesktopShortcuts.texts)
    }
}
